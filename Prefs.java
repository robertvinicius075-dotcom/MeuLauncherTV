package com.meulauncher.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Preferências locais: favoritos (em ordem), recentes e fundo escolhido. Nada sai da TV. */
final class Prefs {
    private static final int MAX_RECENTS = 12;

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    /** Retorna null enquanto os favoritos nunca foram definidos. */
    List<String> favorites() {
        return readList("fav", true);
    }

    void setFavorites(List<String> list) {
        sp.edit().putString("fav", TextUtils.join(",", list)).apply();
    }

    List<String> recents() {
        List<String> l = readList("recent", false);
        return l == null ? new ArrayList<String>() : l;
    }

    void pushRecent(String pkg) {
        List<String> l = recents();
        l.remove(pkg);
        l.add(0, pkg);
        while (l.size() > MAX_RECENTS) {
            l.remove(l.size() - 1);
        }
        sp.edit().putString("recent", TextUtils.join(",", l)).apply();
    }

    int background() {
        return sp.getInt("bg", 0);
    }

    void setBackground(int mode) {
        sp.edit().putInt("bg", mode).apply();
    }

    private List<String> readList(String key, boolean nullIfMissing) {
        String s = sp.getString(key, null);
        if (s == null) {
            return nullIfMissing ? null : new ArrayList<String>();
        }
        List<String> out = new ArrayList<>();
        if (!s.isEmpty()) {
            for (String p : s.split(",")) {
                if (!p.isEmpty()) {
                    out.add(p);
                }
            }
        }
        return out;
    }
}
