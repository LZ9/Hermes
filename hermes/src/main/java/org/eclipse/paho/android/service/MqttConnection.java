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

import android.accounts.NetworkErrorException;
import android.content.Context;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.eclipse.paho.android.service.bean.ClientInfoBean;
import org.eclipse.paho.android.service.contract.DisconnectMqttEventActionListener;
import org.eclipse.paho.android.service.contract.PublishMqttEventActionListener;
import org.eclipse.paho.android.service.contract.SubscribeMqttEventActionListener;
import org.eclipse.paho.android.service.contract.UnsubscribeMqttEventActionListener;
import org.eclipse.paho.android.service.db.DbStoredData;
import org.eclipse.paho.android.service.db.MessageStore;
import org.eclipse.paho.android.service.event.MqttAction;
import org.eclipse.paho.android.service.event.MqttEvent;
import org.eclipse.paho.android.service.sender.AlarmPingSender;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Arrays;

public class MqttConnection implements MqttCallbackExtended {

	private static final String TAG = "MqttConnection";

	private final Context mContext;

	/**
	 * 客户端
	 */
	@Nullable
	private  MqttAsyncClient mClient = null;

	/**
	 * ping数据包发送器
	 */
	private final AlarmPingSender mAlarmPingSender;

	/**
	 * 是否连接中
	 */
	private volatile boolean isConnecting = false;

	/** 唤醒锁 */
	@NonNull
	private final WakeLock mWakeLock;

	/** 数据库操作接口 */
	@NonNull
	public final MessageStore mMessageStore;

	private final ClientInfoBean mBean;

	private DisconnectedBufferOptions bufferOpts = null;

	public MqttConnection(Context context, ClientInfoBean bean, @NonNull MqttClientPersistence persistence, @NonNull MessageStore messageStore) {
		mContext = context;
		mBean = bean;
		mMessageStore = messageStore;
		mWakeLock = MqttUtils.getWakeLock(context, getClass().getName() + " " + bean.getClientId() + " " + "on host " + bean.getServerURI());
		mAlarmPingSender = new AlarmPingSender(mContext);
		try {
			mClient = new MqttAsyncClient(bean.getServerURI(), bean.getClientId(), persistence, mAlarmPingSender);
			mClient.setCallback(this);
		}catch (Exception e){
			e.printStackTrace();
			EventBus.getDefault().post(MqttEvent.createFail(mBean.getClientKey(), MqttAction.ACTION_CREATE_CLIENT, "create MqttAsyncClient fail",e));
		}
	}

	/** 连接服务器 */
	public void connect() {
		if (mClient == null) {
			EventBus.getDefault().post(MqttEvent.createConnectFail(mBean.getClientKey(), "client is null", new NullPointerException()));
			return;
		}
		Log.d(TAG, "start connect {" + mBean.getClientKey() + "}");
		if (mBean.getConnectOptions().isCleanSession()) { // 如果是新的会话则清空数据库缓存
			mMessageStore.clearAllMessages(mBean.getClientKey());
		}

		if (isConnecting) {
			Log.d(TAG, "now connecting");
			return;
		}
		if (mClient.isConnected()) {
			Log.d(TAG, "client is connected");
			connectSuccess();
			return;
		}
		Log.d(TAG, "start connect!");
		isConnecting = true;
		try {
			mClient.connect(mBean.getConnectOptions(), mConnectActionListener);

		} catch (Exception e) {
			e.printStackTrace();
			isConnecting = false;
			EventBus.getDefault().post(MqttEvent.createConnectFail(mBean.getClientKey(), "connect fail", e));
		}
	}

	private final IMqttActionListener mConnectActionListener = new IMqttActionListener() {
		@Override
		public void onSuccess(IMqttToken asyncActionToken) {
			Log.d(TAG, "connect success!");
			connectSuccess();
		}

		@Override
		public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
			Log.e(TAG, "connect fail, call connect to reconnect.reason:" + exception.getMessage());
			connectFail("connect failure", exception);
		}
	};

	/** 连接成功 */
	private void connectSuccess() {
		MqttUtils.acquireWakeLock(mWakeLock);
		EventBus.getDefault().post(MqttEvent.createConnectSuccess(mBean.getClientKey()));
		deliverBacklog();
		isConnecting = false;
		MqttUtils.releaseWakeLock(mWakeLock);
	}

	/**
	 * 将数据库缓存的到达消息全部返回给用户
	 */
	private void deliverBacklog() {
		ArrayList<DbStoredData> backlog = mMessageStore.getAllMessages(mBean.getClientKey());
		for (DbStoredData data : backlog) {
			EventBus.getDefault().post(MqttEvent.createMsgArrived(mBean.getClientKey(), data));
		}
	}

	/** 连接失败 */
	private void connectFail(String errorMsg, Throwable e){
		MqttUtils.acquireWakeLock(mWakeLock);
		isConnecting = false;
		EventBus.getDefault().post(MqttEvent.createConnectFail(mBean.getClientKey(), errorMsg, e));
		MqttUtils.releaseWakeLock(mWakeLock);
	}

	/** 关闭客户端 */
	void close() {
		Log.d(TAG, "close client");
		try {
			if (mClient != null) {
				mClient.close();
			}
			EventBus.getDefault().post(MqttEvent.createSuccess(mBean.getClientKey(), MqttAction.ACTION_CLOSE));
		} catch (Exception e) {
			e.printStackTrace();
			EventBus.getDefault().post(MqttEvent.createFail(mBean.getClientKey(), MqttAction.ACTION_CLOSE, "close client fail", e));
		}
	}

	/**
	 * 断开连接
	 */
	public void disconnect() {
		disconnect(-1);
	}

	/**
	 * 断开连接
	 * @param quiesceTimeout 在断开连接之前允许完成现有工作的时间（以毫秒为单位）。 值为零或更低意味着客户端不会停顿。
	 */
	public void disconnect(long quiesceTimeout) {
		Log.d(TAG, "mqtt disconnect {" + mBean.getClientKey() + "}");
		if (mClient == null) {
			EventBus.getDefault().post(MqttEvent.createFail(mBean.getClientKey(), MqttAction.ACTION_DISCONNECT, "client is null", new NullPointerException()));
			return;
		}
		if (!mClient.isConnected()) {
			EventBus.getDefault().post(MqttEvent.createSuccess(mBean.getClientKey(), MqttAction.ACTION_DISCONNECT));
			return;
		}
		Log.d(TAG, "start disconnect!");
		IMqttActionListener listener = new DisconnectMqttEventActionListener(mBean.getClientKey(), "disconnect failure");
		try {
			if (quiesceTimeout > 0) {
				mClient.disconnect(quiesceTimeout, null, listener);
			} else {
				mClient.disconnect(null, listener);
			}
		} catch (Exception e) {
			e.printStackTrace();
			EventBus.getDefault().post(MqttEvent.createFail(mBean.getClientKey(), MqttAction.ACTION_DISCONNECT, "disconnect fail", e));
		}
		if (mBean.getConnectOptions().isCleanSession()) {
			mMessageStore.clearAllMessages(mBean.getClientKey());
		}
		MqttUtils.releaseWakeLock(mWakeLock);
	}

	/** 是否已连接 */
	public boolean isConnected() {
		return mClient != null && mClient.isConnected();
	}

	/**
	 * 向主题发送消息
	 * @param topic 主题名称
	 * @param message 消息对象
	 */
	public IMqttDeliveryToken publish(String topic, MqttMessage message) {
		if (mClient == null) {
			EventBus.getDefault().post(MqttEvent.createPublishFail(mBean.getClientKey(), topic, "client is null", new NullPointerException()));
			return null;
		}
		IMqttActionListener listener =  new PublishMqttEventActionListener(mBean.getClientKey(), "publish failure", topic, message);
		try {
			if (mClient.isConnected()) {
				return mClient.publish(topic, message, null, listener);
			}
			if (this.bufferOpts != null && this.bufferOpts.isBufferEnabled()) {
				// 虽然客户端未连接，但是缓冲已启用，也允许发送消息
				return mClient.publish(topic, message, null, listener);
			}
			EventBus.getDefault().post(MqttEvent.createPublishFail(mBean.getClientKey(), topic, "client is not connected, can not send message", new RuntimeException()));
		}catch (Exception e){
			e.printStackTrace();
			EventBus.getDefault().post(MqttEvent.createPublishFail(mBean.getClientKey(), topic, "publish fail", e));
		}
		return null;
	}

	/**
	 * 订阅主题
	 * @param topic            主题名称数组
	 * @param qos              服务质量数组 0，1，2
	 * @param messageListeners 消息监听器
	 */
	public void subscribe(String[] topic, int[] qos, @Nullable IMqttMessageListener[] messageListeners) {
		Log.d(TAG, "subscribe({" + Arrays.toString(topic) + "}," + Arrays.toString(qos));
		if (mClient == null) {
			EventBus.getDefault().post(MqttEvent.createSubscribeFail(mBean.getClientKey(), topic, "client is null", new NullPointerException()));
			return;
		}
		if (!mClient.isConnected()) {
			EventBus.getDefault().post(MqttEvent.createSubscribeFail(mBean.getClientKey(), topic, "client is disconnect", new IllegalStateException()));
			return;
		}
		IMqttActionListener listener = new SubscribeMqttEventActionListener(mBean.getClientKey(), "subscribe failure", topic);
		try {
			if (messageListeners == null || messageListeners.length == 0) {
				mClient.subscribe(topic, qos, null, listener);
			} else {
				mClient.subscribe(topic, qos, messageListeners);
			}
		} catch (Exception e) {
			e.printStackTrace();
			EventBus.getDefault().post(MqttEvent.createSubscribeFail(mBean.getClientKey(), topic, "subscribe fail", new IllegalStateException()));
		}
	}

	/**
	 * 取消订阅主题
	 * @param topic         主题名称数组
	 */
	void unsubscribe(String[] topic) {
		Log.d(TAG, "unsubscribe({" + Arrays.toString(topic) );

		if (mClient == null) {
			EventBus.getDefault().post(MqttEvent.createUnsubscribeFail(mBean.getClientKey(), topic, "client is null", new NullPointerException()));
			return;
		}
		if (!mClient.isConnected()) {
			EventBus.getDefault().post(MqttEvent.createUnsubscribeFail(mBean.getClientKey(), topic, "client is disconnect", new IllegalStateException()));
			return;
		}
		try {
			mClient.unsubscribe(topic, null, new UnsubscribeMqttEventActionListener(mBean.getClientKey(), "unsubscribe failure", topic));
		} catch (Exception e) {
			e.printStackTrace();
			EventBus.getDefault().post(MqttEvent.createUnsubscribeFail(mBean.getClientKey(), topic, "unsubscribe fail", new IllegalStateException()));
		}
	}

	/**
	 * 获取IMqttDeliveryToken数组
	 */
	public IMqttDeliveryToken[] getPendingDeliveryTokens() {
		if (mClient == null) {
			return null;
		}
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
		EventBus.getDefault().post(MqttEvent.createConnectionLost(mBean.getClientKey(), why));
		if (mClient == null){
			return;
		}
		try {
			if(!mBean.getConnectOptions().isAutomaticReconnect()) {
				mClient.disconnect();//没有开启自动重连则手动断开连接
			} else {
				mAlarmPingSender.schedule(100);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		MqttUtils.releaseWakeLock(mWakeLock);
	}


	@Override
	public void connectComplete(boolean reconnect, String serverURI) {
		EventBus.getDefault().post(MqttEvent.createConnectComplete(mBean.getClientKey(), reconnect, serverURI));
	}

	/**
	 * 发送的消息已到达
	 * @param messageToken 消息令牌
	 */
	@Override
	public void deliveryComplete(IMqttDeliveryToken messageToken) {
		Log.d(TAG, "deliveryComplete(" + messageToken + ")");
		EventBus.getDefault().post(MqttEvent.createDeliveryComplete(mBean.getClientKey(), messageToken));
	}

	/**
	 * 服务端消息到达
	 * @param topic 主题
	 * @param message 消息对象
	 */
	@Override
	public void messageArrived(String topic, MqttMessage message) {
		Log.d(TAG, "messageArrived(" + topic + ",{" + message.toString() + "})");
		String messageId = mMessageStore.saveMessage(mBean.getClientKey(), topic, message);
		DbStoredData data = new DbStoredData(messageId, mBean.getClientKey(), topic,message);
		EventBus.getDefault().post(MqttEvent.createMsgArrived(mBean.getClientKey(), data));
	}
	/*-------------------------------------- 实现MqttCallbackExtended ----------------------------------------------------*/


	/**
	 * 设置客户端离线
	 */
	public void offline() {
		if (mClient == null){
			return;
		}
		if (mClient.isConnected() && !mBean.getConnectOptions().isCleanSession()) {
			connectionLost(new NetworkErrorException("mqtt offline"));
		}
	}
	
	/** 重连 */
	synchronized void reconnect() {
		if (mClient == null) {
			Log.e(TAG, "client is null");
			return;
		}

		if (isConnecting) {
			Log.d(TAG, "the client is connecting");
			return;
		}

		if (!MqttUtils.isOnline(mContext)) {
			Log.d(TAG, "the network is offline, cannot reconnect");
			return;
		}

		if(mBean.getConnectOptions().isAutomaticReconnect()){ //开启自动重连
			Log.i(TAG, "start automatic reconnect");
			try {
				mClient.reconnect();
			} catch (Exception e){
				e.printStackTrace();
				isConnecting = false;
				EventBus.getDefault().post(MqttEvent.createConnectFail(mBean.getClientKey(), "reconnect fail", e));
			}
			return;
		}

		if (!mClient.isConnected() && !mBean.getConnectOptions().isCleanSession()) { // 手动重连
			connect();
		}
	}
	
	/**
	 * 设置断连缓冲配置项
	 * @param bufferOpts 断连缓冲配置项
	 */
	public void setBufferOpts(DisconnectedBufferOptions bufferOpts) {
		this.bufferOpts = bufferOpts;
		if (mClient != null){
			mClient.setBufferOpts(bufferOpts);
		}
	}

	/**
	 * 获取断连缓冲配置项
	 */
	public int getBufferedMessageCount(){
		return mClient != null ? mClient.getBufferedMessageCount() : 0;
	}

	/**
	 * 获取消息
	 * @param bufferIndex 索引
	 */
	public MqttMessage getBufferedMessage(int bufferIndex){
		return mClient != null ? mClient.getBufferedMessage(bufferIndex) : null;
	}

	/**
	 * 删除消息
	 * @param bufferIndex 索引
	 */
	public void deleteBufferedMessage(int bufferIndex){
		if (mClient != null){
			mClient.deleteBufferedMessage(bufferIndex);
		}
	}
}
