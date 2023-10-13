package com.lodz.android.hermes.mqtt.base.contract

import com.lodz.android.hermes.mqtt.base.bean.eun.MqttAction

/**
 * 连接操作回调
 * @author zhouL
 * @date 2023/10/12
 */
interface ConnectActionListener {

    /** 主键[clientKey]的连接操作[action]成功 */
    fun onSuccess(action: MqttAction, clientKey: String)

    /** 主键[clientKey]的连接操作[action]失败，提示语[errorMsg]，异常[t] */
    fun onFailure(action: MqttAction, clientKey: String, errorMsg: String, t: Throwable)
}