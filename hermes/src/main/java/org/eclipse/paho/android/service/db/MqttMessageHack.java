package org.eclipse.paho.android.service.db;

import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * MqttMessage的继承类，开放setDuplicate方法
 * @author zhouL
 * @date 2023/9/15
 */
public class MqttMessageHack extends MqttMessage {

    public MqttMessageHack(byte[] payload) {
        super(payload);
    }

    public void setDuplicateHack(boolean dup) {
        super.setDuplicate(dup);
    }
}
