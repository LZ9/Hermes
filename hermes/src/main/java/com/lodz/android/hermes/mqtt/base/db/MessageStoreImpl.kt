package com.lodz.android.hermes.mqtt.base.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.lodz.android.hermes.modules.HermesLog
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.util.UUID
import kotlin.collections.ArrayList

/**
 * 实现数据库操作接口
 * @author zhouL
 * @date 2023/10/12
 */
class MessageStoreImpl(context: Context) : MessageStore {

    private val mDbHelper: MQTTDatabaseHelper = MQTTDatabaseHelper(context)

    override suspend fun saveMessage(clientKey: String, topic: String, message: MqttMessage): String {
        HermesLog.d(MQTTDatabaseHelper.TAG, "{$clientKey} start save {$topic} message")
        val values = ContentValues()
        val id = UUID.randomUUID().toString()
        values.put(MQTTDatabaseHelper.DB_COLUMN_MESSAGE_ID, id)
        values.put(MQTTDatabaseHelper.DB_COLUMN_CLIENT_HANDLE, clientKey)
        values.put(MQTTDatabaseHelper.DB_COLUMN_DESTINATION_NAME, topic)
        values.put(MQTTDatabaseHelper.DB_COLUMN_PAYLOAD, message.payload)
        values.put(MQTTDatabaseHelper.DB_COLUMN_QOS, message.qos)
        values.put(MQTTDatabaseHelper.DB_COLUMN_RETAINED, message.isRetained)
        values.put(MQTTDatabaseHelper.DB_COLUMN_DUPLICATE, message.isDuplicate)
        values.put(MQTTDatabaseHelper.DB_COLUMN_MTIMESTAMP, System.currentTimeMillis())
        mDbHelper.writableDatabase.use {
            it.insertOrThrow(MQTTDatabaseHelper.DB_ARRIVED_MESSAGE_TABLE_NAME, null, values)
            HermesLog.i(MQTTDatabaseHelper.TAG, "save finish , id = $id")
            return id
        }
    }

    override suspend fun deleteArrivedMessage(clientKey: String, messageId: String): Boolean {
        HermesLog.d(MQTTDatabaseHelper.TAG, "{$clientKey} start delete {$messageId}")
        val selectionArgs = arrayOf(messageId, clientKey)
        mDbHelper.writableDatabase.use {
            val rows = it.delete(
                MQTTDatabaseHelper.DB_ARRIVED_MESSAGE_TABLE_NAME,
                "${MQTTDatabaseHelper.DB_COLUMN_MESSAGE_ID}=? AND ${MQTTDatabaseHelper.DB_COLUMN_CLIENT_HANDLE}=?",
                selectionArgs
            )
            HermesLog.i(MQTTDatabaseHelper.TAG, "delete finish , rows = $rows")
            return rows == 1
        }
    }

    override suspend fun getAllMessages(clientKey: String): ArrayList<DbStoredData> {
        HermesLog.d(MQTTDatabaseHelper.TAG, "{$clientKey} start query all message")
        val list = ArrayList<DbStoredData>()
        val selectionArgs = arrayOf(clientKey)
        mDbHelper.writableDatabase.use { db ->
            db.query(
                MQTTDatabaseHelper.DB_ARRIVED_MESSAGE_TABLE_NAME,
                null,
                if (clientKey.isEmpty()) null else "${MQTTDatabaseHelper.DB_COLUMN_CLIENT_HANDLE}=?",
                if (clientKey.isEmpty()) null else selectionArgs,
                null,
                null,
                "${MQTTDatabaseHelper.DB_COLUMN_MTIMESTAMP} ASC"
            ).use { c ->
                var hasNext = c.moveToFirst()
                while (hasNext) {
                    val messageId = c.getString(getIndex(c, MQTTDatabaseHelper.DB_COLUMN_MESSAGE_ID))
                    val clientHandle = c.getString(getIndex(c, MQTTDatabaseHelper.DB_COLUMN_CLIENT_HANDLE))
                    val topic = c.getString(getIndex(c, MQTTDatabaseHelper.DB_COLUMN_DESTINATION_NAME))
                    val payload = c.getBlob(getIndex(c, MQTTDatabaseHelper.DB_COLUMN_PAYLOAD))
                    val qos = c.getInt(getIndex(c, MQTTDatabaseHelper.DB_COLUMN_QOS))
                    val retained = c.getString(getIndex(c, MQTTDatabaseHelper.DB_COLUMN_RETAINED)).toBoolean()
                    val dup = c.getString(getIndex(c, MQTTDatabaseHelper.DB_COLUMN_DUPLICATE)).toBoolean()

                    val message = MqttMessageHack(payload)
                    message.qos = qos
                    message.isRetained = retained
                    message.setDuplicateHack(dup)
                    list.add(DbStoredData(messageId, clientHandle, topic, message))
                    hasNext = c.moveToNext()
                }
                HermesLog.i(MQTTDatabaseHelper.TAG, "query finish , list size = ${list.size}")
                return list
            }
        }
    }

    private fun getIndex(c: Cursor, columnName: String): Int = c.getColumnIndex(columnName)

    override suspend fun clearAllMessages(clientKey: String) {
        HermesLog.d(MQTTDatabaseHelper.TAG, "{$clientKey} start clear all message")
        mDbHelper.writableDatabase.use {
            val rows = if (clientKey.isEmpty()) {
                it.delete(MQTTDatabaseHelper.DB_ARRIVED_MESSAGE_TABLE_NAME, null, null)
            } else {
                val selectionArgs = arrayOf(clientKey)
                it.delete(MQTTDatabaseHelper.DB_ARRIVED_MESSAGE_TABLE_NAME, "${MQTTDatabaseHelper.DB_COLUMN_CLIENT_HANDLE}=?", selectionArgs)
            }
            HermesLog.i(MQTTDatabaseHelper.TAG, "clear finish , rows = $rows")
        }
    }
}
