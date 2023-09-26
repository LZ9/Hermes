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
    private String mClientKey = "";

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

    /** 执行连接操作 */
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
     * 与服务器断开连接
     */
    @Override
    public IMqttToken disconnect()  {
        return disconnect(-1, null, null);
    }

    /**
     * 与服务器断开连接
     * @param quiesceTimeout 在断开连接之前允许完成现有工作的时间（以毫秒为单位）。 值为零或更低意味着客户端不会停顿。
     */
    @Override
    public IMqttToken disconnect(long quiesceTimeout){
        return disconnect(quiesceTimeout, null ,null);
    }

    /**
     * 与服务器断开连接
     * @param userContext 需要传递的数据（可选）
     * @param callback    接口回调
     */
    @Override
    public IMqttToken disconnect(Object userContext, IMqttActionListener callback) {
        return disconnect(-1, userContext, callback);
    }

    /**
     * 与服务器断开连接
     * @param quiesceTimeout 在断开连接之前允许完成现有工作的时间（以毫秒为单位）。 值为零或更低意味着客户端不会停顿。
     * @param userContext 需要传递的数据（可选）
     * @param callback    接口回调
     */
    @Override
    public IMqttToken disconnect(long quiesceTimeout,@Nullable Object userContext,@Nullable IMqttActionListener callback)  {
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
     * 广播接收
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
        } else if (MqttServiceConstants.ON_CONNECTION_LOST_ACTION.equals(action)) {
            connectionLostAction(data);
        } else if (MqttServiceConstants.DISCONNECT_ACTION.equals(action)) {
            disconnected(data);
        } else {
            Log.e(MqttService.TAG, "Callback action doesn't exist.");
        }

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConnectionEvent(ConnectionEvent event) {
        // TODO: 2023/9/21 替换onReceive

    }

    /**
     * 手动确认消息到达
     */
    public boolean acknowledgeMessage(String messageId) {
        return messageAck == Ack.MANUAL_ACK && mMqttService.acknowledgeMessageArrival(mClientKey, messageId);
    }

    @Override
    public void messageArrivedComplete(int messageId, int qos) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setManualAcks(boolean manualAcks) {
        throw new UnsupportedOperationException();
    }

    /**
     * 处理连接结果
     * @param data 回调数据
     */
    private void connectAction(Bundle data) {
        IMqttToken token = connectToken;
        removeMqttToken(data);
        simpleAction(token, data);
    }

    /**
     * 回调的通用处理
     * @param token 正在执行操作的令牌
     * @param data  回调数据
     */
    private void simpleAction(IMqttToken token, Bundle data) {
        if (token == null){
            Log.e(MqttService.TAG, "simpleAction : token is null");
            return;
        }
        Status status = (Status) data.getSerializable(MqttServiceConstants.CALLBACK_STATUS);
        if (status == Status.OK) {
            ((MqttTokenAndroid) token).notifyComplete();
        } else {
            Exception exceptionThrown = (Exception) data.getSerializable(MqttServiceConstants.CALLBACK_EXCEPTION);
            ((MqttTokenAndroid) token).notifyFailure(exceptionThrown);
        }
    }

    /**
     * 处理断开连接结果
     * @param data 回调数据
     */
    private void disconnected(Bundle data) {
        mClientKey = "";
        IMqttToken token = removeMqttToken(data);
        if (token != null) {
            ((MqttTokenAndroid) token).notifyComplete();
        }
        if (callback != null) {
            callback.connectionLost(null);
        }
    }

    /**
     * 处理连接丢失结果
     * @param data 回调数据
     */
    private void connectionLostAction(Bundle data) {
        if (callback != null) {
            Exception reason = (Exception) data.getSerializable(MqttServiceConstants.CALLBACK_EXCEPTION);
            callback.connectionLost(reason);
        }
    }

    /**
     * 处理连接完成
     * @param data 回调数据
     */
    private void connectExtendedAction(Bundle data) {
        // This is called differently from a normal connect
        if (callback instanceof MqttCallbackExtended) {
            boolean reconnect = data.getBoolean(MqttServiceConstants.CALLBACK_RECONNECT, false);
            String serverURI = data.getString(MqttServiceConstants.CALLBACK_SERVER_URI);
            ((MqttCallbackExtended) callback).connectComplete(reconnect, serverURI);
        }
    }



    /**
     * 处理发送消息结果
     * @param data 回调数据
     */
    private void sendAction(Bundle data) {
        IMqttToken token = getMqttToken(data); // get, don't remove - will
        // remove on delivery
        simpleAction(token, data);
    }

    /**
     * 处理订阅主题结果
     * @param data 回调数据
     */
    private void subscribeAction(Bundle data) {
        IMqttToken token = removeMqttToken(data);
        simpleAction(token, data);
    }

    /**
     * 处理取消订阅主题结果
     * @param data 回调数据
     */
    private void unSubscribeAction(Bundle data) {
        IMqttToken token = removeMqttToken(data);
        simpleAction(token, data);
    }

    /**
     * 处理已发布消息已送达的结果
     * @param data 回调数据
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
     * 处理消息送达结果
     * @param data 回调数据
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
     * 保存票据
     * @param token 票据
     */
    private synchronized String storeToken(IMqttToken token) {
        tokenMap.put(tokenNumber, token);
        return Integer.toString(tokenNumber++);
    }

    /**
     * 删除票据
     * @param data 回调数据
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
     * 获取票据
     * @param data 回调数据
     */
    private synchronized IMqttToken getMqttToken(Bundle data) {
        String activityToken = data.getString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN);
        return tokenMap.get(Integer.parseInt(activityToken));
    }

    /**
     * 设置断连缓冲配置项
     * @param bufferOpts 断连缓冲配置项
     */
    public void setBufferOpts(DisconnectedBufferOptions bufferOpts) {
        mMqttService.setBufferOpts(mClientKey, bufferOpts);
    }

    /**
     * 获取断连缓冲配置项
     */
    public int getBufferedMessageCount() {
        return mMqttService.getBufferedMessageCount(mClientKey);
    }

    /**
     * 获取消息
     * @param bufferIndex 索引
     */
    public MqttMessage getBufferedMessage(int bufferIndex) {
        return mMqttService.getBufferedMessage(mClientKey, bufferIndex);
    }

    /**
     * 删除消息
     * @param bufferIndex 索引
     */
    public void deleteBufferedMessage(int bufferIndex) {
        mMqttService.deleteBufferedMessage(mClientKey, bufferIndex);
    }

    /**
     * 获取SSLSocketFactory
     * @param keyStore SSL密钥
     * @param password 密码
     */
    public SSLSocketFactory getSSLSocketFactory(InputStream keyStore, String password) throws MqttSecurityException {
        try {
            KeyStore ts = KeyStore.getInstance("BKS");
            ts.load(keyStore, password.toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(ts);
            SSLContext ctx = SSLContext.getInstance("TLSv1");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx.getSocketFactory();

        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException | KeyManagementException e) {
            throw new MqttSecurityException(e);
        }
    }

    @Override
    public void disconnectForcibly() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void disconnectForcibly(long disconnectTimeout)  {
        throw new UnsupportedOperationException();
    }

    @Override
    public void disconnectForcibly(long quiesceTimeout, long disconnectTimeout)  {
        throw new UnsupportedOperationException();
    }

    public void unregisterResources() {
        if (isRegisteredEvent) {
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

    public void registerResources(Context context) {
        if (context != null) {
            this.mContext = context;
            if (!isRegisteredEvent) {
                registerReceiver(this);
            }
        }
    }

    @Override
    public boolean removeMessage(IMqttDeliveryToken token) {

        return false;
    }

    @Override
    public void reconnect() throws MqttException {
        connect();
    }

    @Override
    public int getInFlightMessageCount() {
        return 0;
    }
}
