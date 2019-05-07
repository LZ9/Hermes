package com.lodz.android.hermesdemo;

import android.os.Bundle;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.lodz.android.component.base.activity.BaseActivity;
import com.lodz.android.component.widget.base.TitleBarLayout;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends BaseActivity {

    /** 断开按钮 */
    @BindView(R.id.mqtt_btn)
    MaterialButton mMqttBtn;

    /** 断开按钮 */
    @BindView(R.id.websocket_btn)
    MaterialButton mWebsocketBtn;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected void findViews(Bundle savedInstanceState) {
        ButterKnife.bind(this);
        initTitleBarLayout(getTitleBarLayout());
    }

    private void initTitleBarLayout(TitleBarLayout titleBarLayout) {
        titleBarLayout.setTitleName(R.string.app_name);
        titleBarLayout.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
        titleBarLayout.setTitleTextColor(R.color.white);
        titleBarLayout.needBackButton(false);
    }

    @Override
    protected void setListeners() {
        super.setListeners();

        mMqttBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MqttActivity.start(getContext());
            }
        });

        mWebsocketBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WebSocketActivity.start(getContext());
            }
        });
    }

    @Override
    protected void initData() {
        super.initData();
        showStatusCompleted();
    }
}
