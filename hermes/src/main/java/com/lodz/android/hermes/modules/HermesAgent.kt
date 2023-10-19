package com.lodz.android.hermes.modules

import com.lodz.android.hermes.contract.*
import com.lodz.android.hermes.mqtt.client.MqttClientImpl
import com.lodz.android.hermes.ws.client.WebSocketClientImpl
import com.lodz.android.hermes.ws.server.WebSocketServerImpl

/**
 * 推送代理
 * @author zhouL
 * @date 2019/12/21
 */
class HermesAgent private constructor() {

    companion object {

        /** 创建Mqtt客户端连接对象 */
        @JvmStatic
        fun createMqttClient(): HermesMqttClient = MqttClientImpl()

        /** 创建WebSocket客户端连接对象 */
        @JvmStatic
        fun createWebSocketClient(): HermesWebSocketClient = WebSocketClientImpl()

        /** 创建WebSocket服务端连接对象 */
        @JvmStatic
        fun createWebSocketServer(): HermesWebSocketServer = WebSocketServerImpl()
    }
}