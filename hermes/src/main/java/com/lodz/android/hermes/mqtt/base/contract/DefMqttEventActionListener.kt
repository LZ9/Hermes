package com.lodz.android.hermes.mqtt.base.contract

import com.lodz.android.hermes.mqtt.base.bean.eun.MqttAction
import com.lodz.android.hermes.mqtt.base.bean.event.MqttEvent
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.greenrobot.eventbus.EventBus
import java.lang.RuntimeException

/**
 * 默认实现MQTT消息监听接口
 * @author zhouL
 * @date 2023/10/12
 */
open class DefMqttEventActionListener(
    protected val clientKey: String,
    protected val action: MqttAction,
    protected val errorMsg: String
) : IMqttActionListener {

    override fun onSuccess(asyncActionToken: IMqttToken?) {
        EventBus.getDefault().post(MqttEvent.createSuccess(clientKey, action))
    }

    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
        exception?.printStackTrace()
        EventBus.getDefault().post(MqttEvent.createFail(clientKey, action, errorMsg, getThrowable(exception)))
    }

    protected fun getThrowable(t: Throwable?): Throwable = t ?: RuntimeException()
}