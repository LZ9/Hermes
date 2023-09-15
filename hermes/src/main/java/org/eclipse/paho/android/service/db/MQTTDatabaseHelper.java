package org.eclipse.paho.android.service.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.eclipse.paho.android.service.MqttServiceConstants;

/**
 * 数据库帮助类
 * @author zhouL
 * @date 2023/9/15
 */
public class MQTTDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "MQTTDatabaseHelper";

    private static final String DATABASE_NAME = "mqttAndroidService.db";

    /** 数据库版本号 */
    private static final int DATABASE_VERSION = 1;

    public MQTTDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        String createArrivedTableStatement = "CREATE TABLE "
                + MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME + "("
                + MqttServiceConstants.DB_COLUMN_MESSAGE_ID + " TEXT PRIMARY KEY, "
                + MqttServiceConstants.DB_COLUMN_CLIENT_HANDLE + " TEXT, "
                + MqttServiceConstants.DB_COLUMN_DESTINATION_NAME + " TEXT, "
                + MqttServiceConstants.DB_COLUMN_PAYLOAD + " BLOB, "
                + MqttServiceConstants.DB_COLUMN_QOS + " INTEGER, "
                + MqttServiceConstants.DB_COLUMN_RETAINED + " TEXT, "
                + MqttServiceConstants.DB_COLUMN_DUPLICATE + " TEXT, "
                + MqttServiceConstants.DB_COLUMN_MTIMESTAMP + " INTEGER" + ");";
        Log.d(TAG, "create table : {" + createArrivedTableStatement + "}");
        database.execSQL(createArrivedTableStatement);
        Log.d(TAG, "{" + MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME + "} create success");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "onUpgrade drop table : {" + MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME + "}");
        db.execSQL("DROP TABLE IF EXISTS " + MqttServiceConstants.DB_ARRIVED_MESSAGE_TABLE_NAME);
        onCreate(db);
        Log.d(TAG, "onUpgrade complete");
    }
}