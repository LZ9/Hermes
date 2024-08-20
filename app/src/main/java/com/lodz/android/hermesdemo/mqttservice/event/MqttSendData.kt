package com.lodz.android.hermesdemo.mqttservice.event

/**
 * 发送信息
 * @author zhouL
 * @date 2024/5/24
 */
class MqttSendData(val topic: String, val content: String) : MqttCommandEvent()