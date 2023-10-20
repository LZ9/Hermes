package com.lodz.android.hermes.mqtt.client

/**
 * 构建接口回调
 * @author zhouL
 * @date 2019/12/20
 */
interface OnBuildListener {

    /** 构建成功，客户端主键[clientKey] */
    fun onSuccess(clientKey: String)

    /** 构建失败，客户端主键[clientKey]，异常[cause] */
    fun onFailure(clientKey: String, cause: Throwable)

}