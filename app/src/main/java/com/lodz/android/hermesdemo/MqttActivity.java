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
import com.lodz.android.core.utils.StringUtils;
import com.lodz.android.core.utils.ToastUtils;
import com.lodz.android.hermes.contract.Hermes;
import com.lodz.android.hermes.contract.OnConnectListener;
import com.lodz.android.hermes.contract.OnSendListener;
import com.lodz.android.hermes.contract.OnSubscribeListener;
import com.lodz.android.hermes.modules.HermesAgent;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * MQTT测试类
 * @author zhouL
 * @date 2019/5/6
 */
public class MqttActivity extends BaseActivity {

    public static void start(Context context) {
        Intent starter = new Intent(context, MqttActivity.class);
        context.startActivity(starter);
    }

    /** 默认地址 */
    private static final String DEFAULT_URL = "tcp://192.168.6.141:1883";
    /** 默认客户端id */
    private static final String DEFAULT_CLIENT_ID = "12345";
    /** 默认订阅主题 */
    private static final String DEFAULT_SUB_TOPIC = "test.topic";
    /** 默认发送主题 */
    private static final String DEFAULT_SEND_TOPIC = "test.client";
    /** 默认发送内容 */
    private static final String DEFAULT_SEND_CONTENT = "测试数据";

    /** 滚动控件 */
    @BindView(R.id.scroll_view)
    NestedScrollView mScrollView;
    /** 日志 */
    @BindView(R.id.result)
    TextView mResultTv;

    /** 地址输入框 */
    @BindView(R.id.url_edit)
    EditText mUrlEdit;
    /** ClientId输入框 */
    @BindView(R.id.client_id_edit)
    EditText mClientIdEdit;
    /** 订阅主题输入框 */
    @BindView(R.id.subtopic_edit)
    EditText mSubtopicEdit;
    /** 连接按钮 */
    @BindView(R.id.connect_btn)
    Button mConnectBtn;

    /** 发送主题输入框 */
    @BindView(R.id.send_topic_edit)
    EditText mSendTpoicEdit;
    /** 发送内容输入框 */
    @BindView(R.id.send_edit)
    EditText mSendEdit;
    /** 发送按钮 */
    @BindView(R.id.send_btn)
    Button mSendBtn;

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

    @Override
    protected int getLayoutId() {
        return R.layout.activity_mqtt;
    }

    @Override
    protected void findViews(Bundle savedInstanceState) {
        ButterKnife.bind(this);
        initTitleBarLayout(getTitleBarLayout());
    }

    private void initTitleBarLayout(TitleBarLayout titleBarLayout) {
        titleBarLayout.setTitleName(R.string.mqtt_title);
        titleBarLayout.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
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
        }
        return super.onPressBack();
    }

    @Override
    protected void setListeners() {
        super.setListeners();
        // 连接
        mConnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TextUtils.isEmpty(mUrlEdit.getText())) {
                    ToastUtils.showShort(getContext(), R.string.mqtt_url_empty);
                    return;
                }
                if (TextUtils.isEmpty(mClientIdEdit.getText())) {
                    ToastUtils.showShort(getContext(), R.string.mqtt_client_id_empty);
                    return;
                }
                List<String> list = new ArrayList<>();
                if (!TextUtils.isEmpty(mSubtopicEdit.getText())) {
                    list = StringUtils.getListBySeparator(mSubtopicEdit.getText().toString(), ",");
                }
                connect(mUrlEdit.getText().toString(), mClientIdEdit.getText().toString(), list);
            }
        });

        // 发送
        mSendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mHermes == null || !mHermes.isConnected()){
                    ToastUtils.showShort(getContext(), R.string.mqtt_client_unconnected);
                    return;
                }
                if (TextUtils.isEmpty(mSendTpoicEdit.getText())) {
                    ToastUtils.showShort(getContext(), R.string.mqtt_send_topic_empty);
                    return;
                }
                if (TextUtils.isEmpty(mSendEdit.getText())) {
                    ToastUtils.showShort(getContext(), R.string.mqtt_send_content_empty);
                    return;
                }
                mHermes.sendTopic(mSendTpoicEdit.getText().toString(), mSendEdit.getText().toString());
            }
        });

        // 清空
        mCleanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLog = "";
                mResultTv.setText("");
            }
        });

        // 断开
        mDisconnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mHermes != null && mHermes.isConnected()){
                    mHermes.disconnect();
                }
            }
        });
    }

    @Override
    protected void initData() {
        super.initData();
        mUrlEdit.setText(DEFAULT_URL);
        mUrlEdit.setSelection(mUrlEdit.length());

        mClientIdEdit.setText(DEFAULT_CLIENT_ID);
        mClientIdEdit.setSelection(mClientIdEdit.length());

        mSubtopicEdit.setText(DEFAULT_SUB_TOPIC);
        mSubtopicEdit.setSelection(mSubtopicEdit.length());

        mSendTpoicEdit.setText(DEFAULT_SEND_TOPIC);
        mSendTpoicEdit.setSelection(mSendTpoicEdit.length());

        mSendEdit.setText(DEFAULT_SEND_CONTENT);
        mSendEdit.setSelection(mSendEdit.length());
        showStatusCompleted();
    }


    /**
     * 连接
     * @param url 地址
     * @param clientId 客户端id
     * @param subTopic 订阅主题
     */
    private void connect(String url, String clientId, List<String> subTopic) {
        if (mHermes != null){
            if (!mHermes.isConnected()){
                mHermes.connect();
            }
            return;
        }
        mHermes = HermesAgent.create()
                .setUrl(url)
                .setClientId(clientId)
                .setPrintLog(true)
                .setLogTag("HermesLog")
                .setSubTopics(subTopic)
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
