//package com.lodz.android.hermes.paho.android.service
//
///**
// * Interface for simple trace handling, pass the trace message to trace
// * callback.
// */
//interface MqttTraceHandler {
//
//    /**
//     * Trace debugging information
//     * @param tag identifier for the source of the trace
//     * @param message the text to be traced
//     */
//    fun traceDebug(tag: String, message: String)
//
//    /**
//     * Trace error information
//     * @param tag identifier for the source of the trace
//     * @param message the text to be traced
//     */
//    fun traceError(tag: String, message: String)
//
//    /**
//     * trace exceptions
//     * @param tag identifier for the source of the trace
//     * @param message the text to be traced
//     * @param e the exception
//     */
//    fun traceException(tag: String, message: String, e: Exception)
//}