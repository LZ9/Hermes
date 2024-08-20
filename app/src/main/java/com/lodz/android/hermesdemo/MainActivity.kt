package com.lodz.android.hermesdemo

import android.os.Bundle
import android.view.View
import com.lodz.android.corekt.anko.getColorCompat
import com.lodz.android.hermesdemo.databinding.ActivityMainBinding
import com.lodz.android.hermesdemo.mqtt.MqttActivity
import com.lodz.android.hermesdemo.mqttservice.MqttServiceActivity
import com.lodz.android.pandora.base.activity.BaseActivity
import com.lodz.android.pandora.utils.viewbinding.bindingLayout
import com.lodz.android.pandora.widget.base.TitleBarLayout

class MainActivity : BaseActivity(){

    // TODO: 2023/12/8 页面待完善

    private val mBinding: ActivityMainBinding by bindingLayout(ActivityMainBinding::inflate)

    override fun getViewBindingLayout(): View = mBinding.root

    override fun findViews(savedInstanceState: Bundle?) {
        super.findViews(savedInstanceState)
        initTitleBarLayout(getTitleBarLayout())
    }

    private fun initTitleBarLayout(titleBarLayout: TitleBarLayout) {
        titleBarLayout.setTitleName(R.string.app_name)
        titleBarLayout.setBackgroundColor(getColorCompat(R.color.colorAccent))
        titleBarLayout.setTitleTextColor(R.color.white)
        titleBarLayout.needBackButton(false)
    }

    override fun setListeners() {
        super.setListeners()
        mBinding.mqttBtn.setOnClickListener {
            MqttActivity.start(getContext())//MQTT
        }

        mBinding.mqttServiceBtn.setOnClickListener {
            MqttServiceActivity.start(getContext())//MQTT后台服务
        }

        mBinding.websocketBtn.setOnClickListener {
            WebSocketActivity.start(getContext())//Websocket客户端
        }

        mBinding.wsServerBtn.setOnClickListener {
            WebsocketServerActivity.start(getContext())//Websocket服务端
        }
    }

    override fun initData() {
        super.initData()
        showStatusCompleted()
    }
}