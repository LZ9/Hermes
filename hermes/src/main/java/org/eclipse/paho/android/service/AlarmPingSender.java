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
package org.eclipse.paho.android.service;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttPingSender;
import org.eclipse.paho.client.mqttv3.internal.ClientComms;

/**
 * Default ping sender implementation on Android. It is based on AlarmManager.
 *
 * <p>This class implements the {@link MqttPingSender} pinger interface
 * allowing applications to send ping packet to server every keep alive interval.
 * </p>
 *
 * @see MqttPingSender
 */
class AlarmPingSender implements MqttPingSender {
	// Identifier for Intents, log messages, etc..
	private static final String TAG = "AlarmPingSender";

	private ClientComms mClientComms;
	private final MqttService mMqttService;
	private BroadcastReceiver mAlarmReceiver;
	private PendingIntent mPendingIntent;
	private volatile boolean hasStarted = false;

	public AlarmPingSender(MqttService service) {
		if (service == null) {
			throw new IllegalArgumentException( "Neither service nor client can be null.");
		}
		mMqttService = service;
	}

	@Override
	public void init(ClientComms comms) {
		mClientComms = comms;
		mAlarmReceiver = new AlarmReceiver();
	}

	@Override
	public void start() {
		String action = MqttServiceConstants.PING_SENDER + mClientComms.getClient().getClientId();
		Log.d(TAG, "Register alarmreceiver to MqttService"+ action);
		mMqttService.registerReceiver(mAlarmReceiver, new IntentFilter(action));

		mPendingIntent = PendingIntent.getBroadcast(mMqttService, 0, new Intent(action), PendingIntent.FLAG_UPDATE_CURRENT);

		schedule(mClientComms.getKeepAlive());
		hasStarted = true;
	}

	@Override
	public void stop() {

		Log.d(TAG, "Unregister alarmreceiver to MqttService"+mClientComms.getClient().getClientId());
		if(hasStarted){
			if(mPendingIntent != null){
				// Cancel Alarm.
				AlarmManager alarmManager = (AlarmManager) mMqttService.getSystemService(Service.ALARM_SERVICE);
				alarmManager.cancel(mPendingIntent);
			}

			hasStarted = false;
			try {
				mMqttService.unregisterReceiver(mAlarmReceiver);
			}catch (IllegalArgumentException e){
			    e.printStackTrace();
			}
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
			Log.d(TAG, "Alarm scheule using setExactAndAllowWhileIdle, next: " + delayInMilliseconds);
			alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmInMilliseconds, mPendingIntent);
		} else {
			Log.d(TAG, "Alarm scheule using setExact, delay: " + delayInMilliseconds);
			alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextAlarmInMilliseconds, mPendingIntent);
		}
	}

	/*
	 * This class sends PingReq packet to MQTT broker
	 */
	class AlarmReceiver extends BroadcastReceiver {
		private WakeLock wakelock;
		private final String mWakeLockTag = MqttServiceConstants.PING_WAKELOCK + mClientComms.getClient().getClientId();

		@Override
        @SuppressLint("Wakelock")
		public void onReceive(Context context, Intent intent) {
			// According to the docs, "Alarm Manager holds a CPU wake lock as
			// long as the alarm receiver's onReceive() method is executing.
			// This guarantees that the phone will not sleep until you have
			// finished handling the broadcast.", but this class still get
			// a wake lock to wait for ping finished.

			Log.d(TAG, "Sending Ping at:" + System.currentTimeMillis());

			PowerManager pm = (PowerManager) mMqttService.getSystemService(Service.POWER_SERVICE);
			wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, mWakeLockTag);
			wakelock.acquire();

			// Assign new callback to token to execute code after PingResq
			// arrives. Get another wakelock even receiver already has one,
			// release it until ping response returns.
			IMqttToken token = mClientComms.checkForActivity(new IMqttActionListener() {

				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					Log.d(TAG, "Success. Release lock(" + mWakeLockTag + "):" + System.currentTimeMillis());
					//Release wakelock when it is done.
					wakelock.release();
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
					Log.d(TAG, "Failure. Release lock(" + mWakeLockTag + "):" + System.currentTimeMillis());
					//Release wakelock when it is done.
					wakelock.release();
				}
			});


			if (token == null && wakelock.isHeld()) {
				wakelock.release();
			}
		}
	}
}
