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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.eclipse.paho.android.service.bean.MqttClient;
import org.eclipse.paho.android.service.db.MessageStore;
import org.eclipse.paho.android.service.db.MessageStoreImpl;
import org.eclipse.paho.android.service.event.Ack;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

import java.io.File;
import java.util.HashMap;

public class MqttService extends Service {

    static final String TAG = "MqttService";

    /** 数据库操作接口 */
    public MessageStore mMessageStore;

    /** 网络变化广播接收器 */
    private NetworkConnectionReceiver mNetworkReceiver;


    // a way to pass ourself back to the activity
    private MqttServiceBinder mqttServiceBinder;

    /** 客户端数据信息集合 */
    private final HashMap<String, MqttClient> mClientMap = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        mqttServiceBinder = new MqttServiceBinder(this);
        mMessageStore = new MessageStoreImpl(this);
    }

    @Override
    public void onDestroy() {
        disconnectAll();
        mqttServiceBinder = null;
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
                reconnectAll();
            } else {
                Log.i(TAG, "offline , notify clients");
                notifyClientsOffline();
            }
        }
    };

    /** 通过连接参数创建连接客户端 */
    public String createClientKeyByParam(@NonNull String serverURI, @NonNull String clientId, @NonNull MqttConnectOptions options, @NonNull Ack ackType) {
        return createClientKeyByParam(serverURI, clientId, options, ackType, null);
    }

    /** 通过连接参数创建连接客户端 */
    public String createClientKeyByParam(@NonNull String serverURI, @NonNull String clientId, @NonNull MqttConnectOptions options, @NonNull Ack ackType, @Nullable MqttClientPersistence persistence){
        String clientKey = MqttUtils.getClientKey(this, clientId, serverURI);
        MqttClient client = getClient(clientKey);
        if (client == null){
          addClient(serverURI,clientId, options,ackType, persistence);
        }
        return clientKey;
    }

    /**
     * 连接服务端
     * @param clientKey 客户端主键
     */
    public void connect(String clientKey) {
        MqttClient client = getClient(clientKey);
        if (client != null){
            client.getConnection().connect();
        }
    }

    /**
     * 构建客户端对象
     * @param serverURI 服务端地址
     * @param clientId  客户端id
     * @param options 连接配置
     * @param ackType   消息确认模式类型
     * @param persistence 持久层接口
     */
    private MqttClient createMqttClient(@NonNull String serverURI, @NonNull String clientId, @NonNull MqttConnectOptions options,
                                                @NonNull Ack ackType, @Nullable MqttClientPersistence persistence) {
        if (TextUtils.isEmpty(serverURI)) {
            throw new IllegalArgumentException("serverURI is empty");
        }
        if (TextUtils.isEmpty(clientId)) {
            throw new IllegalArgumentException("clientId is empty");
        }
        if (persistence == null) {
            File myDir = getExternalFilesDir(MqttServiceConstants.FILE_PERSISTENCE_DIR_NAME);
            if (myDir == null) {
                myDir = getDir(MqttServiceConstants.FILE_PERSISTENCE_DIR_NAME, Context.MODE_PRIVATE);
            }
            if (myDir == null) {
                throw new IllegalArgumentException("cannot get file dir");
            }
            //noinspection resource
            persistence = new MqttDefaultFilePersistence(myDir.getAbsolutePath());
        }
        return new MqttClient(this, MqttUtils.getClientKey(this, clientId, serverURI), serverURI, clientId, options, persistence, ackType, mMessageStore);
    }

    /**
     * 添加客户端
     * @param serverURI 服务端地址
     * @param clientId  客户端id
     * @param options 连接配置
     * @param ackType   消息确认模式类型
     * @param persistence 持久层接口
     */
    private MqttClient addClient(@NonNull String serverURI, @NonNull String clientId, @NonNull MqttConnectOptions options,
                             @NonNull Ack ackType, @Nullable MqttClientPersistence persistence) {
        return addClient(createMqttClient(serverURI, clientId, options, ackType, persistence));
    }

    /**
     * 添加客户端
     * @param client 客户端对象
     */
    private MqttClient addClient(MqttClient client) {
        MqttClient data = mClientMap.get(client.getClientKey());
        if (data == null) {
            mClientMap.put(client.getClientKey(), client);
        }
        return client;
    }

    /** 关闭全部客户端 */
    public void closeAll() {
        for (MqttClient client : mClientMap.values()) {
            client.getConnection().close();
        }
    }

    /**
     * 关闭客户端
     * @param clientKey 客户端主键
     */
    public void close(String clientKey) {
        MqttClient client = getClient(clientKey);
        if (client != null){
            client.getConnection().close();
        }
    }

    /**
     * 断开所有客户端连接
     */
    public void disconnectAll() {
        disconnectAll(-1);
    }

    /**
     * 断开所有客户端连接
     */
    public void disconnectAll(long quiesceTimeout) {
        for (MqttClient client : mClientMap.values()) {
            client.getConnection().disconnect(quiesceTimeout);
        }
    }

    /**
     * 断开连接
     * @param clientKey 客户端主键
     */
    public void disconnect(String clientKey) {
        disconnect(clientKey, -1);
    }

    /**
     * 断开连接
     * @param clientKey 客户端主键
     * @param quiesceTimeout 超时时间（毫秒）
     */
    public void disconnect(String clientKey, long quiesceTimeout) {
        MqttClient client = getClient(clientKey);
        if (client != null){
            client.getConnection().disconnect(quiesceTimeout);
        }
    }

    /**
     * 是否已经连接
     * @param clientKey 客户端主键
     */
    public boolean isConnected(String clientKey) {
        MqttClient client = getClient(clientKey);
        return client != null && client.getConnection().isConnected();
    }

    /**
     * 向主题发送消息
     * @param clientKey 客户端主键
     * @param topic 主题名称
     * @param message 消息对象
     */
    public IMqttDeliveryToken publish(String clientKey, String topic, MqttMessage message) {
        MqttClient client = getClient(clientKey);
        if (client != null) {
            return client.getConnection().publish(topic, message);
        }
        return null;
    }

    /**
     * 订阅主题
     * @param clientKey     客户端主键
     * @param topic         主题名称
     * @param qos           服务质量 0，1，2
     */
    public void subscribe(String clientKey, String topic, int qos) {
        subscribe(clientKey, new String[]{topic}, new int[]{qos});
    }


    /**
     * 订阅主题
     * @param clientKey     客户端主键
     * @param topic         主题名称数组
     * @param qos           服务质量数组 0，1，2
     */
    public void subscribe(String clientKey, String[] topic, int[] qos) {
        subscribe(clientKey, topic, qos, null);
    }

    /**
     * 订阅主题
     * @param clientKey        客户端主键
     * @param topic            主题名称数组
     * @param qos              服务质量数组 0，1，2
     * @param messageListeners 消息监听器
     */
    public void subscribe(String clientKey, String[] topic, int[] qos, @Nullable IMqttMessageListener[] messageListeners) {
        MqttClient client = getClient(clientKey);
        if (client != null){
            client.getConnection().subscribe(topic, qos, messageListeners);
        }
    }

    /**
     * 取消订阅主题
     * @param clientKey     客户端主键
     * @param topic         主题名称数组
     */
    public void unsubscribe(String clientKey, String topic) {
        unsubscribe(clientKey, new String[]{topic});
    }

    /**
     * 取消订阅主题
     * @param clientKey     客户端主键
     * @param topic         主题名称数组
     */
    public void unsubscribe(String clientKey,  String[] topic) {
        MqttClient client = getClient(clientKey);
        if (client != null){
            client.getConnection().unsubscribe(topic);
        }
    }

    /**
     * 获取待交付给客户端的票据
     * @param clientKey 客户端主键
     */
    public IMqttDeliveryToken[] getPendingDeliveryTokens(String clientKey) {
        MqttClient client = getClient(clientKey);
        if (client != null) {
            client.getConnection().getPendingDeliveryTokens();
        }
        return null;
    }

    /**
     * 通过主键获取对应的客户端
     * @param clientKey 客户端主键
     */
    @Nullable
    private MqttClient getClient(String clientKey) {
        return mClientMap.get(clientKey);
    }

    /**
     * 获取客户端数据信息列表
     */
    public HashMap<String, MqttClient> getClientMap() {
        return mClientMap;
    }

    /**
     * 确认消息到达并删除数据库缓存
     * @param clientKey 客户端主键
     * @param messageId   消息ID
     */
    public boolean acknowledgeMessageArrival(String clientKey, String messageId) {
        MqttClient client = getClient(clientKey);
        if (client != null && client.getAckType() == Ack.MANUAL_ACK){
            return mMessageStore.deleteArrivedMessage(clientKey, messageId);
        }
        return false;
    }

    /** 重连客户端 */
    private void reconnectAll() {
        for (MqttClient client : mClientMap.values()) {
            client.getConnection().reconnect();
        }
    }

    /** 重连客户端 */
    private void reconnect(String clientKey) {
        MqttClient client = getClient(clientKey);
        if (client != null){
            client.getConnection().reconnect();
        }
    }

    /** 通知客户端离线 */
    private void notifyClientsOffline() {
        for (MqttClient client : mClientMap.values()) {
            client.getConnection().offline();
        }
    }

    /**
     * 设置断连缓冲配置项
     * @param clientKey 客户端主键
     * @param bufferOpts 断连缓冲配置项
     */
    public void setBufferOpts(String clientKey, DisconnectedBufferOptions bufferOpts) {
        MqttClient client = getClient(clientKey);
        if (client != null){
            client.getConnection().setBufferOpts(bufferOpts);
        }
    }

    /**
     * 获取断连缓冲配置项
     * @param clientKey 客户端主键
     */
    public int getBufferedMessageCount(String clientKey) {
        MqttClient client = getClient(clientKey);
        return client != null ? client.getConnection().getBufferedMessageCount() : 0;
    }

    /**
     * 获取消息
     * @param clientKey 客户端主键
     * @param bufferIndex 索引
     */
    public MqttMessage getBufferedMessage(String clientKey, int bufferIndex) {
        MqttClient client = getClient(clientKey);
        return client != null ? client.getConnection().getBufferedMessage(bufferIndex) : null;
    }

    /**
     * 删除消息
     * @param clientKey 客户端主键
     * @param bufferIndex 索引
     */
    public void deleteBufferedMessage(String clientKey, int bufferIndex) {
        MqttClient client = getClient(clientKey);
        if (client != null) {
            client.getConnection().deleteBufferedMessage(bufferIndex);
        }
    }

}
