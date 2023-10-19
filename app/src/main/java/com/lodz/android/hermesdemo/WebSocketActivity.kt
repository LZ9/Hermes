package com.lodz.android.hermesdemo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.lodz.android.corekt.anko.*
import com.lodz.android.corekt.utils.DateUtils
import com.lodz.android.corekt.utils.StatusBarUtil
import com.lodz.android.hermes.contract.HermesWebSocketClient
import com.lodz.android.hermes.modules.HermesAgent
import com.lodz.android.hermes.ws.client.OnConnectListener
import com.lodz.android.hermes.ws.client.OnSendListener
import com.lodz.android.hermes.ws.client.OnSubscribeListener
import com.lodz.android.hermesdemo.databinding.ActivityWsBinding
import com.lodz.android.pandora.base.activity.BaseActivity
import com.lodz.android.pandora.utils.viewbinding.bindingLayout
import com.lodz.android.pandora.widget.base.TitleBarLayout
import java.nio.ByteBuffer

/**
 * WebSocket测试类
 * @author zhouL
 * @date 2020/3/17
 */
class WebSocketActivity : BaseActivity() {

    companion object {

        private const val DEFAULT_URL = "ws://124.222.224.186:8800"

        fun start(context: Context){
            context.startActivity(Intent(context, WebSocketActivity::class.java))
        }
    }

    private val mBinding: ActivityWsBinding by bindingLayout(ActivityWsBinding::inflate)

    override fun getViewBindingLayout(): View = mBinding.root

    /** WebSocket客户端 */
    private var mHermes: HermesWebSocketClient? = null

    override fun findViews(savedInstanceState: Bundle?) {
        super.findViews(savedInstanceState)
        initTitleBarLayout(getTitleBarLayout())
        StatusBarUtil.setColor(window, getColorCompat(R.color.colorPrimary))
    }

    private fun initTitleBarLayout(titleBarLayout: TitleBarLayout) {
        titleBarLayout.setTitleName(R.string.ws_title)
        titleBarLayout.setBackgroundColor(getColorCompat(R.color.colorPrimary))
        titleBarLayout.setTitleTextColor(R.color.white)
    }

    override fun onClickBackBtn() {
        releaseHermes()
        finish()
    }

    override fun onPressBack(): Boolean {
        releaseHermes()
        return false
    }

    private fun releaseHermes(){
        mHermes?.release()
        mHermes = null
    }

    override fun setListeners() {
        super.setListeners()
        // 连接按钮
        mBinding.createBtn.setOnClickListener {
            val url = mBinding.urlEdit.text.toString()
            if (url.isEmpty()) {
                toastShort(R.string.mqtt_url_hint)
                return@setOnClickListener
            }
            if (mHermes != null) {
                releaseHermes()
            }
            mHermes = HermesAgent.createWebSocketClient()
                .init(getContext())
                .setLogTag("WebSocketLog")
                .setPrintLog(true)
                .setSilent(false)
                .setReconnectInterval(20 * 1000)
                .setOnConnectListener(object : OnConnectListener {
                    override fun onConnectComplete() {
                        logResult("连接完成")
                    }

                    override fun onConnectFailure(cause: Throwable) {
                        logResult("连接失败 : ${cause.message}")
                    }

                    override fun onConnectionLost(cause: Throwable) {
                        logResult("连接断开（丢失） : ${cause.message}")
                    }
                })
                .setOnSubscribeListener(object : OnSubscribeListener {
                    override fun onMsgArrived(ip: String, msg: String) {
                        logResult("消息到达 : $ip ---> $msg")
                    }

                    override fun onMsgArrived(ip: String, msg: ByteBuffer) {
                        logResult("消息到达 : $ip ---> $msg")
                    }
                })
                .setOnSendListener(object : OnSendListener {
                    override fun onSendComplete(ip: String, msg: String) {
                        logResult("消息发送成功 : $ip -> $msg")
                    }

                    override fun onSendComplete(ip: String, msg: ByteBuffer) {
                        logResult("消息发送成功 : $ip -> $msg")
                    }

                    override fun onSendFailure(cause: Throwable) {
                        logResult("消息发送失败：${cause.message}")
                    }
                })
                .build(url, true)
            mHermes?.connect()
            logResult("开始连接：$url")
        }

        // 发送按钮
        mBinding.sendBtn.setOnClickListener {
            val content = mBinding.sendEdit.text.toString()
            if (content.isEmpty()) {
                toastShort(R.string.mqtt_send_content_empty)
                return@setOnClickListener
            }
            if (mHermes == null || mHermes?.isConnected() == false) {
                toastShort(R.string.mqtt_client_unconnected)
                return@setOnClickListener
            }
            mHermes?.sendData(mBinding.sendEdit.text.toString())
            mBinding.sendEdit.hideInputMethod()
        }

        // 清空日志按钮
        mBinding.cleanBtn.setOnClickListener {
            mBinding.resultTv.text = ""
        }

        // 连接按钮
        mBinding.connectBtn.setOnClickListener {
            val url = mBinding.urlEdit.text.toString()
            if (url.isEmpty()) {
                toastShort(R.string.mqtt_url_hint)
                return@setOnClickListener
            }
            if (mHermes?.isConnected().check()){
                logResult("已经连接")
                return@setOnClickListener
            }
            logResult("开始连接 : $url")
            mHermes?.build(url, true)
            mHermes?.connect()
        }

        // 断开按钮
        mBinding.disconnectBtn.setOnClickListener {
            mHermes?.disconnect()
        }

        mBinding.silentBtn.setOnClickListener {
            mHermes?.setSilent(true)
            logResult("静默，关闭消息到达提醒")
        }

        mBinding.unsilentBtn.setOnClickListener {
            mHermes?.setSilent(false)
            logResult("非静默，打开消息到达提醒")
        }
    }

    override fun initData() {
        super.initData()
        mBinding.urlEdit.setText(DEFAULT_URL)
        showStatusCompleted()
    }

    /** 打印信息[result] */
    private fun logResult(result: String) {
        val log = "【${DateUtils.getCurrentFormatString(DateUtils.TYPE_8)}】 ".append(result)
        val text = mBinding.resultTv.text
        if (text.isEmpty()) {
            mBinding.resultTv.text = log
        }
        mBinding.resultTv.text = log.append("\n").append(text)
    }
}