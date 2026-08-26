package com.ashkanrafiee.regextractor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main screen: the user pastes sample text, highlights example parts, and
 * the app infers a regular expression with a live match preview.
 *
 * <p>The UI is built programmatically; highlighted selections live as
 * {@link BackgroundColorSpan}s on the editor, so they follow text edits
 * automatically.</p>
 */
public class MainActivity extends Activity {

    // Palette (dark theme)
    private static final int BG = Color.rgb(8, 13, 22);
    private static final int CARD = Color.rgb(17, 27, 43);
    private static final int CARD_ALT = Color.rgb(22, 34, 58);
    private static final int FG = Color.rgb(241, 245, 249);
    private static final int MUTED = Color.rgb(148, 163, 184);
    private static final int CYAN = Color.rgb(103, 232, 249);
    private static final int PURPLE = Color.rgb(167, 139, 250);

    /** Rotating highlight colors for successive selections. */
    private static final int[] SPAN_COLORS = {
            Color.rgb(103, 232, 249), Color.rgb(167, 139, 250), Color.rgb(52, 211, 153),
            Color.rgb(251, 191, 36), Color.rgb(251, 113, 133), Color.rgb(56, 189, 248)
    };

    private static final int MAX_SELECTIONS = 40;
    private static final int MAX_SHOWN_MATCHES = 100;
    private static final long UPDATE_DELAY_MS = 150;

    private EditText editor;
    private final List<BackgroundColorSpan> selectionSpans = new ArrayList<>();
    private TextView selectionsHint;
    private LinearLayout selectionChips;
    private TextView patternView;
    private LinearLayout resultCard;
    private TextView matchesHeader;
    private LinearLayout matchesList;
    private ToggleChip chipCase, chipMultiline, chipDotAll, chipGlobal;
    private Switch namedGroupsSwitch;

    private String currentPattern;
    private final android.os.Handler handler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updateTask = this::updateNow;

    /** One occurrence found by the live preview. */
    private static final class MatchRow {
        final String text;
        final int start;

        MatchRow(String text, int start) {
            this.text = text;
            this.start = start;
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        applySystemBarColors();
        ScrollView root = new ScrollView(this);
        root.setBackgroundColor(BG);
        root.setFillViewport(true);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int[] bars = systemBarInsets(insets);
            v.setPadding(0, bars[0], 0, bars[1] + dp(16));
            return insets;
        });

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(14), dp(20), dp(8));
        root.addView(content, new ScrollView.LayoutParams(-1, -2));

        buildHeader(content);
        buildSampleTextSection(content);
        buildSelectionsSection(content);
        buildOptionsSection(content);
        buildResultSection(content);
        setContentView(root);

        restoreState(state);
        handleIncomingIntent(getIntent());
        refreshOptionsFromPrefs();
        updateNow();
    }

    // ------------------------------------------------------------------
    // UI construction

    private void buildHeader(LinearLayout parent) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("Regextractor", 25, FG);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView about = text("About", 15, PURPLE);
        about.setPadding(dp(12), dp(10), dp(4), dp(10));
        about.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        header.addView(about);

        parent.addView(header);
        TextView tagline = text("Highlight examples in your text and get their regex.", 13, MUTED);
        tagline.setPadding(dp(2), 0, 0, 0);
        parent.addView(tagline, margin(0, 0, 0, 18));
    }

    private void buildSampleTextSection(LinearLayout parent) {
        parent.addView(sectionLabel("SAMPLE TEXT"));

        editor = new EditText(this);
        editor.setHint("Paste or type your sample text here");
        editor.setTextColor(FG);
        editor.setHintTextColor(Color.rgb(100, 116, 139));
        editor.setTextSize(14);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setBackground(rounded(CARD, 15));
        editor.setPadding(dp(14), dp(12), dp(14), dp(12));
        editor.setMinimumHeight(dp(120));
        parent.addView(editor, margin(0, 0, 0, 10));

        editor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                pruneDeadSpans(s);
                scheduleUpdate();
            }
        });

        parent.addView(presetStrip(), margin(0, 0, 0, 16));
    }

    /** Horizontally scrolling row of one-tap example texts plus clipboard/clear actions. */
    private View presetStrip() {
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);

        LinearLayout row = new LinearLayout(this);
        String[][] presets = {
                {"Log lines", "2024-01-15 09:30:12 INFO User alice logged in\n"
                        + "2024-01-15 09:31:47 WARN Disk usage at 91%\n"
                        + "2024-01-15 09:33:05 INFO User bob logged out"},
                {"Dates", "Invoice issued on 2024-03-01.\n"
                        + "Payment due by 2024-04-15.\n"
                        + "Reminder sent on 2023-12-08."},
                {"Emails", "Contact john@example.com for details\n"
                        + "or write to jane.doe@example.org instead."},
                {"Prices", "Coffee - $3.50\nSandwich - $12.00\nJuice - $4.25"},
                {"IDs", "user-101 signed up\nteam-42 merged\nrepo-7 archived"}
        };
        for (String[] preset : presets) {
            row.addView(pill(preset[0], FG, v -> offerPreset(preset[1])));
        }
        row.addView(pill("Paste", PURPLE, v -> pasteFromClipboard()));
        row.addView(pill("Clear all", MUTED, v -> confirmClear()));

        strip.addView(row);
        return strip;
    }

    private void buildSelectionsSection(LinearLayout parent) {
        parent.addView(sectionLabel("HIGHLIGHTED PARTS"));

        selectionsHint = text("Long-press the text above to select a part,"
                + " then tap \u201CAdd selected text\u201D.", 13, MUTED);
        selectionsHint.setPadding(dp(4), 0, dp(4), 0);
        parent.addView(selectionsHint, margin(0, 0, 0, 8));

        selectionChips = new LinearLayout(this);
        selectionChips.setOrientation(LinearLayout.VERTICAL);
        parent.addView(selectionChips, margin(0, 0, 0, 6));

        TextView add = text("+ Add selected text", 15, BG);
        add.setTypeface(null, Typeface.BOLD);
        add.setGravity(Gravity.CENTER);
        add.setBackground(rounded(CYAN, 24));
        add.setMinimumHeight(dp(48));
        add.setOnClickListener(v -> addSelectionFromEditor());
        parent.addView(add, margin(0, 6, 0, 16));
    }

    private void buildOptionsSection(LinearLayout parent) {
        parent.addView(sectionLabel("OPTIONS"));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(CARD, 15));
        card.setPadding(dp(12), dp(12), dp(12), dp(6));

        LinearLayout toggles = new LinearLayout(this);
        chipCase = new ToggleChip(toggles, "Aa", "Ignore letter case", "case_insensitive", false);
        chipMultiline = new ToggleChip(toggles, "^$", "^ and $ also match line breaks",
                "multiline", false);
        chipDotAll = new ToggleChip(toggles, ".*", "Dot also matches line breaks",
                "dot_all", false);
        chipGlobal = new ToggleChip(toggles, "\u221E", "Find every occurrence, not just the first",
                "global", true);
        card.addView(toggles, margin(0, 0, 0, 4));

        LinearLayout namedRow = new LinearLayout(this);
        namedRow.setGravity(Gravity.CENTER_VERTICAL);
        namedRow.setPadding(dp(4), dp(4), dp(4), dp(4));
        TextView namedLabel = text("Named capture groups", 14, FG);
        namedLabel.setContentDescription("Named capture groups");
        namedRow.addView(namedLabel, new LinearLayout.LayoutParams(0, -2, 1));
        namedGroupsSwitch = new Switch(this);
        namedGroupsSwitch.setOnCheckedChangeListener((button, on) -> {
            persistOption("named_groups", on);
            updateNow();
        });
        namedRow.addView(namedGroupsSwitch);
        card.addView(namedRow);

        parent.addView(card, margin(0, 0, 0, 16));
    }

    private void buildResultSection(LinearLayout parent) {
        resultCard = new LinearLayout(this);
        resultCard.setOrientation(LinearLayout.VERTICAL);
        resultCard.setBackground(rounded(CARD_ALT, 15));
        resultCard.setPadding(dp(14), dp(12), dp(14), dp(14));

        resultCard.addView(sectionLabel("PATTERN"));
        patternView = new TextView(this);
        patternView.setTypeface(Typeface.MONOSPACE);
        patternView.setTextSize(14);
        patternView.setTextColor(CYAN);
        patternView.setTextIsSelectable(true); // long-press copy for free
        resultCard.addView(patternView, margin(0, 2, 0, 12));

        LinearLayout actions = new LinearLayout(this);
        actions.addView(actionButton("Copy", CYAN, v -> copyPattern()));
        actions.addView(actionButton("Share", PURPLE, v -> sharePattern()));
        resultCard.addView(actions);

        parent.addView(resultCard, margin(0, 0, 0, 16));

        matchesHeader = sectionLabel("MATCHES");
        parent.addView(matchesHeader, margin(0, 0, 0, 8));
        matchesList = new LinearLayout(this);
        matchesList.setOrientation(LinearLayout.VERTICAL);
        parent.addView(matchesList);
    }

    // ------------------------------------------------------------------
    // Selection handling

    private void addSelectionFromEditor() {
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start < 0 || end <= start) {
            toast("Select a part of the text first");
            return;
        }
        if (selectionSpans.size() >= MAX_SELECTIONS) {
            toast("At most " + MAX_SELECTIONS + " selections are supported");
            return;
        }
        Editable text = editor.getText();
        for (BackgroundColorSpan existing : selectionSpans) {
            if (start < text.getSpanEnd(existing) && text.getSpanStart(existing) < end) {
                toast("Selections must not overlap");
                return;
            }
        }

        BackgroundColorSpan span = new BackgroundColorSpan(
                withAlpha(SPAN_COLORS[selectionSpans.size() % SPAN_COLORS.length], 70));
        text.setSpan(span, start, end, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        selectionSpans.add(span);
        editor.setSelection(editor.length()); // free the handles for the next pick
        refreshSelectionUi();
        updateNow();
    }

    private void removeSelection(int index) {
        if (index < 0 || index >= selectionSpans.size()) return;
        BackgroundColorSpan span = selectionSpans.remove(index);
        editor.getText().removeSpan(span);
        refreshSelectionUi();
        updateNow();
    }

    /** Drops spans that collapsed to nothing after edits, then re-renders chips. */
    private void pruneDeadSpans(Editable text) {
        for (int i = selectionSpans.size() - 1; i >= 0; i--) {
            BackgroundColorSpan span = selectionSpans.get(i);
            if (text.getSpanStart(span) < 0 || text.getSpanEnd(span) <= text.getSpanStart(span)) {
                text.removeSpan(span);
                selectionSpans.remove(i);
            }
        }
    }

    /** Re-renders the chip list from the live spans (positions follow edits). */
    private void refreshSelectionUi() {
        selectionChips.removeAllViews();
        boolean any = !selectionSpans.isEmpty();
        selectionsHint.setVisibility(any ? View.GONE : View.VISIBLE);

        Editable text = editor.getText();
        for (int i = 0; i < selectionSpans.size(); i++) {
            BackgroundColorSpan span = selectionSpans.get(i);
            int start = text.getSpanStart(span);
            int end = text.getSpanEnd(span);
            if (start < 0 || end <= start) continue;
            String snippet = text.subSequence(start, end).toString();
            int accent = SPAN_COLORS[i % SPAN_COLORS.length];
            selectionChips.addView(selectionChip(i, snippet, accent), margin(0, 0, 0, 6));
        }
    }

    private View selectionChip(final int index, String snippet, int accent) {
        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(rounded(CARD, 20));
        chip.setPadding(dp(14), 0, dp(8), 0);
        chip.setMinimumHeight(dp(40));
        chip.setOnClickListener(v -> { // re-select in the editor for easy tweaking
            BackgroundColorSpan span = selectionSpans.get(index);
            editor.requestFocus();
            editor.setSelection(editor.getText().getSpanStart(span),
                    editor.getText().getSpanEnd(span));
        });

        View dot = new View(this);
        dot.setBackground(rounded(accent, 7));
        LinearLayout.LayoutParams dotLayout = new LinearLayout.LayoutParams(dp(14), dp(14));
        dotLayout.rightMargin = dp(10);
        chip.addView(dot, dotLayout);

        TextView label = text("\u201C" + ellipsize(snippet, 32) + "\u201D", 13, FG);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        TextView remove = text("\u00D7", 18, MUTED);
        remove.setGravity(Gravity.CENTER);
        remove.setPadding(dp(14), dp(10), dp(14), dp(10));
        remove.setContentDescription("Remove selection " + (index + 1));
        remove.setOnClickListener(v -> removeSelection(index));
        chip.addView(remove);
        return chip;
    }

    // ------------------------------------------------------------------
    // Pattern generation and live preview

    private void scheduleUpdate() {
        handler.removeCallbacks(updateTask);
        handler.postDelayed(updateTask, UPDATE_DELAY_MS);
    }

    private void updateNow() {
        handler.removeCallbacks(updateTask);
        refreshSelectionUi();

        List<String> examples = new ArrayList<>();
        Editable text = editor.getText();
        for (BackgroundColorSpan span : selectionSpans) {
            int start = text.getSpanStart(span);
            int end = text.getSpanEnd(span);
            if (start >= 0 && end > start && end <= text.length()) {
                examples.add(text.subSequence(start, end).toString());
            }
        }

        if (examples.isEmpty()) {
            currentPattern = null;
            resultCard.setVisibility(View.GONE);
            matchesHeader.setVisibility(View.GONE);
            matchesList.removeAllViews();
            return;
        }

        RegexBuilder.Options options = optionsFromState();
        currentPattern = RegexBuilder.build(examples, options);

        resultCard.setVisibility(View.VISIBLE);
        patternView.setText(currentPattern);
        refreshMatches(options);
    }

    private RegexBuilder.Options optionsFromState() {
        RegexBuilder.Options options = new RegexBuilder.Options();
        options.caseInsensitive = prefs().getBoolean("case_insensitive", false);
        options.multiline = prefs().getBoolean("multiline", false);
        options.dotAll = prefs().getBoolean("dot_all", false);
        options.namedGroups = prefs().getBoolean("named_groups", false);
        return options;
    }

    /**
     * Compiles the current pattern against the sample text and lists what it
     * finds. Inline flags in the pattern itself already encode the toggles,
     * so no extra compile flags are needed here.
     */
    private void refreshMatches(RegexBuilder.Options options) {
        matchesList.removeAllViews();
        boolean global = prefs().getBoolean("global", true);

        List<MatchRow> found = new ArrayList<>();
        int total = 0;
        try {
            Matcher matcher = Pattern.compile(currentPattern)
                    .matcher(shortTextForPreview());
            while (matcher.find()) {
                total++;
                if (found.size() < MAX_SHOWN_MATCHES) {
                    found.add(new MatchRow(matcher.group(), matcher.start()));
                }
                if (matcher.start() == matcher.end()) break; // safety against empty matches
            }
        } catch (RuntimeException broken) {
            matchesHeader.setText("MATCHES");
            matchesList.addView(noteRow("The pattern could not be applied."));
            return;
        }

        List<MatchRow> shown = global || found.isEmpty() ? found : found.subList(0, 1);
        matchesHeader.setText(global
                ? "MATCHES (" + total + ")"
                : "FIRST MATCH OF " + total);

        if (shown.isEmpty()) {
            matchesList.addView(noteRow(
                    "No matches yet \u2014 widen your selections or relax the options."));
            return;
        }
        for (MatchRow row : shown) matchesList.addView(matchRow(row, row == shown.get(0)));
        if (total > shown.size()) {
            matchesList.addView(noteRow("+ " + (total - shown.size()) + " more not listed"));
        }
    }

    private String shortTextForPreview() {
        Editable text = editor.getText();
        return text.subSequence(0, Math.min(text.length(), 200_000)).toString();
    }

    private View matchRow(MatchRow row, boolean first) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setBackground(rounded(CARD, 12));
        item.setPadding(dp(14), 0, dp(14), 0);
        item.setMinimumHeight(dp(44));
        item.setOnClickListener(v -> copyText(row.text));

        TextView value = text(row.text, 13, first ? CYAN : FG);
        value.setTypeface(Typeface.MONOSPACE);
        value.setMaxLines(1);
        value.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        item.addView(value, new LinearLayout.LayoutParams(0, -2, 1));

        TextView position = text("@" + row.start, 11, MUTED);
        position.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams positionLayout =
                new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        positionLayout.leftMargin = dp(10);
        item.addView(position, positionLayout);
        return item;
    }

    // ------------------------------------------------------------------
    // Actions

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = clipboard != null ? clipboard.getPrimaryClip() : null;
        if (clip == null || clip.getItemCount() == 0 || clip.getItemAt(0).getText() == null) {
            toast("Clipboard is empty");
            return;
        }
        offerReplace(clip.getItemAt(0).getText().toString(), "Paste from clipboard?");
    }

    private void offerPreset(String sample) {
        if (editor.length() == 0) setSampleText(sample);
        else offerReplace(sample, "Replace current text?");
    }

    private void offerReplace(String replacement, String title) {
        if (editor.length() == 0) {
            setSampleText(replacement);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("Your sample text and selections will be cleared.")
                .setPositiveButton("Replace", (d, w) -> setSampleText(replacement))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setSampleText(String sample) {
        clearAll();
        editor.setText(sample);
    }

    private void confirmClear() {
        if (editor.length() == 0 && selectionSpans.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Clear everything?")
                .setMessage("This removes the sample text and all selections.")
                .setPositiveButton("Clear", (d, w) -> clearAll())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearAll() {
        editor.getText().clear(); // pruneDeadSpans empties the list alongside
        refreshSelectionUi();
        updateNow();
    }

    private void copyPattern() {
        copyText(currentPattern);
        toast("Pattern copied");
    }

    private void sharePattern() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, currentPattern);
        startActivity(Intent.createChooser(send, "Share pattern"));
    }

    private void copyText(String value) {
        if (value == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Regextractor", value));
        toast(value.length() > 40
                ? "Copied " + value.length() + " characters"
                : "Copied \u201C" + ellipsize(value, 40) + "\u201D");
    }

    // ------------------------------------------------------------------
    // Incoming text (share-to-app plus a small automation hook for testing)

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (shared != null && shared.length() > 0) {
                offerReplace(shared.toString(), "Use shared text?");
            }
        }
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (shared != null && shared.length() > 0) editor.setText(shared);
            return;
        }
        String sample = intent.getStringExtra("sample_text");
        if (sample != null) editor.setText(sample);
        int[] pairs = intent.getIntArrayExtra("demo_selections");
        if (sample != null && pairs != null) {
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                editor.requestFocus();
                editor.setSelection(pairs[i], Math.min(pairs[i + 1], sample.length()));
                addSelectionFromEditor();
            }
        }
    }

    // ------------------------------------------------------------------
    // State

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharSequence("text", editor.getText());
        Editable text = editor.getText();
        int[] ranges = new int[selectionSpans.size() * 2];
        for (int i = 0; i < selectionSpans.size(); i++) {
            ranges[i * 2] = text.getSpanStart(selectionSpans.get(i));
            ranges[i * 2 + 1] = text.getSpanEnd(selectionSpans.get(i));
        }
        outState.putIntArray("ranges", ranges);
    }

    private void restoreState(Bundle state) {
        if (state == null) return;
        CharSequence text = state.getCharSequence("text");
        int[] ranges = state.getIntArray("ranges");
        if (text == null || ranges == null) return;
        editor.setText(text);
        for (int i = 0; i + 1 < ranges.length; i += 2) {
            int start = Math.max(0, Math.min(ranges[i], text.length()));
            int end = Math.max(start, Math.min(ranges[i + 1], text.length()));
            if (end <= start) continue;
            BackgroundColorSpan span = new BackgroundColorSpan(withAlpha(
                    SPAN_COLORS[selectionSpans.size() % SPAN_COLORS.length], 70));
            editor.getText().setSpan(span, start, end,
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            selectionSpans.add(span);
        }
    }

    /** Loads persisted option values into the toggle pills and the switch. */
    private void refreshOptionsFromPrefs() {
        chipCase.sync();
        chipMultiline.sync();
        chipDotAll.sync();
        chipGlobal.sync();
        namedGroupsSwitch.setChecked(prefs().getBoolean("named_groups", false));
    }

    private void persistOption(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
    }

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences("regextractor_prefs", MODE_PRIVATE);
    }

    // ------------------------------------------------------------------
    // Small view helpers

    /** Dark status/navigation bars; still the simplest API for this design. */
    @SuppressWarnings("deprecation")
    private void applySystemBarColors() {
        getWindow().setStatusBarColor(Color.rgb(11, 18, 32));
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

    private TextView sectionLabel(String title) {
        TextView label = text(title, 12, MUTED);
        label.setLetterSpacing(0.12f);
        label.setPadding(dp(4), 0, dp(4), dp(8));
        return label;
    }

    private TextView pill(String label, int textColor, View.OnClickListener onClick) {
        TextView view = text(label, 13, textColor);
        view.setBackground(outlined(CARD_ALT, 20, MUTED));
        view.setPadding(dp(14), dp(9), dp(14), dp(9));
        view.setOnClickListener(onClick);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2, -2);
        layout.rightMargin = dp(8);
        view.setLayoutParams(layout);
        return view;
    }

    private TextView actionButton(String label, int accent, View.OnClickListener onClick) {
        TextView view = text(label, 14, BG);
        view.setTypeface(null, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(accent, 20));
        view.setPadding(dp(24), 0, dp(24), 0);
        view.setMinimumHeight(dp(42));
        view.setOnClickListener(onClick);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2, -2);
        layout.rightMargin = dp(10);
        view.setLayoutParams(layout);
        return view;
    }

    private TextView noteRow(String message) {
        TextView note = text(message, 13, MUTED);
        note.setPadding(dp(4), dp(4), dp(4), dp(8));
        return note;
    }

    /**
     * A pill that reflects and flips one persisted boolean option. Extends
     * {@link android.widget.CheckedTextView} so assistive technologies read
     * its on/off state.
     */
    private final class ToggleChip extends android.widget.CheckedTextView {
        private final String key;
        private final boolean defaultOn;

        ToggleChip(LinearLayout row, String symbol, String accessibilityDescription,
                   String prefKey, boolean defaultOn) {
            super(MainActivity.this);
            this.key = prefKey;
            this.defaultOn = defaultOn;
            setText(symbol);
            setTextSize(13);
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            setGravity(Gravity.CENTER);
            setMinimumWidth(dp(46));
            setMinimumHeight(dp(38));
            setCheckMarkDrawable(null);
            setContentDescription(accessibilityDescription);
            setOnClickListener(v -> {
                persistOption(key, !isOn());
                sync();
                updateNow();
            });
            sync();
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2, -2);
            layout.rightMargin = dp(8);
            row.addView(this, layout);
        }

        boolean isOn() {
            return prefs().getBoolean(key, defaultOn);
        }

        void sync() {
            boolean on = isOn();
            setChecked(on);
            if (on) {
                setBackground(rounded(CYAN, 20));
                setTextColor(BG);
            } else {
                setBackground(outlined(CARD_ALT, 20, MUTED));
                setTextColor(MUTED);
            }
        }
    }

    // ------------------------------------------------------------------
    // Utilities

    private static String ellipsize(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "\u2026";
    }

    private static int withAlpha(int base, int alpha) {
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base));
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

    private GradientDrawable outlined(int fillColor, float radius, int strokeColor) {
        GradientDrawable drawable = rounded(fillColor, radius);
        drawable.setStroke(dp(2), strokeColor);
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

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
