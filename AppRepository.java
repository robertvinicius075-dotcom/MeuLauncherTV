package com.meulauncher.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.content.ComponentName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lê os aplicativos instalados. Respeita a visibilidade de pacotes do Android 11 (&lt;queries&gt;). */
final class AppRepository {
    private AppRepository() {
    }

    static List<AppEntry> load(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, AppEntry> map = new LinkedHashMap<>();
        // Primeiro os apps de TV; depois os apps comuns (ex.: APKs instalados por fora).
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String category : categories) {
            Intent query = new Intent(Intent.ACTION_MAIN);
            query.addCategory(category);
            List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
            for (ResolveInfo ri : found) {
                ActivityInfo ai = ri.activityInfo;
                if (ai == null || ai.packageName.equals(ctx.getPackageName())) {
                    continue;
                }
                if (map.containsKey(ai.packageName)) {
                    continue;
                }
                CharSequence label = ri.loadLabel(pm);
                Drawable icon = ri.loadIcon(pm);
                Drawable banner = null;
                try {
                    banner = ai.loadBanner(pm);
                } catch (Exception ignored) {
                    // sem banner: usa o ícone
                }
                map.put(ai.packageName, new AppEntry(
                        ai.packageName,
                        label == null ? ai.packageName : label.toString(),
                        new ComponentName(ai.packageName, ai.name),
                        icon,
                        banner));
            }
        }
        List<AppEntry> list = new ArrayList<>(map.values());
        Collections.sort(list, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.toLowerCase(Locale.ROOT).compareTo(b.label.toLowerCase(Locale.ROOT));
            }
        });
        return list;
    }
}
