package com.lodz.android.hermes.mqtt.base.contract

import com.lodz.android.hermes.mqtt.base.bean.eun.MqttAction

/**
 * 断连操作回调
 * @author zhouL
 * @date 2023/10/12
 */
interface DisconnectActionListener {

    /** 主键[clientKey]的断连操作[action]成功 */
    fun onSuccess(action: MqttAction, clientKey: String)

    /** 主键[clientKey]的断连操作[action]失败，提示语[errorMsg]，异常[t] */
    fun onFailure(action: MqttAction, clientKey: String, errorMsg: String, t: Throwable)
}