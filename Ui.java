package com.meulauncher.tv;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/**
 * Ajudantes de interface. Todas as medidas são desenhadas para 1080 px de altura
 * e escalam sozinhas em 720p, 1080p e 4K (1 unidade = altura da tela / 1080).
 */
final class Ui {
    static final int LUMINOUS = 0xFFCFF7E4;

    private static float unit = 1f;

    private Ui() {
    }

    static void init(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        unit = Math.min(m.widthPixels, m.heightPixels) / 1080f;
    }

    static int px(float v) {
        return Math.round(v * unit);
    }

    static float textPx(float v) {
        return v * unit;
    }

    static GradientDrawable fill(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(radius));
        return d;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    static GradientDrawable ring(int fillColor, float radius, int strokeColor, float stroke) {
        GradientDrawable d = fill(fillColor, radius);
        d.setStroke(Math.max(2, px(stroke)), strokeColor);
        return d;
    }

    static void roundClip(View v, final float radius) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(radius));
            }
        });
        v.setClipToOutline(true);
    }

    static void circleClip(View v) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        v.setClipToOutline(true);
    }

    /**
     * Destaque do item em foco: cresce suavemente, sobe de nível (sombra sutil)
     * e ganha borda luminosa. Com overlay=true a borda é desenhada por cima dos
     * filhos (usa o foreground do FrameLayout).
     */
    static void focusEffect(View v, final Drawable normal, final Drawable focused,
                            final boolean overlay, final Runnable onFocus) {
        apply(v, normal, overlay);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean has) {
                apply(view, has ? focused : normal, overlay);
                float s = has ? 1.08f : 1f;
                view.animate()
                        .scaleX(s)
                        .scaleY(s)
                        .translationZ(has ? px(10) : 0f)
                        .setDuration(140)
                        .start();
                if (has && onFocus != null) {
                    onFocus.run();
                }
            }
        });
    }

    private static void apply(View v, Drawable d, boolean overlay) {
        if (overlay && v instanceof FrameLayout) {
            ((FrameLayout) v).setForeground(d);
        } else {
            v.setBackground(d);
        }
    }
}
