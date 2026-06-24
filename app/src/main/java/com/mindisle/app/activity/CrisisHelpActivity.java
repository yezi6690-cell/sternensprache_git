package com.mindisle.app.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.mindisle.app.R;
import com.mindisle.app.utils.StatusBarUtils;
import com.mindisle.app.utils.ToastUtils;

public class CrisisHelpActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusBarUtils.applyLightStatusBar(this);
        setContentView(R.layout.activity_crisis_help);
        setupToolbar();
        setupButtons();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupButtons() {
        findViewById(R.id.call_110_button).setOnClickListener(v -> showDialerToast());
        findViewById(R.id.call_120_button).setOnClickListener(v -> showDialerToast());
        findViewById(R.id.contact_counselor_button).setOnClickListener(v -> ToastUtils.show(this, "第一阶段演示：这里将联系辅导员"));
        findViewById(R.id.contact_school_button).setOnClickListener(v -> ToastUtils.show(this, "第一阶段演示：这里将联系学校心理中心"));
        findViewById(R.id.back_chat_button).setOnClickListener(v -> {
            Intent intent = new Intent(this, CompanionActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void showDialerToast() {
        ToastUtils.show(this, "第一阶段演示：这里将调用系统拨号功能");
    }
}
