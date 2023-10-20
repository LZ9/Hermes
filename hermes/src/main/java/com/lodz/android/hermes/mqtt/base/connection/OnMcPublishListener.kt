package com.lodz.android.hermes.mqtt.base.connection

import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * MqttConnection消息发布操作监听器
 * @author zhouL
 * @date 2023/10/20
 */
interface OnMcPublishListener {

    fun onSuccess(clientKey: String, topic: String, message: MqttMessage)

    fun onFail(clientKey: String, topic: String, t: Throwable)


}