package org.eclipse.paho.android.service.contract;

import org.eclipse.paho.android.service.event.MqttAction;
import org.eclipse.paho.android.service.event.MqttEvent;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.greenrobot.eventbus.EventBus;

/**
 * 默认实现MQTT消息监听接口
 * @author zhouL
 * @date 2023/10/9
 */
public class PublishMqttEventActionListener extends DefMqttEventActionListener {
    private String topic;
    private MqttMessage message;


    public PublishMqttEventActionListener(String clientKey, String errorMsg, String topic, MqttMessage message) {
        super(clientKey, MqttAction.ACTION_PUBLISH_MSG, errorMsg);
        this.topic = topic;
        this.message = message;
    }

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {
        EventBus.getDefault().post(MqttEvent.createPublishSuccess(clientKey, topic, message));
    }

    @Override
    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        exception.printStackTrace();
        EventBus.getDefault().post(MqttEvent.createPublishFail(clientKey, topic, errorMsg, exception));
    }
}
