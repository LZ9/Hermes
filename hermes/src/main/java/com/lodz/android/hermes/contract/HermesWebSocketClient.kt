package com.lodz.android.hermes.contract

import android.content.Context
import com.lodz.android.hermes.ws.client.OnConnectListener
import com.lodz.android.hermes.ws.client.OnSendListener
import com.lodz.android.hermes.ws.client.OnSubscribeListener
import org.java_websocket.drafts.Draft
import org.java_websocket.drafts.Draft_6455
import java.nio.ByteBuffer

/**
 * WebSocket客户端
 * @author zhouL
 * @date 2019/12/20
 */
interface HermesWebSocketClient : Hermes {

    override fun init(context: Context): HermesWebSocketClient

    override fun setLogTag(tag: String): HermesWebSocketClient

    override fun setSilent(isSilent: Boolean): HermesWebSocketClient

    override fun setPrintLog(isPrint: Boolean): HermesWebSocketClient

    /** 创建对象，连接路径[url]，是否自动重连[isAutomaticReconnect]，连接方案[protocolDraft]，头信息[httpHeaders]，连接超时[connectTimeout] */
    fun build(
        url: String,
        isAutomaticReconnect: Boolean,
        protocolDraft: Draft = Draft_6455(),
        httpHeaders: Map<String, String>? = null,
        connectTimeout: Int = 0
    ): HermesWebSocketClient

    /** 发送数据[data] */
    fun sendData(data: String)

    /** 发送数据[data] */
    fun sendData(data: ByteBuffer)

    /** 发送数据[data] */
    fun sendData(data: ByteArray)

    /** 设置连接监听器，监听器[listener] */
    fun setOnConnectListener(listener: OnConnectListener?): HermesWebSocketClient

    /** 设置发送监听器，监听器[listener] */
    fun setOnSendListener(listener: OnSendListener?): HermesWebSocketClient

    /** 设置订阅监听器，监听器[listener] */
    fun setOnSubscribeListener(listener: OnSubscribeListener?): HermesWebSocketClient

    /** 设置重连间隔时间[millisecond]，默认1分钟（60x1000） */
    fun setReconnectInterval(millisecond: Long): HermesWebSocketClient
}