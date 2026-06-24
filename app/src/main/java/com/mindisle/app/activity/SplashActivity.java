package com.mindisle.app.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.mindisle.app.R;

public class SplashActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openMainRunnable = () -> {
        startActivity(new Intent(SplashActivity.this, CompanionActivity.class));
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        handler.postDelayed(openMainRunnable, 1200);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(openMainRunnable);
        super.onDestroy();
    }
}
