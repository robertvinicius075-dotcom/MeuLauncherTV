package com.meulauncher.tv;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

/** Um aplicativo instalado que pode ser aberto pelo launcher. */
final class AppEntry {
    final String pkg;
    final String label;
    final ComponentName component;
    final Drawable icon;
    /** Banner do app (pode ser nulo; nem todo app declara um). */
    final Drawable banner;

    AppEntry(String pkg, String label, ComponentName component, Drawable icon, Drawable banner) {
        this.pkg = pkg;
        this.label = label;
        this.component = component;
        this.icon = icon;
        this.banner = banner;
    }
}
