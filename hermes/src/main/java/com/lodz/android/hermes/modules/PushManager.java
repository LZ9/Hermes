package com.lodz.android.hermes.modules;

import android.content.Context;
import android.text.TextUtils;

import com.lodz.android.hermes.contract.OnConnectListener;
import com.lodz.android.hermes.contract.OnPushListener;
import com.lodz.android.hermes.contract.OnSendListener;
import com.lodz.android.hermes.contract.PushClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 推送管理
 * Created by zhouL on 2018/5/23.
 */
public class PushManager {

    /** 服务端地址 */
    private String mUrl;
    /** 客户端id */
    private String mClientId;
    /** 订阅主题列表 */
    private List<String> mSubTopics;
    /** 推送回调 */
    private OnPushListener mOnPushListener;
    /** 连接监听器 */
    private OnConnectListener mOnConnectListener;
    /** 发送监听器 */
    private OnSendListener mOnSendListener;
    /** 是否自动重连 */
    private boolean isAutomaticReconnect = true;
    /** 是否清除Session */
    private boolean isCleanSession = false;

    /** 创建 */
    public static PushManager create(){
        return new PushManager();
    }

    /**
     * 设置后台地址
     * @param url 地址
     */
    public PushManager setUrl(String url){
        this.mUrl = url;
        return this;
    }

    /**
     * 设置客户端id
     * @param clientId 客户端id
     */
    public PushManager setClientId(String clientId){
        this.mClientId = clientId;
        return this;
    }

    /**
     * 设置是否打印日志
     * @param isPrint 是否打印
     */
    public PushManager setPrintLog(boolean isPrint){
        PrintLog.setPrint(isPrint);
        return this;
    }

    /**
     * 设置多个订阅主题
     * @param subTopics 订阅主题
     */
    public PushManager setSubTopics(List<String> subTopics){
        mSubTopics = subTopics;
        return this;
    }

    /**
     * 设置订阅主题
     * @param subTopic 订阅主题
     */
    public PushManager setSubTopic(String subTopic){
        mSubTopics = new ArrayList<>();
        mSubTopics.add(subTopic);
        return this;
    }

    /**
     * 设置推送监听器
     * @param listener 监听器
     */
    public PushManager setOnPushListener(OnPushListener listener){
        mOnPushListener = listener;
        return this;
    }

    /**
     * 设置连接监听器
     * @param listener 监听器
     */
    public PushManager setOnConnectListener(OnConnectListener listener){
        mOnConnectListener = listener;
        return this;
    }

    /**
     * 设置发送监听器
     * @param listener 监听器
     */
    public PushManager setOnSendListener(OnSendListener listener){
        mOnSendListener = listener;
        return this;
    }

    /**
     * 设置是否自动重连
     * @param isAuto 是否自动重连
     */
    public PushManager setAutomaticReconnect(boolean isAuto) {
        isAutomaticReconnect = isAuto;
        return this;
    }

    /**
     * 设置是否清空Session
     * @param isClean 是否清空Session
     */
    public PushManager setCleanSession(boolean isClean) {
        isCleanSession = isClean;
        return this;
    }

    /** 构建推送客户端并自动连接 */
    public PushClient buildConnect(Context context){
        if (context == null){
            throw new NullPointerException("push context is empty");
        }
        if (TextUtils.isEmpty(mUrl)) {
            throw new NullPointerException("push url is empty");
        }
        if (TextUtils.isEmpty(mClientId)) {
            throw new NullPointerException("push client is empty");
        }
        PushClient client = new PushClientImpl();
        client.init(context.getApplicationContext(), mUrl, mClientId, isAutomaticReconnect, isCleanSession);
        client.setSubTopic(mSubTopics);
        client.setOnPushListener(mOnPushListener);
        client.setOnConnectListener(mOnConnectListener);
        client.setOnSendListener(mOnSendListener);
        client.connect();
        return client;
    }

    /** 构建推送客户端 */
    public PushClient build(Context context){
        if (context == null){
            throw new NullPointerException("push context is empty");
        }
        if (TextUtils.isEmpty(mUrl)) {
            throw new NullPointerException("push url is empty");
        }
        if (TextUtils.isEmpty(mClientId)) {
            throw new NullPointerException("push client is empty");
        }
        PushClient client = new PushClientImpl();
        client.init(context.getApplicationContext(), mUrl, mClientId, isAutomaticReconnect, isCleanSession);
        client.setSubTopic(mSubTopics);
        client.setOnPushListener(mOnPushListener);
        client.setOnConnectListener(mOnConnectListener);
        client.setOnSendListener(mOnSendListener);
        return client;
    }

}
