package com.lodz.android.hermes.contract;

import android.content.Context;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

import java.util.List;

/**
 * 推送客户端
 * Created by zhouL on 2018/5/23.
 */
public interface Hermes {

    /**
     * 初始化
     * @param context 上下文
     * @param url 后台地址
     * @param clientId 客户端id
     * @param options 连接配置
     */
    void init(Context context, String url, String clientId, MqttConnectOptions options);

    /**
     * 设置订阅主题
     * @param topics 订阅主题
     */
    void setSubTopic(List<String> topics);

    /**
     * 设置订阅监听器
     * @param listener 监听器
     */
    void setOnSubscribeListener(OnSubscribeListener listener);

    /**
     * 设置连接监听器
     * @param listener 监听器
     */
    void setOnConnectListener(OnConnectListener listener);

    /**
     * 设置发送监听器
     * @param listener 监听器
     */
    void setOnSendListener(OnSendListener listener);

    /**
     * 发送主题内容
     * @param topic 主题
     * @param content 内容
     */
    void sendTopic(String topic, String content);

    /** 连接后台 */
    void connect();

    /** 断开连接 */
    void disconnect();

    /** 是否已连接 */
    boolean isConnected();

    /** 订阅主题 */
    void subscribeTopic();
}