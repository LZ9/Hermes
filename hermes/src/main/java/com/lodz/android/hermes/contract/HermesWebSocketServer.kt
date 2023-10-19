package com.lodz.android.hermes.contract

import android.content.Context
import com.lodz.android.hermes.ws.server.OnWebSocketServerListener
import org.java_websocket.WebSocket
import org.java_websocket.drafts.Draft
import java.net.InetSocketAddress
import java.nio.ByteBuffer

/**
 * WebSocket服务端
 * @author zhouL
 * @date 2019/12/20
 */
interface HermesWebSocketServer : Hermes {

    override fun init(context: Context): HermesWebSocketServer

    override fun setLogTag(tag: String): HermesWebSocketServer

    override fun setSilent(isSilent: Boolean): HermesWebSocketServer

    override fun setPrintLog(isPrint: Boolean): HermesWebSocketServer

    /** 创建对象，IP地址[ip]，端口[port]，WebSocketServer.WebSocketWorkers数量[decoderCount]，支持的协议列表[drafts]，连接容器[connectionsContainer] */
    fun build(
        ip: String,
        port: Int,
        decoderCount: Int = Runtime.getRuntime().availableProcessors(),
        drafts: List<Draft>? = null,
        connectionsContainer: Collection<WebSocket> = HashSet(),
    ): HermesWebSocketServer

    /** 获取服务端地址对象 */
    fun getInetSocketAddress(): InetSocketAddress?

    /** 设置服务端监听器[listener] */
    fun setOnWebSocketServerListener(listener: OnWebSocketServerListener?): HermesWebSocketServer

    /** 设置连接丢失的检查间隔时间[seconds]，默认10秒 */
    fun setConnectionLostTimeout(seconds: Int): HermesWebSocketServer

    /** 关闭所有用户连接 */
    fun closeAllUser()

    /** 获取所有连接的WebSocket对象 */
    fun getAllWebSocket(): HashMap<Int, Pair<String, WebSocket>>

    /** 发送消息[msg]给所有连接的用户 */
    fun sendMsgToAll(msg: String)

    /** 发送消息[msg]给所有连接的用户 */
    fun sendMsgToAll(msg: ByteBuffer)

    /** 发送消息[msg]给所有连接的用户 */
    fun sendMsgToAll(msg: ByteArray)

    /** 发送消息[msg]给指定的WebSocket连接[ws] */
    fun sendMsg(ws: WebSocket?, msg: String)

    /** 发送消息[msg]给指定的WebSocket连接[ws] */
    fun sendMsg(ws: WebSocket?, msg: ByteBuffer)

    /** 发送消息[msg]给指定的WebSocket连接[ws] */
    fun sendMsg(ws: WebSocket?, msg: ByteArray)

    /** 在线用户数量 */
    fun onlineCount(): Int
}