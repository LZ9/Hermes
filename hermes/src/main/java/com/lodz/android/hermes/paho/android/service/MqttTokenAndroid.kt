//package com.lodz.android.hermes.paho.android.service
//
//import info.mqtt.android.service.MqttAndroidClient
//import org.eclipse.paho.client.mqttv3.IMqttActionListener
//import org.eclipse.paho.client.mqttv3.IMqttAsyncClient
//import org.eclipse.paho.client.mqttv3.IMqttToken
//import org.eclipse.paho.client.mqttv3.MqttException
//import org.eclipse.paho.client.mqttv3.internal.wire.MqttWireMessage
//
//
///**
// * <p>
// * Implementation of the IMqttToken interface for use from within the
// * MqttAndroidClient implementation
// */
//open class MqttTokenAndroid(
//    private val client: MqttAndroidClient, // used to pass MqttAndroidClient object
//    private var userContext: Any?, // used to pass context
//    private var listener: IMqttActionListener?, // optional listener that will be notified when the action completes. Use null if not required.
//    private val topics: Array<String>? // topics to subscribe to, which can include wildcards.
//) : IMqttToken {
//
//    @Volatile
//    private var isComplete = false
//
//    @Volatile
//    private var lastException: MqttException? = null
//
//    private val waitObject = Object()
//
//    //specifically for getMessageId
//    private var delegate: IMqttToken? = null
//
//    private var pendingException: MqttException? = null
//
//    constructor(
//        client: MqttAndroidClient,
//        userContext: Any?,
//        listener: IMqttActionListener?
//    ) : this(client, userContext, listener, null)
//
//    override fun waitForCompletion() {
//        synchronized(waitObject) {
//            try {
//                waitObject.wait()
//            } catch (e: InterruptedException) {
//                e.printStackTrace()
//            }
//        }
//        val e = pendingException
//        if (e != null) {
//            throw e
//        }
//    }
//
//    override fun waitForCompletion(timeout: Long) {
//        synchronized(waitObject) {
//            try {
//                waitObject.wait(timeout)
//            } catch (e: InterruptedException) {
//                e.printStackTrace()
//            }
//            if (!isComplete) {
//                throw MqttException(MqttException.REASON_CODE_CLIENT_TIMEOUT.toInt())
//            }
//            val e = pendingException
//            if (e != null) {
//                throw e
//            }
//        }
//    }
//
//    /** notify successful completion of the operation */
//    fun notifyComplete() {
//        synchronized(waitObject) {
//            isComplete = true
//            waitObject.notifyAll()
//            listener?.onSuccess(this)
//        }
//    }
//
//    /** notify unsuccessful completion of the operation */
//    fun notifyFailure(exception: Throwable) {
//        synchronized(waitObject) {
//            isComplete = true
//            pendingException = if (exception is MqttException) exception else MqttException(exception)
//            waitObject.notifyAll()
//            if (exception is MqttException) {
//                lastException = exception
//            }
//            listener?.onFailure(this, exception)
//        }
//    }
//
//    override fun isComplete(): Boolean = isComplete
//
//    fun setComplete(complete: Boolean) {
//        isComplete = complete
//    }
//
//    override fun getException(): MqttException? = lastException
//
//    fun setException(exception: MqttException) {
//        lastException = exception
//    }
//
//    override fun getClient(): IMqttAsyncClient = client
//
//    override fun setActionCallback(listener: IMqttActionListener?) {
//        this.listener = listener
//    }
//
//    override fun getActionCallback(): IMqttActionListener? = listener
//
//    override fun getTopics(): Array<String>? = topics
//
//    override fun setUserContext(userContext: Any?) {
//        this.userContext = userContext
//    }
//
//    override fun getUserContext(): Any? = userContext
//
//    fun setDelegate(delegate: IMqttToken?) {
//        this.delegate = delegate
//    }
//
//    override fun getMessageId(): Int = delegate?.messageId ?: 0
//
//    override fun getResponse(): MqttWireMessage? = delegate?.response
//
//    override fun getSessionPresent(): Boolean = delegate?.sessionPresent ?: false
//
//    override fun getGrantedQos(): IntArray? = delegate?.grantedQos
//
//}