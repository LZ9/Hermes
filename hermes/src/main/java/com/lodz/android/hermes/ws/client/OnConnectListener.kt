package com.lodz.android.hermes.ws.client

/**
 * 连接监听器
 * @author zhouL
 * @date 2019/12/20
 */
interface OnConnectListener {

    /** 连接完成 */
    fun onConnectComplete()

    /** 连接失败，异常[cause] */
    fun onConnectFailure(cause: Throwable)

    /** 连接丢失（连上后断开），异常[cause] */
    fun onConnectionLost(cause: Throwable)
}