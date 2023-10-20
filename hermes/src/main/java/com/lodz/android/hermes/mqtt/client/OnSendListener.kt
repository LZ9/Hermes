package com.lodz.android.hermes.mqtt.client

import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * 发送监听器
 * @author zhouL
 * @date 2019/12/20
 */
interface OnSendListener {

    /** 发送数据完成，发送主题[topic]，内容[data] */
    fun onComplete(topic: String, data: MqttMessage)

    /** 发送数据失败，发送主题[topic]，异常[cause] */
    fun onFailure(topic: String, cause: Throwable)
}