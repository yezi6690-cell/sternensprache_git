package com.mindisle.app.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.mindisle.app.R;
import com.mindisle.app.adapter.MoodAdapter;
import com.mindisle.app.manager.MoodManager;
import com.mindisle.app.mock.MockMoodData;
import com.mindisle.app.model.MoodRecord;
import com.mindisle.app.utils.StatusBarUtils;
import com.mindisle.app.utils.ToastUtils;
import com.mindisle.app.view.EmotionTagView;

import java.util.List;

public class MoodDiaryActivity extends AppCompatActivity {
    private EmotionTagView[] moodViews;
    private String selectedMood = "平静";
    private EditText contentInput;
    private MoodAdapter moodAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusBarUtils.applyLightStatusBar(this);
        setContentView(R.layout.activity_mood_diary);
        setupMoodSelection();
        setupRecords();
        setupSaveButton();
        setupBottomNavigation(R.id.nav_exchange);
    }

    private void setupMoodSelection() {
        moodViews = new EmotionTagView[]{
                findViewById(R.id.mood_happy),
                findViewById(R.id.mood_calm),
                findViewById(R.id.mood_anxious),
                findViewById(R.id.mood_tired),
                findViewById(R.id.mood_low),
                findViewById(R.id.mood_irritable),
                findViewById(R.id.mood_lonely)
        };
        for (EmotionTagView moodView : moodViews) {
            moodView.setOnClickListener(v -> selectMood((EmotionTagView) v));
        }
        selectMood(findViewById(R.id.mood_calm));
    }

    private void selectMood(EmotionTagView selectedView) {
        selectedMood = selectedView.getText().toString();
        for (EmotionTagView moodView : moodViews) {
            moodView.setActive(moodView == selectedView);
        }
    }

    private void setupRecords() {
        contentInput = findViewById(R.id.mood_content_input);
        RecyclerView recyclerView = findViewById(R.id.mood_recycler_view);
        moodAdapter = new MoodAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(moodAdapter);
        recyclerView.setNestedScrollingEnabled(false);
        refreshRecords();
    }

    private void setupSaveButton() {
        MaterialButton saveButton = findViewById(R.id.save_mood_button);
        saveButton.setOnClickListener(v -> {
            String content = contentInput.getText().toString().trim();
            if (content.isEmpty()) {
                content = "今天先把此刻的情绪记下来。";
            }
            MoodManager.saveRecord(this, new MoodRecord(selectedMood, content, System.currentTimeMillis()));
            contentInput.setText("");
            refreshRecords();
            ToastUtils.show(this, "已保存，今天也辛苦了。");
        });
    }

    private void refreshRecords() {
        List<MoodRecord> records = MoodManager.loadRecords(this);
        if (records.isEmpty()) {
            records = MockMoodData.recentRecords();
        }
        moodAdapter.submitList(records);
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
