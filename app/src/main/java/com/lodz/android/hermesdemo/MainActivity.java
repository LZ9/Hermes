package com.lodz.android.hermesdemo;

import android.os.Bundle;

import com.lodz.android.component.base.activity.AbsActivity;

import butterknife.ButterKnife;

public class MainActivity extends AbsActivity {

    @Override
    protected int getAbsLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected void findViews(Bundle savedInstanceState) {
        ButterKnife.bind(this);
    }






}
