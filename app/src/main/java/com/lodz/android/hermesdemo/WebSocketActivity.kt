package com.lodz.android.hermesdemo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.lodz.android.corekt.anko.*
import com.lodz.android.corekt.utils.DateUtils
import com.lodz.android.corekt.utils.StatusBarUtil
import com.lodz.android.hermes.contract.Hermes
import com.lodz.android.hermes.contract.OnConnectListener
import com.lodz.android.hermes.contract.OnSendListener
import com.lodz.android.hermes.contract.OnSubscribeListener
import com.lodz.android.hermes.modules.HermesAgent
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

        private const val DEFAULT_URL = "ws://121.40.165.18:8800"

        fun start(context: Context){
            context.startActivity(Intent(context, WebSocketActivity::class.java))
        }
    }

    private val mBinding: ActivityWsBinding by bindingLayout(ActivityWsBinding::inflate)

    override fun getViewBindingLayout(): View = mBinding.root

    /** 推送客户端 */
    private var mHermes: Hermes? = null

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
        super.onClickBackBtn()
        releaseHermes()
        finish()
    }

    override fun onPressBack(): Boolean {
        releaseHermes()
        return false
    }

    private fun releaseHermes(){
        if (mHermes?.isConnected() == true){
            mHermes?.disconnect()
        }
        mHermes = null
    }

    override fun setListeners() {
        super.setListeners()
        // 连接按钮
        mBinding.createBtn.setOnClickListener {
            val url = mBinding.urlEdit.text.toString()
            if (url.isEmpty()){
                toastShort(R.string.mqtt_url_hint)
                return@setOnClickListener
            }
            if (mHermes != null){
                releaseHermes()
            }
            mHermes = HermesAgent.create()
                    .setConnectType(HermesAgent.WEB_SOCKET)
                    .setUrl(url)
                    .setPrintLog(true)
                    .setOnConnectListener(object :OnConnectListener{
                        override fun onConnectComplete(isReconnected: Boolean) {
                            logResult("连接完成 ： isReconnected ---> $isReconnected")
                        }

                        override fun onConnectFailure(cause: Throwable) {
                            logResult("连接失败 : ${cause.message}")
                        }

                        override fun onConnectionLost(cause: Throwable) {
                            logResult("连接断开（丢失） : ${cause.message}")
                        }
                    })
                    .setOnSendListener(object :OnSendListener{
                        override fun onSendComplete(topic: String, content: String) {
                            logResult("String发送成功 : topic ---> $topic   $content")
                        }

                        override fun onSendComplete(topic: String, data: ByteArray) {
                            logResult("ByteArray发送成功 : topic ---> $topic   $data")
                        }

                        override fun onSendComplete(topic: String, bytes: ByteBuffer) {
                            logResult("ByteBuffer发送成功 : topic ---> $topic   $bytes")
                        }

                        override fun onSendFailure(topic: String, cause: Throwable) {
                            logResult("发送失败 : topic ---> $topic   ${cause.message}")
                        }
                    })
                    .setOnSubscribeListener(object :OnSubscribeListener{
                        override fun onSubscribeSuccess(topic: Array<String>) {
                            logResult("订阅成功 : topic ---> $topic")
                        }

                        override fun onSubscribeFailure(topic: Array<String>, cause: Throwable) {
                            logResult("订阅失败 : topic ---> $topic   ${cause.message}")
                        }

                        override fun onMsgArrived(subTopic: String, msg: String) {
                            logResult("消息到达($subTopic)： $msg")
                        }
                    })
                    .buildConnect(applicationContext)
        }

        // 发送按钮
        mBinding.sendBtn.setOnClickListener {
            val content = mBinding.sendEdit.text.toString()
            if (content.isEmpty()){
                toastShort(R.string.mqtt_send_content_empty)
                return@setOnClickListener
            }
            if (mHermes == null || mHermes?.isConnected() == false) {
                toastShort(R.string.mqtt_client_unconnected)
                return@setOnClickListener
            }
            mHermes?.sendTopic("", mBinding.sendEdit.text.toString())
        }

        // 清空按钮
        mBinding.cleanBtn.setOnClickListener {
            mBinding.resultTv.text = ""
        }

        // 断开按钮
        mBinding.connectBtn.setOnClickListener {
            mHermes?.connect()
        }

        // 断开按钮
        mBinding.disconnectBtn.setOnClickListener {
            mHermes?.disconnect()
        }

        mBinding.silentBtn.setOnClickListener {
            mHermes?.setSilent(true)
        }

        mBinding.unsilentBtn.setOnClickListener {
            mHermes?.setSilent(false)
        }
    }

    override fun initData() {
        super.initData()
        mBinding.urlEdit.setText(DEFAULT_URL)
        showStatusCompleted()
    }

    /** 打印信息[result] */
    private fun logResult(result: String) {
        val log = DateUtils.getCurrentFormatString(DateUtils.TYPE_8).append(" : ").append(result)
        val text = mBinding.resultTv.text
        if (text.isEmpty()) {
            mBinding.resultTv.text = log
        }
        mBinding.resultTv.text = log.append("\n").append(text)
    }
}