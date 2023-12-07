package com.lodz.android.hermes.mqtt.base.connection

/**
 * MqttConnection订阅/取消订阅操作监听器
 * @author zhouL
 * @date 2023/10/20
 */
interface OnMcSubListener {

    fun onSuccess(clientKey: String, topics: Array<String>)

    fun onFail(clientKey: String, topics: Array<String>, t: Throwable)


}