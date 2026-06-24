package com.mindisle.app.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.mindisle.app.R;

public class RoundCardView extends MaterialCardView {
    public RoundCardView(Context context) {
        super(context);
        init();
    }

    public RoundCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RoundCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setRadius(getResources().getDimension(R.dimen.mi_card_radius));
        setCardBackgroundColor(ContextCompat.getColor(getContext(), R.color.mi_card));
        setCardElevation(0f);
        setUseCompatPadding(true);
    }
}
