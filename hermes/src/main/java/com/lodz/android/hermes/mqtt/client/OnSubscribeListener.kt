package com.lodz.android.hermes.mqtt.client

import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * 推送接口回调
 * @author zhouL
 * @date 2019/12/20
 */
interface OnSubscribeListener {

    /** 主题订阅成功，主题名称[topics] */
    fun onSuccess(topics: Array<String>)

    /** 主题订阅失败，主题名称[topics]，异常[cause] */
    fun onFailure(topics: Array<String>, cause: Throwable)

    /** 后台消息到达，订阅主题[subTopic]，消息[msg] */
    fun onMsgArrived(subTopic: String, msg: MqttMessage)
}