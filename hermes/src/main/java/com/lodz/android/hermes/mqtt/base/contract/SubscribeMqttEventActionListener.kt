package com.lodz.android.hermes.mqtt.base.contract

import com.lodz.android.hermes.mqtt.base.bean.eun.MqttAction
import com.lodz.android.hermes.mqtt.base.bean.event.MqttEvent
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.greenrobot.eventbus.EventBus

/**
 * 订阅监听接口实现
 * @author zhouL
 * @date 2023/10/12
 */
class SubscribeMqttEventActionListener(
    clientKey: String,
    errorMsg: String,
    private val topics: Array<String>
) : DefMqttEventActionListener(clientKey, MqttAction.ACTION_SUBSCRIBE, errorMsg) {

    override fun onSuccess(asyncActionToken: IMqttToken?) {
        EventBus.getDefault().post(MqttEvent.createSubscribeSuccess(clientKey, topics))
    }

    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
        exception?.printStackTrace()
        EventBus.getDefault().post(MqttEvent.createSubscribeFail(clientKey, topics, errorMsg, getThrowable(exception)))
    }
}