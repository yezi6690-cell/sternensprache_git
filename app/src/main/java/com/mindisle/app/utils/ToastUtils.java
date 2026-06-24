package com.mindisle.app.utils;

import android.content.Context;
import android.widget.Toast;

public class ToastUtils {
    private ToastUtils() {
    }

    public static void show(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
