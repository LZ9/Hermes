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
import android.util.Log;

import org.eclipse.paho.android.service.MqttServiceConstants;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Iterator;

/**
 * Implementation of the {@link MessageStore} interface, using a SQLite database
 */
public class DatabaseMessageStore implements MessageStore {

    // TAG used for indentify trace data etc.
    private static final String TAG = "DatabaseMessageStore";

    // the database
    private SQLiteDatabase db = null;

    // a SQLiteOpenHelper specific for this database
    private MQTTDatabaseHelper mqttDb = null;



    /**
     * Constructor - create a DatabaseMessageStore to store arrived MQTT message
     *
     * @param context a context to use for android calls
     */
    public DatabaseMessageStore(Context context) {

        // Open message database
        mqttDb = new MQTTDatabaseHelper(context);

        // Android documentation suggests that this perhaps
        // could/should be done in another thread, but as the
        // database is only one table, I doubt it matters...

        Log.d(TAG, "DatabaseMessageStore<init> complete");
    }

    /**
     * Store an MQTT message
     *
     * @param clientHandle identifier for the client storing the message
     * @param topic        The topic on which the message was published
     * @param message      the arrived MQTT message
     * @return an identifier for the message, so that it can be removed when appropriate
     */
    @Override
    public String storeArrived(String clientHandle, String topic, MqttMessage message) {

        db = mqttDb.getWritableDatabase();

        Log.d(TAG, "storeArrived{" + clientHandle + "}, {" + message.toString() + "}");

        byte[] payload = message.getPayload();
        int qos = message.getQos();
        boolean retained = message.isRetained();
        boolean duplicate = message.isDuplicate();

        ContentValues values = new ContentValues();
        String id = java.util.UUID.randomUUID().toString();
        values.put(MqttServiceConstants.DB_COLUMN_MESSAGE_ID, id);
        values.put(MqttServiceConstants.DB_COLUMN_CLIENT_HANDLE, clientHandle);
        values.put(MqttServiceConstants.DB_COLUMN_DESTINATION_NAME, topic);
        values.put(MqttServiceConstants.DB_COLUMN_PAYLOAD, payload);
        values.put(MqttServiceConstants.DB_COLUMN_QOS, qos);
        values.put(MqttServiceConstants.DB_COLUMN_RETAINED, retained);
        values.put(MqttServiceConstants.DB_COLUMN_DUPLICATE, duplicate);
        values.put(MqttServiceConstants.DB_COLUMN_MTIMESTAMP, System.currentTimeMillis());
        try {
            db.insertOrThrow(MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME, null, values);
        } catch (SQLException e) {
            Log.e(TAG, "onUpgrade", e);
            throw e;
        }
        int count = getArrivedRowCount(clientHandle);
        Log.d(TAG, "storeArrived: inserted message with id of {" + id + "} - Number of messages in database for this clientHandle = " + count);
        return id;
    }

    private int getArrivedRowCount(String clientHandle) {
        int count = 0;
        String[] projection = { MqttServiceConstants.DB_COLUMN_MESSAGE_ID, };
        String selection = MqttServiceConstants.DB_COLUMN_CLIENT_HANDLE + "=?";
        String[] selectionArgs = new String[1];
        selectionArgs[0] = clientHandle;
        Cursor c = db.query(
                MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME, // Table Name
                projection, // The columns to return;
                selection, // Columns for WHERE Clause
                selectionArgs, // The values for the WHERE Cause
                null,  //Don't group the rows
                null,  // Don't filter by row groups
                null   // The sort order
        );

        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    /**
     * Delete an MQTT message.
     *
     * @param clientHandle identifier for the client which stored the message
     * @param id           the identifying string returned when the message was stored
     * @return true if the message was found and deleted
     */
    @Override
    public boolean discardArrived(String clientHandle, String id) {

        db = mqttDb.getWritableDatabase();

        Log.d(TAG, "discardArrived{" + clientHandle + "}, {" + id + "}");
        int rows;
        String[] selectionArgs = new String[2];
        selectionArgs[0] = id;
        selectionArgs[1] = clientHandle;

        try {
            rows = db.delete(MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME, MqttServiceConstants.DB_COLUMN_MESSAGE_ID + "=? AND " + MqttServiceConstants.DB_COLUMN_CLIENT_HANDLE + "=?", selectionArgs);
        } catch (SQLException e) {
            Log.e(TAG, "discardArrived", e);
            throw e;
        }
        if (rows != 1) {
            Log.e(TAG, "discardArrived - Error deleting message {" + id + "} from database: Rows affected = " + rows);
            return false;
        }
        int count = getArrivedRowCount(clientHandle);
        Log.d(TAG, "discardArrived - Message deleted successfully. - messages in db for this clientHandle " + count);
        return true;
    }

    /**
     * Get an iterator over all messages stored (optionally for a specific client)
     *
     * @param clientHandle identifier for the client.<br>
     *                     If null, all messages are retrieved
     * @return iterator of all the arrived MQTT messages
     */
    @Override
    public Iterator<DbStoredData> getAllArrivedMessages(final String clientHandle) {
        return new Iterator<DbStoredData>() {
            private Cursor c;
            private boolean hasNext;
            private final String[] selectionArgs = { clientHandle,  };


            {
                db = mqttDb.getWritableDatabase();
                // anonymous initialiser to start a suitable query
                // and position at the first row, if one exists
                if (clientHandle == null) {
                    c = db.query(MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "mtimestamp ASC");
                } else {
                    c = db.query(MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME,
                            null,
                            MqttServiceConstants.DB_COLUMN_CLIENT_HANDLE + "=?",
                            selectionArgs,
                            null,
                            null,
                            "mtimestamp ASC");
                }
                hasNext = c.moveToFirst();
            }

            @Override
            public boolean hasNext() {
                if (!hasNext) {
                    c.close();
                }
                return hasNext;
            }

            @Override
            public DbStoredData next() {
                String messageId = c.getString(c.getColumnIndex(MqttServiceConstants.DB_COLUMN_MESSAGE_ID));
                String clientHandle = c.getString(c.getColumnIndex(MqttServiceConstants.DB_COLUMN_CLIENT_HANDLE));
                String topic = c.getString(c.getColumnIndex(MqttServiceConstants.DB_COLUMN_DESTINATION_NAME));
                byte[] payload = c.getBlob(c.getColumnIndex(MqttServiceConstants.DB_COLUMN_PAYLOAD));
                int qos = c.getInt(c.getColumnIndex(MqttServiceConstants.DB_COLUMN_QOS));
                boolean retained = Boolean.parseBoolean(c.getString(c.getColumnIndex(MqttServiceConstants.DB_COLUMN_RETAINED)));
                boolean dup = Boolean.parseBoolean(c.getString(c.getColumnIndex(MqttServiceConstants.DB_COLUMN_DUPLICATE)));

                // build the result
                MqttMessageHack message = new MqttMessageHack(payload);
                message.setQos(qos);
                message.setRetained(retained);
                message.setDuplicateHack(dup);

                // move on
                hasNext = c.moveToNext();
                return new DbStoredData(messageId, clientHandle, topic, message);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            /* (non-Javadoc)
             * @see java.lang.Object#finalize()
             */
            @Override
            protected void finalize() throws Throwable {
                c.close();
                super.finalize();
            }

        };
    }

    /**
     * Delete all messages (optionally for a specific client)
     *
     * @param clientHandle identifier for the client.<br>
     *                     If null, all messages are deleted
     */
    @Override
    public void clearArrivedMessages(String clientHandle) {

        db = mqttDb.getWritableDatabase();
        String[] selectionArgs = new String[1];
        selectionArgs[0] = clientHandle;

        int rows = 0;
        if (clientHandle == null) {
            Log.d(TAG, "clearArrivedMessages: clearing the table");
            rows = db.delete(MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME, null, null);
        } else {
            Log.d(TAG, "clearArrivedMessages: clearing the table of " + clientHandle + " messages");
            rows = db.delete(MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME, MqttServiceConstants.DB_COLUMN_CLIENT_HANDLE + "=?", selectionArgs);
        }
        Log.d(TAG, "clearArrivedMessages: rows affected = " + rows);
    }

    @Override
    public void close() {
        if (this.db != null)
            this.db.close();

    }

}