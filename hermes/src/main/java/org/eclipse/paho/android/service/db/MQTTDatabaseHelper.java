package org.eclipse.paho.android.service.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * 数据库帮助类
 * @author zhouL
 * @date 2023/9/15
 */
public class MQTTDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "MQTTDatabaseHelper";

    private static final String DATABASE_NAME = "mqttAndroidService.db";

    /** 数据库消息表表名 */
    public static final String DB_ARRIVED_MESSAGE_TABLE_NAME = "MqttArrivedMessageTable";

    /** 数据库字段-是否重复 */
    public static final String DB_COLUMN_DUPLICATE = "duplicate";

    /** 数据库字段-是否服务器保留 */
    public static final String DB_COLUMN_RETAINED = "retained";
    /** 数据库字段-消息的服务质量 */
    public static final String DB_COLUMN_QOS = "qos";
    /** 数据库字段-数据内容 */
    public static final String DB_COLUMN_PAYLOAD = "payload";
    /** 数据库字段-主题名称 */
    public static final String DB_COLUMN_DESTINATION_NAME = "destinationName";
    /** 数据库字段-客户端标识符 */
    public static final String DB_COLUMN_CLIENT_HANDLE = "clientHandle";
    /** 数据库字段-消息ID */
    public static final String DB_COLUMN_MESSAGE_ID = "messageId";
    /** 数据库字段-消息到达时间 */
    public static final String DB_COLUMN_MTIMESTAMP = "mtimestamp";


    /** 数据库版本号 */
    private static final int DATABASE_VERSION = 1;

    public MQTTDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        String createArrivedTableStatement = "CREATE TABLE "
                + MQTTDatabaseHelper.DB_ARRIVED_MESSAGE_TABLE_NAME + "("
                + MQTTDatabaseHelper.DB_COLUMN_MESSAGE_ID + " TEXT PRIMARY KEY, "
                + MQTTDatabaseHelper.DB_COLUMN_CLIENT_HANDLE + " TEXT, "
                + MQTTDatabaseHelper.DB_COLUMN_DESTINATION_NAME + " TEXT, "
                + MQTTDatabaseHelper.DB_COLUMN_PAYLOAD + " BLOB, "
                + MQTTDatabaseHelper.DB_COLUMN_QOS + " INTEGER, "
                + MQTTDatabaseHelper.DB_COLUMN_RETAINED + " TEXT, "
                + MQTTDatabaseHelper.DB_COLUMN_DUPLICATE + " TEXT, "
                + MQTTDatabaseHelper.DB_COLUMN_MTIMESTAMP + " INTEGER" + ");";
        Log.d(TAG, "create table : {" + createArrivedTableStatement + "}");
        database.execSQL(createArrivedTableStatement);
        Log.d(TAG, "{" + MQTTDatabaseHelper.DB_ARRIVED_MESSAGE_TABLE_NAME + "} create success");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "onUpgrade drop table : {" + MQTTDatabaseHelper.DB_ARRIVED_MESSAGE_TABLE_NAME + "}");
        db.execSQL("DROP TABLE IF EXISTS " + MQTTDatabaseHelper.DB_ARRIVED_MESSAGE_TABLE_NAME);
        onCreate(db);
        Log.d(TAG, "onUpgrade complete");
    }
}