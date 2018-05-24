package com.lodz.android.hermes.contract;

/**
 * 连接监听器
 * Created by zhouL on 2018/5/23.
 */
public interface OnConnectListener {
    /**
     * 连接完成
     * @param isReconnected 是否属于重连
     */
    void onConnectComplete(boolean isReconnected);

    /**
     * 连接失败
     * @param cause 异常
     */
    void onConnectFailure(Throwable cause);

    /**
     * 连接丢失（连上后断开）
     * @param cause 异常
     */
    void onConnectionLost(Throwable cause);
}
