package com.thorpad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** A classic arrow cursor. Its tip (the hotspot) is the view's top-left corner. */
final class PointerView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final float scale;
    private boolean pressed;

    PointerView(Context context, float sizeMultiplier) {
        super(context);
        scale = context.getResources().getDisplayMetrics().density * sizeMultiplier;
        fill.setStyle(Paint.Style.FILL);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeJoin(Paint.Join.ROUND);
        outline.setColor(Color.BLACK);
        outline.setStrokeWidth(1.5f * scale);

        // Arrow drawn on a 12 x 19 grid, then scaled to the screen's density.
        float[][] pts = {
                {0, 0}, {0, 16}, {4, 12.5f}, {6.8f, 18.5f}, {9.2f, 17.4f}, {6.5f, 11.6f}, {11.5f, 11.6f}
        };
        float pad = 1.5f;
        arrow.moveTo((pts[0][0] + pad) * scale, (pts[0][1] + pad) * scale);
        for (int i = 1; i < pts.length; i++) {
            arrow.lineTo((pts[i][0] + pad) * scale, (pts[i][1] + pad) * scale);
        }
        arrow.close();
    }

    int sizePx() { return Math.round(22 * scale); }

    /** Tints the pointer while a drag is held down. */
    void setPressed2(boolean value) {
        if (pressed != value) {
            pressed = value;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        fill.setColor(pressed ? Color.rgb(120, 200, 255) : Color.WHITE);
        canvas.drawPath(arrow, fill);
        canvas.drawPath(arrow, outline);
    }
}
