package com.mindisle.app.profile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

public class ProfileQrView extends View {
    private static final int GRID_SIZE = 25;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String seed = "user_10001";

    public ProfileQrView(Context context) {
        this(context, null);
    }

    public ProfileQrView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(Color.WHITE);
    }

    public void setSeed(String seed) {
        this.seed = seed == null ? "" : seed;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cell = Math.min(getWidth(), getHeight()) / (float) GRID_SIZE;
        float offsetX = (getWidth() - cell * GRID_SIZE) / 2f;
        float offsetY = (getHeight() - cell * GRID_SIZE) / 2f;
        Random random = new Random(seed.hashCode());

        paint.setColor(Color.rgb(38, 50, 77));
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int column = 0; column < GRID_SIZE; column++) {
                if (isFinderArea(column, row) || random.nextFloat() > 0.52f) {
                    canvas.drawRect(
                            offsetX + column * cell,
                            offsetY + row * cell,
                            offsetX + (column + 1) * cell,
                            offsetY + (row + 1) * cell,
                            paint
                    );
                }
            }
        }

        drawFinder(canvas, offsetX, offsetY, cell, 1, 1);
        drawFinder(canvas, offsetX, offsetY, cell, GRID_SIZE - 8, 1);
        drawFinder(canvas, offsetX, offsetY, cell, 1, GRID_SIZE - 8);
    }

    private boolean isFinderArea(int x, int y) {
        return (x <= 8 && y <= 8)
                || (x >= GRID_SIZE - 9 && y <= 8)
                || (x <= 8 && y >= GRID_SIZE - 9);
    }

    private void drawFinder(Canvas canvas, float offsetX, float offsetY, float cell, int x, int y) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawRect(offsetX + x * cell, offsetY + y * cell,
                offsetX + (x + 7) * cell, offsetY + (y + 7) * cell, paint);
        paint.setColor(Color.rgb(38, 50, 77));
        canvas.drawRect(offsetX + x * cell, offsetY + y * cell,
                offsetX + (x + 7) * cell, offsetY + (y + 7) * cell, paint);
        paint.setColor(Color.WHITE);
        canvas.drawRect(offsetX + (x + 1) * cell, offsetY + (y + 1) * cell,
                offsetX + (x + 6) * cell, offsetY + (y + 6) * cell, paint);
        paint.setColor(Color.rgb(38, 50, 77));
        canvas.drawRect(offsetX + (x + 2) * cell, offsetY + (y + 2) * cell,
                offsetX + (x + 5) * cell, offsetY + (y + 5) * cell, paint);
    }
}
