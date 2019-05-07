package com.lodz.android.hermesdemo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

import com.lodz.android.component.base.activity.BaseActivity;
import com.lodz.android.component.widget.base.TitleBarLayout;

import butterknife.ButterKnife;

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

    @Override
    protected int getLayoutId() {
        return R.layout.activity_ws;
    }

    @Override
    protected void findViews(Bundle savedInstanceState) {
        ButterKnife.bind(this);
        initTitleBarLayout(getTitleBarLayout());
    }

    private void initTitleBarLayout(TitleBarLayout titleBarLayout) {
        titleBarLayout.setTitleName(R.string.ws_title);
        titleBarLayout.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
        titleBarLayout.setTitleTextColor(R.color.white);
        titleBarLayout.needBackButton(false);
    }

    @Override
    protected void initData() {
        super.initData();
        showStatusCompleted();
    }
}
