package com.lodz.android.hermes.modules;

import android.content.Context;

import com.lodz.android.hermes.contract.OnConnectListener;
import com.lodz.android.hermes.contract.OnSubscribeListener;
import com.lodz.android.hermes.contract.OnSendListener;
import com.lodz.android.hermes.contract.Hermes;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.Charset;
import java.util.List;

/**
 * 推送客户端
 * Created by zhouL on 2018/5/23.
 */
public class HermesImpl implements Hermes {

    /** 日志标签 */
    private static final String TAG = "pushClient";

    /** mqtt服务端 */
    private MqttAndroidClient mMqttClient;
    /** mqtt连接配置 */
    private MqttConnectOptions mMqttConnectOptions;
    /** 订阅监听器 */
    private OnSubscribeListener mOnSubscribeListener;
    /** 连接监听器 */
    private OnConnectListener mOnConnectListener;
    /** 发送监听器 */
    private OnSendListener mOnSendListener;
    /** 订阅主题列表 */
    private List<String> mSubTopics;

    @Override
    public void init(Context context, String url, String clientId, MqttConnectOptions options) {
        mMqttClient = new MqttAndroidClient(context, url, clientId);
        mMqttClient.setCallback(mMqttCallbackExtended);
        mMqttConnectOptions = options;
        if (mMqttConnectOptions == null){
            mMqttConnectOptions = new MqttConnectOptions();
            mMqttConnectOptions.setAutomaticReconnect(true);
            mMqttConnectOptions.setCleanSession(false);
        }
    }

    /** mqtt接口回调 */
    private MqttCallbackExtended mMqttCallbackExtended = new MqttCallbackExtended() {
        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            if (reconnect) {
                PrintLog.d(TAG, "mqtt重新连接上服务地址：" + serverURI);
                subscribeTopic();// 重新连接上需要再次订阅主题
            } else {
                PrintLog.d(TAG, "mqtt连接上服务地址：" + serverURI);
            }
            if (mOnConnectListener != null){
                mOnConnectListener.onConnectComplete(reconnect);
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            if (cause == null){
                cause = new RuntimeException("mqtt connection lost");
            }
            // 连接丢失
            if (mOnConnectListener != null){
                mOnConnectListener.onConnectionLost(cause);
            }
            PrintLog.e(TAG, "mqtt连接丢失 : " + cause.getCause());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            // 后台推送的消息到达客户端
            String msg = new String(message.getPayload(), Charset.forName("UTF-8"));
            PrintLog.i(TAG, "数据到达 : " + msg);
            if (mOnSubscribeListener != null){
                mOnSubscribeListener.onMsgArrived(topic, msg);
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {

        }
    };

    @Override
    public void setSubTopic(List<String> topics) {
        mSubTopics = topics;
    }

    @Override
    public void setOnSubscribeListener(OnSubscribeListener listener) {
        mOnSubscribeListener = listener;
    }

    @Override
    public void setOnConnectListener(OnConnectListener listener) {
        mOnConnectListener = listener;
    }

    @Override
    public void setOnSendListener(OnSendListener listener) {
        mOnSendListener = listener;
    }

    @Override
    public void sendTopic(String topic, String content) {
        try {
            MqttMessage message = new MqttMessage();
            message.setPayload(content.getBytes());
            mMqttClient.publish(topic, message);
            if (mOnSendListener != null){
                mOnSendListener.onSendComplete(topic, content);
            }
            PrintLog.i(TAG, topic + " --- 数据发送 : " + content);
        } catch (Exception e) {
            e.printStackTrace();
            if (mOnSendListener != null){
                mOnSendListener.onSendFailure(topic, e);
            }
            PrintLog.e(TAG, topic + " --- 数据发送失败 : " + e.getCause());
        }
    }

    @Override
    public void connect() {
        try {
            if (isConnected()){
                return;
            }
            mMqttClient.connect(mMqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mMqttClient.setBufferOpts(disconnectedBufferOptions);
                    subscribeTopic();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    if (exception == null){
                        exception = new RuntimeException("mqtt connection failure");
                    }
                    if (mOnConnectListener != null) {
                        mOnConnectListener.onConnectFailure(exception);
                    }
                    PrintLog.e(TAG, "mqtt连接失败 : " + exception.getCause());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            if (mOnConnectListener != null) {
                mOnConnectListener.onConnectFailure(e);
            }
            PrintLog.e(TAG, "mqtt连接失败 : " + e.getCause());
        }
    }

    @Override
    public void disconnect() {
        try {
            if (mMqttClient != null){
                mMqttClient.disconnect();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public boolean isConnected() {
        return mMqttClient != null && mMqttClient.isConnected();
    }

    @Override
    public void subscribeTopic() {
        // 没有可以订阅的主题
        if (mSubTopics == null || mSubTopics.size() == 0){
            return;
        }
        if (mMqttClient == null){
            return;
        }
        try {
            for (final String topic : mSubTopics) {
                mMqttClient.subscribe(topic, 0, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        PrintLog.v(TAG, topic + "订阅成功");
                        if (mOnSubscribeListener != null) {
                            mOnSubscribeListener.onSubscribeSuccess(topic);
                        }
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        if (exception == null){
                            exception = new RuntimeException("mqtt subscribe failure");
                        }
                        PrintLog.e(TAG, topic + "订阅失败 : " + exception.getCause());
                        if (mOnSubscribeListener != null) {
                            mOnSubscribeListener.onSubscribeFailure(topic, exception);
                        }
                    }
                });
            }
        }catch (Exception e){
            e.printStackTrace();
            PrintLog.e(TAG, "订阅失败 : " + e.getCause());
            if (mOnSubscribeListener != null) {
                mOnSubscribeListener.onSubscribeFailure("all", e);
            }
        }
    }

}
