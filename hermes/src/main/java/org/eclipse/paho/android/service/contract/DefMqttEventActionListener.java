package org.eclipse.paho.android.service.contract;

import org.eclipse.paho.android.service.event.MqttAction;
import org.eclipse.paho.android.service.event.MqttEvent;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.greenrobot.eventbus.EventBus;

/**
 * 默认实现MQTT消息监听接口
 * @author zhouL
 * @date 2023/10/9
 */
public class DefMqttEventActionListener implements IMqttActionListener {

    protected final String clientKey;

    protected final MqttAction action;

    protected final String errorMsg;

    public DefMqttEventActionListener(String clientKey, MqttAction action, String errorMsg) {
        this.clientKey = clientKey;
        this.action = action;
        this.errorMsg = errorMsg;
    }

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {
        EventBus.getDefault().post(MqttEvent.createSuccess(clientKey, action));
    }

    @Override
    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        exception.printStackTrace();
        EventBus.getDefault().post(MqttEvent.createFail(clientKey, action, errorMsg, exception));
    }
}
