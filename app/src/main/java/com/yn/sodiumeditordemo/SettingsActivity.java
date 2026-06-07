package com.yn.sodiumeditordemo;

import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
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
  private TextView scrollModeSummary;
  private TextView keyboardSuggestionsSummary;
  private TextView performanceModeSummary;

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
    themeSummary =
        addSettingsRow(
            settingsList,
            "Theme",
            R.drawable.ic_theme,
            v ->
                showListSetting(
                    "Theme",
                    "theme",
                    getResources().getStringArray(R.array.theme_labels),
                    getResources().getStringArray(R.array.theme_values),
                    "light"));
    scrollModeSummary =
        addSettingsRow(
            settingsList,
            "Scroll Mode",
            R.drawable.ic_scroll,
            v ->
                showListSetting(
                    "Scroll Mode",
                    "scroll_mode",
                    getResources().getStringArray(R.array.scroll_mode_labels),
                    getResources().getStringArray(R.array.scroll_mode_values),
                    "2"));
    keyboardSuggestionsSummary =
        addSettingsRow(
            settingsList,
            "Keyboard Suggestions",
            R.drawable.ic_keyboard,
            v -> toggleKeyboardSuggestions());
    performanceModeSummary =
        addSettingsRow(
            settingsList,
            "Performance Mode",
            R.drawable.ic_performance,
            v -> togglePerformanceMode());
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

  private TextView addSettingsRow(
      LinearLayout parent, String title, int iconResId, View.OnClickListener listener) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
    row.setPadding(dp(20), dp(14), dp(20), dp(14));
    row.setMinimumHeight(dp(64));
    row.setBackgroundResource(resolveSelectableItemBackground());
    row.setOnClickListener(listener);

    ImageView icon = new ImageView(this);
    icon.setImageResource(iconResId);
    icon.setColorFilter(getToolbarContentColor(), PorterDuff.Mode.SRC_IN);
    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
    iconParams.setMarginEnd(dp(24));
    row.addView(icon, iconParams);

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
    scrollModeSummary.setText(
        labelForValue(
            getResources().getStringArray(R.array.scroll_mode_labels),
            getResources().getStringArray(R.array.scroll_mode_values),
            prefs.getString("scroll_mode", "2")));
    keyboardSuggestionsSummary.setText(
        prefs.getBoolean("keyboard_suggestions", true) ? "On" : "Off");
    performanceModeSummary.setText(
        prefs.getBoolean("performance_mode", false) ? "On" : "Off");
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
