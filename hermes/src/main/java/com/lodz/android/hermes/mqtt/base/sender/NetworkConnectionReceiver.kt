package com.lodz.android.hermes.mqtt.base.sender

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.lodz.android.hermes.modules.HermesLog
import com.lodz.android.hermes.mqtt.base.utils.MqttUtils

/**
 * 网络变化广播接收器
 * @author zhouL
 * @date 2023/10/12
 */
class NetworkConnectionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "NetworkReceiver"
    }

    private var mListener: NetworkListener? = null

    @SuppressLint("WakelockTimeout")
    override fun onReceive(context: Context, intent: Intent?) {
        val lockTag = "${context.packageName}:MQTT"
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wl = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, lockTag)
        wl?.acquire()
        val isOnline = MqttUtils.isOnline(context)
        HermesLog.d(TAG, "network changed isOnline = $isOnline")
        mListener?.onNetworkChanged(isOnline)
        wl?.release()
    }

    fun setOnNetworkListener(listener: NetworkListener?) {
        mListener = listener
    }

    interface NetworkListener {
        fun onNetworkChanged(isOnline: Boolean)
    }
}