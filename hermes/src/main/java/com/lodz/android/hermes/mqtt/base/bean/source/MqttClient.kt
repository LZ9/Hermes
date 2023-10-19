package com.lodz.android.hermes.mqtt.base.bean.source

import android.content.Context
import com.lodz.android.hermes.mqtt.base.MqttConnection
import com.lodz.android.hermes.mqtt.base.bean.eun.Ack
import com.lodz.android.hermes.mqtt.base.db.MessageStore
import org.eclipse.paho.client.mqttv3.MqttClientPersistence
import org.eclipse.paho.client.mqttv3.MqttConnectOptions

/**
 * MQTT客户端
 * @author zhouL
 * @date 2023/10/12
 */
class MqttClient(
    context: Context, // 上下文
    clientKey: String, // 客户端主键
    serverURI: String, // 服务端路径
    clientId: String, // 客户端ID
    options: MqttConnectOptions, // 连接配置
    private val persistence: MqttClientPersistence, // 持久层接口
    ackType: Ack, // 接受消息的确认模式
) : ClientInfoBean(clientKey, serverURI, clientId, options, ackType) {

    private val connection: MqttConnection

    init {
        connection = MqttConnection(context, this, persistence)
    }

    fun getPersistence() = persistence

    fun getConnection() = connection
}