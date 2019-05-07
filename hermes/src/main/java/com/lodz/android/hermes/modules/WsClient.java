package com.lodz.android.hermes.modules;

import android.os.Looper;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket客户端
 * @author zhouL
 * @date 2019/5/7
 */
public class WsClient extends WebSocketClient {

    private OnWebSocketListener mListener;

    public WsClient(String url) {
        super(URI.create(url));
    }

    public WsClient(URI serverUri, Draft protocolDraft) {
        super(serverUri, protocolDraft);
    }

    public WsClient(URI serverUri, Map<String, String> httpHeaders) {
        super(serverUri, httpHeaders);
    }

    public WsClient(URI serverUri, Draft protocolDraft, Map<String, String> httpHeaders) {
        super(serverUri, protocolDraft, httpHeaders);
    }

    public WsClient(URI serverUri, Draft protocolDraft, Map<String, String> httpHeaders, int connectTimeout) {
        super(serverUri, protocolDraft, httpHeaders, connectTimeout);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        doOpen(handshakedata);
    }

    @Override
    public void onMessage(String message) {
        doMessage(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        doClose(code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        doError(ex);
    }

    @Override
    public void connect() {
        try {
            super.connect();
        }catch (Exception e){
            e.printStackTrace();
            doError(e);
        }
    }

    @Override
    public void reconnect() {
        Thread thread = new Thread(this);
        thread.start();
        super.reconnect();
    }

    /** 当前是否在主线程（UI线程） */
    private boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    private void doOpen(final ServerHandshake handshakedata) {
        UiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) {
                    mListener.onOpen(handshakedata);
                }
            }
        });
    }

    private void doMessage(final String message) {
        UiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) {
                    mListener.onMessage(message);
                }
            }
        });
    }

    private void doClose(final int code, final String reason, final boolean remote) {
        UiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) {
                    mListener.onClose(code, reason, remote);
                }
            }
        });
    }

    private void doError(final Exception ex) {
        UiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) {
                    mListener.onError(ex);
                }
            }
        });
    }

    /**
     * 设置监听器
     * @param listener 监听器
     */
    public void setOnWebSocketListener(OnWebSocketListener listener){
        mListener = listener;
    }

    public interface OnWebSocketListener {

        void onOpen(ServerHandshake handshakedata);

        void onMessage(String message);

        void onClose(int code, String reason, boolean remote);

        void onError(Exception e);
    }
}
