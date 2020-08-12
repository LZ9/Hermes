package com.lodz.android.hermesdemo

import android.content.Context
import androidx.multidex.MultiDex
import com.lodz.android.pandora.base.application.BaseApplication

/**
 * @author zhouL
 * @date 2020/8/12
 */
class App :BaseApplication(){

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onStartCreate() {
    }

    override fun onExit() {
    }
}