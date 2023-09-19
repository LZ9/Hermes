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
package org.eclipse.paho.android.service.db;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;

/** 数据库操作接口 */
public interface MessageStore {

	/**
	 * 缓存到达的消息数据
	 * @param clientId 客户端ID
	 * @param Topic 主题
	 * @param message 消息
	 */
	String saveMessage(String clientId, String Topic, MqttMessage message);

	/**
	 * 删除已经被应用消费的缓存消息数据
	 * @param clientId 客户端ID
	 * @param messageId 消息ID
	 */
	boolean deleteArrivedMessage(String clientId, String messageId);

	/**
	 * 获取本地缓存的所有消息数据
	 * @param clientId 客户端ID，如果为空，则返回所有客户端的数据
	 */
	ArrayList<DbStoredData> getAllMessages(String clientId);

	/**
	 * 清空本地缓存的所有消息数据
	 * @param clientId 客户端ID，如果为空，则清空所有客户端的数据
	 */
	void clearAllMessages(String clientId);
}
