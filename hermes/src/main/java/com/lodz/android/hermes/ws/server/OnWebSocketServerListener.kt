package com.lodz.android.hermes.ws.server

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import java.nio.ByteBuffer

/**
 * WebSocket服务端监听器
 * @author zhouL
 * @date 2019/12/21
 */
interface OnWebSocketServerListener {

    /** 打开 */
    fun onOpen(ws: WebSocket?, ip: String, handshake: ClientHandshake?)

    /** 关闭 */
    fun onClose(ws: WebSocket?, ip: String, code: Int, reason: String, isRemote: Boolean)

    /** 消息接收 */
    fun onMessage(ws: WebSocket?, ip: String, msg: String)

    /** 消息接收 */
    fun onMessage(ws: WebSocket?, ip: String, msg: ByteBuffer?)

    /** 异常 */
    fun onError(ws: WebSocket?, ip: String, e: Exception)

    /** 开始 */
    fun onStart()
}