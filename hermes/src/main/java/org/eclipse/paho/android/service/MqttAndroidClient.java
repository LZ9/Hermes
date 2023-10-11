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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.eclipse.paho.android.service.bean.ClientInfoBean;
import org.eclipse.paho.android.service.bean.MqttClient;
import org.eclipse.paho.android.service.contract.ConnectActionListener;
import org.eclipse.paho.android.service.contract.DisconnectActionListener;
import org.eclipse.paho.android.service.contract.MqttListener;
import org.eclipse.paho.android.service.contract.PublishActionListener;
import org.eclipse.paho.android.service.contract.ServiceStartActionListener;
import org.eclipse.paho.android.service.contract.SubscribeActionListener;
import org.eclipse.paho.android.service.contract.UnsubscribeActionListener;
import org.eclipse.paho.android.service.event.Ack;
import org.eclipse.paho.android.service.event.MqttAction;
import org.eclipse.paho.android.service.event.MqttEvent;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class MqttAndroidClient  {

    /** 上下文 */
    @NonNull
    private final Context mContext;

    /** MQTT服务 */
    @Nullable
    private MqttService mMqttService;
    /** 连接和消息到达回调接口 */
    @Nullable
    private MqttListener mCallback;

    private volatile boolean isRegisteredEvent = false;

    private SubscribeActionListener mSubscribeActionListener;

    private UnsubscribeActionListener mUnsubscribeActionListener;

    private PublishActionListener mPublishActionListener;

    private ServiceStartActionListener mServiceStartActionListener;

    private ConnectActionListener mConnectActionListener;

    private DisconnectActionListener mDisconnectActionListener;


    /**
     * @param context   上下文
     */
    public MqttAndroidClient(@NonNull Context context) {
        this.mContext = context;
        registerEvent();
    }

    public void release(){
        unregisterEvent();
        if (mMqttService != null){
            mMqttService.disconnectAll();
            mMqttService.closeAll();
            mContext.unbindService(mServiceConnection);
            Intent intent = new Intent();
            intent.setClassName(mContext, MqttService.class.getName());
            mContext.stopService(intent);
            mMqttService = null;
        }
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
     * 设置连接和消息到达回调监听器
     */
    public void setCallback(MqttListener callback) {
        this.mCallback = callback;
    }

    /**
     * 是否已连接
     * @param clientKey 客户端主键
     */
    public boolean isConnected(String clientKey) {
        return mMqttService != null && mMqttService.isConnected(clientKey);
    }

    /**
     * 获取客户端数据信息集合
     */
    public HashMap<String, ClientInfoBean> getClientInfoList() {
        HashMap<String, ClientInfoBean> result = new HashMap<>();
        if (mMqttService != null) {
            HashMap<String, MqttClient> map = mMqttService.getClientMap();
            for (String key : map.keySet()) {
                result.put(key, map.get(key));
            }
        }
        return result;
    }

    /**
     * 关闭客户端并释放资源，关闭后客户端无法再连接
     */
    public void close(String clientKey) {
        if (mMqttService != null) {
            mMqttService.close(clientKey);
        }
    }

    /**
     * 关闭客户端并释放资源，关闭后客户端无法再连接
     */
    public void closeAll() {
        if (mMqttService != null) {
            mMqttService.closeAll();
        }
    }

    /** 通过连接参数创建连接客户端 */
    public String createClientKeyByParam(@NonNull String serverURI, @NonNull String clientId) {
        return createClientKeyByParam(serverURI, clientId, new MqttConnectOptions());
    }

    /** 通过连接参数创建连接客户端 */
    public String createClientKeyByParam(@NonNull String serverURI, @NonNull String clientId, @NonNull MqttConnectOptions options) {
        return createClientKeyByParam(serverURI, clientId, options, Ack.AUTO_ACK);
    }

    /** 通过连接参数创建连接客户端 */
    public String createClientKeyByParam(@NonNull String serverURI, @NonNull String clientId, @NonNull MqttConnectOptions options, @NonNull Ack ackType) {
        return createClientKeyByParam(serverURI, clientId, options, ackType, null);
    }

    /** 通过连接参数创建连接客户端 */
    public String createClientKeyByParam(@NonNull String serverURI, @NonNull String clientId, @NonNull MqttConnectOptions options, @NonNull Ack ackType, @Nullable MqttClientPersistence persistence) {
        return mMqttService != null ? mMqttService.createClientKeyByParam(serverURI, clientId, options, ackType, persistence) : "";
    }

    public void startService(ServiceStartActionListener listener){
        mServiceStartActionListener = listener;
        Intent intent = new Intent();
        intent.setClassName(mContext, MqttService.class.getName());
        ComponentName service = mContext.startService(intent);
        if (service == null) {
            if (mServiceStartActionListener != null) {
                mServiceStartActionListener.onFailure("cannot start service " + MqttService.class.getName(), new RuntimeException());
            }
            return;
        }
        boolean flag = mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        if (!flag){
            if (mServiceStartActionListener != null) {
                mServiceStartActionListener.onFailure("cannot bind service " + MqttService.class.getName(), new RuntimeException());
            }
        }
    }

    /**
     * 绑定服务
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mMqttService = ((MqttServiceBinder) binder).getService();
            if (mServiceStartActionListener != null){
                mServiceStartActionListener.onSuccess();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mMqttService = null;
        }
    };

    /**
     * 连接服务器
     */
    public void connect(String clientKey, ConnectActionListener listener) {
        mConnectActionListener = listener;
        if (mMqttService != null) {
            mMqttService.connect(clientKey);
        }
    }

    /**
     * 与服务器断开连接
     */
    public void disconnectAll()  {
        if (mMqttService != null) {
            mMqttService.disconnectAll();
        }
    }

    /**
     * 与服务器断开连接
     * @param quiesceTimeout 在断开连接之前允许完成现有工作的时间（以毫秒为单位）。 值为零或更低意味着客户端不会停顿。
     */
    public void disconnectAll(long quiesceTimeout){
        if (mMqttService != null) {
            mMqttService.disconnectAll(quiesceTimeout);
        }
    }

    /**
     * 与服务器断开连接
     */
    public void disconnect(String clientKey, DisconnectActionListener listener) {
        disconnect(clientKey, -1, listener);
    }

    /**
     * 与服务器断开连接
     * @param quiesceTimeout 在断开连接之前允许完成现有工作的时间（以毫秒为单位）。 值为零或更低意味着客户端不会停顿。
     */
    public void disconnect(String clientKey, long quiesceTimeout, DisconnectActionListener listener) {
        mDisconnectActionListener = listener;
        if (mMqttService != null) {
            mMqttService.disconnect(clientKey, quiesceTimeout);
        }
    }

    /**
     * 向服务器的主题发布消息
     * @param topic  主题
     */
    public IMqttDeliveryToken publish(String clientKey, String topic, String content) {
        return publish(clientKey, topic, new MqttMessage(content.getBytes(StandardCharsets.UTF_8)), null);
    }

    /**
     * 向服务器的主题发布消息
     * @param topic  主题
     */
    public IMqttDeliveryToken publish(String clientKey, String topic, String content, PublishActionListener listener) {
        return publish(clientKey, topic, new MqttMessage(content.getBytes(StandardCharsets.UTF_8)), listener);
    }

    /**
     * 向服务器的主题发布消息
     * @param topic  主题
     */
    public IMqttDeliveryToken publish(String clientKey, String topic, byte[] payload) {
        return publish(clientKey, topic, new MqttMessage(payload), null);
    }

    /**
     * 向服务器的主题发布消息
     * @param topic  主题
     */
    public IMqttDeliveryToken publish(String clientKey, String topic, byte[] payload, PublishActionListener listener) {
        return publish(clientKey, topic, new MqttMessage(payload), listener);
    }

    /**
     * 向服务器的主题发布消息
     * @param topic  主题
     * @param payload   消息内容
     * @param qos 服务质量：0，1，2
     * @param retained MQTT服务器是否保留该消息
     */
    public IMqttDeliveryToken publish(String clientKey, String topic, byte[] payload, int qos, boolean retained, PublishActionListener listener) {
        MqttMessage message = new MqttMessage(payload);
        message.setQos(qos);
        message.setRetained(retained);
        return publish(clientKey, topic, message,listener);
    }

    /**
     * 向服务器的主题发布消息
     * @param topic  主题
     * @param message   消息
     */
    public IMqttDeliveryToken publish(String clientKey, String topic, MqttMessage message, PublishActionListener listener) {
        mPublishActionListener = listener;
        return mMqttService != null ? mMqttService.publish(clientKey, topic, message) : null;
    }

    /**
     * 订阅主题
     * @param topic 服务端主题
     */
    public void subscribe(String clientKey, String topic, SubscribeActionListener actionListener) {
        subscribe(clientKey, topic, 0,actionListener);
    }

    /**
     * 订阅主题
     * @param topic 服务端主题数组
     */
    public void subscribe(String clientKey, String[] topic, SubscribeActionListener actionListener) {
        int[] qosArray = new int[topic.length];
        for (int i = 0; i < topic.length; i++) {
            qosArray[i] = 0;
        }
        subscribe(clientKey, topic, qosArray,actionListener);
    }

    /**
     * 订阅主题
     * @param topic 服务端主题
     * @param qos 服务质量：0，1，2
     */
    public void subscribe(String clientKey, String topic, int qos, SubscribeActionListener actionListener) {
        subscribe(clientKey, topic, qos, null,actionListener);
    }

    /**
     * 订阅主题
     * @param topic 服务端主题数组
     * @param qos 服务质量数组：0，1，2
     */
    public void subscribe(String clientKey, String[] topic, int[] qos, SubscribeActionListener actionListener) {
        subscribe(clientKey, topic, qos, null,actionListener);
    }

    /**
     * 订阅主题
     * @param topic  服务端主题
     * @param qos   服务质量：0，1，2
     * @param listener 消息到达监听器
     */
    public void subscribe(String clientKey, String topic, int qos, @Nullable IMqttMessageListener listener, SubscribeActionListener actionListener) {
        subscribe(clientKey, new String[]{topic}, new int[]{qos}, listener == null ? null : new IMqttMessageListener[]{listener}, actionListener);
    }

    /**
     * 订阅主题
     * @param topic  服务端主题
     * @param qos   服务质量：0，1，2
     * @param listeners 消息到达监听器数组
     */
    public void subscribe(String clientKey, String[] topic, int[] qos, @Nullable IMqttMessageListener[] listeners, SubscribeActionListener actionListener) {
        mSubscribeActionListener = actionListener;
        if (mMqttService != null) {
            mMqttService.subscribe(clientKey, topic, qos, listeners);
        }
    }

    /**
     * 取消服务端主题的订阅
     * @param topic 服务端主题
     */
    public void unsubscribe(String clientKey, String topic, UnsubscribeActionListener listener) {
        unsubscribe(clientKey, new String[]{topic}, listener);
    }

    /**
     * 取消服务端主题的订阅
     * @param topic  服务端主题数组
     */
    public void unsubscribe(String clientKey, String[] topic, UnsubscribeActionListener listener) {
        mUnsubscribeActionListener = listener;
        if (mMqttService != null) {
            mMqttService.unsubscribe(clientKey, topic);
        }
    }

    /**
     * 获取IMqttDeliveryToken数组
     */
    public IMqttDeliveryToken[] getPendingDeliveryTokens(String clientKey) {
        return mMqttService != null ? mMqttService.getPendingDeliveryTokens(clientKey) : null;
    }

    /**
     * 手动确认消息到达
     */
    public boolean acknowledgeMessage(String clientKey, String messageId) {
        return mMqttService != null && mMqttService.acknowledgeMessageArrival(clientKey, messageId);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMqttEvent(MqttEvent event) {
        MqttAction action = event.action;
        if (action == MqttAction.ACTION_MSG_ARRIVED){
            msgArrivedCallback(event);
            return;
        }
        if (action == MqttAction.ACTION_CONNECTION_LOST){
            connectionLostCallback(event);
            return;
        }
        if (action == MqttAction.ACTION_DELIVERY_COMPLETE){
            deliveryCompleteCallback(event);
            return;
        }
        if (action == MqttAction.ACTION_CONNECT_COMPLETE){
            connectCompleteCallback(event);
            return;
        }
        if (action == MqttAction.ACTION_DISCONNECT){
            disconnectCallback(event);
            return;
        }
        if (action == MqttAction.ACTION_SUBSCRIBE){
            subscribeCallback(event);
            return;
        }
        if (action == MqttAction.ACTION_UNSUBSCRIBE){
            unsubscribeCallback(event);
            return;
        }
        if (action == MqttAction.ACTION_PUBLISH_MSG){
            publishMsgCallback(event);
            return;
        }
        if (action == MqttAction.ACTION_CONNECT){
            connectCallback(event);
            return;
        }
//        callActionListener(event);
    }

    private void connectCallback(MqttEvent event) {
        if (event.result == MqttEvent.RESULT_SUCCESS) {
            if (mConnectActionListener != null) {
                mConnectActionListener.onSuccess(event.action, event.clientKey);
            }
        }
        if (event.result == MqttEvent.RESULT_FAIL) {
            if (mConnectActionListener != null) {
                mConnectActionListener.onFailure(event.action, event.clientKey, event.errorMsg, event.exception);
            }
        }
    }

    private void publishMsgCallback(MqttEvent event) {
        if (event.result == MqttEvent.RESULT_SUCCESS) {
            if (mPublishActionListener != null) {
                mPublishActionListener.onSuccess(event.action, event.clientKey, event.topic, event.message);
            }
        }
        if (event.result == MqttEvent.RESULT_FAIL) {
            if (mPublishActionListener != null) {
                mPublishActionListener.onFailure(event.action, event.clientKey, event.topic, event.errorMsg, event.exception);
            }
        }
    }


    private void subscribeCallback(MqttEvent event) {
        if (event.result == MqttEvent.RESULT_SUCCESS) {
            if (mSubscribeActionListener != null) {
                mSubscribeActionListener.onSuccess(event.action, event.clientKey, event.topics);
            }
        }
        if (event.result == MqttEvent.RESULT_FAIL) {
            if (mSubscribeActionListener != null) {
                mSubscribeActionListener.onFailure(event.action, event.clientKey, event.topics, event.errorMsg, event.exception);
            }
        }
    }

    private void unsubscribeCallback(MqttEvent event) {
        if (event.result == MqttEvent.RESULT_SUCCESS) {
            if (mUnsubscribeActionListener != null) {
                mUnsubscribeActionListener.onSuccess(event.action, event.clientKey, event.topics);
            }
        }
        if (event.result == MqttEvent.RESULT_FAIL) {
            if (mUnsubscribeActionListener != null) {
                mUnsubscribeActionListener.onFailure(event.action, event.clientKey, event.topics, event.errorMsg, event.exception);
            }
        }
    }

    private void msgArrivedCallback(MqttEvent event) {
        if (mMqttService != null) {
            acknowledgeMessage(event.clientKey, event.data.messageId);
        }
        if (mCallback != null) {
            mCallback.messageArrived(event.data.clientKey, event.data.topic, event.data.messageId, event.data.message);
        }
    }

    private void connectionLostCallback(MqttEvent event) {
        if (mCallback != null) {
            mCallback.connectionLost(event.clientKey, event.exception);
        }
    }

    private void deliveryCompleteCallback(MqttEvent event) {
        if (mCallback != null) {
            mCallback.deliveryComplete(event.clientKey, event.token);
        }
    }

    private void connectCompleteCallback(MqttEvent event) {
        if (mCallback != null) {
            mCallback.connectComplete(event.clientKey, event.isReconnect, event.serverURI);
        }
    }

    private void disconnectCallback(MqttEvent event) {
        if (event.result == MqttEvent.RESULT_SUCCESS) {
            if (mDisconnectActionListener != null) {
                mDisconnectActionListener.onSuccess(event.action, event.clientKey);
            }
            if (mCallback != null) {
                mCallback.connectionLost(event.clientKey, event.exception);
            }
        }
        if (event.result == MqttEvent.RESULT_FAIL) {
            if (mDisconnectActionListener != null) {
                mDisconnectActionListener.onFailure(event.action, event.clientKey, event.errorMsg, event.exception);
            }
        }
    }

    /**
     * 设置断连缓冲配置项
     * @param bufferOpts 断连缓冲配置项
     */
    public void setBufferOpts(String clientKey, DisconnectedBufferOptions bufferOpts) {
        if (mMqttService != null) {
            mMqttService.setBufferOpts(clientKey, bufferOpts);
        }
    }

    /**
     * 获取断连缓冲配置项
     */
    public int getBufferedMessageCount(String clientKey) {
        return mMqttService != null ? mMqttService.getBufferedMessageCount(clientKey) : 0;
    }

    /**
     * 获取消息
     * @param bufferIndex 索引
     */
    public MqttMessage getBufferedMessage(String clientKey, int bufferIndex) {
        return  mMqttService != null ? mMqttService.getBufferedMessage(clientKey, bufferIndex) : null;
    }

    /**
     * 删除消息
     * @param bufferIndex 索引
     */
    public void deleteBufferedMessage(String clientKey, int bufferIndex) {
        if (mMqttService != null){
            mMqttService.deleteBufferedMessage(clientKey, bufferIndex);
        }
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
}
