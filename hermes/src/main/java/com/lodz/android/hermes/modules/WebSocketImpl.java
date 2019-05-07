package com.lodz.android.hermes.modules;

import android.content.Context;
import android.text.TextUtils;

import com.lodz.android.hermes.contract.Hermes;
import com.lodz.android.hermes.contract.OnConnectListener;
import com.lodz.android.hermes.contract.OnSendListener;
import com.lodz.android.hermes.contract.OnSubscribeListener;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;

import java.net.SocketException;
import java.util.List;

/**
 * WebSocket实现
 * @author zhouL
 * @date 2019/5/7
 */
public class WebSocketImpl implements Hermes {

    /** 日志标签 */
    public static String TAG = "WebSocketLog";

    /** WebSocket客户端 */
    private WsClient mWsClient;
    /** 订阅监听器 */
    private OnSubscribeListener mOnSubscribeListener;
    /** 连接监听器 */
    private OnConnectListener mOnConnectListener;
    /** 发送监听器 */
    private OnSendListener mOnSendListener;
    /** 路径 */
    private String mUrl = "";

    @Override
    public void init(Context context, String url, String clientId, MqttConnectOptions options) {
        mUrl = url;
        mWsClient = new WsClient(url);
        mWsClient.setOnWebSocketListener(new WsClient.OnWebSocketListener() {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                PrintLog.d(TAG, "WebSocket通道打开成功：" + handshakedata.getHttpStatusMessage());
                if (mOnConnectListener != null){
                    mOnConnectListener.onConnectComplete(false);
                }
            }

            @Override
            public void onMessage(String message) {
                PrintLog.i(TAG, "数据到达 : " + message);
                if (mOnSubscribeListener != null){
                    mOnSubscribeListener.onMsgArrived("", message);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                PrintLog.v(TAG, "WebSocket连接关闭 ->  code : " + code + " ; reason : " + reason + " ; remote : " + remote);
                if (code == CloseFrame.NORMAL){
                    reason = "连接断开";
                }
                if (code == CloseFrame.ABNORMAL_CLOSE){
                    reason = "长时间未接收或发送信息";
                    if (mOnConnectListener != null){
                        mOnConnectListener.onConnectionLost(new SocketException(reason));
                    }
                    return;
                }
                if (mOnConnectListener != null){
                    mOnConnectListener.onConnectFailure(new SocketException(reason));
                }
            }

            @Override
            public void onError(Exception e) {
                PrintLog.e(TAG, "WebSocket连接异常 : " + e.getMessage());
                if (mOnConnectListener != null){
                    mOnConnectListener.onConnectFailure(e);
                }
            }
        });
    }

    @Override
    public void setSubTopic(List<String> topics) {

    }

    @Override
    public void setOnSubscribeListener(OnSubscribeListener listener) {
        mOnSubscribeListener = listener;
    }

    @Override
    public void setOnConnectListener(OnConnectListener listener) {
        mOnConnectListener = listener;
    }

    @Override
    public void setOnSendListener(OnSendListener listener) {
        mOnSendListener = listener;
    }

    @Override
    public void sendTopic(String topic, String content) {
        if (mWsClient != null){
            try {
                PrintLog.i(TAG, topic + " --- 数据发送 : " + content);
                mWsClient.send(content);
                if (mOnSendListener != null){
                    mOnSendListener.onSendComplete(topic, content);
                }
            }catch (Exception e){
                e.printStackTrace();
                PrintLog.e(TAG, topic + " --- 数据发送失败 : " + e.getCause());
                if (mOnSendListener != null){
                    mOnSendListener.onSendFailure(topic, e);
                }
            }
        }
    }

    @Override
    public void connect() {
        if (TextUtils.isEmpty(mUrl)) {
            return;
        }
        if (mWsClient == null){
            init(null, mUrl, null, null);
        }
        if (mWsClient != null){
            mWsClient.connect();
        }
    }

    @Override
    public void disconnect() {
        if (mWsClient != null){
            mWsClient.close();
            mWsClient = null;
        }
    }

    @Override
    public boolean isConnected() {
        return mWsClient != null && mWsClient.isOpen();
    }

    @Override
    public void subscribeTopic() {

    }
}
