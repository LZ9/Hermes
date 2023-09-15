//package com.lodz.android.hermes.paho.android.service
//
//import org.eclipse.paho.client.mqttv3.MqttMessage
//
//
//
//
///**
// * <p>
// * Mechanism for persisting messages until we know they have been received
// * </p>
// * <ul>
// * <li>A Service should store messages as they arrive via
// * {@link #storeArrived(String, String, MqttMessage)}.
// * <li>When a message has been passed to the consuming entity,
// * {@link #discardArrived(String, String)} should be called.
// * <li>To recover messages which have not been definitely passed to the
// * consumer, {@link MessageStore#getAllArrivedMessages(String)} is used.
// * <li>When a clean session is started {@link #clearArrivedMessages(String)} is
// * used.
// * </ul>
// */
//interface MessageStore {
//
//    /** External representation of a stored message */
//    interface StoredMessage {
//        /** the identifier for the message within the store */
//        fun getMessageId(): String
//
//        /** the identifier of the client which stored this message */
//        fun getClientHandle(): String
//
//        /** the topic on which the message was received */
//        fun getTopic(): String
//
//        /** the identifier of the client which stored this message */
//        fun getMessage(): MqttMessage
//    }
//
//    /**
//     * Store a message and return an identifier for it
//     * @param clientHandle identifier for the client
//     * @param message message to be stored
//     * @return a unique identifier for it
//     */
//    fun storeArrived(clientHandle: String, topic: String?, message: MqttMessage?): String
//
//    /**
//     * Discard a message - called when we are certain that an arrived message has reached the application.
//     * @param clientHandle identifier for the client
//     * @param id id of message to be discarded
//     */
//    fun discardArrived(clientHandle: String, id: String): Boolean
//
//    /**
//     * Get all the stored messages, usually for a specific client
//     * @param clientHandle identifier for the client - if null, then messages for all clients are returned
//     */
//    fun getAllArrivedMessages(clientHandle: String): Iterator<StoredMessage>
//
//    /**
//     * Discard stored messages, usually for a specific client
//     * @param clientHandle identifier for the client - if null, then messages for all clients are discarded
//     */
//    fun clearArrivedMessages(clientHandle: String)
//
//    fun close()
//}