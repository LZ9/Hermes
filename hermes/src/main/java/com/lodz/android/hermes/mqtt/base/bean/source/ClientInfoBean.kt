package com.lodz.android.hermes.mqtt.base.bean.source

import com.lodz.android.hermes.mqtt.base.bean.eun.Ack
import org.eclipse.paho.client.mqttv3.MqttConnectOptions

/**
 * 客户端信息
 * @author zhouL
 * @date 2023/10/12
 */
open class ClientInfoBean(
    private val clientKey: String, // 客户端主键
    private val serverURI: String, // 服务端路径
    private val clientId: String, // 客户端ID
    private val options: MqttConnectOptions, // 连接配置
    private val ackType: Ack // 接受消息的确认模式
) {
    fun getClientKey() = clientKey

    fun getServerURI() = serverURI

    fun getClientId() = clientId

    fun getAckType() = ackType

    fun getConnectOptions() = options
}