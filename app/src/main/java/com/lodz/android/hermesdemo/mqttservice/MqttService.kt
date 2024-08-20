package com.lodz.android.hermesdemo.mqttservice

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Switch
import androidx.core.app.NotificationCompat
import com.lodz.android.corekt.log.PrintLog
import com.lodz.android.hermesdemo.App
import com.lodz.android.hermesdemo.R
import com.lodz.android.hermesdemo.mqttservice.event.MqttCommandEvent
import com.lodz.android.hermesdemo.mqttservice.event.MqttConnect
import com.lodz.android.hermesdemo.mqttservice.event.MqttCreate
import com.lodz.android.hermesdemo.mqttservice.event.MqttDisconnect
import com.lodz.android.hermesdemo.mqttservice.event.MqttRelease
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 *
 * @author zhouL
 * @date 2024/5/23
 */
class MqttService : Service() {

    /** 前台通知id */
    private val SERVICE_NOTIFY_ID = 777777

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(SERVICE_NOTIFY_ID, getNotification())// 启动前台通知


        return super.onStartCommand(intent, flags, startId)
    }

    /** 获取通知 */
    private fun getNotification(): Notification {
        val title = App.get().getString(R.string.mqtt_foreground_service_title)
        val content = App.get().getString(R.string.mqtt_foreground_service_content)

        val builder = NotificationCompat.Builder(App.get(), App.NOTIFI_CHANNEL_SERVICE_ID)
        builder.setTicker(title)// 通知栏显示的文字
        builder.setContentTitle(title)// 通知栏通知的标题
        builder.setContentText(content)// 通知栏通知的详细内容（只有一行）
        builder.setAutoCancel(false)// 设置为true，点击该条通知会自动删除，false时只能通过滑动来删除（一般都是true）
        builder.setSmallIcon(R.mipmap.ic_launcher)//通知上面的小图标（必传）
        builder.setDefaults(NotificationCompat.DEFAULT_LIGHTS)//通知默认的声音 震动 呼吸灯
        builder.priority = NotificationCompat.PRIORITY_MAX//设置优先级，级别高的排在前面
        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMqttCommandEvent(event: MqttCommandEvent) {
        when (event) {
            is MqttCreate -> create()
            is MqttConnect -> connect()
            is MqttDisconnect -> disconnect()
            is MqttRelease -> release()
            else -> return
        }
    }

    private fun create() {


    }

    private fun connect() {


    }

    private fun disconnect() {


    }

    private fun release() {


    }
}