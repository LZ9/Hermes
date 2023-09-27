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

import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
	@NonNull
	private WakeLock mWakeLock;

	private DisconnectedBufferOptions bufferOpts = null;

	public MqttConnection(MqttService service, String serverURI, String clientId, MqttClientPersistence persistence, String clientKey) {
		mServerURI = serverURI;
		mService = service;
		mClientId = clientId;
		mPersistence = persistence;
		mClientKey = clientKey;
		mWakeLock = MqttUtils.getWakeLock(service, getClass().getName() + " " + mClientId + " " + "on host " + mServerURI);
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
				mPersistence = new MqttDefaultFilePersistence(myDir.getAbsolutePath());
			}


			if (mClient == null) {
				mAlarmPingSender = new AlarmPingSender(mService);
				mClient = new MqttAsyncClient(mServerURI, mClientId, mPersistence, mAlarmPingSender);
				mClient.setCallback(this);
			}

			if (isConnecting) {
				Log.d(TAG, "now connecting");
				return;
			}
			if (!isDisconnected) {
				Log.d(TAG, "client is connected");
				doAfterConnectSuccess(resultBundle);
				return;
			}
			Log.d(TAG, "mClient != null and the client is not connected");
			Log.d(TAG, "Do Real connect!");
			setConnectingState(true);
			mClient.connect(mConnectOptions, new IMqttActionListener() {

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
			});
		} catch (Exception e) {
			Log.e(TAG, "Exception occurred attempting to connect: " + e.getMessage());
			setConnectingState(false);
			handleException(resultBundle, e);
		}
	}

	/** 连接成功 */
	private void doAfterConnectSuccess(final Bundle resultBundle) {
		MqttUtils.acquireWakeLock(mWakeLock);
		mService.sendBroadcastToClient(mClientKey, Status.OK, resultBundle);
		deliverBacklog();
		setConnectingState(false);
		isDisconnected = false;
		MqttUtils.releaseWakeLock(mWakeLock);
	}


	/** 连接失败 */
	private void doAfterConnectFail(final Bundle resultBundle){
		MqttUtils.acquireWakeLock(mWakeLock);
		isDisconnected = true;
		setConnectingState(false);
		mService.sendBroadcastToClient(mClientKey, Status.ERROR, resultBundle);
		MqttUtils.releaseWakeLock(mWakeLock);
	}

	/** 处理异常 */
	private void handleException(final Bundle resultBundle, Exception e) {
		resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, e.getLocalizedMessage());
		resultBundle.putSerializable(MqttServiceConstants.CALLBACK_EXCEPTION, e);
		mService.sendBroadcastToClient(mClientKey, Status.ERROR, resultBundle);
	}

	/**
	 * 将数据库缓存的到达消息全部返回给用户
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
	 * 创建消息的Bundle
	 * @param messageId 消息ID
	 * @param topic 主题
	 * @param message 消息对象
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
	 * 关闭客户端
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
	 * @param quiesceTimeout 在断开连接之前允许完成现有工作的时间（以毫秒为单位）。 值为零或更低意味着客户端不会停顿。
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

		MqttUtils.releaseWakeLock(mWakeLock);
	}

	/**
	 * 是否已连接
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

		try {
			if (mClient != null && mClient.isConnected()) {
				IMqttDeliveryToken sendToken = mClient.publish(topic, message, null, new DefMqttActionListener(mClientKey, resultBundle, mService));
				storeSendDetails(topic, message, sendToken, activityToken);
				return sendToken;
			}
			if (mClient != null && this.bufferOpts != null && this.bufferOpts.isBufferEnabled()) {
				// 虽然客户端未连接，但是缓冲已启用，也允许发送消息
				IMqttDeliveryToken sendToken = mClient.publish(topic, message, null, new DefMqttActionListener(mClientKey, resultBundle, mService));
				storeSendDetails(topic, message, sendToken, activityToken);
				return sendToken;
			}
			Log.i(TAG, "client is not connected, can not send message");
			resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, NOT_CONNECTED);
			Log.e(MqttServiceConstants.SEND_ACTION, NOT_CONNECTED);
			mService.sendBroadcastToClient(mClientKey, Status.ERROR, resultBundle);
		}catch (Exception e){
		    e.printStackTrace();
			handleException(resultBundle, e);
		}
		return null;
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

		if(mClient != null && mClient.isConnected()){
			try {
				if (messageListeners == null || messageListeners.length == 0) {
					mClient.subscribe(topic, qos, null, new DefMqttActionListener(mClientKey, resultBundle, mService));
				} else {
					mClient.subscribe(topic, qos, messageListeners);
				}
			} catch (Exception e){
				handleException(resultBundle, e);
			}
			return;
		}
		resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, NOT_CONNECTED);
		Log.e(TAG, "subscribe " + NOT_CONNECTED);
		mService.sendBroadcastToClient(mClientKey, Status.ERROR, resultBundle);
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
		if (mClient != null && mClient.isConnected()) {
			try {
				mClient.unsubscribe(topic, null, new DefMqttActionListener(mClientKey, resultBundle, mService));
			} catch (Exception e) {
				handleException(resultBundle, e);
			}
			return;
		}
		resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, NOT_CONNECTED);
		Log.e(TAG, "unsubscribe " + NOT_CONNECTED);
		mService.sendBroadcastToClient(mClientKey, Status.ERROR, resultBundle);
	}

	/**
	 * 获取IMqttDeliveryToken数组
	 */
	public IMqttDeliveryToken[] getPendingDeliveryTokens() {
		return mClient.getPendingDeliveryTokens();
	}

	/*-------------------------------------- 实现MqttCallbackExtended ----------------------------------------------------*/
	/**
	 * 连接丢失回调
	 * @param why 中断原因
	 *
	 */
	@Override
	public void connectionLost(@Nullable Throwable why) {
		Log.d(TAG, "connectionLost(" + why + ")");
		isDisconnected = true;
		try {
			if(!this.mConnectOptions.isAutomaticReconnect()) {
				mClient.disconnect();//没有开启自动重连则手动断开连接
			} else {
				mAlarmPingSender.schedule(100);
			}
		} catch (Exception e) {
			e.printStackTrace();
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
		MqttUtils.releaseWakeLock(mWakeLock);
	}


	@Override
	public void connectComplete(boolean reconnect, String serverURI) {
		Bundle resultBundle = new Bundle();
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.CONNECT_EXTENDED_ACTION);
		resultBundle.putBoolean(MqttServiceConstants.CALLBACK_RECONNECT, reconnect);
		resultBundle.putString(MqttServiceConstants.CALLBACK_SERVER_URI, serverURI);
		mService.sendBroadcastToClient(mClientKey, Status.OK, resultBundle);
	}

	/**
	 * 消息已传递
	 * @param messageToken 消息令牌
	 */
	@Override
	public void deliveryComplete(IMqttDeliveryToken messageToken) {
		Log.d(TAG, "deliveryComplete(" + messageToken + ")");

		MqttMessage message = mSavedSentMessages.remove(messageToken);
		if (message != null) {
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
	}

	/**
	 * 服务端消息到达
	 * @param topic 主题
	 * @param message 消息对象
	 */
	@Override
	public void messageArrived(String topic, MqttMessage message) {
		Log.d(TAG, "messageArrived(" + topic + ",{" + message.toString() + "})");
		String messageId = mService.mMessageStore.saveMessage(mClientKey, topic, message);
		Bundle resultBundle = messageToBundle(messageId, topic, message);
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.MESSAGE_ARRIVED_ACTION);
		resultBundle.putString(MqttServiceConstants.CALLBACK_MESSAGE_ID, messageId);
		mService.sendBroadcastToClient(mClientKey, Status.OK, resultBundle);
	}
	/*-------------------------------------- 实现MqttCallbackExtended ----------------------------------------------------*/


	/**
	 * 关键信息存储
	 * @param topic 主题
	 * @param msg 消息对象
	 * @param messageToken 票据
	 * @param activityToken  票据
	 */
	private void storeSendDetails(final String topic, final MqttMessage msg, final IMqttDeliveryToken messageToken, final String activityToken) {
		mSavedTopics.put(messageToken, topic);
		mSavedSentMessages.put(messageToken, msg);
		mSavedActivityTokens.put(messageToken, activityToken);
	}

	/**
	 * 设置客户端离线
	 */
	public void offline() {
		if (!isDisconnected && !isCleanSession) {
			connectionLost(new Exception("Android offline"));
		}
	}
	
	/**
	* 重连
	*/
	synchronized void reconnect() {

		if (mClient == null) {
			Log.e(TAG,"client is null");
			return;
		}

		if (isConnecting) {
			Log.d(TAG, "the client is connecting");
			return ;
		}
		
		if(!MqttUtils.isOnline(mService)){
			Log.d(TAG, "the network is offline, cannot reconnect");
			return;
		}

		if(mConnectOptions.isAutomaticReconnect()){ //开启自动重连
			Log.i(TAG, "start automatic reconnect");

			try {
				mClient.reconnect();
			} catch (MqttException ex){
				Log.e(TAG, "Exception occurred attempting to reconnect: " + ex.getMessage());
				setConnectingState(false);
				final Bundle resultBundle = new Bundle();
				resultBundle.putString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN, mReconnectActivityToken);
				resultBundle.putString(MqttServiceConstants.CALLBACK_INVOCATION_CONTEXT, null);
				resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.CONNECT_ACTION);
				handleException(resultBundle, ex);
			}
			return;
		}


		if (isDisconnected && !isCleanSession) { // 手动重连
			Log.d(TAG,"Do Real Reconnect!");
			final Bundle resultBundle = new Bundle();
			resultBundle.putString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN, mReconnectActivityToken);
			resultBundle.putString(MqttServiceConstants.CALLBACK_INVOCATION_CONTEXT, null);
			resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.CONNECT_ACTION);
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
			try {
				mClient.connect(mConnectOptions, null, listener);
				setConnectingState(true);
			} catch (Exception e){
				Log.e(TAG, "cannot reconnect to remote server", e);
				setConnectingState(false);
				handleException(resultBundle, e);
			}
		}
	}
	
	/**
	 * 设置是否连接中
	 * @param isConnecting 是否连接中
	 */
	private synchronized void setConnectingState(boolean isConnecting){
		this.isConnecting = isConnecting; 
	}

	/**
	 * 设置断连缓冲配置项
	 * @param bufferOpts 断连缓冲配置项
	 */
	public void setBufferOpts(DisconnectedBufferOptions bufferOpts) {
		this.bufferOpts = bufferOpts;
		mClient.setBufferOpts(bufferOpts);
	}

	/**
	 * 获取断连缓冲配置项
	 */
	public int getBufferedMessageCount(){
		return mClient.getBufferedMessageCount();
	}

	/**
	 * 获取消息
	 * @param bufferIndex 索引
	 */
	public MqttMessage getBufferedMessage(int bufferIndex){
		return mClient.getBufferedMessage(bufferIndex);
	}

	/**
	 * 删除消息
	 * @param bufferIndex 索引
	 */
	public void deleteBufferedMessage(int bufferIndex){
		mClient.deleteBufferedMessage(bufferIndex);
	}
}
