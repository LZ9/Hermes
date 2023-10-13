package com.lodz.android.hermes.mqtt.base.db

import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * 数据库操作接口
 * @author zhouL
 * @date 2023/10/12
 */
interface MessageStore {

    /** 存储主键为[clientKey]，主题为[topic]的消息[message]数据 */
    suspend fun saveMessage(clientKey: String, topic: String, message: MqttMessage): String

    /** 删除主键为[clientKey]，编号为[messageId]的已经被应用消费的缓存消息数据 */
    suspend fun deleteArrivedMessage(clientKey: String, messageId: String): Boolean

    /** 获取主键为[clientKey]的本地缓存的所有消息数据 */
    suspend fun getAllMessages(clientKey: String): ArrayList<DbStoredData>

    /** 清空主键为[clientKey]的本地缓存的所有消息数据 */
    suspend fun clearAllMessages(clientKey: String)
}
