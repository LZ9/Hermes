package com.lodz.android.hermes.mqtt.base.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.IBinder
import com.lodz.android.hermes.modules.HermesLog
import com.lodz.android.hermes.mqtt.base.bean.eun.Ack
import com.lodz.android.hermes.mqtt.base.bean.source.MqttClient
import com.lodz.android.hermes.mqtt.base.db.MessageStore
import com.lodz.android.hermes.mqtt.base.db.MessageStoreImpl
import com.lodz.android.hermes.mqtt.base.sender.NetworkConnectionReceiver
import com.lodz.android.hermes.mqtt.base.utils.IoScope
import com.lodz.android.hermes.mqtt.base.utils.MqttUtils
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttMessageListener
import org.eclipse.paho.client.mqttv3.MqttClientPersistence
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence

/**
 * 后台服务
 * @author zhouL
 * @date 2023/10/13
 */
class MqttService : Service() {
    companion object{
        const val TAG = "MqttService"
        private const val FILE_PERSISTENCE_DIR_NAME = "MqttConnection"
    }

    /** 数据库操作接口 */
    private var mMessageStore: MessageStore? = null

    private var mBinder: MqttServiceBinder? = null

    /** 网络变化广播接收器 */
    private var mNetworkReceiver: NetworkConnectionReceiver? = null

    /** 客户端数据信息集合 */
    private val mClientMap = HashMap<String, MqttClient>()

    override fun onCreate() {
        super.onCreate()
        mBinder = MqttServiceBinder(this)
        mMessageStore = MessageStoreImpl(this)
    }

    override fun onDestroy() {
        disconnectAll()
        mBinder = null
        unregisterBroadcastReceivers()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = mBinder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        registerBroadcastReceivers()
        return super.onStartCommand(intent, flags, startId)
    }

    /** 注册网络变化广播接收器 */
    private fun registerBroadcastReceivers(){
        if (mNetworkReceiver == null) {
            mNetworkReceiver = NetworkConnectionReceiver()
            mNetworkReceiver?.setOnNetworkListener(mListener)
            registerReceiver(mNetworkReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        }
    }

    /** 解注册网络变化广播接收器 */
    private fun unregisterBroadcastReceivers() {
        if (mNetworkReceiver != null) {
            unregisterReceiver(mNetworkReceiver)
            mNetworkReceiver?.setOnNetworkListener(null)
            mNetworkReceiver = null
        }
    }

    /** 网络变化监听器 */
    private val mListener = object : NetworkConnectionReceiver.NetworkListener {
        override fun onNetworkChanged(isOnline: Boolean) {
            if (isOnline) {
                HermesLog.i(TAG, "online , reconnect")
                reconnectAll()
            } else {
                HermesLog.i(TAG, "offline , notify clients")
                notifyClientsOffline()
            }
        }
    }

    /** 通过连接参数创建连接客户端 */
    @JvmOverloads
    fun createClientKeyByParam(
        serverURI: String,
        clientId: String,
        options: MqttConnectOptions = MqttConnectOptions(),
        ackType: Ack = Ack.AUTO_ACK,
        persistence: MqttClientPersistence? = null
    ): String {
        val clientKey = MqttUtils.getClientKey(this, clientId, serverURI)
        val client = getClient(clientKey)
        if (client == null){
            addClient(serverURI,clientId, options,ackType, persistence)
        }
        return clientKey
    }

    /** 根据客户端主键[clientKey]连接服务端 */
    fun connect(clientKey: String) {
        getClient(clientKey)?.getConnection()?.connect()
    }

    /** 构建客户端对象，服务端地址[serverURI]，客户端编号[clientId]，连接配置[options]，消息确认模式类型[ackType]，持久层接口[persistence] */
    private fun createMqttClient(
        serverURI: String,
        clientId: String,
        options: MqttConnectOptions,
        ackType: Ack,
        persistence: MqttClientPersistence?
    ): MqttClient {
        if (serverURI.isEmpty()) {
            throw IllegalArgumentException("serverURI is empty")
        }
        if (clientId.isEmpty()){
            throw IllegalArgumentException("clientId is empty")
        }
        val store = mMessageStore ?: throw IllegalArgumentException("MessageStore is empty")
        val p = if (persistence != null) {
            persistence
        } else {
            val myDir = getExternalFilesDir(FILE_PERSISTENCE_DIR_NAME)
                ?: getDir(FILE_PERSISTENCE_DIR_NAME, Context.MODE_PRIVATE)
                ?: throw IllegalArgumentException("cannot get file dir")
            MqttDefaultFilePersistence(myDir.absolutePath)
        }
        return MqttClient(this, MqttUtils.getClientKey(this, clientId, serverURI), serverURI, clientId, options, p, ackType, store)
    }

    /** 添加客户端，服务端地址[serverURI]，客户端编号[clientId]，连接配置[options]，消息确认模式类型[ackType]，持久层接口[persistence] */
    private fun addClient(
        serverURI: String,
        clientId: String,
        options: MqttConnectOptions,
        ackType: Ack,
        persistence: MqttClientPersistence?
    ): MqttClient = addClient(createMqttClient(serverURI, clientId, options, ackType, persistence))

    /** 添加客户端[client] */
    private fun addClient(client: MqttClient): MqttClient {
        val data = mClientMap[client.getClientKey()]
        if (data == null) {
            mClientMap[client.getClientKey()] = client
        }
        return client
    }

    /** 关闭全部客户端 */
    fun closeAll() {
        for (entry in mClientMap) {
            entry.value.getConnection().close()
        }
    }

    /** 根据客户端主键[clientKey]关闭客户端 */
    fun close(clientKey: String) {
        getClient(clientKey)?.getConnection()?.close()
    }

    /** 断开所有客户端连接 */
    @JvmOverloads
    fun disconnectAll(quiesceTimeout: Long = -1) {
        for (entry in mClientMap) {
            entry.value.getConnection().disconnect(quiesceTimeout)
        }
    }

    /** 根据客户端主键[clientKey]断开连接，超时时间[quiesceTimeout]（毫秒） */
    @JvmOverloads
    fun disconnect(clientKey: String, quiesceTimeout: Long = -1) {
        getClient(clientKey)?.getConnection()?.disconnect(quiesceTimeout)
    }

    /** 根据客户端主键[clientKey]判断是否已经连接 */
    fun isConnected(clientKey: String): Boolean = getClient(clientKey)?.getConnection()?.isConnected() ?: false

    /** 根据客户端主键[clientKey]向主题[topic]发送消息[content] */
    fun publish(clientKey: String, topic: String, content: String): IMqttDeliveryToken? =
        getClient(clientKey)?.getConnection()?.publish(topic, content)

    /** 根据客户端主键[clientKey]向主题[topic]发送消息，消息内容[payload]，服务质量[qos]一般为0、1、2，MQTT服务器是否保留该消息[isRetained] */
    @JvmOverloads
    fun publish(
        clientKey: String,
        topic: String,
        payload: ByteArray,
        qos: Int = 1,
        isRetained: Boolean = false
    ): IMqttDeliveryToken? = getClient(clientKey)?.getConnection()?.publish(topic, payload, qos, isRetained)

    /** 根据客户端主键[clientKey]向主题[topic]发送消息[message] */
    fun publish(clientKey: String, topic: String, message: MqttMessage): IMqttDeliveryToken? =
        getClient(clientKey)?.getConnection()?.publish(topic, message)

    /** 根据客户端主键[clientKey]订阅多主题[topics]，消息监听[listeners] */
    @JvmOverloads
    fun subscribe(clientKey: String, topics: Array<String>, listeners: Array<IMqttMessageListener>? = null) {
        getClient(clientKey)?.getConnection()?.subscribe(topics, listeners)
    }

    /** 根据客户端主键[clientKey]订阅主题[topic]，服务质量[qos]一般为0、1、2，消息监听器[listeners] */
    @JvmOverloads
    fun subscribe(
        clientKey: String,
        topic: String,
        qos: Int = 1,
        listeners: Array<IMqttMessageListener>? = null
    ) {
        getClient(clientKey)?.getConnection()?.subscribe(topic, qos, listeners)
    }

    /** 根据客户端主键[clientKey]订阅主题[topics]，服务质量[qos]一般为0、1、2，消息监听器[listeners] */
    @JvmOverloads
    fun subscribe(clientKey: String, topics: Array<String>, qos: IntArray, listeners: Array<IMqttMessageListener>? = null) {
        getClient(clientKey)?.getConnection()?.subscribe(topics, qos, listeners)
    }

    /** 根据客户端主键[clientKey]取消订阅主题[topic] */
    fun unsubscribe(clientKey: String, topic: String) {
        getClient(clientKey)?.getConnection()?.unsubscribe(topic)
    }

    /** 根据客户端主键[clientKey]取消订阅主题[topics] */
    fun unsubscribe(clientKey: String, topics: Array<String>) {
        getClient(clientKey)?.getConnection()?.unsubscribe(topics)
    }

    /** 根据客户端主键[clientKey]获取待交付给客户端的票据 */
    fun getPendingDeliveryTokens(clientKey: String): Array<IMqttDeliveryToken>? =
        getClient(clientKey)?.getConnection()?.getPendingDeliveryTokens()

    /** 通过主键[clientKey]获取对应的客户端 */
    private fun getClient(clientKey: String): MqttClient? = mClientMap[clientKey]

    /** 获取客户端数据信息列表 */
    fun getClientMap() = mClientMap

    /** 根据客户端主键[clientKey]确认消息到达并删除数据库消息编号[messageId]的缓存 */
    fun acknowledgeMessageArrival(clientKey: String, messageId: String) {
        val client = getClient(clientKey)
        if (client != null && client.getAckType() == Ack.AUTO_ACK) {
            IoScope().launch { mMessageStore?.deleteArrivedMessage(clientKey, messageId) }
        }
    }

    /** 重连客户端 */
    private fun reconnectAll() {
        for (entry in mClientMap) {
            entry.value.getConnection().reconnect()
        }
    }

    /** 根据客户端主键[clientKey]重连客户端 */
    private fun reconnect(clientKey: String) {
        getClient(clientKey)?.getConnection()?.reconnect()
    }

    /** 通知客户端离线 */
    private fun notifyClientsOffline() {
        for (entry in mClientMap) {
            entry.value.getConnection().offline()
        }
    }

    /** 根据客户端主键[clientKey]设置断连缓冲配置项[bufferOpts] */
    fun setBufferOpts(clientKey: String, bufferOpts: DisconnectedBufferOptions) {
        getClient(clientKey)?.getConnection()?.setBufferOpts(bufferOpts)
    }

    /** 根据客户端主键[clientKey]获取断连缓冲配置项 */
    fun getBufferedMessageCount(clientKey: String): Int =
        getClient(clientKey)?.getConnection()?.getBufferedMessageCount() ?: 0

    /** 根据客户端主键[clientKey]和索引[bufferIndex]获取消息 */
    fun getBufferedMessage(clientKey: String, bufferIndex: Int): MqttMessage? =
        getClient(clientKey)?.getConnection()?.getBufferedMessage(bufferIndex)

    /** 根据客户端主键[clientKey]和索引[bufferIndex]删除消息 */
    fun deleteBufferedMessage(clientKey: String, bufferIndex: Int) {
        getClient(clientKey)?.getConnection()?.deleteBufferedMessage(bufferIndex)
    }
}