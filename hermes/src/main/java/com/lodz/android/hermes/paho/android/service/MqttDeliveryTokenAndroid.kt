//package com.lodz.android.hermes.paho.android.service
//
//import info.mqtt.android.service.MqttAndroidClient
//import org.eclipse.paho.client.mqttv3.IMqttActionListener
//import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
//import org.eclipse.paho.client.mqttv3.MqttMessage
//
//
///**
// * <p>
// * Implementation of the IMqttDeliveryToken interface for use from within the
// * MqttAndroidClient implementation
// */
//class MqttDeliveryTokenAndroid(
//    client: MqttAndroidClient,
//    userContext: Any?,
//    listener: IMqttActionListener?,
//    private var msg: MqttMessage
//) : MqttTokenAndroid(client, userContext, listener), IMqttDeliveryToken {
//
//    override fun getMessage(): MqttMessage = msg
//
//    fun setMessage(message: MqttMessage) {
//        msg = message
//    }
//
//    fun notifyDelivery(delivered: MqttMessage) {
//        msg = delivered
//        super.notifyComplete()
//    }
//}