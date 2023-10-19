package com.lodz.android.hermes.contract

import android.content.Context

/**
 * 推送基础能力
 * @author zhouL
 * @date 2019/12/20
 */
interface Hermes {

    /** 初始化，上下文[context] */
    fun init(context: Context): Hermes

    /** 连接 */
    fun connect()

    /** 断开连接 */
    fun disconnect()

    /** 释放资源 */
    fun release()

    /** 是否已连接 */
    fun isConnected(): Boolean

    /** 设置日志标签[tag] */
    fun setLogTag(tag: String): Hermes

    /** 设置是否保持静默不接收消息提醒[isSilent] */
    fun setSilent(isSilent: Boolean): Hermes

    /** 是否保持静默不接收消息提醒 */
    fun isSilent(): Boolean

    /** 设置是否打印日志[isPrint] */
    fun setPrintLog(isPrint: Boolean): Hermes
}