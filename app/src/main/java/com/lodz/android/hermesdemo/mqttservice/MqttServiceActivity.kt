package com.lodz.android.hermesdemo.mqttservice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.lodz.android.corekt.anko.getColorCompat
import com.lodz.android.corekt.utils.StatusBarUtil
import com.lodz.android.hermesdemo.R
import com.lodz.android.hermesdemo.databinding.ActivityMqttServiceBinding
import com.lodz.android.pandora.base.activity.BaseActivity
import com.lodz.android.pandora.utils.viewbinding.bindingLayout
import com.lodz.android.pandora.widget.base.TitleBarLayout

/**
 * MQTT后台服务测试类
 * @author zhouL
 * @date 2023/12/7
 */
class MqttServiceActivity : BaseActivity() {
    companion object {

        /** 默认地址 */
        private const val DEFAULT_URL = "tcp://192.168.1.37:1883"

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
            context.startActivity(Intent(context, MqttServiceActivity::class.java))
        }
    }

    private val mBinding: ActivityMqttServiceBinding by bindingLayout(ActivityMqttServiceBinding::inflate)

    override fun getViewBindingLayout(): View = mBinding.root

    override fun findViews(savedInstanceState: Bundle?) {
        super.findViews(savedInstanceState)
        initTitleBarLayout(getTitleBarLayout())
        StatusBarUtil.setColor(window, getColorCompat(R.color.color_3a958d))
    }

    private fun initTitleBarLayout(titleBarLayout: TitleBarLayout) {
        titleBarLayout.setTitleName(R.string.mqtt_service_title)
        titleBarLayout.setBackgroundColor(getColorCompat(R.color.color_3a958d))
        titleBarLayout.setTitleTextColor(R.color.white)
    }

    override fun onClickBackBtn() {
        finish()
    }

    override fun initData() {
        showStatusCompleted()
    }

}