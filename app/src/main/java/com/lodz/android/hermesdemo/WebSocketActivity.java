package com.lodz.android.hermesdemo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.lodz.android.component.base.activity.BaseActivity;
import com.lodz.android.component.widget.base.TitleBarLayout;
import com.lodz.android.core.utils.DateUtils;
import com.lodz.android.core.utils.ScreenUtils;
import com.lodz.android.core.utils.StatusBarUtil;
import com.lodz.android.core.utils.ToastUtils;
import com.lodz.android.hermes.contract.Hermes;
import com.lodz.android.hermes.contract.OnConnectListener;
import com.lodz.android.hermes.contract.OnSendListener;
import com.lodz.android.hermes.contract.OnSubscribeListener;
import com.lodz.android.hermes.modules.HermesAgent;

import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * WebSocket测试类
 * @author zhouL
 * @date 2019/5/6
 */
public class WebSocketActivity extends BaseActivity {

    public static void start(Context context) {
        Intent starter = new Intent(context, WebSocketActivity.class);
        context.startActivity(starter);
    }

    /** 默认地址 */
    private static final String DEFAULT_URL = "ws://192.168.7.39:9090/yszz/jms/1";

    /** 地址输入框 */
    @BindView(R.id.url_edit)
    EditText mUrlEdit;
    /** 连接按钮 */
    @BindView(R.id.connect_btn)
    Button mConnectBtn;
    /** 发送内容输入框 */
    @BindView(R.id.send_edit)
    EditText mSendEdit;
    /** 发送按钮 */
    @BindView(R.id.send_btn)
    Button mSendBtn;

    /** 滚动控件 */
    @BindView(R.id.scroll_view)
    NestedScrollView mScrollView;
    /** 日志 */
    @BindView(R.id.result_tv)
    TextView mResultTv;

    /** 清空按钮 */
    @BindView(R.id.clean_btn)
    Button mCleanBtn;
    /** 断开按钮 */
    @BindView(R.id.disconnect_btn)
    Button mDisconnectBtn;

    /** 日志 */
    private String mLog = "";
    /** 推送客户端 */
    private Hermes mHermes;
    /** 保活订阅者 */
    private Disposable mDisposable;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_ws;
    }

    @Override
    protected void findViews(Bundle savedInstanceState) {
        ButterKnife.bind(this);
        initTitleBarLayout(getTitleBarLayout());
        StatusBarUtil.setColor(this, ContextCompat.getColor(getContext(), R.color.colorPrimary), 0);
    }

    private void initTitleBarLayout(TitleBarLayout titleBarLayout) {
        titleBarLayout.setTitleName(R.string.ws_title);
        titleBarLayout.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.colorPrimary));
        titleBarLayout.setTitleTextColor(R.color.white);
        titleBarLayout.needBackButton(false);
    }

    @Override
    protected boolean onPressBack() {
        if (mHermes != null){
            if (mHermes.isConnected()){
                mHermes.disconnect();
            }
            mHermes = null;
            mDisposable.dispose();
        }
        return super.onPressBack();
    }

    @Override
    protected void setListeners() {
        super.setListeners();

        // 连接按钮
        mConnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = mUrlEdit.getText().toString();
                if (TextUtils.isEmpty(url)) {
                    ToastUtils.showShort(getContext(), R.string.mqtt_url_hint);
                    return;
                }
                if (mHermes != null){
                    if (!mHermes.isConnected()){
                        mHermes.connect();
                    }
                    return;
                }
                mHermes = HermesAgent.create()
                        .setConnectType(HermesAgent.WEB_SOCKET)
                        .setUrl(url)
                        .setPrintLog(true)
                        .setOnConnectListener(new OnConnectListener() {
                            @Override
                            public void onConnectComplete(boolean isReconnected) {
                                logResult("连接完成 ： isReconnected ---> " + isReconnected);
                            }

                            @Override
                            public void onConnectFailure(Throwable cause) {
                                logResult("连接失败 : " + cause.getMessage());
                            }

                            @Override
                            public void onConnectionLost(Throwable cause) {
                                logResult("连接断开（丢失） : " + cause.getMessage());
                            }
                        })
                        .setOnSendListener(new OnSendListener() {
                            @Override
                            public void onSendComplete(String topic, String content) {
                                logResult("发送成功 : topic ---> " + topic + "   " + content);
                            }

                            @Override
                            public void onSendFailure(String topic, Throwable cause) {
                                logResult("发送失败 : topic ---> " + topic + "    " + cause.getMessage());
                            }
                        })
                        .setOnSubscribeListener(new OnSubscribeListener() {

                            @Override
                            public void onSubscribeSuccess(String topic) {
                                logResult("订阅成功 : topic ---> " + topic);
                            }

                            @Override
                            public void onSubscribeFailure(String topic, Throwable cause) {
                                logResult("订阅失败 : topic ---> " + topic + "    " + cause.getMessage());
                            }

                            @Override
                            public void onMsgArrived(String subTopic, String msg) {
                                logResult("消息到达(" + subTopic + ")： " + msg);
                            }
                        })
                        .buildConnect(getContext().getApplicationContext());
                initKeepAlive();
            }
        });

        // 发送按钮
        mSendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String content = mSendEdit.getText().toString();
                if (TextUtils.isEmpty(content)) {
                    ToastUtils.showShort(getContext(), R.string.mqtt_send_content_empty);
                    return;
                }
                if (mHermes == null || !mHermes.isConnected()) {
                    ToastUtils.showShort(getContext(), R.string.mqtt_client_unconnected);
                    return;
                }
                mHermes.sendTopic("", mSendEdit.getText().toString());
            }
        });

        // 清空按钮
        mCleanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLog = "";
                mResultTv.setText("");
            }
        });

        // 断开按钮
        mDisconnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mHermes != null){
                    mHermes.disconnect();
                }
            }
        });
    }

    /** 初始化保活线程 */
    private void initKeepAlive() {
        mDisposable = Observable.interval(60, 60, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long i) throws Exception {
                        if (mHermes == null) {
                            if (mDisposable != null) {
                                mDisposable.dispose();
                            }
                            return;
                        }
                        if (!mHermes.isConnected()) {
                            mHermes.disconnect();
                            Thread.sleep(1000);
                            mHermes.connect();
                        }
                    }
                });
    }

    @Override
    protected void initData() {
        super.initData();
        mUrlEdit.setText(DEFAULT_URL);
        showStatusCompleted();
    }

    /**
     * 打印信息
     * @param result 信息
     */
    private void logResult(String result){
        if (TextUtils.isEmpty(result)) {
            result = "";
        }
        mLog = mLog + DateUtils.getCurrentFormatString(DateUtils.TYPE_8) + " : " + result + "\n";
        mResultTv.setText(mLog);
        mScrollView.post(new Runnable() {
            @Override
            public void run() {
                mScrollView.smoothScrollTo(ScreenUtils.getScreenWidth(getContext()), ScreenUtils.getScreenHeight(getContext()));
            }
        });
    }

}
