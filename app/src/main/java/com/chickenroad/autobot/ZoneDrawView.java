package com.chickenroad.autobot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Full-screen transparent view on which user draws a rectangle
 * to define the vehicle detection zone.
 */
public class ZoneDrawView extends View {

    private final Paint borderPaint;
    private final Paint fillPaint;
    private final Paint textPaint;
    private final Paint dimPaint;

    private float startX, startY, endX, endY;
    private boolean drawing = false;
    private boolean done = false;

    private final MainActivity activity;

    public ZoneDrawView(MainActivity activity) {
        super(activity);
        this.activity = activity;
        setAlpha(1f);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.parseColor("#00ff88"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(5f);
        borderPaint.setPathEffect(new DashPathEffect(new float[]{20, 10}, 0));

        fillPaint = new Paint();
        fillPaint.setColor(Color.parseColor("#4400ff88"));
        fillPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(42f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        dimPaint = new Paint();
        dimPaint.setColor(Color.parseColor("#88000000"));
        dimPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!drawing && !done) {
            // Initial hint
            textPaint.setTextSize(38f);
            canvas.drawText("⬇ Yahan DRAG karo ⬇", getWidth() / 2f, getHeight() * 0.5f, textPaint);
            textPaint.setTextSize(28f);
            canvas.drawText("(Lane area select karo)", getWidth() / 2f, getHeight() * 0.5f + 60, textPaint);
            return;
        }

        float left = Math.min(startX, endX);
        float top = Math.min(startY, endY);
        float right = Math.max(startX, endX);
        float bottom = Math.max(startY, endY);
        RectF rect = new RectF(left, top, right, bottom);

        // Draw selected rectangle
        canvas.drawRect(rect, fillPaint);
        canvas.drawRect(rect, borderPaint);

        // Dimensions text
        int w = (int)(right - left), h = (int)(bottom - top);
        textPaint.setTextSize(32f);
        canvas.drawText(w + " × " + h + " px", (left + right) / 2f, top - 16, textPaint);

        if (done) {
            textPaint.setTextSize(40f);
            canvas.drawText("✅ Zone Set!", getWidth() / 2f, getHeight() * 0.85f, textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (done) return true;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                startY = event.getY();
                endX = startX;
                endY = startY;
                drawing = true;
                invalidate();
                break;

            case MotionEvent.ACTION_MOVE:
                endX = event.getX();
                endY = event.getY();
                invalidate();
                break;

            case MotionEvent.ACTION_UP:
                endX = event.getX();
                endY = event.getY();
                drawing = false;

                int x1 = (int) Math.min(startX, endX);
                int y1 = (int) Math.min(startY, endY);
                int x2 = (int) Math.max(startX, endX);
                int y2 = (int) Math.max(startY, endY);

                // Must be at least 50x50
                if ((x2 - x1) >= 50 && (y2 - y1) >= 50) {
                    done = true;
                    invalidate();
                    // Save & callback after 600ms (show confirmation)
                    postDelayed(() -> activity.onZoneDrawn(x1, y1, x2, y2), 600);
                } else {
                    // Too small, reset
                    drawing = false;
                    done = false;
                    startX = startY = endX = endY = 0;
                    invalidate();
                }
                break;
        }
        return true;
    }
}
