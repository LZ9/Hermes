package com.lodz.android.hermes.contract

/**
 * 解除订阅接口回调
 * @author zhouL
 * @date 2019/12/20
 */
interface OnUnsubscribeListener {

    /** 解除订阅成功，主题名称[topic] */
    fun onUnsubscribeSuccess(topic: String)

    /** 解除订阅失败，主题名称[topic]，异常[cause] */
    fun onUnsubscribeFailure(topic: String, cause: Throwable)

}