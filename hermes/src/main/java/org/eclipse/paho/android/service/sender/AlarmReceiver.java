package org.eclipse.paho.android.service.sender;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;

import org.eclipse.paho.android.service.MqttUtils;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.internal.ClientComms;

/**
 * 发送ping数据包广播接收器
 * @author zhouL
 * @date 2023/9/19
 */
public class AlarmReceiver extends BroadcastReceiver {

    private final static String TAG = "AlarmReceiver";

    //Constant for wakelock
    private final static String PING_WAKELOCK = "MqttService.client.";

    @NonNull
    private final PowerManager.WakeLock mWakeLock;

    private final ClientComms mClientComms;

    public AlarmReceiver(@NonNull ClientComms clientComms, @NonNull Context context) {
        mClientComms = clientComms;
        mWakeLock = MqttUtils.getWakeLock(context, AlarmReceiver.PING_WAKELOCK + clientComms.getClient().getClientId());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Sending Ping at:" + System.currentTimeMillis());
        MqttUtils.acquireWakeLock(mWakeLock);

        IMqttToken token = mClientComms.checkForActivity(new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                Log.d(TAG, "Success. Release lock(AlarmReceiver):" + System.currentTimeMillis());
                MqttUtils.releaseWakeLock(mWakeLock);
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Log.d(TAG, "Failure. Release lock(AlarmReceiver):" + System.currentTimeMillis());
                MqttUtils.releaseWakeLock(mWakeLock);
            }
        });

        if (token == null) {
            MqttUtils.releaseWakeLock(mWakeLock);
        }
    }
}