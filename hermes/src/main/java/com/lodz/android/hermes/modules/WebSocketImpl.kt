package com.lodz.android.hermes.modules

import android.content.Context
import com.lodz.android.hermes.contract.*
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.java_websocket.framing.CloseFrame
import org.java_websocket.handshake.ServerHandshake
import java.net.SocketException

/**
 * WebSocket实现
 * @author zhouL
 * @date 2019/12/21
 */
class WebSocketImpl : Hermes {

    /** 日志标签 */
    private var mTag = "WebSocketLog"
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

    override fun init(context: Context?, url: String, clientId: String?, options: MqttConnectOptions?) {
        mUrl = url
        isAutomaticReconnect = options?.isAutomaticReconnect ?: true
        mWsClient = WsClient(url)
        mWsClient?.setOnWebSocketListener(object : OnWebSocketListener {
            override fun onOpen(handshakedata: ServerHandshake?) {
                PrintLog.d(mTag, "WebSocket通道打开成功 ： ${handshakedata?.httpStatusMessage}")
                mOnConnectListener?.onConnectComplete(false)
            }

            override fun onMessage(message: String) {
                PrintLog.i(mTag, "数据到达 ： $message")
                mOnSubscribeListener?.onMsgArrived("", message)
            }

            override fun onClose(code: Int, reason: String, remote: Boolean) {
                PrintLog.v(mTag, "WebSocket连接关闭 -> code : $code ; reason : $reason ; remote : $remote")
                var defReason = reason
                if (code == CloseFrame.NORMAL) {
                    defReason = "连接断开"
                }
                if (code == CloseFrame.ABNORMAL_CLOSE) {
                    mOnConnectListener?.onConnectionLost(SocketException(defReason))
                    return
                }
                mOnConnectListener?.onConnectFailure(SocketException(defReason))
            }

            override fun onError(e: Exception) {
                PrintLog.e(mTag, "WebSocket连接异常 ： ${e.message}")
                mOnConnectListener?.onConnectFailure(e)
            }
        })

    }

    override fun setSubTopic(topics: List<String>?) {}

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
            PrintLog.i(mTag, "$topic  --- 数据发送 : $content")
            mWsClient?.send(content)
            mOnSendListener?.onSendComplete(topic, content)
        } catch (e: Exception) {
            e.printStackTrace()
            PrintLog.e(mTag, "$topic  --- 数据发送失败 : ${e.cause}")
            mOnSendListener?.onSendFailure(topic, e)
        }
    }

    override fun connect() {
        if (mUrl.isEmpty()) {
            return
        }
        if (mWsClient == null) {
            init(null, mUrl, null, null)
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
        mJob = null
        mWsClient?.close()
        mWsClient = null
    }

    override fun isConnected(): Boolean = mWsClient != null && mWsClient?.isOpen ?: false

    override fun subscribeTopic() {}

    override fun setTag(tag: String) {
        if (tag.isNotEmpty()){
            mTag = tag
        }
    }

    /** 设置重连 */
    private fun setReconnect() {
        mJob = GlobalScope.launch(Dispatchers.IO) {
            try {
                PrintLog.i(mTag, "保活进程启动")
                while (true) {
                    delay(60 * 1000)
                    if (mWsClient == null) {
                        mJob?.cancel()
                        return@launch
                    }
                    if (!isConnected()) {
                        PrintLog.d(mTag, "通道重连")
                        mWsClient?.close()
                        mWsClient = null
                        delay(2000)
                        connect()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                PrintLog.e(mTag, "保活进程中断")
            }
        }
    }

}