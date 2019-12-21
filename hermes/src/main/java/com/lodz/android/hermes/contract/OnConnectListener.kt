package com.lodz.android.hermes.contract

/**
 * 连接监听器
 * @author zhouL
 * @date 2019/12/20
 */
interface OnConnectListener {

    /** 连接完成，是否属于重连[isReconnected] */
    fun onConnectComplete(isReconnected: Boolean)

    /** 连接失败，异常[cause] */
    fun onConnectFailure(cause: Throwable)

    /** 连接丢失（连上后断开），异常[cause] */
    fun onConnectionLost(cause: Throwable)
}