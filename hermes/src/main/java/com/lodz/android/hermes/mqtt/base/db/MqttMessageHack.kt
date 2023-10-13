package com.lodz.android.hermes.mqtt.base.db

import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * MqttMessage的继承类，开放setDuplicate方法
 * @author zhouL
 * @date 2023/10/12
 */
class MqttMessageHack(payload: ByteArray) : MqttMessage(payload) {

    fun setDuplicateHack(dup: Boolean) {
        super.setDuplicate(dup)
    }

}