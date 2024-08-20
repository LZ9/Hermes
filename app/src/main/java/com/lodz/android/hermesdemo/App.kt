package com.lodz.android.hermesdemo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.multidex.MultiDex
import com.lodz.android.corekt.utils.NotificationUtils
import com.lodz.android.hermesdemo.mqttservice.MqttService
import com.lodz.android.hermesdemo.mqttservice.event.MqttCommandEvent
import com.lodz.android.pandora.base.application.BaseApplication
import org.greenrobot.eventbus.EventBus
import java.util.ArrayList

/**
 * @author zhouL
 * @date 2020/8/12
 */
class App :BaseApplication(){

    companion object{
        /** 通知组id */
        const val NOTIFI_GROUP_ID = "g0001"
        /** 主频道id */
        const val NOTIFI_CHANNEL_MAIN_ID = "c0001"
        /** 服务频道id */
        const val NOTIFI_CHANNEL_SERVICE_ID = "c0002"

        fun get(): App = BaseApplication.get() as App
    }

    /** 是否绑定了MQTT前台服务 */
    private var isBindMqttService = false


    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onStartCreate() {
        initNotificationChannel(this)// 初始化通知通道
    }

    /** 初始化通知通道 */
    private fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val group = NotificationChannelGroup(NOTIFI_GROUP_ID, "通知组")
            NotificationUtils.create(context).createNotificationChannelGroup(group)// 设置通知组

            val channels = ArrayList<NotificationChannel>()
            val mainChannel = getMainChannel()
            if (mainChannel != null) {
                channels.add(mainChannel)
            }
            val serviceChannel = getServiceChannel()
            if (serviceChannel != null) {
                channels.add(serviceChannel)
            }
            NotificationUtils.create(context).createNotificationChannels(channels)// 设置频道
        }
    }

    /** 获取主通道 */
    private fun getMainChannel(): NotificationChannel? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFI_CHANNEL_MAIN_ID, "主通知", NotificationManager.IMPORTANCE_DEFAULT)
            channel.enableLights(true)// 开启指示灯，如果设备有的话。
            channel.lightColor = Color.GREEN// 设置指示灯颜色
            channel.description = "应用主通知频道"// 通道描述
            channel.enableVibration(true)// 开启震动
            channel.vibrationPattern = longArrayOf(100, 200, 400, 300, 100)// 设置震动频率
            channel.group = NOTIFI_GROUP_ID
            channel.canBypassDnd()// 检测是否绕过免打扰模式
            channel.setBypassDnd(true)// 设置绕过免打扰模式
            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            channel.canShowBadge()// 检测是否显示角标
            channel.setShowBadge(true)// 设置是否显示角标
            return channel
        }
        return null
    }


    /** 获取前台服务通道 */
    private fun getServiceChannel(): NotificationChannel? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFI_CHANNEL_SERVICE_ID, "服务通知", NotificationManager.IMPORTANCE_HIGH)
            channel.enableLights(true)// 开启指示灯，如果设备有的话。
            channel.lightColor = Color.GREEN// 设置指示灯颜色
            channel.description = "应用服务通知频道"// 通道描述
            channel.enableVibration(true)// 开启震动
            channel.vibrationPattern = longArrayOf(100, 200, 400, 300, 100)// 设置震动频率
            channel.group = NOTIFI_GROUP_ID
            channel.setBypassDnd(true)// 设置绕过免打扰模式
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setShowBadge(false)// 设置是否显示角标
            return channel
        }
        return null
    }

    /** 创建MQTT前台服务 */
    fun createMqttService(){
        try {
            val intent = Intent(this, MqttService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            isBindMqttService = true
//            sendMqttCommand()
        } catch (e: Exception) {
            e.printStackTrace()
            isBindMqttService = false
        }
    }

    /** MQTT前台服务是否创建 */
    fun isBindMqttService() = isBindMqttService

    /** 停止MQTT前台服务 */
    fun stopMqttService(){
        try {
//            sendMqttCommand()
            stopService(Intent(this, MqttService::class.java))
            isBindMqttService = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendMqttCommand(command: MqttCommandEvent) {
        EventBus.getDefault().post(command)
    }

    override fun onExit() {
    }
}