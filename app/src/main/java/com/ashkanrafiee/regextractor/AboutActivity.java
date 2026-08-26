package com.ashkanrafiee.regextractor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * About screen in the same style as the author's other apps: a hero card,
 * a short description, tappable info rows and a version footer read live
 * from the package manager.
 */
public class AboutActivity extends Activity {

    private static final int BG = Color.rgb(8, 13, 22);
    private static final int CARD = Color.rgb(17, 27, 43);
    private static final int HERO_CARD = Color.rgb(31, 42, 74);
    private static final int MUTED = Color.rgb(148, 163, 184);
    private static final int LINK = Color.rgb(190, 184, 255);
    private static final int CYAN = Color.rgb(103, 232, 249);

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        applySystemBarColors();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int[] bars = systemBarInsets(insets);
            v.setPadding(dp(20), bars[0] + dp(14), dp(20), bars[1] + dp(14));
            return insets;
        });
        setContentView(root);

        root.addView(topBar());
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -1));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        body.addView(heroCard(), margin(0, 0, 0, 22));
        body.addView(section("ABOUT THIS APP",
                "Regextractor builds regular expressions from examples. Paste any "
                        + "text, highlight the parts you want to capture, and it infers "
                        + "a pattern you can copy into your code \u2014 with a live preview "
                        + "of every match."));
        body.addView(infoRow("Created by", "Ashkan Rafiee", "https://AshkanRafiee.com/"),
                margin(0, 0, 0, 8));
        body.addView(infoRow("License", "GNU General Public License v3.0",
                "https://github.com/AshkanRafiee/regextractor/blob/main/LICENSE"),
                margin(0, 0, 0, 8));
        body.addView(infoRow("Source code", "github.com/ashkanrafiee/regextractor",
                "https://github.com/AshkanRafiee/regextractor"), margin(0, 0, 0, 8));
        body.addView(infoRow("Privacy", "Offline by design \u00b7 no cloud \u00b7 no account",
                null), margin(0, 0, 0, 20));

        TextView footer = text("Regextractor \u00b7 Version " + appVersion(), 11,
                Color.rgb(103, 115, 136));
        footer.setGravity(Gravity.CENTER);
        body.addView(footer);
    }

    // ------------------------------------------------------------------
    // Pieces

    private LinearLayout topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("\u2039", 34, Color.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(42), dp(48)));

        bar.addView(text("About", 21, Color.WHITE), margin(10, 0, 0, 0));
        return bar;
    }

    private LinearLayout heroCard() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(20), dp(24), dp(20), dp(24));
        hero.setBackground(rounded(HERO_CARD, 22));

        TextView mark = text("R", 25, BG);
        mark.setGravity(Gravity.CENTER);
        mark.setTypeface(null, Typeface.BOLD);
        mark.setBackground(rounded(CYAN, 16));
        mark.setContentDescription("Regextractor icon");
        hero.addView(mark, new LinearLayout.LayoutParams(dp(56), dp(56)));

        TextView title = text("Regextractor", 24, Color.WHITE);
        title.setPadding(0, dp(14), 0, dp(2));
        hero.addView(title);
        hero.addView(text("Regex builder from examples", 13, Color.rgb(201, 211, 230)));
        return hero;
    }

    private TextView section(String heading, String bodyText) {
        TextView view = text(heading + "\n" + bodyText, 12, MUTED);
        view.setLineSpacing(2, 1.05f);
        return view;
    }

    /** An info card; opens {@code url} in a browser when one is given. */
    private LinearLayout infoRow(String heading, String value, String url) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(13), dp(16), dp(13));
        box.setBackground(rounded(CARD, 15));
        box.addView(text(heading, 12, MUTED));

        TextView line = text(value, 14, url != null ? LINK : Color.WHITE);
        line.setPadding(0, dp(5), 0, 0);
        line.setMaxLines(2);
        line.setEllipsize(TextUtils.TruncateAt.END);
        if (url != null) {
            line.setOnClickListener(view ->
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
        }
        box.addView(line);
        return box;
    }

    // ------------------------------------------------------------------
    // Helpers

    /** Dark status/navigation bars; still the simplest API for this design. */
    @SuppressWarnings("deprecation")
    private void applySystemBarColors() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
    }

    /** Top and bottom system bar sizes as {top, bottom}. */
    @SuppressWarnings("deprecation")
    private int[] systemBarInsets(android.view.WindowInsets insets) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars =
                    insets.getInsets(android.view.WindowInsets.Type.systemBars());
            return new int[]{bars.top, bars.bottom};
        }
        return new int[]{insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetBottom()};
    }

    /** Read at display time so the About screen never shows a stale version. */
    @SuppressWarnings("deprecation")
    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception unavailable) {
            return "unknown";
        }
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable rounded(int fillColor, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
}
