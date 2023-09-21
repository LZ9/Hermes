package org.eclipse.paho.android.service;

/**
 * 接受消息的确认模式
 * @author zhouL
 * @date 2023/9/21
 */
public enum Ack {
    /**
     * 自动确认消息被接受
     */
    AUTO_ACK,
    /**
     * 需要手动调用MqttAndroidClient的acknowledgeMessage()方法来确认消息被接受
     */
    MANUAL_ACK
}