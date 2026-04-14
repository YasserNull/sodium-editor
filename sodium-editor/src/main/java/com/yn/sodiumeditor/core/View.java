package com.yn.sodiumeditor.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import com.yn.sodiumeditor.SodiumEditor;
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

    public final AtomicInteger goToLineVersion = new AtomicInteger(0);

    public View(SodiumEditor editor) {
        this.editor = editor;
    }

    // ============================================================================
    // Performance Mode
    // ============================================================================

    public void setPerformanceModeEnabled(boolean enabled) {
        if (editor.textRender.isPerformanceModeEnabled == enabled) return;
        editor.textRender.isPerformanceModeEnabled = enabled;
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
            editor.codeFold.setIndentationBlocksEnabled(false);
            editor.codeFold.setCodeFoldingEnabled(false);
        }
        editor.invalidate();
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
            Log.e("SodiumEditor", "setEditorBackgroundImageFromAssets failed: " + assetPath, e);
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
            Log.e("SodiumEditor", "setEditorBackgroundImageFromFile failed: " + filePath, e);
        }
    }

    // ============================================================================
    // Font
    // ============================================================================

    public void setFontFromAssets(String assetPath, int style) {
        try {
            Typeface tf = Typeface.createFromAsset(editor.getContext().getAssets(), assetPath);
            editor.textRender.applyTypeface(tf, style);
        } catch (Exception e) {
            Log.e("SodiumEditor", "setFontFromAssets failed: " + assetPath, e);
        }
    }

    public void setFontFromFile(String filePath, int style) {
        try {
            Typeface tf = Typeface.createFromFile(filePath);
            editor.textRender.applyTypeface(tf, style);
        } catch (Exception e) {
            Log.e("SodiumEditor", "setFontFromFile failed: " + filePath, e);
        }
    }

    // ============================================================================
    // Read-Only / Disable
    // ============================================================================

    public void setReadOnly(boolean readOnly) {
        if (editor.isReadOnly == readOnly) return;
        editor.isReadOnly = readOnly;
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
        editor.isDisabled = disable;
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
            synchronized (editor.textRender.linesWindow) {
                knownTotal = Math.max(1, editor.textRender.windowStartLine + editor.textRender.linesWindow.size());
            }
        } else if (editor.fileIO.isIndexReady) {
            synchronized (editor.fileIO.lineOffsetsLock) {
                knownTotal = Math.max(1, editor.fileIO.lineOffsets.length);
            }
        } else if (editor.fileIO.isEof) {
            synchronized (editor.textRender.linesWindow) {
                knownTotal = Math.max(1, editor.textRender.windowStartLine + editor.textRender.linesWindow.size());
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
            && !(targetLine >= editor.textRender.windowStartLine && targetLine < editor.textRender.windowStartLine + editor.textRender.linesWindow.size())) {
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

                if (editor.cursor.cursorLine >= editor.textRender.windowStartLine
                    && editor.cursor.cursorLine < editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) {
                    String lineText = editor.textRender.getLineTextForRender(editor.cursor.cursorLine);
                    editor.cursor.cursorChar = Math.max(0, Math.min(targetCol, lineText.length()));
                } else if (editor.fileIO.isEof) {
                    int lastLineInDoc = editor.textRender.windowStartLine + editor.textRender.linesWindow.size() - 1;
                    if (editor.cursor.cursorLine > lastLineInDoc) editor.cursor.cursorLine = Math.max(0, lastLineInDoc);
                    String lineText = editor.textRender.getLineTextForRender(editor.cursor.cursorLine);
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
                        editor.showKeyboard();
                        editor.requestFocus();
                        InputMethodManager imm =
                            (InputMethodManager)
                                editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.restartInput(editor);
                    });
            };

        if (editor.fileIO.isFileCleared
            || editor.fileIO.sourceFile == null
            || (targetLine >= editor.textRender.windowStartLine && targetLine < editor.textRender.windowStartLine + editor.textRender.linesWindow.size())) {
            completionAction.run();
        } else {
            int targetStart = Math.max(0, targetLine - editor.textRender.prefetchLines);
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
}
