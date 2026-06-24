package com.mindisle.app.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mindisle.app.R;
import com.mindisle.app.adapter.PracticeAdapter;
import com.mindisle.app.mock.MockPracticeData;
import com.mindisle.app.model.PracticeItem;
import com.mindisle.app.utils.StatusBarUtils;

public class PracticeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusBarUtils.applyLightStatusBar(this);
        setContentView(R.layout.activity_practice);
        setupToolbar();
        setupPracticeList();
        setupBottomNavigation(R.id.nav_diagnosis);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupPracticeList() {
        RecyclerView recyclerView = findViewById(R.id.practice_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new PracticeAdapter(MockPracticeData.list(), this::showPracticeDialog));
    }

    private void showPracticeDialog(PracticeItem item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(item.getTitle())
                .setMessage(item.getDetail())
                .setPositiveButton("我知道了", null)
                .show();
    }

    private void setupBottomNavigation(int selectedItemId) {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_nav);
        bottomNavigationView.setSelectedItemId(selectedItemId);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_companion) {
                startActivity(new Intent(this, CompanionActivity.class));
            } else if (itemId == R.id.nav_exchange) {
                Intent intent = new Intent(this, CompanionActivity.class);
                intent.putExtra(CompanionActivity.EXTRA_INITIAL_TAB, CompanionActivity.TAB_EXCHANGE);
                startActivity(intent);
            } else if (itemId == R.id.nav_diagnosis) {
                startActivity(new Intent(this, DiagnosisActivity.class));
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
            }
            return true;
        });
    }
}
