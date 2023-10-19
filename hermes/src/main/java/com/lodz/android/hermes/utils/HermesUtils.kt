package com.lodz.android.hermes.utils

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hermes工具类
 * @author zhouL
 * @date 2023/10/12
 */
object HermesUtils {

    /** 手机网络是否在线 */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networkInfo = cm?.activeNetworkInfo
        return networkInfo != null && networkInfo.isAvailable && networkInfo.isConnected
    }

    /** 得到WakeLock对象 */
    fun getWakeLock(context: Context, tag: String): PowerManager.WakeLock {
        val pm = context.getSystemService(Service.POWER_SERVICE) as PowerManager
        return pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
    }

    /** 获取唤醒锁 */
    @SuppressLint("WakelockTimeout")
    fun acquireWakeLock(wakeLock: PowerManager.WakeLock) {
        wakeLock.acquire()
    }

    /** 释放唤醒锁 */
    fun releaseWakeLock(wakeLock: PowerManager.WakeLock) {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    /** 获取客户端主键 */
    fun getClientKey(context: Context, clientId: String, serverURI: String): String =
        "${serverURI}:${clientId}:${context.applicationInfo.packageName}"
}

@Suppress("FunctionName")
internal fun IoScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)