package com.lodz.android.hermes.mqtt.base.contract

import com.lodz.android.hermes.mqtt.base.bean.eun.MqttAction
import com.lodz.android.hermes.mqtt.base.bean.event.MqttEvent
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.greenrobot.eventbus.EventBus

/**
 * 断连监听接口实现
 * @author zhouL
 * @date 2023/10/12
 */
class DisconnectMqttEventActionListener(
    clientKey: String,
    errorMsg: String
) : DefMqttEventActionListener(clientKey, MqttAction.ACTION_DISCONNECT, errorMsg) {

    override fun onSuccess(asyncActionToken: IMqttToken?) {
        EventBus.getDefault().post(MqttEvent.createDisconnectSuccess(clientKey))
    }

    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
        exception?.printStackTrace()
        EventBus.getDefault().post(MqttEvent.createDisconnectFail(clientKey, errorMsg, getThrowable(exception)))
    }
}