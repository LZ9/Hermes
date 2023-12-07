package com.lodz.android.hermes.mqtt.base.connection

import android.accounts.NetworkErrorException
import android.content.Context
import android.os.PowerManager
import com.lodz.android.hermes.modules.HermesLog
import com.lodz.android.hermes.mqtt.base.bean.event.MqttEvent
import com.lodz.android.hermes.mqtt.base.bean.source.ClientInfoBean
import com.lodz.android.hermes.mqtt.base.db.DbStoredData
import com.lodz.android.hermes.mqtt.base.db.MessageStore
import com.lodz.android.hermes.mqtt.base.db.MessageStoreImpl
import com.lodz.android.hermes.mqtt.base.sender.AlarmPingSender
import com.lodz.android.hermes.mqtt.client.MqttClientCallback
import com.lodz.android.hermes.utils.IoScope
import com.lodz.android.hermes.utils.HermesUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttMessageListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClientPersistence
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.greenrobot.eventbus.EventBus
import java.lang.RuntimeException
import kotlin.concurrent.Volatile

/**
 * MQTT连接对象
 * @author zhouL
 * @date 2023/10/12
 */
class MqttConnection(
    private val mContext: Context, // 上下文
    private val mBean: ClientInfoBean, // 客户端信息
    mPersistence: MqttClientPersistence, // 持久层接口
) : MqttCallbackExtended {

    companion object {
        private const val TAG = "MqttConnection"
    }

    /** 客户端 */
    private var mClient: MqttAsyncClient? = null
    /** 数据库操作方法 */
    private val mMessageStore: MessageStore = MessageStoreImpl(mContext)

    /** 唤醒锁 */
    private val mWakeLock: PowerManager.WakeLock = HermesUtils.getWakeLock(mContext,
        "${javaClass.name} ${mBean.getClientId()} on host ${mBean.getServerURI()}")

    /** ping数据包发送器 */
    private val mAlarmPingSender: AlarmPingSender = AlarmPingSender(mContext)

    /** 连接断开配置 */
    private var mBufferOpts: DisconnectedBufferOptions? = null

    /** 是否连接中 */
    @Volatile
    private var isConnecting = false

    private var mMqttCallback: MqttClientCallback? = null

    init {
        try {
            mClient = MqttAsyncClient(mBean.getServerURI(), mBean.getClientId(), mPersistence, mAlarmPingSender)
            mClient?.setCallback(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 设置MQTT回调监听器 */
    fun setMqttCallback(callback: MqttClientCallback?) {
        mMqttCallback = callback
    }

    /** 连接服务器 */
    @JvmOverloads
    fun connect(actionListener: OnMcActionListener? = null) {
        val client = mClient
        if (client == null) {
            val e = NullPointerException("client is null")
            actionListener.fail(e){ MqttEvent.createConnectFail(mBean.getClientKey(), e) }
            return
        }
        HermesLog.d(TAG, "start connect {${mBean.getClientKey()}}")
        if (mBean.getConnectOptions().isCleanSession){// 如果是新的会话则清空数据库缓存
            IoScope().launch { mMessageStore.clearAllMessages(mBean.getClientKey()) }
        }
        if (isConnecting) {
            HermesLog.d(TAG, "now connecting")
            return
        }
        if (client.isConnected) {
            HermesLog.d(TAG, "client is already connected")
            connectSuccess(actionListener)
            return
        }
        HermesLog.d(TAG, "start connect!")
        isConnecting = true
        try {
            client.connect(mBean.getConnectOptions(), object :IMqttActionListener{
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    HermesLog.i(TAG, "connect success!")
                    connectSuccess(actionListener)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    HermesLog.e(TAG, "connect fail , reason : ${exception?.message}")
                    connectFail(actionListener, exception ?: RuntimeException("connect failure"))
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            isConnecting = false
            connectFail(actionListener, e)
        }
        HermesUtils.releaseWakeLock(mWakeLock)
    }

    /** 连接成功 */
    private fun connectSuccess(actionListener: OnMcActionListener?){
        HermesUtils.acquireWakeLock(mWakeLock)
        actionListener.success { MqttEvent.createConnectSuccess(mBean.getClientKey()) }
        deliverBacklog()
        isConnecting = false
        HermesUtils.releaseWakeLock(mWakeLock)
    }

    /** 将数据库缓存的到达消息全部返回给用户 */
    private fun deliverBacklog() {
        IoScope().launch {
            val backlog = mMessageStore.getAllMessages(mBean.getClientKey())
            for (data in backlog) {
                launch(Dispatchers.Main) {
                    mMqttCallback.callbackMessageArrived(data.topic, data.message, data)
                }
            }
        }
    }

    /** 连接失败 */
    private fun connectFail(actionListener: OnMcActionListener?, e: Throwable) {
        HermesUtils.acquireWakeLock(mWakeLock)
        isConnecting = false
        actionListener.fail(e){ MqttEvent.createConnectFail(mBean.getClientKey(), e) }
        HermesUtils.releaseWakeLock(mWakeLock)
    }

    /** 关闭客户端 */
    fun close(){
        HermesLog.d(TAG, "close client")
        try {
            mClient?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 断开连接，在断开连接之前允许完成现有工作的时间[quiesceTimeout]（以毫秒为单位）。 值为零或更低意味着客户端不会停顿 */
    @JvmOverloads
    fun disconnect(quiesceTimeout: Long = -1, actionListener: OnMcActionListener? = null) {
        val client = mClient
        if (client == null) {
            val e = NullPointerException("client is null")
            actionListener.fail(e){ MqttEvent.createDisconnectFail(mBean.getClientKey(), e) }
            return
        }
        HermesLog.d(TAG, "mqtt disconnect {${mBean.getClientKey()}")
        if (!client.isConnected){
            actionListener.success { MqttEvent.createDisconnectSuccess(mBean.getClientKey()) }
            return
        }
        HermesLog.d(TAG, "start disconnect!")
        val listener = object :IMqttActionListener{
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                actionListener.success { MqttEvent.createDisconnectSuccess(mBean.getClientKey()) }
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                val e = exception ?: RuntimeException("disconnect failure")
                actionListener.fail(e){ MqttEvent.createDisconnectFail(mBean.getClientKey(), e) }
            }
        }
        try {
            if (quiesceTimeout > 0) {
                client.disconnect(quiesceTimeout, null, listener)
            } else {
                client.disconnect(null, listener)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            actionListener.fail(e){ MqttEvent.createDisconnectFail(mBean.getClientKey(), e) }
        }
        if (mBean.getConnectOptions().isCleanSession){
            IoScope().launch { mMessageStore.clearAllMessages(mBean.getClientKey()) }
        }
        HermesUtils.releaseWakeLock(mWakeLock)
    }

    /** 是否已连接 */
    fun isConnected() = mClient?.isConnected ?: false

    /** 向主题[topic]发送消息[content] */
    fun publish(
        topic: String,
        content: String,
        actionListener: OnMcPublishListener? = null
    ): IMqttDeliveryToken? = publish(topic, content.toByteArray(), actionListener = actionListener)

    /** 向主题[topic]发送消息，消息内容[payload]，服务质量[qos]一般为0、1、2，MQTT服务器是否保留该消息[isRetained] */
    @JvmOverloads
    fun publish(
        topic: String,
        payload: ByteArray,
        qos: Int = 1,
        isRetained: Boolean = false,
        actionListener: OnMcPublishListener? = null
    ): IMqttDeliveryToken? {
        val message = MqttMessage(payload)
        message.qos = qos
        message.isRetained = isRetained
        return publish(topic, message, actionListener)
    }

    /** 向主题[topic]发送消息[message] */
    fun publish(topic: String, message: MqttMessage, actionListener: OnMcPublishListener? = null): IMqttDeliveryToken? {
        val client = mClient
        if (client == null) {
            val e = NullPointerException("client is null")
            actionListener.fail(topic, e){ MqttEvent.createPublishFail(mBean.getClientKey(), topic, e) }
            return null
        }
        val listener = object :IMqttActionListener{
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                actionListener.success(topic, message){ MqttEvent.createPublishSuccess(mBean.getClientKey(), topic, message) }
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                val e = exception ?: RuntimeException("publish failure")
                actionListener.fail(topic, e){ MqttEvent.createPublishFail(mBean.getClientKey(), topic, e) }
            }
        }
        try {
            if (client.isConnected) {
                return client.publish(topic, message, null, listener)
            }
            val opts = mBufferOpts
            if (opts != null && opts.isBufferEnabled) {
                // 虽然客户端未连接，但是缓冲已启用，也允许发送消息
                return client.publish(topic, message, null, listener)
            }
            val e = RuntimeException("client is not connected, can not send message")
            actionListener.fail(topic, e){ MqttEvent.createPublishFail(mBean.getClientKey(), topic, e) }
        } catch (e: Exception) {
            e.printStackTrace()
            actionListener.fail(topic, e){ MqttEvent.createPublishFail(mBean.getClientKey(), topic, e) }
        }
        return null
    }

    /** 订阅主题[topic]，服务质量[qos]一般为0、1、2，消息监听[listeners] */
    @JvmOverloads
    fun subscribe(
        topic: String,
        qos: Int = 1,
        listeners: Array<IMqttMessageListener>? = null,
        actionListener: OnMcSubListener? = null
    ) {
        subscribe(arrayOf(topic), intArrayOf(qos), listeners, actionListener)
    }

    /** 订阅多主题[topics]，消息监听[listeners] */
    @JvmOverloads
    fun subscribe(
        topics: Array<String>,
        listeners: Array<IMqttMessageListener>? = null,
        actionListener: OnMcSubListener? = null
    ) {
        val qos = IntArray(topics.size)
        topics.forEachIndexed { i, s ->
            qos[i] = 1
        }
        subscribe(topics, qos, listeners, actionListener)
    }

    /** 订阅多主题[topics]，服务质量[qos]一般为0、1、2，消息监听[listeners] */
    @JvmOverloads
    fun subscribe(
        topics: Array<String>,
        qos: IntArray,
        listeners: Array<IMqttMessageListener>? = null,
        actionListener: OnMcSubListener? = null
    ) {
        HermesLog.d(TAG, "subscribe topics : ${topics.contentToString()} , qos : ${qos.contentToString()}")
        val client = mClient
        if (client == null) {
            val e = NullPointerException("client is null")
            actionListener.fail(topics, e){ MqttEvent.createSubscribeFail(mBean.getClientKey(), topics, e) }
            return
        }
        if (!client.isConnected) {
            val e = IllegalStateException("client is disconnect")
            actionListener.fail(topics, e){ MqttEvent.createSubscribeFail(mBean.getClientKey(), topics, e) }
            return
        }

        val listener = object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                actionListener.success(topics){ MqttEvent.createSubscribeSuccess(mBean.getClientKey(), topics) }
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                val e = exception ?: RuntimeException("subscribe failure")
                actionListener.fail(topics, e){ MqttEvent.createSubscribeFail(mBean.getClientKey(), topics, e) }
            }
        }
        try {
            if (listeners.isNullOrEmpty()) {
                client.subscribe(topics, qos, null, listener)
            } else {
                client.subscribe(topics, qos, listeners)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            actionListener.fail(topics, e){ MqttEvent.createSubscribeFail(mBean.getClientKey(), topics, e) }
        }
    }

    /** 取消订阅主题[topic] */
    fun unsubscribe(topic: String, actionListener: OnMcSubListener? = null) {
        unsubscribe(arrayOf(topic), actionListener)
    }

    /** 取消订阅主题[topics] */
    fun unsubscribe(topics: Array<String>, actionListener: OnMcSubListener? = null) {
        HermesLog.d(TAG, "unsubscribe topic : ${topics.contentToString()}")
        val client = mClient
        if (client == null) {
            val e = NullPointerException("client is null")
            actionListener.fail(topics, e){ MqttEvent.createUnsubscribeFail(mBean.getClientKey(), topics, e) }
            return
        }
        if (!client.isConnected) {
            val e = IllegalStateException("client is disconnect")
            actionListener.fail(topics, e){ MqttEvent.createUnsubscribeFail(mBean.getClientKey(), topics, e) }
            return
        }
        try {
            client.unsubscribe(topics, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    actionListener.success(topics){ MqttEvent.createUnsubscribeSuccess(mBean.getClientKey(), topics) }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    val e = exception ?: RuntimeException("unsubscribe failure")
                    actionListener.fail(topics, e){ MqttEvent.createUnsubscribeFail(mBean.getClientKey(), topics, e) }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            actionListener.fail(topics, e){ MqttEvent.createUnsubscribeFail(mBean.getClientKey(), topics, e) }
        }
    }

    /** 获取IMqttDeliveryToken数组 */
    fun getPendingDeliveryTokens() : Array<IMqttDeliveryToken>? = mClient?.pendingDeliveryTokens

    /*-------------------------------------- 实现MqttCallbackExtended ----------------------------------------------------*/

    /** 连接丢失回调，断连原因[cause] */
    override fun connectionLost(cause: Throwable?) {
        val t = cause ?: NetworkErrorException("mqtt connection lost")
        HermesLog.d(TAG, "connectionLost case : $t")
        mMqttCallback.callbackConnectionLost(t){ MqttEvent.createConnectionLost(mBean.getClientKey(), t) }
        val client = mClient ?: return
        try {
            if (!mBean.getConnectOptions().isAutomaticReconnect) {
                client.disconnect()//没有开启自动重连则手动断开连接
            } else {
                mAlarmPingSender.schedule(100)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        HermesUtils.releaseWakeLock(mWakeLock)
    }

    /** 服务端消息到达 */
    override fun messageArrived(topic: String, message: MqttMessage) {
        HermesLog.d(TAG, "messageArrived topic : $topic , message : $message")
        IoScope().launch {
            val messageId = mMessageStore.saveMessage(mBean.getClientKey(), topic, message)
            launch(Dispatchers.Main) {
                mMqttCallback.callbackMessageArrived(topic, message, DbStoredData(messageId, mBean.getClientKey(), topic, message))
            }
        }
    }

    /** 发送的消息已到达 */
    override fun deliveryComplete(token: IMqttDeliveryToken?) {
        HermesLog.d(TAG, "deliveryComplete IMqttDeliveryToken : $token")
        mMqttCallback.callbackDeliveryComplete(token)
    }

    override fun connectComplete(reconnect: Boolean, serverURI: String) {
        isConnecting = false
        mMqttCallback.callbackConnectComplete(reconnect, serverURI)
    }

    /*-------------------------------------- 实现MqttCallbackExtended ----------------------------------------------------*/

    /** 设置客户端离线 */
    fun offline() {
        val client = mClient ?: return
        if (client.isConnected && !mBean.getConnectOptions().isCleanSession) {
            connectionLost(NetworkErrorException("mqtt offline"))
        }
    }

    /** 重连 */
    fun reconnect() {
        val client = mClient ?: return
        if (isConnecting) {
            HermesLog.d(TAG, "the client is connecting")
            return
        }
        if (!HermesUtils.isOnline(mContext)) {
            HermesLog.d(TAG, "the network is offline, cannot reconnect")
            return
        }
        if (mBean.getConnectOptions().isAutomaticReconnect) {//开启自动重连
            HermesLog.i(TAG, "start automatic reconnect")
            try {
                isConnecting = true
                client.reconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                isConnecting = false
            }
            return
        }
        if (!client.isConnected && !mBean.getConnectOptions().isCleanSession) {// 手动重连
            connect()
        }
    }

    /** 设置断连缓冲配置项[bufferOpts] */
    fun setBufferOpts(bufferOpts: DisconnectedBufferOptions) {
        mBufferOpts = bufferOpts
        mClient?.setBufferOpts(bufferOpts)
    }

    /** 获取断连缓冲配置项 */
    fun getBufferedMessageCount(): Int = mClient?.bufferedMessageCount ?: 0

    /** 根据索引[bufferIndex]获取消息 */
    fun getBufferedMessage(bufferIndex: Int): MqttMessage? = mClient?.getBufferedMessage(bufferIndex)

    /** 根据索引[bufferIndex]删除消息 */
    fun deleteBufferedMessage(bufferIndex: Int) {
        mClient?.deleteBufferedMessage(bufferIndex)
    }

    /** 确认消息到达并删除数据库消息编号[messageId]的缓存 */
    fun acknowledgeMessageArrival(messageId: String) {
        IoScope().launch { mMessageStore.deleteArrivedMessage(mBean.getClientKey(), messageId) }
    }

    /** 执行操作成功回调 */
    private fun OnMcActionListener?.success(block: () -> MqttEvent) {
        if (this != null) {
            this.onSuccess(mBean.getClientKey())
        } else {
            EventBus.getDefault().post(block.invoke())
        }
    }

    /** 执行操作失败回调 */
    private fun OnMcActionListener?.fail(t: Throwable, block: () -> MqttEvent) {
        if (this != null) {
            this.onFail(mBean.getClientKey(), t)
        } else {
            EventBus.getDefault().post(block.invoke())
        }
    }

    /** 执行消息发布操作成功回调 */
    private fun OnMcPublishListener?.success(topic: String, message: MqttMessage, block: () -> MqttEvent) {
        if (this != null) {
            this.onSuccess(mBean.getClientKey(), topic, message)
        } else {
            EventBus.getDefault().post(block.invoke())
        }
    }

    /** 执行消息发布操作失败回调 */
    private fun OnMcPublishListener?.fail(topic: String, t: Throwable, block: () -> MqttEvent) {
        if (this != null) {
            this.onFail(mBean.getClientKey(), topic, t)
        } else {
            EventBus.getDefault().post(block.invoke())
        }
    }

    /** 执行订阅/取消订阅操作成功回调 */
    private fun OnMcSubListener?.success(topics: Array<String>, block: () -> MqttEvent) {
        if (this != null) {
            this.onSuccess(mBean.getClientKey(), topics)
        } else {
            EventBus.getDefault().post(block.invoke())
        }
    }

    /** 执行订阅/取消订阅操作失败回调 */
    private fun OnMcSubListener?.fail(topics: Array<String>, t: Throwable, block: () -> MqttEvent) {
        if (this != null) {
            this.onFail(mBean.getClientKey(), topics, t)
        } else {
            EventBus.getDefault().post(block.invoke())
        }
    }

    /** 回调连接完成 */
    private fun MqttClientCallback?.callbackConnectComplete(reconnect: Boolean, serverURI: String) {
        if (this != null) {
            this.connectComplete(reconnect, serverURI)
        } else {
            EventBus.getDefault().post(MqttEvent.createConnectComplete(mBean.getClientKey(), reconnect, serverURI))
        }
    }

    /** 回调连接丢失 */
    private fun MqttClientCallback?.callbackConnectionLost(t: Throwable, block: () -> MqttEvent) {
        if (this != null) {
            this.connectionLost(t)
        } else {
            EventBus.getDefault().post(block.invoke())
        }
    }

    /** 回调发送消息传递完成 */
    private fun MqttClientCallback?.callbackDeliveryComplete(token: IMqttDeliveryToken?) {
        if (this != null) {
            this.deliveryComplete(token)
        } else {
            EventBus.getDefault().post(MqttEvent.createDeliveryComplete(mBean.getClientKey(), token))
        }
    }

    /** 回调消息到达 */
    private fun MqttClientCallback?.callbackMessageArrived(topic: String, message: MqttMessage, data: DbStoredData) {
        if (this != null) {
            this.messageArrived(topic, message, data)
        } else {
            EventBus.getDefault().post(MqttEvent.createMsgArrived(mBean.getClientKey(), data, mBean.getAckType()))
        }
    }

}