package com.mindisle.app.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mindisle.app.R;
import com.mindisle.app.utils.StatusBarUtils;
import com.mindisle.app.utils.ToastUtils;
import com.mindisle.app.view.EmotionTagView;

public class MainActivity extends AppCompatActivity {
    private EmotionTagView[] quickMoodViews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusBarUtils.applyLightStatusBar(this);
        setContentView(R.layout.activity_main);
        setupQuickMoods();
        setupCards();
        setupBottomNavigation(R.id.nav_companion);
    }

    private void setupQuickMoods() {
        quickMoodViews = new EmotionTagView[]{
                findViewById(R.id.home_mood_happy),
                findViewById(R.id.home_mood_calm),
                findViewById(R.id.home_mood_anxious),
                findViewById(R.id.home_mood_tired),
                findViewById(R.id.home_mood_low)
        };
        for (EmotionTagView moodView : quickMoodViews) {
            moodView.setOnClickListener(v -> {
                selectQuickMood((EmotionTagView) v);
                ToastUtils.show(this, "已选择：" + ((EmotionTagView) v).getText());
            });
        }
    }

    private void selectQuickMood(EmotionTagView selectedView) {
        for (EmotionTagView moodView : quickMoodViews) {
            moodView.setActive(moodView == selectedView);
        }
    }

    private void setupCards() {
        bindOpen(R.id.card_chat, CompanionActivity.class);
        bindOpen(R.id.card_mood_diary, MoodDiaryActivity.class);
        bindOpen(R.id.card_practice, PracticeActivity.class);
        bindOpen(R.id.card_pet, PetCustomizeActivity.class);
        bindOpen(R.id.card_article, ArticleActivity.class);
        bindOpen(R.id.card_crisis, CrisisHelpActivity.class);
        bindOpen(R.id.card_profile, ProfileActivity.class);
    }

    private void bindOpen(int viewId, Class<?> target) {
        View view = findViewById(viewId);
        view.setOnClickListener(v -> startActivity(new Intent(this, target)));
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
