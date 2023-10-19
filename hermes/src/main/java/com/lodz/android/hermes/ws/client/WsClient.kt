package com.lodz.android.hermes.ws.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ServerHandshake
import java.net.SocketException
import java.net.URI
import java.nio.ByteBuffer

/**
 * WebSocket客户端
 * @author zhouL
 * @date 2019/12/21
 */
open class WsClient(
    url: String,
    protocolDraft: Draft = Draft_6455(),
    httpHeaders: Map<String, String>? = null,
    connectTimeout: Int = 0
) : WebSocketClient(URI.create(url), protocolDraft, httpHeaders, connectTimeout) {

    private var mListener: OnWebSocketListener? = null

    override fun onOpen(handshakeData: ServerHandshake) {
        doOpen(handshakeData)
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        doClose(code, reason ?: "", remote)
    }

    override fun onMessage(message: String?) {
        doMessage(message ?: "")
    }

    override fun onMessage(bytes: ByteBuffer?) {
        bytes?.let { doMessage(it) }
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

    private fun doOpen(handshakeData: ServerHandshake) {
        MainScope().launch { mListener?.onOpen(handshakeData) }
    }

    private fun doMessage(message: String) {
        MainScope().launch(Dispatchers.Main) { mListener?.onMessage(message) }
    }

    private fun doMessage(message: ByteBuffer) {
        MainScope().launch(Dispatchers.Main) { mListener?.onMessage(message) }
    }

    private fun doClose(code: Int, reason: String, remote: Boolean) {
        MainScope().launch(Dispatchers.Main) { mListener?.onClose(code, reason, remote) }
    }


    private fun doError(e: Exception) {
        MainScope().launch(Dispatchers.Main) { mListener?.onError(e) }
    }

    /** 设置监听器[listener] */
    fun setOnWebSocketListener(listener: OnWebSocketListener) {
        mListener = listener
    }
}