package com.lodz.android.hermes.mqtt.client

import android.accounts.NetworkErrorException
import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import com.lodz.android.hermes.contract.*
import com.lodz.android.hermes.modules.HermesLog
import com.lodz.android.hermes.mqtt.base.bean.eun.Ack
import com.lodz.android.hermes.mqtt.base.bean.source.MqttClient
import com.lodz.android.hermes.mqtt.base.connection.OnMcActionListener
import com.lodz.android.hermes.mqtt.base.connection.OnMcPublishListener
import com.lodz.android.hermes.mqtt.base.connection.OnMcSubListener
import com.lodz.android.hermes.mqtt.base.db.DbStoredData
import com.lodz.android.hermes.mqtt.base.sender.NetworkConnectionReceiver
import com.lodz.android.hermes.mqtt.base.service.MqttService
import com.lodz.android.hermes.utils.HermesUtils
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttClientPersistence
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence
import java.io.File

/**
 * MQTT推送客户端实现
 * @author zhouL
 * @date 2019/12/21
 */
class MqttClientImpl : HermesMqttClient {

    /** 日志标签 */
    private var mTag = "MqttClientLog"

    /** 上下文 */
    private var mContext: Context? = null

    /** 是否静默 */
    private var isSilent: Boolean = false

    /** 是否至少连接过一次 */
    private var isConnectedOnce = false

    /** mqtt客户端 */
    private var mMqttClient: MqttClient? = null

    /** 订阅监听器 */
    private var mOnSubscribeListener: OnSubscribeListener? = null
    /** 解除订阅监听器 */
    private var mOnUnsubscribeListener: OnUnsubscribeListener? = null
    /** 连接监听器 */
    private var mOnConnectListener: OnConnectListener? = null
    /** 发送监听器 */
    private var mOnSendListener: OnSendListener? = null
    /** 构建监听器 */
    private var mOnBuildListener: OnBuildListener? = null
    /** 订阅主题列表 */
    private var mSubTopics: Array<String>? = null

    /** 网络变化广播接收器 */
    private var mNetworkReceiver: NetworkConnectionReceiver? = null

    override fun init(context: Context): HermesMqttClient = apply {
        mContext = context
        registerBroadcastReceivers()
    }

    // TODO: 2023/10/20 断网后再联网后不会自动订阅

    override fun setLogTag(tag: String): HermesMqttClient = apply { if (tag.isNotEmpty()) { mTag = tag } }

    override fun setSilent(isSilent: Boolean): HermesMqttClient = apply { this.isSilent = isSilent }

    override fun isSilent(): Boolean = isSilent

    override fun setPrintLog(isPrint: Boolean): HermesMqttClient = apply { HermesLog.setPrint(isPrint) }

    override fun build(
        serverURI: String,
        clientId: String,
        options: MqttConnectOptions?,
        ackType: Ack,
        persistence: MqttClientPersistence?
    ): HermesMqttClient {
        val context = mContext
        if (context == null) {
            MainScope().launch { mOnBuildListener?.onFailure("", NullPointerException("context is null , you need call init()")) }
            return this
        }
        val key = HermesUtils.getClientKey(context, clientId, serverURI)
        if (serverURI.isEmpty()) {
            MainScope().launch { mOnBuildListener?.onFailure(key, IllegalArgumentException("serverURI is empty")) }
            return this
        }
        if (clientId.isEmpty()) {
            MainScope().launch { mOnBuildListener?.onFailure(key, IllegalArgumentException("clientId is empty")) }
            return this
        }
        var p = persistence
        if (p == null) {
            val file: File? = context.getExternalFilesDir(MqttService.FILE_PERSISTENCE_DIR_NAME)
                ?: context.getDir(MqttService.FILE_PERSISTENCE_DIR_NAME, Context.MODE_PRIVATE)
            if (file == null) {
                MainScope().launch { mOnBuildListener?.onFailure(key, IllegalArgumentException("cannot get file dir")) }
                return this
            }
            p = MqttDefaultFilePersistence(file.absolutePath)
        }
        val connectOptions = options ?: MqttConnectOptions().apply {
            this.isAutomaticReconnect = true
            this.isCleanSession = false
        }
        mMqttClient = MqttClient(context, key, serverURI, clientId, connectOptions, p, ackType)
        mMqttClient?.getConnection()?.setMqttCallback(object : MqttClientCallback {
            override fun connectionLost(cause: Throwable) {
                MainScope().launch { mOnConnectListener?.onConnectionLost(cause) }
            }

            override fun messageArrived(topic: String, message: MqttMessage, data: DbStoredData) {
                if (ackType == Ack.AUTO_ACK){//自动确认到达时删除数据库内的缓存
                    mMqttClient?.getConnection()?.acknowledgeMessageArrival(data.messageId)
                }
                MainScope().launch { mOnSubscribeListener?.onMsgArrived(topic, message) }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                MainScope().launch { mOnConnectListener?.deliveryComplete(token) }
            }

            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                if (reconnect) {
                    subscribe(mSubTopics ?: arrayOf())
                }
                MainScope().launch { mOnConnectListener?.onConnectComplete(reconnect) }
            }
        })
        MainScope().launch { mOnBuildListener?.onSuccess(key) }
        return this
    }

    private fun putSubTopic(topics: Array<String>?): Array<String>? {
        if (topics.isNullOrEmpty()) {
            return mSubTopics
        }
        val list = mSubTopics?.toMutableList() ?: arrayListOf()
        list.addAll(topics.toList())
        mSubTopics = LinkedHashSet(list).toTypedArray()//去重
        return mSubTopics
    }

    override fun sendData(topic: String, data: String): IMqttDeliveryToken? = sendData(topic, data.toByteArray())

    override fun sendData(topic: String, data: ByteArray): IMqttDeliveryToken? =
        mMqttClient?.getConnection()?.publish(topic, data, actionListener = object : OnMcPublishListener {
            override fun onSuccess(clientKey: String, topic: String, message: MqttMessage) {
                MainScope().launch { mOnSendListener?.onComplete(topic, message) }
            }

            override fun onFail(clientKey: String, topic: String, t: Throwable) {
                MainScope().launch { mOnSendListener?.onFailure(topic, t) }
            }
        })

    override fun subscribe(topics: Array<String>): HermesMqttClient {
        if (topics.isEmpty()){
            MainScope().launch { mOnSubscribeListener?.onFailure(topics, IllegalArgumentException("topics is empty")) }
            return this
        }
        val list = putSubTopic(topics)
        if (list.isNullOrEmpty()) { // 没有可以订阅的主题
            return this
        }
        if (!isConnectedOnce){
            return this
        }
        val client = mMqttClient
        if (client == null){ // 未初始化
            MainScope().launch { mOnSubscribeListener?.onFailure(topics, NullPointerException("client is null , you need call build")) }
            return this
        }
        if (!client.getConnection().isConnected()){ // 未连接客户端
            MainScope().launch { mOnSubscribeListener?.onFailure(topics, IllegalStateException("client is not connect")) }
            return this
        }
        client.getConnection().subscribe(topics, actionListener = object : OnMcSubListener {
            override fun onSuccess(clientKey: String, topics: Array<String>) {
                MainScope().launch { mOnSubscribeListener?.onSuccess(topics) }
            }

            override fun onFail(clientKey: String, topics: Array<String>, t: Throwable) {
                MainScope().launch { mOnSubscribeListener?.onFailure(topics, t) }
            }

        })
        return this
    }

    override fun unsubscribe(topics: Array<String>): HermesMqttClient {
        if (topics.isEmpty()){
            MainScope().launch { mOnUnsubscribeListener?.onFailure(topics, IllegalArgumentException("topics is empty")) }
            return this
        }
        val client = mMqttClient
        if (client == null){ // 未初始化
            MainScope().launch { mOnUnsubscribeListener?.onFailure(topics, NullPointerException("client is null , you need call build")) }
            return this
        }
        if (!client.getConnection().isConnected()){ // 未连接客户端
            MainScope().launch { mOnUnsubscribeListener?.onFailure(topics, IllegalStateException("client is not connect")) }
            return this
        }
        val subList = mSubTopics
        if (subList.isNullOrEmpty()){ // 没有主题被订阅
            MainScope().launch { mOnUnsubscribeListener?.onFailure(topics, NullPointerException("no topic is subscribed")) }
            return this
        }
        val noSubTopicList = arrayListOf<String>() // 未订阅列表
        for (t in topics) {
            if (!subList.toMutableList().contains(t)) {
                noSubTopicList.add(t)
            }
        }
        if (noSubTopicList.isNotEmpty()) {
            MainScope().launch {
                mOnUnsubscribeListener?.onFailure(topics,
                    IllegalStateException("the ${noSubTopicList.toTypedArray().contentToString()} is no subscribed"))
            }
            return this
        }
        client.getConnection().unsubscribe(topics, object : OnMcSubListener {
            override fun onSuccess(clientKey: String, topics: Array<String>) {
                val result = mSubTopics?.filter { !topics.contains(it) }
                mSubTopics = result?.toTypedArray()
                MainScope().launch { mOnUnsubscribeListener?.onSuccess(topics) }
            }

            override fun onFail(clientKey: String, topics: Array<String>, t: Throwable) {
                MainScope().launch { mOnUnsubscribeListener?.onFailure(topics, t) }
            }
        })
        return this
    }

    override fun connect() {
        val client = mMqttClient
        if (client == null){ // 未初始化
            MainScope().launch { mOnConnectListener?.onConnectFailure(NullPointerException("client is null , you need call build")) }
            return
        }
        if (client.getConnection().isConnected()){ // 客户端已连接
            MainScope().launch { mOnConnectListener?.onConnectComplete(false) }
            return
        }
        client.getConnection().connect(object : OnMcActionListener {
            override fun onSuccess(clientKey: String) {
                isConnectedOnce = true
                val disconnectedBufferOptions = DisconnectedBufferOptions()
                disconnectedBufferOptions.isBufferEnabled = true
                disconnectedBufferOptions.bufferSize = 100
                disconnectedBufferOptions.isPersistBuffer = false
                disconnectedBufferOptions.isDeleteOldestMessages = false
                mMqttClient?.getConnection()?.setBufferOpts(disconnectedBufferOptions)
                val topics = mSubTopics ?: arrayOf()
                if (topics.isNotEmpty()){
                    subscribe(topics)
                }
            }

            override fun onFail(clientKey: String, t: Throwable) {
                MainScope().launch { mOnConnectListener?.onConnectFailure(t) }
            }
        })
    }

    override fun disconnect(quiesceTimeout: Long) {
        val client = mMqttClient ?: return
        if (!client.getConnection().isConnected()) { // 客户端未连接
            return
        }
        client.getConnection().disconnect(quiesceTimeout, object : OnMcActionListener {
            override fun onSuccess(clientKey: String) {
                MainScope().launch { mOnConnectListener?.onConnectionLost(NetworkErrorException()) }
            }

            override fun onFail(clientKey: String, t: Throwable) {
                MainScope().launch { mOnConnectListener?.onConnectionLost(t) }
            }
        })
    }

    override fun isConnected(): Boolean = mMqttClient?.getConnection()?.isConnected() ?: false

    override fun getSubscribeTopic(): Array<String> = mSubTopics ?: arrayOf()

    override fun setOnSubscribeListener(listener: OnSubscribeListener?): HermesMqttClient =
        apply { mOnSubscribeListener = listener }

    override fun setOnUnsubscribeListener(listener: OnUnsubscribeListener?): HermesMqttClient =
        apply { mOnUnsubscribeListener = listener }

    override fun setOnConnectListener(listener: OnConnectListener?): HermesMqttClient =
        apply { mOnConnectListener = listener }

    override fun setOnSendListener(listener: OnSendListener?): HermesMqttClient =
        apply { mOnSendListener = listener }

    override fun setOnBuildListener(listener: OnBuildListener?): HermesMqttClient =
        apply { mOnBuildListener = listener }

    override fun release() {
        unregisterBroadcastReceivers()
        mMqttClient?.getConnection()?.disconnect()
        mMqttClient?.getConnection()?.close()
        mMqttClient = null
    }

    /** 注册网络变化广播接收器 */
    private fun registerBroadcastReceivers(){
        if (mNetworkReceiver == null) {
            mNetworkReceiver = NetworkConnectionReceiver()
            mNetworkReceiver?.setOnNetworkListener(mListener)
            mContext?.registerReceiver(mNetworkReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        }
    }

    /** 解注册网络变化广播接收器 */
    private fun unregisterBroadcastReceivers() {
        if (mNetworkReceiver != null) {
            mContext?.unregisterReceiver(mNetworkReceiver)
            mNetworkReceiver?.setOnNetworkListener(null)
            mNetworkReceiver = null
        }
    }

    /** 网络变化监听器 */
    private val mListener = object : NetworkConnectionReceiver.NetworkListener {
        override fun onNetworkChanged(isOnline: Boolean) {
            if (isOnline) {
                HermesLog.i(mTag, "online , reconnect")
                if (mMqttClient?.getConnectOptions()?.isAutomaticReconnect == true && isConnectedOnce){
                    mMqttClient?.getConnection()?.reconnect()
                }
            } else {
                HermesLog.i(mTag, "offline , notify clients")
                mMqttClient?.getConnection()?.offline()
            }
        }
    }
}