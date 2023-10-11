package com.lodz.android.hermes.ws.client

import org.java_websocket.handshake.ServerHandshake

/**
 * WebSocket监听器
 * @author zhouL
 * @date 2019/12/21
 */
interface OnWebSocketListener {

    fun onOpen(handshakedata: ServerHandshake?)

    fun onMessage(message: String)

    fun onClose(code: Int, reason: String, remote: Boolean)

    fun onError(e: Exception)
}