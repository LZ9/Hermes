//package com.lodz.android.hermes.paho.android.service
//
///**
// * The Acknowledgment mode for messages received from {@link MqttCallback#messageArrived(String, MqttMessage)}
// */
//enum class Ack {
//    /**
//     * As soon as the {@link MqttCallback#messageArrived(String, MqttMessage)} returns,
//     * the message has been acknowledged as received .
//     */
//    AUTO_ACK,
//
//    /**
//     * When {@link MqttCallback#messageArrived(String, MqttMessage)} returns, the message
//     * will not be acknowledged as received, the application will have to make an acknowledgment call
//     * to {@link MqttAndroidClient} using {@link MqttAndroidClient#acknowledgeMessage(String)}
//     */
//    MANUAL_ACK
//}