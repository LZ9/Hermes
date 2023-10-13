package com.lodz.android.hermes.mqtt.base

import android.accounts.NetworkErrorException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.lodz.android.hermes.mqtt.base.bean.eun.Ack
import com.lodz.android.hermes.mqtt.base.bean.eun.MqttAction
import com.lodz.android.hermes.mqtt.base.bean.event.MqttEvent
import com.lodz.android.hermes.mqtt.base.bean.source.ClientInfoBean
import com.lodz.android.hermes.mqtt.base.contract.ConnectActionListener
import com.lodz.android.hermes.mqtt.base.contract.DisconnectActionListener
import com.lodz.android.hermes.mqtt.base.contract.MqttListener
import com.lodz.android.hermes.mqtt.base.contract.PublishActionListener
import com.lodz.android.hermes.mqtt.base.contract.ServiceStartActionListener
import com.lodz.android.hermes.mqtt.base.contract.SubscribeActionListener
import com.lodz.android.hermes.mqtt.base.contract.UnsubscribeActionListener
import com.lodz.android.hermes.mqtt.base.service.MqttService
import com.lodz.android.hermes.mqtt.base.service.MqttServiceBinder
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttMessageListener
import org.eclipse.paho.client.mqttv3.MqttClientPersistence
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.IOException
import java.io.InputStream
import java.security.KeyManagementException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.Volatile

/**
 *
 * @author zhouL
 * @date 2023/10/13
 */
class MqttAndroidClient(private val context: Context) {

    /** MQTT服务 */
    private var mMqttService: MqttService? = null

    @Volatile
    private var isRegisteredEvent = false

    /** 连接和消息到达回调接口 */
    private var mCallback: MqttListener? = null

    /** 订阅操作回调 */
    private var mSubscribeActionListener: SubscribeActionListener? = null

    /** 解订阅相关操作回调 */
    private var mUnsubscribeActionListener: UnsubscribeActionListener? = null

    /** 发布消息操作回调 */
    private var mPublishActionListener: PublishActionListener? = null

    /** 后台服务启动操作回调 */
    private var mServiceStartActionListener: ServiceStartActionListener? = null

    /** 连接操作回调 */
    private var mConnectActionListener: ConnectActionListener? = null

    /** 断连操作回调 */
    private var mDisconnectActionListener: DisconnectActionListener? = null

    init {
        registerEvent()
    }

    fun release() {
        unregisterEvent()
        mMqttService?.disconnectAll()
        mMqttService?.closeAll()
        try {
            context.unbindService(mServiceConnection)
            val intent = Intent()
            intent.setClassName(context, MqttService::class.qualifiedName ?: "")
            context.stopService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mMqttService = null
    }

    private fun registerEvent() {
        if (!isRegisteredEvent) {
            EventBus.getDefault().register(this)
            isRegisteredEvent = true
        }
    }

    private fun unregisterEvent() {
        if (isRegisteredEvent) {
            EventBus.getDefault().unregister(this)
            isRegisteredEvent = false
        }
    }

    /** 设置连接和消息到达回调监听器[callback] */
    fun setCallback(callback: MqttListener?) {
        mCallback = callback
    }

    /** 根据客户端主键[clientKey]判断是否已经连接 */
    fun isConnected(clientKey: String): Boolean = mMqttService?.isConnected(clientKey) ?: false

    /** 获取客户端数据信息集合 */
    fun getClientInfoList(): HashMap<String, ClientInfoBean> {
        val result = HashMap<String, ClientInfoBean>()
        val data = mMqttService?.getClientMap() ?: return result
        for (entry in data) {
            result[entry.key] = entry.value
        }
        return result
    }

    /** 根据客户端主键[clientKey]关闭客户端并释放资源，关闭后客户端无法再连接 */
    fun close(clientKey: String) {
        mMqttService?.close(clientKey)
    }

    /** 关闭全部客户端 */
    fun closeAll() {
        mMqttService?.closeAll()
    }

    /** 通过连接参数创建连接客户端 */
    @JvmOverloads
    fun createClientKeyByParam(
        serverURI: String,
        clientId: String,
        options: MqttConnectOptions = MqttConnectOptions(),
        ackType: Ack = Ack.AUTO_ACK,
        persistence: MqttClientPersistence? = null
    ): String = mMqttService?.createClientKeyByParam(serverURI, clientId, options, ackType, persistence) ?: ""

    /** 启动服务 */
    @JvmOverloads
    fun startService(listener: ServiceStartActionListener? = null) {
        mServiceStartActionListener = listener
        val intent = Intent()
        intent.setClassName(context, MqttService::class.qualifiedName ?: "")
        val service = context.startService(intent)
        if (service == null) {
            mServiceStartActionListener?.onFailure("cannot start service ${MqttService::class.qualifiedName}", RuntimeException())
            return
        }
        if (!context.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)) {
            mServiceStartActionListener?.onFailure("cannot bind service ${MqttService::class.qualifiedName}", RuntimeException())
        }
    }

    /** 绑定服务 */
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mMqttService = (service as? MqttServiceBinder)?.getService()
            mServiceStartActionListener?.onSuccess()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mMqttService = null
        }
    }

    /** 根据客户端主键[clientKey]连接服务端，连接操作回调监听器[listener] */
    @JvmOverloads
    fun connect(clientKey: String, listener: ConnectActionListener? = null) {
        mConnectActionListener = listener
        mMqttService?.connect(clientKey)
    }

    /** 与服务器断开连接 */
    fun disconnectAll(quiesceTimeout: Long = -1) {
        mMqttService?.disconnectAll(quiesceTimeout)
    }

    /** 根据客户端主键[clientKey]断开连接，超时时间[quiesceTimeout]（毫秒） */
    @JvmOverloads
    fun disconnect(clientKey: String, quiesceTimeout: Long = -1, listener: DisconnectActionListener? = null) {
        mDisconnectActionListener = listener
        mMqttService?.disconnect(clientKey, quiesceTimeout)
    }

    /** 根据客户端主键[clientKey]向主题[topic]发送消息[content] */
    fun publish(
        clientKey: String,
        topic: String,
        content: String,
        listener: PublishActionListener? = null
    ): IMqttDeliveryToken? {
        mPublishActionListener = listener
        return mMqttService?.publish(clientKey, topic, content)
    }


    /** 根据客户端主键[clientKey]向主题[topic]发送消息，消息内容[payload]，服务质量[qos]一般为0、1、2，MQTT服务器是否保留该消息[isRetained] */
    @JvmOverloads
    fun publish(
        clientKey: String,
        topic: String,
        payload: ByteArray,
        qos: Int = 1,
        isRetained: Boolean = false,
        listener: PublishActionListener? = null
    ): IMqttDeliveryToken? {
        mPublishActionListener = listener
        return mMqttService?.publish(clientKey, topic, payload, qos, isRetained)
    }


    /** 根据客户端主键[clientKey]向主题[topic]发送消息[message] */
    @JvmOverloads
    fun publish(
        clientKey: String,
        topic: String,
        message: MqttMessage,
        listener: PublishActionListener? = null
    ): IMqttDeliveryToken? {
        mPublishActionListener = listener
        return mMqttService?.publish(clientKey, topic, message)
    }

    /** 根据客户端主键[clientKey]订阅多主题[topics]，消息监听[listeners] */
    @JvmOverloads
    fun subscribe(
        clientKey: String,
        topics: Array<String>,
        listeners: Array<IMqttMessageListener>? = null,
        actionListener: SubscribeActionListener? = null
    ) {
        mSubscribeActionListener = actionListener
        mMqttService?.subscribe(clientKey, topics, listeners)
    }

    /** 根据客户端主键[clientKey]订阅主题[topic]，服务质量[qos]一般为0、1、2，消息监听器[listeners] */
    @JvmOverloads
    fun subscribe(
        clientKey: String,
        topic: String,
        qos: Int = 1,
        listeners: Array<IMqttMessageListener>? = null,
        actionListener: SubscribeActionListener? = null
    ) {
        mSubscribeActionListener = actionListener
        mMqttService?.subscribe(clientKey, topic, qos, listeners)
    }

    /** 根据客户端主键[clientKey]订阅主题[topics]，服务质量[qos]一般为0、1、2，消息监听器[listeners] */
    @JvmOverloads
    fun subscribe(
        clientKey: String,
        topics: Array<String>,
        qos: IntArray,
        listeners: Array<IMqttMessageListener>? = null,
        actionListener: SubscribeActionListener? = null
    ) {
        mSubscribeActionListener = actionListener
        mMqttService?.subscribe(clientKey, topics, qos, listeners)
    }

    /** 根据客户端主键[clientKey]取消订阅主题[topic] */
    fun unsubscribe(clientKey: String, topic: String, listener: UnsubscribeActionListener? = null) {
        mUnsubscribeActionListener = listener
        mMqttService?.unsubscribe(clientKey, topic)
    }

    /** 根据客户端主键[clientKey]取消订阅主题[topics] */
    fun unsubscribe(clientKey: String, topics: Array<String>, listener: UnsubscribeActionListener? = null) {
        mUnsubscribeActionListener = listener
        mMqttService?.unsubscribe(clientKey, topics)
    }

    /** 根据客户端主键[clientKey]获取待交付给客户端的票据 */
    fun getPendingDeliveryTokens(clientKey: String): Array<IMqttDeliveryToken>? =
        mMqttService?.getPendingDeliveryTokens(clientKey)

    /** 根据客户端主键[clientKey]手动确认消息[messageId]到达 */
    fun acknowledgeMessage(clientKey: String, messageId: String) {
        mMqttService?.acknowledgeMessageArrival(clientKey, messageId)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMqttEvent(event: MqttEvent) {
        when (event.action) {
            MqttAction.ACTION_MSG_ARRIVED -> msgArrivedCallback(event)
            MqttAction.ACTION_CONNECTION_LOST -> connectionLostCallback(event)
            MqttAction.ACTION_DELIVERY_COMPLETE -> deliveryCompleteCallback(event)
            MqttAction.ACTION_CONNECT_COMPLETE -> connectCompleteCallback(event)
            MqttAction.ACTION_DISCONNECT -> disconnectCallback(event)
            MqttAction.ACTION_SUBSCRIBE -> subscribeCallback(event)
            MqttAction.ACTION_UNSUBSCRIBE -> unsubscribeCallback(event)
            MqttAction.ACTION_PUBLISH_MSG -> publishMsgCallback(event)
            MqttAction.ACTION_CONNECT -> connectCallback(event)
            else -> {}
        }
    }

    /** 连接事件回调 */
    private fun connectCallback(event: MqttEvent) {
        when (event.result) {
            MqttEvent.RESULT_SUCCESS -> mConnectActionListener?.onSuccess(
                event.action, event.clientKey
            )
            MqttEvent.RESULT_FAIL -> mConnectActionListener?.onFailure(
                event.action, event.clientKey, event.errorMsg, event.t ?: NetworkErrorException()
            )
        }
    }

    /** 发布消息事件回调 */
    private fun publishMsgCallback(event: MqttEvent) {
        when (event.result) {
            MqttEvent.RESULT_SUCCESS -> mPublishActionListener?.onSuccess(
                event.action, event.clientKey, event.topic, event.message ?: MqttMessage()
            )
            MqttEvent.RESULT_FAIL -> mPublishActionListener?.onFailure(
                event.action, event.clientKey, event.topic, event.errorMsg, event.t ?: NetworkErrorException()
            )
        }
    }

    /** 订阅事件回调 */
    private fun subscribeCallback(event: MqttEvent) {
        when (event.result) {
            MqttEvent.RESULT_SUCCESS -> mSubscribeActionListener?.onSuccess(
                event.action, event.clientKey, event.topics
            )
            MqttEvent.RESULT_FAIL -> mSubscribeActionListener?.onFailure(
                event.action, event.clientKey, event.topics, event.errorMsg, event.t ?: NetworkErrorException()
            )
        }
    }

    /** 解订阅事件回调 */
    private fun unsubscribeCallback(event: MqttEvent) {
        when (event.result) {
            MqttEvent.RESULT_SUCCESS -> mUnsubscribeActionListener?.onSuccess(
                event.action, event.clientKey, event.topics
            )
            MqttEvent.RESULT_FAIL -> mUnsubscribeActionListener?.onFailure(
                event.action, event.clientKey, event.topics, event.errorMsg, event.t ?: NetworkErrorException()
            )
        }
    }

    /** 消息到达事件回调 */
    private fun msgArrivedCallback(event: MqttEvent) {
        val data = event.data
        if (data != null) {
            acknowledgeMessage(event.clientKey, data.messageId)
            mCallback?.messageArrived(data.clientKey, data.topic, data.messageId, data.message)
        }
    }

    /** 连接丢失事件回调 */
    private fun connectionLostCallback(event: MqttEvent) {
        mCallback?.connectionLost(event.clientKey, event.t ?: NetworkErrorException())
    }

    /** 消息传递到服务端完成事件回调 */
    private fun deliveryCompleteCallback(event: MqttEvent) {
        mCallback?.deliveryComplete(event.clientKey, event.token)
    }

    /** 与服务器连接完成事件回调 */
    private fun connectCompleteCallback(event: MqttEvent) {
        mCallback?.connectComplete(event.clientKey, event.isReconnect, event.serverURI)
    }

    /** 断开连接事件回调 */
    private fun disconnectCallback(event: MqttEvent) {
        when (event.result) {
            MqttEvent.RESULT_SUCCESS -> {
                mDisconnectActionListener?.onSuccess(event.action, event.clientKey)
                mCallback?.connectionLost(event.clientKey, event.t ?: NetworkErrorException())
            }
            MqttEvent.RESULT_FAIL -> mDisconnectActionListener?.onFailure(
                event.action, event.clientKey, event.errorMsg, event.t ?: NetworkErrorException()
            )
        }
    }

    /** 根据客户端主键[clientKey]设置断连缓冲配置项[bufferOpts] */
    fun setBufferOpts(clientKey: String, bufferOpts: DisconnectedBufferOptions) {
        mMqttService?.setBufferOpts(clientKey, bufferOpts)
    }

    /** 根据客户端主键[clientKey]获取断连缓冲配置项 */
    fun getBufferedMessageCount(clientKey: String): Int =
        mMqttService?.getBufferedMessageCount(clientKey) ?: 0

    /** 根据客户端主键[clientKey]和索引[bufferIndex]获取消息 */
    fun getBufferedMessage(clientKey: String, bufferIndex: Int): MqttMessage? =
        mMqttService?.getBufferedMessage(clientKey, bufferIndex)

    /** 根据客户端主键[clientKey]和索引[bufferIndex]删除消息 */
    fun deleteBufferedMessage(clientKey: String, bufferIndex: Int) {
        mMqttService?.deleteBufferedMessage(clientKey, bufferIndex)
    }

    @Throws(
        KeyStoreException::class,
        IOException::class,
        NoSuchAlgorithmException::class,
        CertificateException::class,
        KeyManagementException::class
    )
    fun getSSLSocketFactory(keyStore: InputStream, password: String): SSLSocketFactory {
        val ts = KeyStore.getInstance("BKS")
        ts.load(keyStore, password.toCharArray())
        val tmf = TrustManagerFactory.getInstance("X509")
        tmf.init(ts)
        val ctx = SSLContext.getInstance("TLSv1")
        ctx.init(null, tmf.trustManagers, null)
        return ctx.socketFactory
    }
}