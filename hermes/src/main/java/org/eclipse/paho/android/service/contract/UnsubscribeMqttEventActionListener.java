package org.eclipse.paho.android.service.contract;

import org.eclipse.paho.android.service.event.MqttAction;
import org.eclipse.paho.android.service.event.MqttEvent;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.greenrobot.eventbus.EventBus;

/**
 * 默认实现MQTT消息监听接口
 * @author zhouL
 * @date 2023/10/9
 */
public class UnsubscribeMqttEventActionListener extends DefMqttEventActionListener {
    private String[] topics;


    public UnsubscribeMqttEventActionListener(String clientKey, String errorMsg, String[] topics) {
        super(clientKey, MqttAction.ACTION_UNSUBSCRIBE, errorMsg);
        this.topics = topics;
    }

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {
        EventBus.getDefault().post(MqttEvent.createUnsubscribeSuccess(clientKey, topics));
    }

    @Override
    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        exception.printStackTrace();
        EventBus.getDefault().post(MqttEvent.createUnsubscribeFail(clientKey, topics, errorMsg, exception));
    }
}
