package com.lodz.android.hermes.ws.client

import java.nio.ByteBuffer

/**
 * WebSocket订阅接口回调
 * @author zhouL
 * @date 2019/12/20
 */
interface OnSubscribeListener {

    /** 后台消息到达，订阅主题[ip]，消息[msg] */
    fun onMsgArrived(ip: String, msg: String)

    /** 后台消息到达，订阅主题[ip]，消息[msg] */
    fun onMsgArrived(ip: String, msg: ByteBuffer)

}