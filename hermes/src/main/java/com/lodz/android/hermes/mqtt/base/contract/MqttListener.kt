package com.lodz.android.hermes.mqtt.base.contract

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * mqtt监听器
 * @author zhouL
 * @date 2023/10/12
 */
interface MqttListener {

    /** 与服务器连接丢失 */
    fun connectionLost(clientKey: String, cause: Throwable)

    /** 消息到达 */
    fun messageArrived(clientKey: String, topic: String, messageId: String, message: MqttMessage)

    /** 消息传递到服务端完成 */
    fun deliveryComplete(clientKey: String, token: IMqttDeliveryToken?)

    /** 与服务器连接完成 */
    fun connectComplete(clientKey: String, reconnect: Boolean, serverURI: String)

}