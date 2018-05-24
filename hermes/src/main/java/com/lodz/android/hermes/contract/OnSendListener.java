package com.lodz.android.hermes.contract;

/**
 * 发送监听器
 * Created by zhouL on 2018/5/23.
 */
public interface OnSendListener {

    /**
     * 发送数据完成
     * @param topic 发送主题
     * @param content 内容
     */
    void onSendComplete(String topic, String content);

    /**
     * 发送数据失败
     * @param topic 发送主题
     * @param cause 异常
     */
    void onSendFailure(String topic, Throwable cause);
}
