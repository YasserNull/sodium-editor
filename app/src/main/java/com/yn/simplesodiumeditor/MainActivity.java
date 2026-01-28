package com.yn.simplesodiumeditor;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.yn.sodiumeditor.view.SodiumEditorView;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_CODE = 101;
    private SodiumEditorView editor;
    private ActivityResultLauncher<Intent> storagePermissionLauncher;
    private boolean wordWrapEnabled = false;
    private boolean autoCompletionEnabled = true;
    private boolean autoPathCompletionEnabled = true;
    private boolean readOnlyEnabled = false;
    private boolean urlUnderliningEnabled = false;
    private boolean pathUnderliningEnabled = false;
    private boolean whitespaceGuidesEnabled = false;
    private int whitespaceGuidesColor = 0xFF7A7A7A;
    private int whitespaceGuidesStep = 2;
    private boolean stableGlyphPositionsEnabled = true;
    private boolean cursorAnimationEnabled = true;
    private boolean charAnimationEnabled = true;
    private int charAnimationDurationMs = 100;
    private boolean colorCodeHighlightingEnabled = false;
    private boolean smoothScrollEffectEnabled = false;
    private int maxSyntaxLineLength = 100;
    private int prefetchCols = 100;
    private int colsCacheSize = 100;
    private int windowSize = 100;
    private int prefetchLines = 100;
    private int lineCacheSize = 100;
    private int renderWindow = 100;
    private int renderPrefetch = 100;
    private boolean deferWrapReflowDuringZoom = true;
    private boolean layoutRtl = false;
    private boolean zoomEnabledState = true;
    private float zoomMinTextSize = 8f;
    private float zoomMaxTextSize = 45f;
    private float zoomStepClampValue = 0.02f;
    private float zoomFocusSmoothing = 0f;
    private float zoomScaleSmoothing = 0.02f;
    private boolean zoomLockToInitialFocus = false;
    private boolean hideDecorationsWhileZooming = false;
    private int scrollMode = SodiumEditorView.SCROLL_MODE_FREE;
    private float scrollSensitivity = 1.2f;
    private float flingSensitivity = 0.8f;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Launcher for MANAGE_EXTERNAL_STORAGE
        storagePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (Environment.isExternalStorageManager()) {
                            loadFile();
                        } else {
                            Toast.makeText(this, "All Files Access permission denied.", Toast.LENGTH_LONG).show();
                        }
                    }
                });

        setContentView(R.layout.activity_main);

        Button gotoBtn = findViewById(R.id.gotoBtn);
        Button settingsBtn = findViewById(R.id.settingsBtn);
        editor = findViewById(R.id.editor);

        gotoBtn.setOnClickListener(v -> {
            showGotoDialog();
        });

        settingsBtn.setOnClickListener(v -> showSettingsDialog());
        editor.setBackgroundColor(0xFF000000);
        editor.setTextColor(0xFFFFFFFF);
        editor.setGutterBackgroundColor(0xFF000000);
       editor.setTextSize(16f);
        editor.setStableGlyphPositionsEnabled(stableGlyphPositionsEnabled);
        editor.setWindowSize(100);
//editor.setWordWrapEnabled(true);
editor.setShowLineNumbers(true);
        editor.setPrefetchLines(100);
editor.setLineWidthCacheSize(100);
editor.setBinarySafeRenderingEnabled(true);
//editor.setFileEncoding("ISO-8859-1"); // ثابت
 editor.setPrefetchCols(100);      // شبيه
//editor.setFlingBounceEnabled(true); 
  editor.setColsWidthCacheSize(100); // كاش متوسط
 
//  editor.setMaxSyntaxLineLength(10000); //
editor.setGutterSeparatorWidth(2);
editor.setHighlightCurrentLine(true);
//editor.setColorHighlightingEnabled(true); 
//editor.setFlingBounceDistancePx(120); // أو
//  editor.setFlingBounceDistanceFactor(0.2f);
editor.setScrollBarEnabled(true);
editor.setScrollBarHaloColor(0x66FFFFFF);   //
//  لون الهالة
  editor.setScrollBarHaloSizePx(12f);         //
//  حجم الهالة
  editor.setScrollBarFadeEnabled(true);       //
  //تفعيل الإخفاء
  editor.setScrollBarFadeDelayMs(1000);       //
//  التأخير قبل الإخفاء
  editor.setScrollBarFadeDurationMs(200);     //
editor.setScrollBarMarginPx(30f);
editor.setScrollBarCornerRadiusPx(4f);
  editor.setScrollBarColor(0xFF0000FF);
  editor.setScrollBarWidthPx(20f);
  editor.setScrollBarMinThumbPx(85f);
editor.setStretchOverscrollEnabled(true);
  editor.setStretchOverscrollStrength(1.0f);
editor.setZoomTextSizeRange(8f,45f);
editor.setZoomEnabled(true);
editor.setClickAfterEndToAddLineEnabled(true);
//editor.setEdgeEffectEnabled(true);
//  editor.setEdgeEffectColor(0xFFFFFFFF);
//  editor.setEdgeEffectStrength(1.5f);
editor.setDeferWordWrapReflowDuringZoom(true);
        editor.setCursorAnimationEnabled(cursorAnimationEnabled);
        editor.setCharAnimation(charAnimationEnabled, charAnimationDurationMs);
//        editor.setColorCodeHighlightingEnabled(colorCodeHighlightingEnabled);
//        editor.setSmoothScrollEffectEnabled(smoothScrollEffectEnabled);
editor.setZoomScaleSmoothing(0.02f);
editor.setZoomFocusSmoothing(0.0f);
editor.setZoomLockToInitialFocus(false);
//editor.setZoomDebugLoggingEnabled(true);
editor.setDeferWordWrapReflowDuringZoom(true);
editor.setZoomEnabled(true);
//editor.setSmoothScrollEffectBlurEnabled(true);
//editor.setPerformanceModeEnabled(true);
//editor.setBinaryModeEnabled(true);
        editor.setAutoPairingEnabled(true);
        editor.setAutoBracketNewlineEnabled(true);
        editor.setAutoCompletionEnabled(autoCompletionEnabled);
        editor.setAutoPathCompletionEnabled(autoPathCompletionEnabled);
        editor.setWhitespaceGuidesEnabled(whitespaceGuidesEnabled);
        editor.setWhitespaceGuidesColor(whitespaceGuidesColor);
        editor.setWhitespaceGuidesSpaceStep(whitespaceGuidesStep);
        editor.setReadOnly(readOnlyEnabled);
        editor.setLayoutDirection(layoutRtl);

//editor.setScrollMode(SodiumEditorView.SCROLL_MODE_SINGLE_AXIS);
//editor.setScrollMode(SodiumEditorView.SCROLL_MODE_GRID);
editor.setScrollMode(scrollMode);
  editor.setScrollSensitivity(scrollSensitivity);
  editor.setFlingSensitivity(flingSensitivity);

        checkPermissionAndLoadFile();
    }

    private void showSettingsDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        Button autoCompletionBtn = new Button(this);
        autoCompletionBtn.setAllCaps(false);
        autoCompletionBtn.setText(autoCompletionEnabled ? "Auto Completion: ON" : "Auto Completion: OFF");
        autoCompletionBtn.setOnClickListener(v -> {
            autoCompletionEnabled = !autoCompletionEnabled;
            editor.setAutoCompletionEnabled(autoCompletionEnabled);
            autoCompletionBtn.setText(autoCompletionEnabled ? "Auto Completion: ON" : "Auto Completion: OFF");
        });

        Button autoPathBtn = new Button(this);
        autoPathBtn.setAllCaps(false);
        autoPathBtn.setText(autoPathCompletionEnabled ? "Auto Path: ON" : "Auto Path: OFF");
        autoPathBtn.setOnClickListener(v -> {
            autoPathCompletionEnabled = !autoPathCompletionEnabled;
            editor.setAutoPathCompletionEnabled(autoPathCompletionEnabled);
            autoPathBtn.setText(autoPathCompletionEnabled ? "Auto Path: ON" : "Auto Path: OFF");
        });

        Button wrapBtn = new Button(this);
        wrapBtn.setAllCaps(false);
        wrapBtn.setText(wordWrapEnabled ? "Wrap: ON" : "Wrap: OFF");
        wrapBtn.setOnClickListener(v -> {
            wordWrapEnabled = !wordWrapEnabled;
            editor.setWordWrapEnabled(wordWrapEnabled);
            wrapBtn.setText(wordWrapEnabled ? "Wrap: ON" : "Wrap: OFF");
        });

        Button readOnlyBtn = new Button(this);
        readOnlyBtn.setAllCaps(false);
        readOnlyBtn.setText(readOnlyEnabled ? "Read Only: ON" : "Read Only: OFF");
        readOnlyBtn.setOnClickListener(v -> {
            readOnlyEnabled = !readOnlyEnabled;
            editor.setReadOnly(readOnlyEnabled);
            readOnlyBtn.setText(readOnlyEnabled ? "Read Only: ON" : "Read Only: OFF");
        });

        Button wrapIndicatorBtn = new Button(this);
        wrapIndicatorBtn.setAllCaps(false);
        wrapIndicatorBtn.setText("Wrap Indicator: ON");
        final boolean[] wrapIndicatorEnabled = {true};
        wrapIndicatorBtn.setOnClickListener(v -> {
            wrapIndicatorEnabled[0] = !wrapIndicatorEnabled[0];
            editor.setWordWrapIndicatorEnabled(wrapIndicatorEnabled[0]);
            wrapIndicatorBtn.setText(wrapIndicatorEnabled[0] ? "Wrap Indicator: ON" : "Wrap Indicator: OFF");
        });

        Button wrapIndicatorColorBtn = new Button(this);
        wrapIndicatorColorBtn.setAllCaps(false);
        wrapIndicatorColorBtn.setText("Wrap Indicator Color");
        wrapIndicatorColorBtn.setOnClickListener(v -> showColorDialog("Wrap Indicator Color", color -> editor.setWordWrapIndicatorColor(color)));

        Button deferWrapBtn = new Button(this);
        deferWrapBtn.setAllCaps(false);
        deferWrapBtn.setText(deferWrapReflowDuringZoom ? "Defer Wrap Reflow: ON" : "Defer Wrap Reflow: OFF");
        deferWrapBtn.setOnClickListener(v -> {
            deferWrapReflowDuringZoom = !deferWrapReflowDuringZoom;
            editor.setDeferWordWrapReflowDuringZoom(deferWrapReflowDuringZoom);
            deferWrapBtn.setText(deferWrapReflowDuringZoom ? "Defer Wrap Reflow: ON" : "Defer Wrap Reflow: OFF");
        });

        Button layoutDirBtn = new Button(this);
        layoutDirBtn.setAllCaps(false);
        layoutDirBtn.setText(layoutRtl ? "Layout RTL: ON" : "Layout RTL: OFF");
        layoutDirBtn.setOnClickListener(v -> {
            layoutRtl = !layoutRtl;
            editor.setLayoutDirection(layoutRtl);
            layoutDirBtn.setText(layoutRtl ? "Layout RTL: ON" : "Layout RTL: OFF");
        });

        Button urlUnderlineBtn = new Button(this);
        urlUnderlineBtn.setAllCaps(false);
        urlUnderlineBtn.setText(urlUnderliningEnabled ? "URL Underline: ON" : "URL Underline: OFF");
        urlUnderlineBtn.setOnClickListener(v -> {
            urlUnderliningEnabled = !urlUnderliningEnabled;
            editor.setUrlUnderliningEnabled(urlUnderliningEnabled);
            urlUnderlineBtn.setText(urlUnderliningEnabled ? "URL Underline: ON" : "URL Underline: OFF");
        });

        Button pathUnderlineBtn = new Button(this);
        pathUnderlineBtn.setAllCaps(false);
        pathUnderlineBtn.setText(pathUnderliningEnabled ? "Path Underline: ON" : "Path Underline: OFF");
        pathUnderlineBtn.setOnClickListener(v -> {
            pathUnderliningEnabled = !pathUnderliningEnabled;
            editor.setPathUnderliningEnabled(pathUnderliningEnabled);
            pathUnderlineBtn.setText(pathUnderliningEnabled ? "Path Underline: ON" : "Path Underline: OFF");
        });

        Button whitespaceGuidesBtn = new Button(this);
        whitespaceGuidesBtn.setAllCaps(false);
        whitespaceGuidesBtn.setText(whitespaceGuidesEnabled ? "Whitespace Guides: ON" : "Whitespace Guides: OFF");
        whitespaceGuidesBtn.setOnClickListener(v -> {
            whitespaceGuidesEnabled = !whitespaceGuidesEnabled;
            editor.setWhitespaceGuidesEnabled(whitespaceGuidesEnabled);
            whitespaceGuidesBtn.setText(whitespaceGuidesEnabled ? "Whitespace Guides: ON" : "Whitespace Guides: OFF");
        });

        Button whitespaceGuidesColorBtn = new Button(this);
        whitespaceGuidesColorBtn.setAllCaps(false);
        whitespaceGuidesColorBtn.setText("Whitespace Guides Color");
        whitespaceGuidesColorBtn.setOnClickListener(
                v -> showColorDialog("Whitespace Guides Color", color -> {
                    whitespaceGuidesColor = color;
                    editor.setWhitespaceGuidesColor(color);
                }));

        Button whitespaceGuidesStepBtn = new Button(this);
        whitespaceGuidesStepBtn.setAllCaps(false);
        whitespaceGuidesStepBtn.setText("Whitespace Guides Step: " + whitespaceGuidesStep);
        whitespaceGuidesStepBtn.setOnClickListener(
                v -> showIntInputDialog("Whitespace Guides Step", whitespaceGuidesStep, val -> {
                    whitespaceGuidesStep = Math.max(1, val);
                    editor.setWhitespaceGuidesSpaceStep(whitespaceGuidesStep);
                    whitespaceGuidesStepBtn.setText("Whitespace Guides Step: " + whitespaceGuidesStep);
                }));

        Button stableGlyphBtn = new Button(this);
        stableGlyphBtn.setAllCaps(false);
        stableGlyphBtn.setText(stableGlyphPositionsEnabled ? "Stable Glyphs: ON" : "Stable Glyphs: OFF");
        stableGlyphBtn.setOnClickListener(v -> {
            stableGlyphPositionsEnabled = !stableGlyphPositionsEnabled;
            editor.setStableGlyphPositionsEnabled(stableGlyphPositionsEnabled);
            stableGlyphBtn.setText(stableGlyphPositionsEnabled ? "Stable Glyphs: ON" : "Stable Glyphs: OFF");
        });

        Button cursorAnimBtn = new Button(this);
        cursorAnimBtn.setAllCaps(false);
        cursorAnimBtn.setText(cursorAnimationEnabled ? "Cursor Animation: ON" : "Cursor Animation: OFF");
        cursorAnimBtn.setOnClickListener(v -> {
            cursorAnimationEnabled = !cursorAnimationEnabled;
            editor.setCursorAnimationEnabled(cursorAnimationEnabled);
            cursorAnimBtn.setText(cursorAnimationEnabled ? "Cursor Animation: ON" : "Cursor Animation: OFF");
        });

        Button charAnimBtn = new Button(this);
        charAnimBtn.setAllCaps(false);
        charAnimBtn.setText("Char Animation: " + (charAnimationEnabled ? "ON" : "OFF") + " / " + charAnimationDurationMs + "ms");
        charAnimBtn.setOnClickListener(v -> showIntInputDialog("Char Animation Duration (ms)", charAnimationDurationMs, val -> {
            charAnimationEnabled = !charAnimationEnabled;
            charAnimationDurationMs = Math.max(0, val);
            editor.setCharAnimation(charAnimationEnabled, charAnimationDurationMs);
            charAnimBtn.setText("Char Animation: " + (charAnimationEnabled ? "ON" : "OFF") + " / " + charAnimationDurationMs + "ms");
        }));

        Button colorCodeBtn = new Button(this);
        colorCodeBtn.setAllCaps(false);
        colorCodeBtn.setText(colorCodeHighlightingEnabled ? "Color Code Highlight: ON" : "Color Code Highlight: OFF");
        colorCodeBtn.setOnClickListener(v -> {
            colorCodeHighlightingEnabled = !colorCodeHighlightingEnabled;
            editor.setColorCodeHighlightingEnabled(colorCodeHighlightingEnabled);
            colorCodeBtn.setText(colorCodeHighlightingEnabled ? "Color Code Highlight: ON" : "Color Code Highlight: OFF");
        });
/*
        Button smoothScrollBtn = new Button(this);
        smoothScrollBtn.setAllCaps(false);
        smoothScrollBtn.setText(smoothScrollEffectEnabled ? "Smooth Scroll Effect: ON" : "Smooth Scroll Effect: OFF");
        smoothScrollBtn.setOnClickListener(v -> {
            smoothScrollEffectEnabled = !smoothScrollEffectEnabled;
            editor.setSmoothScrollEffectEnabled(smoothScrollEffectEnabled);
            smoothScrollBtn.setText(smoothScrollEffectEnabled ? "Smooth Scroll Effect: ON" : "Smooth Scroll Effect: OFF");
        });
*/
        Button maxSyntaxBtn = new Button(this);
        maxSyntaxBtn.setAllCaps(false);
        maxSyntaxBtn.setText("Max Syntax Line Length: " + maxSyntaxLineLength);
        maxSyntaxBtn.setOnClickListener(v -> showIntInputDialog("Max Syntax Line Length", maxSyntaxLineLength, val -> {
            maxSyntaxLineLength = val;
            editor.setMaxSyntaxLineLength(val);
            maxSyntaxBtn.setText("Max Syntax Line Length: " + val);
        }));

        Button prefetchColsBtn = new Button(this);
        prefetchColsBtn.setAllCaps(false);
        prefetchColsBtn.setText("Prefetch Cols: " + prefetchCols);
        prefetchColsBtn.setOnClickListener(v -> showIntInputDialog("Prefetch Cols", prefetchCols, val -> {
            prefetchCols = val;
            editor.setPrefetchCols(val);
            prefetchColsBtn.setText("Prefetch Cols: " + val);
        }));

        Button colsCacheBtn = new Button(this);
        colsCacheBtn.setAllCaps(false);
        colsCacheBtn.setText("Cols Cache Size: " + colsCacheSize);
        colsCacheBtn.setOnClickListener(v -> showIntInputDialog("Cols Width Cache Size", colsCacheSize, val -> {
            colsCacheSize = val;
            editor.setColsWidthCacheSize(val);
            colsCacheBtn.setText("Cols Cache Size: " + val);
        }));

        Button windowSizeBtn = new Button(this);
        windowSizeBtn.setAllCaps(false);
        windowSizeBtn.setText("Window Size: " + windowSize);
        windowSizeBtn.setOnClickListener(v -> showIntInputDialog("Window Size", windowSize, val -> {
            windowSize = val;
            editor.setWindowSize(val);
            windowSizeBtn.setText("Window Size: " + val);
        }));

        Button prefetchLinesBtn = new Button(this);
        prefetchLinesBtn.setAllCaps(false);
        prefetchLinesBtn.setText("Prefetch Lines: " + prefetchLines);
        prefetchLinesBtn.setOnClickListener(v -> showIntInputDialog("Prefetch Lines", prefetchLines, val -> {
            prefetchLines = val;
            editor.setPrefetchLines(val);
            prefetchLinesBtn.setText("Prefetch Lines: " + val);
        }));

        Button lineCacheBtn = new Button(this);
        lineCacheBtn.setAllCaps(false);
        lineCacheBtn.setText("Line Cache Size: " + lineCacheSize);
        lineCacheBtn.setOnClickListener(v -> showIntInputDialog("Line Width Cache Size", lineCacheSize, val -> {
            lineCacheSize = val;
            editor.setLineWidthCacheSize(val);
            lineCacheBtn.setText("Line Cache Size: " + val);
        }));

        Button renderWindowBtn = new Button(this);
        renderWindowBtn.setAllCaps(false);
        renderWindowBtn.setText("Render Window: " + renderWindow + " / " + renderPrefetch);
        renderWindowBtn.setOnClickListener(v -> showRenderWindowDialog(renderWindowBtn));

        Button zoomEnabledBtn = new Button(this);
        zoomEnabledBtn.setAllCaps(false);
        zoomEnabledBtn.setText(zoomEnabledState ? "Zoom: ON" : "Zoom: OFF");
        zoomEnabledBtn.setOnClickListener(v -> {
            zoomEnabledState = !zoomEnabledState;
            editor.setZoomEnabled(zoomEnabledState);
            zoomEnabledBtn.setText(zoomEnabledState ? "Zoom: ON" : "Zoom: OFF");
        });

        Button zoomRangeBtn = new Button(this);
        zoomRangeBtn.setAllCaps(false);
        zoomRangeBtn.setText("Zoom Text Range: " + zoomMinTextSize + "–" + zoomMaxTextSize);
        zoomRangeBtn.setOnClickListener(v -> showZoomRangeDialog(zoomRangeBtn));

        Button zoomStepBtn = new Button(this);
        zoomStepBtn.setAllCaps(false);
        zoomStepBtn.setText("Zoom Step Clamp: " + zoomStepClampValue);
        zoomStepBtn.setOnClickListener(v -> showFloatInputDialog("Zoom Step Clamp", zoomStepClampValue, val -> {
            zoomStepClampValue = val;
            editor.setZoomStepClamp(val);
            zoomStepBtn.setText("Zoom Step Clamp: " + val);
        }));

        Button zoomFocusBtn = new Button(this);
        zoomFocusBtn.setAllCaps(false);
        zoomFocusBtn.setText("Zoom Focus Smoothing: " + zoomFocusSmoothing);
        zoomFocusBtn.setOnClickListener(v -> showFloatInputDialog("Zoom Focus Smoothing", zoomFocusSmoothing, val -> {
            zoomFocusSmoothing = val;
            editor.setZoomFocusSmoothing(val);
            zoomFocusBtn.setText("Zoom Focus Smoothing: " + val);
        }));

        Button zoomScaleBtn = new Button(this);
        zoomScaleBtn.setAllCaps(false);
        zoomScaleBtn.setText("Zoom Scale Smoothing: " + zoomScaleSmoothing);
        zoomScaleBtn.setOnClickListener(v -> showFloatInputDialog("Zoom Scale Smoothing", zoomScaleSmoothing, val -> {
            zoomScaleSmoothing = val;
            editor.setZoomScaleSmoothing(val);
            zoomScaleBtn.setText("Zoom Scale Smoothing: " + val);
        }));

        Button zoomLockBtn = new Button(this);
        zoomLockBtn.setAllCaps(false);
        zoomLockBtn.setText(zoomLockToInitialFocus ? "Zoom Lock To Focus: ON" : "Zoom Lock To Focus: OFF");
        zoomLockBtn.setOnClickListener(v -> {
            zoomLockToInitialFocus = !zoomLockToInitialFocus;
            editor.setZoomLockToInitialFocus(zoomLockToInitialFocus);
            zoomLockBtn.setText(zoomLockToInitialFocus ? "Zoom Lock To Focus: ON" : "Zoom Lock To Focus: OFF");
        });

        Button hideDecorationsBtn = new Button(this);
        hideDecorationsBtn.setAllCaps(false);
        hideDecorationsBtn.setText(hideDecorationsWhileZooming ? "Hide Decorations While Zooming: ON" : "Hide Decorations While Zooming: OFF");
        hideDecorationsBtn.setOnClickListener(v -> {
            hideDecorationsWhileZooming = !hideDecorationsWhileZooming;
            editor.setHideDecorationsWhileZooming(hideDecorationsWhileZooming);
            hideDecorationsBtn.setText(hideDecorationsWhileZooming ? "Hide Decorations While Zooming: ON" : "Hide Decorations While Zooming: OFF");
        });

        Button scrollSensitivityBtn = new Button(this);
        scrollSensitivityBtn.setAllCaps(false);
        scrollSensitivityBtn.setText("Scroll Sensitivity: " + scrollSensitivity);
        scrollSensitivityBtn.setOnClickListener(v -> showFloatInputDialog("Scroll Sensitivity", scrollSensitivity, val -> {
            scrollSensitivity = val;
            editor.setScrollSensitivity(val);
            scrollSensitivityBtn.setText("Scroll Sensitivity: " + val);
        }));

        Button flingSensitivityBtn = new Button(this);
        flingSensitivityBtn.setAllCaps(false);
        flingSensitivityBtn.setText("Fling Sensitivity: " + flingSensitivity);
        flingSensitivityBtn.setOnClickListener(v -> showFloatInputDialog("Fling Sensitivity", flingSensitivity, val -> {
            flingSensitivity = val;
            editor.setFlingSensitivity(val);
            flingSensitivityBtn.setText("Fling Sensitivity: " + val);
        }));

        layout.addView(autoCompletionBtn);
        layout.addView(autoPathBtn);
        layout.addView(wrapBtn);
        layout.addView(readOnlyBtn);
        layout.addView(wrapIndicatorBtn);
        layout.addView(wrapIndicatorColorBtn);
        layout.addView(deferWrapBtn);
        layout.addView(layoutDirBtn);
        layout.addView(urlUnderlineBtn);
        layout.addView(pathUnderlineBtn);
        layout.addView(whitespaceGuidesBtn);
        layout.addView(whitespaceGuidesColorBtn);
        layout.addView(whitespaceGuidesStepBtn);
        layout.addView(stableGlyphBtn);
        layout.addView(cursorAnimBtn);
        layout.addView(charAnimBtn);
        layout.addView(colorCodeBtn);
//        layout.addView(smoothScrollBtn);
        layout.addView(maxSyntaxBtn);
        layout.addView(prefetchColsBtn);
        layout.addView(colsCacheBtn);
        layout.addView(windowSizeBtn);
        layout.addView(prefetchLinesBtn);
        layout.addView(lineCacheBtn);
        layout.addView(renderWindowBtn);
        layout.addView(scrollSensitivityBtn);
        layout.addView(flingSensitivityBtn);
        layout.addView(zoomEnabledBtn);
        layout.addView(zoomRangeBtn);
        layout.addView(zoomStepBtn);
        layout.addView(zoomFocusBtn);
        layout.addView(zoomScaleBtn);
        layout.addView(zoomLockBtn);
        layout.addView(hideDecorationsBtn);

        Button scrollModeBtn = new Button(this);
        scrollModeBtn.setAllCaps(false);
        scrollModeBtn.setText(scrollModeLabel());
        scrollModeBtn.setOnClickListener(v -> {
            if (scrollMode == SodiumEditorView.SCROLL_MODE_SINGLE_AXIS) {
                scrollMode = SodiumEditorView.SCROLL_MODE_GRID;
            } else if (scrollMode == SodiumEditorView.SCROLL_MODE_GRID) {
                scrollMode = SodiumEditorView.SCROLL_MODE_FREE;
            } else {
                scrollMode = SodiumEditorView.SCROLL_MODE_SINGLE_AXIS;
            }
            editor.setScrollMode(scrollMode);
            scrollModeBtn.setText(scrollModeLabel());
        });

        layout.addView(scrollModeBtn);
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(layout);

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(scrollView)
                .setPositiveButton("Close", (d, w) -> d.dismiss())
                .show();
    }
    private void showGotoDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Line");
        input.setGravity(Gravity.CENTER_HORIZONTAL);

        new AlertDialog.Builder(this)
                .setTitle("Go To Line")
                .setView(input)
                .setPositiveButton("Go", (d, w) -> {
                    try {
                        String lineString = input.getText().toString().trim();
                        if (!lineString.isEmpty()) {
                            int line = Integer.parseInt(lineString);
                            editor.goToLine(line, 1);
                        }
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .show();
    }

    private String scrollModeLabel() {
        if (scrollMode == SodiumEditorView.SCROLL_MODE_SINGLE_AXIS) return "Scroll Mode: Single Axis";
        if (scrollMode == SodiumEditorView.SCROLL_MODE_GRID) return "Scroll Mode: Grid";
        return "Scroll Mode: Free";
    }

    private void showIntInputDialog(String title, int currentValue, IntConsumer callback) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(currentValue));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("Set", (d, w) -> {
                    try {
                        int val = Integer.parseInt(input.getText().toString().trim());
                        callback.accept(val);
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .show();
    }

    private void showFloatInputDialog(String title, float currentValue, Consumer<Float> callback) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.valueOf(currentValue));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("Set", (d, w) -> {
                    try {
                        float val = Float.parseFloat(input.getText().toString().trim());
                        callback.accept(val);
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .show();
    }

    private void showRenderWindowDialog(Button source) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(20, 20, 20, 20);

        EditText windowInput = new EditText(this);
        windowInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        windowInput.setHint("Window size");
        windowInput.setText(String.valueOf(renderWindow));

        EditText prefetchInput = new EditText(this);
        prefetchInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        prefetchInput.setHint("Prefetch lines");
        prefetchInput.setText(String.valueOf(renderPrefetch));

        container.addView(windowInput);
        container.addView(prefetchInput);

        new AlertDialog.Builder(this)
                .setTitle("Render Window")
                .setView(container)
                .setPositiveButton("Set", (d, w) -> {
                    try {
                        int win = Integer.parseInt(windowInput.getText().toString().trim());
                        int pre = Integer.parseInt(prefetchInput.getText().toString().trim());
                        renderWindow = win;
                        renderPrefetch = pre;
                        editor.setRenderWindow(win, pre);
                        source.setText("Render Window: " + renderWindow + " / " + renderPrefetch);
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .show();
    }

    private void showZoomRangeDialog(Button source) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(20, 20, 20, 20);

        EditText minInput = new EditText(this);
        minInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        minInput.setHint("Min text size (sp)");
        minInput.setText(String.valueOf(zoomMinTextSize));

        EditText maxInput = new EditText(this);
        maxInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        maxInput.setHint("Max text size (sp)");
        maxInput.setText(String.valueOf(zoomMaxTextSize));

        container.addView(minInput);
        container.addView(maxInput);

        new AlertDialog.Builder(this)
                .setTitle("Zoom Text Size Range")
                .setView(container)
                .setPositiveButton("Set", (d, w) -> {
                    try {
                        float min = Float.parseFloat(minInput.getText().toString().trim());
                        float max = Float.parseFloat(maxInput.getText().toString().trim());
                        zoomMinTextSize = min;
                        zoomMaxTextSize = max;
                        editor.setZoomTextSizeRange(min, max);
                        source.setText("Zoom Text Range: " + zoomMinTextSize + "–" + zoomMaxTextSize);
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .show();
    }

    private void showColorDialog(String title, IntConsumer callback) {
        EditText input = new EditText(this);
        input.setHint("ARGB (e.g. FF00FF00)");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("Set", (d, w) -> {
                    try {
                        String hex = input.getText().toString().trim();
                        if (hex.startsWith("#")) hex = hex.substring(1);
                        if (hex.length() == 6) hex = "FF" + hex;
                        int color = (int) Long.parseLong(hex, 16);
                        callback.accept(color);
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .show();
    }



    private void checkPermissionAndLoadFile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            if (Environment.isExternalStorageManager()) {
                loadFile();
            } else {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    storagePermissionLauncher.launch(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    storagePermissionLauncher.launch(intent);
                }
            }
        } else { // Android 10 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                loadFile();
            } else {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        STORAGE_PERMISSION_CODE
                );
            }
        }
    }

    private void loadFile() {
        File file = new File("/sdcard/code.txt");
        if (file.exists()) {
            editor.loadFromFile(file);
            // Remove the initial goToLine call to prevent blur on startup
            // editor.goToLine(1, 1); // optional initial jump
        } else {
            Toast.makeText(this, "File not found: /sdcard/code.txt", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadFile();
            } else {
                Toast.makeText(this, "Permission denied. Cannot load file.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
