package com.lodz.android.hermes.mqtt.base.connection

import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * MqttConnection订阅/取消订阅操作监听器
 * @author zhouL
 * @date 2023/10/20
 */
interface OnMcSubListener {

    fun onSuccess(clientKey: String, topic: Array<String>)

    fun onFail(clientKey: String, topic: Array<String>, t: Throwable)


}