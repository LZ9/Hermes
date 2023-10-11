package org.eclipse.paho.android.service.bean;

import android.content.Context;

import androidx.annotation.NonNull;

import org.eclipse.paho.android.service.event.Ack;
import org.eclipse.paho.android.service.MqttConnection;
import org.eclipse.paho.android.service.db.MessageStore;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

/**
 * MQTT客户端
 * @author zhouL
 * @date 2023/10/7
 */
public class MqttClient extends ClientInfoBean {

    /** 持久层接口 */
    @NonNull
    private final MqttClientPersistence persistence;
    /** 连接池 */
    private final MqttConnection connection;

    public MqttClient(Context context, @NonNull String clientKey, @NonNull String serverURI, @NonNull String clientId, @NonNull MqttConnectOptions connectOptions, @NonNull MqttClientPersistence persistence, @NonNull Ack ackType, MessageStore messageStore) {
        super(clientKey, serverURI, clientId, connectOptions, ackType);
        this.persistence = persistence;
        this.connection = new MqttConnection(context, this, persistence, messageStore);
    }

    @NonNull
    public MqttClientPersistence getPersistence() {
        return persistence;
    }

    public MqttConnection getConnection() {
        return connection;
    }
}
