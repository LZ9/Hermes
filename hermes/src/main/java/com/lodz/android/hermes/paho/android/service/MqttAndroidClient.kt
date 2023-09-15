//package com.lodz.android.hermes.paho.android.service
//
//import android.content.BroadcastReceiver
//import android.content.ComponentName
//import android.content.Context
//import android.content.Intent
//import android.content.IntentFilter
//import android.content.ServiceConnection
//import android.os.IBinder
//import android.util.SparseArray
//import androidx.localbroadcastmanager.content.LocalBroadcastManager
//import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
//import org.eclipse.paho.client.mqttv3.IMqttActionListener
//import org.eclipse.paho.client.mqttv3.IMqttAsyncClient
//import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
//import org.eclipse.paho.client.mqttv3.IMqttMessageListener
//import org.eclipse.paho.client.mqttv3.IMqttToken
//import org.eclipse.paho.client.mqttv3.MqttCallback
//import org.eclipse.paho.client.mqttv3.MqttClientPersistence
//import org.eclipse.paho.client.mqttv3.MqttConnectOptions
//import org.eclipse.paho.client.mqttv3.MqttException
//import org.eclipse.paho.client.mqttv3.MqttMessage
//import java.util.concurrent.Executors
//
//
///**
// * Enables an android application to communicate with an MQTT server using non-blocking methods.
// * <p>
// * Implementation of the MQTT asynchronous client interface {@link IMqttAsyncClient} , using the MQTT
// * android service to actually interface with MQTT server. It provides android applications a simple programming interface to all features of the MQTT version 3.1
// * specification including:
// * </p>
// * <ul>
// * <li>connect
// * <li>publish
// * <li>subscribe
// * <li>unsubscribe
// * <li>disconnect
// * </ul>
// */
//class MqttAndroidClient(
//    private val context: Context,// context used to pass context to the callback.
//    private val serverURI: String,// serverURI specifies the protocol, host name and port to be used to connect to an MQTT server
//    private val clientId: String,// clientId specifies the name by which this connection should be identified to the server
//    private val persistence: MqttClientPersistence?,// persistence the persistence class to use to store in-flight message. If null then the default persistence mechanism is used
//    private val ackType: Ack // ackType how the application wishes to acknowledge a message has been processed.
//) : BroadcastReceiver(), IMqttAsyncClient {
//
//    companion object {
//
//        private const val SERVICE_NAME = "org.eclipse.paho.android.service.MqttService"
//
//        private const val BIND_SERVICE_FLAG = 0
//
//        private val pool = Executors.newCachedThreadPool()
//    }
//
//    /**
//     * Constructor - create an MqttAndroidClient that can be used to communicate with an MQTT server on android
//     * @param context object used to pass context to the callback.
//     * @param serverURI specifies the protocol, host name and port to be used to connect to an MQTT server
//     * @param clientId specifies the name by which this connection should be identified to the server
//     */
//    constructor(context: Context, serverURI: String, clientId: String) : this(
//        context,
//        serverURI,
//        clientId,
//        null,
//        Ack.AUTO_ACK
//    )
//
//    /**
//     * Constructor - create an MqttAndroidClient that can be used to communicate with an MQTT server on android
//     * @param ctx Application's context
//     * @param serverURI specifies the protocol, host name and port to be used to connect to an MQTT server
//     * @param clientId specifies the name by which this connection should be identified to the server
//     * @param ackType how the application wishes to acknowledge a message has been processed
//     */
//    constructor(ctx: Context, serverURI: String, clientId: String, ackType: Ack) : this(
//        ctx,
//        serverURI,
//        clientId,
//        null,
//        ackType
//    )
//
//    /**
//     * Constructor - create an MqttAndroidClient that can be used to communicate with an MQTT server on android
//     * @param ctx Application's context
//     * @param serverURI specifies the protocol, host name and port to be used to connect to an MQTT server
//     * @param clientId specifies the name by which this connection should be identified to the server
//     * @param persistence The object to use to store persisted data
//     */
//    constructor(
//        ctx: Context,
//        serverURI: String,
//        clientId: String,
//        persistence: MqttClientPersistence
//    ) : this(ctx, serverURI, clientId, persistence, Ack.AUTO_ACK)
//
//
//    // Listener for when the service is connected or disconnected
//    private val serviceConnection = MyServiceConnection()
//
//    // The Android Service which will process our mqtt calls
//    private var mqttService: MqttService? = null
//
//    // An identifier for the underlying client connection, which we can pass to the service
//    private var clientHandle: String = ""
//
//
//    // We hold the various tokens in a collection and pass identifiers for them to the service
//    private val tokenMap = SparseArray<IMqttToken>()
//    private var tokenNumber = 0
//
//    // Connection data
//    private var connectOptions: MqttConnectOptions? = null
//    private var connectToken: IMqttToken? = null
//
//    // The MqttCallback provided by the application
//    private var callback: MqttCallback? = null
//    private var traceCallback: MqttTraceHandler? = null
//
//    //The acknowledgment that a message has been processed by the application
//    private var messageAck: Ack? = null
//    private var traceEnabled = false
//
//    @Volatile
//    private var receiverRegistered = false
//
//    @Volatile
//    private var bindedService = false
//
//    /** ServiceConnection to process when we bind to our service */
//    private inner class MyServiceConnection : ServiceConnection {
//        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            if (service != null && service is MqttServiceBinder) {
//                mqttService = service.getService()
//                bindedService = true
//                // now that we have the service available, we can actually connect...
//                doConnect()
//            }
//        }
//
//        override fun onServiceDisconnected(name: ComponentName?) {
//            mqttService = null
//        }
//    }
//
//    /**
//     * Determines if this client is currently connected to the server.
//     * @return <code>true</code> if connected, <code>false</code> otherwise.
//     */
//    override fun isConnected(): Boolean =
//        clientHandle.isNotEmpty() && mqttService != null && (mqttService?.isConnected(clientHandle) == true)
//
//    /**
//     * Returns the client ID used by this client. <p> All clients connected to the same server or server farm must have a unique ID. </p>
//     * @return the client ID used by this client.
//     */
//    override fun getClientId(): String = clientId
//
//    /**
//     * Returns the URI address of the server used by this client. <p> The format of the returned String is the same as that used on the constructor. </p>
//     * @return the server's address, as a URI String.
//     */
//    override fun getServerURI(): String = serverURI
//
//    /**
//     * Close the client. Releases all resource associated with the client. After the client has been closed it cannot be reused. For instance attempts to connect will fail.
//     */
//    override fun close() {
//        if(mqttService != null){
//            if (clientHandle.isNotEmpty()) {
//                clientHandle = mqttService?.getClient(serverURI, clientId, context.applicationInfo.packageName, persistence) ?: ""
//            }
//            mqttService?.close(clientHandle)
//        }
//    }
//
//    /**
//     * Connects to an MQTT server using the default options. <p> The default options are specified in {@link MqttConnectOptions} class. </p>
//     * @throws MqttException for any connected problems
//     * @return token used to track and wait for the connect to complete. The token will be passed to the callback methods if a callback is set.
//     * @see #connect(MqttConnectOptions, Object, IMqttActionListener)
//     */
//    override fun connect(): IMqttToken = connect(null, null)
//
//    /**
//     * Connects to an MQTT server using the provided connect options.
//     * <p> The connection will be established using the options specified in the {@link MqttConnectOptions} parameter. </p>
//     * @param options a set of connection parameters that override the defaults.
//     * @throws MqttException for any connected problems
//     * @return token used to track and wait for the connect to complete. The token will be passed to any callback that has been set.
//     * @see #connect(MqttConnectOptions, Object, IMqttActionListener)
//     */
//    override fun connect(options: MqttConnectOptions?): IMqttToken = connect(options, null, null)
//
//    /**
//     * Connects to an MQTT server using the default options. <p> The default options are specified in {@link MqttConnectOptions} class. </p>
//     *
//     * @param userContext optional object used to pass context to the callback. Use null if not required.
//     * @param callback optional listener that will be notified when the connect completes. Use null if not required.
//     * @throws MqttException for any connected problems
//     * @return token used to track and wait for the connect to complete. The token will be passed to any callback that has been set.
//     * @see #connect(MqttConnectOptions, Object, IMqttActionListener)
//     */
//    override fun connect(userContext: Any?, callback: IMqttActionListener?): IMqttToken =
//        connect(MqttConnectOptions(), userContext, callback)
//
//    /**
//     * Connects to an MQTT server using the specified options.
//     * <p>
//     * The server to connect to is specified on the constructor. It is
//     * recommended to call {@link #setCallback(MqttCallback)} prior to
//     * connecting in order that messages destined for the client can be accepted
//     * as soon as the client is connected.
//     * </p>
//     *
//     * <p>
//     * The method returns control before the connect completes. Completion can
//     * be tracked by:
//     * </p>
//     * <ul>
//     * <li>Waiting on the returned token {@link IMqttToken#waitForCompletion()}
//     * or</li>
//     * <li>Passing in a callback {@link IMqttActionListener}</li>
//     * </ul>
//     *
//     *
//     * @param options a set of connection parameters that override the defaults.
//     * @param userContext optional object for used to pass context to the callback. Use null if not required.
//     * @param callback optional listener that will be notified when the connect completes. Use null if not required.
//     * @return token used to track and wait for the connect to complete. The token will be passed to any callback that has been set.
//     * @throws MqttException for any connected problems, including communication errors
//     */
//    override fun connect(
//        options: MqttConnectOptions?,
//        userContext: Any?,
//        callback: IMqttActionListener?
//    ): IMqttToken {
//        val token = MqttTokenAndroid(this, userContext, callback)
//        connectOptions = options
//        connectToken = token
//
//        /*
//         * The actual connection depends on the service, which we start and bind
//         * to here, but which we can't actually use until the serviceConnection
//         * onServiceConnected() method has run (asynchronously), so the
//         * connection itself takes place in the onServiceConnected() method
//         */
//
//        if (mqttService == null) {  // First time - must bind to the service
//            val serviceStartIntent = Intent()
//            serviceStartIntent.setClassName(context, SERVICE_NAME)
//            val service = context.startService(serviceStartIntent)
//            if (service == null) {
//                token.actionCallback?.onFailure(token, RuntimeException("cannot start service $SERVICE_NAME"))
//            }
//            // We bind with BIND_SERVICE_FLAG (0), leaving us the manage the lifecycle until the last time it is stopped by a call to stopService()
//            context.bindService(serviceStartIntent, serviceConnection, Context.BIND_AUTO_CREATE)
//            if (!receiverRegistered){
//                registerReceiver(this)
//            }
//        } else {
//            pool.execute {
//                doConnect()
//                //Register receiver to show shoulder tap.
//                if (!receiverRegistered) {
//                    registerReceiver(this)
//                }
//            }
//
//        }
//        return token
//    }
//
//    private fun registerReceiver(receiver: BroadcastReceiver) {
//        val filter = IntentFilter()
//        filter.addAction(MqttServiceConstants.CALLBACK_TO_ACTIVITY)
//        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter)
//        receiverRegistered = true
//    }
//
//    /** Actually do the mqtt connect operation */
//    private fun doConnect() {
//        if (clientHandle.isEmpty()) {
//            clientHandle = mqttService?.getClient(serverURI, clientId, context.getApplicationInfo().packageName, persistence) ?: ""
//        }
//        mqttService?.setTraceEnabled(traceEnabled)
//        mqttService?.setTraceCallbackId(clientHandle)
//
//        val activityToken = storeToken(connectToken)
//        try {
//            mqttService?.connect(clientHandle, connectOptions, "", activityToken)
//        } catch (e: MqttException) {
//            connectToken?.actionCallback?.onFailure(connectToken, e)
//        }
//    }
//
//    /**
//     * Disconnects from the server.
//     * <p>
//     * An attempt is made to quiesce the client allowing outstanding work to
//     * complete before disconnecting. It will wait for a maximum of 30 seconds
//     * for work to quiesce before disconnecting. This method must not be called
//     * from inside {@link MqttCallback} methods.
//     * </p>
//     *
//     * @return token used to track and wait for disconnect to complete. The token will be passed to any callback that has been set.
//     * @throws MqttException for problems encountered while disconnecting
//     * @see #disconnect(long, Object, IMqttActionListener)
//     */
//    override fun disconnect(): IMqttToken {
//        val token = MqttTokenAndroid(this, null, null)
//        val activityToken = storeToken(token)
//        mqttService?.disconnect(clientHandle, "", activityToken)
//        return token
//    }
//
//    /**
//     * Disconnects from the server.
//     * <p>
//     * An attempt is made to quiesce the client allowing outstanding work to
//     * complete before disconnecting. It will wait for a maximum of the
//     * specified quiesce time for work to complete before disconnecting. This
//     * method must not be called from inside {@link MqttCallback} methods.
//     * </p>
//     *
//     * @param quiesceTimeout
//     *            the amount of time in milliseconds to allow for existing work
//     *            to finish before disconnecting. A value of zero or less means
//     *            the client will not quiesce.
//     * @return token used to track and wait for disconnect to complete. The
//     *         token will be passed to the callback methods if a callback is
//     *         set.
//     * @throws MqttException
//     *             for problems encountered while disconnecting
//     * @see #disconnect(long, Object, IMqttActionListener)
//     */
//    override fun disconnect(quiesceTimeout: Long): IMqttToken {
//        val token = MqttTokenAndroid(this, null, null)
//        val activityToken = storeToken(token)
//        mqttService?.disconnect(clientHandle, quiesceTimeout, "", activityToken)
//        return token
//    }
//
//    /**
//     * Disconnects from the server.
//     * <p>
//     * An attempt is made to quiesce the client allowing outstanding work to
//     * complete before disconnecting. It will wait for a maximum of 30 seconds
//     * for work to quiesce before disconnecting. This method must not be called
//     * from inside {@link MqttCallback} methods.
//     * </p>
//     *
//     * @param userContext
//     *            optional object used to pass context to the callback. Use null
//     *            if not required.
//     * @param callback
//     *            optional listener that will be notified when the disconnect
//     *            completes. Use null if not required.
//     * @return token used to track and wait for the disconnect to complete. The
//     *         token will be passed to any callback that has been set.
//     * @throws MqttException
//     *             for problems encountered while disconnecting
//     * @see #disconnect(long, Object, IMqttActionListener)
//     */
//    override fun disconnect(userContext: Any?, callback: IMqttActionListener?): IMqttToken {
//        val token = MqttTokenAndroid(this, userContext, callback)
//        val activityToken = storeToken(token)
//        mqttService?.disconnect(clientHandle, "", activityToken)
//        return token
//    }
//
//    /**
//     * Disconnects from the server.
//     * <p>
//     * The client will wait for {@link MqttCallback} methods to complete. It
//     * will then wait for up to the quiesce timeout to allow for work which has
//     * already been initiated to complete. For instance when a QoS 2 message has
//     * started flowing to the server but the QoS 2 flow has not completed.It
//     * prevents new messages being accepted and does not send any messages that
//     * have been accepted but not yet started delivery across the network to the
//     * server. When work has completed or after the quiesce timeout, the client
//     * will disconnect from the server. If the cleanSession flag was set to
//     * false and next time it is also set to false in the connection, the
//     * messages made in QoS 1 or 2 which were not previously delivered will be
//     * delivered this time.
//     * </p>
//     * <p>
//     * This method must not be called from inside {@link MqttCallback} methods.
//     * </p>
//     * <p>
//     * The method returns control before the disconnect completes. Completion
//     * can be tracked by:
//     * </p>
//     * <ul>
//     * <li>Waiting on the returned token {@link IMqttToken#waitForCompletion()}
//     * or</li>
//     * <li>Passing in a callback {@link IMqttActionListener}</li>
//     * </ul>
//     *
//     * @param quiesceTimeout
//     *            the amount of time in milliseconds to allow for existing work
//     *            to finish before disconnecting. A value of zero or less means
//     *            the client will not quiesce.
//     * @param userContext
//     *            optional object used to pass context to the callback. Use null
//     *            if not required.
//     * @param callback
//     *            optional listener that will be notified when the disconnect
//     *            completes. Use null if not required.
//     * @return token used to track and wait for the disconnect to complete. The
//     *         token will be passed to any callback that has been set.
//     * @throws MqttException
//     *             for problems encountered while disconnecting
//     */
//    override fun disconnect(
//        quiesceTimeout: Long,
//        userContext: Any?,
//        callback: IMqttActionListener?
//    ): IMqttToken {
//        val token =  MqttTokenAndroid(this, userContext, callback)
//        val activityToken = storeToken(token)
//        mqttService?.disconnect(clientHandle, quiesceTimeout, "", activityToken)
//        return token
//    }
//
//    /**
//     * Publishes a message to a topic on the server.
//     * <p>
//     * A convenience method, which will create a new {@link MqttMessage} object
//     * with a byte array payload and the specified QoS, and then publish it.
//     * </p>
//     *
//     * @param topic
//     *            to deliver the message to, for example "finance/stock/ibm".
//     * @param payload
//     *            the byte array to use as the payload
//     * @param qos
//     *            the Quality of Service to deliver the message at. Valid values
//     *            are 0, 1 or 2.
//     * @param retained
//     *            whether or not this message should be retained by the server.
//     * @return token used to track and wait for the publish to complete. The
//     *         token will be passed to any callback that has been set.
//     * @throws MqttPersistenceException
//     *             when a problem occurs storing the message
//     * @throws IllegalArgumentException
//     *             if value of QoS is not 0, 1 or 2.
//     * @throws MqttException
//     *             for other errors encountered while publishing the message.
//     *             For instance, too many messages are being processed.
//     * @see #publish(String, MqttMessage, Object, IMqttActionListener)
//     */
//    override fun publish(
//        topic: String?,
//        payload: ByteArray?,
//        qos: Int,
//        retained: Boolean
//    ): IMqttDeliveryToken = publish(topic, payload, qos, retained, null, null)
//
//    /**
//     * Publishes a message to a topic on the server. Takes an
//     * {@link MqttMessage} message and delivers it to the server at the
//     * requested quality of service.
//     *
//     * @param topic
//     *            to deliver the message to, for example "finance/stock/ibm".
//     * @param message
//     *            to deliver to the server
//     * @return token used to track and wait for the publish to complete. The
//     *         token will be passed to any callback that has been set.
//     * @throws MqttPersistenceException
//     *             when a problem occurs storing the message
//     * @throws IllegalArgumentException
//     *             if value of QoS is not 0, 1 or 2.
//     * @throws MqttException
//     *             for other errors encountered while publishing the message.
//     *             For instance client not connected.
//     * @see #publish(String, MqttMessage, Object, IMqttActionListener)
//     */
//    override fun publish(topic: String?, message: MqttMessage?): IMqttDeliveryToken =
//        publish(topic, message, null, null)
//
//    /**
//     * Publishes a message to a topic on the server.
//     * <p>
//     * A convenience method, which will create a new {@link MqttMessage} object
//     * with a byte array payload, the specified QoS and retained, then publish it.
//     * </p>
//     *
//     * @param topic
//     *            to deliver the message to, for example "finance/stock/ibm".
//     * @param payload
//     *            the byte array to use as the payload
//     * @param qos
//     *            the Quality of Service to deliver the message at. Valid values
//     *            are 0, 1 or 2.
//     * @param retained
//     *            whether or not this message should be retained by the server.
//     * @param userContext
//     *            optional object used to pass context to the callback. Use null
//     *            if not required.
//     * @param callback
//     *            optional listener that will be notified when message delivery
//     *            has completed to the requested quality of service
//     * @return token used to track and wait for the publish to complete. The
//     *         token will be passed to any callback that has been set.
//     * @throws MqttPersistenceException
//     *             when a problem occurs storing the message
//     * @throws IllegalArgumentException
//     *             if value of QoS is not 0, 1 or 2.
//     * @throws MqttException
//     *             for other errors encountered while publishing the message.
//     *             For instance client not connected.
//     * @see #publish(String, MqttMessage, Object, IMqttActionListener)
//     */
//    override fun publish(
//        topic: String,
//        payload: ByteArray?,
//        qos: Int,
//        retained: Boolean,
//        userContext: Any?,
//        callback: IMqttActionListener?
//    ): IMqttDeliveryToken {
//        val message = MqttMessage(payload)
//        message.qos = qos
//        message.isRetained = retained
//        val token = MqttDeliveryTokenAndroid(this, userContext, callback, message)
//        val activityToken = storeToken(token)
//        val internalToken = mqttService?.publish(clientHandle, topic, payload, qos, retained, "", activityToken)
//
//        //		;
//        //		token.setDelegate(internalToken);
//        //		return token;
//    }
//
//    override fun publish(
//        topic: String?,
//        message: MqttMessage?,
//        userContext: Any?,
//        callback: IMqttActionListener?
//    ): IMqttDeliveryToken {
//
//    }
//
//
//    override fun onReceive(context: Context?, intent: Intent?) {
//
//    }
//
//
//    override fun disconnectForcibly() {
//
//    }
//
//    override fun disconnectForcibly(disconnectTimeout: Long) {
//
//    }
//
//    override fun disconnectForcibly(quiesceTimeout: Long, disconnectTimeout: Long) {
//
//    }
//
//
//    override fun subscribe(topicFilter: String?, qos: Int): IMqttToken {
//
//    }
//
//    override fun subscribe(
//        topicFilter: String?,
//        qos: Int,
//        userContext: Any?,
//        callback: IMqttActionListener?
//    ): IMqttToken {
//
//    }
//
//    override fun subscribe(topicFilters: Array<out String>?, qos: IntArray?): IMqttToken {
//
//    }
//
//    override fun subscribe(
//        topicFilters: Array<out String>?,
//        qos: IntArray?,
//        userContext: Any?,
//        callback: IMqttActionListener?
//    ): IMqttToken {
//
//    }
//
//    override fun subscribe(
//        topicFilter: String?,
//        qos: Int,
//        userContext: Any?,
//        callback: IMqttActionListener?,
//        messageListener: IMqttMessageListener?
//    ): IMqttToken {
//
//    }
//
//    override fun subscribe(
//        topicFilter: String?,
//        qos: Int,
//        messageListener: IMqttMessageListener?
//    ): IMqttToken {
//
//    }
//
//    override fun subscribe(
//        topicFilters: Array<out String>?,
//        qos: IntArray?,
//        messageListeners: Array<out IMqttMessageListener>?
//    ): IMqttToken {
//
//    }
//
//    override fun subscribe(
//        topicFilters: Array<out String>?,
//        qos: IntArray?,
//        userContext: Any?,
//        callback: IMqttActionListener?,
//        messageListeners: Array<out IMqttMessageListener>?
//    ): IMqttToken {
//
//    }
//
//    override fun unsubscribe(topicFilter: String?): IMqttToken {
//
//    }
//
//    override fun unsubscribe(topicFilters: Array<out String>?): IMqttToken {
//
//    }
//
//    override fun unsubscribe(
//        topicFilter: String?,
//        userContext: Any?,
//        callback: IMqttActionListener?
//    ): IMqttToken {
//
//    }
//
//    override fun unsubscribe(
//        topicFilters: Array<out String>?,
//        userContext: Any?,
//        callback: IMqttActionListener?
//    ): IMqttToken {
//
//    }
//
//    override fun removeMessage(token: IMqttDeliveryToken?): Boolean {
//
//    }
//
//    override fun setCallback(callback: MqttCallback?) {
//
//    }
//
//    override fun getPendingDeliveryTokens(): Array<IMqttDeliveryToken> {
//
//    }
//
//    override fun setManualAcks(manualAcks: Boolean) {
//
//    }
//
//    override fun reconnect() {
//
//    }
//
//    override fun messageArrivedComplete(messageId: Int, qos: Int) {
//
//    }
//
//    override fun setBufferOpts(bufferOpts: DisconnectedBufferOptions?) {
//
//    }
//
//    override fun getBufferedMessageCount(): Int {
//
//    }
//
//    override fun getBufferedMessage(bufferIndex: Int): MqttMessage {
//
//    }
//
//    override fun deleteBufferedMessage(bufferIndex: Int) {
//
//    }
//
//    override fun getInFlightMessageCount(): Int {
//
//    }
//
//
//}