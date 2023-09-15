//package com.lodz.android.hermes.paho.android.service
//
//import android.os.Binder
//
///**
// * What the Service passes to the Activity on binding:-
// * <ul>
// * <li>a reference to the Service
// * <li>the activityToken provided when the Service was started
// * </ul>
// */
//class MqttServiceBinder(private val mqttService: MqttService) : Binder() {
//
//    private var activityToken: String = ""
//
//    /** a reference to the Service */
//    fun getService(): MqttService = mqttService
//
//    fun setActivityToken(activityToken: String) {
//        this.activityToken = activityToken
//    }
//
//    /** the activityToken provided when the Service was started */
//    fun getActivityToken(): String = activityToken
//
//}