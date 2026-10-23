package com.meulauncher.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tela inicial. Interface 100% nativa (sem WebView), montada em código para ficar leve:
 * abas Início / Aplicativos / Biblioteca, banner, favoritos e fileira de apps.
 */
public class HomeActivity extends Activity {
    private static final int TAB_HOME = 0;
    private static final int TAB_APPS = 1;
    private static final int TAB_LIB = 2;
    private static final String[] TAB_NAMES = {"Início", "Aplicativos", "Biblioteca"};

    private static final int[] PRESETS = {0xFF3B6B58, 0xFF14352A, 0xFF16233A, 0xFF1B1C20};
    private static final String[] PRESET_NAMES = {"Verde suave", "Verde escuro", "Azul noite", "Grafite"};

    /** Nomes (sem espaços e símbolos) usados para escolher os favoritos iniciais, na ordem. */
    private static final String[] DEFAULT_FAVORITES = {
            "unitv", "youtube", "netflix", "globoplay", "primevideo", "disney", "spotify"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private Prefs prefs;

    private List<AppEntry> apps = new ArrayList<>();
    private final Map<String, AppEntry> byPkg = new HashMap<>();
    private String signature = "";
    private boolean loading;
    private boolean firstLoadDone;
    private int tab = TAB_HOME;

    private FrameLayout root;
    private ImageView bgImage;
    private final TextView[] tabViews = new TextView[3];
    private final ScrollView[] pageViews = new ScrollView[3];
    private LinearLayout homeContent;
    private LinearLayout appsContent;
    private LinearLayout libContent;

    private TextView heroTitle;
    private TextView heroSub;
    private TextView heroButton;
    private ImageView heroImage;
    private AppEntry heroApp;
    private View firstFocus;

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        prefs = new Prefs(this);

        root = new FrameLayout(this);
        bgImage = new ImageView(this);
        bgImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bgImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        column.addView(buildTopBar());

        FrameLayout host = new FrameLayout(this);
        column.addView(host, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        homeContent = new LinearLayout(this);
        appsContent = new LinearLayout(this);
        libContent = new LinearLayout(this);
        LinearLayout[] contents = {homeContent, appsContent, libContent};
        for (int i = 0; i < 3; i++) {
            contents[i].setOrientation(LinearLayout.VERTICAL);
            contents[i].setClipChildren(false);
            ScrollView sv = new ScrollView(this);
            sv.setFocusable(false);
            sv.setVerticalScrollBarEnabled(false);
            sv.setClipChildren(false);
            sv.addView(contents[i], new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            host.addView(sv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            pageViews[i] = sv;
        }

        setContentView(root);
        applyBackground();
        showTab(TAB_HOME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Botão Home pressionado com o launcher já aberto: volta ao começo.
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            tabViews[TAB_HOME].requestFocus();
            pageViews[TAB_HOME].scrollTo(0, 0);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showPersonalize();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // Um launcher nunca fecha: Voltar só leva à aba Início.
        if (tab != TAB_HOME) {
            tabViews[TAB_HOME].requestFocus();
        }
    }

    // ------------------------------------------------------------------ carregamento

    private void reload() {
        if (loading) {
            return;
        }
        loading = true;
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppEntry> list = AppRepository.load(app);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        applyApps(list);
                    }
                });
            }
        }, "apps-loader").start();
    }

    private void applyApps(List<AppEntry> list) {
        StringBuilder sb = new StringBuilder();
        for (AppEntry a : list) {
            sb.append(a.pkg).append(';');
        }
        String sig = sb.toString();
        if (firstLoadDone && sig.equals(signature)) {
            return;
        }
        signature = sig;
        apps = list;
        byPkg.clear();
        for (AppEntry a : apps) {
            byPkg.put(a.pkg, a);
        }
        if (prefs.favorites() == null) {
            List<String> defaults = defaultFavorites();
            if (!defaults.isEmpty()) {
                prefs.setFavorites(defaults);
            }
        }
        rebuildHome();
        rebuildApps();
        rebuildLibrary();
        if (!firstLoadDone) {
            firstLoadDone = true;
            if (firstFocus != null) {
                firstFocus.requestFocus();
            }
        } else if (getCurrentFocus() == null && firstFocus != null) {
            firstFocus.requestFocus();
        }
    }

    // ------------------------------------------------------------------ favoritos

    private List<String> favoritePackages() {
        List<String> s = prefs.favorites();
        return s == null ? new ArrayList<String>() : s;
    }

    private List<AppEntry> favorites() {
        List<AppEntry> out = new ArrayList<>();
        for (String p : favoritePackages()) {
            AppEntry a = byPkg.get(p);
            if (a != null) {
                out.add(a);
            }
        }
        return out;
    }

    private boolean isFavorite(String pkg) {
        return favoritePackages().contains(pkg);
    }

    private void toggleFavorite(AppEntry a) {
        List<String> l = favoritePackages();
        if (l.contains(a.pkg)) {
            l.remove(a.pkg);
        } else {
            l.add(a.pkg);
        }
        prefs.setFavorites(l);
        afterFavoritesChanged();
    }

    private void moveToFront(AppEntry a) {
        List<String> l = favoritePackages();
        l.remove(a.pkg);
        l.add(0, a.pkg);
        prefs.setFavorites(l);
        afterFavoritesChanged();
    }

    private void afterFavoritesChanged() {
        rebuildHome();
        if (tab == TAB_HOME && firstFocus != null) {
            firstFocus.requestFocus();
        }
    }

    private List<String> defaultFavorites() {
        List<String> out = new ArrayList<>();
        for (String key : DEFAULT_FAVORITES) {
            AppEntry a = find(key);
            if (a != null && !out.contains(a.pkg)) {
                out.add(a.pkg);
            }
        }
        return out;
    }

    private AppEntry find(String key) {
        for (AppEntry a : apps) {
            if (norm(a.label).equals(key)) {
                return a;
            }
        }
        for (AppEntry a : apps) {
            if (norm(a.label).startsWith(key)) {
                return a;
            }
        }
        return null;
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // ------------------------------------------------------------------ barra superior e abas

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.px(64), Ui.px(32), Ui.px(64), Ui.px(8));

        TextView avatar = text("TV", 22, Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.oval(0x55FFFFFF));
        bar.addView(avatar, new LinearLayout.LayoutParams(Ui.px(56), Ui.px(56)));

        View gap = new View(this);
        bar.addView(gap, new LinearLayout.LayoutParams(Ui.px(28), 1));

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = pill(TAB_NAMES[i]);
            t.setId(View.generateViewId());
            t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean has) {
                    if (has) {
                        showTab(idx);
                    }
                    refreshTabs();
                }
            });
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTab(idx);
                }
            });
            tabViews[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.px(8);
            bar.addView(t, lp);
        }

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        bar.addView(barButton("Wi-Fi", new Runnable() {
            @Override
            public void run() {
                openSettings(Settings.ACTION_WIFI_SETTINGS);
            }
        }));
        bar.addView(barButton("Configurações", new Runnable() {
            @Override
            public void run() {
                openSettings(Settings.ACTION_SETTINGS);
            }
        }));
        bar.addView(barButton("Personalizar", new Runnable() {
            @Override
            public void run() {
                showPersonalize();
            }
        }));

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(28));
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = Ui.px(24);
        bar.addView(clock, clp);
        return bar;
    }

    private View barButton(String label, final Runnable action) {
        TextView t = pill(label);
        Ui.focusEffect(t, Ui.fill(0x33FFFFFF, 28),
                Ui.ring(0x55FFFFFF, 28, Ui.LUMINOUS, 3), false, null);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.px(12);
        t.setLayoutParams(lp);
        return t;
    }

    private void showTab(int index) {
        tab = index;
        for (int i = 0; i < 3; i++) {
            pageViews[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        if (index == TAB_LIB && firstLoadDone) {
            rebuildLibrary();
        }
        refreshTabs();
    }

    private void refreshTabs() {
        for (int i = 0; i < 3; i++) {
            TextView t = tabViews[i];
            if (t == null) {
                continue;
            }
            boolean selected = i == tab;
            boolean focused = t.hasFocus();
            if (selected && focused) {
                t.setBackground(Ui.ring(0xFFF2F2F2, 28, Ui.LUMINOUS, 3));
                t.setTextColor(0xFF1B1C20);
            } else if (selected) {
                t.setBackground(Ui.fill(0xFFF2F2F2, 28));
                t.setTextColor(0xFF1B1C20);
            } else if (focused) {
                t.setBackground(Ui.ring(0x33FFFFFF, 28, Ui.LUMINOUS, 3));
                t.setTextColor(Color.WHITE);
            } else {
                t.setBackground(null);
                t.setTextColor(0xCCFFFFFF);
            }
        }
    }

    private void openSettings(String action) {
        try {
            Intent i = new Intent(action);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Esta TV não permite abrir essa tela", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ página Início

    private void rebuildHome() {
        homeContent.removeAllViews();
        firstFocus = null;
        List<AppEntry> favs = favorites();
        heroApp = !favs.isEmpty() ? favs.get(0) : (apps.isEmpty() ? null : apps.get(0));

        homeContent.addView(buildHero());

        if (apps.isEmpty()) {
            TextView t = text("Nenhum aplicativo encontrado.", 26, 0xCCFFFFFF);
            t.setPadding(Ui.px(64), Ui.px(32), Ui.px(64), 0);
            homeContent.addView(t);
            return;
        }

        if (!favs.isEmpty()) {
            homeContent.addView(sectionTitle("Favoritos"));
            List<View> cards = new ArrayList<>();
            for (AppEntry a : favs) {
                View c = card(a);
                c.setLayoutParams(rowParams(300, 169));
                cards.add(c);
            }
            homeContent.addView(row(cards));
            firstFocus = cards.get(0);
        }

        homeContent.addView(sectionTitle("Seus aplicativos"));
        List<View> icons = new ArrayList<>();
        for (AppEntry a : apps) {
            View v = iconItem(a, true);
            v.setLayoutParams(rowParams(160, ViewGroup.LayoutParams.WRAP_CONTENT));
            icons.add(v);
        }
        homeContent.addView(row(icons));
        if (firstFocus == null) {
            firstFocus = icons.get(0);
        }
        updateHero(heroApp);
    }

    private View buildHero() {
        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(Ui.fill(0x33000000, 28));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(320));
        hlp.setMargins(Ui.px(64), Ui.px(12), Ui.px(64), Ui.px(8));
        hero.setLayoutParams(hlp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(Ui.px(48), Ui.px(24), Ui.px(48), Ui.px(24));
        hero.addView(col, new FrameLayout.LayoutParams(
                Ui.px(1000), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

        heroTitle = text("", 54, Color.WHITE);
        heroTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heroTitle.setSingleLine();
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(heroTitle);

        heroSub = text("", 24, 0xCCFFFFFF);
        heroSub.setMaxLines(2);
        heroSub.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.px(10);
        col.addView(heroSub, slp);

        heroButton = pill("Abrir");
        heroButton.setTextColor(0xFF1B1C20);
        Ui.focusEffect(heroButton, Ui.fill(0xFFF2F2F2, 28),
                Ui.ring(0xFFF2F2F2, 28, Ui.LUMINOUS, 4), false, null);
        heroButton.setNextFocusUpId(tabViews[TAB_HOME].getId());
        heroButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (heroApp != null) {
                    launch(heroApp);
                }
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.px(28);
        col.addView(heroButton, blp);

        heroImage = new ImageView(this);
        Ui.roundClip(heroImage, 20);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                Ui.px(440), Ui.px(248), Gravity.END | Gravity.CENTER_VERTICAL);
        ilp.rightMargin = Ui.px(40);
        hero.addView(heroImage, ilp);
        return hero;
    }

    private void updateHero(AppEntry a) {
        if (a == null || heroTitle == null) {
            return;
        }
        heroApp = a;
        heroTitle.setText(a.label);
        heroSub.setText(isFavorite(a.pkg)
                ? "Favorito. Pressione OK para abrir ou segure OK para mais opções."
                : "Pressione OK para abrir ou segure OK para favoritar.");
        if (a.banner != null) {
            heroImage.setPadding(0, 0, 0, 0);
            heroImage.setBackground(null);
            heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            heroImage.setImageDrawable(a.banner);
        } else {
            heroImage.setPadding(Ui.px(70), Ui.px(40), Ui.px(70), Ui.px(40));
            heroImage.setBackground(Ui.fill(0x22000000, 20));
            heroImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            heroImage.setImageDrawable(a.icon);
        }
    }

    // ------------------------------------------------------------------ página Aplicativos

    private void rebuildApps() {
        appsContent.removeAllViews();
        appsContent.addView(sectionTitle("Todos os aplicativos"));
        if (apps.isEmpty()) {
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int cols = Math.max(4, (dm.widthPixels - 2 * Ui.px(64)) / Ui.px(168));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(cols);
        grid.setClipChildren(false);
        grid.setPadding(Ui.px(56), Ui.px(8), Ui.px(56), Ui.px(24));
        for (int i = 0; i < apps.size(); i++) {
            View v = iconItem(apps.get(i), false);
            if (i < cols) {
                v.setNextFocusUpId(tabViews[TAB_APPS].getId());
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = Ui.px(160);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.setMargins(Ui.px(4), Ui.px(4), Ui.px(4), Ui.px(12));
            grid.addView(v, lp);
        }
        appsContent.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ------------------------------------------------------------------ página Biblioteca

    private void rebuildLibrary() {
        libContent.removeAllViews();
        libContent.addView(sectionTitle("Abertos recentemente"));
        List<View> items = new ArrayList<>();
        for (String p : prefs.recents()) {
            AppEntry a = byPkg.get(p);
            if (a != null) {
                View v = iconItem(a, false);
                v.setNextFocusUpId(tabViews[TAB_LIB].getId());
                v.setLayoutParams(rowParams(160, ViewGroup.LayoutParams.WRAP_CONTENT));
                items.add(v);
            }
        }
        if (items.isEmpty()) {
            TextView t = text("Os aplicativos que você abrir aparecem aqui.", 24, 0xCCFFFFFF);
            t.setPadding(Ui.px(64), Ui.px(8), Ui.px(64), 0);
            libContent.addView(t);
        } else {
            libContent.addView(row(items));
        }

        libContent.addView(sectionTitle("Dicas"));
        String[] tips = {
                "Segure OK em um aplicativo para favoritar, mover para o início ou ver informações.",
                "O botão Menu do controle abre a personalização do fundo.",
                "Voltar sempre leva à aba Início."};
        for (String s : tips) {
            TextView t = text(s, 24, 0xCCFFFFFF);
            t.setPadding(Ui.px(64), Ui.px(6), Ui.px(64), Ui.px(6));
            libContent.addView(t);
        }
    }

    // ------------------------------------------------------------------ componentes

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(size));
        return t;
    }

    private TextView pill(String s) {
        TextView t = text(s, 24, Color.WHITE);
        t.setFocusable(true);
        t.setClickable(true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine();
        t.setPadding(Ui.px(28), Ui.px(12), Ui.px(28), Ui.px(12));
        return t;
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 28, Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(Ui.px(64), Ui.px(22), Ui.px(64), Ui.px(2));
        return t;
    }

    private LinearLayout.LayoutParams rowParams(int w, int h) {
        int height = h > 0 ? Ui.px(h) : h;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(w), height);
        lp.rightMargin = Ui.px(w >= 300 ? 20 : 8);
        return lp;
    }

    private HorizontalScrollView row(List<View> items) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setFocusable(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.setPadding(Ui.px(64), Ui.px(14), Ui.px(64), Ui.px(14));
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setClipChildren(false);
        for (View v : items) {
            l.addView(v);
        }
        hs.addView(l, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return hs;
    }

    /** Cartão grande de favorito (16:9), com banner do app ou o ícone. */
    private View card(final AppEntry a) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(Ui.fill(0x33000000, 22));
        Ui.roundClip(f, 22);

        ImageView iv = new ImageView(this);
        if (a.banner != null) {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(a.banner);
        } else {
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(Ui.px(70), Ui.px(24), Ui.px(70), Ui.px(56));
            iv.setImageDrawable(a.icon);
        }
        f.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView label = text(a.label, 20, Color.WHITE);
        label.setSingleLine();
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setBackgroundColor(0x66000000);
        label.setPadding(Ui.px(14), Ui.px(8), Ui.px(14), Ui.px(8));
        f.addView(label, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        f.setFocusable(true);
        f.setClickable(true);
        Ui.focusEffect(f, null, Ui.ring(0x00000000, 22, Ui.LUMINOUS, 4), true, new Runnable() {
            @Override
            public void run() {
                updateHero(a);
            }
        });
        wireClicks(f, a);
        return f;
    }

    /** Ícone redondo com nome, usado na fileira "Seus aplicativos" e na grade. */
    private View iconItem(final AppEntry a, final boolean updatesHero) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(Ui.px(14), Ui.px(14), Ui.px(14), Ui.px(10));

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setPadding(Ui.px(16), Ui.px(16), Ui.px(16), Ui.px(16));
        iv.setBackground(Ui.oval(0x33FFFFFF));
        iv.setImageDrawable(a.icon);
        Ui.circleClip(iv);
        item.addView(iv, new LinearLayout.LayoutParams(Ui.px(104), Ui.px(104)));

        TextView label = text(a.label, 18, Color.WHITE);
        label.setSingleLine();
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                Ui.px(130), ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.px(8);
        item.addView(label, llp);

        item.setFocusable(true);
        item.setClickable(true);
        Runnable onFocus = updatesHero ? new Runnable() {
            @Override
            public void run() {
                updateHero(a);
            }
        } : null;
        Ui.focusEffect(item, null, Ui.ring(0x22FFFFFF, 26, Ui.LUMINOUS, 3), false, onFocus);
        wireClicks(item, a);
        return item;
    }

    private void wireClicks(View v, final AppEntry a) {
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launch(a);
            }
        });
        v.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                showOptions(a);
                return true;
            }
        });
    }

    // ------------------------------------------------------------------ ações

    private void launch(AppEntry a) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setComponent(a.component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
            prefs.pushRecent(a.pkg);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void showOptions(final AppEntry a) {
        final String[] items = isFavorite(a.pkg)
                ? new String[]{"Remover dos favoritos", "Mover para o início", "Informações do app"}
                : new String[]{"Adicionar aos favoritos", "Informações do app"};
        new AlertDialog.Builder(this)
                .setTitle(a.label)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        String s = items[which];
                        if (s.startsWith("Remover") || s.startsWith("Adicionar")) {
                            toggleFavorite(a);
                        } else if (s.startsWith("Mover")) {
                            moveToFront(a);
                        } else {
                            openAppInfo(a);
                        }
                    }
                })
                .show();
    }

    private void openAppInfo(AppEntry a) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + a.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Esta TV não abre as informações do app", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ fundo

    private void showPersonalize() {
        final String[] items = new String[PRESET_NAMES.length + 1];
        System.arraycopy(PRESET_NAMES, 0, items, 0, PRESET_NAMES.length);
        items[PRESET_NAMES.length] = "Usar imagem de fundo";
        new AlertDialog.Builder(this)
                .setTitle("Fundo da tela inicial")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which < PRESETS.length) {
                            prefs.setBackground(which);
                            applyBackground();
                        } else {
                            useImage();
                        }
                    }
                })
                .show();
    }

    private File wallpaperFile() {
        File dir = getExternalFilesDir(null);
        return dir == null ? null : new File(dir, "wallpaper.jpg");
    }

    private void useImage() {
        File f = wallpaperFile();
        if (f != null && f.exists()) {
            prefs.setBackground(PRESETS.length);
            applyBackground();
            return;
        }
        String where = f == null ? "(armazenamento indisponível)" : f.getAbsolutePath();
        new AlertDialog.Builder(this)
                .setTitle("Imagem não encontrada")
                .setMessage("Copie uma imagem chamada wallpaper.jpg para esta pasta da TV:\n\n"
                        + where + "\n\nDepois abra esta opção de novo.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void applyBackground() {
        int mode = prefs.background();
        root.setBackgroundColor(PRESETS[mode >= PRESETS.length ? 0 : mode]);
        bgImage.setImageDrawable(null);
        bgImage.clearColorFilter();
        if (mode < PRESETS.length) {
            return;
        }
        final File f = wallpaperFile();
        if (f == null || !f.exists()) {
            return;
        }
        final int screenW = getResources().getDisplayMetrics().widthPixels;
        new Thread(new Runnable() {
            @Override
            public void run() {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                int sample = 1;
                while (o.outWidth / (sample * 2) >= screenW) {
                    sample *= 2;
                }
                o.inJustDecodeBounds = false;
                o.inSampleSize = sample;
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                final Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                if (bmp == null) {
                    return;
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        bgImage.setImageBitmap(bmp);
                        // Escurece um pouco para o texto continuar legível.
                        bgImage.setColorFilter(Color.argb(110, 0, 0, 0), PorterDuff.Mode.SRC_ATOP);
                    }
                });
            }
        }, "wallpaper").start();
    }
}
