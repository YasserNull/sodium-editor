package com.yn.simplesodiumeditor;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.yn.sodiumeditor.SodiumEditor;
import java.io.File;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

  private SodiumEditor editor;
  private ActivityResultLauncher<Intent> openFileLauncher;
  private ActivityResultLauncher<Intent> manageStorageLauncher;
  private ActivityResultLauncher<String[]> requestPermissionLauncher;
  private int currentScrollMode = 2; // SCROLL_MODE_FREE
  private Uri pendingUri = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    editor = findViewById(R.id.editor);
    Button openFileBtn = findViewById(R.id.openFileBtn);
    Button settingsBtn = findViewById(R.id.settingsBtn);
    Button copyLogBtn = findViewById(R.id.copyLogBtn);

    //
    // Launcher for managing storage permission (Android 11+)
    manageStorageLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                  if (pendingUri != null) {
                    loadUriIntoEditor(pendingUri);
                    pendingUri = null;
                  } else {
                    Toast.makeText(this, "تم منح صلاحية الوصول للملفات", Toast.LENGTH_SHORT).show();
                  }
                } else {
                  Toast.makeText(this, "لم يتم منح صلاحية الوصول للملفات", Toast.LENGTH_SHORT)
                      .show();
                }
              }
            });

    // Launcher for standard storage permissions (Android 6-10)
    requestPermissionLauncher =
        registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            isGranted -> {
              boolean allGranted = true;
              for (Boolean granted : isGranted.values()) {
                if (!granted) {
                  allGranted = false;
                  break;
                }
              }
              if (allGranted) {
                if (pendingUri != null) {
                  loadUriIntoEditor(pendingUri);
                  pendingUri = null;
                } else {
                  showFilePicker();
                }
              } else {
                Toast.makeText(this, "يجب منح الصلاحيات للوصول للملفات", Toast.LENGTH_SHORT).show();
              }
            });

    openFileLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
              Uri uri = result.getData().getData();
              if (uri == null) return;
              loadUriIntoEditor(uri);
            });

    openFileBtn.setOnClickListener(v -> openFilePicker());

    settingsBtn.setOnClickListener(v -> showSettingsDialog());
    copyLogBtn.setOnClickListener(v -> showLogDialog());

    // Clear log when opening a new file
    clearLogcat();

    // Check permissions on start
    checkPermissionsAndStart();
  }

  private void checkPermissionsAndStart() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (!Environment.isExternalStorageManager()) {
        new AlertDialog.Builder(this)
            .setTitle("صلاحية الوصول للملفات")
            .setMessage(
                "يحتاج هذا التطبيق لصلاحية الوصول لجميع الملفات ليتمكن من فتح وتحرير الملفات"
                    + " البرمجية.")
            .setPositiveButton(
                "منح",
                (dialog, which) -> {
                  try {
                    Intent intent =
                        new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    manageStorageLauncher.launch(intent);
                  } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    manageStorageLauncher.launch(intent);
                  }
                })
            .setNegativeButton("إلغاء", null)
            .show();
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
          != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestPermissionLauncher.launch(
            new String[] {
              android.Manifest.permission.READ_EXTERNAL_STORAGE,
              android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            });
      }
    }
  }

  private void openFilePicker() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (!Environment.isExternalStorageManager()) {
        checkPermissionsAndStart();
        return;
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
          != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        checkPermissionsAndStart();
        return;
      }
    }
    showFilePicker();
  }

  private void showFilePicker() {
    File rootDir = Environment.getExternalStorageDirectory();
    showFilePickerDialog(rootDir);
  }

  private void showFilePickerDialog(File currentDir) {
    File[] files = currentDir.listFiles();
    if (files == null) files = new File[0];

    // Sort: directories first, then alphabetically
    Arrays.sort(
        files,
        (a, b) -> {
          if (a.isDirectory() && !b.isDirectory()) return -1;
          if (!a.isDirectory() && b.isDirectory()) return 1;
          return a.getName().compareToIgnoreCase(b.getName());
        });

    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(32, 32, 32, 32);

    ScrollView scrollView = new ScrollView(this);
    LinearLayout fileLayout = new LinearLayout(this);
    fileLayout.setOrientation(LinearLayout.VERTICAL);

    // Parent directory button
    if (currentDir.getParentFile() != null) {
      TextView parentBtn = new TextView(this);
      parentBtn.setText("📁 ..");
      parentBtn.setTextSize(18f);
      parentBtn.setPadding(24, 24, 24, 24);
      parentBtn.setOnClickListener(
          v -> {
            showFilePickerDialog(currentDir.getParentFile());
          });
      fileLayout.addView(parentBtn);
    }

    // File/directory buttons
    for (File file : files) {
      TextView tv = new TextView(this);
      String icon = file.isDirectory() ? "📁" : "📄";
      tv.setText(icon + " " + file.getName());
      tv.setTextSize(16f);
      tv.setPadding(24, 20, 24, 20);
      tv.setOnClickListener(
          v -> {
            if (file.isDirectory()) {
              showFilePickerDialog(file);
            } else {
              editor.fileIO.loadFromFile(file);
              Toast.makeText(this, "تم فتح الملف (" + file.length() + " بايت)", Toast.LENGTH_SHORT)
                  .show();
            }
          });
      fileLayout.addView(tv);
    }

    scrollView.addView(fileLayout);
    layout.addView(
        scrollView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

    // Current path display
    TextView pathView = new TextView(this);
    pathView.setText(currentDir.getAbsolutePath());
    pathView.setTextSize(12f);
    pathView.setPadding(24, 16, 24, 16);
    pathView.setMaxLines(2);
    pathView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
    layout.addView(pathView);

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("اختر ملف");
    builder.setView(layout);
    builder.setNegativeButton("إلغاء", null);
    builder.show();
  }

  private void clearLogcat() {
    new Thread(
            () -> {
              try {
                ProcessBuilder pb = new ProcessBuilder("logcat", "-c");
                pb.start().waitFor();
              } catch (Exception ignored) {
              }
            })
        .start();
  }

  private void showSettingsDialog() {
    String[] scrollModes = {"Axis", "Grid", "Free"};
    int currentItem = currentScrollMode;

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Scroll Settings");

    builder.setSingleChoiceItems(
        scrollModes,
        currentItem,
        (dialog, which) -> {
          currentScrollMode = which;
          editor.scroll.setScrollMode(which);
          Toast.makeText(this, "Scroll mode: " + scrollModes[which], Toast.LENGTH_SHORT).show();
        });

    builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
    builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

    AlertDialog dialog = builder.create();
    dialog.show();
  }

  private void showLogDialog() {
    // Create TextView for log output
    android.widget.TextView textView = new android.widget.TextView(this);
    textView.setPadding(48, 48, 48, 48);
    textView.setTextSize(12f);
    textView.setMaxLines(30);
    textView.setMovementMethod(new android.text.method.ScrollingMovementMethod());
    textView.setText("Loading logs...");

    // Create horizontal layout for buttons
    android.widget.LinearLayout buttonLayout = new android.widget.LinearLayout(this);
    buttonLayout.setPadding(48, 32, 48, 48);
    buttonLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);

    // Tags button
    android.widget.Button tagsButton = new android.widget.Button(this);
    tagsButton.setText("Tags");
    tagsButton.setLayoutParams(
        new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    tagsButton.setPadding(16, 24, 16, 24);

    // Clear button
    android.widget.Button clearButton = new android.widget.Button(this);
    clearButton.setText("Clear");
    clearButton.setLayoutParams(
        new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    clearButton.setPadding(16, 24, 16, 24);

    // Copy button
    android.widget.Button copyButton = new android.widget.Button(this);
    copyButton.setText("نسخ");
    copyButton.setLayoutParams(
        new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    copyButton.setPadding(16, 24, 16, 24);

    // Close button
    android.widget.Button closeButton = new android.widget.Button(this);
    closeButton.setText("إغلاق");
    closeButton.setLayoutParams(
        new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    closeButton.setPadding(16, 24, 16, 24);

    buttonLayout.addView(tagsButton);
    buttonLayout.addView(clearButton);
    buttonLayout.addView(copyButton);
    buttonLayout.addView(closeButton);

    // Create main vertical layout
    android.widget.LinearLayout mainLayout = new android.widget.LinearLayout(this);
    mainLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
    mainLayout.addView(
        textView,
        new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            1f));
    mainLayout.addView(buttonLayout);

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Log");
    builder.setView(mainLayout);

    AlertDialog dialog = builder.create();

    // Default tags
    final String[] defaultTags = {
    "CursorTarget","ImeScanner","Ime","SodiumInputConnection","ImeContext","DragSelectionHandler",
"GestureHandler",
"OnDoubleTap",
"OnDown",
"OnFling",
"OnKeyDown",
"OnLongPress",
"OnScroll",
"OnSingleTapUp",
"OnTouch",
"PopupInteractionHandler",
"ScrollBarHandler"
    /*
"AutoCompletion",
"initSuggestionPaint",
"setAutoCompletionEnabled",
"isAutoCompletionEnabled",
"setSuggestions",
"acceptAutoCompletion",
"clearActiveSuggestion",
"updateSuggestion",
"updateSuggestionInternal",
"getCurrentWordFragment",
"setSuggestionTextSize",
"clear",
"insert",
"findFirstSuggestion",
"findFirstWordFromNode",
"drawAutoSuggestion",
"drawAutoSuggestionWrapped",
"Caret",
"startBlink",
"stopBlink",
"resetBlink",
"getCaretDocumentX",
"getCaretDocumentY",
"drawCaret",
"updateCaretAppearance",
"getCaretXForSegment",
"getCaretXForLine",
"Cursor",
"setCursorWidth",
"setCursorPosition",
"moveToLine",
"moveToChar",
"clampToDocument",
"getLine",
"getChar",
"reset",
"isAtEndOfLine",
"isAtStartOfLine",
"isAtEndOfDocument",
"isAtStartOfDocument",
"moveCursorLeft",
"moveCursorRight",
"moveCursorUp",
"moveCursorDown",
"setCursorPositionNoClear",
"skipForbiddenBracePositions",
"invalidateCursorArea",
"CursorHandle",
"updateCursorHandlePosition",
"drawCursorHandle",
"hitTest",
"shouldShow",
"setCursorHandleSize",
"setCursorHandleColor",
"setCursorHandleRadius",
"getHandleRect",
"CodeFoldDetector",
"findFoldRangeForLine",
"isIndentFoldCandidate",
"getLineTextForFoldScan",
"findIndentFoldRangeForLine",
"findFoldTokenInLine",
"findLastUnclosedFoldTokenInLine",
"findBlockCommentEndLine",
"findMatchingBracketFrom",
"getClosingBracket",
"getIndentWidth",
"rstripWhitespace",
"isTokenEscaped",
"readLineUtf8AtByte",
"shouldShowFoldMarkerFromLine",
"isPotentialFoldStart",
"detectFoldRangeAsync",
"CodeFold",
"setCodeFoldingEnabled",
"isCodeFoldingEnabled",
"toggleFoldAtLine",
"invalidateFoldCaches",
"markIntervalsDirty",
"getVisibleLineCount",
"mapVisibleIndexToGlobal",
"getVisibleIndexForGlobalLine",
"isLineHidden",
"isFoldStart",
"getFoldRangeAtStart",
"clearFoldRanges",
"invalidateFoldRangesInRange",
"rebuildFoldIntervalsIfNeeded",
"isFoldPlaceholderHit",
"getFoldPlaceholderBounds",
"clearAllFolds",
"setIndentationBlocksEnabled",
"invalidateFoldRangeForLine",
"adjustFoldRangesForLineEdit",
"startFoldMarkerRipple",
"startFoldPlaceholderRipple",
"FoldRange",
"BracketGuideCache",
"invalidateCache",
"isCacheValid",
"swapCache",
"swapCachePartial",
"getTokensForLine",
"getStateForLine",
"LineBracketInfo",
"BracketPosition",
"isOpeningBracket",
"isClosingBracket",
"getMatchingBracket",
"QuotePosition",
"BracketCache",
"scanFileAsync",
"rebuildFoldRangesInBg",
"isInStringOrCommentQuick",
"parseLine",
"invalidateLines",
"getLineInfo",
"isInStringOrComment",
"getOpeningBrackets",
"findMatchingBracket",
"isQuoteChar",
"getStringState",
"isEscaped",
"isScanning",
"BracketGuideAsyncBuilder",
"buildCacheAsync",
"BracketGuideCheckpoint",
"ensureCheckpointCapacity",
"ensureCheckpointsUpTo",
"getCheckpointIndexForLine",
"getCheckpointState",
"getCheckpointLine",
"mergeWithMainCache",
"invalidate",
"containsLine",
"getEditVersion",
"BracketGuideSpanCache",
"ensureSpanCapacity",
"addSpan",
"getGuideXApproxFromColumn",
"drawBracketGuidesForVisibleRange",
"buildSpanCacheAsync",
"getStartLine",
"getEndLine",
"canDraw",
"BracketGuideState",
"cloneState",
"BracketGuideToken",
"getX",
"BracketGuides",
"setBracketGuidesEnabled",
"setDrawGuidesForOffScreenLines",
"isDrawGuidesForOffScreenLinesEnabled",
"setSkipGuidesDuringFastScroll",
"setMinRebuildIntervalMs",
"setBracketGuidesColor",
"setBracketGuidesStrokeWidth",
"updateStrokeWidth",
"invalidateBracketGuideCache",
"setShowGuidesDuringFastScroll",
"getBracketGuideCacheConfigHash",
"calculateBracketGuideStateForLine",
"calculateBracketGuideStateFromWindowStart",
"ensureBracketGuideCacheForWindow",
"getBracketGuideTokensForLine",
"getBracketGuideStateForLine",
"updateBracketGuideStateForLine",
"scanLineForSpans",
"getLineTextForGuideScan",
"copyState",
"getGuideTokensFromStack",
"getGuideX",
"beginRenderFrame",
"setFrameFastScroll",
"canDrawBracketGuides",
"isLineVisible",
"drawBracketGuidesForLine",
"drawBracketGuidesForLineFromStack",
"endRenderFrameMaybeLog",
"ensureBracketGuideSpanCacheForWindow",
"ensureBracketGuideCheckpointsUpTo",
"BracketMatch",
"BracketMatchManager",
"setBracketMatchingEnabled",
"setBracketMatchColor",
"setBracketMatchStrokeWidth",
"clearBracketMatchCache",
"findAndCacheBracketMatch",
"findBracketMatchInVisible",
"findBracketMatchInDocument",
"findBracketMatchInRange",
"drawBracketMatchForLine",
"drawBracketMatchForSegment",
"drawBracketBoxSegment",
"drawBracketBox",
"drawBracketBoxRange",
"drawBracketBoxRect",
"drawBracketBoxRectAtY",
"BracketToken",
"IndentGuides",
"initPaint",
"setIndentGuidesEnabled",
"setIndentGuidesColor",
"setIndentGuidesStrokeWidth",
"rebuildIndentGuideIntervalsIfNeeded",
"getLineTextForScan",
"isLineInIndentBlock",
"drawIndentGuidesForLine",
"clearIntervals",
"WhitespaceGuides",
"setWhitespaceGuidesEnabled",
"setWhitespaceGuidesSpaceStep",
"setWhitespaceGuidesColor",
"updateMetrics",
"getWhitespaceGuideStep",
"drawWhitespaceGuidesForRangeRtl",
"drawWhitespaceGuidesForLine",
"drawWhitespaceGuidesForSegment",
"drawWhitespaceGuidesSegment",
"getWhitespaceGuideSyntaxSpans",
"CurrentLineHighlight",
"setHighlightCurrentLine",
"setCurrentLineHighlightColor",
"setCurrentLineGutterHighlightEnabled",
"setAnimationEnabled",
"getAnimatedVisualIndex",
"drawCurrentLineHighlightInGutter",
"drawCurrentLineHighlightUnwrapped",
"drawCurrentLineHighlightWrapped",
"drawCurrentLineHighlightSegment",
"ColorCodeHighlight",
"setColorCodeHighlightingEnabled",
"clearColorCodeCacheForLine",
"clearColorCodeCaches",
"drawColorCodeBackgrounds",
"ErrorUnderlineSpan",
"ErrorUnderline",
"clearErrorUnderlines",
"clearErrorUnderlinesForLine",
"getErrorUnderlineSpansForLine",
"setErrorUnderline",
"setErrorUnderlineColor",
"getErrorUnderlineColor",
"setErrorUnderlineEnabled",
"isErrorUnderlineEnabled",
"setErrorUnderlineHeightScale",
"getErrorUnderlineHeightScale",
"setErrorUnderlineWaveLengthScale",
"getErrorUnderlineWaveLengthScale",
"setErrorUnderlineStrokeScale",
"getErrorUnderlineStrokeScale",
"setErrorUnderlineSmoothness",
"getErrorUnderlineSmoothness",
"drawErrorUnderlinesForLine",
"drawErrorUnderlinesForLineRange",
"drawErrorUnderlinesForSegment",
"drawErrorSquiggle",
"HighlightParser",
"parseLineForSyntax",
"isLineCommentStart",
"isStringDelimiter",
"isTripleQuoteStart",
"getStringStateForDelimiter",
"findStringEndForState",
"HighlightRules",
"initWhitespaceRules",
"addHighlightRule",
"clearHighlightRules",
"extractLineCommentDelimiter",
"addLineCommentDelimiter",
"isEmpty",
"calculateSyntaxSpansForLine",
"PathUnderline",
"setPathUnderliningEnabled",
"clearPathUnderlineCache",
"clearPathUnderlineCacheForLine",
"getPathUnderlineSpansForLine",
"ensurePathUnderlineCacheForLine",
"invalidatePathUnderlineCacheForLine",
"clearAllCaches",
"isPathUnderliningActive",
"validatePathInBackground",
"getPathUnderlinePaint",
"Highlite",
"markTyping",
"maybeEnsureHighlightCacheForRange",
"invalidateHighlightEnsureRange",
"syncRulesFromComponent",
"syncRulesToComponent",
"setLineCommentDelimiter",
"setStringHighlightColor",
"setBlockCommentHighlight",
"onTextSizeChanged",
"onTypefaceChanged",
"getLineStateAtStart",
"getHighlightSpansForLine",
"calculateSpansForLine",
"clearHighlightCaches",
"invalidateHighlightCacheForLine",
"setStringsHighlight",
"setMultiLineStringsHighlight",
"setBacktickStringsEnabled",
"setMultiLineComments",
"setSingleLineCommentDelimiters",
"ensureLineCommentDelimiter",
"setSingleLineCommentsHighlight",
"setSingleLineCommentSyntax",
"setTripleQuoteStringsEnabled",
"measureHighlightedSegmentWidth",
"drawHighlightedSegment",
"measureTextInRange",
"UrlUnderline",
"setUrlUnderliningEnabled",
"setUrlUnderliningRegex",
"clearUrlUnderlineCache",
"clearUrlUnderlineCacheForLine",
"getUrlUnderlineSpansForLine",
"trimUrlUnderlineEnd",
"ensureUrlUnderlineCacheForLine",
"isUrlUnderliningActive",
"LineNumberSelection",
"isInLineNumberGutter",
"beginLineNumberSelection",
"updateLineNumberSelection",
"endLineNumberSelection",
"LineNumber",
"setShowLineNumbers",
"invalidateLineNumberCache",
"getShowLineNumbers",
"setLineNumberColor",
"getLineNumberColor",
"isCurrentLineGutterHighlightEnabled",
"setLineNumberSelectionEnabled",
"isLineNumberSelectionEnabled",
"setGutterBackgroundColor",
"getGutterBackgroundColor",
"setGutterSeparatorColor",
"getGutterSeparatorColor",
"setGutterSeparatorWidth",
"getGutterSeparatorWidth",
"setCurrentLineNumberColor",
"getCurrentLineNumberColor",
"setLineNumberTextSize",
"getLineNumberTextSize",
"setLineNumberTypeface",
"getLineNumberTypeface",
"writeIntToChars",
"updateGutterWidth",
"drawLineNumbersDirectUnwrapped",
"drawLineNumbersDirectWrapped",
"shouldUseLineNumberCache",
"ensureLineNumberCacheBitmap",
"drawCurrentlineNumberUnwrapped",
"drawCurrentlineNumberWrapped",
"getGutterStartX",
"drawLineNumbersCachedUnwrapped",
"drawLineNumbersCachedWrapped",
"drawCurrentLineNumberUnwrapped",
"drawCurrentLineNumberWrapped",
"Edge",
"setEdgeEffectColor",
"pullTop",
"pullBottom",
"pullLeft",
"pullRight",
"releaseVertical",
"releaseHorizontal",
"absorbTop",
"absorbBottom",
"absorbLeft",
"absorbRight",
"releaseAll",
"draw",
"drawGlowArc",
"Popup",
"applyPopupConfig",
"setPopupBackgroundColor",
"setPopupTextColor",
"setPopupTextSize",
"setPopupTextSizePx",
"setPopupTextFollowsEditorTypeface",
"setPopupTextTypeface",
"setPopupLabels",
"showPopupAtSelection",
"showMinimalPopupAtCursor",
"hidePopup",
"showPopupAnimated",
"hidePopupAnimated",
"shouldKeepVisible",
"drawPopup",
"shouldHideCopyCutForSelection",
"getPopupRectForAction",
"getPopupLabelForAction",
"getPopupActionAt",
"startPopupRipple",
"startPopupRippleHold",
"cancelPopupRipple",
"spToPx",
"Scroll",
"handleScroll",
"handleFling",
"drawStretch",
"drawEdge",
"getMaxScrollXForClamp",
"getMaxScrollYForClamp",
"clampScrollX",
"clampScrollY",
"getBottomBarrierPadding",
"getKeyboardBarrierPadding",
"drawScrollBar",
"showScrollBar",
"startScrollBarFadeOut",
"cancelScrollBarFade",
"scrollTo",
"smoothScrollTo",
"abortAnimation",
"cancelFlingStopAnimation",
"getFlingOverScrollX",
"getFlingOverScrollY",
"computeScroll",
"scrollToLineFastForSelectAll",
"keepCursorVisibleHorizontally",
"getEffectiveScrollX",
"viewToTextX",
"startFlingStopAnimation",
"setScrollMode",
"setScrollSensitivity",
"setFlingSensitivity",
"setScrollBarEnabled",
"setScrollBarFadeEnabled",
"setScrollBarColor",
"setScrollBarWidthPx",
"setScrollBarMinThumbPx",
"setScrollBarFadeDelayMs",
"setScrollBarFadeDurationMs",
"setScrollBarHaloColor",
"setScrollBarHaloSizePx",
"setScrollBarCornerRadiusPx",
"setScrollBarMarginPx",
"setStretchOverscrollEnabled",
"setStretchOverscrollStrength",
"setFlingBounceEnabled",
"setFlingBounceDistancePx",
"setFlingBounceDistanceFactor",
"ScrollBar",
"show",
"startFadeOut",
"cancelFade",
"setEnabled",
"setFadeEnabled",
"setColor",
"setWidthPx",
"setMinThumbPx",
"setFadeDelayMs",
"setFadeDurationMs",
"setHaloColor",
"setHaloSizePx",
"setCornerRadiusPx",
"setMarginPx",
"ScrollBounds",
"ScrollHandler",
"Stretch",
"pullStretchX",
"pullStretchY",
"absorbStretchX",
"absorbStretchY",
"releaseStretch",
"cancelStretchRelease",
"Selection",
"syncFromState",
"syncToState",
"setSelection",
"clearSelection",
"selectAll",
"selectWordAtCursor",
"selectLineAtCursor",
"getSelectedText",
"copyOrCutSelection",
"deleteSelection",
"pasteFromClipboard",
"replaceSelectionWithText",
"applySmartDoubleTapSelection",
"buildDoubleTapCandidates",
"comparePos",
"contains",
"setSelectionInternal",
"clearSelectionStateAfterDelete",
"beginLongPressSelection",
"updateLongPressSelection",
"updateLongPressSelectionFromSelectionEnd",
"endLongPressSelection",
"findSelectionCandidateIndex",
"setSelectionAnimationEnabled",
"isPositionInsideSelection",
"buildSelectedTextFromWindow",
"buildSelectedTextBlocking",
"recordReplaceSelectionEdit",
"SelectionActionHandler",
"finishSelectAll",
"handleSelectAllReplace",
"handleSingleLineReplace",
"finalizeAction",
"SelectionClipboard",
"setPrimaryClip",
"SelectionHandles",
"drawTeardropHandle",
"getAnimatedHandlePosition",
"setHandleMoveAnimationEnabled",
"updateHandlesPosition",
"drawHandles",
"hitTestLeft",
"hitTestRight",
"getCharX",
"getLineY",
"startDragLeft",
"startDragRight",
"stopDrag",
"isDragging",
"isDraggingLeft",
"isDraggingRight",
"setHandleSize",
"setHandleColor",
"setSelectionHandleColor",
"setHandleRadius",
"getLeftHandleRect",
"getRightHandleRect",
"SelectionState",
"getSelectionAlpha",
"getHandleAlpha",
"isSelectionAnimationEnabled",
"getStartChar",
"getEndChar",
"getLineCount",
"hasSelection",
"isSelectAll",
"setSelectionColor",
"setSelectionHighlightColor",
"updateSelectionVisibility",
"clampLineForSelection",
"isLineSelectable",
"restoreSelection",
"SelectionTextRange",
"SmartSelection",
"OnDoubleTap",
"onDoubleTap",
"DragSelectionHandler",
"handleActionDown",
"handleActionMove",
"handleActionUpOrCancel",
"updateAutoScroll",
"updateHandlePosition",
"handleCodeFoldSelection",
"GestureHandler",
"handleActionPointerDown",
"handleActionPointerUp",
"processGestures",
"OnDown",
"onDown",
"OnKeyDown",
"onKeyDown",
"handleReadOnlyKey",
"handleSelectionWithPrintingKey",
"handleNormalKey",
"OnFling",
"onFling",
"OnLongPress",
"onLongPress",
"onSingleTapUpFallback",
"OnScroll",
"getGestureDetector",
"onSingleTapUp",
"onScroll",
"OnSingleTapUp",
"OnTouch",
"onTouchEvent",
"handleSuggestionTap",
"drawSelectionSegment",
"PopupInteractionHandler",
"handleActionUp",
"handleActionCancel",
"ScrollBarHandler",
"CursorTarget",
"Ime",
"onCreateInputConnection",
"onGetExtractedText",
"onSetSelection",
"onSetComposingRegion",
"onFinishComposingText",
"onCommitCompletion",
"onCommitCorrection",
"onCommitText",
"onSetComposingText",
"onDeleteSurroundingText",
"updateImeSelection",
"commitComposing",
"replaceComposingWith",
"deleteComposing",
"updateComposingPendingOp",
"markImeCommit",
"replaceWordAtCursorWith",
"tryReplaceWordFromImeCommit",
"getWordBoundsAtCursor",
"setImeExtractedTextMonitor",
"setImeExtractedTextToken",
"setImeContextSize",
"hasComposing",
"clearComposing",
"restartInput",
"hideKeyboard",
"showKeyboard",
"ImeScanner",
"buildImeContext",
"buildExtractedTextFromContext",
"getImeTextBeforeCursor",
"getImeTextAfterCursor",
"openImeRandomAccessFile",
"getLineTextForImeScan",
"clampLineCharToDocument",
"moveCursorByCharsForIme",
"buildRangeTextForIme",
"offsetToLineCharInContext",
"lineCharToOffsetInContext",
"ImeContext",
"SodiumInputConnection",
"getEditable",
"getExtractedText",
"getTextBeforeCursor",
"getTextAfterCursor",
"getSurroundingText",
"getCursorCapsMode",
"setComposingRegion",
"finishComposingText",
"commitCompletion",
"commitCorrection",
"commitText",
"setComposingText",
"deleteSurroundingText",
"toJson",
"fromJson",
"dequeToJson",
"listFromJson",
"BinaryFileReader",
"readLineWithBinarySafe",
"readLineSliceAtByte",
"readLineSliceByChars",
"ByteRangeLocator",
"computeByteRangeFastOrScan",
"computeByteRangeUsingIndex",
"computeByteRangeByScanning",
"findTwoLineStartBytesByScanning",
"findLineStartByteByScanning",
"EditOperators",
"canUndo",
"canRedo",
"getUndoStackSize",
"getPendingEditsCount",
"clearUndoRedoHistory",
"getLastEditTimestamp",
"undo",
"redo",
"insertCharAtCursor",
"deleteCharAtCursor",
"deleteForwardAtCursor",
"insertStringAtCursor",
"insertTextAtCursor",
"applyPendingEditsToFileAsync",
"recordEdit",
"recordEditNoUndo",
"countNewlines",
"computeCursorAfterInsert",
"rewriteReplaceRangeAsync",
"applyEditForUndoRedo",
"EditRecordManager",
"isLargePasteText",
"EditorActions",
"isLineInLoadedWindow",
"handleCodeFoldBeforeEdit",
"moveCursorToFoldEnd",
"handleCodeFoldNewline",
"handleWindowEdgeCase",
"insertTextAt",
"FileCache",
"populateDirectLinesForRange",
"FileEditHandler",
"rewriteReplaceRangeBlocking",
"transferRange",
"FileIO",
"loadFromFile",
"clearContent",
"loadWindowAround",
"buildFileIndex",
"readRangeText",
"invalidatePendingIO",
"invalidatePendingIOForEdit",
"reopenReaderAtStart",
"cancelAndCloseReader",
"countTotalLines",
"FileIndexer",
"buildIndexJava",
"disableIndex",
"getLineByteLengthFromIndex",
"FileMetadata",
"LineCacheShifter",
"shiftModifiedLines",
"shiftTextRenderCaches",
"Redo",
"execute",
"Undo",
"SelectionTextBuilder",
"getUndoSize",
"getPendingSize",
"pushUndo",
"popUndo",
"pushRedo",
"popRedo",
"CurrentLineHighlightAnimation",
"getTargetVisualIndex",
"checkAndStartAnimation",
"cancelAnimation",
"CharAnimation",
"setCharAnimation",
"isCharAnimationEnabled",
"setAnimationParameters",
"startCharAnimationFromText",
"startDeleteAnimation",
"cancelAllAnimations",
"cancelCharAnimation",
"cancelDeleteAnimation",
"isCharAnimationRunning",
"isDeleteAnimationRunning",
"getCharAnimAlpha",
"getDelAnimAlpha",
"getCharAnimLine",
"getDelAnimLine",
"CodeFoldAnimation",
"clearFoldRipple",
"updateTextSize",
"updateTypeface",
"CursorAnimation",
"setCursorAnimationEnabled",
"isCursorAnimationEnabled",
"setAnimationDurationMs",
"updateCursorDrawPosition",
"snapToPosition",
"getDrawX",
"getDrawY",
"getTargetX",
"getTargetY",
"isRunning",
"LoadingCircleAnimation",
"startRotation",
"stopRotation",
"beginLargeEditUiIfNeeded",
"endLargeEditUi",
"cancel",
"isAnimating",
"PopupAnimation",
"startFade",
"startRipple",
"startRippleHold",
"cancelRipple",
"SelectionAnimation",
"resetAnimationState",
"BracketGuideDraw",
"resetDrawTracking",
"BinaryLineDrawer",
"updateCachedCharWidth",
"getCachedCharWidth",
"setCachedCharWidth",
"setBinaryTokenBoxEnabled",
"setBinaryTokenFillColor",
"setBinaryTokenStrokeColor",
"setBinaryTokenStrokeWidth",
"setBinaryTokenBoxPadding",
"setBinaryTokenCornerRadius",
"setBinaryTokenTextColor",
"setBinaryCaretNotationEnabled",
"getBinaryTokenFillPaint",
"getBinaryTokenStrokePaint",
"snapBinaryCursor",
"getCharIndexForXBinary",
"getXForCharBinary",
"drawBinaryLine",
"drawBinaryLineSlice",
"TextLineDraw",
"drawTextSegmentWithFade",
"drawTextSegmentWithFadeAndUnderlines",
"drawUnderlineSegmentWithFade",
"drawTextSegmentWithVisualSpaces",
"drawDeleteAnimationForSegment",
"CodeFoldRender",
"drawFoldMarkersForVisibleLines",
"drawFoldedLine",
"drawFoldedContent",
"getLineTextForRenderWithDirect",
"getLogicalLineLength",
"getRtlLineBaseX",
"findBlockCommentEnd",
"findClosingBracketInLine",
"getEndLineTextForFold",
"HighlightCacheManager",
"ensureHighlightCacheForVisibleRange",
"HighliteRender",
"getPaintForChar",
"drawHighlightedLine",
"drawHighlightedLineRange",
"drawHighlightedLineSegment",
"setMaxSyntaxLineLength",
"setPrefetchCols",
"Layout",
"setLayoutDirection",
"isRtl",
"isLtr",
"calculateTextAreaWidth",
"calculateTextAreaHeight",
"getTextAreaWidth",
"getTextAreaHeight",
"getGuideXForColumn",
"isWhitespaceAtX",
"isGuideHitOnWhitespaceBoundary",
"hitTestWhitespaceSegment",
"getRtlSegmentBaseX",
"convertXToRtl",
"convertXToLtr",
"setPadding",
"setPaddingLeft",
"setPaddingRight",
"setPaddingTop",
"setPaddingBottom",
"getEffectivePaddingLeft",
"getEffectivePaddingRight",
"getTextStartX",
"invalidateLayout",
"updateTextAreaDimensions",
"getViewXForLineChar",
"getViewYTopForLineChar",
"LineNumberCache",
"shouldUseCache",
"ensureBitmap",
"needsRebuild",
"updateMetadata",
"TextRender",
"getAverageCharWidthForLine",
"getVisibleCharRangeForLine",
"getVisibleCharRangeForLineFast",
"computeStreamedSliceBounds",
"getInitialStreamedSliceSize",
"shouldUselineNumberCache",
"ensurelineNumberCacheBitmap",
"drawlineNumbersCachedUnwrapped",
"drawlineNumbersCachedWrapped",
"drawlineNumbersDirectUnwrapped",
"drawlineNumbersDirectWrapped",
"getDrawLineTop",
"getDrawLineBottom",
"getHitTestBaseY",
"setColsWidthCacheSize",
"setEditorBackgroundColor",
"clearEditorBackgroundColor",
"setEditorBackgroundBitmap",
"clearEditorBackgroundImage",
"clearCachesOnTypefaceChange",
"getVisualSpaceScale",
"getVisualSpaceWidth",
"getCharAdvanceWidth",
"getVisualTabWidth",
"measureTextWithVisualSpaces",
"measureText",
"getCharIndexForX",
"ViewRender",
"drawContent",
"drawContentUnfolded",
"drawTextContent",
"drawSearchHighlightsForLine",
"drawSelectionForLine",
"getEditor",
"WindowRender",
"getLineTextForRender",
"maybeUpdateStreamedSlicesForVisibleRange",
"getStreamLineThreshold",
"shouldStreamLineLength",
"getStreamedLineLength",
"getStreamedLineSliceStart",
"setStreamedLineInfo",
"clearStreamedLineInfo",
"clearStreamedLineCaches",
"isSingleByteCharset",
"getWindowEndLine",
"getLineFromWindowLocal",
"maybeKickWindowLoad",
"recalculateMaxLineWidth",
"applyMultiLineReplaceInWindowNow",
"applyMultiLineDeleteInWindowNow",
"setWindowSize",
"setPrefetchLines",
"setLineWidthCacheSize",
"setRenderWindow",
"computeMinWindowSize",
"computeMinWindowSizeForPrefetch",
"reloadWindowAroundVisible"*/
    };
    final String[] currentTags = defaultTags.clone();

    // Tags button click listener
    tagsButton.setOnClickListener(
        v -> {
          // Create input field for tags
          android.widget.EditText tagInput = new android.widget.EditText(this);
          tagInput.setPadding(48, 48, 48, 48);
          tagInput.setText(String.join("|", currentTags));
          tagInput.setHint("Tag1|Tag2|Tag3");

          AlertDialog.Builder tagsBuilder = new AlertDialog.Builder(this);
          tagsBuilder.setTitle("Log Tags");
          tagsBuilder.setMessage("Enter tags separated by | (e.g., SodiumEditor|TestTag)");
          tagsBuilder.setView(tagInput);

          tagsBuilder.setPositiveButton(
              "OK",
              (dlg, which) -> {
                String input = tagInput.getText().toString().trim();
                if (input.isEmpty()) {
                  currentTags[0] = "*"; // Show all logs
                } else {
                  currentTags[0] = input;
                }
                // Reload logs with new tags
                loadLogsIntoTextView(textView, currentTags[0]);
              });

          tagsBuilder.setNegativeButton("Cancel", (dlg, which) -> dlg.dismiss());
          tagsBuilder.show();
        });

    // Load logs asynchronously
    loadLogsIntoTextView(textView, String.join("|", currentTags));

    // Clear button click listener
    clearButton.setOnClickListener(
        v -> {
          clearLogcat();
          textView.setText("Log cleared");
          Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show();
        });

    // Copy button click listener
    copyButton.setOnClickListener(
        v -> {
          ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
          if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("log", textView.getText().toString()));
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
          }
        });

    // Close button click listener
    closeButton.setOnClickListener(v -> dialog.dismiss());

    dialog.show();
  }

  private void loadLogsIntoTextView(android.widget.TextView textView, String tagsFilter) {
    textView.setText("Loading logs...");
    new Thread(
            () -> {
              String output;
              try {
                int pid = Process.myPid();
                java.util.List<String> cmd = new java.util.ArrayList<>();
                cmd.add("logcat");
                cmd.add("-d");
                cmd.add("-v");
                cmd.add("time");
                cmd.add("--pid=" + pid);

                // Parse tags filter
                String[] tags = tagsFilter.split("\\|");
                for (String tag : tags) {
                  tag = tag.trim();
                  if (!tag.isEmpty()) {
                    cmd.add(tag + ":V"); // Show all log levels (Verbose)
                  }
                }
                if (tags.length == 0 || (tags.length == 1 && tags[0].trim().isEmpty())) {
                  cmd.add("*:V"); // Show all logs if no filter
                } else {
                  cmd.add("*:S"); // Silence other logs
                }

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                java.lang.Process proc = pb.start();
                StringBuilder sb = new StringBuilder();
                try (java.io.InputStream in = proc.getInputStream()) {
                  byte[] buf = new byte[8192];
                  int read;
                  while ((read = in.read(buf)) != -1) {
                    sb.append(new String(buf, 0, read));
                    if (sb.length() > 500_000) break; // Limit to 500KB
                  }
                }
                proc.waitFor();
                output = sb.toString();
                if (output.isEmpty()) output = "No log output for tags: " + tagsFilter;
              } catch (Exception e) {
                output = "Failed to read logs: " + e.getMessage();
              }
              String finalOutput = output;
              runOnUiThread(
                  () -> {
                    textView.setText(finalOutput);
                    // Scroll to top
                    textView.scrollTo(0, 0);
                  });
            })
        .start();
  }

  private void loadUriIntoEditor(Uri uri) {
    // Check and request MANAGE_EXTERNAL_STORAGE permission on Android 11+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (!Environment.isExternalStorageManager()) {
        pendingUri = uri;
        checkPermissionsAndStart();
        return;
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      // On older Android, check READ/WRITE_EXTERNAL_STORAGE
      if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
          != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        pendingUri = uri;
        requestPermissionLauncher.launch(
            new String[] {
              android.Manifest.permission.READ_EXTERNAL_STORAGE,
              android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            });
        return;
      }
    }

    clearLogcat();

    new Thread(
            () -> {
              try {
                String path = getPathFromUri(uri);
                if (path == null) {
                  runOnUiThread(
                      () ->
                          Toast.makeText(this, "تعذر الحصول على مسار الملف", Toast.LENGTH_SHORT)
                              .show());
                  return;
                }

                File file = new File(path);
                if (!file.exists()) {
                  runOnUiThread(
                      () ->
                          Toast.makeText(this, "الملف غير موجود: " + path, Toast.LENGTH_SHORT)
                              .show());
                  return;
                }

                long size = file.length();
                runOnUiThread(
                    () -> {
                      editor.fileIO.loadFromFile(file);
                      Toast.makeText(this, "تم فتح الملف (" + size + " بايت)", Toast.LENGTH_SHORT)
                          .show();
                    });
              } catch (Exception e) {
                runOnUiThread(
                    () ->
                        Toast.makeText(
                                this, "تعذر فتح الملف: " + e.getMessage(), Toast.LENGTH_SHORT)
                            .show());
              }
            })
        .start();
  }

  private String getPathFromUri(Uri uri) {
    if (uri == null) return null;

    // file:// scheme
    if ("file".equals(uri.getScheme())) {
      return uri.getPath();
    }

    // content:// scheme - try to get real path
    if ("content".equals(uri.getScheme())) {
      // Try MediaStore
      try (android.database.Cursor cursor =
          getContentResolver()
              .query(
                  uri,
                  new String[] {android.provider.MediaStore.Files.FileColumns.DATA},
                  null,
                  null,
                  null)) {
        if (cursor != null && cursor.moveToFirst()) {
          int idx = cursor.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DATA);
          if (idx >= 0) {
            return cursor.getString(idx);
          }
        }
      } catch (Exception ignored) {
      }

      // Try DocumentsContract (for SAF / external storage)
      try {
        String docId = android.provider.DocumentsContract.getDocumentId(uri);
        if (docId != null && docId.contains(":")) {
          String[] parts = docId.split(":");
          String type = parts[0];
          String id = parts[1];

          if ("primary".equalsIgnoreCase(type)) {
            return android.os.Environment.getExternalStorageDirectory() + "/" + id;
          }

          // Handle other storage types
          java.io.File[] dirs = getExternalFilesDirs(null);
          for (java.io.File dir : dirs) {
            if (dir != null) {
              String path =
                  dir.getAbsolutePath().replace("/Android/data/" + getPackageName() + "/files", "");
              if (dir.exists()) {
                java.io.File target = new java.io.File(path + "/" + id);
                if (target.exists()) {
                  return target.getAbsolutePath();
                }
              }
            }
          }
        }
      } catch (Exception ignored) {
      }
    }

    return uri.getPath();
  }

  private String queryDisplayName(Uri uri) {
    try (Cursor cursor =
        getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (index >= 0) {
          return cursor.getString(index);
        }
      }
    } catch (Exception ignored) {
      // Fallback handled by caller.
    }
    return null;
  }
}
