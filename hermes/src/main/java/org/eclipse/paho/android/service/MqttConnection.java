/*******************************************************************************
 * Copyright (c) 1999, 2016 IBM Corp.
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
import android.app.Service;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.lodz.android.hermes.paho.android.service.Status;

import org.eclipse.paho.android.service.db.DbStoredData;
import org.eclipse.paho.android.service.event.ConnectionEvent;
import org.eclipse.paho.android.service.sender.AlarmPingSender;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MqttConnection implements MqttCallbackExtended {

	private static final String TAG = "MqttConnection";
	// Error status messages
	private static final String NOT_CONNECTED = "not connected";

	/**
	 * 服务端地址
	 */
	private final String mServerURI;
	/**
	 * 客户端ID
	 */
	private final String mClientId;

	/**
	 * 持久层接口
	 */
	private MqttClientPersistence mPersistence;
	/**
	 * 连接配置项
	 */
	private MqttConnectOptions mConnectOptions;

	/**
	 * 客户端主键
	 */
	private final String mClientKey;


	/**
	 * 重连票据
	 */
	private String mReconnectActivityToken = "";

	/**
	 * 客户端
	 */
	private MqttAsyncClient mClient = null;

	/**
	 * ping数据包发送器
	 */
	private AlarmPingSender mAlarmPingSender = null;

	/**
	 * Mqtt服务
	 */
	private final MqttService mService;

	/**
	 * 是否断开连接
	 */
	private volatile boolean isDisconnected = true;
	/**
	 * 是否清理会话
	 */
	private boolean isCleanSession = true;

	/**
	 * 是否连接中
	 */
	private volatile boolean isConnecting = false;

	/**
	 * 主题缓存
	 */
	private final Map<IMqttDeliveryToken, String> mSavedTopics = new HashMap<>();
	/**
	 * 消息缓存
	 */
	private final Map<IMqttDeliveryToken, MqttMessage> mSavedSentMessages = new HashMap<>();
	/**
	 * 票据缓存
	 */
	private final Map<IMqttDeliveryToken, String> mSavedActivityTokens = new HashMap<>();

	/** 唤醒锁 */
	private WakeLock mWakeLock = null;

	private DisconnectedBufferOptions bufferOpts = null;

	public MqttConnection(MqttService service, String serverURI, String clientId, MqttClientPersistence persistence, String clientKey) {
		mServerURI = serverURI;
		mService = service;
		mClientId = clientId;
		mPersistence = persistence;
		mClientKey = clientKey;
	}

	/**
	 * 连接服务器
	 *
	 * @param options 连接配置项
	 * @param activityToken 票据
	 */
	public void connect(@NonNull MqttConnectOptions options, String activityToken) {
		Log.d(TAG, "start connect {" + mClientKey + "}");
		mConnectOptions = options;
		mReconnectActivityToken = activityToken;
		isCleanSession = options.isCleanSession();
		if (isCleanSession) { // 如果是新的会话则清空数据库缓存
			mService.mMessageStore.clearAllMessages(mClientKey);
		}


		final Bundle resultBundle = new Bundle();
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN, activityToken);
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.CONNECT_ACTION);
		
		try {
			if (mPersistence == null) {
				File myDir = mService.getExternalFilesDir(TAG);
				if (myDir == null) {
					myDir =  mService.getDir(TAG, Context.MODE_PRIVATE);
					if(myDir == null){// 无法获取存储目录
						EventBus.getDefault().post(ConnectionEvent.createError(activityToken, "can not get files dir", new MqttPersistenceException()));

						resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, "Error! No external and internal storage available");
						resultBundle.putSerializable(MqttServiceConstants.CALLBACK_EXCEPTION, new MqttPersistenceException());
						mService.sendBroadcastToClient(mClientKey, Status.ERROR, resultBundle);
						return;
					}
				}
				// use that to setup MQTT client persistence storage
				mPersistence = new MqttDefaultFilePersistence(myDir.getAbsolutePath());
			}

			IMqttActionListener listener = new IMqttActionListener() {

				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					doAfterConnectSuccess(resultBundle);
					Log.d(TAG, "connect success!");
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
					resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, exception.getLocalizedMessage());
					resultBundle.putSerializable(MqttServiceConstants.CALLBACK_EXCEPTION, exception);
					Log.e(TAG, "connect fail, call connect to reconnect.reason:" + exception.getMessage());

					doAfterConnectFail(resultBundle);

				}
			};

			if (mClient != null) {
				if (isConnecting) {
					Log.d(TAG, "mClient != null and the client is connecting. Connect return directly.");
					Log.d(TAG, "Connect return:isConnecting:" + isConnecting + ".isDisconnected:" + isDisconnected);
				} else if (!isDisconnected) {
					Log.d(TAG, "mClient != null and the client is connected and notify!");
					doAfterConnectSuccess(resultBundle);
				} else {
					Log.d(TAG, "mClient != null and the client is not connected");
					Log.d(TAG, "Do Real connect!");
					setConnectingState(true);
					mClient.connect(mConnectOptions, listener);
				}
			} else {
				// if mClient is null, then create a new connection
				mAlarmPingSender = new AlarmPingSender(mService);
				mClient = new MqttAsyncClient(mServerURI, mClientId, mPersistence, mAlarmPingSender);
				mClient.setCallback(this);

				Log.d(TAG,"Do Real connect!");
				setConnectingState(true);
				mClient.connect(mConnectOptions, listener);
			}
		} catch (Exception e) {
			Log.e(TAG, "Exception occurred attempting to connect: " + e.getMessage());
			setConnectingState(false);
			handleException(resultBundle, e);
		}
	}

	private void doAfterConnectSuccess(final Bundle resultBundle) {
		//since the device's cpu can go to sleep, acquire a wakelock and drop it later.
		acquireWakeLock();
		mService.sendBroadcastToClient(mClientKey, Status.OK, resultBundle);
		deliverBacklog();
		setConnectingState(false);
		isDisconnected = false;
		releaseWakeLock();
	}

	@Override
	public void connectComplete(boolean reconnect, String serverURI) {
		Bundle resultBundle = new Bundle();
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION,
				MqttServiceConstants.CONNECT_EXTENDED_ACTION);
		resultBundle.putBoolean(MqttServiceConstants.CALLBACK_RECONNECT, reconnect);
		resultBundle.putString(MqttServiceConstants.CALLBACK_SERVER_URI, serverURI);
		mService.sendBroadcastToClient(mClientKey, Status.OK, resultBundle);
	}

	private void doAfterConnectFail(final Bundle resultBundle){
		//
		acquireWakeLock();
		isDisconnected = true;
		setConnectingState(false);
		mService.sendBroadcastToClient(mClientKey, Status.ERROR, resultBundle);
		releaseWakeLock();
	}
	
	private void handleException(final Bundle resultBundle, Exception e) {
		resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, e.getLocalizedMessage());
		resultBundle.putSerializable(MqttServiceConstants.CALLBACK_EXCEPTION, e);
		mService.sendBroadcastToClient(mClientKey, Status.ERROR, resultBundle);
	}

	/**
	 * Attempt to deliver any outstanding messages we've received but which the
	 * application hasn't acknowledged. If "cleanSession" was specified, we'll
	 * have already purged any such messages from our messageStore.
	 */
	private void deliverBacklog() {
		ArrayList<DbStoredData> backlog = mService.mMessageStore.getAllMessages(mClientKey);
		for (DbStoredData data : backlog) {
			Bundle resultBundle = messageToBundle(data.messageId, data.topic, data.message);
			resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.MESSAGE_ARRIVED_ACTION);
			mService.sendBroadcastToClient(mClientKey, Status.OK, resultBundle);
		}
	}

	/**
	 * Create a bundle containing all relevant data pertaining to a message
	 * 
	 * @param messageId
	 *            the message's identifier in the messageStore, so that a
	 *            callback can be made to remove it once delivered
	 * @param topic
	 *            the topic on which the message was delivered
	 * @param message
	 *            the message itself
	 * @return the bundle
	 */
	private Bundle messageToBundle(String messageId, String topic, MqttMessage message) {
		Bundle result = new Bundle();
		result.putString(MqttServiceConstants.CALLBACK_MESSAGE_ID, messageId);
		result.putString(MqttServiceConstants.CALLBACK_DESTINATION_NAME, topic);
		result.putParcelable(MqttServiceConstants.CALLBACK_MESSAGE_PARCEL, new ParcelableMqttMessage(message));
		return result;
	}
	
	/**
	 * Close connection from the server
	 * 
	 */
	void close() {
		Log.d(TAG, "close()");
		try {
			if (mClient != null) {
				mClient.close();
			}
		} catch (MqttException e) {
			// Pass a new bundle, let handleException stores error messages.
			handleException(new Bundle(), e);
		}
	}

	/**
	 * 断开连接
	 */
	public void disconnect() {
		disconnect(-1, "");
	}

	/**
	 * 断开连接
	 * @param quiesceTimeout 超时时间（毫秒）
	 * @param activityToken 票据
	 */
	public void disconnect(long quiesceTimeout, @NonNull String activityToken) {
		Log.d(TAG, "mqtt disconnect");
		isDisconnected = true;
		final Bundle resultBundle = new Bundle();
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN, activityToken);
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.DISCONNECT_ACTION);
		if ((mClient != null) && (mClient.isConnected())) {
			IMqttActionListener listener = new DefMqttActionListener(mClientKey, resultBundle, mService);
			try {
				if (quiesceTimeout > 0) {
					mClient.disconnect(quiesceTimeout, null, listener);
				} else {
					mClient.disconnect(null, listener);
				}
			} catch (Exception e) {
				handleException(resultBundle, e);
			}
		} else {
			resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, NOT_CONNECTED);
			Log.e(MqttServiceConstants.DISCONNECT_ACTION, NOT_CONNECTED);
			mService.sendBroadcastToClient(mClientKey, Status.ERROR, resultBundle);
		}

		if (mConnectOptions != null && mConnectOptions.isCleanSession()) {
			// assume we'll clear the stored messages at this point
			mService.mMessageStore.clearAllMessages(mClientKey);
		}

		releaseWakeLock();
	}

	/**
	 * @return true if we are connected to an MQTT server
	 */
	public boolean isConnected() {
		return mClient != null && mClient.isConnected();
	}

	/**
	 * 向主题发送消息
	 * @param topic 主题名称
	 * @param message 消息对象
	 * @param activityToken 票据
	 */
	public IMqttDeliveryToken publish(String topic, MqttMessage message, String activityToken) {
		final Bundle resultBundle = new Bundle();
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.SEND_ACTION);
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN, activityToken);

		IMqttDeliveryToken sendToken = null;

		if ((mClient != null) && (mClient.isConnected())) {
			try {
				sendToken = mClient.publish(topic, message, null, new DefMqttActionListener(mClientKey, resultBundle, mService));
				storeSendDetails(topic, message, sendToken, activityToken);
			} catch (Exception e) {
				handleException(resultBundle, e);
			}
		} else if ((mClient !=null) && (this.bufferOpts != null) && (this.bufferOpts.isBufferEnabled())){
			// Client is not connected, but buffer is enabled, so sending message
			try {
				sendToken = mClient.publish(topic, message, null, new DefMqttActionListener(mClientKey, resultBundle, mService));
				storeSendDetails(topic, message, sendToken, activityToken);
			} catch (Exception e) {
				handleException(resultBundle, e);
			}
		}  else {
			Log.i(TAG, "Client is not connected, so not sending message");
			resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, NOT_CONNECTED);
			Log.e(MqttServiceConstants.SEND_ACTION, NOT_CONNECTED);
			mService.sendBroadcastToClient(mClientKey, Status.ERROR, resultBundle);
		}
		return sendToken;
	}

	/**
	 * 订阅主题
	 * @param topic            主题名称数组
	 * @param qos              服务质量数组 0，1，2
	 * @param activityToken    票据
	 * @param messageListeners 消息监听器
	 */
	public void subscribe(String[] topic, int[] qos, String activityToken, IMqttMessageListener[] messageListeners) {
		Log.d(TAG, "subscribe({" + Arrays.toString(topic) + "}," + Arrays.toString(qos) + ", {" + activityToken + "}");
		final Bundle resultBundle = new Bundle();
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.SUBSCRIBE_ACTION);
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN, activityToken);

		if((mClient != null) && (mClient.isConnected())){
			try {
				if (messageListeners == null || messageListeners.length == 0) {
					mClient.subscribe(topic, qos, null, new DefMqttActionListener(mClientKey, resultBundle, mService));
				} else {
					mClient.subscribe(topic, qos, messageListeners);
				}
			} catch (Exception e){
				handleException(resultBundle, e);
			}
		} else {
			resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, NOT_CONNECTED);
			Log.e("subscribe", NOT_CONNECTED);
			mService.sendBroadcastToClient(mClientKey, Status.ERROR, resultBundle);
		}
	}

	/**
	 * 取消订阅主题
	 * @param topic         主题名称数组
	 * @param activityToken 票据
	 */
	void unsubscribe(String[] topic, String activityToken) {
		Log.d(TAG, "unsubscribe({" + Arrays.toString(topic) + "}, {" + activityToken + "})");
		final Bundle resultBundle = new Bundle();
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.UNSUBSCRIBE_ACTION);
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN, activityToken);
		if ((mClient != null) && (mClient.isConnected())) {
			try {
				mClient.unsubscribe(topic, null, new DefMqttActionListener(mClientKey, resultBundle, mService));
			} catch (Exception e) {
				handleException(resultBundle, e);
			}
		} else {
			resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, NOT_CONNECTED);
			Log.e("subscribe", NOT_CONNECTED);
			mService.sendBroadcastToClient(mClientKey, Status.ERROR, resultBundle);
		}
	}

	/**
	 * 获取IMqttDeliveryToken数组
	 */
	public IMqttDeliveryToken[] getPendingDeliveryTokens() {
		return mClient.getPendingDeliveryTokens();
	}

	// Implement MqttCallback
	/**
	 * Callback for connectionLost
	 * 
	 * @param why
	 *            the exeception causing the break in communications
	 */
	@Override
	public void connectionLost(Throwable why) {
		Log.d(TAG, "connectionLost(" + why.getMessage() + ")");
		isDisconnected = true;
		try {
			if(!this.mConnectOptions.isAutomaticReconnect()) {
				mClient.disconnect(null, new IMqttActionListener() {

					@Override
					public void onSuccess(IMqttToken asyncActionToken) {
						// No action
					}

					@Override
					public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
						// No action
					}
				});
			} else {
				// Using the new Automatic reconnect functionality.
				// We can't force a disconnection, but we can speed one up
				mAlarmPingSender.schedule(100);
			}
		} catch (Exception e) {
			// ignore it - we've done our best
		}

		Bundle resultBundle = new Bundle();
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.ON_CONNECTION_LOST_ACTION);
		if (why != null) {
			resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, why.getMessage());
			if (why instanceof MqttException) {
				resultBundle.putSerializable(MqttServiceConstants.CALLBACK_EXCEPTION, why);
			}
			resultBundle.putString(MqttServiceConstants.CALLBACK_EXCEPTION_STACK, Log.getStackTraceString(why));
		}
		mService.sendBroadcastToClient(mClientKey, Status.OK, resultBundle);
		// client has lost connection no need for wake lock
		releaseWakeLock();
	}

	/**
	 * Callback to indicate a message has been delivered (the exact meaning of
	 * "has been delivered" is dependent on the QOS value)
	 * 
	 * @param messageToken
	 *            the messge token provided when the message was originally sent
	 */
	@Override
	public void deliveryComplete(IMqttDeliveryToken messageToken) {

		Log.d(TAG, "deliveryComplete(" + messageToken + ")");

		MqttMessage message = mSavedSentMessages.remove(messageToken);
		if (message != null) { // If I don't know about the message, it's
			// irrelevant
			String topic = mSavedTopics.remove(messageToken);
			String activityToken = mSavedActivityTokens.remove(messageToken);

			Bundle resultBundle = messageToBundle(null, topic, message);
			if (activityToken != null) {
				resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.SEND_ACTION);
				resultBundle.putString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN, activityToken);

				mService.sendBroadcastToClient(mClientKey, Status.OK, resultBundle);
			}
			resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.MESSAGE_DELIVERED_ACTION);
			mService.sendBroadcastToClient(mClientKey, Status.OK, resultBundle);
		}

		// this notification will have kept the connection alive but send the previously sechudled ping anyway
	}

	/**
	 * Callback when a message is received
	 * 
	 * @param topic
	 *            the topic on which the message was received
	 * @param message
	 *            the message itself
	 */
	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {

		Log.d(TAG, "messageArrived(" + topic + ",{" + message.toString() + "})");

		String messageId = mService.mMessageStore.saveMessage(mClientKey, topic, message);
	
		Bundle resultBundle = messageToBundle(messageId, topic, message);
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.MESSAGE_ARRIVED_ACTION);
		resultBundle.putString(MqttServiceConstants.CALLBACK_MESSAGE_ID, messageId);
		mService.sendBroadcastToClient(mClientKey, Status.OK, resultBundle);
				
	}



	/**
	 * Store details of sent messages so we can handle "deliveryComplete" callbacks from the mqttClient
	 * 
	 * @param topic
	 * @param msg
	 * @param messageToken
	 * @param activityToken
	 */
	private void storeSendDetails(final String topic, final MqttMessage msg, final IMqttDeliveryToken messageToken, final String activityToken) {
		mSavedTopics.put(messageToken, topic);
		mSavedSentMessages.put(messageToken, msg);
		mSavedActivityTokens.put(messageToken, activityToken);
	}

	/**
	 * 获取唤醒锁
	 */
	@SuppressLint("WakelockTimeout")
	private void acquireWakeLock() {
		if (mWakeLock == null) {
			String wakeLockTag = getClass().getName() + " " + mClientId + " " + "on host " + mServerURI;
			PowerManager pm = (PowerManager) mService.getSystemService(Service.POWER_SERVICE);
			mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, wakeLockTag);
		}
		mWakeLock.acquire();
	}

	/**
	 * 释放唤醒锁
	 */
	private void releaseWakeLock() {
		if(mWakeLock != null && mWakeLock.isHeld()){
			mWakeLock.release();
		}
	}

	/**
	 * Receive notification that we are offline<br>
	 * if cleanSession is true, we need to regard this as a disconnection
	 */
	public void offline() {
		if (!isDisconnected && !isCleanSession) {
			connectionLost(new Exception("Android offline"));
		}
	}
	
	/**
	* Reconnect<br>
	* Only appropriate if cleanSession is false and we were connected.
	* Declare as synchronized to avoid multiple calls to this method to send connect 
	* multiple times 
	*/
	synchronized void reconnect() {

		if (mClient == null) {
			Log.e(TAG,"Reconnect mClient = null. Will not do reconnect");
			return;
		}

		if (isConnecting) {
			Log.d(TAG, "The client is connecting. Reconnect return directly.");
			return ;
		}
		
		if(!MqttUtils.isOnline(mService)){
			Log.d(TAG, "The network is not reachable. Will not do reconnect");
			return;
		}

		if(mConnectOptions.isAutomaticReconnect()){
			//The Automatic reconnect functionality is enabled here
			Log.i(TAG, "Requesting Automatic reconnect using New Java AC");
			final Bundle resultBundle = new Bundle();
			resultBundle.putString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN, mReconnectActivityToken);
			resultBundle.putString(MqttServiceConstants.CALLBACK_INVOCATION_CONTEXT, null);
			resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.CONNECT_ACTION);
			try {
				mClient.reconnect();
			} catch (MqttException ex){
				Log.e(TAG, "Exception occurred attempting to reconnect: " + ex.getMessage());
				setConnectingState(false);
				handleException(resultBundle, ex);
			}
		} else if (isDisconnected && !isCleanSession) {
			// use the activityToke the same with action connect
			Log.d(TAG,"Do Real Reconnect!");
			final Bundle resultBundle = new Bundle();
			resultBundle.putString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN, mReconnectActivityToken);
			resultBundle.putString(MqttServiceConstants.CALLBACK_INVOCATION_CONTEXT, null);
			resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.CONNECT_ACTION);
			
			try {

				IMqttActionListener listener = new DefMqttActionListener(mClientKey, resultBundle, mService) {
					@Override
					public void onSuccess(IMqttToken asyncActionToken) {
						// since the device's cpu can go to sleep, acquire a wakelock and drop it later.
						Log.d(TAG, "Reconnect Success!");
						Log.d(TAG, "DeliverBacklog when reconnect.");
						doAfterConnectSuccess(resultBundle);
					}

					@Override
					public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
						super.onFailure(asyncActionToken, exception);
						doAfterConnectFail(resultBundle);
					}
				};
				mClient.connect(mConnectOptions, null, listener);
				setConnectingState(true);
			} catch (MqttException e) {
				Log.e(TAG, "Cannot reconnect to remote server." + e.getMessage());
				setConnectingState(false);
				handleException(resultBundle, e);
			} catch (Exception e){
				/*  TODO: Added Due to: https://github.com/eclipse/paho.mqtt.android/issues/101
				    For some reason in a small number of cases, mClient is null here and so
				    a NullPointer Exception is thrown. This is a workaround to pass the exception
				    up to the application. mClient should not be null so more investigation is
				    required.
				*/
				Log.e(TAG, "Cannot reconnect to remote server." + e.getMessage());
				setConnectingState(false);
				MqttException newEx = new MqttException(MqttException.REASON_CODE_UNEXPECTED_ERROR, e.getCause());
				handleException(resultBundle, newEx);
			}
		}
	}
	
	/**
	 * 
	 * @param isConnecting
	 */
	private synchronized void setConnectingState(boolean isConnecting){
		this.isConnecting = isConnecting; 
	}

	/**
	 * Sets the DisconnectedBufferOptions for this client
	 * @param bufferOpts
	 */
	public void setBufferOpts(DisconnectedBufferOptions bufferOpts) {
		this.bufferOpts = bufferOpts;
		mClient.setBufferOpts(bufferOpts);
	}

	public int getBufferedMessageCount(){
		return mClient.getBufferedMessageCount();
	}

	public MqttMessage getBufferedMessage(int bufferIndex){
		return mClient.getBufferedMessage(bufferIndex);
	}

	public void deleteBufferedMessage(int bufferIndex){
		mClient.deleteBufferedMessage(bufferIndex);
	}
}
