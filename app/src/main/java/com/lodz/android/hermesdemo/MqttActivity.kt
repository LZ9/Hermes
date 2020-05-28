package com.lodz.android.hermesdemo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.lodz.android.corekt.anko.*
import com.lodz.android.corekt.utils.DateUtils
import com.lodz.android.corekt.utils.StringUtils
import com.lodz.android.hermes.contract.Hermes
import com.lodz.android.hermes.contract.OnConnectListener
import com.lodz.android.hermes.contract.OnSendListener
import com.lodz.android.hermes.contract.OnSubscribeListener
import com.lodz.android.hermes.modules.HermesAgent
import com.lodz.android.pandora.base.activity.BaseActivity
import com.lodz.android.pandora.widget.base.TitleBarLayout

/**
 * MQTT测试类
 * @author zhouL
 * @date 2020/3/17
 */
class MqttActivity : BaseActivity(){

    companion object {
        fun start(context: Context){
            val intent = Intent(context, MqttActivity::class.java)
            context.startActivity(intent)
        }
    }

    /** 默认地址 */
    private val DEFAULT_URL = "tcp://192.168.6.141:1883"
    /** 默认客户端id */
    private val DEFAULT_CLIENT_ID = "12345"
    /** 默认订阅主题 */
    private val DEFAULT_SUB_TOPIC = "test.topic"
    /** 默认发送主题 */
    private val DEFAULT_SEND_TOPIC = "test.client"
    /** 默认发送内容 */
    private val DEFAULT_SEND_CONTENT = "测试数据"

    /** 滚动控件 */
    private val mScrollView by bindView<NestedScrollView>(R.id.scroll_view)
    /** 日志 */
    private val mResultTv by bindView<TextView>(R.id.result)

    /** 地址输入框 */
    private val mUrlEdit by bindView<EditText>(R.id.url_edit)
    /** ClientId输入框 */
    private val mClientIdEdit by bindView<EditText>(R.id.client_id_edit)
    /** 订阅主题输入框 */
    private val mSubtopicEdit by bindView<EditText>(R.id.subtopic_edit)
    /** 连接按钮 */
    private val mConnectBtn by bindView<Button>(R.id.connect_btn)

    /** 发送主题输入框 */
    private val mSendTpoicEdit by bindView<EditText>(R.id.send_topic_edit)
    /** 发送内容输入框 */
    private val mSendEdit by bindView<EditText>(R.id.send_edit)
    /** 发送按钮 */
    private val mSendBtn by bindView<Button>(R.id.send_btn)

    /** 清空按钮 */
    private val mCleanBtn by bindView<Button>(R.id.clean_btn)
    /** 断开按钮 */
    private val mDisconnectBtn by bindView<Button>(R.id.disconnect_btn)

    /** 静默按钮 */
    private val mSlientBtn by bindView<Button>(R.id.slient_btn)
    /** 非静默按钮 */
    private val mUnslientBtn by bindView<Button>(R.id.unslient_btn)


    /** 日志 */
    private var mLog = ""
    /** 推送客户端 */
    private var mHermes: Hermes? = null

    override fun getLayoutId(): Int = R.layout.activity_mqtt

    override fun findViews(savedInstanceState: Bundle?) {
        super.findViews(savedInstanceState)
        initTitleBarLayout(getTitleBarLayout())
    }

    private fun initTitleBarLayout(titleBarLayout: TitleBarLayout) {
        titleBarLayout.setTitleName(R.string.mqtt_title)
        titleBarLayout.setBackgroundColor(getColorCompat(R.color.colorAccent))
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
        // 连接
        mConnectBtn.setOnClickListener {
            if (mUrlEdit.text.isEmpty()) {
                toastShort(R.string.mqtt_url_empty)
                return@setOnClickListener
            }
            if (mClientIdEdit.text.isEmpty()) {
                toastShort(R.string.mqtt_client_id_empty)
                return@setOnClickListener
            }
            var list: List<String> = ArrayList()
            if (mSubtopicEdit.text.isNotEmpty()) {
                list = StringUtils.getListBySeparator(mSubtopicEdit.text.toString(), ",")
            }
            connect(mUrlEdit.text.toString(), mClientIdEdit.text.toString(), list)
        }

        // 发送
        mSendBtn.setOnClickListener {
            if (mHermes == null || mHermes?.isConnected() == false) {
                toastShort(R.string.mqtt_client_unconnected)
                return@setOnClickListener
            }
            if (mSendTpoicEdit.text.isEmpty()) {
                toastShort(R.string.mqtt_send_topic_empty)
                return@setOnClickListener
            }
            if (mSendEdit.text.isEmpty()) {
                toastShort(R.string.mqtt_send_content_empty)
                return@setOnClickListener
            }
            mHermes?.sendTopic(mSendTpoicEdit.text.toString(), mSendEdit.text.toString())
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

        // 设置静默
        mSlientBtn.setOnClickListener {
            mHermes?.setSilent(true)
        }

        // 设置非静默
        mUnslientBtn.setOnClickListener {
            mHermes?.setSilent(false)
        }
    }

    override fun initData() {
        super.initData()
        mUrlEdit.setText(DEFAULT_URL)
        mUrlEdit.setSelection(mUrlEdit.length())

        mClientIdEdit.setText(DEFAULT_CLIENT_ID)
        mClientIdEdit.setSelection(mClientIdEdit.length())

        mSubtopicEdit.setText(DEFAULT_SUB_TOPIC)
        mSubtopicEdit.setSelection(mSubtopicEdit.length())

        mSendTpoicEdit.setText(DEFAULT_SEND_TOPIC)
        mSendTpoicEdit.setSelection(mSendTpoicEdit.length())

        mSendEdit.setText(DEFAULT_SEND_CONTENT)
        mSendEdit.setSelection(mSendEdit.length())
        showStatusCompleted()
    }

    /** 连接，地址[url]，客户端id[clientId]，订阅主题[subTopic] */
    private fun connect(url: String, clientId: String, subTopic: List<String>) {
        if (mHermes != null){
            if (mHermes?.isConnected() == false){
                mHermes?.connect()
            }
            return
        }
        mHermes = HermesAgent.create()
                .setConnectType(HermesAgent.MQTT)
                .setUrl(url)
                .setClientId(clientId)
                .setPrintLog(true)
                .setLogTag("HermesLog")
                .setSubTopics(subTopic)
                .setOnConnectListener(object : OnConnectListener {
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
                .setOnSendListener(object : OnSendListener {
                    override fun onSendComplete(topic: String, content: String) {
                        logResult("发送成功 : topic ---> $topic   $content")
                    }

                    override fun onSendFailure(topic: String, cause: Throwable) {
                        logResult("发送失败 : topic ---> $topic   ${cause.message}")
                    }
                })
                .setOnSubscribeListener(object : OnSubscribeListener {
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


















    /** 打印信息[result] */
    private fun logResult(result: String) {
        mLog += DateUtils.getCurrentFormatString(DateUtils.TYPE_8).append(" : ").append(result).append("\n")
        mResultTv.text = mLog
        mScrollView.post {
            mScrollView.smoothScrollTo(getScreenWidth(), getScreenHeight())
        }
    }

}