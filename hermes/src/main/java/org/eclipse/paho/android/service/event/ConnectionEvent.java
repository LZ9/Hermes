package org.eclipse.paho.android.service.event;

/**
 * 连接事件
 * @author zhouL
 * @date 2023/9/21
 */
public class ConnectionEvent {

    /**
     * 异常
     */
    public static final int ACTION_ERROR = 0;
    /**
     * 连接
     */
    public static final int ACTION_CONNECT = 1;

    /**
     * 票据
     */
    public String token = "";
    /**
     * 动作
     */
    public int action = ACTION_ERROR;

    /**
     * 异常提示语
     */
    public String errorMsg = "";

    /**
     * 异常
     */
    public Exception exception = null;

    /**
     * 创建异常的连接事件
     */
    public static ConnectionEvent createError(String token, String errorMsg, Exception exception) {
        ConnectionEvent event = new ConnectionEvent();
        event.token = token;
        event.action = ACTION_ERROR;
        event.errorMsg = errorMsg;
        event.exception = exception;
        return event;
    }
}
