package com.lodz.android.hermes.modules

import android.content.Context
import com.lodz.android.hermes.contract.Hermes
import com.lodz.android.hermes.contract.OnConnectListener
import com.lodz.android.hermes.contract.OnSendListener
import com.lodz.android.hermes.contract.OnSubscribeListener
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * MQTT推送客户端实现
 * @author zhouL
 * @date 2019/12/21
 */
class HermesImpl : Hermes {

    /** 日志标签 */
    private var mTag = "HermesLog"
    /** mqtt服务端 */
    private var mMqttClient: MqttAndroidClient? = null
    /** mqtt连接配置 */
    private var mMqttConnectOptions: MqttConnectOptions? = null
    /** 订阅监听器 */
    private var mOnSubscribeListener: OnSubscribeListener? = null
    /** 连接监听器 */
    private var mOnConnectListener: OnConnectListener? = null
    /** 发送监听器 */
    private var mOnSendListener: OnSendListener? = null
    /** 订阅主题列表 */
    private var mSubTopics: List<String>? = null
    /** 是否静默 */
    private var isSilent: Boolean = false

    override fun init(context: Context?, url: String, clientId: String?, options: MqttConnectOptions?) {
        mMqttClient = MqttAndroidClient(context, url, clientId)
        mMqttClient?.setCallback(mMqttCallbackExtended)
        mMqttConnectOptions = options
        if (mMqttConnectOptions == null) {
            mMqttConnectOptions = MqttConnectOptions()
            mMqttConnectOptions?.isAutomaticReconnect = true
            mMqttConnectOptions?.isCleanSession = false
        }
    }

    /** mqtt接口回调 */
    private val mMqttCallbackExtended = object : MqttCallbackExtended {

        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            if (reconnect) {
                HermesLog.d(mTag, "mqtt重新连接上服务地址 : $serverURI")
                subscribeTopic()// 重新连接上需要再次订阅主题
            } else {
                HermesLog.d(mTag, "mqtt连接上服务地址 : $serverURI")
            }
            mOnConnectListener?.onConnectComplete(reconnect)
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            if (isSilent) {// 静默时不往外推送数据
                return
            }
            if (message == null) {
                HermesLog.i(mTag, "数据到达 : null")
                return
            }
            // 后台推送的消息到达客户端
            val msg = String(message.payload, Charset.forName("UTF-8"))
            HermesLog.i(mTag, "数据到达 : $msg")
            mOnSubscribeListener?.onMsgArrived(topic ?: "", msg)
        }

        override fun connectionLost(cause: Throwable?) {
            val defCause = cause ?: RuntimeException("mqtt connection lost")
            mOnConnectListener?.onConnectionLost(defCause)// 连接丢失
            HermesLog.e(mTag, "mqtt连接丢失 : ${defCause.cause}")
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
    }

    override fun setSubTopic(topics: List<String>?) {
        mSubTopics = topics
    }

    override fun setOnSubscribeListener(listener: OnSubscribeListener?) {
        mOnSubscribeListener = listener
    }

    override fun setOnConnectListener(listener: OnConnectListener?) {
        mOnConnectListener = listener
    }

    override fun setOnSendListener(listener: OnSendListener?) {
        mOnSendListener = listener
    }

    override fun sendTopic(topic: String, content: String) {
        try {
            mMqttClient?.publish(topic, MqttMessage(content.toByteArray()))
            mOnSendListener?.onSendComplete(topic, content)
            HermesLog.i(mTag, "$topic  --- 数据发送 : $content")
        } catch (e: Exception) {
            e.printStackTrace()
            mOnSendListener?.onSendFailure(topic, e)
            HermesLog.e(mTag, "$topic  --- 数据发送失败 : ${e.cause}")
        }
    }

    override fun sendTopic(topic: String, data: ByteArray) {
        try {
            mMqttClient?.publish(topic, MqttMessage(data))
            mOnSendListener?.onSendComplete(topic, data)
            HermesLog.i(mTag, "$topic  --- 数据发送 : $data")
        } catch (e: Exception) {
            e.printStackTrace()
            mOnSendListener?.onSendFailure(topic, e)
            HermesLog.e(mTag, "$topic  --- 数据发送失败 : ${e.cause}")
        }
    }

    override fun sendTopic(topic: String, bytes: ByteBuffer) {
        try {
            mMqttClient?.publish(topic, MqttMessage(toByteArray(bytes)))
            mOnSendListener?.onSendComplete(topic, bytes)
            HermesLog.i(mTag, "$topic  --- 数据发送 : $bytes")
        } catch (e: Exception) {
            e.printStackTrace()
            mOnSendListener?.onSendFailure(topic, e)
            HermesLog.e(mTag, "$topic  --- 数据发送失败 : ${e.cause}")
        }
    }

    override fun connect() {
        try {
            if (isConnected()) {
                return
            }
            mMqttClient?.connect(mMqttConnectOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    val disconnectedBufferOptions = DisconnectedBufferOptions()
                    disconnectedBufferOptions.isBufferEnabled = true
                    disconnectedBufferOptions.bufferSize = 100
                    disconnectedBufferOptions.isPersistBuffer = false
                    disconnectedBufferOptions.isDeleteOldestMessages = false
                    mMqttClient?.setBufferOpts(disconnectedBufferOptions)
                    subscribeTopic()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    val defException = exception ?: RuntimeException("mqtt connection failure")
                    mOnConnectListener?.onConnectFailure(defException)
                    HermesLog.e(mTag, "mqtt连接失败 : ${defException.cause}")
                }
            })

        } catch (e: Exception) {
            e.printStackTrace()
            mOnConnectListener?.onConnectFailure(e)
            HermesLog.e(mTag, "mqtt连接失败 : ${e.cause}")
        }
    }

    override fun disconnect() {
        try {
            mMqttClient?.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun isConnected(): Boolean = mMqttClient != null && mMqttClient?.isConnected ?: false

    override fun subscribeTopic() {
        val list = mSubTopics
        // 没有可以订阅的主题
        if (list.isNullOrEmpty()) {
            return
        }
        if (mMqttClient == null) {
            return
        }
        try {
            list.forEach { topic ->
                mMqttClient?.subscribe(topic, 0, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        HermesLog.v(mTag, "$topic 订阅成功")
                        mOnSubscribeListener?.onSubscribeSuccess(topic)
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        val defException = exception ?: RuntimeException("mqtt subscribe failure")
                        HermesLog.e(mTag, "$topic 订阅失败 : ${defException.cause}")
                        mOnSubscribeListener?.onSubscribeFailure(topic, defException)
                    }
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
            HermesLog.e(mTag, "订阅失败 : ${e.cause}")
            mOnSubscribeListener?.onSubscribeFailure("all", e)
        }
    }

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