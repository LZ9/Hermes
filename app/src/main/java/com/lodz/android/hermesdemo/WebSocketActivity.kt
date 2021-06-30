package com.lodz.android.hermesdemo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.lodz.android.corekt.anko.*
import com.lodz.android.corekt.utils.DateUtils
import com.lodz.android.corekt.utils.StatusBarUtil
import com.lodz.android.hermes.contract.Hermes
import com.lodz.android.hermes.contract.OnConnectListener
import com.lodz.android.hermes.contract.OnSendListener
import com.lodz.android.hermes.contract.OnSubscribeListener
import com.lodz.android.hermes.modules.HermesAgent
import com.lodz.android.pandora.base.activity.BaseActivity
import com.lodz.android.pandora.widget.base.TitleBarLayout
import java.nio.ByteBuffer

/**
 * WebSocket测试类
 * @author zhouL
 * @date 2020/3/17
 */
class WebSocketActivity : BaseActivity() {

    companion object {
        fun start(context: Context){
            val intent = Intent(context, WebSocketActivity::class.java)
            context.startActivity(intent)
        }
    }

    private val DEFAULT_URL = "ws://192.168.7.39:9090/yszz/jms/1"

    /** 地址输入框 */
    private val mUrlEdit by bindView<EditText>(R.id.url_edit)
    /** 连接按钮 */
    private val mConnectBtn by bindView<Button>(R.id.connect_btn)
    /** 发送内容输入框 */
    private val mSendEdit by bindView<EditText>(R.id.send_edit)
    /** 发送按钮 */
    private val mSendBtn by bindView<Button>(R.id.send_btn)

    /** 滚动控件 */
    private val mScrollView by bindView<NestedScrollView>(R.id.scroll_view)
    /** 日志 */
    private val mResultTv by bindView<TextView>(R.id.result_tv)
    /** 清空按钮 */
    private val mCleanBtn by bindView<Button>(R.id.clean_btn)
    /** 断开按钮 */
    private val mDisconnectBtn by bindView<Button>(R.id.disconnect_btn)

    /** 静默按钮 */
    private val mSlientBtn by bindView<MaterialButton>(R.id.slient_btn)
    /** 非静默按钮 */
    private val mUnslientBtn by bindView<MaterialButton>(R.id.unslient_btn)

    /** 日志 */
    private var mLog = ""
    /** 推送客户端 */
    private var mHermes: Hermes? = null

    override fun getLayoutId(): Int = R.layout.activity_ws

    override fun findViews(savedInstanceState: Bundle?) {
        super.findViews(savedInstanceState)
        initTitleBarLayout(getTitleBarLayout())
        StatusBarUtil.setColor(window, getColorCompat(R.color.colorPrimary))
    }

    private fun initTitleBarLayout(titleBarLayout: TitleBarLayout) {
        titleBarLayout.setTitleName(R.string.ws_title)
        titleBarLayout.setBackgroundColor(getColorCompat(R.color.colorPrimary))
        titleBarLayout.setTitleTextColor(R.color.white)
        titleBarLayout.needBackButton(false)
    }

    override fun onPressBack(): Boolean {
        if (mHermes?.isConnected() == true){
            mHermes?.disconnect()
        }
        mHermes = null
        return super.onPressBack()
    }

    override fun setListeners() {
        super.setListeners()
        // 连接按钮
        mConnectBtn.setOnClickListener {
            val url = mUrlEdit.text.toString()
            if (url.isEmpty()){
                toastShort(R.string.mqtt_url_hint)
                return@setOnClickListener
            }
            if (mHermes != null){
                if (mHermes?.isConnected() == false){
                    mHermes?.connect()
                }
                return@setOnClickListener
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
                            logResult("发送成功 : topic ---> $topic   $content")
                        }

                        override fun onSendComplete(topic: String, data: ByteArray) {
                            logResult("发送成功 : topic ---> $topic   $data")
                        }

                        override fun onSendComplete(topic: String, bytes: ByteBuffer) {
                            logResult("发送成功 : topic ---> $topic   $bytes")
                        }

                        override fun onSendFailure(topic: String, cause: Throwable) {
                            logResult("发送失败 : topic ---> $topic   ${cause.message}")
                        }
                    })
                    .setOnSubscribeListener(object :OnSubscribeListener{
                        override fun onSubscribeSuccess(topic: String) {
                            logResult("订阅成功 : topic ---> $topic")
                        }

                        override fun onSubscribeFailure(topic: String, cause: Throwable) {
                            logResult("订阅失败 : topic ---> $topic   ${cause.message}")
                        }

                        override fun onMsgArrived(subTopic: String, msg: String) {
                            logResult("消息到达($subTopic)： $msg")
                        }
                    })
                    .buildConnect(applicationContext)
        }

        // 发送按钮
        mSendBtn.setOnClickListener {
            val content = mSendEdit.text.toString()
            if (content.isEmpty()){
                toastShort(R.string.mqtt_send_content_empty)
                return@setOnClickListener
            }
            if (mHermes == null || mHermes?.isConnected() == false) {
                toastShort(R.string.mqtt_client_unconnected)
                return@setOnClickListener
            }
            mHermes?.sendTopic("", mSendEdit.text.toString())
        }

        // 清空按钮
        mCleanBtn.setOnClickListener {
            mLog = ""
            mResultTv.text = ""
        }

        // 断开按钮
        mDisconnectBtn.setOnClickListener {
            mHermes?.disconnect()
        }

        mSlientBtn.setOnClickListener {
            mHermes?.setSilent(true)
        }

        mUnslientBtn.setOnClickListener {
            mHermes?.setSilent(false)
        }
    }

    override fun initData() {
        super.initData()
        mUrlEdit.setText(DEFAULT_URL)
        showStatusCompleted()
    }

    /** 打印信息[result] */
    private fun logResult(result: String) {
        mLog += DateUtils.getCurrentFormatString(DateUtils.TYPE_8).append(" : ").append(result).append("\n")
        mResultTv.text = mLog
        mScrollView.post {
            mScrollView.smoothScrollTo(getScreenWidth(), getScreenHeight())
        }
    }
}