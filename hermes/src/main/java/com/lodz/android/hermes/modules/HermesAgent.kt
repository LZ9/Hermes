package com.lodz.android.hermes.modules

import android.content.Context
import androidx.annotation.IntDef
import com.lodz.android.hermes.contract.Hermes
import com.lodz.android.hermes.contract.OnConnectListener
import com.lodz.android.hermes.contract.OnSendListener
import com.lodz.android.hermes.contract.OnSubscribeListener
import org.eclipse.paho.client.mqttv3.MqttConnectOptions

/**
 * 推送代理
 * @author zhouL
 * @date 2019/12/21
 */
class HermesAgent private constructor() {

    /** 推送类型 */
    @IntDef(MQTT, WEB_SOCKET)
    @Retention(AnnotationRetention.SOURCE)
    annotation class ConnectType

    companion object {
        /** mqtt订阅 */
        const val MQTT = 1
        /** WebSocket订阅 */
        const val WEB_SOCKET = 2

        /** 创建 */
        @JvmStatic
        fun create(): HermesAgent = HermesAgent()
    }

    /** 服务端地址 */
    private var mUrl = ""
    /** 客户端id */
    private var mClientId = ""
    /** 订阅主题列表 */
    private var mSubTopics: List<String>? = null
    /** 订阅回调 */
    private var mOnSubscribeListener: OnSubscribeListener? = null
    /** 连接监听器 */
    private var mOnConnectListener: OnConnectListener? = null
    /** 发送监听器 */
    private var mOnSendListener: OnSendListener? = null
    /** 连接配置 */
    private var mMqttConnectOptions: MqttConnectOptions? = null
    /** 日志标签 */
    private var mTag = ""
    /** 连接类型 */
    @ConnectType
    private var mType = MQTT

    /** 设置连接类型[type] */
    fun setConnectType(@ConnectType type: Int): HermesAgent = this.apply {
        this.mType = type
    }

    /** 设置后台地址[url] */
    fun setUrl(url: String): HermesAgent = this.apply {
        this.mUrl = url
    }

    /** 设置客户端id[clientId] */
    fun setClientId(clientId: String): HermesAgent = this.apply {
        this.mClientId = clientId
    }

    /** 设置是否打印日志[isPrint] */
    fun setPrintLog(isPrint: Boolean): HermesAgent = this.apply {
        HermesLog.setPrint(isPrint)
    }

    /** 设置日志标签[tag] */
    fun setLogTag(tag: String): HermesAgent = this.apply {
        if (tag.isNotEmpty()) {
            this.mTag = tag
        }
    }

    /** 设置多个订阅主题[subTopics] */
    fun setSubTopics(subTopics: List<String>): HermesAgent = this.apply {
        this.mSubTopics = subTopics
    }

    /** 设置订阅主题[subTopic] */
    fun setSubTopic(subTopic: String): HermesAgent = this.apply {
        this.mSubTopics = arrayListOf(subTopic)
    }

    /** 设置推送监听器[listener] */
    fun setOnSubscribeListener(listener: OnSubscribeListener): HermesAgent = this.apply {
        this.mOnSubscribeListener = listener
    }

    /** 设置连接监听器[listener] */
    fun setOnConnectListener(listener: OnConnectListener): HermesAgent = this.apply {
        this.mOnConnectListener = listener
    }

    /** 设置发送监听器[listener] */
    fun setOnSendListener(listener: OnSendListener): HermesAgent = this.apply {
        this.mOnSendListener = listener
    }

    /** 设置连接配置[options] */
    fun setConnectOptions(options: MqttConnectOptions): HermesAgent = this.apply {
        this.mMqttConnectOptions = options
    }

    /** 构建推送客户端并自动连接 */
    fun buildConnect(context: Context): Hermes {
        val client = build(context)
        client.connect()
        return client
    }

    /** 构建推送客户端 */
    fun build(context: Context): Hermes {
        if (mUrl.isEmpty()) {
            throw NullPointerException("push url is null")
        }
        if (mType == MQTT && mClientId.isEmpty()) {
            throw NullPointerException("push clientId is null")
        }
        val client = when (mType) {
            MQTT -> HermesImpl()
            WEB_SOCKET -> WebSocketImpl()
            else -> null
        } ?: throw NullPointerException("unsupport connect type")
        client.init(context.applicationContext, mUrl, mClientId, mMqttConnectOptions)
        client.setSubTopic(mSubTopics)
        client.setOnSubscribeListener(mOnSubscribeListener)
        client.setOnConnectListener(mOnConnectListener)
        client.setOnSendListener(mOnSendListener)
        client.setTag(mTag)
        return client
    }
}