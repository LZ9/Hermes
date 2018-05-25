package com.lodz.android.hermes.contract;

/**
 * 推送接口回调
 * Created by zhouL on 2018/5/23.
 */
public interface OnSubscribeListener {

    /**
     * 主题订阅成功
     * @param topic 主题名称
     */
    void onSubscribeSuccess(String topic);

    /**
     * 主题订阅失败
     * @param topic 主题名称
     * @param cause 异常
     */
    void onSubscribeFailure(String topic, Throwable cause);

    /**
     * 后台消息到达
     * @param subTopic 订阅主题
     * @param msg 消息
     */
    void onMsgArrived(String subTopic, String msg);
}
