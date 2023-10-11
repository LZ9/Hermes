package com.lodz.android.hermesdemo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.lodz.android.corekt.anko.IoScope
import com.lodz.android.corekt.anko.append
import com.lodz.android.corekt.anko.getColorCompat
import com.lodz.android.corekt.anko.getIpv4List
import com.lodz.android.corekt.anko.toastShort
import com.lodz.android.corekt.utils.StatusBarUtil
import com.lodz.android.hermes.ws.server.OnWebSocketServerListener
import com.lodz.android.hermes.ws.server.BaseWebSocketServer
import com.lodz.android.hermesdemo.databinding.ActivityWsServerBinding
import com.lodz.android.pandora.base.activity.BaseActivity
import com.lodz.android.pandora.utils.viewbinding.bindingLayout
import com.lodz.android.pandora.widget.base.TitleBarLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        /** 默认端口号 */
        private const val DEFAULT_PORT = 8800
        fun start(context: Context){
            context.startActivity(Intent(context, WebsocketServerActivity::class.java))
        }
    }

    private val mBinding: ActivityWsServerBinding by bindingLayout(ActivityWsServerBinding::inflate)

    override fun getViewBindingLayout(): View = mBinding.root

    private var mWebSocketServer: BaseWebSocketServer? = null

    override fun findViews(savedInstanceState: Bundle?) {
        super.findViews(savedInstanceState)
        initTitleBarLayout(getTitleBarLayout())
        StatusBarUtil.setColor(window, getColorCompat(R.color.color_ff6307))
        mBinding.ipEdit.setText(getIpv4())
        mBinding.portEdit.setText(DEFAULT_PORT.toString())
    }

    /** 获取IPV4地址 */
    private fun getIpv4(): String {
        val list = getIpv4List()
        if (list.isEmpty()){
            return ""
        }
        if (list.size > 1) {
            for (pair in list) {
                if (pair.first.contentEquals("wlan", true)){
                    return pair.second
                }
            }
        }
        return list[0].second
    }

    private fun initTitleBarLayout(titleBarLayout: TitleBarLayout) {
        titleBarLayout.setTitleName(R.string.ws_server_title)
        titleBarLayout.setBackgroundColor(getColorCompat(R.color.color_ff6307))
        titleBarLayout.setTitleTextColor(R.color.white)
    }

    override fun onPressBack(): Boolean {
        closeWebSocketServer()
        return false
    }

    override fun onClickBackBtn() {
        super.onClickBackBtn()
        closeWebSocketServer()
        finish()
    }

    override fun setListeners() {
        super.setListeners()
        mBinding.openBtn.setOnClickListener {
            val ip = mBinding.ipEdit.text.toString()
            val port = mBinding.portEdit.text.toString()
            if (ip.isEmpty()){
                toastShort(R.string.ws_server_ip_hint)
                return@setOnClickListener
            }
            if (port.isEmpty()){
                toastShort(R.string.ws_server_port_hint)
                return@setOnClickListener
            }
            openWebSocketServer(ip, port.toInt())
        }

        mBinding.closeBtn.setOnClickListener {
            closeWebSocketServer()
        }

        mBinding.sendBtn.setOnClickListener {
            if (mWebSocketServer == null){
                toastShort(R.string.ws_server_unopen)
                return@setOnClickListener
            }
            val msg = mBinding.contentEdit.text.toString()
            if (msg.isEmpty()){
                toastShort(R.string.ws_server_send_hint)
                return@setOnClickListener
            }
            sendMsg(msg)
        }

        mBinding.cleanBtn.setOnClickListener {
            mBinding.logTv.text = ""
        }
    }

    private fun openWebSocketServer(ip: String, port: Int) {
        if (mWebSocketServer != null) {
            addLog("服务已启动")
            return
        }
        addLog("开启WebSocket服务端")
        mWebSocketServer = BaseWebSocketServer(ip, port)
        mWebSocketServer?.setOnWebSocketServerListener(object : OnWebSocketServerListener {
            override fun onOpen(ws: WebSocket?, handshake: ClientHandshake?) {
                IoScope().launch {
                    val name = ws?.remoteSocketAddress?.hostName ?: "未知"
                    launch(Dispatchers.Main) {
                        addLog("$name 用户已连接上")
                    }
                }
            }

            override fun onClose(ws: WebSocket?, code: Int, reason: String, isRemote: Boolean) {
                IoScope().launch {
                    val name = ws?.remoteSocketAddress?.hostName ?: "未知"
                    launch(Dispatchers.Main) {
                        val log = name.append(" 用户已断开")
                            .append(" ; code : $code")
                            .append(" ; reason : $reason")
                            .append(" ; isRemote : $isRemote")
                        addLog(log)
                    }
                }
            }

            override fun onMessage(ws: WebSocket?, message: String) {
                IoScope().launch {
                    val name = ws?.remoteSocketAddress?.hostName ?: "未知"
                    launch(Dispatchers.Main) {
                        addLog("$name 用户发来String消息：$message")
                    }
                }
            }

            override fun onMessage(ws: WebSocket?, byteBuffer: ByteBuffer?) {
                IoScope().launch {
                    val name = ws?.remoteSocketAddress?.hostName ?: "未知"
                    launch(Dispatchers.Main) {
                        addLog("$name 用户发来ByteBuffer消息：$byteBuffer")
                    }
                }
            }

            override fun onError(ws: WebSocket?, e: Exception) {
                IoScope().launch {
                    val name = ws?.remoteSocketAddress?.hostName ?: "未知"
                    launch(Dispatchers.Main) {
                        addLog("$name 用户连接出现异常 ; message : ${e.message}")
                    }
                }
            }

            override fun onStart() {
                addLog("WebSocket服务端已启动")
            }
        })
        mWebSocketServer?.isReuseAddr = true
        mWebSocketServer?.start()
        addLog("服务端路径：${mWebSocketServer?.address?.toString()}")
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
        val text = mBinding.logTv.text.toString()
        if (text.isEmpty()) {
            mBinding.logTv.text = log
        }
        mBinding.logTv.text = log.append("\n").append(text)
    }
}