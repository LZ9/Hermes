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
    public void connect(String clientKey, @NonNull MqttConnectOptions connectOptions, String activityToken) {
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
     * 设置断连缓冲配置项
     * @param clientKey 客户端主键
     * @param bufferOpts 断连缓冲配置项
     */
    public void setBufferOpts(String clientKey, DisconnectedBufferOptions bufferOpts) {
        MqttConnection client = getConnection(clientKey);
        client.setBufferOpts(bufferOpts);
    }

    /**
     * 获取断连缓冲配置项
     * @param clientKey 客户端主键
     */
    public int getBufferedMessageCount(String clientKey) {
        MqttConnection client = getConnection(clientKey);
        return client.getBufferedMessageCount();
    }

    /**
     * 获取消息
     * @param clientKey 客户端主键
     * @param bufferIndex 索引
     */
    public MqttMessage getBufferedMessage(String clientKey, int bufferIndex) {
        MqttConnection client = getConnection(clientKey);
        return client.getBufferedMessage(bufferIndex);
    }

    /**
     * 删除消息
     * @param clientKey 客户端主键
     * @param bufferIndex 索引
     */
    public void deleteBufferedMessage(String clientKey, int bufferIndex) {
        MqttConnection client = getConnection(clientKey);
        client.deleteBufferedMessage(bufferIndex);
    }

}
