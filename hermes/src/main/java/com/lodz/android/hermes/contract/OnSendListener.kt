package com.lodz.android.hermes.contract

/**
 * 发送监听器
 * @author zhouL
 * @date 2019/12/20
 */
interface OnSendListener {

    /** 发送数据完成，发送主题[topic]，内容[content] */
    fun onSendComplete(topic: String, content: String)

    /** 发送数据失败，发送主题[topic]，异常[cause] */
    fun onSendFailure(topic: String, cause: Throwable)
}