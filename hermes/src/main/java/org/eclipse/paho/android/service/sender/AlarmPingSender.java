/*******************************************************************************
 * Copyright (c) 2014 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution. 
 *
 * The Eclipse Public License is available at 
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at 
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.eclipse.paho.android.service.sender;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import org.eclipse.paho.android.service.MqttService;
import org.eclipse.paho.android.service.MqttServiceConstants;
import org.eclipse.paho.client.mqttv3.MqttPingSender;
import org.eclipse.paho.client.mqttv3.internal.ClientComms;

/** ping数据包发送器 */
public class AlarmPingSender implements MqttPingSender {
	private static final String TAG = "AlarmPingSender";

	private ClientComms mClientComms;
	private final MqttService mMqttService;
	private AlarmReceiver mAlarmReceiver;
	private PendingIntent mPendingIntent;
	private volatile boolean hasStarted = false;

	public AlarmPingSender(@NonNull MqttService service) {
		mMqttService = service;
	}

	@Override
	public void init(ClientComms comms) {
		mClientComms = comms;
		mAlarmReceiver = new AlarmReceiver(comms, mMqttService);
	}

	@Override
	public void start() {
		String action = MqttServiceConstants.PING_SENDER + mClientComms.getClient().getClientId();
		Log.d(TAG, "register AlarmReceiver to MqttService by action = " + action);
		mMqttService.registerReceiver(mAlarmReceiver, new IntentFilter(action));
		mPendingIntent = PendingIntent.getBroadcast(mMqttService, 0, new Intent(action), PendingIntent.FLAG_UPDATE_CURRENT);

		hasStarted = true;
		schedule(mClientComms.getKeepAlive());
	}

	@Override
	public void stop() {
		Log.i(TAG, "unregister AlarmReceiver to MqttService");
		if(hasStarted){
			hasStarted = false;
			if(mPendingIntent != null){
				// Cancel Alarm.
				AlarmManager alarmManager = (AlarmManager) mMqttService.getSystemService(Service.ALARM_SERVICE);
				alarmManager.cancel(mPendingIntent);
			}
			mMqttService.unregisterReceiver(mAlarmReceiver);
		}
	}

	@Override
	public void schedule(long delayInMilliseconds) {
		long nextAlarmInMilliseconds = System.currentTimeMillis() + delayInMilliseconds;
		Log.d(TAG, "Schedule next alarm at " + nextAlarmInMilliseconds);
		AlarmManager alarmManager = (AlarmManager) mMqttService.getSystemService(Service.ALARM_SERVICE);

		if (Build.VERSION.SDK_INT >= 23) {
			// In SDK 23 and above, dosing will prevent setExact, setExactAndAllowWhileIdle will force
			// the device to run this task whilst dosing.
			Log.d(TAG, "Alarm schedule using setExactAndAllowWhileIdle, next: " + delayInMilliseconds);
			alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmInMilliseconds, mPendingIntent);
		} else {
			Log.d(TAG, "Alarm schedule using setExact, delay: " + delayInMilliseconds);
			alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextAlarmInMilliseconds, mPendingIntent);
		}
	}


}
