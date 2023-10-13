package com.lodz.android.hermes.mqtt.base.contract

import com.lodz.android.hermes.mqtt.base.bean.eun.MqttAction
import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * 发布消息操作回调
 * @author zhouL
 * @date 2023/10/12
 */
interface PublishActionListener {

    /** 主键[clientKey]的发布消息操作[action]成功，主题[topic]，消息内容[message] */
    fun onSuccess(action: MqttAction, clientKey: String, topic: String, message: MqttMessage)

    /** 主键[clientKey]的发布消息操作[action]失败，主题[topic]，提示语[errorMsg]，异常[t] */
    fun onFailure(action: MqttAction, clientKey: String, topic: String, errorMsg: String,t: Throwable)

}