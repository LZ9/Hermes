package com.lodz.android.hermes.modules;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.IntDef;

import com.lodz.android.hermes.contract.Hermes;
import com.lodz.android.hermes.contract.OnConnectListener;
import com.lodz.android.hermes.contract.OnSendListener;
import com.lodz.android.hermes.contract.OnSubscribeListener;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * 推送管理
 * Created by zhouL on 2018/5/23.
 */
public class HermesAgent {

    /** 日常巡查状态 */
    @IntDef({ConnectType.MQTT, ConnectType.WEB_SOCKET})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ConnectType {
        /** mqtt订阅 */
        int MQTT = 1;
        /** WebSocket订阅 */
        int WEB_SOCKET = 2;
    }

    /** 服务端地址 */
    private String mUrl;
    /** 客户端id */
    private String mClientId;
    /** 订阅主题列表 */
    private List<String> mSubTopics;
    /** 订阅回调 */
    private OnSubscribeListener mOnSubscribeListener;
    /** 连接监听器 */
    private OnConnectListener mOnConnectListener;
    /** 发送监听器 */
    private OnSendListener mOnSendListener;
    /** 连接配置 */
    private MqttConnectOptions mMqttConnectOptions;
    /** 日志标签 */
    private String mTag = "";
    /** 连接类型 */
    private int mType = ConnectType.MQTT;

    private HermesAgent() {
    }

    /** 创建 */
    public static HermesAgent create(){
        return new HermesAgent();
    }

    /**
     * 设置连接类型
     * @param type 类型
     */
    public HermesAgent setConnectType(@ConnectType int type){
        this.mType = type;
        return this;
    }

    /**
     * 设置后台地址
     * @param url 地址
     */
    public HermesAgent setUrl(String url){
        this.mUrl = url;
        return this;
    }

    /**
     * 设置客户端id
     * @param clientId 客户端id
     */
    public HermesAgent setClientId(String clientId){
        this.mClientId = clientId;
        return this;
    }

    /**
     * 设置是否打印日志
     * @param isPrint 是否打印
     */
    public HermesAgent setPrintLog(boolean isPrint){
        PrintLog.setPrint(isPrint);
        return this;
    }

    /**
     * 设置日志标签
     * @param tag 标签
     */
    public HermesAgent setLogTag(String tag){
        if (!TextUtils.isEmpty(tag)) {
            mTag = tag;
        }
        return this;
    }

    /**
     * 设置多个订阅主题
     * @param subTopics 订阅主题
     */
    public HermesAgent setSubTopics(List<String> subTopics){
        mSubTopics = subTopics;
        return this;
    }

    /**
     * 设置订阅主题
     * @param subTopic 订阅主题
     */
    public HermesAgent setSubTopic(String subTopic){
        mSubTopics = new ArrayList<>();
        mSubTopics.add(subTopic);
        return this;
    }

    /**
     * 设置推送监听器
     * @param listener 监听器
     */
    public HermesAgent setOnSubscribeListener(OnSubscribeListener listener){
        mOnSubscribeListener = listener;
        return this;
    }

    /**
     * 设置连接监听器
     * @param listener 监听器
     */
    public HermesAgent setOnConnectListener(OnConnectListener listener){
        mOnConnectListener = listener;
        return this;
    }

    /**
     * 设置发送监听器
     * @param listener 监听器
     */
    public HermesAgent setOnSendListener(OnSendListener listener){
        mOnSendListener = listener;
        return this;
    }

    /**
     * 设置连接配置
     * @param options 配置
     */
    public HermesAgent setConnectOptions(MqttConnectOptions options){
        mMqttConnectOptions = options;
        return this;
    }

    /** 构建推送客户端并自动连接 */
    public Hermes buildConnect(Context context) throws NullPointerException{
        Hermes client = build(context);
        client.connect();
        return client;
    }

    /** 构建推送客户端 */
    public Hermes build(Context context) throws NullPointerException{
        if (context == null){
            throw new NullPointerException("push context is empty");
        }
        if (TextUtils.isEmpty(mUrl)) {
            throw new NullPointerException("push url is empty");
        }
        if (mType == ConnectType.MQTT && TextUtils.isEmpty(mClientId)) {
            throw new NullPointerException("push client is empty");
        }

        if (!TextUtils.isEmpty(mTag)) {
            if (mType == ConnectType.MQTT) {
                HermesImpl.TAG = mTag;
            } else {
                WebSocketImpl.TAG = mTag;
            }
        }

        Hermes client = mType == ConnectType.MQTT ? new HermesImpl() : new WebSocketImpl();
        client.init(context.getApplicationContext(), mUrl, mClientId, mMqttConnectOptions);
        client.setSubTopic(mSubTopics);
        client.setOnSubscribeListener(mOnSubscribeListener);
        client.setOnConnectListener(mOnConnectListener);
        client.setOnSendListener(mOnSendListener);
        return client;
    }

}
