package com.lodz.android.hermes.mqtt.base.contract

/**
 * 后台服务启动操作回调
 * @author zhouL
 * @date 2023/10/12
 */
interface ServiceStartActionListener {

    /** 后台服务启动成功 */
    fun onSuccess()

    /** 后台服务启动失败，提示语[errorMsg]，异常[t] */
    fun onFailure(errorMsg: String, t: Throwable)

}