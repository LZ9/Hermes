package org.eclipse.paho.android.service.db;

import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * 数据库存储对象
 * @author zhouL
 * @date 2023/9/15
 */
public class DbStoredData {
    /** 消息ID */
    public String messageId;
    /** 客户端主键 */
    public String clientKey;
    /** 消息主题 */
    public String topic;
    /** 消息内容 */
    public MqttMessage message;

    public DbStoredData(String messageId, String clientKey, String topic, MqttMessage message) {
        this.messageId = messageId;
        this.clientKey = clientKey;
        this.topic = topic;
        this.message = message;
    }
}
