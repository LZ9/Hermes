package com.lodz.android.hermes.mqtt.base.connection

/**
 * MqttConnection操作监听器
 * @author zhouL
 * @date 2023/10/20
 */
interface OnMcActionListener {

    fun onSuccess(clientKey: String)

    fun onFail(clientKey: String, t: Throwable)

}