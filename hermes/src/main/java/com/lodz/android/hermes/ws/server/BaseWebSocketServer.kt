package com.lodz.android.hermes.ws.server

import android.util.Log
import kotlinx.coroutines.*
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.lang.Exception
import java.net.InetSocketAddress
import java.nio.ByteBuffer

/**
 * WebSocket服务端基类
 * @author zhouL
 * @date 2021/6/30
 */
open class BaseWebSocketServer @JvmOverloads constructor(
    host: String,
    port: Int,
    private val isAutoSaveWs: Boolean = true
) : WebSocketServer(InetSocketAddress(host, port)) {

    /** 监听器 */
    private var mListener: OnWebSocketServerListener? = null
    /** 连接上的用户 */
    private val mConnectUserMap: HashMap<String, WebSocket> = HashMap()

    private var mLogTag = "WebSocketServer"

    /** 设置日志标签 */
    fun setLogTag(tag: String) {
        mLogTag = tag
    }

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        Log.d(mLogTag, "有用户连接上")
        if (isAutoSaveWs){
            putUser(conn)
        }
        MainScope().launch { mListener?.onOpen(conn, handshake) }
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        Log.d(mLogTag, "有用户断开")
        if (isAutoSaveWs){
            removeUser(conn)
        }
        MainScope().launch { mListener?.onClose(conn, code, reason ?: "", remote) }
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        Log.i(mLogTag, "有用户发来消息 : $message")
        MainScope().launch { mListener?.onMessage(conn, message ?: "") }
    }

    override fun onMessage(conn: WebSocket?, message: ByteBuffer?) {
        super.onMessage(conn, message)
        Log.i(mLogTag, "有用户发来消息 : $message")
        MainScope().launch { mListener?.onMessage(conn, message) }
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        Log.e(mLogTag, "服务器出现异常 : ${ex?.message}")
        MainScope().launch { mListener?.onError(conn, ex ?: RuntimeException("WebSocket error but exception is null")) }
    }

    override fun onStart() {
        Log.d(mLogTag, "服务器已启动")
        MainScope().launch { mListener?.onStart() }
    }

    override fun stop() {
        releaseUser()
        super.stop()
    }

    /** 设置WebSocket服务端监听器[listener] */
    fun setOnWebSocketServerListener(listener: OnWebSocketServerListener?) {
        mListener = listener
    }

    /** 存储用户连接[ws] */
    fun putUser(ws: WebSocket?) {
        if (ws == null) {
            return
        }
        putUser(ws.remoteSocketAddress.hostName, ws)
    }

    /** 以用户名称[name]存储连接[ws] */
    fun putUser(name: String, ws: WebSocket?) {
        if (ws == null) {
            return
        }
        if (!mConnectUserMap.containsKey(name)) {
            mConnectUserMap[name] = ws
        }
    }

    /** 移除用户连接[ws] */
    fun removeUser(ws: WebSocket?) {
        if (ws == null) {
            return
        }
        removeUser(ws.remoteSocketAddress.hostName)
    }

    /** 移除用户连接[name] */
    fun removeUser(name: String) {
        mConnectUserMap.remove(name)
    }

    /** 释放所有用户连接 */
    fun releaseUser() {
        for (map in mConnectUserMap) {
            val ws = map.value
            if (ws.isOpen){
                ws.close()
            }
        }
        mConnectUserMap.clear()
    }

    /** 根据名称[name]，获取WebSocket对象 */
    fun getWebSocket(name: String): WebSocket? = mConnectUserMap[name]

    /** 发送消息[msg]给所有连接的用户 */
    fun sendMsgToAll(msg: String) {
        if (msg.isEmpty()) {
            return
        }
        for (map in mConnectUserMap) {
            val ws = map.value
            if (ws.isOpen) {
                ws.send(msg)
            }
        }
    }

    /** 发送消息[byteBuffer]给所有连接的用户 */
    fun sendMsgToAll(byteBuffer: ByteBuffer) {
        for (map in mConnectUserMap) {
            val ws = map.value
            if (ws.isOpen) {
                ws.send(byteBuffer)
            }
        }
    }

    /** 发送消息[byteArray]给所有连接的用户 */
    fun sendMsgToAll(byteArray: ByteArray) {
        if (byteArray.isEmpty()){
            return
        }
        for (map in mConnectUserMap) {
            val ws = map.value
            if (ws.isOpen) {
                ws.send(byteArray)
            }
        }
    }

    /** 发送消息[msg]给指定的WebSocket连接[ws] */
    fun sendMsg(msg: String, ws: WebSocket?) {
        if (msg.isEmpty() || ws == null) {
            return
        }
        if (ws.isOpen) {
            ws.send(msg)
        }
    }

    /** 发送消息[byteBuffer]给指定的WebSocket连接[ws] */
    fun sendMsg(byteBuffer: ByteBuffer, ws: WebSocket?) {
        if (ws == null) {
            return
        }
        if (ws.isOpen) {
            ws.send(byteBuffer)
        }
    }

    /** 发送消息[byteArray]给指定的WebSocket连接[ws] */
    fun sendMsg(byteArray: ByteArray, ws: WebSocket?) {
        if (byteArray.isEmpty() || ws == null) {
            return
        }
        if (ws.isOpen) {
            ws.send(byteArray)
        }
    }

    /** 发送消息[msg]给指定名称[name]的用户 */
    fun sendMsg(msg: String, name: String) {
        if (msg.isEmpty() || name.isEmpty()) {
            return
        }
        for (map in mConnectUserMap) {
            if (name == map.key) {
                val ws = map.value
                if (ws.isOpen) {
                    ws.send(msg)
                }
            }
        }
    }

    /** 发送消息[byteBuffer]给指定名称[name]的用户 */
    fun sendMsg(byteBuffer: ByteBuffer, name: String) {
        if (name.isEmpty()) {
            return
        }
        for (map in mConnectUserMap) {
            if (name == map.key) {
                val ws = map.value
                if (ws.isOpen) {
                    ws.send(byteBuffer)
                }
            }
        }
    }

    /** 发送消息[byteArray]给指定名称[name]的用户 */
    fun sendMsg(byteArray: ByteArray, name: String) {
        if (byteArray.isEmpty() || name.isEmpty()) {
            return
        }
        for (map in mConnectUserMap) {
            if (name == map.key) {
                val ws = map.value
                if (ws.isOpen) {
                    ws.send(byteArray)
                }
            }
        }
    }
}