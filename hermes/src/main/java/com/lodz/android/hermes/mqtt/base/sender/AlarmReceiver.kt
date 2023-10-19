package com.lodz.android.hermes.mqtt.base.sender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lodz.android.hermes.modules.HermesLog
import com.lodz.android.hermes.utils.HermesUtils
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.internal.ClientComms
import java.lang.RuntimeException

/**
 * 发送ping数据包广播接收器
 * @author zhouL
 * @date 2023/10/12
 */
class AlarmReceiver(private val comms: ClientComms?, context: Context) : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
        private const val PING_WAKELOCK = "MqttService.client."
    }

    private val mWakeLock = HermesUtils.getWakeLock(context, PING_WAKELOCK + comms?.client?.clientId)

    override fun onReceive(context: Context?, intent: Intent?) {
        HermesLog.d(TAG, "Sending Ping at : ${System.currentTimeMillis()}")
        HermesUtils.acquireWakeLock(mWakeLock)
        val token = comms?.checkForActivity(object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                HermesLog.d(TAG, "Success. Release lock(AlarmReceiver) at : ${System.currentTimeMillis()}")
                HermesUtils.releaseWakeLock(mWakeLock)
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                val e = exception ?: RuntimeException()
                HermesLog.e(TAG, "Failure. Release lock(AlarmReceiver) at : ${System.currentTimeMillis()}", e)
                HermesUtils.releaseWakeLock(mWakeLock)
            }
        })
        if (token == null){
            HermesUtils.releaseWakeLock(mWakeLock)
        }
    }
}