package com.lodz.android.hermesdemo

import android.os.Bundle
import com.google.android.material.button.MaterialButton
import com.lodz.android.corekt.anko.bindView
import com.lodz.android.corekt.anko.getColorCompat
import com.lodz.android.pandora.base.activity.BaseActivity
import com.lodz.android.pandora.widget.base.TitleBarLayout

class MainActivity : BaseActivity(){

    /** 断开按钮 */
    private val mMqttBtn by bindView<MaterialButton>(R.id.mqtt_btn)
    /** 断开按钮 */
    private val mWebsocketBtn by bindView<MaterialButton>(R.id.websocket_btn)

    override fun getLayoutId(): Int = R.layout.activity_main

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
        mMqttBtn.setOnClickListener {
            MqttActivity.start(getContext())
        }

        mWebsocketBtn.setOnClickListener {
            WebSocketActivity.start(getContext())
        }
    }

    override fun initData() {
        super.initData()
        showStatusCompleted()
    }
}