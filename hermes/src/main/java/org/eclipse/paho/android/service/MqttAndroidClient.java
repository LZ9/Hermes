/*******************************************************************************
 * Copyright (c) 1999, 2016 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution. 
 *
 * The Eclipse Public License is available at 
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at 
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *   Ian Craggs - Per subscription message handlers bug 466579
 *   Ian Craggs - ack control (bug 472172)
 *
 */
package org.eclipse.paho.android.service;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.lodz.android.hermes.paho.android.service.Status;

import org.eclipse.paho.android.service.event.ConnectionEvent;
import org.eclipse.paho.android.service.token.MqttDeliveryTokenAndroid;
import org.eclipse.paho.android.service.token.MqttTokenAndroid;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * Enables an android application to communicate with an MQTT server using non-blocking methods.
 * <p>
 * Implementation of the MQTT asynchronous client interface {@link IMqttAsyncClient} , using the MQTT
 * android service to actually interface with MQTT server. It provides android applications a simple programming interface to all features of the MQTT version 3.1
 * specification including:
 * </p>
 * <ul>
 * <li>connect
 * <li>publish
 * <li>subscribe
 * <li>unsubscribe
 * <li>disconnect
 * </ul>
 */
public class MqttAndroidClient extends BroadcastReceiver implements IMqttAsyncClient {


    // The Android Service which will process our mqtt calls
    private MqttService mMqttService;

    /**
     * 客户端主键
     */
    @NonNull
    private String mClientKey;

    @NonNull
    private Context mContext;

    // We hold the various tokens in a collection and pass identifiers for them to the service
    private final SparseArray<IMqttToken> tokenMap = new SparseArray<>();
    private int tokenNumber = 0;

    /**
     * MQTT服务端地址
     */
    private final String mServerURI;
    /** 客户端ID */
    private final String mClientId;
    private final MqttClientPersistence mPersistence;
    private MqttConnectOptions mConnectOptions;
    private IMqttToken connectToken;

    // The MqttCallback provided by the application
    private MqttCallback callback;

    //The acknowledgment that a message has been processed by the application
    private final Ack messageAck;

    private volatile boolean isRegisteredEvent = false;
    private volatile boolean bindedService = false;

    /**
     * @param context   上下文
     * @param serverURI MQTT服务端地址
     * @param clientId  客户端id
     */
    public MqttAndroidClient(@NonNull Context context, @NonNull String serverURI, @NonNull String clientId) {
        this(context, serverURI, clientId, Ack.AUTO_ACK);
    }

    /**
     * @param context   上下文
     * @param serverURI MQTT服务端地址
     * @param clientId  客户端id
     * @param ackType   消息确认模式类型
     */
    public MqttAndroidClient(@NonNull Context context, @NonNull String serverURI, @NonNull String clientId, @NonNull Ack ackType) {
        this(context, serverURI, clientId, null, ackType);
    }

    /**
     * @param context     上下文
     * @param serverURI   MQTT服务端地址
     * @param clientId    客户端id
     * @param persistence 持久层接口
     */
    public MqttAndroidClient(@NonNull Context context, @NonNull String serverURI, @NonNull String clientId, @Nullable MqttClientPersistence persistence) {
        this(context, serverURI, clientId, persistence, Ack.AUTO_ACK);
    }

    /**
     * @param context     上下文
     * @param serverURI   MQTT服务端地址
     * @param clientId    客户端id
     * @param persistence 持久层接口
     * @param ackType     消息确认模式类型
     */
    public MqttAndroidClient(@NonNull Context context, @NonNull String serverURI, @NonNull String clientId, @Nullable MqttClientPersistence persistence, @NonNull Ack ackType) {
        mContext = context;
        mServerURI = serverURI;
        mClientId = clientId;
        mPersistence = persistence;
        messageAck = ackType;
    }

    /**
     * 是否已连接
     */
    @Override
    public boolean isConnected() {
        return !mClientKey.isEmpty() && mMqttService != null && mMqttService.isConnected(mClientKey);
    }

    /**
     * 获取客户端ID
     */
    @Override
    public String getClientId() {
        return mClientId;
    }

    /**
     * 获取MQTT服务端地址
     */
    @Override
    public String getServerURI() {
        return mServerURI;
    }

    /**
     * 关闭客户端并释放资源，关闭后客户端无法再连接
     */
    @Override
    public void close() {
        if (mMqttService != null) {
            if (TextUtils.isEmpty(mClientKey)) {
                mClientKey = mMqttService.putClient(mServerURI, mClientId, mContext.getApplicationInfo().packageName, mPersistence);
            }
            mMqttService.close(mClientKey);
        }
    }

    /**
     * 连接服务器
     */
    @Override
    public IMqttToken connect()  {
        return connect(null, null);
    }


    /**
     * 连接服务器
     * @param options 连接配置
     */
    @Override
    public IMqttToken connect(@NonNull MqttConnectOptions options)  {
        return connect(options, null, null);
    }

    /**
     * 连接服务器
     * @param userContext 需要传递的数据（可选）
     * @param callback 发布回调监听器
     */
    @Override
    public IMqttToken connect(@Nullable Object userContext,@Nullable IMqttActionListener callback)  {
        return connect(new MqttConnectOptions(), userContext, callback);
    }

    /**
     * 连接服务器
     * @param options 连接配置
     * @param userContext 需要传递的数据（可选）
     * @param callback 发布回调监听器
     */

    @Override
    public IMqttToken connect(@NonNull MqttConnectOptions options, @Nullable Object userContext, @Nullable IMqttActionListener callback)  {
        IMqttToken token = new MqttTokenAndroid(this, userContext, callback);
        mConnectOptions = options;
        connectToken = token;
        if (mMqttService != null) {
            doConnect();   // 重新连接
            return token;
        }

        Intent intent = new Intent();
        intent.setClassName(mContext, MqttService.class.getName());
        ComponentName service = mContext.startService(intent);
        if (service == null) {
            IMqttActionListener listener = token.getActionCallback();
            if (listener != null) {
                listener.onFailure(token, new RuntimeException("cannot start service " + MqttService.class.getName()));
            }
            return token;
        }

        // We bind with BIND_SERVICE_FLAG (0), leaving us the manage the lifecycle until the last time it is stopped by a call to stopService()
        mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        return token;
    }


    /**
     * 绑定服务
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mMqttService = ((MqttServiceBinder) binder).getService();
            bindedService = true;
            // now that we have the service available, we can actually connect...
            doConnect();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mMqttService = null;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            ServiceConnection.super.onBindingDied(name);
        }

        @Override
        public void onNullBinding(ComponentName name) {
            ServiceConnection.super.onNullBinding(name);
        }
    };

    private void registerReceiver(BroadcastReceiver receiver) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(MqttServiceConstants.CALLBACK_TO_ACTIVITY);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(receiver, filter);
        isRegisteredEvent = true;
    }

    private void registerEvent() {
        if (!isRegisteredEvent) {
            EventBus.getDefault().register(this);
            isRegisteredEvent = true;
        }
    }

    private void unregisterEvent() {
        if (isRegisteredEvent) {
            EventBus.getDefault().unregister(this);
            isRegisteredEvent = false;
        }
    }

    /**
     * Actually do the mqtt connect operation
     */
    private void doConnect() {
        if (!isRegisteredEvent){
            registerReceiver(this);
            registerEvent();
        }
        if (TextUtils.isEmpty(mClientKey)) {
            mClientKey = mMqttService.putClient(mServerURI, mClientId, mContext.getApplicationInfo().packageName, mPersistence);
        }
        String activityToken = storeToken(connectToken);
        try {
            mMqttService.connect(mClientKey, mConnectOptions, activityToken);
        } catch (Exception e) {
            IMqttActionListener listener = connectToken.getActionCallback();
            if (listener != null) {
                listener.onFailure(connectToken, e);
            }
        }
    }

    /**
     * Disconnects from the server.
     * <p>
     * An attempt is made to quiesce the client allowing outstanding work to
     * complete before disconnecting. It will wait for a maximum of 30 seconds
     * for work to quiesce before disconnecting. This method must not be called
     * from inside {@link MqttCallback} methods.
     * </p>
     *
     * @return token used to track and wait for disconnect to complete. The
     * token will be passed to any callback that has been set.
     * @throws MqttException for problems encountered while disconnecting
     * @see #disconnect(long, Object, IMqttActionListener)
     */
    @Override
    public IMqttToken disconnect() throws MqttException {
        IMqttToken token = new MqttTokenAndroid(this, null, null);
        String activityToken = storeToken(token);
        mMqttService.disconnect(mClientKey, activityToken);
        return token;
    }

    /**
     * Disconnects from the server.
     * <p>
     * An attempt is made to quiesce the client allowing outstanding work to
     * complete before disconnecting. It will wait for a maximum of the
     * specified quiesce time for work to complete before disconnecting. This
     * method must not be called from inside {@link MqttCallback} methods.
     * </p>
     *
     * @param quiesceTimeout the amount of time in milliseconds to allow for existing work
     *                       to finish before disconnecting. A value of zero or less means
     *                       the client will not quiesce.
     * @return token used to track and wait for disconnect to complete. The
     * token will be passed to the callback methods if a callback is
     * set.
     * @throws MqttException for problems encountered while disconnecting
     * @see #disconnect(long, Object, IMqttActionListener)
     */
    @Override
    public IMqttToken disconnect(long quiesceTimeout) throws MqttException {
        IMqttToken token = new MqttTokenAndroid(this, null, null);
        String activityToken = storeToken(token);
        mMqttService.disconnect(mClientKey, quiesceTimeout, activityToken);
        return token;
    }

    /**
     * Disconnects from the server.
     * <p>
     * An attempt is made to quiesce the client allowing outstanding work to
     * complete before disconnecting. It will wait for a maximum of 30 seconds
     * for work to quiesce before disconnecting. This method must not be called
     * from inside {@link MqttCallback} methods.
     * </p>
     *
     * @param userContext optional object used to pass context to the callback. Use null
     *                    if not required.
     * @param callback    optional listener that will be notified when the disconnect
     *                    completes. Use null if not required.
     * @return token used to track and wait for the disconnect to complete. The
     * token will be passed to any callback that has been set.
     * @throws MqttException for problems encountered while disconnecting
     * @see #disconnect(long, Object, IMqttActionListener)
     */
    @Override
    public IMqttToken disconnect(Object userContext,
                                 IMqttActionListener callback) throws MqttException {
        IMqttToken token = new MqttTokenAndroid(this, userContext,
                callback);
        String activityToken = storeToken(token);
        mMqttService.disconnect(mClientKey, activityToken);
        return token;
    }

    /**
     * Disconnects from the server.
     * <p>
     * The client will wait for {@link MqttCallback} methods to complete. It
     * will then wait for up to the quiesce timeout to allow for work which has
     * already been initiated to complete. For instance when a QoS 2 message has
     * started flowing to the server but the QoS 2 flow has not completed.It
     * prevents new messages being accepted and does not send any messages that
     * have been accepted but not yet started delivery across the network to the
     * server. When work has completed or after the quiesce timeout, the client
     * will disconnect from the server. If the cleanSession flag was set to
     * false and next time it is also set to false in the connection, the
     * messages made in QoS 1 or 2 which were not previously delivered will be
     * delivered this time.
     * </p>
     * <p>
     * This method must not be called from inside {@link MqttCallback} methods.
     * </p>
     * <p>
     * The method returns control before the disconnect completes. Completion
     * can be tracked by:
     * </p>
     * <ul>
     * <li>Waiting on the returned token {@link IMqttToken#waitForCompletion()}
     * or</li>
     * <li>Passing in a callback {@link IMqttActionListener}</li>
     * </ul>
     *
     * @param quiesceTimeout the amount of time in milliseconds to allow for existing work
     *                       to finish before disconnecting. A value of zero or less means
     *                       the client will not quiesce.
     * @param userContext    optional object used to pass context to the callback. Use null
     *                       if not required.
     * @param callback       optional listener that will be notified when the disconnect
     *                       completes. Use null if not required.
     * @return token used to track and wait for the disconnect to complete. The
     * token will be passed to any callback that has been set.
     * @throws MqttException for problems encountered while disconnecting
     */
    @Override
    public IMqttToken disconnect(long quiesceTimeout, Object userContext, IMqttActionListener callback) throws MqttException {
        IMqttToken token = new MqttTokenAndroid(this, userContext, callback);
        String activityToken = storeToken(token);
        mMqttService.disconnect(mClientKey, quiesceTimeout, activityToken);
        return token;
    }

    /**
     * 向服务器的主题发布消息
     * @param topic 主题
     * @param payload 消息内容
     * @param qos 服务质量：0，1，2
     * @param retained MQTT服务器是否保留该消息
     */
    @Override
    public IMqttDeliveryToken publish(String topic, byte[] payload, int qos, boolean retained) {
        return publish(topic, payload, qos, retained, null, null);
    }

    /**
     * 向服务器的主题发布消息
     * @param topic 主题
     * @param message 消息内容
     */
    @Override
    public IMqttDeliveryToken publish(String topic, MqttMessage message) {
        return publish(topic, message, null, null);
    }

    /**
     * 向服务器的主题发布消息
     * @param topic  主题
     * @param payload   消息内容
     * @param qos 服务质量：0，1，2
     * @param retained MQTT服务器是否保留该消息
     * @param userContext 需要传递的数据（可选）
     * @param callback 发布回调监听器
     */
    @Override
    public IMqttDeliveryToken publish(String topic, byte[] payload, int qos, boolean retained, Object userContext, IMqttActionListener callback) {
        MqttMessage message = new MqttMessage(payload);
        message.setQos(qos);
        message.setRetained(retained);
        return publish(topic, message, userContext, callback);
    }

    /**
     * 向服务器的主题发布消息
     * @param topic  主题
     * @param message   消息
     * @param userContext 需要传递的数据（可选）
     * @param callback 发布回调监听器
     */
    @Override
    public IMqttDeliveryToken publish(String topic, MqttMessage message, Object userContext, IMqttActionListener callback) {
        MqttDeliveryTokenAndroid token = new MqttDeliveryTokenAndroid(this, userContext, callback, message);
        String activityToken = storeToken(token);
        IMqttDeliveryToken internalToken = mMqttService.publish(mClientKey, topic, message, activityToken);
        token.setDelegate(internalToken);
        return token;
    }

    /**
     * 订阅主题
     * @param topic 服务端主题
     * @param qos 服务质量：0，1，2
     */
    @Override
    public IMqttToken subscribe(String topic, int qos) {
        return subscribe(topic, qos, null, null);
    }

    /**
     * 订阅主题
     * @param topic 服务端主题数组
     * @param qos 服务质量数组：0，1，2
     */
    @Override
    public IMqttToken subscribe(String[] topic, int[] qos) {
        return subscribe(topic, qos, null, null);
    }

    /**
     * 订阅主题
     * @param topic 服务端主题
     * @param qos 服务质量：0，1，2
     * @param userContext 需要传递的数据（可选）
     * @param callback 订阅回调监听器
     */
    @Override
    public IMqttToken subscribe(String topic, int qos, Object userContext, IMqttActionListener callback)  {
        return subscribe(new String[]{topic}, new int[]{qos}, userContext, callback);
    }

    /**
     * 订阅主题
     * @param topic 服务端主题
     * @param callback 订阅回调监听器
     */
    public IMqttToken subscribe(String topic, IMqttActionListener callback)  {
        return subscribe(topic, 0, null, callback);
    }

    /**
     * 订阅主题
     * @param topic 服务端主题数组
     * @param callback 订阅回调监听器
     */
    public IMqttToken subscribe(String[] topic, IMqttActionListener callback)  {
        int[] qosArray = new int[topic.length];
        for (int i = 0; i < topic.length; i++) {
            qosArray[i] = 0;
        }
        return subscribe(topic, qosArray, null, callback);
    }

    /**
     * 订阅主题
     * @param topic 服务端主题
     * @param qos 服务质量：0，1，2
     * @param userContext 需要传递的数据（可选）
     * @param callback 订阅回调监听器
     */
    @Override
    public IMqttToken subscribe(String[] topic, int[] qos, Object userContext, IMqttActionListener callback)  {
        return subscribe(topic, qos, userContext, callback, null);
    }

    /**
     * 订阅主题
     * @param topic  服务端主题
     * @param qos   服务质量：0，1，2
     * @param userContext  需要传递的数据（可选）
     * @param callback     订阅回调监听器
     * @param messageListener 消息到达监听器
     */
    public IMqttToken subscribe(String topic, int qos, Object userContext, IMqttActionListener callback, IMqttMessageListener messageListener){
        return subscribe(new String[]{topic}, new int[]{qos}, userContext, callback, new IMqttMessageListener[]{messageListener});
    }

    /**
     * 订阅主题
     * @param topic  服务端主题
     * @param qos   服务质量：0，1，2
     * @param messageListener 消息到达监听器
     */
    public IMqttToken subscribe(String topic, int qos, IMqttMessageListener messageListener)  {
        return subscribe(topic, qos, null, null, messageListener);
    }

    /**
     * 订阅主题
     * @param topic  服务端主题数组
     * @param qos   服务质量数组：0，1，2
     * @param messageListeners 消息到达监听器数组
     */
    public IMqttToken subscribe(String[] topic, int[] qos, IMqttMessageListener[] messageListeners)  {
        return subscribe(topic, qos, null, null, messageListeners);
    }

    /**
     * 订阅主题
     * @param topic  服务端主题
     * @param qos   服务质量：0，1，2
     * @param userContext  需要传递的数据（可选）
     * @param callback     订阅回调监听器
     * @param messageListeners 消息到达监听器数组
     */
    public IMqttToken subscribe(String[] topic, int[] qos, Object userContext, IMqttActionListener callback, IMqttMessageListener[] messageListeners){
        IMqttToken token = new MqttTokenAndroid(this, userContext, callback, topic);
        String activityToken = storeToken(token);
        mMqttService.subscribe(mClientKey, topic, qos, activityToken, messageListeners);
        return token;
    }


    /**
     * 取消服务端主题的订阅
     * @param topic 服务端主题
     */
    @Override
    public IMqttToken unsubscribe(String topic)  {
        return unsubscribe(topic, null, null);
    }

    /**
     * 取消服务端主题的订阅
     * @param topic 服务端主题数组
     */
    @Override
    public IMqttToken unsubscribe(String[] topic)  {
        return unsubscribe(topic, null, null);
    }

    /**
     * 取消服务端主题的订阅
     * @param topic  服务端主题
     * @param userContext  需要传递的数据（可选）
     * @param callback     订阅回调监听器
     */
    @Override
    public IMqttToken unsubscribe(String topic, Object userContext, IMqttActionListener callback) {
        return unsubscribe(new String[]{topic}, userContext, callback);
    }

    /**
     * 取消服务端主题的订阅
     * @param topic  服务端主题数组
     * @param userContext  需要传递的数据（可选）
     * @param callback     订阅回调监听器
     */
    @Override
    public IMqttToken unsubscribe(String[] topic, Object userContext, IMqttActionListener callback) {
        IMqttToken token = new MqttTokenAndroid(this, userContext, callback);
        String activityToken = storeToken(token);
        mMqttService.unsubscribe(mClientKey, topic, activityToken);
        return token;
    }

    /**
     * 获取IMqttDeliveryToken数组
     */
    @Override
    public IMqttDeliveryToken[] getPendingDeliveryTokens() {
        return mMqttService.getPendingDeliveryTokens(mClientKey);
    }

    /**
     * 设置Mqtt监听器
     */
    @Override
    public void setCallback(MqttCallback callback) {
        this.callback = callback;
    }

    /**
     * <p>
     * Process incoming Intent objects representing the results of operations
     * and asynchronous activities such as message received
     * </p>
     * <p>
     * <strong>Note:</strong> This is only a public method because the Android
     * APIs require such.<br>
     * This method should not be explicitly invoked.
     * </p>
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle data = intent.getExtras();

        String handleFromIntent = data.getString(MqttServiceConstants.CALLBACK_CLIENT_HANDLE);

        if ((handleFromIntent == null) || (!handleFromIntent.equals(mClientKey))) {
            return;
        }

        String action = data.getString(MqttServiceConstants.CALLBACK_ACTION);

        if (MqttServiceConstants.CONNECT_ACTION.equals(action)) {
            connectAction(data);
        } else if (MqttServiceConstants.CONNECT_EXTENDED_ACTION.equals(action)) {
            connectExtendedAction(data);
        } else if (MqttServiceConstants.MESSAGE_ARRIVED_ACTION.equals(action)) {
            messageArrivedAction(data);
        } else if (MqttServiceConstants.SUBSCRIBE_ACTION.equals(action)) {
            subscribeAction(data);
        } else if (MqttServiceConstants.UNSUBSCRIBE_ACTION.equals(action)) {
            unSubscribeAction(data);
        } else if (MqttServiceConstants.SEND_ACTION.equals(action)) {
            sendAction(data);
        } else if (MqttServiceConstants.MESSAGE_DELIVERED_ACTION.equals(action)) {
            messageDeliveredAction(data);
        } else if (MqttServiceConstants.ON_CONNECTION_LOST_ACTION
                .equals(action)) {
            connectionLostAction(data);
        } else if (MqttServiceConstants.DISCONNECT_ACTION.equals(action)) {
            disconnected(data);
        } else {
            Log.e(MqttService.TAG, "Callback action doesn't exist.");
        }

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActivityFinishEvent(ConnectionEvent event) {
        // TODO: 2023/9/21 替换onReceive

    }

    /**
     * Acknowledges a message received on the
     * {@link MqttCallback#messageArrived(String, MqttMessage)}
     *
     * @param messageId the messageId received from the MqttMessage (To access this
     *                  field you need to cast {@link MqttMessage} to
     *                  {@link ParcelableMqttMessage})
     * @return whether or not the message was successfully acknowledged
     */
    public boolean acknowledgeMessage(String messageId) {
        if (messageAck == Ack.MANUAL_ACK) {
            return mMqttService.acknowledgeMessageArrival(mClientKey, messageId);
        }
        return false;

    }

    public void messageArrivedComplete(int messageId, int qos) throws MqttException {
        throw new UnsupportedOperationException();
    }

    public void setManualAcks(boolean manualAcks) {
        throw new UnsupportedOperationException();
    }

    /**
     * Process the results of a connection
     *
     * @param data
     */
    private void connectAction(Bundle data) {
        IMqttToken token = connectToken;
        removeMqttToken(data);

        simpleAction(token, data);
    }


    /**
     * Process a notification that we have disconnected
     *
     * @param data
     */
    private void disconnected(Bundle data) {
        mClientKey = ""; // avoid reuse!
        IMqttToken token = removeMqttToken(data);
        if (token != null) {
            ((MqttTokenAndroid) token).notifyComplete();
        }
        if (callback != null) {
            callback.connectionLost(null);
        }
    }

    /**
     * Process a Connection Lost notification
     *
     * @param data
     */
    private void connectionLostAction(Bundle data) {
        if (callback != null) {
            Exception reason = (Exception) data.getSerializable(MqttServiceConstants.CALLBACK_EXCEPTION);
            callback.connectionLost(reason);
        }
    }

    private void connectExtendedAction(Bundle data) {
        // This is called differently from a normal connect

        if (callback instanceof MqttCallbackExtended) {
            boolean reconnect = data.getBoolean(MqttServiceConstants.CALLBACK_RECONNECT, false);
            String serverURI = data.getString(MqttServiceConstants.CALLBACK_SERVER_URI);
            ((MqttCallbackExtended) callback).connectComplete(reconnect, serverURI);
        }

    }

    /**
     * Common processing for many notifications
     *
     * @param token the token associated with the action being undertake
     * @param data  the result data
     */
    private void simpleAction(IMqttToken token, Bundle data) {
        if (token != null) {
            Status status = (Status) data.getSerializable(MqttServiceConstants.CALLBACK_STATUS);
            if (status == Status.OK) {
                ((MqttTokenAndroid) token).notifyComplete();
            } else {
                Exception exceptionThrown = (Exception) data.getSerializable(MqttServiceConstants.CALLBACK_EXCEPTION);
                ((MqttTokenAndroid) token).notifyFailure(exceptionThrown);
            }
        } else {
            Log.e(MqttService.TAG, "simpleAction : token is null");
        }
    }

    /**
     * Process notification of a publish(send) operation
     *
     * @param data
     */
    private void sendAction(Bundle data) {
        IMqttToken token = getMqttToken(data); // get, don't remove - will
        // remove on delivery
        simpleAction(token, data);
    }

    /**
     * Process notification of a subscribe operation
     *
     * @param data
     */
    private void subscribeAction(Bundle data) {
        IMqttToken token = removeMqttToken(data);
        simpleAction(token, data);
    }

    /**
     * Process notification of an unsubscribe operation
     *
     * @param data
     */
    private void unSubscribeAction(Bundle data) {
        IMqttToken token = removeMqttToken(data);
        simpleAction(token, data);
    }

    /**
     * Process notification of a published message having been delivered
     *
     * @param data
     */
    private void messageDeliveredAction(Bundle data) {
        IMqttToken token = removeMqttToken(data);
        if (token != null) {
            if (callback != null) {
                Status status = (Status) data.getSerializable(MqttServiceConstants.CALLBACK_STATUS);
                if (status == Status.OK && token instanceof IMqttDeliveryToken) {
                    callback.deliveryComplete((IMqttDeliveryToken) token);
                }
            }
        }
    }

    /**
     * Process notification of a message's arrival
     *
     * @param data
     */
    private void messageArrivedAction(Bundle data) {
        if (callback != null) {
            String messageId = data.getString(MqttServiceConstants.CALLBACK_MESSAGE_ID);
            String destinationName = data.getString(MqttServiceConstants.CALLBACK_DESTINATION_NAME);

            ParcelableMqttMessage message = data.getParcelable(MqttServiceConstants.CALLBACK_MESSAGE_PARCEL);
            try {
                if (messageAck == Ack.AUTO_ACK) {
                    callback.messageArrived(destinationName, message);
                    mMqttService.acknowledgeMessageArrival(mClientKey, messageId);
                } else {
                    message.messageId = messageId;
                    callback.messageArrived(destinationName, message);
                }

                // let the service discard the saved message details
            } catch (Exception e) {
                // Swallow the exception
            }
        }
    }

    /**
     * @param token identifying an operation
     * @return an identifier for the token which can be passed to the Android
     * Service
     */
    private synchronized String storeToken(IMqttToken token) {
        tokenMap.put(tokenNumber, token);
        return Integer.toString(tokenNumber++);
    }

    /**
     * Get a token identified by a string, and remove it from our map
     *
     * @param data
     * @return the token
     */
    private synchronized IMqttToken removeMqttToken(Bundle data) {
        String activityToken = data.getString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN);
        if (activityToken != null) {
            int tokenNumber = Integer.parseInt(activityToken);
            IMqttToken token = tokenMap.get(tokenNumber);
            tokenMap.delete(tokenNumber);
            return token;
        }
        return null;
    }

    /**
     * Get a token identified by a string, and remove it from our map
     *
     * @param data
     * @return the token
     */
    private synchronized IMqttToken getMqttToken(Bundle data) {
        String activityToken = data.getString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN);
        return tokenMap.get(Integer.parseInt(activityToken));
    }

    /**
     * Sets the DisconnectedBufferOptions for this client
     *
     * @param bufferOpts the DisconnectedBufferOptions
     */
    public void setBufferOpts(DisconnectedBufferOptions bufferOpts) {
        mMqttService.setBufferOpts(mClientKey, bufferOpts);
    }

    public int getBufferedMessageCount() {
        return mMqttService.getBufferedMessageCount(mClientKey);
    }

    public MqttMessage getBufferedMessage(int bufferIndex) {
        return mMqttService.getBufferedMessage(mClientKey, bufferIndex);
    }

    public void deleteBufferedMessage(int bufferIndex) {
        mMqttService.deleteBufferedMessage(mClientKey, bufferIndex);
    }

    /**
     * Get the SSLSocketFactory using SSL key store and password
     * <p>
     * A convenience method, which will help user to create a SSLSocketFactory
     * object
     * </p>
     *
     * @param keyStore the SSL key store which is generated by some SSL key tool,
     *                 such as keytool in Java JDK
     * @param password the password of the key store which is set when the key store
     *                 is generated
     * @return SSLSocketFactory used to connect to the server with SSL
     * authentication
     * @throws MqttSecurityException if there was any error when getting the SSLSocketFactory
     */
    public SSLSocketFactory getSSLSocketFactory(InputStream keyStore, String password) throws MqttSecurityException {
        try {
            SSLContext ctx = null;
            SSLSocketFactory sslSockFactory = null;
            KeyStore ts;
            ts = KeyStore.getInstance("BKS");
            ts.load(keyStore, password.toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(ts);
            TrustManager[] tm = tmf.getTrustManagers();
            ctx = SSLContext.getInstance("TLSv1");
            ctx.init(null, tm, null);

            sslSockFactory = ctx.getSocketFactory();
            return sslSockFactory;

        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException |
                 KeyManagementException e) {
            throw new MqttSecurityException(e);
        }
    }

    @Override
    public void disconnectForcibly() throws MqttException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void disconnectForcibly(long disconnectTimeout) throws MqttException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void disconnectForcibly(long quiesceTimeout, long disconnectTimeout)
            throws MqttException {
        throw new UnsupportedOperationException();
    }

    /**
     * Unregister receiver which receives intent from MqttService avoids IntentReceiver leaks.
     */
    public void unregisterResources() {
        if (mContext != null && isRegisteredEvent) {
            synchronized (MqttAndroidClient.this) {
                LocalBroadcastManager.getInstance(mContext).unregisterReceiver(this);
                isRegisteredEvent = false;
            }
            if (bindedService) {
                try {
                    mContext.unbindService(mServiceConnection);
                    bindedService = false;
                } catch (IllegalArgumentException e) {
                    //Ignore unbind issue.
                }
            }
        }
    }

    /**
     * Register receiver to receiver intent from MqttService. Call this method when activity is hidden and become to show again.
     *
     * @param context - Current activity context.
     */
    public void registerResources(Context context) {
        if (context != null) {
            this.mContext = context;
            if (!isRegisteredEvent) {
                registerReceiver(this);
            }
        }
    }

    @Override
    public boolean removeMessage(IMqttDeliveryToken token) throws MqttException {
        return false;
    }

    @Override
    public void reconnect() throws MqttException {

    }

    @Override
    public int getInFlightMessageCount() {
        return 0;
    }
}
