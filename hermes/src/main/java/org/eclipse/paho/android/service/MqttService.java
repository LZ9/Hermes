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
 * Contributors:
 *   James Sutton - isOnline Null Pointer (bug 473775)
 */
package org.eclipse.paho.android.service;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.lodz.android.hermes.paho.android.service.Status;

import org.eclipse.paho.android.service.db.MessageStore;
import org.eclipse.paho.android.service.db.MessageStoreImpl;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 * The android service which interfaces with an MQTT client implementation
 * </p>
 * <p>
 * The main API of MqttService is intended to pretty much mirror the
 * IMqttAsyncClient with appropriate adjustments for the Android environment.<br>
 * These adjustments usually consist of adding two parameters to each method :-
 * </p>
 * <ul>
 * <li>invocationContext - a string passed from the application to identify the
 * context of the operation (mainly included for support of the javascript API
 * implementation)</li>
 * <li>activityToken - a string passed from the Activity to relate back to a
 * callback method or other context-specific data</li>
 * </ul>
 * <p>
 * To support multiple client connections, the bulk of the MQTT work is
 * delegated to MqttConnection objects. These are identified by "client
 * handle" strings, which is how the Activity, and the higher-level APIs refer
 * to them.
 * </p>
 * <p>
 * Activities using this service are expected to start it and bind to it using
 * the BIND_AUTO_CREATE flag. The life cycle of this service is based on this
 * approach.
 * </p>
 * <p>
 * Operations are highly asynchronous - in most cases results are returned to
 * the Activity by broadcasting one (or occasionally more) appropriate Intents,
 * which the Activity is expected to register a listener for.<br>
 * The Intents have an Action of
 * {@link MqttServiceConstants#CALLBACK_TO_ACTIVITY
 * MqttServiceConstants.CALLBACK_TO_ACTIVITY} which allows the Activity to
 * register a listener with an appropriate IntentFilter.<br>
 * Further data is provided by "Extra Data" in the Intent, as follows :-
 * </p>
 * <table border="1" summary="">
 * <tr>
 * <th align="left">Name</th>
 * <th align="left">Data Type</th>
 * <th align="left">Value</th>
 * <th align="left">Operations used for</th>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_CLIENT_HANDLE
 * MqttServiceConstants.CALLBACK_CLIENT_HANDLE}</td>
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">The clientHandle identifying the client which
 * initiated this operation</td>
 * <td align="left" valign="top">All operations</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">{@link MqttServiceConstants#CALLBACK_STATUS
 * MqttServiceConstants.CALLBACK_STATUS}</td>
 * <td align="left" valign="top">Serializable</td>
 * <td align="left" valign="top">An {@link Status} value indicating success or
 * otherwise of the operation</td>
 * <td align="left" valign="top">All operations</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_ACTIVITY_TOKEN
 * MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN}</td>
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">the activityToken passed into the operation</td>
 * <td align="left" valign="top">All operations</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_INVOCATION_CONTEXT
 * MqttServiceConstants.CALLBACK_INVOCATION_CONTEXT}</td>
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">the invocationContext passed into the operation
 * </td>
 * <td align="left" valign="top">All operations</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">{@link MqttServiceConstants#CALLBACK_ACTION
 * MqttServiceConstants.CALLBACK_ACTION}</td>
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">one of
 * <table summary="">
 * <tr>
 * <td align="left" valign="top"> {@link MqttServiceConstants#SEND_ACTION
 * MqttServiceConstants.SEND_ACTION}</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#UNSUBSCRIBE_ACTION
 * MqttServiceConstants.UNSUBSCRIBE_ACTION}</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top"> {@link MqttServiceConstants#SUBSCRIBE_ACTION
 * MqttServiceConstants.SUBSCRIBE_ACTION}</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top"> {@link MqttServiceConstants#DISCONNECT_ACTION
 * MqttServiceConstants.DISCONNECT_ACTION}</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top"> {@link MqttServiceConstants#CONNECT_ACTION
 * MqttServiceConstants.CONNECT_ACTION}</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#MESSAGE_ARRIVED_ACTION
 * MqttServiceConstants.MESSAGE_ARRIVED_ACTION}</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#MESSAGE_DELIVERED_ACTION
 * MqttServiceConstants.MESSAGE_DELIVERED_ACTION}</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#ON_CONNECTION_LOST_ACTION
 * MqttServiceConstants.ON_CONNECTION_LOST_ACTION}</td>
 * </tr>
 * </table>
 * </td>
 * <td align="left" valign="top">All operations</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_ERROR_MESSAGE
 * MqttServiceConstants.CALLBACK_ERROR_MESSAGE}
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">A suitable error message (taken from the
 * relevant exception where possible)</td>
 * <td align="left" valign="top">All failing operations</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_ERROR_NUMBER
 * MqttServiceConstants.CALLBACK_ERROR_NUMBER}
 * <td align="left" valign="top">int</td>
 * <td align="left" valign="top">A suitable error code (taken from the relevant
 * exception where possible)</td>
 * <td align="left" valign="top">All failing operations</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_EXCEPTION_STACK
 * MqttServiceConstants.CALLBACK_EXCEPTION_STACK}</td>
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">The stacktrace of the failing call</td>
 * <td align="left" valign="top">The Connection Lost event</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_MESSAGE_ID
 * MqttServiceConstants.CALLBACK_MESSAGE_ID}</td>
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">The identifier for the message in the message
 * store, used by the Activity to acknowledge the arrival of the message, so
 * that the service may remove it from the store</td>
 * <td align="left" valign="top">The Message Arrived event</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_DESTINATION_NAME
 * MqttServiceConstants.CALLBACK_DESTINATION_NAME}
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">The topic on which the message was received</td>
 * <td align="left" valign="top">The Message Arrived event</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_MESSAGE_PARCEL
 * MqttServiceConstants.CALLBACK_MESSAGE_PARCEL}</td>
 * <td align="left" valign="top">Parcelable</td>
 * <td align="left" valign="top">The new message encapsulated in Android
 * Parcelable format as a {@link ParcelableMqttMessage}</td>
 * <td align="left" valign="top">The Message Arrived event</td>
 * </tr>
 * </table>
 */
public class MqttService extends Service {

    static final String TAG = "MqttService";

    /** 数据库操作接口 */
    public MessageStore mMessageStore;

    /** 网络变化广播接收器 */
    private NetworkConnectionReceiver mNetworkReceiver;


    // a way to pass ourself back to the activity
    private MqttServiceBinder mqttServiceBinder;

    /**
     * 客户端存储map
     */
    private final Map<String, MqttConnection> mMqttConnectionMap = new ConcurrentHashMap<>();

    /**
     * 发送广播给客户端
     * @param clientKey 客户端主键
     * @param status 状态
     * @param dataBundle 数据
     */
    public void sendBroadcastToClient(@NonNull String clientKey, Status status, @NonNull Bundle dataBundle) {
        Intent callbackIntent = new Intent(MqttServiceConstants.CALLBACK_TO_ACTIVITY);
        callbackIntent.putExtra(MqttServiceConstants.CALLBACK_CLIENT_HANDLE, clientKey);
        callbackIntent.putExtra(MqttServiceConstants.CALLBACK_STATUS, status);
        callbackIntent.putExtras(dataBundle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(callbackIntent);
    }

    /**
     * 创建MQTT连接并返回它的主键
     * @param serverURI   访问路径
     * @param clientId    客户端ID
     * @param packageName   APP包名
     * @param persistence 持久层接口
     */
    public String putClient(String serverURI, String clientId, String packageName, MqttClientPersistence persistence) {
        String clientKey = serverURI + ":" + clientId + ":" + packageName;
        if (!mMqttConnectionMap.containsKey(clientKey)) {
            MqttConnection client = new MqttConnection(this, serverURI, clientId, persistence, clientKey);
            mMqttConnectionMap.put(clientKey, client);
        }
        return clientKey;
    }

    /**
     * 连接服务端
     * @param clientKey 客户端主键
     * @param connectOptions 配置项
     * @param activityToken 票据
     */
    public void connect(String clientKey, MqttConnectOptions connectOptions, String activityToken) {
        MqttConnection client = getConnection(clientKey);
        client.connect(connectOptions, activityToken);
    }

    /**
     * 关闭客户端
     * @param clientKey 客户端主键
     */
    public void close(String clientKey) {
        MqttConnection client = getConnection(clientKey);
        client.close();
    }

    /**
     * 断开连接
     * @param clientKey 客户端主键
     * @param activityToken 票据
     */
    public void disconnect(String clientKey, String activityToken) {
        disconnect(clientKey, -1 , activityToken);
    }

    /**
     * 断开连接
     * @param clientKey 客户端主键
     * @param quiesceTimeout 超时时间（毫秒）
     * @param activityToken 票据
     */
    public void disconnect(String clientKey, long quiesceTimeout, String activityToken) {
        MqttConnection client = getConnection(clientKey);
        client.disconnect(quiesceTimeout, activityToken);
        mMqttConnectionMap.remove(clientKey);
        stopSelf();
    }

    /**
     * 是否已经连接
     * @param clientKey 客户端主键
     */
    public boolean isConnected(String clientKey) {
        MqttConnection client = getConnection(clientKey);
        return client.isConnected();
    }

    /**
     * 向主题发送消息
     * @param clientKey 客户端主键
     * @param topic 主题
     * @param payload 消息内容
     * @param qos 服务质量 0，1，2
     * @param retained MQTT服务器是否保留该消息
     * @param activityToken 票据
     */
    public IMqttDeliveryToken publish(String clientKey, String topic, byte[] payload, int qos, boolean retained, String activityToken) {
        MqttMessage message = new MqttMessage(payload);
        message.setQos(qos);
        message.setRetained(retained);
        return publish(clientKey, topic, message, activityToken);
    }

    /**
     * 向主题发送消息
     * @param clientKey 客户端主键
     * @param topic 主题名称
     * @param message 消息对象
     * @param activityToken 票据
     */
    public IMqttDeliveryToken publish(String clientKey, String topic, MqttMessage message, String activityToken) {
        MqttConnection client = getConnection(clientKey);
        return client.publish(topic, message, activityToken);
    }

    /**
     * 订阅主题
     * @param clientKey     客户端主键
     * @param topic         主题名称
     * @param qos           服务质量 0，1，2
     * @param activityToken 票据
     */
    public void subscribe(String clientKey, String topic, int qos, String activityToken) {
        subscribe(clientKey, new String[]{topic}, new int[]{qos}, activityToken);
    }


    /**
     * 订阅主题
     * @param clientKey     客户端主键
     * @param topic         主题名称数组
     * @param qos           服务质量数组 0，1，2
     * @param activityToken 票据
     */
    public void subscribe(String clientKey, String[] topic, int[] qos, String activityToken) {
        subscribe(clientKey, topic, qos, activityToken, null);
    }

    /**
     * 订阅主题
     * @param clientKey        客户端主键
     * @param topic            主题名称数组
     * @param qos              服务质量数组 0，1，2
     * @param activityToken    票据
     * @param messageListeners 消息监听器
     */
    public void subscribe(String clientKey, String[] topic, int[] qos, String activityToken, IMqttMessageListener[] messageListeners) {
        MqttConnection client = getConnection(clientKey);
        client.subscribe(topic, qos, activityToken, messageListeners);
    }

    /**
     * 取消订阅主题
     * @param clientKey     客户端主键
     * @param topic         主题名称数组
     * @param activityToken 票据
     */
    public void unsubscribe(String clientKey, String topic, String activityToken) {
        unsubscribe(clientKey, new String[]{topic}, activityToken);
    }

    /**
     * 取消订阅主题
     * @param clientKey     客户端主键
     * @param topic         主题名称数组
     * @param activityToken 票据
     */
    public void unsubscribe(String clientKey, final String[] topic, String activityToken) {
        MqttConnection client = getConnection(clientKey);
        client.unsubscribe(topic, activityToken);
    }

    /**
     * 获取待交付给客户端的票据
     * @param clientKey 客户端主键
     */
    public IMqttDeliveryToken[] getPendingDeliveryTokens(String clientKey) {
        MqttConnection client = getConnection(clientKey);
        return client.getPendingDeliveryTokens();
    }

    /**
     * 通过主键获取MqttConnection
     * @param clientKey 客户端主键
     */
    private MqttConnection getConnection(String clientKey) {
        MqttConnection client = mMqttConnectionMap.get(clientKey);
        if (client == null) {
            throw new IllegalArgumentException("invalid clientKey , MqttConnection is null");
        }
        return client;
    }

    /**
     * 确认消息到达并删除数据库缓存
     * @param clientKey 客户端主键
     * @param messageId   消息ID
     */
    public boolean acknowledgeMessageArrival(String clientKey, String messageId) {
        return mMessageStore.deleteArrivedMessage(clientKey, messageId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mqttServiceBinder = new MqttServiceBinder(this);
        mMessageStore = new MessageStoreImpl(this);
    }

    @Override
    public void onDestroy() {
        for (MqttConnection client : mMqttConnectionMap.values()) {
            client.disconnect();
        }
        if (mqttServiceBinder != null) {
            mqttServiceBinder = null;
        }
        unregisterBroadcastReceivers();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        String activityToken = intent.getStringExtra(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN);
        mqttServiceBinder.setActivityToken(activityToken);
        return mqttServiceBinder;
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, final int startId) {
        registerBroadcastReceivers();
        return START_STICKY;
    }

    /** 注册网络变化广播接收器 */
    private void registerBroadcastReceivers() {
        if (mNetworkReceiver == null) {
            mNetworkReceiver = new NetworkConnectionReceiver();
            mNetworkReceiver.setOnNetworkListener(mListener);
            registerReceiver(mNetworkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        }
    }

    /** 解注册网络变化广播接收器 */
    private void unregisterBroadcastReceivers() {
        if (mNetworkReceiver != null) {
            unregisterReceiver(mNetworkReceiver);
            mNetworkReceiver.setOnNetworkListener(null);
            mNetworkReceiver = null;
        }
    }

    /** 网络变化监听器 */
    private final NetworkConnectionReceiver.NetworkListener mListener = new NetworkConnectionReceiver.NetworkListener() {
        @Override
        public void onNetworkChanged(boolean isOnline) {
            if (isOnline) {
                Log.i(TAG, "online , reconnect");
                reconnect();
            } else {
                Log.i(TAG, "offline , notify clients");
                notifyClientsOffline();
            }
        }
    };

    /** 重连客户端 */
    private void reconnect() {
        for (MqttConnection client : mMqttConnectionMap.values()) {
            client.reconnect();
        }
    }

    /** 通知客户端离线 */
    private void notifyClientsOffline() {
        for (MqttConnection connection : mMqttConnectionMap.values()) {
            connection.offline();
        }
    }


    /**
     * 设置
     * @param clientKey 客户端主键
     * @param bufferOpts   the DisconnectedBufferOptions for this client
     */
    public void setBufferOpts(String clientKey, DisconnectedBufferOptions bufferOpts) {
        MqttConnection client = getConnection(clientKey);
        client.setBufferOpts(bufferOpts);
    }

    public int getBufferedMessageCount(String clientHandle) {
        MqttConnection client = getConnection(clientHandle);
        return client.getBufferedMessageCount();
    }

    public MqttMessage getBufferedMessage(String clientHandle, int bufferIndex) {
        MqttConnection client = getConnection(clientHandle);
        return client.getBufferedMessage(bufferIndex);
    }

    public void deleteBufferedMessage(String clientHandle, int bufferIndex) {
        MqttConnection client = getConnection(clientHandle);
        client.deleteBufferedMessage(bufferIndex);
    }

}
