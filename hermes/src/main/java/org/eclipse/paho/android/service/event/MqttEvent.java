package org.eclipse.paho.android.service.event;

import org.eclipse.paho.android.service.db.DbStoredData;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * 连接事件
 * @author zhouL
 * @date 2023/9/21
 */
public class MqttEvent {

    /** 成功 */
    public static final int RESULT_SUCCESS = 0;
    /** 失败 */
    public static final int RESULT_FAIL = 1;

    /** 客户端主键 */
    public String clientKey = "";

    /** 执行结果 */
    public int result = RESULT_FAIL;

    /** 执行内容 */
    public final MqttAction action;

    /** 异常提示语 */
    public String errorMsg = "";

    /** 异常 */
    public Throwable exception = null;

    /** 消息数据 */
    public DbStoredData data = null;

    /** 是否重连 */
    public boolean isReconnect;

    /** 服务端地址 */
    public String serverURI;

    /** 发布消息的送达票据 */
    public IMqttDeliveryToken token;

    /** 主题 */
    public String topic;

    /** 主题 */
    public String[] topics;

    /** 消息数据 */
    public MqttMessage message;

    public MqttEvent(String clientKey, MqttAction action) {
        this.clientKey = clientKey;
        this.action = action;
    }

    /**
     * 创建失败事件
     */
    public static MqttEvent createFail(String clientKey, MqttAction action, String errorMsg, Throwable exception) {
        MqttEvent event = new MqttEvent(clientKey, action);
        event.result = RESULT_FAIL;
        event.errorMsg = errorMsg;
        event.exception = exception;
        return event;
    }

    /**
     * 创建成功事件
     */
    public static MqttEvent createSuccess(String clientKey, MqttAction action) {
        MqttEvent event = new MqttEvent(clientKey, action);
        event.result = RESULT_SUCCESS;
        return event;
    }

    /**
     * 创建消息到达事件
     */
    public static MqttEvent createMsgArrived(String clientKey, DbStoredData data) {
        MqttEvent event = new MqttEvent(clientKey, MqttAction.ACTION_MSG_ARRIVED);
        event.result = RESULT_SUCCESS;
        event.data = data;
        return event;
    }

    /**
     * 创建连接丢失事件
     */
    public static MqttEvent createConnectionLost(String clientKey, Throwable exception) {
        MqttEvent event = new MqttEvent(clientKey, MqttAction.ACTION_CONNECTION_LOST);
        event.result = RESULT_SUCCESS;
        event.exception = exception;
        return event;
    }

    /**
     * 创建连接完成事件
     */
    public static MqttEvent createConnectComplete(String clientKey, boolean isReconnect, String serverURI) {
        MqttEvent event = new MqttEvent(clientKey, MqttAction.ACTION_CONNECT_COMPLETE);
        event.result = RESULT_SUCCESS;
        event.isReconnect = isReconnect;
        event.serverURI = serverURI;
        return event;
    }

    /**
     * 创建发送的消息已到达事件
     */
    public static MqttEvent createDeliveryComplete(String clientKey, IMqttDeliveryToken token) {
        MqttEvent event = new MqttEvent(clientKey, MqttAction.ACTION_DELIVERY_COMPLETE);
        event.result = RESULT_SUCCESS;
        event.token = token;
        return event;
    }

    /**
     * 创建消息发布成功事件
     */
    public static MqttEvent createPublishSuccess(String clientKey, String topic, MqttMessage message) {
        MqttEvent event = new MqttEvent(clientKey, MqttAction.ACTION_PUBLISH_MSG);
        event.result = RESULT_SUCCESS;
        event.topic = topic;
        event.message = message;
        return event;
    }

    /**
     * 创建消息发布失败事件
     */
    public static MqttEvent createPublishFail(String clientKey, String topic, String errorMsg, Throwable exception) {
        MqttEvent event = new MqttEvent(clientKey, MqttAction.ACTION_PUBLISH_MSG);
        event.result = RESULT_FAIL;
        event.errorMsg = errorMsg;
        event.exception = exception;
        event.topic = topic;
        return event;
    }


    /**
     * 创建订阅成功事件
     */
    public static MqttEvent createSubscribeSuccess(String clientKey, String[] topics) {
        MqttEvent event = new MqttEvent(clientKey, MqttAction.ACTION_SUBSCRIBE);
        event.result = RESULT_SUCCESS;
        event.topics = topics;
        return event;
    }

    /**
     * 创建订阅失败事件
     */
    public static MqttEvent createSubscribeFail(String clientKey, String[] topics, String errorMsg, Throwable exception) {
        MqttEvent event = new MqttEvent(clientKey, MqttAction.ACTION_SUBSCRIBE);
        event.result = RESULT_FAIL;
        event.errorMsg = errorMsg;
        event.exception = exception;
        event.topics = topics;
        return event;
    }


    /**
     * 创建解订阅成功事件
     */
    public static MqttEvent createUnsubscribeSuccess(String clientKey, String[] topics) {
        MqttEvent event = new MqttEvent(clientKey, MqttAction.ACTION_UNSUBSCRIBE);
        event.result = RESULT_SUCCESS;
        event.topics = topics;
        return event;
    }

    /**
     * 创建解订阅失败事件
     */
    public static MqttEvent createUnsubscribeFail(String clientKey, String[] topics, String errorMsg, Throwable exception) {
        MqttEvent event = new MqttEvent(clientKey, MqttAction.ACTION_UNSUBSCRIBE);
        event.result = RESULT_FAIL;
        event.errorMsg = errorMsg;
        event.exception = exception;
        event.topics = topics;
        return event;
    }

    /**
     * 创建连接成功事件
     */
    public static MqttEvent createConnectSuccess(String clientKey) {
        MqttEvent event = new MqttEvent(clientKey, MqttAction.ACTION_CONNECT);
        event.result = RESULT_SUCCESS;
        return event;
    }

    /**
     * 创建连接失败事件
     */
    public static MqttEvent createConnectFail(String clientKey, String errorMsg, Throwable exception) {
        MqttEvent event = new MqttEvent(clientKey, MqttAction.ACTION_CONNECT);
        event.result = RESULT_FAIL;
        event.errorMsg = errorMsg;
        event.exception = exception;
        return event;
    }

    /**
     * 创建失败事件
     */
    public static MqttEvent createDisconnectFail(String clientKey, String errorMsg, Throwable exception) {
        MqttEvent event = new MqttEvent(clientKey, MqttAction.ACTION_DISCONNECT);
        event.result = RESULT_FAIL;
        event.errorMsg = errorMsg;
        event.exception = exception;
        return event;
    }

    /**
     * 创建成功事件
     */
    public static MqttEvent createDisconnectSuccess(String clientKey) {
        MqttEvent event = new MqttEvent(clientKey, MqttAction.ACTION_DISCONNECT);
        event.result = RESULT_SUCCESS;
        return event;
    }

}
