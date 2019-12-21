package com.lodz.android.hermes.modules

import com.lodz.android.hermes.contract.OnWebSocketListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.SocketException
import java.net.URI

/**
 * WebSocket客户端
 * @author zhouL
 * @date 2019/12/21
 */
class WsClient(url: String) : WebSocketClient(URI.create(url)) {

    private var mListener: OnWebSocketListener? = null

    override fun onOpen(handshakedata: ServerHandshake?) {
        doOpen(handshakedata)
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        doClose(code, reason ?: "", remote)
    }

    override fun onMessage(message: String?) {
        doMessage(message ?: "")
    }

    override fun onError(ex: Exception?) {
        doError(ex ?: SocketException("exception is null"))
    }

    override fun connect() {
        try {
            super.connect()
        } catch (e: Exception) {
            e.printStackTrace()
            doError(e)
        }
    }

    override fun reconnect() {
        val thread = Thread(this)
        thread.start()
        super.reconnect()
    }

    private fun doOpen(handshakedata: ServerHandshake?) {
        GlobalScope.launch(Dispatchers.Main) {
            mListener?.onOpen(handshakedata)
        }
    }

    private fun doMessage(message: String) {
        GlobalScope.launch(Dispatchers.Main) {
            mListener?.onMessage(message)
        }
    }

    private fun doClose(code: Int, reason: String, remote: Boolean) {
        GlobalScope.launch(Dispatchers.Main) {
            mListener?.onClose(code, reason, remote)
        }
    }


    private fun doError(e: Exception) {
        GlobalScope.launch(Dispatchers.Main) {
            mListener?.onError(e)
        }
    }

    /** 设置监听器[listener] */
    fun setOnWebSocketListener(listener: OnWebSocketListener) {
        mListener = listener
    }
}