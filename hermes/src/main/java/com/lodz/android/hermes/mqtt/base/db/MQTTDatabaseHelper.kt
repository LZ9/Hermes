package com.lodz.android.hermes.mqtt.base.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.lodz.android.hermes.modules.HermesLog

/**
 * 数据库帮助类
 * @author zhouL
 * @date 2023/10/12
 */
class MQTTDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val TAG = "MqttDatabase"

        /** 数据库名称 */
        private const val DATABASE_NAME = "mqttAndroidService.db"

        /** 数据库版本号 */
        private const val DATABASE_VERSION = 1

        /** 数据库消息表表名 */
        const val DB_ARRIVED_MESSAGE_TABLE_NAME = "MqttArrivedMessageTable"

        /** 数据库字段-是否重复 */
        const val DB_COLUMN_DUPLICATE = "duplicate"

        /** 数据库字段-是否服务器保留 */
        const val DB_COLUMN_RETAINED = "retained"

        /** 数据库字段-消息的服务质量 */
        const val DB_COLUMN_QOS = "qos";

        /** 数据库字段-数据内容 */
        const val DB_COLUMN_PAYLOAD = "payload";

        /** 数据库字段-主题名称 */
        const val DB_COLUMN_DESTINATION_NAME = "destinationName";

        /** 数据库字段-客户端标识符 */
        const val DB_COLUMN_CLIENT_HANDLE = "clientHandle";

        /** 数据库字段-消息ID */
        const val DB_COLUMN_MESSAGE_ID = "messageId";

        /** 数据库字段-消息到达时间 */
        const val DB_COLUMN_MTIMESTAMP = "mtimestamp";
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createArrivedTableStatement = ("CREATE TABLE $DB_ARRIVED_MESSAGE_TABLE_NAME("
                + "$DB_COLUMN_MESSAGE_ID TEXT PRIMARY KEY, "
                + "$DB_COLUMN_CLIENT_HANDLE TEXT, "
                + "$DB_COLUMN_DESTINATION_NAME TEXT, "
                + "$DB_COLUMN_PAYLOAD BLOB, "
                + "$DB_COLUMN_QOS INTEGER, "
                + "$DB_COLUMN_RETAINED TEXT, "
                + "$DB_COLUMN_DUPLICATE TEXT, "
                + "$DB_COLUMN_MTIMESTAMP INTEGER);")
        HermesLog.d(TAG, "create table by sql : $createArrivedTableStatement")
        db?.execSQL(createArrivedTableStatement)
        HermesLog.d(TAG, "$DB_ARRIVED_MESSAGE_TABLE_NAME create success")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        HermesLog.i(TAG, "onUpgrade drop table $DB_ARRIVED_MESSAGE_TABLE_NAME");
        db?.execSQL("DROP TABLE IF EXISTS $DB_ARRIVED_MESSAGE_TABLE_NAME")
        onCreate(db)
        HermesLog.i(TAG, "onUpgrade complete")
    }
}