package com.lodz.android.hermes.mqtt.base.bean.eun

/**
 * mqtt操作
 * @author zhouL
 * @date 2023/10/12
 */
enum class MqttAction {
    /**
     * 连接
     */
    ACTION_CONNECT,

    /**
     * 断开连接
     */
    ACTION_DISCONNECT,

    /**
     * 订阅
     */
    ACTION_SUBSCRIBE,

    /**
     * 解除订阅
     */
    ACTION_UNSUBSCRIBE,

    /**
     * 发布消息
     */
    ACTION_PUBLISH_MSG,

    /**
     * 创建客户端
     */
    ACTION_CREATE_CLIENT,

    /**
     * 消息到达
     */
    ACTION_MSG_ARRIVED,

    /**
     * 连接断开
     */
    ACTION_CONNECTION_LOST,

    /**
     * 连接完成
     */
    ACTION_CONNECT_COMPLETE,

    /**
     * 消息交付完成
     */
    ACTION_DELIVERY_COMPLETE
}