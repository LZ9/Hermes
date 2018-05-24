package com.lodz.android.hermes.contract;

import android.content.Context;

import java.util.List;

/**
 * 推送客户端
 * Created by zhouL on 2018/5/23.
 */
public interface PushClient {

    /**
     * 初始化
     * @param context 上下文
     * @param url 后台地址
     * @param clientId 客户端id
     * @param isAutomaticReconnect 是否自动重连
     * @param isCleanSession 是否清空Session
     */
    void init(Context context, String url, String clientId, boolean isAutomaticReconnect, boolean isCleanSession);

    /**
     * 设置订阅主题
     * @param topics 订阅主题
     */
    void setSubTopic(List<String> topics);

    /**
     * 设置推送监听器
     * @param listener 监听器
     */
    void setOnPushListener(OnPushListener listener);

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