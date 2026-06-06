package com.yn.sodiumeditor.core.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.InputMethodManager;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.TextRender;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Basic view-level operations for SodiumEditor.
 * Handles performance mode, backgrounds, fonts, read-only/disable,
 * navigation, and scaling utilities.
 */
public class View {
    private final SodiumEditor editor;

    
    public final int[] tmpLocationInWindow = new int[2];
    public final android.graphics.Rect visibleDisplayFrame = new android.graphics.Rect();
    public int keyboardHeight = 0;

    public boolean isDisabled = false;
    public boolean isReadOnly = false;

    public int heavyFeaturesThreshold = 50000;

    public final Runnable delayedWindowCheck =
        new Runnable() {
          @Override
          public void run() {
            editor.fileIO.checkAndLoadWindow();
          }
        };

    public final AtomicInteger goToLineVersion = new AtomicInteger(0);

    // Editor background
    public boolean hasEditorBackgroundColor = false;
    public int editorBackgroundColor = 0x00000000;
    @androidx.annotation.Nullable public Bitmap editorBackgroundBitmap = null;
    public final android.graphics.Rect editorBackgroundDst = new android.graphics.Rect();

    // Editor Settings
    public boolean isPerformanceModeEnabled = false;
    public boolean isStableGlyphPositionsEnabled = true;
    public static final int DEFAULT_TAB_SIZE_SPACES = 4;

    // Fold marker path
    public final android.graphics.Path teardropPath = new android.graphics.Path();

    // Whitespace Guides
    public float[] guideSeenXBuffer;
    public float[] whitespaceWidthBuffer;
    public float[] whitespaceDotBuffer;
    public float[] measureWidthBuffer;

    public static final class WhitespaceDrawState {
        public int syntaxIndex;
    }

    public final WhitespaceDrawState whitespaceDrawState = new WhitespaceDrawState();

    public View(SodiumEditor editor) {
        this.editor = editor;
        // Default to a solid white background to avoid transparent-canvas "ghosting"
        // and to match typical editor expectations. Apps can override via
        // setEditorBackgroundColor()/setEditorBackgroundBitmap()/clearEditorBackgroundColor().
        hasEditorBackgroundColor = true;
        editorBackgroundColor = 0xFFFFFFFF;
    }

    // ============================================================================
    // Editor Background
    // ============================================================================

    /**
     * Draw the editor background (color or bitmap)
     */
    public void drawEditorBackground(android.graphics.Canvas canvas) {
        if (hasEditorBackgroundColor) {
            canvas.drawColor(editorBackgroundColor);
        }
        if (editorBackgroundBitmap != null && !editorBackgroundBitmap.isRecycled()) {
            editorBackgroundDst.set(0, 0, editor.getWidth(), editor.getHeight());
            canvas.drawBitmap(editorBackgroundBitmap, null, editorBackgroundDst, null);
        }
    }

    /**
     * Set editor background color
     */
    public void setEditorBackgroundColor(int color) {
        hasEditorBackgroundColor = true;
        editorBackgroundColor = color;
        editor.invalidate();
    }

    /**
     * Clear editor background color
     */
    public void clearEditorBackgroundColor() {
        hasEditorBackgroundColor = false;
        editor.invalidate();
    }

    /**
     * Set editor background bitmap
     */
    public void setEditorBackgroundBitmap(android.graphics.Bitmap bitmap) {
        if (editorBackgroundBitmap != null && !editorBackgroundBitmap.isRecycled()) {
            editorBackgroundBitmap.recycle();
        }
        editorBackgroundBitmap = bitmap;
        editor.invalidate();
    }

    /**
     * Clear editor background image
     */
    public void clearEditorBackgroundImage() {
        if (editorBackgroundBitmap != null && !editorBackgroundBitmap.isRecycled()) {
            editorBackgroundBitmap.recycle();
        }
        editorBackgroundBitmap = null;
        editor.invalidate();
    }

    // ============================================================================
    // Font and Typeface Methods
    // ============================================================================

    /**
     * Apply typeface to editor
     */
    public void applyTypeface(@androidx.annotation.Nullable android.graphics.Typeface typeface, int style) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            final android.graphics.Typeface tf = typeface;
            final int st = style;
            editor.post(() -> applyTypeface(tf, st));
            return;
        }
        android.graphics.Typeface safeBase = (typeface != null) ? typeface : android.graphics.Typeface.DEFAULT;
        editor.textRender.baseTypeface = safeBase;
        int typefaceStyle;
        switch (style) {
            case com.yn.sodiumeditor.core.view.FontStyle.STYLE_BOLD:
                typefaceStyle = android.graphics.Typeface.BOLD;
                break;
            case com.yn.sodiumeditor.core.view.FontStyle.STYLE_ITALIC:
                typefaceStyle = android.graphics.Typeface.ITALIC;
                break;
            case com.yn.sodiumeditor.core.view.FontStyle.STYLE_BOLD_ITALIC:
                typefaceStyle = android.graphics.Typeface.BOLD_ITALIC;
                break;
            default:
                typefaceStyle = android.graphics.Typeface.NORMAL;
                break;
        }
        android.graphics.Typeface finalTypeface = android.graphics.Typeface.create(safeBase, typefaceStyle);
        editor.textRender.paint.setTypeface(finalTypeface);
        editor.autoCompletion.suggestionPaint.setTypeface(finalTypeface);
        editor.lineNumber.lineNumbersPaint.setTypeface(finalTypeface);
        editor.wordWrap.indicator.wordWrapIndicatorPaint.setTypeface(finalTypeface);
        if (editor.highlightRules.whitespaceStringRule != null)
            editor.highlightRules.whitespaceStringRule.updateTypeface(safeBase);
        if (editor.highlightRules.whitespaceCommentRule != null)
            editor.highlightRules.whitespaceCommentRule.updateTypeface(safeBase);
        if (editor.highlightRules.lineCommentHighlightRule != null)
            editor.highlightRules.lineCommentHighlightRule.updateTypeface(safeBase);
        for (com.yn.sodiumeditor.renderer.HighliteRender.HighlightRule rule : editor.highlite.highlightRules) {
            rule.updateTypeface(safeBase);
        }
        editor.highlite.clearHighlightCaches();

        editor.textRender.lineHeight = editor.textRender.paint.getFontSpacing();
        editor.whitespaceGuides.updateMetrics();
        editor.lineNumber.invalidateLineNumberCache();
        editor.wordWrap.indicator.wordWrapIndicatorWidth =
                editor.wordWrap.indicator.wordWrapIndicatorPaint.measureText(
                        com.yn.sodiumeditor.core.wordwrap.WordWrapIndicator.WORD_WRAP_INDICATOR_TEXT);

        editor.textRender.clearCachesOnTypefaceChange();
        editor.windowRender.lineWidthCache.clear();
        editor.windowRender.avgCharWidthCache.clear();

        editor.windowRender.currentMaxWindowLineWidth = 0f;
        editor.windowRender.globalMaxLineWidth = 0f;
        editor.scroll.maxLineWidthForScroll = 0f;
        editor.scroll.maxTextStartXForScroll = 0f;
        editor.scroll.maxScrollXForScroll = 0f;
        editor.windowRender.recalculateMaxLineWidth();

        editor.requestLayout();
        if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics(true);
        editor.wordWrap.requestWrapPrefixRebuild();
        editor.invalidate();
    }

    /**
     * Apply text size to editor
     */
    public void applyTextSizePx(float sizePx) {
        applyTextSizePx(sizePx, false);
    }

    /**
     * Apply text size to editor with defer option
     */
    public void applyTextSizePx(float sizePx, boolean deferWrapRebuild) {
        float oldSize = editor.textRender.paint.getTextSize();
        if (Math.abs(sizePx - oldSize) < 0.1f) return;

        editor.textRender.paint.setTextSize(sizePx);

        // Update binary render cached character width
        editor.binaryRender.updateCachedCharWidth(editor.textRender.paint);

        if (!editor.autoCompletion.isSuggestionTextSizeCustom) {
            editor.autoCompletion.suggestionTextSizeScale = 1f;
        }
        editor.autoCompletion.suggestionPaint.setTextSize(sizePx * editor.autoCompletion.suggestionTextSizeScale);
        editor.lineNumber.lineNumbersPaint.setTextSize(sizePx);
        editor.wordWrap.indicator.wordWrapIndicatorPaint.setTextSize(
                sizePx * editor.wordWrap.indicator.wordWrapIndicatorTextScale);
        editor.wordWrap.indicator.wordWrapIndicatorPaint.setTypeface(editor.textRender.paint.getTypeface());
        editor.wordWrap.indicator.wordWrapIndicatorWidth =
                editor.wordWrap.indicator.wordWrapIndicatorPaint.measureText(
                        com.yn.sodiumeditor.core.wordwrap.WordWrapIndicator.WORD_WRAP_INDICATOR_TEXT);
        editor.textRender.lineHeight = editor.textRender.paint.getFontSpacing();
        editor.windowRender.recalculateMaxLineWidth();
        editor.whitespaceGuides.updateMetrics();
        editor.cursorHandle.updateHandleMetricsForTextSize(sizePx);
        editor.selectionHandles.updateHandleMetricsForTextSize(sizePx);
        editor.lineNumber.invalidateLineNumberCache();

        editor.textRender.clearCachesOnTypefaceChange();
        editor.windowRender.lineWidthCache.clear();
        editor.windowRender.avgCharWidthCache.clear();

        for (com.yn.sodiumeditor.renderer.HighliteRender.HighlightRule rule : editor.highlite.highlightRules) {
            rule.updateTextSize(sizePx);
        }
        if (editor.highlightRules.whitespaceStringRule != null)
            editor.highlightRules.whitespaceStringRule.updateTextSize(sizePx);
        if (editor.highlightRules.whitespaceCommentRule != null)
            editor.highlightRules.whitespaceCommentRule.updateTextSize(sizePx);
        if (editor.highlightRules.lineCommentHighlightRule != null)
            editor.highlightRules.lineCommentHighlightRule.updateTextSize(sizePx);
        editor.highlite.clearHighlightCaches();

        float scale = sizePx / oldSize;
        editor.windowRender.currentMaxWindowLineWidth *= scale;
        editor.windowRender.globalMaxLineWidth *= scale;
        editor.scroll.maxLineWidthForScroll *= scale;
        editor.scroll.maxScrollXForScroll *= scale;
        editor.scroll.maxTextStartXForScroll = 0f;
        if (scale < 1f) {
            editor.scroll.maxLineWidthForScroll = 0f;
            editor.scroll.maxScrollXForScroll = 0f;
        }

        editor.requestLayout();
        if (editor.wordWrap.isWordWrapEnabled) editor.wordWrap.invalidateWrapMetrics(true, !deferWrapRebuild);
        editor.wordWrap.requestWrapPrefixRebuild();
        editor.invalidate();
    }

    public void applyTextSizePxForZoomFrame(float sizePx) {
        float oldSize = editor.textRender.paint.getTextSize();
        if (Math.abs(sizePx - oldSize) < 0.1f) return;

        editor.textRender.paint.setTextSize(sizePx);
        editor.binaryRender.updateCachedCharWidth(editor.textRender.paint);

        if (!editor.autoCompletion.isSuggestionTextSizeCustom) {
            editor.autoCompletion.suggestionTextSizeScale = 1f;
        }
        editor.autoCompletion.suggestionPaint.setTextSize(sizePx * editor.autoCompletion.suggestionTextSizeScale);
        editor.lineNumber.lineNumbersPaint.setTextSize(sizePx);
        editor.wordWrap.indicator.wordWrapIndicatorPaint.setTextSize(
                sizePx * editor.wordWrap.indicator.wordWrapIndicatorTextScale);
        editor.wordWrap.indicator.wordWrapIndicatorPaint.setTypeface(editor.textRender.paint.getTypeface());
        editor.wordWrap.indicator.wordWrapIndicatorWidth =
                editor.wordWrap.indicator.wordWrapIndicatorPaint.measureText(
                        com.yn.sodiumeditor.core.wordwrap.WordWrapIndicator.WORD_WRAP_INDICATOR_TEXT);
        editor.textRender.lineHeight = editor.textRender.paint.getFontSpacing();
        editor.whitespaceGuides.updateMetrics();
        editor.cursorHandle.updateHandleMetricsForTextSize(sizePx);
        editor.selectionHandles.updateHandleMetricsForTextSize(sizePx);
        editor.lineNumber.invalidateLineNumberCache();
        editor.textRender.clearCachesOnTypefaceChange();

        for (com.yn.sodiumeditor.renderer.HighliteRender.HighlightRule rule : editor.highlite.highlightRules) {
            rule.updateTextSize(sizePx);
        }
        if (editor.highlightRules.whitespaceStringRule != null)
            editor.highlightRules.whitespaceStringRule.updateTextSize(sizePx);
        if (editor.highlightRules.whitespaceCommentRule != null)
            editor.highlightRules.whitespaceCommentRule.updateTextSize(sizePx);
        if (editor.highlightRules.lineCommentHighlightRule != null)
            editor.highlightRules.lineCommentHighlightRule.updateTextSize(sizePx);

        float scale = sizePx / oldSize;
        editor.windowRender.currentMaxWindowLineWidth *= scale;
        editor.windowRender.globalMaxLineWidth *= scale;
        editor.scroll.maxLineWidthForScroll *= scale;
        editor.scroll.maxScrollXForScroll *= scale;
        editor.scroll.maxTextStartXForScroll = 0f;
        clearVisibleLineMetricCaches();
        editor.invalidate();
    }

    public void finishZoomTextSizeUpdate() {
        synchronized (editor.windowRender.lineWidthCache) {
            editor.windowRender.lineWidthCache.clear();
        }
        synchronized (editor.windowRender.avgCharWidthCache) {
            editor.windowRender.avgCharWidthCache.clear();
        }
        editor.textRender.clearCachesOnTypefaceChange();
        editor.lineNumber.invalidateLineNumberCache();
        editor.requestLayout();
        editor.invalidate();
    }

    private void clearVisibleLineMetricCaches() {
        if (editor.textRender.lineHeight <= 0f) return;
        int firstVisibleIndex = Math.max(0, (int) (editor.scroll.scrollY / editor.textRender.lineHeight));
        int lastVisibleIndex =
                firstVisibleIndex + (int) Math.ceil(editor.getHeight() / editor.textRender.lineHeight) + 1;
        int firstLine;
        int lastLine;
        if (editor.wordWrap.isWordWrapEnabled) {
            firstLine = editor.wordWrap.getVisualPositionForIndex(firstVisibleIndex).line;
            lastLine = editor.wordWrap.getVisualPositionForIndex(lastVisibleIndex).line;
        } else {
            firstLine = firstVisibleIndex;
            lastLine = lastVisibleIndex;
        }
        synchronized (editor.windowRender.lineWidthCache) {
            for (int line = Math.max(0, firstLine); line <= Math.max(firstLine, lastLine); line++) {
                editor.windowRender.lineWidthCache.remove(line);
            }
        }
        synchronized (editor.windowRender.avgCharWidthCache) {
            for (int line = Math.max(0, firstLine); line <= Math.max(firstLine, lastLine); line++) {
                editor.windowRender.avgCharWidthCache.remove(line);
            }
        }
    }

    // ============================================================================
    // Performance Mode
    // ============================================================================

    public void setPerformanceModeEnabled(boolean enabled) {
        if (this.isPerformanceModeEnabled == enabled) return;
        this.isPerformanceModeEnabled = enabled;
        if (enabled) {
            editor.urlUnderline.setUrlUnderliningEnabled(false);
            editor.pathUnderline.setPathUnderliningEnabled(false);
            editor.colorCodeHighlight.setColorCodeHighlightingEnabled(false);
            editor.bracketMatchManager.setBracketMatchingEnabled(false);
            editor.bracketGuides.setBracketGuidesEnabled(false);
            editor.indentGuides.setIndentGuidesEnabled(false);
            editor.whitespaceGuides.setWhitespaceGuidesEnabled(false);
            editor.wordWrap.setWordWrapIndicatorEnabled(false);
            editor.autoCompletion.setAutoCompletionEnabled(false);
            editor.autoPathCompletion.setAutoPathCompletionEnabled(false);
            editor.charAnimation.setCharAnimation(false, editor.charAnimation.charAnimationDurationMs);
            editor.currentLineHighlight.setHighlightCurrentLine(false);
        }
        editor.invalidate();
    }

    public void invalidateLineGlobal(int globalLine) {
        if (editor.wordWrap.isWordWrapEnabled) {
            editor.invalidate();
            return;
        }
        int idx =
                false
                        ? globalLine
                        : globalLine;
        float top = (idx * editor.textRender.lineHeight) - editor.scroll.scrollY;
        editor.invalidate(
                0,
                (int) Math.floor(top),
                editor.getWidth(),
                (int) Math.ceil(top + editor.textRender.lineHeight));
    }

    public int getBraceGuideColumnForLine(String line, int globalLine, int braceIndex, int firstNonSpace) {
        int column = (firstNonSpace >= 0) ? firstNonSpace : braceIndex;
        if (firstNonSpace >= 0 && braceIndex > firstNonSpace) {
            char first = line.charAt(firstNonSpace);
            if (first == ')' || first == ']') {
                int prevIndent = getPreviousNonEmptyIndentColumn(globalLine - 1);
                if (prevIndent >= 0) {
                    column = prevIndent;
                }
            }
        }
        return column;
    }

    public int getPreviousNonEmptyIndentColumn(int line) {
        for (int l = line; l >= 0; l--) {
            String prev = editor.windowRender.getLineTextForRender(l);
            if (prev == null) continue;
            int idx = com.yn.sodiumeditor.utils.TextUtils.getFirstNonSpaceIndex(prev);
            if (idx >= 0) return idx;
        }
        return -1;
    }

    public void release() {
        editor.fileIO.cancelAndCloseReader();
        if (editor.charAnimation.charAnimAnimator != null) editor.charAnimation.charAnimAnimator.cancel();
        if (editor.charAnimation.delAnimAnimator != null) editor.charAnimation.delAnimAnimator.cancel();
        editor.removeCallbacks(editor.cursorAnimation.cursorAnimStep);
        editor.fileIO.ioThread.quitSafely();
    }

    // ============================================================================
    // Background Image
    // ============================================================================

    public void setEditorBackgroundImageFromAssets(String assetPath) {
        if (assetPath == null) return;
        try (InputStream input = editor.getContext().getAssets().open(assetPath)) {
            Bitmap bmp = BitmapFactory.decodeStream(input);
            if (bmp != null) {
                editor.textRender.setEditorBackgroundBitmap(bmp);
            }
        } catch (Exception e) {
        }
    }

    public void setEditorBackgroundImageFromFile(String filePath) {
        if (filePath == null) return;
        try {
            Bitmap bmp = BitmapFactory.decodeFile(filePath);
            if (bmp != null) {
                editor.textRender.setEditorBackgroundBitmap(bmp);
            }
        } catch (Exception e) {
        }
    }

    // ============================================================================
    // Font
    // ============================================================================

    public void setFontFromAssets(String assetPath, int style) {
        try {
            Typeface tf = Typeface.createFromAsset(editor.getContext().getAssets(), assetPath);
            applyTypeface(tf, style);
        } catch (Exception e) {
        }
    }

    public void setFontFromFile(String filePath, int style) {
        try {
            Typeface tf = Typeface.createFromFile(filePath);
            applyTypeface(tf, style);
        } catch (Exception e) {
        }
    }

    // ============================================================================
    // Read-Only / Disable
    // ============================================================================

    public void setReadOnly(boolean readOnly) {
        if (isReadOnly == readOnly) return;
        isReadOnly = readOnly;
        if (readOnly) {
            editor.autoCompletion.clearActiveSuggestion();
            editor.selection.hasSelection = false;
            editor.selection.isSelectAllActive = false;
            editor.selection.isEntireFileSelected = false;
            InputMethodManager imm =
                (InputMethodManager) editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        }
        restartInput();
        editor.invalidate();
    }

    public void setDisable(boolean disable) {
        isDisabled = disable;
    }

    public void restartInput() {
        InputMethodManager imm =
            (InputMethodManager) editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.restartInput(editor);
        }
    }

    // ============================================================================
    // Navigation
    // ============================================================================

    public void goToLine(int line) {
        goToLine(line, 1);
    }

    public void goToLine(int line, int col) {
        final int currentGoToLineVersion = goToLineVersion.incrementAndGet();
        setDisable(true);
        editor.loadingCircle.showLoadingCircle(true);

        if (editor.selection.hasSelection) {
            editor.selection.hasSelection = false;
            editor.selection.isSelectAllActive = false;
            editor.selection.isEntireFileSelected = false;
            editor.selection.selecting = false;
        }

        final int requestedLine = Math.max(0, line - 1);
        final int requestedCol = Math.max(0, col - 1);

        Integer knownTotal = null;

        if (editor.fileIO.sourceFile == null || editor.fileIO.isFileCleared) {
            synchronized (editor.windowRender.linesWindow) {
                knownTotal = Math.max(1, editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size());
            }
        } else if (editor.fileIO.isIndexReady) {
            synchronized (editor.fileIO.lineOffsetsLock) {
                knownTotal = Math.max(1, editor.fileIO.lineOffsets.length);
            }
        } else if (editor.fileIO.isEof) {
            synchronized (editor.windowRender.linesWindow) {
                knownTotal = Math.max(1, editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size());
            }
        }

        if (knownTotal != null) {
            int clampedLine = Math.min(requestedLine, Math.max(0, knownTotal - 1));
            proceedGoToLineClamped(currentGoToLineVersion, clampedLine, requestedCol);
        } else {
            editor.fileIO.countTotalLines(
                totalLines -> {
                    if (currentGoToLineVersion != goToLineVersion.get()) return;
                    int total = (totalLines > 0) ? totalLines : (requestedLine + 1);
                    int clampedLine = Math.min(requestedLine, Math.max(0, total - 1));
                    proceedGoToLineClamped(currentGoToLineVersion, clampedLine, requestedCol);
                });
        }
    }

    public void proceedGoToLineClamped(
        final int currentGoToLineVersion, final int targetLine, final int targetCol) {

        if (editor.fileIO.isWindowLoading
            && editor.fileIO.sourceFile != null
            && !(targetLine >= editor.windowRender.windowStartLine && targetLine < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size())) {
            editor.caret.mainHandler.postDelayed(
                () -> {
                    if (currentGoToLineVersion != goToLineVersion.get()) return;
                    proceedGoToLineClamped(currentGoToLineVersion, targetLine, targetCol);
                },
                30);
            return;
        }

        Runnable completionAction =
            () -> {
                if (currentGoToLineVersion != goToLineVersion.get()) return;

                editor.cursor.cursorLine = targetLine;

                if (editor.cursor.cursorLine >= editor.windowRender.windowStartLine
                    && editor.cursor.cursorLine < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size()) {
                    String lineText = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
                    editor.cursor.cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
                } else if (editor.fileIO.isEof) {
                    int lastLineInDoc = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size() - 1;
                    if (editor.cursor.cursorLine > lastLineInDoc) editor.cursor.cursorLine = Math.max(0, lastLineInDoc);
                    String lineText = editor.windowRender.getLineTextForRender(editor.cursor.cursorLine);
                    editor.cursor.cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
                } else {
                    editor.cursor.cursorChar = 0;
                }

                editor.scroll.keepCursorVisibleHorizontally();
                setDisable(false);
                editor.loadingCircle.showLoadingCircle(false);

                editor.requestFocus();
                editor.post(
                    () -> {
                        editor.ime.showKeyboard();
                        editor.requestFocus();
                        InputMethodManager imm =
                            (InputMethodManager)
                                editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.restartInput(editor);
                    });
            };

        if (editor.fileIO.isFileCleared
            || editor.fileIO.sourceFile == null
            || (targetLine >= editor.windowRender.windowStartLine && targetLine < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size())) {
            completionAction.run();
        } else {
            int targetStart = Math.max(0, targetLine - editor.windowRender.prefetchLines);
            editor.fileIO.loadWindowAround(targetStart, completionAction, false);
        }
    }

    // ============================================================================
    // Scaling Utilities
    // ============================================================================

    public float spToPx(float sp) {
        return sp * editor.getResources().getDisplayMetrics().scaledDensity;
    }

    public float scaleByTextSize(float baseValue, float baseTextSizePx, float newTextSizePx) {
        if (baseTextSizePx <= 0f) return baseValue;
        return baseValue * (newTextSizePx / baseTextSizePx);
    }

    // ============================================================================
    // Line and Character Measurement
    // ============================================================================

    public int getLinesCount() {
        if (editor.fileIO.isFileCleared) {
            return Math.max(1, editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size());
        }
        int windowCount = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size();
        if (editor.fileIO.isIndexReady && editor.fileIO.lineOffsets.length > 0) {
            boolean hasEdits = editor.windowRender.hasAnyModifiedLines();
            if (!hasEdits && editor.editOperators.lineCountDelta == 0) {
                return editor.fileIO.lineOffsets.length;
            }
            int count = editor.fileIO.lineOffsets.length + editor.editOperators.lineCountDelta;
            if (count < 1) count = 1;
            return count;
        }
        if (editor.fileIO.isEof) return editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size();
        if (!editor.windowRender.linesWindow.isEmpty()) return editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size();
        return -1;
    }

    public int getLogicalLineLength(int globalLine, String line) {
        String mod = editor.windowRender.getModifiedLine(globalLine);
        if (mod != null) return mod.length();
        int len = (line == null) ? 0 : line.length();
        int longLen = editor.windowRender.getStreamedLineLength(globalLine);
        return (longLen > len) ? longLen : len;
    }

    public void computeWidthForLine(int globalIndex, String line) {
        String safe = (line == null) ? "" : line;
        Float oldWidth = null;
        synchronized (editor.windowRender.lineWidthCache) {
            oldWidth = editor.windowRender.lineWidthCache.get(globalIndex);
        }
        float w;
        int logicalLen = getLogicalLineLength(globalIndex, safe);
        if (logicalLen > editor.highliteRender.maxSyntaxLineLength) {
            w = editor.textRender.getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
        } else {
            w = editor.textRender.measureTextWithVisualSpaces(safe, 0, safe.length(), editor.textRender.paint);
        }
        synchronized (editor.windowRender.lineWidthCache) {
            editor.windowRender.lineWidthCache.put(globalIndex, w);
        }
        if (w > editor.windowRender.currentMaxWindowLineWidth) {
            editor.windowRender.currentMaxWindowLineWidth = w;
        }
        if (w > editor.windowRender.globalMaxLineWidth) {
            editor.windowRender.globalMaxLineWidth = w;
        }
        if (w > editor.scroll.maxLineWidthForScroll) {
            editor.scroll.maxLineWidthForScroll = w;
        }
        if (oldWidth != null
                && oldWidth >= editor.windowRender.globalMaxLineWidth
                && w < oldWidth) {
            editor.windowRender.recalculateMaxLineWidth();
        }
    }

    public float getWidthForLine(int globalIndex, String line) {
        synchronized (editor.windowRender.lineWidthCache) {
            Float v = editor.windowRender.lineWidthCache.get(globalIndex);
            if (v != null) return v;
        }
        String safe = (line == null) ? "" : line;
        float w;
        int logicalLen = getLogicalLineLength(globalIndex, safe);
        if (logicalLen > editor.highliteRender.maxSyntaxLineLength) {
            w = editor.textRender.getAverageCharWidthForLine(safe, globalIndex) * logicalLen;
        } else {
            w = editor.textRender.measureTextWithVisualSpaces(safe, 0, safe.length(), editor.textRender.paint);
        }
        synchronized (editor.windowRender.lineWidthCache) {
            editor.windowRender.lineWidthCache.put(globalIndex, w);
        }
        return w;
    }

    public int getCharIndexForX(String text, float x, int globalLine) {
        if (text == null || text.isEmpty()) return 0;
        if (editor.textRender.isRtl) {
            float baseX = editor.layout.getRtlLineBaseX(text, globalLine);
            x -= baseX;
            float w =
                    editor.highlite.measureHighlightedSegmentWidth(
                            text, globalLine, 0, getLogicalLineLength(globalLine, text));
            x = w - x;
        }
        if (x <= 0f) return 0;
        if (editor.binaryRender.shouldUseBinaryRenderingForLine(globalLine)) {
            int[] spans = editor.binaryRender.getBinaryTokenSpans(globalLine);
            float padX = editor.binaryRender.binaryCaretNotationEnabled ? 0f : editor.binaryRender.binaryTokenPaddingX;

            return editor.binaryRender.getCharIndexForXBinary(
                    text, 0, text.length(), x, editor.textRender.paint, spans, padX);
        }

        int len = getLogicalLineLength(globalLine, text);
        if (len > editor.highliteRender.maxSyntaxLineLength) {
            float avg = editor.textRender.getAverageCharWidthForLine(text, globalLine);
            if (avg <= 0f) return 0;
            int idx = (int) Math.round(x / avg);
            return Math.max(0, Math.min(idx, len));
        }
        int textLen = text.length();
        if (editor.textRender.getVisualSpaceScale() == 1) {
            int count = editor.textRender.paint.breakText(text, true, x, null);
            if (count <= 0) return 0;
            if (count >= textLen) return textLen;

            float wPrev = (count > 1) ? editor.textRender.paint.measureText(text, 0, count - 1) : 0f;
            float wCount = editor.textRender.paint.measureText(text, 0, count);
            float mid = wPrev + (wCount - wPrev) * 0.5f;
            return (x < mid) ? (count - 1) : count;
        }

        if (measureWidthBuffer == null || measureWidthBuffer.length < textLen) {
            measureWidthBuffer = new float[textLen];
        }
        editor.textRender.paint.getTextWidths(text, 0, textLen, measureWidthBuffer);
        float current = 0f;
        for (int i = 0; i < textLen; i++) {
            float adv = editor.textRender.getCharAdvanceWidth(text.charAt(i), measureWidthBuffer[i], editor.textRender.paint);
            float mid = current + adv * 0.5f;
            if (x < mid) return i;
            if (x < current + adv) return i + 1;
            current += adv;
        }
        return textLen;
    }

    public long computeByteOffsetInLineUtf8(String lineText, int charIndex) {
        if (lineText == null) return 0L;
        int safe = Math.max(0, Math.min(charIndex, lineText.length()));
        if (safe == 0) return 0L;
        return lineText.substring(0, safe).getBytes(editor.fileIO.fileCharset).length;
    }

    public static String buildIndentFromWidth(int width) {
        if (width <= 0) return "";
        char[] buf = new char[width];
        for (int i = 0; i < width; i++) buf[i] = ' ';
        return new String(buf);
    }

    public int getIndentWidth(String line) {
        if (line == null || line.isEmpty()) return 0;
        int width = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') {
                width++;
            } else if (c == '\t') {
                width += TextRender.DEFAULT_TAB_SIZE_SPACES;
            } else {
                break;
            }
        }
        return width;
    }

    public String getLineLeadingWhitespace(int line) {
        String ln = editor.windowRender.getLineTextForRender(line);
        if (ln == null || ln.isEmpty()) return "";
        int i = 0;
        while (i < ln.length()) {
            char c = ln.charAt(i);
            if (c != ' ' && c != '\t') break;
            i++;
        }
        return (i == 0) ? "" : ln.substring(0, i);
    }

    public static class StreamedCharSlice {
        public final String text;
        public final int length;

        public StreamedCharSlice(String text, int length) {
            this.text = text;
            this.length = length;
        }
    }

    public int[] computeWordBounds(String line, int pos) {
        pos = Math.max(0, Math.min(pos, line.length()));
        if (line.length() == 0) return new int[] {0, 0};
        if (pos == line.length()) pos = Math.max(0, pos - 1);
        if (Character.isWhitespace(line.charAt(pos))) {
            int i = pos;
            while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
            if (i >= line.length()) {
                i = pos - 1;
                while (i >= 0 && Character.isWhitespace(line.charAt(i))) i--;
            }
            if (i < 0) return new int[] {pos, pos};
            pos = i;
        }
        int start = pos;
        int end = pos;
        while (start > 0 && !Character.isWhitespace(line.charAt(start - 1))) start--;
        while (end < line.length() - 1 && !Character.isWhitespace(line.charAt(end + 1))) end++;
        return new int[] {start, end + 1};
    }

    public boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    public int[] computeWordBoundsSmart(String line, int pos) {
        if (line == null || line.isEmpty()) return new int[] {0, 0};
        int len = line.length();
        int idx = Math.max(0, Math.min(pos, len - 1));
        if (!isWordChar(line.charAt(idx))) {
            if (idx > 0 && isWordChar(line.charAt(idx - 1))) {
                idx = idx - 1;
            } else if (idx + 1 < len && isWordChar(line.charAt(idx + 1))) {
                idx = idx + 1;
            } else {
                return new int[] {idx, idx};
            }
        }
        int start = idx;
        int end = idx;
        while (start > 0 && isWordChar(line.charAt(start - 1))) start--;
        while (end < len - 1 && isWordChar(line.charAt(end + 1))) end++;
        return new int[] {start, end + 1};
    }

    public void updateLocalLine(int localIdx, String text) {
        if (localIdx >= 0 && localIdx < editor.windowRender.linesWindow.size()) {
            editor.windowRender.linesWindow.set(localIdx, text);
            editor.wordWrap.onLineContentChanged(editor.windowRender.windowStartLine + localIdx, text);
            editor.windowRender.clearStreamedLineInfo(editor.windowRender.windowStartLine + localIdx);
        }
    }
}
