package org.eclipse.paho.android.service.contract;

import org.eclipse.paho.android.service.event.MqttAction;

/**
 * 操作回调
 * @author zhouL
 * @date 2023/9/27
 */
public interface ConnectActionListener {

    void onSuccess(MqttAction action, String clientKey);

    void onFailure(MqttAction action, String clientKey, String errorMsg, Throwable t);
}
