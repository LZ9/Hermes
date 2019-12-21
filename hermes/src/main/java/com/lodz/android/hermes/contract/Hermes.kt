package com.lodz.android.hermes.contract

import android.content.Context
import org.eclipse.paho.client.mqttv3.MqttConnectOptions

/**
 * 推送客户端
 * @author zhouL
 * @date 2019/12/20
 */
interface Hermes {

    /** 初始化，上下文[context]，后台地址[url]，客户端id[clientId]，连接配置[options] */
    fun init(context: Context?, url: String, clientId: String?, options: MqttConnectOptions?)

    /** 设置订阅主题，订阅主题列表[topics] */
    fun setSubTopic(topics: List<String>?)

    /** 设置订阅监听器，监听器[listener] */
    fun setOnSubscribeListener(listener: OnSubscribeListener?)

    /** 设置连接监听器，监听器[listener] */
    fun setOnConnectListener(listener: OnConnectListener?)

    /** 设置发送监听器，监听器[listener] */
    fun setOnSendListener(listener: OnSendListener?)

    /** 发送主题内容，主题[topic]，内容[content] */
    fun sendTopic(topic: String, content: String)

    /** 连接后台 */
    fun connect()

    /** 断开连接 */
    fun disconnect()

    /** 是否已连接 */
    fun isConnected(): Boolean

    /** 订阅主题 */
    fun subscribeTopic()

    /** 设置日志标签[tag] */
    fun setTag(tag: String)

}