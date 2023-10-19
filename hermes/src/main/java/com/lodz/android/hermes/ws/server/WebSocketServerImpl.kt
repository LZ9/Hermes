package com.lodz.android.hermes.ws.server

import android.content.Context
import com.lodz.android.hermes.contract.HermesWebSocketServer
import com.lodz.android.hermes.modules.HermesLog
import org.java_websocket.WebSocket
import org.java_websocket.drafts.Draft
import org.java_websocket.handshake.ClientHandshake
import java.net.InetSocketAddress
import java.nio.ByteBuffer

/**
 * WebSocket服务端实现
 * @author zhouL
 * @date 2023/10/18
 */
class WebSocketServerImpl : HermesWebSocketServer {

    /** 日志标签 */
    private var mTag = "WebSocketServerLog"

    private var mContext: Context? = null

    private var mWebSocketServer: BaseWebSocketServer? = null

    /** 监听器 */
    private var mListener: OnWebSocketServerListener? = null

    /** 是否静默 */
    private var isSilent: Boolean = false

    /** 连接丢失的检查间隔时间 */
    private var mConnectionLostTimeout = 10

    override fun init(context: Context): HermesWebSocketServer = apply { mContext = context }

    override fun build(
        ip: String,
        port: Int,
        decoderCount: Int,
        drafts: List<Draft>?,
        connectionsContainer: Collection<WebSocket>,
    ): HermesWebSocketServer {
        if (mWebSocketServer != null) {
            release()
        }
        mWebSocketServer = BaseWebSocketServer(ip, port, decoderCount, drafts, connectionsContainer)
        mWebSocketServer?.connectionLostTimeout = mConnectionLostTimeout
        mWebSocketServer?.setOnWebSocketServerListener(object :OnWebSocketServerListener{
            override fun onOpen(ws: WebSocket?, ip: String, handshake: ClientHandshake?) {
                mListener?.onOpen(ws, ip, handshake)
            }

            override fun onClose(ws: WebSocket?, ip: String, code: Int, reason: String, isRemote: Boolean) {
                mListener?.onClose(ws, ip, code, reason, isRemote)
            }

            override fun onMessage(ws: WebSocket?, ip: String, message: String) {
                if (isSilent) {
                    return
                }
                mListener?.onMessage(ws, ip, message)
            }

            override fun onMessage(ws: WebSocket?, ip: String, byteBuffer: ByteBuffer?) {
                if (isSilent) {
                    return
                }
                mListener?.onMessage(ws, ip, byteBuffer)
            }

            override fun onError(ws: WebSocket?, ip: String, e: Exception) {
                mListener?.onError(ws, ip, e)
            }

            override fun onStart() {
                mListener?.onStart()
            }
        })
        mWebSocketServer?.isReuseAddr = true
        return this
    }

    override fun getInetSocketAddress(): InetSocketAddress? = mWebSocketServer?.address

    override fun setOnWebSocketServerListener(listener: OnWebSocketServerListener?): HermesWebSocketServer =
        apply { mListener = listener }

    override fun setConnectionLostTimeout(seconds: Int): HermesWebSocketServer = apply {
        if (seconds > 0) {
            mConnectionLostTimeout = seconds
        }
    }

    override fun connect() {
        mWebSocketServer?.start()
    }

    override fun disconnect() {
        mWebSocketServer?.stop()
    }

    override fun closeAllUser() {
        mWebSocketServer?.closeAllUser()
    }

    override fun getAllWebSocket(): HashMap<Int, Pair<String, WebSocket>> =
        mWebSocketServer?.getAllWebSocket() ?: HashMap()

    override fun sendMsgToAll(msg: String) {
        mWebSocketServer?.sendMsgToAll(msg)
    }

    override fun sendMsgToAll(msg: ByteBuffer) {
        mWebSocketServer?.sendMsgToAll(msg)
    }

    override fun sendMsgToAll(msg: ByteArray) {
        mWebSocketServer?.sendMsgToAll(msg)
    }

    override fun sendMsg(ws: WebSocket?, msg: String) {
        mWebSocketServer?.sendMsg(ws, msg)
    }

    override fun sendMsg(ws: WebSocket?, msg: ByteBuffer) {
        mWebSocketServer?.sendMsg(ws, msg)
    }

    override fun sendMsg(ws: WebSocket?, msg: ByteArray) {
        mWebSocketServer?.sendMsg(ws, msg)
    }

    override fun onlineCount(): Int = mWebSocketServer?.connections?.size ?: 0

    override fun release() {
        mWebSocketServer?.closeAllUser()
        mWebSocketServer?.stop()
        mWebSocketServer?.setOnWebSocketServerListener(null)
        mWebSocketServer = null
    }

    override fun isConnected(): Boolean = mWebSocketServer != null

    override fun setSilent(isSilent: Boolean): HermesWebSocketServer = apply { this.isSilent = isSilent }

    override fun isSilent(): Boolean = this.isSilent

    override fun setLogTag(tag: String): HermesWebSocketServer = apply { if (tag.isNotEmpty()) { mTag = tag } }

    override fun setPrintLog(isPrint: Boolean): HermesWebSocketServer = apply { HermesLog.setPrint(isPrint) }

}