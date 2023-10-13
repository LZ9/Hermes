package com.lodz.android.hermes.mqtt.base.sender

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.lodz.android.hermes.modules.HermesLog
import com.lodz.android.hermes.mqtt.base.service.MqttService
import org.eclipse.paho.client.mqttv3.MqttPingSender
import org.eclipse.paho.client.mqttv3.internal.ClientComms
import kotlin.concurrent.Volatile

/**
 * ping数据包发送器
 * @author zhouL
 * @date 2023/10/12
 */
class AlarmPingSender(private val context: Context) : MqttPingSender {
    companion object {
        private const val TAG = "AlarmPingSender"
        private const val PING_SENDER = MqttService.TAG + ".pingSender."
    }

    private var mClientComms: ClientComms? = null
    private var mAlarmReceiver: AlarmReceiver? = null
    private var mPendingIntent: PendingIntent? = null
    @Volatile
    private var hasStarted = false

    override fun init(comms: ClientComms?) {
        mClientComms = comms
        mAlarmReceiver = AlarmReceiver(comms, context)
    }

    override fun start() {
        val action = PING_SENDER + mClientComms?.client?.clientId
        HermesLog.d(TAG, "register AlarmReceiver to MqttService by action = $action")
        context.registerReceiver(mAlarmReceiver, IntentFilter(action))
        mPendingIntent = PendingIntent.getBroadcast(context, 0, Intent(action), PendingIntent.FLAG_UPDATE_CURRENT)
        hasStarted = true
        schedule(mClientComms?.keepAlive ?: 0)
    }

    override fun stop() {
        HermesLog.i(TAG, "unregister AlarmReceiver to MqttService")
        if (!hasStarted){
            return
        }
        hasStarted = false
        val pi = mPendingIntent
        if(pi != null){
            val alarmManager = context.getSystemService(Service.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(pi)
        }
        context.unregisterReceiver(mAlarmReceiver)
    }

    override fun schedule(delayInMilliseconds: Long) {
        val pi = mPendingIntent ?: return
        val nextAlarmInMilliseconds = System.currentTimeMillis() + delayInMilliseconds
        HermesLog.d(TAG, "Schedule next alarm at : $nextAlarmInMilliseconds")
        val alarmManager = context.getSystemService(Service.ALARM_SERVICE) as? AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            HermesLog.i(TAG, "Alarm schedule using setExactAndAllowWhileIdle, next: $delayInMilliseconds")
            alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmInMilliseconds, pi)
        } else {
            HermesLog.i(TAG, "Alarm schedule using setExact, delay: $delayInMilliseconds")
            alarmManager?.setExact(AlarmManager.RTC_WAKEUP, nextAlarmInMilliseconds, pi)
        }
    }
}