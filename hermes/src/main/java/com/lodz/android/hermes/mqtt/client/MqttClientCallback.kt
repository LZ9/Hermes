package com.lodz.android.hermes.mqtt.client

import com.lodz.android.hermes.mqtt.base.db.DbStoredData
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * MQTT客户端回调监听器
 * @author zhouL
 * @date 2023/12/7
 */
interface MqttClientCallback {

    fun connectionLost(cause: Throwable)

    fun messageArrived(topic: String, message: MqttMessage, data: DbStoredData)

    fun deliveryComplete(token: IMqttDeliveryToken?)

    fun connectComplete(reconnect: Boolean, serverURI: String)
}