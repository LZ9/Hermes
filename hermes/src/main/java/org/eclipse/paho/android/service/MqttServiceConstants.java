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

/**
 * Various strings used to identify operations or data in the Android MQTT
 * service, mainly used in Intents passed between Activities and the Service.
 */
public interface MqttServiceConstants {

  /** 数据库字段-是否重复 */
  String DB_COLUMN_DUPLICATE = "duplicate";

  /** 数据库字段-是否服务器保留 */
  String DB_COLUMN_RETAINED = "retained";
  /** 数据库字段-消息的服务质量 */
  String DB_COLUMN_QOS = "qos";
  /** 数据库字段-数据内容 */
  String DB_COLUMN_PAYLOAD = "payload";
  /** 数据库字段-主题名称 */
  String DB_COLUMN_DESTINATION_NAME = "destinationName";
  /** 数据库字段-客户端标识符 */
  String DB_COLUMN_CLIENT_HANDLE = "clientHandle";
  /** 数据库字段-消息ID */
  String DB_COLUMN_MESSAGE_ID = "messageId";
  /** 数据库字段-消息到达时间 */
  String DB_COLUMN_MTIMESTAMP = "mtimestamp";
  /** 数据库消息表表名 */
  String DB_ARRIVED_MESSAGE_TABLE_NAME = "MqttArrivedMessageTable";




  //Intent prefix for Ping sender.
  String PING_SENDER = MqttService.TAG + ".pingSender.";
  
  //Constant for wakelock
  String PING_WAKELOCK = "MqttService.client.";

  // the name of the table in the database to which we will save messages

  String FILE_PERSISTENCE_DIR_NAME = "MqttConnection";

}