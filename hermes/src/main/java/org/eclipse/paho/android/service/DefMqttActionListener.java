package org.eclipse.paho.android.service;

import android.os.Bundle;

import com.lodz.android.hermes.paho.android.service.Status;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;

/**
 * 默认实现MQTT消息监听接口
 * @author zhouL
 * @date 2023/9/20
 */
public class DefMqttActionListener implements IMqttActionListener {

    private final Bundle mBundle;

    private final MqttService mMqttService;

    private final String mClientKey;

    public DefMqttActionListener(String clientKey, Bundle resultBundle, MqttService service) {
        mClientKey = clientKey;
        mBundle = resultBundle;
        mMqttService = service;
    }

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {
        mMqttService.sendBroadcastToClient(mClientKey, Status.OK, mBundle);
    }

    @Override
    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        mBundle.putString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE, exception.getLocalizedMessage());
        mBundle.putSerializable(MqttServiceConstants.CALLBACK_EXCEPTION, exception);
        mMqttService.sendBroadcastToClient(mClientKey, Status.ERROR, mBundle);
    }
}