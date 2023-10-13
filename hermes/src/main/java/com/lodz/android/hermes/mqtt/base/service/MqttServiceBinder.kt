package com.lodz.android.hermes.mqtt.base.service

import android.os.Binder

/**
 * @author zhouL
 * @date 2023/10/12
 */
class MqttServiceBinder(private val service: MqttService) : Binder() {

    fun getService() = service

}