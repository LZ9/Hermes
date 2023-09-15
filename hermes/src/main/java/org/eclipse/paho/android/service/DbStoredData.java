package org.eclipse.paho.android.service;

import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * 数据库存储对象
 * @author zhouL
 * @date 2023/9/15
 */
public class DbStoredData {
    /** 消息ID */
    public String messageId;
    /** 客户端标识符 */
    public String clientHandle;
    /** 消息主题 */
    public String topic;
    /** 消息内容 */
    public MqttMessage message;

    public DbStoredData(String messageId, String clientHandle, String topic, MqttMessage message) {
        this.messageId = messageId;
        this.clientHandle = clientHandle;
        this.topic = topic;
        this.message = message;
    }
}
