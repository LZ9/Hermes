package org.eclipse.paho.android.service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PowerManager;

import androidx.annotation.NonNull;

/**
 * @author zhouL
 * @date 2023/9/19
 */
public class MqttUtils {

    /** 手机网络是否在线 */
    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected();
    }

    /** 得到WakeLock对象 */
    public static PowerManager.WakeLock getWakeLock(Context context, String tag) {
        PowerManager pm = (PowerManager) context.getSystemService(Service.POWER_SERVICE);
        return pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag);
    }


    /** 获取唤醒锁 */
    @SuppressLint("WakelockTimeout")
    public static void acquireWakeLock(@NonNull PowerManager.WakeLock wakeLock) {
        wakeLock.acquire();
    }

    /** 释放唤醒锁 */
    public static void releaseWakeLock(@NonNull PowerManager.WakeLock wakeLock) {
        if (wakeLock.isHeld()){
            wakeLock.release();
        }
    }

    /** 获取客户端主键 */
    public static String getClientKey(Context context, String clientId, String serverURI) {
        return serverURI + ":" + clientId + ":" + context.getApplicationInfo().packageName;
    }

}
