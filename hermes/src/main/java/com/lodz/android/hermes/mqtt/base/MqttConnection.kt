package com.lodz.android.hermes.mqtt.base

import android.accounts.NetworkErrorException
import android.content.Context
import android.os.PowerManager
import com.lodz.android.hermes.modules.HermesLog
import com.lodz.android.hermes.mqtt.base.bean.eun.MqttAction
import com.lodz.android.hermes.mqtt.base.bean.event.MqttEvent
import com.lodz.android.hermes.mqtt.base.bean.source.ClientInfoBean
import com.lodz.android.hermes.mqtt.base.contract.DisconnectMqttEventActionListener
import com.lodz.android.hermes.mqtt.base.contract.PublishMqttEventActionListener
import com.lodz.android.hermes.mqtt.base.contract.SubscribeMqttEventActionListener
import com.lodz.android.hermes.mqtt.base.contract.UnsubscribeMqttEventActionListener
import com.lodz.android.hermes.mqtt.base.db.DbStoredData
import com.lodz.android.hermes.mqtt.base.db.MessageStore
import com.lodz.android.hermes.mqtt.base.db.MessageStoreImpl
import com.lodz.android.hermes.mqtt.base.sender.AlarmPingSender
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


    init {
        try {
            mClient = MqttAsyncClient(mBean.getServerURI(), mBean.getClientId(), mPersistence, mAlarmPingSender)
            mClient?.setCallback(this)
        } catch (e: Exception) {
            e.printStackTrace()
            EventBus.getDefault().post(MqttEvent.createFail(mBean.getClientKey(), MqttAction.ACTION_CREATE_CLIENT, "create MqttAsyncClient fail",e))
        }
    }

    /** 连接服务器 */
    fun connect() {
        val client = mClient
        if (client == null) {
            EventBus.getDefault().post(MqttEvent.createConnectFail(mBean.getClientKey(), "client is null",  NullPointerException()))
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
            connectSuccess()
            return
        }
        HermesLog.d(TAG, "start connect!")
        isConnecting = true
        try {
            client.connect(mBean.getConnectOptions(), object :IMqttActionListener{
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    HermesLog.i(TAG, "connect success!")
                    connectSuccess()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    HermesLog.e(TAG, "connect fail, call connect to reconnect.reason : ${exception?.message}")
                    connectFail("connect failure", exception)
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            isConnecting = false
            connectFail("connect fail", e)
        }
        HermesUtils.releaseWakeLock(mWakeLock)
    }

    /** 连接成功 */
    private fun connectSuccess(){
        HermesUtils.acquireWakeLock(mWakeLock)
        EventBus.getDefault().post(MqttEvent.createConnectSuccess(mBean.getClientKey()))
        deliverBacklog()
        isConnecting = false
        HermesUtils.releaseWakeLock(mWakeLock)
    }

    /** 将数据库缓存的到达消息全部返回给用户 */
    private fun deliverBacklog() {
        IoScope().launch {
            val backlog = mMessageStore.getAllMessages(mBean.getClientKey())
            for (data in backlog) {
                launch(Dispatchers.Main) { EventBus.getDefault().post(MqttEvent.createMsgArrived(mBean.getClientKey(), data, mBean.getAckType())) }
            }
        }
    }

    /** 连接失败 */
    private fun connectFail(errorMsg: String, e: Throwable?) {
        HermesUtils.acquireWakeLock(mWakeLock)
        isConnecting = false
        EventBus.getDefault().post(MqttEvent.createConnectFail(mBean.getClientKey(), errorMsg, e ?: RuntimeException()))
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
    fun disconnect(quiesceTimeout: Long = -1) {
        val client = mClient
        if (client == null) {
            EventBus.getDefault().post(MqttEvent.createFail(mBean.getClientKey(), MqttAction.ACTION_DISCONNECT, "client is null",  NullPointerException()))
            return
        }
        HermesLog.d(TAG, "mqtt disconnect {${mBean.getClientKey()}")
        if (!client.isConnected){
            EventBus.getDefault().post(MqttEvent.createDisconnectSuccess(mBean.getClientKey()))
            return
        }
        HermesLog.d(TAG, "start disconnect!")
        val listener = DisconnectMqttEventActionListener(mBean.getClientKey(), "disconnect failure")
        try {
            if (quiesceTimeout > 0) {
                client.disconnect(quiesceTimeout, null, listener)
            } else {
                client.disconnect(null, listener)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            EventBus.getDefault().post(MqttEvent.createDisconnectFail(mBean.getClientKey(), "disconnect fail", e))
        }
        if (mBean.getConnectOptions().isCleanSession){
            IoScope().launch { mMessageStore.clearAllMessages(mBean.getClientKey()) }
        }
        HermesUtils.releaseWakeLock(mWakeLock)
    }

    /** 是否已连接 */
    fun isConnected() = mClient?.isConnected ?: false

    /** 向主题[topic]发送消息[content] */
    fun publish(topic: String, content: String): IMqttDeliveryToken? = publish(topic, content.toByteArray())

    /** 向主题[topic]发送消息，消息内容[payload]，服务质量[qos]一般为0、1、2，MQTT服务器是否保留该消息[isRetained] */
    @JvmOverloads
    fun publish(
        topic: String,
        payload: ByteArray,
        qos: Int = 1,
        isRetained: Boolean = false
    ): IMqttDeliveryToken? {
        val message = MqttMessage(payload)
        message.qos = qos
        message.isRetained = isRetained
        return publish(topic, message)
    }

    /** 向主题[topic]发送消息[message] */
    fun publish(topic: String, message: MqttMessage): IMqttDeliveryToken? {
        val client = mClient
        if (client == null) {
            EventBus.getDefault().post(MqttEvent.createPublishFail(mBean.getClientKey(), topic, "client is null",  NullPointerException()))
            return null
        }
        val listener = PublishMqttEventActionListener(mBean.getClientKey(), "publish failure", topic, message)
        try {
            if (client.isConnected) {
                return client.publish(topic, message, null, listener)
            }
            val opts = mBufferOpts
            if (opts != null && opts.isBufferEnabled) {
                // 虽然客户端未连接，但是缓冲已启用，也允许发送消息
                return client.publish(topic, message, null, listener)
            }
            EventBus.getDefault().post(MqttEvent.createPublishFail(mBean.getClientKey(), topic, "client is not connected, can not send message", RuntimeException()))
        } catch (e: Exception) {
            e.printStackTrace()
            EventBus.getDefault().post(MqttEvent.createPublishFail(mBean.getClientKey(), topic, "publish fail", e))
        }
        return null
    }

    /** 订阅主题[topic]，服务质量[qos]一般为0、1、2，消息监听[listeners] */
    @JvmOverloads
    fun subscribe(topic: String, qos: Int = 1, listeners: Array<IMqttMessageListener>? = null) {
        subscribe(arrayOf(topic), intArrayOf(qos), listeners)
    }

    /** 订阅多主题[topics]，消息监听[listeners] */
    @JvmOverloads
    fun subscribe(topics: Array<String>, listeners: Array<IMqttMessageListener>? = null) {
        val qos = IntArray(topics.size)
        topics.forEachIndexed { i, s ->
            qos[i] = 1
        }
        subscribe(topics, qos, listeners)
    }

    /** 订阅多主题[topics]，服务质量[qos]一般为0、1、2，消息监听[listeners] */
    @JvmOverloads
    fun subscribe(topics: Array<String>, qos: IntArray, listeners: Array<IMqttMessageListener>? = null) {
        HermesLog.d(TAG, "subscribe topics : ${topics.contentToString()} , qos : ${qos.contentToString()}")
        val client = mClient
        if (client == null) {
            EventBus.getDefault().post(MqttEvent.createSubscribeFail(mBean.getClientKey(), topics, "client is null",  NullPointerException()))
            return
        }
        if (!client.isConnected) {
            EventBus.getDefault().post(MqttEvent.createSubscribeFail(mBean.getClientKey(), topics, "client is disconnect",  IllegalStateException()))
            return
        }
        val listener = SubscribeMqttEventActionListener(mBean.getClientKey(), "subscribe failure", topics)
        try {
            if (listeners.isNullOrEmpty()) {
                client.subscribe(topics, qos, null, listener)
            } else {
                client.subscribe(topics, qos, listeners)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            EventBus.getDefault().post(MqttEvent.createSubscribeFail(mBean.getClientKey(), topics, "subscribe fail",  IllegalStateException()))
        }
    }

    /** 取消订阅主题[topic] */
    fun unsubscribe(topic: String) {
        unsubscribe(arrayOf(topic))
    }

    /** 取消订阅主题[topics] */
    fun unsubscribe(topics: Array<String>) {
        HermesLog.d(TAG, "unsubscribe topic : ${topics.contentToString()}")
        val client = mClient
        if (client == null) {
            EventBus.getDefault().post(MqttEvent.createUnsubscribeFail(mBean.getClientKey(), topics, "client is null",  NullPointerException()))
            return
        }
        if (!client.isConnected) {
            EventBus.getDefault().post(MqttEvent.createUnsubscribeFail(mBean.getClientKey(), topics, "client is disconnect",  IllegalStateException()))
            return
        }
        try {
            client.unsubscribe(topics, null,  UnsubscribeMqttEventActionListener(mBean.getClientKey(), "unsubscribe failure", topics))
        } catch (e: Exception) {
            e.printStackTrace()
            EventBus.getDefault().post(MqttEvent.createUnsubscribeFail(mBean.getClientKey(), topics, "unsubscribe fail",  IllegalStateException()))
        }
    }

    /** 获取IMqttDeliveryToken数组 */
    fun getPendingDeliveryTokens() : Array<IMqttDeliveryToken>? = mClient?.pendingDeliveryTokens

    /*-------------------------------------- 实现MqttCallbackExtended ----------------------------------------------------*/

    /** 连接丢失回调，断连原因[cause] */
    override fun connectionLost(cause: Throwable?) {
        HermesLog.d(TAG, "connectionLost case : $cause")
        EventBus.getDefault().post(MqttEvent.createConnectionLost(mBean.getClientKey(), cause))
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
                EventBus.getDefault().post(MqttEvent.createMsgArrived(mBean.getClientKey(), DbStoredData(messageId, mBean.getClientKey(), topic, message), mBean.getAckType()))
            }
        }
    }

    /** 发送的消息已到达 */
    override fun deliveryComplete(token: IMqttDeliveryToken?) {
        HermesLog.d(TAG, "deliveryComplete IMqttDeliveryToken : $token");
        EventBus.getDefault().post(MqttEvent.createDeliveryComplete(mBean.getClientKey(), token))
    }

    override fun connectComplete(reconnect: Boolean, serverURI: String) {
        isConnecting = false
        EventBus.getDefault().post(MqttEvent.createConnectComplete(mBean.getClientKey(), reconnect, serverURI))
    }

    /*-------------------------------------- 实现MqttCallbackExtended ----------------------------------------------------*/

    /** 设置客户端离线 */
    fun offline() {
        val client = mClient ?: return
        if (client.isConnected && !mBean.getConnectOptions().isCleanSession) {
            connectionLost(NetworkErrorException("mqtt offline"));
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
                EventBus.getDefault().post(MqttEvent.createConnectFail(mBean.getClientKey(), "reconnect fail", e))
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
}