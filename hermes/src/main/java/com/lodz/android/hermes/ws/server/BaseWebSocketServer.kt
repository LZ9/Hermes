package com.lodz.android.hermes.ws.server

import com.lodz.android.hermes.modules.HermesLog
import com.lodz.android.hermes.utils.IoScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.java_websocket.WebSocket
import org.java_websocket.drafts.Draft
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.ByteBuffer

/**
 * WebSocket服务端基类
 * @author zhouL
 * @date 2021/6/30
 */
open class BaseWebSocketServer @JvmOverloads constructor(
    host: String,
    port: Int,
    decoderCount: Int = Runtime.getRuntime().availableProcessors(),
    drafts: List<Draft>? = null,
    connectionsContainer: Collection<WebSocket> = HashSet(),
) : WebSocketServer(InetSocketAddress(host, port), decoderCount, drafts, connectionsContainer) {

    /** 监听器 */
    private var mListener: OnWebSocketServerListener? = null
    /** 连接上的用户缓存 */
    private val mConnectUserMap: HashMap<Int, Pair<String, WebSocket>> = HashMap()
    /** 日志标签 */
    private var mLogTag = "WebSocketServer"

    /** 设置日志标签[tag] */
    fun setLogTag(tag: String) {
        mLogTag = tag
    }

    /** 设置是否打印[isPrint] 日志*/
    fun setPrintLog(isPrint: Boolean) {
        HermesLog.setPrint(isPrint)
    }

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        IoScope().launch {
            val ip = conn?.remoteSocketAddress?.hostName ?: ""
            HermesLog.d(mLogTag, "用户[$ip]已连接上")
            putUser(ip, conn)
            launch(Dispatchers.Main) { mListener?.onOpen(conn, ip, handshake) }
        }
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        val pair = getWebSocket(conn?.hashCode() ?: 0)
        HermesLog.i(mLogTag, "用户[${pair?.first}]已断开连接")
        removeUser(conn)
        val reasonDef = if (reason.isNullOrEmpty()) "用户断开连接" else reason
        MainScope().launch { mListener?.onClose(conn, pair?.first ?: "", code, reasonDef, remote) }
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        val ip = conn?.remoteSocketAddress?.hostName ?: ""
        HermesLog.i(mLogTag, "用户[$ip]发来消息 : $message")
        MainScope().launch { mListener?.onMessage(conn, ip, message ?: "") }
    }

    override fun onMessage(conn: WebSocket?, message: ByteBuffer?) {
        val ip = conn?.remoteSocketAddress?.hostName ?: ""
        HermesLog.i(mLogTag, "用户[$ip]发来消息 : $message")
        MainScope().launch { mListener?.onMessage(conn, ip, message) }
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        val e = ex ?: SocketException("WebSocket error but exception is null")
        if (conn == null) {
            MainScope().launch { mListener?.onError(conn, "", e) }
            return
        }
        val pair = getWebSocket(conn.hashCode())
        HermesLog.e(mLogTag, "[$${pair?.first}]连接异常 : ${e.cause}")
        MainScope().launch { mListener?.onError(conn, pair?.first ?: "", e) }
    }

    override fun onStart() {
        HermesLog.d(mLogTag, "服务器已启动")
        MainScope().launch { mListener?.onStart() }
    }

    override fun stop() {
        closeAllUser()
        super.stop()
    }

    /** 设置WebSocket服务端监听器[listener] */
    fun setOnWebSocketServerListener(listener: OnWebSocketServerListener?) {
        mListener = listener
    }

    /** 存储用户连接[ws] */
    private fun putUser(ws: WebSocket?) {
        ws?.let { putUser(it.remoteSocketAddress?.hostName ?: "", it) }
    }

    /** 存储用户[ip]地址和连接[ws] */
    private fun putUser(ip: String, ws: WebSocket?) {
        ws?.let {
            val hashCode = it.hashCode()
            if (!mConnectUserMap.containsKey(hashCode)) {
                mConnectUserMap[hashCode] = Pair(ip, it)
            }
        }
    }

    /** 移除用户连接[ws] */
    private fun removeUser(ws: WebSocket?) {
        ws?.let { mConnectUserMap.remove(it.hashCode()) }
    }

    /** 关闭所有用户连接 */
    fun closeAllUser() {
        for (ws in connections) {
            if (ws.isOpen) {
                ws.close()
            }
        }
        mConnectUserMap.clear()
    }

    /** 根据哈希编码[hashCode]，获取本地缓存的WebSocket对象 */
    private fun getWebSocket(hashCode: Int): Pair<String, WebSocket>? = mConnectUserMap[hashCode]

    /** 获取所有连接的WebSocket对象 */
    fun getAllWebSocket(): HashMap<Int, Pair<String, WebSocket>> = mConnectUserMap

    /** 发送消息[msg]给所有连接的用户 */
    fun sendMsgToAll(msg: String) {
        if (msg.isEmpty()) {
            return
        }
        for (ws in connections) {
            if (ws.isOpen) {
                ws.send(msg)
            }
        }
    }

    /** 发送消息[msg]给所有连接的用户 */
    fun sendMsgToAll(msg: ByteBuffer) {
        for (ws in connections) {
            if (ws.isOpen) {
                ws.send(msg)
            }
        }
    }

    /** 发送消息[msg]给所有连接的用户 */
    fun sendMsgToAll(msg: ByteArray) {
        if (msg.isEmpty()) {
            return
        }
        for (ws in connections) {
            if (ws.isOpen) {
                ws.send(msg)
            }
        }
    }

    /** 发送消息[msg]给指定的WebSocket连接[ws] */
    fun sendMsg(ws: WebSocket?, msg: String) {
        if (msg.isEmpty() || ws == null) {
            return
        }
        if (ws.isOpen) {
            ws.send(msg)
        }
    }

    /** 发送消息[msg]给指定的WebSocket连接[ws] */
    fun sendMsg(ws: WebSocket?, msg: ByteBuffer) {
        if (ws == null) {
            return
        }
        if (ws.isOpen) {
            ws.send(msg)
        }
    }

    /** 发送消息[msg]给指定的WebSocket连接[ws] */
    fun sendMsg(ws: WebSocket?, msg: ByteArray) {
        if (msg.isEmpty() || ws == null) {
            return
        }
        if (ws.isOpen) {
            ws.send(msg)
        }
    }
}