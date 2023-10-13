package com.lodz.android.hermes.mqtt

import android.content.Context
import com.lodz.android.hermes.contract.*
import com.lodz.android.hermes.modules.HermesLog
import com.lodz.android.hermes.mqtt.base.MqttAndroidClient
import com.lodz.android.hermes.mqtt.base.bean.eun.MqttAction
import com.lodz.android.hermes.mqtt.base.contract.ConnectActionListener
import com.lodz.android.hermes.mqtt.base.contract.DisconnectActionListener
import com.lodz.android.hermes.mqtt.base.contract.MqttListener
import com.lodz.android.hermes.mqtt.base.contract.ServiceStartActionListener
import com.lodz.android.hermes.mqtt.base.contract.SubscribeActionListener
import com.lodz.android.hermes.mqtt.base.contract.UnsubscribeActionListener
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.LinkedHashSet

/**
 * MQTT推送客户端实现
 * @author zhouL
 * @date 2019/12/21
 */
class MqttImpl : Hermes {

    /** 日志标签 */
    private var mTag = "HermesLog"
    /** mqtt服务端 */
    private var mMqttClient: MqttAndroidClient? = null
    /** 订阅监听器 */
    private var mOnSubscribeListener: OnSubscribeListener? = null
    /** 解除订阅监听器 */
    private var mOnUnsubscribeListener: OnUnsubscribeListener? = null
    /** 连接监听器 */
    private var mOnConnectListener: OnConnectListener? = null
    /** 发送监听器 */
    private var mOnSendListener: OnSendListener? = null
    /** 订阅主题列表 */
    private var mSubTopics: List<String>? = null
    /** 是否静默 */
    private var isSilent: Boolean = false

    private var mClientKey = ""

    override fun init(context: Context?, url: String, clientId: String?, options: MqttConnectOptions?, listener: ServiceStartActionListener?) {
        if (context == null){
            MainScope().launch { mOnConnectListener?.onConnectFailure(IllegalArgumentException("context is null")) }
            return
        }
        if (clientId == null){
            MainScope().launch { mOnConnectListener?.onConnectFailure(IllegalArgumentException("clientId is null")) }
            return
        }
        mMqttClient = MqttAndroidClient(context)
        mMqttClient?.setCallback(mMqttCallbackExtended)
        val connectOptions = options ?: MqttConnectOptions().apply {
            this.isAutomaticReconnect = true
            this.isCleanSession = false
        }
        mMqttClient?.startService(object : ServiceStartActionListener {
            override fun onSuccess() {
                mClientKey = mMqttClient?.createClientKeyByParam(url, clientId, connectOptions) ?: ""
                if (mClientKey.isEmpty()){
                    MainScope().launch { mOnConnectListener?.onConnectFailure(IllegalArgumentException("clientKey is empty")) }
                }
                listener?.onSuccess()
            }

            override fun onFailure(errorMsg: String, t: Throwable) {
                MainScope().launch { mOnConnectListener?.onConnectFailure(t) }
                listener?.onFailure(errorMsg, t)
            }
        })

    }

    /** mqtt接口回调 */
    private val mMqttCallbackExtended = object: MqttListener {

        override fun connectionLost(clientKey: String, cause: Throwable) {
            val defCause = cause ?: RuntimeException("mqtt connection lost")
            MainScope().launch { mOnConnectListener?.onConnectionLost(defCause) }// 连接丢失
            HermesLog.e(mTag, "mqtt连接丢失 : ${defCause.cause}")
        }

        override fun messageArrived(clientKey: String, topic: String, messageId: String, message: MqttMessage) {
            if (isSilent) {// 静默时不往外推送数据
                return
            }
            // 后台推送的消息到达客户端
            val msg = String(message.payload, Charset.forName("UTF-8"))
            HermesLog.i(mTag, "数据到达 : $msg")
            MainScope().launch { mOnSubscribeListener?.onMsgArrived(topic, msg) }
        }

        override fun deliveryComplete(clientKey: String, token: IMqttDeliveryToken?) {}

        override fun connectComplete(clientKey: String, reconnect: Boolean, serverURI: String) {
            if (reconnect) {
                HermesLog.d(mTag, "mqtt重新连接上服务地址 : $serverURI")
                subscribeTopic()// 重新连接上需要再次订阅主题
            } else {
                HermesLog.d(mTag, "mqtt连接上服务地址 : $serverURI")
            }
            MainScope().launch { mOnConnectListener?.onConnectComplete(reconnect) }
        }
    }

    override fun setSubTopic(topics: List<String>?) {
        val list: MutableList<String> = mSubTopics?.toMutableList() ?: arrayListOf()
        list.addAll(topics ?: arrayListOf())
        mSubTopics = LinkedHashSet(list).toList()
    }


    override fun setOnSubscribeListener(listener: OnSubscribeListener?) {
        mOnSubscribeListener = listener
    }

    override fun setOnUnsubscribeListener(listener: OnUnsubscribeListener?) {
        mOnUnsubscribeListener = listener
    }

    override fun setOnConnectListener(listener: OnConnectListener?) {
        mOnConnectListener = listener
    }

    override fun setOnSendListener(listener: OnSendListener?) {
        mOnSendListener = listener
    }

    override fun sendTopic(topic: String, content: String) {
        try {
            mMqttClient?.publish(mClientKey, topic, content)
            MainScope().launch { mOnSendListener?.onSendComplete(topic, content) }
            HermesLog.i(mTag, "$topic  --- 数据发送 : $content")
        } catch (e: Exception) {
            e.printStackTrace()
            MainScope().launch { mOnSendListener?.onSendFailure(topic, e) }
            HermesLog.e(mTag, "$topic  --- 数据发送失败 : ${e.cause}")
        }
    }

    override fun sendTopic(topic: String, data: ByteArray) {
        try {
            mMqttClient?.publish(mClientKey, topic, data)
            MainScope().launch { mOnSendListener?.onSendComplete(topic, data) }
            HermesLog.i(mTag, "$topic  --- 数据发送 : $data")
        } catch (e: Exception) {
            e.printStackTrace()
            MainScope().launch { mOnSendListener?.onSendFailure(topic, e) }
            HermesLog.e(mTag, "$topic  --- 数据发送失败 : ${e.cause}")
        }
    }

    override fun sendTopic(topic: String, bytes: ByteBuffer) {
        try {
            mMqttClient?.publish(mClientKey,topic, toByteArray(bytes))
            MainScope().launch { mOnSendListener?.onSendComplete(topic, bytes) }
            HermesLog.i(mTag, "$topic  --- 数据发送 : $bytes")
        } catch (e: Exception) {
            e.printStackTrace()
            MainScope().launch { mOnSendListener?.onSendFailure(topic, e) }
            HermesLog.e(mTag, "$topic  --- 数据发送失败 : ${e.cause}")
        }
    }

    override fun connect() {
        try {
            if (isConnected()) {
                return
            }
            mMqttClient?.connect(mClientKey,object : ConnectActionListener{
                override fun onSuccess(action: MqttAction, clientKey: String) {
                    val disconnectedBufferOptions = DisconnectedBufferOptions()
                    disconnectedBufferOptions.isBufferEnabled = true
                    disconnectedBufferOptions.bufferSize = 100
                    disconnectedBufferOptions.isPersistBuffer = false
                    disconnectedBufferOptions.isDeleteOldestMessages = false
                    mMqttClient?.setBufferOpts(clientKey, disconnectedBufferOptions)
                    subscribeTopic()
                }

                override fun onFailure(action: MqttAction, clientKey: String, errorMsg: String, t: Throwable) {
                    MainScope().launch { mOnConnectListener?.onConnectFailure(t) }
                    HermesLog.e(mTag, "mqtt连接失败 : ${t.cause}")
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            MainScope().launch { mOnConnectListener?.onConnectFailure(e) }
            HermesLog.e(mTag, "mqtt连接失败 : ${e.cause}")
        }
    }

    override fun disconnect() {
        try {
            mMqttClient?.disconnect(mClientKey, listener = object : DisconnectActionListener {
                override fun onSuccess(action: MqttAction, clientKey: String) {
                    HermesLog.e(mTag, "断开连接成功 : $clientKey")
                }

                override fun onFailure(action: MqttAction, clientKey: String, errorMsg: String, t: Throwable) {
                    HermesLog.e(mTag, "断开连接失败 : $t")
                }

            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun release() {
        mMqttClient?.release()
    }

    override fun isConnected(): Boolean = mMqttClient != null && mMqttClient?.isConnected(mClientKey) ?: false

    override fun subscribeTopic() {
        val list = mSubTopics
        // 没有可以订阅的主题
        if (list.isNullOrEmpty()) {
            return
        }
        // 未初始化
        if (mMqttClient == null) {
            return
        }
        // 未连接
        if (!isConnected()){
            return
        }
        try {
            mMqttClient?.subscribe(mClientKey,list.toTypedArray(),actionListener= object : SubscribeActionListener{
                override fun onSuccess(action: MqttAction, clientKey: String, topics: Array<String>) {
                    HermesLog.v(mTag, "$topics 订阅成功")
                    MainScope().launch { mOnSubscribeListener?.onSubscribeSuccess(topics) }
                }

                override fun onFailure(action: MqttAction, clientKey: String, topics: Array<String>, errorMsg: String, t: Throwable) {
                    val defException = t ?: RuntimeException("mqtt subscribe failure")
                    HermesLog.e(mTag, "${topics} 订阅失败 : ${defException.cause}")
                    MainScope().launch { mOnSubscribeListener?.onSubscribeFailure(topics, defException) }
                }

            })
        } catch (e: Exception) {
            e.printStackTrace()
            HermesLog.e(mTag, "订阅失败 : ${e.cause}")
            MainScope().launch { mOnSubscribeListener?.onSubscribeFailure(arrayOf("all"), e) }
        }
    }

    override fun unsubscribe(topics: List<String>?) {
        if (topics.isNullOrEmpty()) {
            return
        }
        // 未初始化
        if (mMqttClient == null) {
            return
        }
        try {
            topics.forEach { topic ->
                mMqttClient?.unsubscribe(mClientKey, topic, object : UnsubscribeActionListener{
                    override fun onSuccess(action: MqttAction, clientKey: String, topics: Array<String>) {
                        HermesLog.v(mTag, "$topics 解除订阅成功")
                        val list = mSubTopics?.toMutableList() ?: arrayListOf()
                        mSubTopics = list.filter {
                            var isSave = true
                            for (t in topics) {
                                if (it == t){
                                    isSave = false
                                    break
                                }
                            }
                            isSave
                        }
                        MainScope().launch { mOnUnsubscribeListener?.onUnsubscribeSuccess(topics) }
                    }

                    override fun onFailure(action: MqttAction, clientKey: String, topics: Array<String>, errorMsg: String, t: Throwable) {
                        HermesLog.e(mTag, "$topics 解除订阅失败 : ${t.cause}")
                        MainScope().launch { mOnUnsubscribeListener?.onUnsubscribeFailure(topics, t) }
                    }
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
            HermesLog.e(mTag, "解除订阅失败 : ${e.cause}")
            MainScope().launch { mOnUnsubscribeListener?.onUnsubscribeFailure(arrayOf("all"), e) }
        }
    }

    override fun getSubscribeTopic(): List<String> = mSubTopics ?: arrayListOf()

    override fun setTag(tag: String) {
        if (tag.isNotEmpty()) {
            mTag = tag
        }
    }

    override fun setSilent(isSilent: Boolean) {
        this.isSilent = isSilent
    }

    override fun isSilent(): Boolean = this.isSilent

    private fun toByteArray(buffer: ByteBuffer): ByteArray {
        buffer.flip()
        val length = buffer.limit() - buffer.position()

        val byte = ByteArray(length)
        for (i in byte.indices) {
            byte[i] = buffer.get()
        }
        return byte;
    }
}