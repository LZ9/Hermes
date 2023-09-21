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
package org.eclipse.paho.android.service;

import android.os.Binder;

public class MqttServiceBinder extends Binder {

	private final MqttService mMqttService;

	public MqttServiceBinder(MqttService mqttService) {
		this.mMqttService = mqttService;
	}

	/**
	 * 获取mqtt服务对象
	 */
	public MqttService getService() {
		return mMqttService;
	}

}
