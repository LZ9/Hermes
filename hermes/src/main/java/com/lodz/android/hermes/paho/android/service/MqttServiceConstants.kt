//package com.lodz.android.hermes.paho.android.service
//
//
///**
// * const val ious strings used to identify operations or data in the Android MQTT
// * service, mainly used in Intents passed between Activities and the Service.
// */
//object MqttServiceConstants {
//    /* Version information */
//    const val VERSION = "v0"
//
//    /* Attributes of messages <p> Used for the column names in the database */
//    const val DUPLICATE = "duplicate"
//    const val RETAINED = "retained"
//    const val QOS = "qos"
//    const val PAYLOAD = "payload"
//    const val DESTINATION_NAME = "destinationName"
//    const val CLIENT_HANDLE = "clientHandle"
//    const val MESSAGE_ID = "messageId"
//
//    /* Tags for actions passed between the Activity and the Service */
//    const val SEND_ACTION = "send"
//    const val UNSUBSCRIBE_ACTION = "unsubscribe"
//    const val SUBSCRIBE_ACTION = "subscribe"
//    const val DISCONNECT_ACTION = "disconnect"
//    const val CONNECT_ACTION = "connect"
//    const val CONNECT_EXTENDED_ACTION = "connectExtended"
//    const val MESSAGE_ARRIVED_ACTION = "messageArrived"
//    const val MESSAGE_DELIVERED_ACTION = "messageDelivered"
//    const val ON_CONNECTION_LOST_ACTION = "onConnectionLost"
//    const val TRACE_ACTION = "trace"
//
//    /* Identifies an Intent which calls back to the Activity */
//    const val CALLBACK_TO_ACTIVITY = MqttService.TAG + ".callbackToActivity" + "." + VERSION
//
//    /* Identifiers for extra data on Intents broadcast to the Activity */
//    const val CALLBACK_ACTION = MqttService.TAG + ".callbackAction"
//    const val CALLBACK_STATUS = MqttService.TAG + ".callbackStatus"
//    const val CALLBACK_CLIENT_HANDLE = MqttService.TAG + "." + CLIENT_HANDLE
//    const val CALLBACK_ERROR_MESSAGE = MqttService.TAG + ".errorMessage"
//    const val CALLBACK_EXCEPTION_STACK = MqttService.TAG + ".exceptionStack"
//    const val CALLBACK_INVOCATION_CONTEXT = MqttService.TAG + "." + "invocationContext"
//    const val CALLBACK_ACTIVITY_TOKEN = MqttService.TAG + "." + "activityToken"
//    const val CALLBACK_DESTINATION_NAME = MqttService.TAG + '.' + DESTINATION_NAME
//    const val CALLBACK_MESSAGE_ID = MqttService.TAG + '.' + MESSAGE_ID
//    const val CALLBACK_RECONNECT = MqttService.TAG + ".reconnect"
//    const val CALLBACK_SERVER_URI = MqttService.TAG + ".serverURI"
//    const val CALLBACK_MESSAGE_PARCEL = MqttService.TAG + ".PARCEL"
//    const val CALLBACK_TRACE_SEVERITY = MqttService.TAG + ".traceSeverity"
//    const val CALLBACK_TRACE_TAG = MqttService.TAG + ".traceTag"
//    const val CALLBACK_TRACE_ID = MqttService.TAG + ".traceId"
//    const val CALLBACK_ERROR_NUMBER = MqttService.TAG + ".ERROR_NUMBER"
//
//    const val CALLBACK_EXCEPTION = MqttService.TAG + ".exception"
//
//    //Intent prefix for Ping sender.
//    const val PING_SENDER = MqttService.TAG + ".pingSender."
//
//    //Constant for wakelock
//    const val PING_WAKELOCK = MqttService.TAG + ".client."
//    const val WAKELOCK_NETWORK_INTENT = MqttService.TAG + ""
//
//    //Trace severity levels
//    const val TRACE_ERROR = "error"
//    const val TRACE_DEBUG = "debug"
//    const val TRACE_EXCEPTION = "exception"
//
//    //exception code for non MqttExceptions
//    const val NON_MQTT_EXCEPTION = -1
//}