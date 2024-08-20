package com.lodz.android.hermesdemo.mqttservice.event

/**
 * 创建MQTT
 * @author zhouL
 * @date 2024/5/24
 */
class MqttCreate(val url: String, val clientId: String, val topic: String) : MqttCommandEvent()