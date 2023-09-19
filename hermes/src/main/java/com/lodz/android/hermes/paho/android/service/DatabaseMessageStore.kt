//package com.lodz.android.hermes.paho.android.service
//
//import android.content.ContentValues
//import android.content.Context
//import android.database.Cursor
//import android.database.SQLException
//import android.database.sqlite.SQLiteDatabase
//import android.database.sqlite.SQLiteOpenHelper
//import org.eclipse.paho.client.mqttv3.MqttMessage
//import java.util.UUID
//
//
///**
// * Implementation of the {@link MessageStore} interface, using a SQLite database
// */
//class MessageStoreImpl(service: MqttService, context: Context) : MessageStore {
//
//    companion object{
//        // TAG used for indentify trace data etc.
//        private const val TAG = "MessageStoreImpl"
//
//        // One "private" database column name
//        // The other database column names are defined in MqttServiceConstants
//        private const val MTIMESTAMP = "mtimestamp"
//
//        // the name of the table in the database to which we will save messages
//        private const val ARRIVED_MESSAGE_TABLE_NAME = "MqttArrivedMessageTable"
//
//        // TAG used for indentify trace data etc.
//        private const val SQLITE_TAG = "MQTTDatabaseHelper"
//
//        private const val DATABASE_NAME = "mqttAndroidService.db"
//
//        // database version, used to recognise when we need to upgrade
//        // (delete and recreate)
//        private const val DATABASE_VERSION = 1
//    }
//
//    // the database
//    private var db: SQLiteDatabase? = null
//
//    // a SQLiteOpenHelper specific for this database
//    private var mqttDb: MQTTDatabaseHelper? = null
//
//    // a place to send trace data
//    private var traceHandler: MqttTraceHandler? = null
//
//    init {
//        // Open message database
//        mqttDb =  MQTTDatabaseHelper(traceHandler, context)
//
//        // Android documentation suggests that this perhaps could/should be done in another thread, but as the database is only one table, I doubt it matters...
//        traceHandler?.traceDebug(TAG, "MessageStoreImpl<init> complete")
//    }
//
//    /** We need a SQLiteOpenHelper to handle database creation and updating */
//    private inner class MQTTDatabaseHelper(private val traceHandler:MqttTraceHandler? , context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
//
//        override fun onCreate(db: SQLiteDatabase?) {
//            val createArrivedTableStatement = ("CREATE TABLE "
//                    + ARRIVED_MESSAGE_TABLE_NAME + "("
//                    + MqttServiceConstants.MESSAGE_ID + " TEXT PRIMARY KEY, "
//                    + MqttServiceConstants.CLIENT_HANDLE + " TEXT, "
//                    + MqttServiceConstants.DESTINATION_NAME + " TEXT, "
//                    + MqttServiceConstants.PAYLOAD + " BLOB, "
//                    + MqttServiceConstants.QOS + " INTEGER, "
//                    + MqttServiceConstants.RETAINED + " TEXT, "
//                    + MqttServiceConstants.DUPLICATE + " TEXT, " + MTIMESTAMP
//                    + " INTEGER" + ");")
//            traceHandler?.traceDebug(SQLITE_TAG, "onCreate {$createArrivedTableStatement}")
//            try {
//                db?.execSQL(createArrivedTableStatement)
//                traceHandler?.traceDebug(SQLITE_TAG, "created the table")
//            } catch (e: Exception) {
//                e.printStackTrace()
//                traceHandler?.traceException(SQLITE_TAG, "onCreate", e)
//                throw e
//            }
//        }
//
//        override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
//            traceHandler?.traceDebug(SQLITE_TAG, "onUpgrade")
//            try {
//                db?.execSQL("DROP TABLE IF EXISTS $ARRIVED_MESSAGE_TABLE_NAME")
//            } catch (e: Exception) {
//                e.printStackTrace()
//                traceHandler?.traceException(SQLITE_TAG, "onUpgrade", e)
//                throw e
//            }
//            onCreate(db)
//            traceHandler?.traceDebug(SQLITE_TAG, "onUpgrade complete")
//        }
//    }
//
//    /**
//     * Store an MQTT message
//     * @param clientHandle identifier for the client storing the message
//     * @param topic The topic on which the message was published
//     * @param message the arrived MQTT message
//     * @return an identifier for the message, so that it can be removed when appropriate
//     */
//    override fun storeArrived(clientHandle: String, topic: String?, message: MqttMessage?): String {
//        db = mqttDb?.writableDatabase
//        traceHandler?.traceDebug(TAG, "storeArrived{$clientHandle}, {${message.toString()}}")
//
//        val payload = message?.payload
//        val qos = message?.qos
//        val retained = message?.isRetained ?: false
//        val duplicate = message?.isDuplicate ?: false
//
//        val values = ContentValues()
//        val id = UUID.randomUUID().toString()
//        values.put(MqttServiceConstants.MESSAGE_ID, id)
//        values.put(MqttServiceConstants.CLIENT_HANDLE, clientHandle)
//        values.put(MqttServiceConstants.DESTINATION_NAME, topic)
//        values.put(MqttServiceConstants.PAYLOAD, payload)
//        values.put(MqttServiceConstants.QOS, qos)
//        values.put(MqttServiceConstants.RETAINED, retained)
//        values.put(MqttServiceConstants.DUPLICATE, duplicate)
//        values.put(MTIMESTAMP, System.currentTimeMillis())
//        try {
//            db?.insertOrThrow(ARRIVED_MESSAGE_TABLE_NAME, null, values)
//        } catch (e: Exception) {
//            e.printStackTrace()
//            traceHandler?.traceException(TAG, "onUpgrade", e)
//            throw e
//        }
//
//        val count = getArrivedRowCount(clientHandle)
//        traceHandler?.traceDebug(TAG, "storeArrived: inserted message with id of {$id} - Number of messages in database for this clientHandle = $count")
//        return id
//    }
//
//    private fun getArrivedRowCount(clientHandle: String): Int {
//        var count = 0
//        val projection = arrayOf(MqttServiceConstants.MESSAGE_ID)
//        val selection = MqttServiceConstants.CLIENT_HANDLE + "=?"
//        val selectionArgs = arrayOf(clientHandle)
//
//        val database = db ?: return count
//
//        val c = database.query(
//            ARRIVED_MESSAGE_TABLE_NAME, // Table Name
//            projection, // The columns to return;
//            selection, // Columns for WHERE Clause
//            selectionArgs , // The values for the WHERE Cause
//            null,  //Don't group the rows
//            null,  // Don't filter by row groups
//            null   // The sort order
//        )
//        if (c.moveToFirst()) {
//            count = c.getInt(0)
//        }
//        c.close()
//        return count
//    }
//
//    /**
//     * Delete an MQTT message.
//     * @param clientHandle identifier for the client which stored the message
//     * @param id the identifying string returned when the message was stored
//     * @return true if the message was found and deleted
//     */
//    override fun discardArrived(clientHandle: String, id: String): Boolean {
//        db = mqttDb?.writableDatabase
//        traceHandler?.traceDebug(TAG, "discardArrived{$clientHandle}, {$id}")
//        var rows = 0
//        val selectionArgs = arrayOf(id, clientHandle)
//        try {
//            rows = db?.delete(ARRIVED_MESSAGE_TABLE_NAME, MqttServiceConstants.MESSAGE_ID + "=? AND " + MqttServiceConstants.CLIENT_HANDLE + "=?", selectionArgs)      ?: throw SQLException()
//        } catch (e: Exception) {
//            e.printStackTrace()
//            traceHandler?.traceException(TAG, "discardArrived", e)
//            throw e
//        }
//        if (rows != 1) {
//            traceHandler?.traceError(TAG, "discardArrived - Error deleting message {$id} from database: Rows affected = $rows")
//            return false
//        }
//        val count = getArrivedRowCount(clientHandle)
//        traceHandler?.traceDebug(TAG, "discardArrived - Message deleted successfully. - messages in db for this clientHandle $count")
//        return true
//    }
//
//    /**
//     * Get an iterator over all messages stored (optionally for a specific client)
//     * @param clientHandle identifier for the client.<br> If null, all messages are retrieved
//     * @return iterator of all the arrived MQTT messages
//     */
//    override fun getAllArrivedMessages(clientHandle: String): MutableIterator<MessageStore.StoredMessage> =
//        object : MutableIterator<MessageStore.StoredMessage> {
//            private var c: Cursor? = null
//            private var hasNext = false
//            private val selectionArgs = arrayOf(clientHandle)
//
//            init {
//                db = mqttDb?.writableDatabase
//                // anonymous initialiser to start a suitable query and position at the first row, if one exists
//                c = if (clientHandle.isEmpty()) {
//                    db?.query(
//                        ARRIVED_MESSAGE_TABLE_NAME,
//                        null,
//                        null,
//                        null,
//                        null,
//                        null,
//                        "mtimestamp ASC"
//                    )
//                } else {
//                    db?.query(
//                        ARRIVED_MESSAGE_TABLE_NAME,
//                        null,
//                        MqttServiceConstants.CLIENT_HANDLE + "=?",
//                        selectionArgs,
//                        null,
//                        null,
//                        "mtimestamp ASC"
//                    )
//                }
//                hasNext = c?.moveToFirst() ?: false
//            }
//
//            override fun hasNext(): Boolean {
//                if (!hasNext){
//                    c?.close()
//                }
//                return hasNext
//            }
//
//            override fun next(): MessageStore.StoredMessage {
//                val messageId = getStringValue(getColumnIndex(MqttServiceConstants.MESSAGE_ID))
//                val clientHandle = getStringValue(getColumnIndex(MqttServiceConstants.CLIENT_HANDLE))
//                val topic = getStringValue(getColumnIndex(MqttServiceConstants.DESTINATION_NAME))
//                val payload = getBlobValue(getColumnIndex(MqttServiceConstants.PAYLOAD))
//                val qos = getIntValue(getColumnIndex(MqttServiceConstants.QOS))
//                val retained = getStringValue(getColumnIndex(MqttServiceConstants.RETAINED), "false").toBoolean()
//                val dup = getStringValue(getColumnIndex(MqttServiceConstants.DUPLICATE), "false").toBoolean()
//
//                // build the result
//                val message = MqttMessageHack(payload)
//                message.qos = qos
//                message.isRetained = retained
//                message.setDup(dup)
//
//                // move on
//                hasNext = c?.moveToNext() ?: false
//                return DbStoredData(messageId, clientHandle, topic, message)
//            }
//
//            override fun remove() {
//                throw UnsupportedOperationException()
//            }
//
//
//            private fun getColumnIndex(columnName: String): Int = c?.getColumnIndex(columnName) ?: -1
//
//            private fun getStringValue(index: Int, defValue: String = ""): String = if (index >= 0) c?.getString(index) ?: defValue else defValue
//
//            private fun getIntValue(index: Int): Int = if (index >= 0) c?.getInt(index) ?: 0 else 0
//
//            private fun getBlobValue(index: Int): ByteArray = if (index >= 0) c?.getBlob(index) ?: ByteArray(0) else ByteArray(0)
//        }
//
//    /**
//     * Delete all messages (optionally for a specific client)
//     * @param clientHandle identifier for the client.<br> If null, all messages are deleted
//     */
//    override fun clearArrivedMessages(clientHandle: String) {
//        db = mqttDb?.writableDatabase
//        val selectionArgs = arrayOf(clientHandle)
//        val rows = if (clientHandle.isEmpty()) {
//            traceHandler?.traceDebug(TAG, "clearArrivedMessages: clearing the table")
//            db?.delete(ARRIVED_MESSAGE_TABLE_NAME, null, null) ?: 0
//        } else {
//            traceHandler?.traceDebug(TAG, "clearArrivedMessages: clearing the table of $clientHandle messages")
//            db?.delete(ARRIVED_MESSAGE_TABLE_NAME, MqttServiceConstants.CLIENT_HANDLE + "=?", selectionArgs) ?: 0
//        }
//        traceHandler?.traceDebug(TAG, "clearArrivedMessages: rows affected = $rows")
//    }
//
//    private inner class DbStoredData(
//        private val messageId: String,
//        private val clientHandle: String,
//        private val topic: String,
//        private val message: MqttMessage
//    ) : MessageStore.StoredMessage {
//        override fun getMessageId(): String = messageId
//
//        override fun getClientHandle(): String = clientHandle
//
//        override fun getTopic(): String = topic
//
//        override fun getMessage(): MqttMessage = message
//    }
//
//    private inner class MqttMessageHack(payload: ByteArray) : MqttMessage(payload) {
//        fun setDup(dup: Boolean) {
//            super.setDuplicate(dup)
//        }
//    }
//
//    override fun close() {
//        db?.close()
//    }
//}