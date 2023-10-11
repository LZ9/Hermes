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
public class DisconnectMqttEventActionListener extends DefMqttEventActionListener {

    public DisconnectMqttEventActionListener(String clientKey, String errorMsg) {
        super(clientKey, MqttAction.ACTION_DISCONNECT, errorMsg);
    }

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {
        EventBus.getDefault().post(MqttEvent.createDisconnectSuccess(clientKey));
    }

    @Override
    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        exception.printStackTrace();
        EventBus.getDefault().post(MqttEvent.createDisconnectFail(clientKey, errorMsg, exception));
    }
}
