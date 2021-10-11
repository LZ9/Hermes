package com.lodz.android.hermesdemo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.lodz.android.corekt.anko.append
import com.lodz.android.corekt.anko.bindView
import com.lodz.android.corekt.anko.getColorCompat
import com.lodz.android.corekt.anko.toastShort
import com.lodz.android.corekt.utils.StatusBarUtil
import com.lodz.android.hermes.contract.OnWebSocketServerListener
import com.lodz.android.hermes.modules.BaseWebSocketServer
import com.lodz.android.pandora.base.activity.BaseActivity
import com.lodz.android.pandora.widget.base.TitleBarLayout
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import java.nio.ByteBuffer

/**
 * WebSocket服务的（APP作为服务端）
 * @author zhouL
 * @date 2021/6/30
 */
class WebsocketServerActivity : BaseActivity() {

    companion object {
        fun start(context: Context){
            val intent = Intent(context, WebsocketServerActivity::class.java)
            context.startActivity(intent)
        }
    }

    /** 默认端口号 */
    private val DEFAULT_PORT = 8800

    /** 端口输入框 */
    private val mPortEdit by bindView<EditText>(R.id.port_edit)
    /** 启动服务 */
    private val mOpenBtn by bindView<MaterialButton>(R.id.open_btn)
    /** 关闭服务 */
    private val mCloseBtn by bindView<MaterialButton>(R.id.close_btn)
    /** 发送内容输入框 */
    private val mContentEdit by bindView<EditText>(R.id.content_edit)
    /** 发送按钮 */
    private val mSendBtn by bindView<MaterialButton>(R.id.send_btn)
    /** 日志 */
    private val mLogTv by bindView<TextView>(R.id.log_tv)
    /** 清空日志按钮 */
    private val mCleanBtn by bindView<MaterialButton>(R.id.clean_btn)

    private var mWebSocketServer: BaseWebSocketServer? = null

    override fun getLayoutId(): Int = R.layout.activity_ws_server

    override fun findViews(savedInstanceState: Bundle?) {
        super.findViews(savedInstanceState)
        initTitleBarLayout(getTitleBarLayout())
        StatusBarUtil.setColor(window, getColorCompat(R.color.color_ff6307))
        mPortEdit.setText(DEFAULT_PORT.toString())
    }

    private fun initTitleBarLayout(titleBarLayout: TitleBarLayout) {
        titleBarLayout.setTitleName(R.string.ws_server_title)
        titleBarLayout.setBackgroundColor(getColorCompat(R.color.color_ff6307))
        titleBarLayout.setTitleTextColor(R.color.white)
        titleBarLayout.needBackButton(false)
    }

    override fun setListeners() {
        super.setListeners()
        mOpenBtn.setOnClickListener {
            val port = mPortEdit.text.toString()
            if (port.isEmpty()){
                toastShort(R.string.ws_server_port_hint)
                return@setOnClickListener
            }
            openWebSocketServer(port.toInt())
        }

        mCloseBtn.setOnClickListener {
            closeWebSocketServer()
        }

        mSendBtn.setOnClickListener {
            if (mWebSocketServer == null){
                toastShort(R.string.ws_server_unopen)
                return@setOnClickListener
            }
            val msg = mContentEdit.text.toString()
            if (msg.isEmpty()){
                toastShort(R.string.ws_server_send_hint)
                return@setOnClickListener
            }
            sendMsg(msg)
        }

        mCleanBtn.setOnClickListener {
            mLogTv.text = ""
        }
    }

    private fun openWebSocketServer(port: Int) {
        if (mWebSocketServer != null) {
            addLog("服务已启动")
            return
        }
        addLog("开启WebSocket服务端")
        mWebSocketServer = BaseWebSocketServer(port)
        mWebSocketServer?.setOnWebSocketServerListener(object : OnWebSocketServerListener {
            override fun onOpen(ws: WebSocket?, handshake: ClientHandshake?) {
                var name = "未知"
                if (ws != null){
                    name = ws.remoteSocketAddress.hostName
                }
                addLog("$name 用户已连接上")
            }

            override fun onClose(ws: WebSocket?, code: Int, reason: String, isRemote: Boolean) {
                var name = "未知"
                if (ws != null){
                    name = ws.remoteSocketAddress.hostName
                }
                val log = name.append(" 用户已断开")
                    .append(" ; code : $code")
                    .append(" ; reason : $reason")
                    .append(" ; isRemote : $isRemote")
                addLog(log)
            }


            override fun onMessage(ws: WebSocket?, message: String) {
                var name = "未知"
                if (ws != null){
                    name = ws.remoteSocketAddress.hostName
                }
                addLog("$name 用户发来消息：$message")
            }

            override fun onMessage(ws: WebSocket?, byteBuffer: ByteBuffer?) {
                var name = "未知"
                if (ws != null){
                    name = ws.remoteSocketAddress.hostName
                }
                addLog("$name 用户发来消息：$byteBuffer")
            }

            override fun onError(ws: WebSocket?, e: Exception) {
                var name = "未知"
                if (ws != null){
                    name = ws.remoteSocketAddress.hostName
                }
                addLog("$name 用户连接出现异常 ; message : ${e.message}")
            }

            override fun onStart() {
                addLog("WebSocket服务端已启动")
            }
        })
        mWebSocketServer?.isReuseAddr = true
        mWebSocketServer?.start()
    }

    private fun closeWebSocketServer() {
        addLog("关闭WebSocket服务端")
        mWebSocketServer?.setOnWebSocketServerListener(null)
        mWebSocketServer?.stop()
        mWebSocketServer = null
    }

    private fun sendMsg(msg: String) {
        addLog("向所有连接用户发送：$msg")
        mWebSocketServer?.sendMsgToAll(msg)
    }

    override fun initData() {
        super.initData()
        showStatusCompleted()
    }

    private fun addLog(log: String) {
        val text = mLogTv.text.toString()
        if (text.isEmpty()) {
            mLogTv.text = log
        }
        mLogTv.text = log.append("\n").append(text)
    }
}