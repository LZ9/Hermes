package org.eclipse.paho.android.service.contract;

import org.eclipse.paho.android.service.event.MqttAction;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * 消息发布操作回调
 * @author zhouL
 * @date 2023/9/27
 */
public interface PublishActionListener {

    void onSuccess(MqttAction action, String clientKey, String topic, MqttMessage message);

    void onFailure(MqttAction action, String clientKey, String topic, String errorMsg, Throwable t);
}
