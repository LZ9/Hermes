package com.lodz.android.hermes.contract

import android.content.Context
import com.lodz.android.hermes.mqtt.base.bean.eun.Ack
import com.lodz.android.hermes.mqtt.client.OnBuildListener
import com.lodz.android.hermes.mqtt.client.OnConnectListener
import com.lodz.android.hermes.mqtt.client.OnSendListener
import com.lodz.android.hermes.mqtt.client.OnSubscribeListener
import com.lodz.android.hermes.mqtt.client.OnUnsubscribeListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttClientPersistence
import org.eclipse.paho.client.mqttv3.MqttConnectOptions

/**
 * 推送客户端
 * @author zhouL
 * @date 2019/12/20
 */
interface HermesMqttClient : Hermes {

    override fun init(context: Context): HermesMqttClient

    override fun setLogTag(tag: String): HermesMqttClient

    override fun setSilent(isSilent: Boolean): HermesMqttClient

    override fun setPrintLog(isPrint: Boolean): HermesMqttClient

    /** 创建对象，服务端地址[serverURI]，客户端编号[clientId]，连接配置[options]，接受消息的确认模式[ackType]，持久层接口[persistence] */
    fun build(
        serverURI: String,
        clientId: String,
        options: MqttConnectOptions? = null,
        ackType: Ack = Ack.AUTO_ACK,
        persistence: MqttClientPersistence? = null
    ): HermesMqttClient

    /** 构建监听器，监听器[listener] */
    fun setOnBuildListener(listener: OnBuildListener?): HermesMqttClient

    /** 设置订阅监听器，监听器[listener] */
    fun setOnSubscribeListener(listener: OnSubscribeListener?): HermesMqttClient

    /** 设置解除订阅监听器，监听器[listener] */
    fun setOnUnsubscribeListener(listener: OnUnsubscribeListener?): HermesMqttClient

    /** 设置连接监听器，监听器[listener] */
    fun setOnConnectListener(listener: OnConnectListener?): HermesMqttClient

    /** 设置发送监听器，监听器[listener] */
    fun setOnSendListener(listener: OnSendListener?): HermesMqttClient

    /** 发送主题内容，主题[topic]，内容[data] */
    fun sendData(topic: String, data: String): IMqttDeliveryToken?

    /** 发送主题内容，主题[topic]，内容[data] */
    fun sendData(topic: String, data: ByteArray): IMqttDeliveryToken?

    /** 订阅主题 */
    fun subscribe(topics: Array<String>): HermesMqttClient

    /** 取消订阅主题列表[topics] */
    fun unsubscribe(topics: Array<String>): HermesMqttClient

    /** 获取已经订阅的主题 */
    fun getSubscribeTopic(): Array<String>

    /** 断开连接 */
    fun disconnect(quiesceTimeout: Long)

    /** 断开连接 */
    override fun disconnect() {
        disconnect(-1)
    }
}