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

import android.app.Service;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.lodz.android.hermes.paho.android.service.Status;

import org.eclipse.paho.android.service.db.DbStoredData;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * MqttConnection holds a MqttAsyncClient {host,port,clientId} instance to perform 
 * MQTT operations to MQTT broker.
 * </p>
 * <p>
 * Most of the major API here is intended to implement the most general forms of
 * the methods in IMqttAsyncClient, with slight adjustments for the Android
 * environment<br>
 * These adjustments usually consist of adding two parameters to each method :-
 * <ul>
 * <li>invocationContext - a string passed from the application to identify the
 * context of the operation (mainly included for support of the javascript API
 * implementation)</li>
 * <li>activityToken - a string passed from the Activity to relate back to a
 * callback method or other context-specific data</li>
 * </ul>
 * </p>
 * <p>
 * Operations are very much asynchronous, so success and failure are notified by
 * packing the relevant data into Intent objects which are broadcast back to the
 * Activity via the MqttService.callbackToActivity() method.
 * </p>
 */
class MqttConnection implements MqttCallbackExtended {

	// Strings for Intents etc..
	private static final String TAG = "MqttConnection";
	// Error status messages
	private static final String NOT_CONNECTED = "not connected";

	// fields for the connection definition
	private String serverURI;
	public String getServerURI() {
		return serverURI;
	}

	public void setServerURI(String serverURI) {
		this.serverURI = serverURI;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	private String clientId;
	private MqttClientPersistence persistence = null;
	private MqttConnectOptions connectOptions;

	public MqttConnectOptions getConnectOptions() {
		return connectOptions;
	}

	public void setConnectOptions(MqttConnectOptions connectOptions) {
		this.connectOptions = connectOptions;
	}

	// Client handle, used for callbacks...
	private String clientHandle;


	//store connect ActivityToken for reconnect
	private String reconnectActivityToken = null;

	// our client object - instantiated on connect
	private MqttAsyncClient myClient = null;

	private AlarmPingSender alarmPingSender = null;

	// our (parent) service object
	private MqttService service = null;

	private volatile boolean disconnected = true;
	private boolean cleanSession = true;

	// Indicate this connection is connecting or not.
	// This variable uses to avoid reconnect multiple times.
	private volatile boolean isConnecting = false;

	// Saved sent messages and their corresponding Topics, activityTokens and
	// invocationContexts, so we can handle "deliveryComplete" callbacks
	// from the mqttClient
	private Map<IMqttDeliveryToken, String /* Topic */> savedTopics = new HashMap<>();
	private Map<IMqttDeliveryToken, MqttMessage> savedSentMessages = new HashMap<>();
	private Map<IMqttDeliveryToken, String> savedActivityTokens = new HashMap<>();

	private WakeLock wakelock = null;
	private String wakeLockTag = null;

	private DisconnectedBufferOptions bufferOpts = null;

	/**
	 * Constructor - create an MqttConnection to communicate with MQTT server
	 * 
	 * @param service
	 *            our "parent" service - we make callbacks to it
	 * @param serverURI
	 *            the URI of the MQTT server to which we will connect
	 * @param clientId
	 *            the name by which we will identify ourselves to the MQTT
	 *            server
	 * @param persistence
	 *            the persistence class to use to store in-flight message. If
	 *            null then the default persistence mechanism is used
	 * @param clientHandle
	 *            the "handle" by which the activity will identify us
	 */
	public MqttConnection(MqttService service, String serverURI, String clientId,
			MqttClientPersistence persistence, String clientHandle) {
		this.serverURI = serverURI;
		this.service = service;
		this.clientId = clientId;
		this.persistence = persistence;
		this.clientHandle = clientHandle;

		StringBuilder stringBuilder = new StringBuilder(this.getClass().getCanonicalName());
		stringBuilder.append(" ");
		stringBuilder.append(clientId);
		stringBuilder.append(" ");
		stringBuilder.append("on host ");
		stringBuilder.append(serverURI);
		wakeLockTag = stringBuilder.toString();
	}

	// The major API implementation follows
	/**
	 * Connect to the server specified when we were instantiated
	 * 
	 * @param options
	 *            timeout, etc
	 * @param activityToken
	 *            arbitrary identifier to be passed back to the Activity
	 */
	public void connect(MqttConnectOptions options, String activityToken) {
		connectOptions = options;
		reconnectActivityToken = activityToken;
		if (options != null) {
			cleanSession = options.isCleanSession();
		}
		if (connectOptions.isCleanSession()) { // if it's a clean session,
			// discard old data
			service.mMessageStore.clearAllMessages(clientHandle);
		}

		Log.d(TAG, "Connecting {" + serverURI + "} as {" + clientId + "}");
		final Bundle resultBundle = new Bundle();
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN, activityToken);
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.CONNECT_ACTION);
		
		try {
			if (persistence == null) {
				// ask Android where we can put files
				File myDir = service.getExternalFilesDir(TAG);
				if (myDir == null) {
					// No external storage, use internal storage instead.
					myDir = service.getDir(TAG, Context.MODE_PRIVATE);
					
					if(myDir == null){
						//Shouldn't happen.
						resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, "Error! No external and internal storage available");
						resultBundle.putSerializable(MqttServiceConstants.CALLBACK_EXCEPTION, new MqttPersistenceException());
						service.sendBroadcastToClient(clientHandle, Status.ERROR, resultBundle);
						return;
					}
				}
				// use that to setup MQTT client persistence storage
				persistence = new MqttDefaultFilePersistence(myDir.getAbsolutePath());
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
			
			if (myClient != null) {
				if (isConnecting ) {
					Log.d(TAG, "myClient != null and the client is connecting. Connect return directly.");
					Log.d(TAG,"Connect return:isConnecting:"+isConnecting+".disconnected:"+disconnected);
				}else if(!disconnected){
					Log.d(TAG,"myClient != null and the client is connected and notify!");
					doAfterConnectSuccess(resultBundle);
				}
				else {					
					Log.d(TAG, "myClient != null and the client is not connected");
					Log.d(TAG,"Do Real connect!");
					setConnectingState(true);
					myClient.connect(connectOptions, listener);
				}
			}
			
			// if myClient is null, then create a new connection
			else {
				alarmPingSender = new AlarmPingSender(service);
				myClient = new MqttAsyncClient(serverURI, clientId, persistence, alarmPingSender);
				myClient.setCallback(this);

				Log.d(TAG,"Do Real connect!");
				setConnectingState(true);
				myClient.connect(connectOptions, listener);
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
		service.sendBroadcastToClient(clientHandle, Status.OK, resultBundle);
		deliverBacklog();
		setConnectingState(false);
		disconnected = false;
		releaseWakeLock();
	}

	@Override
	public void connectComplete(boolean reconnect, String serverURI) {
		Bundle resultBundle = new Bundle();
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION,
				MqttServiceConstants.CONNECT_EXTENDED_ACTION);
		resultBundle.putBoolean(MqttServiceConstants.CALLBACK_RECONNECT, reconnect);
		resultBundle.putString(MqttServiceConstants.CALLBACK_SERVER_URI, serverURI);
		service.sendBroadcastToClient(clientHandle, Status.OK, resultBundle);
	}

	private void doAfterConnectFail(final Bundle resultBundle){
		//
		acquireWakeLock();
		disconnected = true;
		setConnectingState(false);
		service.sendBroadcastToClient(clientHandle, Status.ERROR, resultBundle);
		releaseWakeLock();
	}
	
	private void handleException(final Bundle resultBundle, Exception e) {
		resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, e.getLocalizedMessage());
		resultBundle.putSerializable(MqttServiceConstants.CALLBACK_EXCEPTION, e);
		service.sendBroadcastToClient(clientHandle, Status.ERROR, resultBundle);
	}

	/**
	 * Attempt to deliver any outstanding messages we've received but which the
	 * application hasn't acknowledged. If "cleanSession" was specified, we'll
	 * have already purged any such messages from our messageStore.
	 */
	private void deliverBacklog() {
		ArrayList<DbStoredData> backlog = service.mMessageStore.getAllMessages(clientHandle);
		for (DbStoredData data : backlog) {
			Bundle resultBundle = messageToBundle(data.messageId, data.topic, data.message);
			resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.MESSAGE_ARRIVED_ACTION);
			service.sendBroadcastToClient(clientHandle, Status.OK, resultBundle);

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
			if (myClient != null) {
				myClient.close();
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
		disconnected = true;
		final Bundle resultBundle = new Bundle();
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN, activityToken);
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.DISCONNECT_ACTION);
		if ((myClient != null) && (myClient.isConnected())) {
			IMqttActionListener listener = new DefMqttActionListener(clientHandle, resultBundle, service);
			try {
				if (quiesceTimeout > 0) {
					myClient.disconnect(quiesceTimeout, null, listener);
				} else {
					myClient.disconnect(null, listener);
				}
			} catch (Exception e) {
				handleException(resultBundle, e);
			}
		} else {
			resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, NOT_CONNECTED);
			Log.e(MqttServiceConstants.DISCONNECT_ACTION, NOT_CONNECTED);
			service.sendBroadcastToClient(clientHandle, Status.ERROR, resultBundle);
		}

		if (connectOptions != null && connectOptions.isCleanSession()) {
			// assume we'll clear the stored messages at this point
			service.mMessageStore.clearAllMessages(clientHandle);
		}

		releaseWakeLock();
	}

	/**
	 * @return true if we are connected to an MQTT server
	 */
	public boolean isConnected() {
		return myClient != null && myClient.isConnected();
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

		if ((myClient != null) && (myClient.isConnected())) {
			try {
				sendToken = myClient.publish(topic, message, null, new DefMqttActionListener(clientHandle, resultBundle, service));
				storeSendDetails(topic, message, sendToken, activityToken);
			} catch (Exception e) {
				handleException(resultBundle, e);
			}
		} else if ((myClient !=null) && (this.bufferOpts != null) && (this.bufferOpts.isBufferEnabled())){
			// Client is not connected, but buffer is enabled, so sending message
			try {
				sendToken = myClient.publish(topic, message, null, new DefMqttActionListener(clientHandle, resultBundle, service));
				storeSendDetails(topic, message, sendToken, activityToken);
			} catch (Exception e) {
				handleException(resultBundle, e);
			}
		}  else {
			Log.i(TAG, "Client is not connected, so not sending message");
			resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, NOT_CONNECTED);
			Log.e(MqttServiceConstants.SEND_ACTION, NOT_CONNECTED);
			service.sendBroadcastToClient(clientHandle, Status.ERROR, resultBundle);
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

		if((myClient != null) && (myClient.isConnected())){
			try {
				if (messageListeners == null || messageListeners.length == 0) {
					myClient.subscribe(topic, qos, null, new DefMqttActionListener(clientHandle, resultBundle, service));
				} else {
					myClient.subscribe(topic, qos, messageListeners);
				}
			} catch (Exception e){
				handleException(resultBundle, e);
			}
		} else {
			resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, NOT_CONNECTED);
			Log.e("subscribe", NOT_CONNECTED);
			service.sendBroadcastToClient(clientHandle, Status.ERROR, resultBundle);
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
		if ((myClient != null) && (myClient.isConnected())) {
			try {
				myClient.unsubscribe(topic, null, new DefMqttActionListener(clientHandle, resultBundle, service));
			} catch (Exception e) {
				handleException(resultBundle, e);
			}
		} else {
			resultBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, NOT_CONNECTED);
			Log.e("subscribe", NOT_CONNECTED);
			service.sendBroadcastToClient(clientHandle, Status.ERROR, resultBundle);
		}
	}

	/**
	 * Get tokens for all outstanding deliveries for a client
	 * 
	 * @return an array (possibly empty) of tokens
	 */
	public IMqttDeliveryToken[] getPendingDeliveryTokens() {
		return myClient.getPendingDeliveryTokens();
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
		disconnected = true;
		try {
			if(!this.connectOptions.isAutomaticReconnect()) {
				myClient.disconnect(null, new IMqttActionListener() {

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
				alarmPingSender.schedule(100);
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
		service.sendBroadcastToClient(clientHandle, Status.OK, resultBundle);
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

		MqttMessage message = savedSentMessages.remove(messageToken);
		if (message != null) { // If I don't know about the message, it's
			// irrelevant
			String topic = savedTopics.remove(messageToken);
			String activityToken = savedActivityTokens.remove(messageToken);

			Bundle resultBundle = messageToBundle(null, topic, message);
			if (activityToken != null) {
				resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.SEND_ACTION);
				resultBundle.putString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN, activityToken);

				service.sendBroadcastToClient(clientHandle, Status.OK, resultBundle);
			}
			resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.MESSAGE_DELIVERED_ACTION);
			service.sendBroadcastToClient(clientHandle, Status.OK, resultBundle);
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
	public void messageArrived(String topic, MqttMessage message)
			throws Exception {

		Log.d(TAG, "messageArrived(" + topic + ",{" + message.toString() + "})");

		String messageId = service.mMessageStore.saveMessage(clientHandle, topic, message);
	
		Bundle resultBundle = messageToBundle(messageId, topic, message);
		resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.MESSAGE_ARRIVED_ACTION);
		resultBundle.putString(MqttServiceConstants.CALLBACK_MESSAGE_ID, messageId);
		service.sendBroadcastToClient(clientHandle, Status.OK, resultBundle);
				
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
		savedTopics.put(messageToken, topic);
		savedSentMessages.put(messageToken, msg);
		savedActivityTokens.put(messageToken, activityToken);
	}

	/**
	 * Acquires a partial wake lock for this client
	 */
	private void acquireWakeLock() {
		if (wakelock == null) {
			PowerManager pm = (PowerManager) service
					.getSystemService(Service.POWER_SERVICE);
			wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
					wakeLockTag);
		}
		wakelock.acquire();

	}

	/**
	 * Releases the currently held wake lock for this client
	 */
	private void releaseWakeLock() {
		if(wakelock != null && wakelock.isHeld()){
			wakelock.release();
		}
	}

	/**
	 * Receive notification that we are offline<br>
	 * if cleanSession is true, we need to regard this as a disconnection
	 */
	public void offline() {
		if (!disconnected && !cleanSession) {
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

		if (myClient == null) {
			Log.e(TAG,"Reconnect myClient = null. Will not do reconnect");
			return;
		}

		if (isConnecting) {
			Log.d(TAG, "The client is connecting. Reconnect return directly.");
			return ;
		}
		
		if(!MqttUtils.isOnline(service)){
			Log.d(TAG, "The network is not reachable. Will not do reconnect");
			return;
		}

		if(connectOptions.isAutomaticReconnect()){
			//The Automatic reconnect functionality is enabled here
			Log.i(TAG, "Requesting Automatic reconnect using New Java AC");
			final Bundle resultBundle = new Bundle();
			resultBundle.putString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN, reconnectActivityToken);
			resultBundle.putString(MqttServiceConstants.CALLBACK_INVOCATION_CONTEXT, null);
			resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.CONNECT_ACTION);
			try {
				myClient.reconnect();
			} catch (MqttException ex){
				Log.e(TAG, "Exception occurred attempting to reconnect: " + ex.getMessage());
				setConnectingState(false);
				handleException(resultBundle, ex);
			}
		} else if (disconnected && !cleanSession) {
			// use the activityToke the same with action connect
			Log.d(TAG,"Do Real Reconnect!");
			final Bundle resultBundle = new Bundle();
			resultBundle.putString(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN, reconnectActivityToken);
			resultBundle.putString(MqttServiceConstants.CALLBACK_INVOCATION_CONTEXT, null);
			resultBundle.putString(MqttServiceConstants.CALLBACK_ACTION, MqttServiceConstants.CONNECT_ACTION);
			
			try {

				IMqttActionListener listener = new DefMqttActionListener(clientHandle, resultBundle, service) {
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
				myClient.connect(connectOptions, null, listener);
				setConnectingState(true);
			} catch (MqttException e) {
				Log.e(TAG, "Cannot reconnect to remote server." + e.getMessage());
				setConnectingState(false);
				handleException(resultBundle, e);
			} catch (Exception e){
				/*  TODO: Added Due to: https://github.com/eclipse/paho.mqtt.android/issues/101
				    For some reason in a small number of cases, myClient is null here and so
				    a NullPointer Exception is thrown. This is a workaround to pass the exception
				    up to the application. myClient should not be null so more investigation is
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
		myClient.setBufferOpts(bufferOpts);
	}

	public int getBufferedMessageCount(){
		return myClient.getBufferedMessageCount();
	}

	public MqttMessage getBufferedMessage(int bufferIndex){
		return myClient.getBufferedMessage(bufferIndex);
	}

	public void deleteBufferedMessage(int bufferIndex){
		myClient.deleteBufferedMessage(bufferIndex);
	}
}
