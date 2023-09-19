/*******************************************************************************
 * Copyright (c) 1999, 2014 IBM Corp.
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
package org.eclipse.paho.android.service.token;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * <p>
 * Implementation of the IMqttDeliveryToken interface for use from within the MqttAndroidClient implementation
 */
public class MqttDeliveryTokenAndroid extends MqttTokenAndroid implements IMqttDeliveryToken {

	// The message which is being tracked by this token
	private final MqttMessage mMqttMessage;

	public MqttDeliveryTokenAndroid(MqttAndroidClient client, Object userContext, IMqttActionListener listener, MqttMessage message) {
		super(client, userContext, listener);
		this.mMqttMessage = message;
	}

	@Override
	public MqttMessage getMessage()  {
		return mMqttMessage;
	}

}
