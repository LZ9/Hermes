package org.eclipse.paho.android.service.contract;

import org.eclipse.paho.android.service.event.MqttAction;

/**
 * 订阅/解订阅相关操作回调
 * @author zhouL
 * @date 2023/9/27
 */
public interface SubscribeActionListener {

    void onSuccess(MqttAction action, String clientKey, String[] topic);

    void onFailure(MqttAction action, String clientKey, String[] topic, String errorMsg, Throwable t);
}
