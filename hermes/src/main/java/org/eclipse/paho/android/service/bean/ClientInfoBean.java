package org.eclipse.paho.android.service.bean;

import androidx.annotation.NonNull;

import org.eclipse.paho.android.service.event.Ack;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

/**
 * 客户端信息
 *
 * @author zhouL
 * @date 2023/9/27
 */
public class ClientInfoBean {

    /**
     * 客户端主键
     */
    @NonNull
    private final String clientKey;
    /**
     * 服务端路径
     */
    @NonNull
    private final String serverURI;
    /**
     * 客户端ID
     */
    @NonNull
    private final String clientId;
    /**
     * 接受消息的确认模式
     */
    @NonNull
    private final Ack ackType;
    /**
     * 连接配置
     */
    @NonNull
    private final MqttConnectOptions connectOptions;


    public ClientInfoBean(@NonNull String clientKey, @NonNull String serverURI, @NonNull String clientId,
                          @NonNull MqttConnectOptions connectOptions, @NonNull Ack ackType) {
        this.clientKey = clientKey;
        this.serverURI = serverURI;
        this.clientId = clientId;
        this.ackType = ackType;
        this.connectOptions = connectOptions;
    }

    @NonNull
    public String getClientKey() {
        return clientKey;
    }

    @NonNull
    public String getServerURI() {
        return serverURI;
    }

    @NonNull
    public String getClientId() {
        return clientId;
    }

    @NonNull
    public Ack getAckType() {
        return ackType;
    }

    @NonNull
    public MqttConnectOptions getConnectOptions() {
        return connectOptions;
    }

}
