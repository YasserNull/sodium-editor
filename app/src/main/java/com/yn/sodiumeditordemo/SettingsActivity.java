package com.yn.sodiumeditordemo;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

  private SharedPreferences prefs;
  private TextView themeSummary;
  private TextView showLineNumbersSummary;
  private TextView wordWrapSummary;
  private TextView scrollModeSummary;
  private TextView keyboardSuggestionsSummary;
  private TextView performanceModeSummary;
  private TextView bracketGuidesSummary;
  private TextView bracketMatchSummary;
  private TextView whitespaceGuidesSummary;
  private TextView indentGuidesSummary;
  private TextView binaryRenderSummary;
  private TextView binarySafeRenderingSummary;
  private TextView binaryHexTokensSummary;
  private TextView binaryCaretNotationSummary;
  private TextView binaryTokenBoxSummary;
  private final java.util.List<Runnable> dynamicSummaries = new java.util.ArrayList<>();

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    String theme = PreferenceManager.getDefaultSharedPreferences(this).getString("theme", "light");
    applyTheme(theme);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_settings);
    applySystemWindowInsets(findViewById(R.id.root));
    styleSystemBars(theme);
    prefs = PreferenceManager.getDefaultSharedPreferences(this);

    Toolbar toolbar = findViewById(R.id.settingsToolbar);
    styleToolbar(toolbar, theme);
    toolbar.setNavigationOnClickListener(v -> finish());

    LinearLayout settingsList = findViewById(R.id.settingsList);

    addSectionHeader(settingsList, "Appearance");
    themeSummary =
        addSettingsRow(
            settingsList,
            "Theme",
            v ->
                showListSetting(
                    "Theme",
                    "theme",
                    getResources().getStringArray(R.array.theme_labels),
                    getResources().getStringArray(R.array.theme_values),
                    "light"));
    showLineNumbersSummary =
        addSettingsRow(
            settingsList,
            "Show Line Numbers",
            v -> toggleBooleanSetting("show_line_numbers", true));
    wordWrapSummary =
        addSettingsRow(settingsList, "Word Wrap", v -> toggleBooleanSetting("word_wrap", false));

    addSectionHeader(settingsList, "Guides");
    bracketGuidesSummary =
        addSettingsRow(
            settingsList, "Bracket Guides", v -> toggleBooleanSetting("bracket_guides", true));
    bracketMatchSummary =
        addSettingsRow(
            settingsList, "Bracket Match", v -> toggleBooleanSetting("bracket_match", true));
    whitespaceGuidesSummary =
        addSettingsRow(
            settingsList,
            "Whitespace Guides",
            v -> toggleBooleanSetting("whitespace_guides", true));
    indentGuidesSummary =
        addSettingsRow(
            settingsList, "Indent Guides", v -> toggleBooleanSetting("indent_guides", true));

    addSectionHeader(settingsList, "Editing");
    keyboardSuggestionsSummary =
        addSettingsRow(settingsList, "Keyboard Suggestions", v -> toggleKeyboardSuggestions());

    addSectionHeader(settingsList, "Scrolling & Zoom");
    scrollModeSummary =
        addSettingsRow(
            settingsList,
            "Scroll Mode",
            v ->
                showListSetting(
                    "Scroll Mode",
                    "scroll_mode",
                    getResources().getStringArray(R.array.scroll_mode_labels),
                    getResources().getStringArray(R.array.scroll_mode_values),
                    "2"));

    addSectionHeader(settingsList, "Binary");
    binaryRenderSummary =
        addSettingsRow(
            settingsList,
            "Binary Render",
            v -> toggleBooleanSetting("binary_render", true));
    binarySafeRenderingSummary =
        addSettingsRow(
            settingsList,
            "Binary Safe Rendering",
            v -> toggleBooleanSetting("binary_safe_rendering", true));
    binaryHexTokensSummary =
        addSettingsRow(
            settingsList, "Binary Hex Tokens", v -> toggleBooleanSetting("binary_hex_tokens", true));
    binaryCaretNotationSummary =
        addSettingsRow(
            settingsList,
            "Binary Caret Notation",
            v -> toggleBooleanSetting("binary_caret_notation", false));
    binaryTokenBoxSummary =
        addSettingsRow(
            settingsList,
            "Binary Token Box",
            v -> toggleBooleanSetting("binary_token_box", true));

    addSectionHeader(settingsList, "Performance");
    performanceModeSummary =
        addSettingsRow(settingsList, "Performance Mode", v -> togglePerformanceMode());

    String currentSection = null;
    for (EditorSettings.Item item : EditorSettings.ITEMS) {
      if (!item.section.equals(currentSection)) {
        currentSection = item.section;
        addSectionHeader(settingsList, currentSection);
      }
      final EditorSettings.Item it = item;
      TextView summary =
          addSettingsRow(
              settingsList,
              it.title,
              v -> {
                if (it.isBool) {
                  toggleBooleanSetting(it.key, it.defBool);
                } else if (it.strKind != 0) {
                  showTextSetting(it);
                } else {
                  showNumberSetting(it);
                }
              });
      dynamicSummaries.add(
          () ->
              summary.setText(
                  it.isBool
                      ? (prefs.getBoolean(it.key, it.defBool) ? "On" : "Off")
                      : it.strKind != 0
                          ? (prefs.getString(it.key, "").isEmpty()
                              ? "Default"
                              : prefs.getString(it.key, ""))
                          : EditorSettings.formatNum(EditorSettings.getNum(prefs, it))));
    }
    updateSummaries();
  }

  private void applyTheme(String themeValue) {
    switch (themeValue) {
      case "dark":
        setTheme(R.style.AppTheme_Dark);
        break;
      case "black":
        setTheme(R.style.AppTheme_Black);
        break;
      default:
        setTheme(R.style.AppTheme_White);
        break;
    }
  }

  private void addSectionHeader(LinearLayout parent, String title) {
    TextView header = new TextView(this);
    header.setText(title);
    header.setTextSize(14);
    header.setAllCaps(true);
    header.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    header.setTextColor(resolveTextColor(android.R.attr.textColorSecondary));
    int paddingTop = dp(16);
    int paddingBottom = dp(4);
    int paddingStart = dp(20);
    int paddingEnd = dp(20);
    header.setPadding(paddingStart, paddingTop, paddingEnd, paddingBottom);
    parent.addView(
        header,
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
  }

  private TextView addSettingsRow(
      LinearLayout parent, String title, View.OnClickListener listener) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
    row.setPadding(dp(20), dp(14), dp(20), dp(14));
    row.setMinimumHeight(dp(64));
    row.setBackgroundResource(resolveSelectableItemBackground());
    row.setOnClickListener(listener);

    LinearLayout textContainer = new LinearLayout(this);
    textContainer.setOrientation(LinearLayout.VERTICAL);

    TextView titleView = new TextView(this);
    titleView.setText(title);
    titleView.setTextSize(16);
    titleView.setTextColor(resolveTextColor(android.R.attr.textColorPrimary));
    textContainer.addView(titleView);

    TextView summaryView = new TextView(this);
    summaryView.setTextSize(14);
    summaryView.setTextColor(resolveTextColor(android.R.attr.textColorSecondary));
    textContainer.addView(summaryView);

    row.addView(
        textContainer,
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    parent.addView(
        row,
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    return summaryView;
  }

  private void showListSetting(
      String title, String key, String[] labels, String[] values, String defaultValue) {
    String currentValue = prefs.getString(key, defaultValue);
    int selected = indexOf(values, currentValue);
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setSingleChoiceItems(
            labels,
            selected,
            (dialog, which) -> {
              prefs.edit().putString(key, values[which]).apply();
              dialog.dismiss();
              updateSummaries();
              if ("theme".equals(key)) {
                recreate();
              }
            })
        .show();
  }

  private void updateSummaries() {
    themeSummary.setText(
        labelForValue(
            getResources().getStringArray(R.array.theme_labels),
            getResources().getStringArray(R.array.theme_values),
            prefs.getString("theme", "light")));
    showLineNumbersSummary.setText(
        prefs.getBoolean("show_line_numbers", true) ? "On" : "Off");
    wordWrapSummary.setText(prefs.getBoolean("word_wrap", false) ? "On" : "Off");
    scrollModeSummary.setText(
        labelForValue(
            getResources().getStringArray(R.array.scroll_mode_labels),
            getResources().getStringArray(R.array.scroll_mode_values),
            prefs.getString("scroll_mode", "2")));
    keyboardSuggestionsSummary.setText(
        prefs.getBoolean("keyboard_suggestions", true) ? "On" : "Off");
    bracketGuidesSummary.setText(prefs.getBoolean("bracket_guides", true) ? "On" : "Off");
    bracketMatchSummary.setText(prefs.getBoolean("bracket_match", true) ? "On" : "Off");
    whitespaceGuidesSummary.setText(prefs.getBoolean("whitespace_guides", true) ? "On" : "Off");
    indentGuidesSummary.setText(prefs.getBoolean("indent_guides", true) ? "On" : "Off");
    binaryRenderSummary.setText(prefs.getBoolean("binary_render", true) ? "On" : "Off");
    binarySafeRenderingSummary.setText(
        prefs.getBoolean("binary_safe_rendering", true) ? "On" : "Off");
    binaryHexTokensSummary.setText(prefs.getBoolean("binary_hex_tokens", true) ? "On" : "Off");
    binaryCaretNotationSummary.setText(
        prefs.getBoolean("binary_caret_notation", false) ? "On" : "Off");
    binaryTokenBoxSummary.setText(prefs.getBoolean("binary_token_box", true) ? "On" : "Off");
    performanceModeSummary.setText(prefs.getBoolean("performance_mode", false) ? "On" : "Off");
    for (Runnable r : dynamicSummaries) {
      r.run();
    }
  }

  private void showTextSetting(EditorSettings.Item item) {
    android.widget.EditText input = new android.widget.EditText(this);
    input.setSingleLine(true);
    input.setHint(item.strKind == 1 ? "#RRGGBB or #AARRGGBB" : "/path/to/file");
    input.setText(prefs.getString(item.key, ""));
    input.setSelection(input.getText().length());
    new AlertDialog.Builder(this)
        .setTitle(item.title)
        .setView(input)
        .setPositiveButton(
            "OK",
            (d, w) -> {
              String text = input.getText().toString().trim();
              if (!text.isEmpty()
                  && item.strKind == 1
                  && EditorSettings.parseColor(text) == null) {
                android.widget.Toast.makeText(
                        this, "Invalid color", android.widget.Toast.LENGTH_SHORT)
                    .show();
                return;
              }
              prefs.edit().putString(item.key, text).apply();
              updateSummaries();
            })
        .setNeutralButton(
            "Reset",
            (d, w) -> {
              prefs.edit().remove(item.key).apply();
              updateSummaries();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void showNumberSetting(EditorSettings.Item item) {
    android.widget.EditText input = new android.widget.EditText(this);
    input.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    input.setText(EditorSettings.formatNum(EditorSettings.getNum(prefs, item)));
    input.setSelection(input.getText().length());
    new AlertDialog.Builder(this)
        .setTitle(item.title)
        .setMessage("Default: " + EditorSettings.formatNum(item.defNum))
        .setView(input)
        .setPositiveButton(
            "OK",
            (d, w) -> {
              String text = input.getText().toString().trim();
              if (text.isEmpty()) {
                prefs.edit().remove(item.key).apply();
              } else {
                try {
                  double v = Double.parseDouble(text);
                  if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) {
                    throw new NumberFormatException();
                  }
                  prefs.edit().putString(item.key, EditorSettings.formatNum(v)).apply();
                } catch (NumberFormatException ex) {
                  android.widget.Toast.makeText(this, "Invalid number", android.widget.Toast.LENGTH_SHORT)
                      .show();
                }
              }
              updateSummaries();
            })
        .setNeutralButton(
            "Reset",
            (d, w) -> {
              prefs.edit().remove(item.key).apply();
              updateSummaries();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void toggleKeyboardSuggestions() {
    boolean enabled = !prefs.getBoolean("keyboard_suggestions", true);
    prefs.edit().putBoolean("keyboard_suggestions", enabled).apply();
    updateSummaries();
  }

  private void togglePerformanceMode() {
    boolean enabled = !prefs.getBoolean("performance_mode", false);
    prefs.edit().putBoolean("performance_mode", enabled).apply();
    updateSummaries();
  }

  private void toggleBooleanSetting(String key, boolean defaultValue) {
    boolean enabled = !prefs.getBoolean(key, defaultValue);
    prefs.edit().putBoolean(key, enabled).apply();
    updateSummaries();
  }

  private String labelForValue(String[] labels, String[] values, String value) {
    int index = indexOf(values, value);
    return index >= 0 ? labels[index] : "";
  }

  private int indexOf(String[] values, String value) {
    for (int i = 0; i < values.length; i++) {
      if (values[i].equals(value)) {
        return i;
      }
    }
    return -1;
  }

  private int resolveSelectableItemBackground() {
    android.util.TypedValue outValue = new android.util.TypedValue();
    getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
    return outValue.resourceId;
  }

  private int resolveTextColor(int attr) {
    android.content.res.TypedArray typedArray = getTheme().obtainStyledAttributes(new int[] {attr});
    try {
      return typedArray.getColor(0, 0xFF000000);
    } finally {
      typedArray.recycle();
    }
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
  }

  private void styleSystemBars(String themeValue) {
    int backgroundColor = getAppBackgroundColor(themeValue);
    getWindow().setStatusBarColor(backgroundColor);
    getWindow().setNavigationBarColor(backgroundColor);
    if (Build.VERSION.SDK_INT >= 23) {
      int flags = getWindow().getDecorView().getSystemUiVisibility();
      flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
      if (Build.VERSION.SDK_INT >= 26) {
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
      }
      if ("light".equals(themeValue)) {
        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) {
          flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
      }
      getWindow().getDecorView().setSystemUiVisibility(flags);
    }
  }

  private void styleToolbar(Toolbar toolbar, String themeValue) {
    int backgroundColor = getAppBackgroundColor(themeValue);
    int contentColor = getToolbarContentColor(themeValue);
    toolbar.setBackgroundColor(backgroundColor);
    toolbar.setTitleTextColor(contentColor);
    if (toolbar.getNavigationIcon() != null) {
      toolbar.getNavigationIcon().setTint(contentColor);
    }
  }

  private int getAppBackgroundColor(String themeValue) {
    switch (themeValue) {
      case "dark":
        return 0xFF121212;
      case "black":
        return 0xFF000000;
      default:
        return 0xFFFFFFFF;
    }
  }

  private int getToolbarContentColor() {
    return getToolbarContentColor(prefs != null ? prefs.getString("theme", "light") : "light");
  }

  private int getToolbarContentColor(String themeValue) {
    return "light".equals(themeValue) ? 0xFF111111 : 0xFFFFFFFF;
  }

  private void applySystemWindowInsets(View root) {
    if (Build.VERSION.SDK_INT < 20 || root == null) {
      return;
    }
    int initialLeft = root.getPaddingLeft();
    int initialTop = root.getPaddingTop();
    int initialRight = root.getPaddingRight();
    int initialBottom = root.getPaddingBottom();
    root.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          view.setPadding(
              initialLeft + insets.getSystemWindowInsetLeft(),
              initialTop + insets.getSystemWindowInsetTop(),
              initialRight + insets.getSystemWindowInsetRight(),
              initialBottom + insets.getSystemWindowInsetBottom());
          return insets;
        });
    root.requestApplyInsets();
  }
}
