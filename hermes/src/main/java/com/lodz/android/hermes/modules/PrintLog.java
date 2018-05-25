package com.lodz.android.hermes.modules;

import android.text.TextUtils;
import android.util.Log;

/**
 * 日志打印
 * Created by zhouL on 2016/11/17.
 */
class PrintLog {

    private static final int LOG_I = 0;
    private static final int LOG_V = 1;
    private static final int LOG_D = 2;
    private static final int LOG_W = 3;
    private static final int LOG_E = 4;

    /** 是否打印日志 */
    private static boolean sIsPrint = false;

    public static void setPrint(boolean isPrint) {
        sIsPrint = isPrint;
    }

    public static void i(String tag, String msg) {
        if (sIsPrint) {
            if (TextUtils.isEmpty(msg)) {
                Log.i(tag, "NULL");
            } else {
                Log.i(tag, msg);
            }
        }
    }

    public static void is(String tag, String msg) {
        if (sIsPrint) {
            if (TextUtils.isEmpty(msg)) {
                Log.i(tag, "NULL");
            } else {
                logSegmented(LOG_I, tag, msg);
            }
        }
    }

    public static void v(String tag, String msg) {
        if (sIsPrint) {
            if (TextUtils.isEmpty(msg)) {
                Log.v(tag, "NULL");
            } else {
                Log.v(tag, msg);
            }
        }
    }

    public static void vs(String tag, String msg) {
        if (sIsPrint) {
            if (TextUtils.isEmpty(msg)) {
                Log.v(tag, "NULL");
            } else {
                logSegmented(LOG_V, tag, msg);
            }
        }
    }

    public static void d(String tag, String msg) {
        if (sIsPrint) {
            if (TextUtils.isEmpty(msg)) {
                Log.d(tag, "NULL");
            } else {
                Log.d(tag, msg);
            }
        }
    }

    public static void ds(String tag, String msg) {
        if (sIsPrint) {
            if (TextUtils.isEmpty(msg)) {
                Log.d(tag, "NULL");
            } else {
                logSegmented(LOG_D, tag, msg);
            }
        }
    }

    public static void w(String tag, String msg) {
        if (sIsPrint) {
            if (TextUtils.isEmpty(msg)) {
                Log.w(tag, "NULL");
            } else {
                Log.w(tag, msg);
            }
        }
    }

    public static void ws(String tag, String msg) {
        if (sIsPrint) {
            if (TextUtils.isEmpty(msg)) {
                Log.w(tag, "NULL");
            } else {
                logSegmented(LOG_W, tag, msg);
            }
        }
    }

    public static void e(String tag, String msg) {
        if (sIsPrint) {
            if (TextUtils.isEmpty(msg)) {
                Log.e(tag, "NULL");
            } else {
                Log.e(tag, msg);
            }
        }
    }

    public static void es(String tag, String msg) {
        if (sIsPrint) {
            if (TextUtils.isEmpty(msg)) {
                Log.e(tag, "NULL");
            } else {
                logSegmented(LOG_E, tag, msg);
            }
        }
    }

    public static void e(String tag, String msg, Throwable t) {
        if (sIsPrint) {
            if (TextUtils.isEmpty(msg)) {
                Log.e(tag, "NULL");
            } else {
                Log.e(tag, msg, t);
            }
        }
    }

    private static void logSegmented(int type, String tag, String log) {
        if (TextUtils.isEmpty(log) || log.length() < 3000){
            logByType(type, tag, log);
            return;
        }
        int index = (int) Math.ceil(log.length() / 3000.0);
        for (int i = 0; i < index; i++){
            int start = i * 3000;
            int end = 3000 + i * 3000;
            if (end >= log.length()){
                end = log.length();
            }
            logByType(type, tag, log.substring(start, end));
            if (end == log.length()){
                return;
            }
        }
    }

    private static void logByType(int type, String tag, String log) {
        switch (type){
            case LOG_I:
                Log.i(tag, log);
                break;
            case LOG_V:
                Log.v(tag, log);
                break;
            case LOG_D:
                Log.d(tag, log);
                break;
            case LOG_W:
                Log.w(tag, log);
                break;
            case LOG_E:
                Log.e(tag, log);
                break;
            default:
                Log.i(tag, log);
                break;
        }
    }

}
