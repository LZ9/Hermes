package com.lodz.android.hermesdemo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.lodz.android.corekt.anko.IoScope
import com.lodz.android.corekt.anko.append
import com.lodz.android.corekt.anko.check
import com.lodz.android.corekt.anko.getColorCompat
import com.lodz.android.corekt.anko.getIpv4List
import com.lodz.android.corekt.anko.hideInputMethod
import com.lodz.android.corekt.anko.toastShort
import com.lodz.android.corekt.utils.DateUtils
import com.lodz.android.corekt.utils.StatusBarUtil
import com.lodz.android.hermes.contract.HermesWebSocketServer
import com.lodz.android.hermes.modules.HermesAgent
import com.lodz.android.hermes.ws.server.OnWebSocketServerListener
import com.lodz.android.hermesdemo.databinding.ActivityWsServerBinding
import com.lodz.android.pandora.base.activity.BaseActivity
import com.lodz.android.pandora.utils.viewbinding.bindingLayout
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
        /** 默认端口号 */
        private const val DEFAULT_PORT = 8800
        fun start(context: Context){
            context.startActivity(Intent(context, WebsocketServerActivity::class.java))
        }
    }

    // TODO: 2023/10/18 添加向指定客户端发送消息的交互
    private val mBinding: ActivityWsServerBinding by bindingLayout(ActivityWsServerBinding::inflate)

    override fun getViewBindingLayout(): View = mBinding.root

    /** WebSocket服务端 */
    private var mHermes: HermesWebSocketServer? = null

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
            if (!mHermes?.isConnected().check()){
                toastShort(R.string.ws_server_no_open)
                return@setOnClickListener
            }
            val msg = mBinding.contentEdit.text.toString()
            if (msg.isEmpty()){
                toastShort(R.string.ws_server_send_hint)
                return@setOnClickListener
            }
            logResult("向所有连接用户发送信息：$msg")
            mHermes?.sendMsgToAll(msg)
            mBinding.contentEdit.hideInputMethod()
        }

        mBinding.cleanBtn.setOnClickListener {
            mBinding.logTv.text = ""
        }

        mBinding.silentBtn.setOnClickListener {
            mHermes?.setSilent(true)
        }

        mBinding.unsilentBtn.setOnClickListener {
            mHermes?.setSilent(false)
        }
    }

    /** 启动WebSocket服务端 */
    private fun openWebSocketServer(ip: String, port: Int) {
        if (mHermes != null) {
            closeWebSocketServer()
        }
        logResult("开启WebSocket服务端")
        mHermes = HermesAgent.createWebSocketServer()
            .init(getContext())
            .setLogTag("WebSocketLog")
            .setPrintLog(true)
            .setSilent(false)
            .setConnectionLostTimeout(5)
            .setOnWebSocketServerListener(object :OnWebSocketServerListener{
                override fun onOpen(ws: WebSocket?, ip: String, handshake: ClientHandshake?) {
                    logResult("${ip.ifEmpty { "未知" }} 用户已连接上（在线数${mHermes?.onlineCount()}）")
                }

                override fun onClose(ws: WebSocket?, ip: String, code: Int, reason: String, isRemote: Boolean) {
                    val log = ip.ifEmpty { "未知" }.append("用户已断开")
                        .append(" ; code : $code")
                        .append(" ; reason : $reason")
                        .append(" ; isRemote : $isRemote")
                        .append("（在线数${mHermes?.onlineCount()}）")
                    logResult(log)
                }

                override fun onMessage(ws: WebSocket?, ip: String, msg: String) {
                    logResult("${ip.ifEmpty { "未知" }}用户发来String消息：$msg")
                }

                override fun onMessage(ws: WebSocket?, ip: String, msg: ByteBuffer?) {
                    logResult("${ip.ifEmpty { "未知" }}用户发来ByteBuffer消息：$msg")
                }

                override fun onError(ws: WebSocket?, ip: String, e: Exception) {
                    logResult("${ip.ifEmpty { "未知" }}用户连接出现异常 ; e : ${e.cause}")
                }

                override fun onStart() {
                    logResult("WebSocket服务端已启动")
                }
            })
            .build(ip, port)
        mHermes?.connect()
        logResult("服务端路径：${mHermes?.getInetSocketAddress()?.toString()}")
    }

    private fun closeWebSocketServer() {
        logResult("关闭WebSocket服务端")
        releaseHermes()
    }

    override fun initData() {
        super.initData()
        showStatusCompleted()
    }

    private fun releaseHermes(){
        mHermes?.release()
        mHermes?.setOnWebSocketServerListener(null)
        mHermes = null
    }

    /** 打印信息[result] */
    private fun logResult(result: String) {
        val log = "【${DateUtils.getCurrentFormatString(DateUtils.TYPE_8)}】 ".append(result)
        val text = mBinding.logTv.text
        if (text.isEmpty()) {
            mBinding.logTv.text = log
        }
        mBinding.logTv.text = log.append("\n").append(text)
    }
}