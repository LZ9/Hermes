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
 *
 *   Contributors:
 *     James Sutton - Removing SQL Injection vunerability (bug 467378)
 */
package org.eclipse.paho.android.service.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;

import org.eclipse.paho.android.service.MqttServiceConstants;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Implementation of the {@link MessageStore} interface, using a SQLite database
 */
public class MessageStoreImpl implements MessageStore {

    private static final String TAG = "MessageStoreImpl";

    private final MQTTDatabaseHelper mqttDb;

    public MessageStoreImpl(Context context) {
         mqttDb = new MQTTDatabaseHelper(context);
        Log.d(TAG, "MessageStoreImpl init");
    }

    /**
     * 缓存服务端推送来的消息数据
     * @param clientKey 客户端主键
     * @param topic 主题
     * @param message 消息对象
     */
    @Override
    public String saveMessage(String clientKey, String topic, MqttMessage message) {
        //需要异步执行
        Log.d(TAG, "{" + clientKey + "} start save message {" + message.toString() + "}");
        SQLiteDatabase db = mqttDb.getWritableDatabase();
        ContentValues values = new ContentValues();
        String id = UUID.randomUUID().toString();
        values.put(MqttServiceConstants.DB_COLUMN_MESSAGE_ID, id);
        values.put(MqttServiceConstants.DB_COLUMN_CLIENT_HANDLE, clientKey);
        values.put(MqttServiceConstants.DB_COLUMN_DESTINATION_NAME, topic);
        values.put(MqttServiceConstants.DB_COLUMN_PAYLOAD, message.getPayload());
        values.put(MqttServiceConstants.DB_COLUMN_QOS, message.getQos());
        values.put(MqttServiceConstants.DB_COLUMN_RETAINED, message.isRetained());
        values.put(MqttServiceConstants.DB_COLUMN_DUPLICATE, message.isDuplicate());
        values.put(MqttServiceConstants.DB_COLUMN_MTIMESTAMP, System.currentTimeMillis());
        try {
            db.insertOrThrow(MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME, null, values);
        }catch (Exception e){
            e.printStackTrace();
            Log.e(TAG, "save message error", e);
        }finally {
            db.close();
        }
        Log.i(TAG, "save finish , id = " + id);
        return id;
    }

    /**
     * 删除已经被应用消费的缓存消息数据
     * @param clientKey 客户端主键
     */
    @Override
    public boolean deleteArrivedMessage(String clientKey, String messageId) {
        Log.d(TAG, "{" + clientKey + "} start delete {" + messageId + "}");
        SQLiteDatabase db = mqttDb.getWritableDatabase();
        int rows = 0;
        String[] selectionArgs = new String[2];
        selectionArgs[0] = messageId;
        selectionArgs[1] = clientKey;

        try {
            rows = db.delete(MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME, MqttServiceConstants.DB_COLUMN_MESSAGE_ID + "=? AND " + MqttServiceConstants.DB_COLUMN_CLIENT_HANDLE + "=?", selectionArgs);
        } catch (SQLException e) {
            e.printStackTrace();
            Log.e(TAG, "delete message error", e);
        } finally {
            db.close();
        }
        Log.i(TAG, "delete finish , rows = " + rows);
        return rows == 1;
    }

    /**
     * 获取本地缓存的所有消息数据
     * @param clientKey 客户端主键，如果为空，则返回所有客户端的数据
     */
    @Override
    public ArrayList<DbStoredData> getAllMessages(String clientKey) {
        Log.d(TAG, "{" + clientKey + "} start query all message");
        ArrayList<DbStoredData> list = new ArrayList<>();
        SQLiteDatabase db = mqttDb.getWritableDatabase();
        String[] selectionArgs = {clientKey};
        Cursor c = null;
        try {
            if (TextUtils.isEmpty(clientKey)) {
                c = db.query(MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MqttServiceConstants.DB_COLUMN_MTIMESTAMP + " ASC");
            } else {
                c = db.query(MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME,
                        null,
                        MqttServiceConstants.DB_COLUMN_CLIENT_HANDLE + "=?",
                        selectionArgs,
                        null,
                        null,
                        MqttServiceConstants.DB_COLUMN_MTIMESTAMP + " ASC");
            }
            boolean hasNext = c.moveToFirst();
            while (hasNext){
                String messageId = c.getString(getIndex(c, MqttServiceConstants.DB_COLUMN_MESSAGE_ID));
                String clientHandle = c.getString(getIndex(c, MqttServiceConstants.DB_COLUMN_CLIENT_HANDLE));
                String topic = c.getString(getIndex(c, MqttServiceConstants.DB_COLUMN_DESTINATION_NAME));
                byte[] payload = c.getBlob(getIndex(c, MqttServiceConstants.DB_COLUMN_PAYLOAD));
                int qos = c.getInt(getIndex(c, MqttServiceConstants.DB_COLUMN_QOS));
                boolean retained = Boolean.parseBoolean(c.getString(getIndex(c, MqttServiceConstants.DB_COLUMN_RETAINED)));
                boolean dup = Boolean.parseBoolean(c.getString(getIndex(c, MqttServiceConstants.DB_COLUMN_DUPLICATE)));

                MqttMessageHack message = new MqttMessageHack(payload);
                message.setQos(qos);
                message.setRetained(retained);
                message.setDuplicateHack(dup);
                list.add(new DbStoredData(messageId, clientHandle, topic, message));
                hasNext = c.moveToNext();
            }
        }catch (Exception e){
            e.printStackTrace();
            Log.e(TAG, "query all message error", e);
        }finally {
            if (c != null){
                c.close();
            }
            db.close();
        }
        Log.i(TAG, "query finish , list size = " + list.size());
        return list;
    }

    private int getIndex(Cursor c, String columnName) {
        return c.getColumnIndex(columnName);
    }

    /**
     * 清空本地缓存的所有消息数据
     * @param clientKey 客户端主键，如果为空，则清空所有客户端的数据
     */
    @Override
    public void clearAllMessages(String clientKey) {
        Log.d(TAG, "{" + clientKey + "} start clear all message");
        SQLiteDatabase db = mqttDb.getWritableDatabase();
        int rows = 0;
        try {
            if (TextUtils.isEmpty(clientKey)) {
                rows = db.delete(MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME, null, null);
            }else {
                String[] selectionArgs = new String[1];
                selectionArgs[0] = clientKey;
                rows = db.delete(MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME, MqttServiceConstants.DB_COLUMN_CLIENT_HANDLE + "=?", selectionArgs);
            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "clear all message error", e);
        } finally {
            db.close();
        }
        Log.i(TAG, "clear finish , rows = " + rows);
    }
}