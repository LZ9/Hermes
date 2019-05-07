package com.lodz.android.hermes.modules;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

/**
 * 把runnable post到UI线程执行的工具类
 * Created by zhouL on 2016/11/17.
 */
public class UiHandler {

    private static Handler sHandler;

    /** 初始化 */
    private static void prepare() {
        synchronized(UiHandler.class){
            if (sHandler == null) {
                sHandler = new Handler(Looper.getMainLooper());
            }
        }
    }

    private UiHandler() {
    }

    /**
     * 在UI线程执行Runnable
     * @param r 线程体
     */
    public static void post(Runnable r) {
        prepare();
        if(Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            sHandler.post(r);
        }
    }

    /**
     * 在UI线程执行Runnable，并指定token
     * @param r 线程体
     * @param token 标志
     */
    public static void post(Runnable r, Object token) {
        prepare();
        if(Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            Message message = Message.obtain(sHandler, r);
            message.obj = token;
            sHandler.sendMessage(message);
        }
    }

    /**
     * 延迟执行
     * @param r 线程体
     * @param delay 延迟时间（毫秒）
     */
    public static void postDelayed(Runnable r, long delay) {
        prepare();
        sHandler.postDelayed(r, delay);
    }

    /**
     * 延迟执行，并指定token
     * @param r 线程体
     * @param token 标志
     * @param delay
     */
    public static void postDelayed(Runnable r, Object token, long delay) {
        prepare();
        Message message = Message.obtain(sHandler, r);
        message.obj = token;
        sHandler.sendMessageDelayed(message, delay);
    }

    /**
     * 移除指定token的Runnable（token传null则移除所有的Runnable）
     * @param token 标志
     */
    public static void remove(Object token) {
        prepare();
        sHandler.removeCallbacksAndMessages(token);
    }

    /** 销毁Handler */
    public static void destroy(){
        remove(null);
        sHandler = null;
    }

}
