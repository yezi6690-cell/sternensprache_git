package com.mindisle.app.utils;

import android.app.Activity;

import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.mindisle.app.R;

@SuppressWarnings("deprecation")
public class StatusBarUtils {
    private StatusBarUtils() {
    }

    public static void applyLightStatusBar(Activity activity) {
        activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.xingyue_system_bar));
        activity.getWindow().setNavigationBarColor(ContextCompat.getColor(activity, R.color.xingyue_system_bar));
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }
}
