package org.eclipse.paho.android.service;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

/**
 * 网络变化广播接收器
 * @author zhouL
 * @date 2023/9/19
 */
public class NetworkConnectionReceiver extends BroadcastReceiver {

    private final static String TAG = "NetworkReceiver";

    private NetworkListener mNetworkListener = null;

    @Override
    @SuppressLint("WakelockTimeout")
    public void onReceive(Context context, Intent intent) {
        String lockTag = context.getPackageName() + ":" + "MQTT";
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, lockTag);
        wl.acquire();
        boolean isOnline = MqttUtils.isOnline(context);
        Log.d(TAG, "network changed isOnline = " + isOnline);
        if (mNetworkListener != null){
            mNetworkListener.onNetworkChanged(isOnline);
        }
        wl.release();
    }

    public void setOnNetworkListener(NetworkListener listener){
        mNetworkListener = listener;
    }

    public interface NetworkListener {
        void onNetworkChanged(boolean isOnline);
    }

}