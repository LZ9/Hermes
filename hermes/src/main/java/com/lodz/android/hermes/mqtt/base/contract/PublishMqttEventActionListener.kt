package com.lodz.android.hermes.mqtt.base.contract

import com.lodz.android.hermes.mqtt.base.bean.eun.MqttAction
import com.lodz.android.hermes.mqtt.base.bean.event.MqttEvent
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.greenrobot.eventbus.EventBus

/**
 * 发布消息监听接口实现
 * @author zhouL
 * @date 2023/10/12
 */
class PublishMqttEventActionListener(
    clientKey: String,
    errorMsg: String,
    private val topic: String,
    private val message: MqttMessage
) : DefMqttEventActionListener(clientKey, MqttAction.ACTION_PUBLISH_MSG, errorMsg) {

    override fun onSuccess(asyncActionToken: IMqttToken?) {
        EventBus.getDefault().post(MqttEvent.createPublishSuccess(clientKey, topic, message))
    }

    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
        exception?.printStackTrace()
        EventBus.getDefault().post(MqttEvent.createPublishFail(clientKey, topic, errorMsg, getThrowable(exception)))
    }
}