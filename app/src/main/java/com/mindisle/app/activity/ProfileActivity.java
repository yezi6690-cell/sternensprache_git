package com.mindisle.app.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mindisle.app.R;
import com.mindisle.app.manager.UserManager;
import com.mindisle.app.utils.StatusBarUtils;

public class ProfileActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusBarUtils.applyLightStatusBar(this);
        setContentView(R.layout.activity_profile);
        setupProfileInfo();
        setupRows();
        setupBottomNavigation(R.id.nav_profile);
    }

    private void setupProfileInfo() {
        TextView nameText = findViewById(R.id.profile_name_text);
        TextView identityText = findViewById(R.id.profile_identity_text);
        TextView studentIdText = findViewById(R.id.profile_student_id_text);
        nameText.setText(UserManager.getNickname());
        identityText.setText(UserManager.getIdentity());
        studentIdText.setText("学号：" + UserManager.getStudentId());
    }

    private void setupRows() {
        findViewById(R.id.profile_mood_row).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("个人资料")
                .setMessage("昵称：心屿用户\n身份：大学生\n学号：20260001")
                .setPositiveButton("好的", null)
                .show());
        findViewById(R.id.profile_pet_row).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("二维码")
                .setMessage("第一版演示：这里将展示个人二维码。")
                .setPositiveButton("好的", null)
                .show());
        findViewById(R.id.profile_privacy_row).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("主题设置")
                .setMessage("第一版演示：主题色、深浅模式将在后续接入。")
                .setPositiveButton("我知道了", null)
                .show());
        findViewById(R.id.profile_about_row).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("账号设置")
                .setMessage("第一版暂不接入真实登录注册。")
                .setPositiveButton("好的", null)
                .show());
        findViewById(R.id.profile_about_app_row).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("关于心屿")
                .setMessage("心屿 MindIsle 是面向大学生的 AI 心理陪伴与情绪自助 App 原型。角色设置统一放在陪伴页右滑菜单中。")
                .setPositiveButton("好的", null)
                .show());
    }

    private void setupBottomNavigation(int selectedItemId) {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_nav);
        bottomNavigationView.setSelectedItemId(selectedItemId);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == selectedItemId) return true;
            if (itemId == R.id.nav_companion) {
                startActivity(new Intent(this, CompanionActivity.class));
            } else if (itemId == R.id.nav_exchange) {
                Intent intent = new Intent(this, CompanionActivity.class);
                intent.putExtra(CompanionActivity.EXTRA_INITIAL_TAB, CompanionActivity.TAB_EXCHANGE);
                startActivity(intent);
            } else if (itemId == R.id.nav_diagnosis) {
                startActivity(new Intent(this, DiagnosisActivity.class));
            }
            return true;
        });
    }
}
