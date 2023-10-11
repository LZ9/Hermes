package com.lodz.android.hermesdemo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.lodz.android.corekt.anko.*
import com.lodz.android.corekt.utils.DateUtils
import com.lodz.android.hermes.contract.*
import com.lodz.android.hermes.modules.HermesAgent
import com.lodz.android.hermes.mqtt.OnConnectListener
import com.lodz.android.hermes.mqtt.OnSendListener
import com.lodz.android.hermes.mqtt.OnSubscribeListener
import com.lodz.android.hermes.mqtt.OnUnsubscribeListener
import com.lodz.android.hermesdemo.databinding.ActivityMqttBinding
import com.lodz.android.pandora.base.activity.BaseActivity
import com.lodz.android.pandora.utils.viewbinding.bindingLayout
import com.lodz.android.pandora.widget.base.TitleBarLayout
import java.nio.ByteBuffer

/**
 * MQTT测试类
 * @author zhouL
 * @date 2020/3/17
 */
class MqttActivity : BaseActivity(){

    companion object {

        /** 默认地址 */
        private const val DEFAULT_URL = "tcp://192.168.1.193:1883"

        /** 默认客户端id */
        private const val DEFAULT_CLIENT_ID = "12345"

        /** 默认订阅主题 */
        private const val DEFAULT_SUB_TOPIC = "test.topic,test.token"

        /** 默认发送主题 */
        private const val DEFAULT_SEND_TOPIC = "test.client"

        /** 默认发送内容 */
        private const val DEFAULT_SEND_CONTENT = "测试数据"

        /** 默认新增订阅主题 */
        private const val DEFAULT_ADD_TOPIC = "test.topic,test.queue"

        fun start(context: Context) {
            context.startActivity(Intent(context, MqttActivity::class.java))
        }
    }

    private val mBinding: ActivityMqttBinding by bindingLayout(ActivityMqttBinding::inflate)

    override fun getViewBindingLayout(): View = mBinding.root

    /** 推送客户端 */
    private var mHermes: Hermes? = null

    override fun findViews(savedInstanceState: Bundle?) {
        super.findViews(savedInstanceState)
        initTitleBarLayout(getTitleBarLayout())
    }

    private fun initTitleBarLayout(titleBarLayout: TitleBarLayout) {
        titleBarLayout.setTitleName(R.string.mqtt_title)
        titleBarLayout.setBackgroundColor(getColorCompat(R.color.colorAccent))
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
        mHermes?.release()
        mHermes = null
    }

    override fun setListeners() {
        super.setListeners()
        // 创建
        mBinding.createBtn.setOnClickListener {
            if (mBinding.urlEdit.text.isEmpty()) {
                toastShort(R.string.mqtt_url_empty)
                return@setOnClickListener
            }
            if (mBinding.clientIdEdit.text.isEmpty()) {
                toastShort(R.string.mqtt_client_id_empty)
                return@setOnClickListener
            }
            var list: List<String> = ArrayList()
            if (mBinding.subtopicEdit.text.isNotEmpty()) {
                list = mBinding.subtopicEdit.text.toString().getListBySeparator(",")
            }
            create(mBinding.urlEdit.text.toString(), mBinding.clientIdEdit.text.toString(), list)
        }

        // 发送
        mBinding.sendBtn.setOnClickListener {
            if (mHermes == null || mHermes?.isConnected() == false) {
                toastShort(R.string.mqtt_client_unconnected)
                return@setOnClickListener
            }
            if (mBinding.sendTopicEdit.text.isEmpty()) {
                toastShort(R.string.mqtt_send_topic_empty)
                return@setOnClickListener
            }
            if (mBinding.sendEdit.text.isEmpty()) {
                toastShort(R.string.mqtt_send_content_empty)
                return@setOnClickListener
            }
            mHermes?.sendTopic(mBinding.sendTopicEdit.text.toString(), mBinding.sendEdit.text.toString())
        }

        // 清空日志按钮
        mBinding.cleanBtn.setOnClickListener {
            mBinding.resultTv.text = ""
        }

        // 连接按钮
        mBinding.connectBtn.setOnClickListener {
            mHermes?.connect()
        }

        // 断开按钮
        mBinding.disconnectBtn.setOnClickListener {
            mHermes?.disconnect()
        }

        // 设置静默
        mBinding.silentBtn.setOnClickListener {
            mHermes?.setSilent(true)
        }

        // 设置非静默
        mBinding.unsilentBtn.setOnClickListener {
            mHermes?.setSilent(false)
        }

        // 动态添加订阅主题
        mBinding.addTopicBtn.setOnClickListener {
            if (mHermes == null || mHermes?.isConnected() == false) {
                toastShort(R.string.mqtt_client_unconnected)
                return@setOnClickListener
            }
            if (mBinding.addTopicEdit.text.isEmpty()) {
                toastShort(R.string.mqtt_add_topic_empty)
                return@setOnClickListener
            }
            mHermes?.setSubTopic(mBinding.addTopicEdit.text.toString().getListBySeparator(","))
        }

        // 动态删除订阅主题
        mBinding.removeTopicBtn.setOnClickListener {
            if (mHermes == null || mHermes?.isConnected() == false) {
                toastShort(R.string.mqtt_client_unconnected)
                return@setOnClickListener
            }
            if (mBinding.removeTopicEdit.text.isEmpty()) {
                toastShort(R.string.mqtt_remove_topic_empty)
                return@setOnClickListener
            }
            mHermes?.unsubscribe(mBinding.removeTopicEdit.text.toString().getListBySeparator(","))
        }
    }

    override fun initData() {
        super.initData()
        mBinding.urlEdit.setText(DEFAULT_URL)
        mBinding.urlEdit.setSelection(mBinding.urlEdit.length())

        mBinding.clientIdEdit.setText(DEFAULT_CLIENT_ID)
        mBinding.clientIdEdit.setSelection(mBinding.clientIdEdit.length())

        mBinding.subtopicEdit.setText(DEFAULT_SUB_TOPIC)
        mBinding.subtopicEdit.setSelection(mBinding.subtopicEdit.length())

        mBinding.sendTopicEdit.setText(DEFAULT_SEND_TOPIC)
        mBinding.sendTopicEdit.setSelection(mBinding.sendTopicEdit.length())

        mBinding.sendEdit.setText(DEFAULT_SEND_CONTENT)
        mBinding.sendEdit.setSelection(mBinding.sendEdit.length())

        mBinding.addTopicEdit.setText(DEFAULT_ADD_TOPIC)
        mBinding.addTopicEdit.setSelection(mBinding.addTopicEdit.length())

        mBinding.removeTopicEdit.setText(DEFAULT_SUB_TOPIC)
        mBinding.removeTopicEdit.setSelection(mBinding.removeTopicEdit.length())
        showStatusCompleted()
    }

    /** 连接，地址[url]，客户端id[clientId]，订阅主题[subTopic] */
    private fun create(url: String, clientId: String, subTopic: List<String>) {
        if (mHermes != null){
            releaseHermes()
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
            .setOnSubscribeListener(object : OnSubscribeListener {
                override fun onSubscribeSuccess(topic: Array<String>) {
                    logResult("订阅成功 : topic ---> $topic")
                    logResult("当前订阅列表 : ${mHermes?.getSubscribeTopic()}")
                }

                override fun onSubscribeFailure(topic: Array<String>, cause: Throwable) {
                    logResult("订阅失败 : topic ---> $topic   ${cause.message}")
                }

                override fun onMsgArrived(subTopic: String, msg: String) {
                    logResult("消息到达($subTopic)： $msg")
                }
            })
            .setOnUnsubscribeListener(object : OnUnsubscribeListener {
                override fun onUnsubscribeSuccess(topics: Array<String>) {
                    logResult("解除订阅成功 : topic ---> $topics")
                    logResult("剩余订阅列表 : ${mHermes?.getSubscribeTopic()}")
                }

                override fun onUnsubscribeFailure(topics: Array<String>, cause: Throwable) {
                    logResult("解除订阅失败 : topic ---> $topics   ${cause.message}")
                }
            })
            .buildConnect(applicationContext)
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