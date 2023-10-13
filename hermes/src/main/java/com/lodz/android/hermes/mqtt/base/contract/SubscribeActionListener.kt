package com.lodz.android.hermes.mqtt.base.contract

import com.lodz.android.hermes.mqtt.base.bean.eun.MqttAction

/**
 * 订阅操作回调
 * @author zhouL
 * @date 2023/10/12
 */
interface SubscribeActionListener {

    /** 主键[clientKey]的订阅操作[action]成功，主题[topics] */
    fun onSuccess(action: MqttAction, clientKey: String, topics: Array<String>)

    /** 主键[clientKey]的订阅操作[action]失败，主题[topics]，提示语[errorMsg]，异常[t] */
    fun onFailure(action: MqttAction, clientKey: String, topics: Array<String>, errorMsg: String, t: Throwable)

}