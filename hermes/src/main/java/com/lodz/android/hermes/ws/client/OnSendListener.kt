package com.lodz.android.hermes.ws.client

import java.nio.ByteBuffer

/**
 * 发送监听器
 * @author zhouL
 * @date 2019/12/20
 */
interface OnSendListener {

    /** 发送数据完成 */
    fun onSendComplete(ip: String, msg: String)

    /** 发送数据完成 */
    fun onSendComplete(ip: String, msg: ByteBuffer)

    /** 发送数据失败，异常[cause] */
    fun onSendFailure(cause: Throwable)
}