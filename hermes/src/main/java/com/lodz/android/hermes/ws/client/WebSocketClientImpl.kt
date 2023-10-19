package com.lodz.android.hermes.ws.client

import android.content.Context
import com.lodz.android.hermes.contract.*
import com.lodz.android.hermes.modules.HermesLog
import com.lodz.android.hermes.utils.IoScope
import kotlinx.coroutines.*
import org.java_websocket.drafts.Draft
import org.java_websocket.handshake.ServerHandshake
import java.net.SocketException
import java.nio.ByteBuffer

/**
 * WebSocket客户端实现
 * @author zhouL
 * @date 2019/12/21
 */
class WebSocketClientImpl : HermesWebSocketClient {

    /** 日志标签 */
    private var mTag = "WebSocketLog"
    /** 上下文 */
    private var mContext: Context? = null
    /** WebSocket客户端 */
    private var mWsClient: WsClient? = null
    /** 订阅监听器 */
    private var mOnSubscribeListener: OnSubscribeListener? = null
    /** 连接监听器 */
    private var mOnConnectListener: OnConnectListener? = null
    /** 发送监听器 */
    private var mOnSendListener: OnSendListener? = null
    /** 路径 */
    private var mUrl: String = ""
    /** 是否自动重连 */
    private var isAutomaticReconnect = true
    /** 是否需要重连 */
    private var mJob: Job? = null
    /** 是否静默 */
    private var isSilent: Boolean = false
    /** 重连间隔时间 */
    private var mReconnectIntervalTime: Long = 60 * 1000

    override fun init(context: Context): HermesWebSocketClient = apply { mContext = context }

    override fun build(
        url: String,
        isAutomaticReconnect: Boolean,
        protocolDraft: Draft,
        httpHeaders: Map<String, String>?,
        connectTimeout: Int
    ): HermesWebSocketClient {
        if (mWsClient != null) {
            release()
        }
        mUrl = url
        this.isAutomaticReconnect = isAutomaticReconnect
        mWsClient = WsClient(url, protocolDraft, httpHeaders, connectTimeout)
        mWsClient?.setOnWebSocketListener(object :OnWebSocketListener{
            override fun onOpen(handshakeData: ServerHandshake) {
                HermesLog.d(mTag, "WebSocket通道已打开 ： ${handshakeData.httpStatusMessage}")
                MainScope().launch { mOnConnectListener?.onConnectComplete() }
            }

            override fun onMessage(message: String) {
                doOnMessage { ip ->
                    HermesLog.i(mTag, "$ip -> 数据到达 ： $message")
                    mOnSubscribeListener?.onMsgArrived(ip, message)
                }
            }

            override fun onMessage(message: ByteBuffer) {
                doOnMessage { ip ->
                    HermesLog.i(mTag, "$ip -> 数据到达 ： $message")
                    mOnSubscribeListener?.onMsgArrived(ip, message)
                }
            }

            override fun onClose(code: Int, reason: String, remote: Boolean) {
                HermesLog.v(mTag, "WebSocket连接关闭 -> code : $code ; reason : $reason ; remote : $remote")
                MainScope().launch { mOnConnectListener?.onConnectionLost(SocketException(reason.ifEmpty { "连接断开" })) }
            }

            override fun onError(e: Exception) {
                HermesLog.e(mTag, "WebSocket连接异常 ： ${e.message}")
                MainScope().launch { mOnConnectListener?.onConnectFailure(e) }
            }
        })
        return this
    }

    private fun doOnMessage(block: (ip: String) -> Unit) {
        MainScope().launch {
            if (isSilent) {// 静默时不往外推送数据
                return@launch
            }
            launch(Dispatchers.IO) {
                val ip = mWsClient?.remoteSocketAddress?.hostName ?: ""
                launch(Dispatchers.Main) { block.invoke(ip) }
            }
        }
    }

    override fun sendData(data: String) {
        HermesLog.i(mTag, "数据发送 : $data")
        doSendData {
            mWsClient?.send(data)
            IoScope().launch {
                val ip = mWsClient?.remoteSocketAddress?.hostName ?: ""
                launch(Dispatchers.Main) { mOnSendListener?.onSendComplete(ip, data) }
            }
        }
    }

    override fun sendData(data: ByteBuffer) {
        HermesLog.i(mTag, "数据发送 : $data")
        doSendData {
            mWsClient?.send(data)
            IoScope().launch {
                val ip = mWsClient?.remoteSocketAddress?.hostName ?: ""
                launch(Dispatchers.Main) { mOnSendListener?.onSendComplete(ip, data) }
            }
        }
    }

    override fun sendData(data: ByteArray) {
        sendData(ByteBuffer.wrap(data))
    }

    private fun doSendData(block: () -> Unit) {
        try {
            block.invoke()
        } catch (e: Exception) {
            e.printStackTrace()
            HermesLog.e(mTag, "数据发送失败 : ${e.cause}")
            MainScope().launch { mOnSendListener?.onSendFailure(e) }
        }
    }

    override fun connect() {
        if (mUrl.isEmpty()) {
            MainScope().launch { mOnConnectListener?.onConnectFailure(IllegalArgumentException("url is empty")) }
            return
        }
        if (mWsClient == null) {
            build(mUrl, isAutomaticReconnect)
        }
        mWsClient?.connect()
        mJob?.cancel()
        mJob = null
        if (isAutomaticReconnect){
            setReconnect()
        }
    }

    override fun disconnect() {
        mJob?.cancel()
        mWsClient?.close()
    }

    override fun release() {
        disconnect()
        mJob = null
        mWsClient = null
    }

    override fun isConnected(): Boolean = mWsClient?.isOpen ?: false

    override fun setLogTag(tag: String): HermesWebSocketClient = apply { if (tag.isNotEmpty()) { mTag = tag } }

    override fun setSilent(isSilent: Boolean): HermesWebSocketClient = apply { this.isSilent = isSilent }

    override fun setPrintLog(isPrint: Boolean): HermesWebSocketClient = apply { HermesLog.setPrint(isPrint) }

    override fun isSilent(): Boolean = this.isSilent

    override fun setOnConnectListener(listener: OnConnectListener?): HermesWebSocketClient = apply { mOnConnectListener = listener }

    override fun setOnSubscribeListener(listener: OnSubscribeListener?): HermesWebSocketClient = apply { mOnSubscribeListener = listener }
    override fun setReconnectInterval(millisecond: Long): HermesWebSocketClient = apply {
        if (millisecond > 0){
            mReconnectIntervalTime = millisecond
        }
    }

    override fun setOnSendListener(listener: OnSendListener?): HermesWebSocketClient = apply { mOnSendListener = listener }

    /** 设置重连 */
    private fun setReconnect() {
        mJob = IoScope().launch {
            try {
                HermesLog.v(mTag, "保活线程：启动")
                while (true) {
                    delay(mReconnectIntervalTime)
                    if (mWsClient == null) {
                        HermesLog.v(mTag, "保活线程：WsClient为空")
                        mJob?.cancel()
                        return@launch
                    }
                    if (!isConnected()) {
                        HermesLog.v(mTag, "保活线程：通道重连")
                        mWsClient?.close()
                        mWsClient = null
                        delay(2000)
                        connect()
                        return@launch
                    }
                    HermesLog.v(mTag, "保活线程：连接正常")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                HermesLog.e(mTag, "保活线程：中断 -> ${e.cause}")
            }
        }
    }


}