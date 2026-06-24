package com.mindisle.app.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.mindisle.app.R;

public class EmotionTagView extends AppCompatTextView {
    private boolean active;

    public EmotionTagView(Context context) {
        super(context);
        init();
    }

    public EmotionTagView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EmotionTagView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setGravity(Gravity.CENTER);
        setMinHeight(dp(38));
        setTextSize(14);
        setActive(false);
    }

    public void setActive(boolean active) {
        this.active = active;
        setBackgroundResource(active ? R.drawable.bg_tag_active : R.drawable.bg_tag_inactive);
        setTextColor(ContextCompat.getColor(getContext(), active ? android.R.color.white : R.color.mi_text_main));
    }

    public boolean isActive() {
        return active;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
