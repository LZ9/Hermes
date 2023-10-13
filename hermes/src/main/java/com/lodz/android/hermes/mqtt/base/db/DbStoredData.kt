package com.lodz.android.hermes.mqtt.base.db

import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * 数据库存储对象
 * @author zhouL
 * @date 2023/10/12
 */
class DbStoredData(
    val messageId: String, // 消息ID
    val clientKey: String, // 客户端主键
    val topic: String, // 消息主题
    val message: MqttMessage // 消息内容
)