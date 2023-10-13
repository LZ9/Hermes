package com.lodz.android.hermes.mqtt.base.bean.event

import android.accounts.NetworkErrorException
import com.lodz.android.hermes.mqtt.base.bean.eun.MqttAction
import com.lodz.android.hermes.mqtt.base.db.DbStoredData
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * 连接事件
 * @author zhouL
 * @date 2023/10/12
 */
class MqttEvent(
    val clientKey: String, // 客户端主键
    val action: MqttAction, // 执行操作
    val result : Int // 执行结果
) {

    /** 异常提示语 */
    var errorMsg = ""

    /** 异常 */
    var t: Throwable? = null

    /** 消息数据 */
    var data: DbStoredData? = null

    /** 是否重连 */
    var isReconnect = false

    /** 服务端地址 */
    var serverURI = ""

    /** 发布消息的送达票据 */
    var token: IMqttDeliveryToken? = null

    /** 主题 */
    var topic = ""

    /** 主题 */
    var topics = arrayOf<String>()

    /** 消息数据 */
    var message: MqttMessage? = null

    companion object {
        /** 成功 */
        const val RESULT_SUCCESS = 0

        /** 失败 */
        const val RESULT_FAIL = 1

        /** 创建失败事件 */
        fun createFail(clientKey: String, action: MqttAction, errorMsg: String, t: Throwable): MqttEvent {
            val event = MqttEvent(clientKey, action, RESULT_FAIL)
            event.errorMsg = errorMsg
            event.t = t
            return event
        }

        /** 创建成功事件 */
        fun createSuccess(clientKey: String, action: MqttAction): MqttEvent = MqttEvent(clientKey, action, RESULT_SUCCESS)


        /** 创建消息到达事件 */
        fun createMsgArrived(clientKey: String, data: DbStoredData): MqttEvent {
            val event = MqttEvent(clientKey, MqttAction.ACTION_MSG_ARRIVED, RESULT_SUCCESS)
            event.data = data
            return event
        }

        /** 创建连接丢失事件 */
        fun createConnectionLost(clientKey: String, t: Throwable?): MqttEvent {
            val event = MqttEvent(clientKey, MqttAction.ACTION_CONNECTION_LOST, RESULT_SUCCESS)
            event.t = t ?: NetworkErrorException()
            return event
        }

        /** 创建连接完成事件 */
        fun createConnectComplete(clientKey: String, isReconnect: Boolean, serverURI: String): MqttEvent {
            val event = MqttEvent(clientKey, MqttAction.ACTION_CONNECT_COMPLETE, RESULT_SUCCESS)
            event.isReconnect = isReconnect
            event.serverURI = serverURI
            return event
        }

        /** 创建发送的消息已到达事件 */
        fun createDeliveryComplete(clientKey: String, token: IMqttDeliveryToken?): MqttEvent {
            val event = MqttEvent(clientKey, MqttAction.ACTION_DELIVERY_COMPLETE, RESULT_SUCCESS)
            event.token = token
            return event
        }

        /** 创建消息发布成功事件 */
        fun createPublishSuccess(clientKey: String, topic: String, message: MqttMessage): MqttEvent {
            val event = MqttEvent(clientKey, MqttAction.ACTION_PUBLISH_MSG, RESULT_SUCCESS)
            event.topic = topic
            event.message = message
            return event
        }

        /** 创建消息发布失败事件 */
        fun createPublishFail(clientKey: String, topic: String, errorMsg: String, t: Throwable): MqttEvent {
            val event = MqttEvent(clientKey, MqttAction.ACTION_PUBLISH_MSG, RESULT_FAIL)
            event.errorMsg = errorMsg
            event.t = t
            event.topic = topic
            return event
        }

        /** 创建订阅成功事件 */
        fun createSubscribeSuccess(clientKey: String, topics: Array<String>): MqttEvent {
            val event = MqttEvent(clientKey, MqttAction.ACTION_SUBSCRIBE, RESULT_SUCCESS)
            event.topics = topics
            return event
        }

        /** 创建订阅失败事件 */
        fun createSubscribeFail(clientKey: String, topics: Array<String>, errorMsg: String, t: Throwable): MqttEvent {
            val event = MqttEvent(clientKey, MqttAction.ACTION_SUBSCRIBE, RESULT_FAIL)
            event.errorMsg = errorMsg
            event.t = t
            event.topics = topics
            return event
        }

        /** 创建解订阅成功事件 */
        fun createUnsubscribeSuccess(clientKey: String, topics: Array<String>): MqttEvent {
            val event = MqttEvent(clientKey, MqttAction.ACTION_UNSUBSCRIBE, RESULT_SUCCESS)
            event.topics = topics
            return event
        }

        /** 创建解订阅失败事件 */
        fun createUnsubscribeFail(clientKey: String, topics: Array<String>, errorMsg: String, t: Throwable): MqttEvent {
            val event = MqttEvent(clientKey, MqttAction.ACTION_UNSUBSCRIBE, RESULT_FAIL)
            event.errorMsg = errorMsg
            event.t = t
            event.topics = topics
            return event
        }

        /** 创建连接成功事件 */
        fun createConnectSuccess(clientKey: String): MqttEvent = MqttEvent(clientKey, MqttAction.ACTION_CONNECT, RESULT_SUCCESS)

        /** 创建连接失败事件 */
        fun createConnectFail(clientKey: String, errorMsg: String, t: Throwable): MqttEvent {
            val event = MqttEvent(clientKey, MqttAction.ACTION_CONNECT, RESULT_FAIL)
            event.errorMsg = errorMsg
            event.t = t
            return event
        }

        /** 创建失败事件 */
        fun createDisconnectFail(clientKey: String, errorMsg: String, t: Throwable): MqttEvent {
            val event = MqttEvent(clientKey, MqttAction.ACTION_DISCONNECT, RESULT_FAIL)
            event.errorMsg = errorMsg
            event.t = t
            return event
        }

        /** 创建成功事件 */
        fun createDisconnectSuccess(clientKey: String): MqttEvent = MqttEvent(clientKey, MqttAction.ACTION_DISCONNECT, RESULT_SUCCESS)
    }
}