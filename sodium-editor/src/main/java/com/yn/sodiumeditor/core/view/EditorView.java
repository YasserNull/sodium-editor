package com.yn.sodiumeditor.core.view;

import android.view.inputmethod.InputMethodManager;
import com.yn.sodiumeditor.SodiumEditor;
import java.util.concurrent.atomic.AtomicInteger;

/** Main view facade and coordinator for SodiumEditor subsystems. */
public class EditorView {
  final SodiumEditor editor;

  public final ViewBackground background;
  public final ViewTypography typography;
  public final ViewNavigation navigation;
  public final ViewMetrics metrics;
  public final ViewIndentation indentation;
  public final ViewWordSelection wordSelection;

  public final int[] tmpLocationInWindow = new int[2];
  public final android.graphics.Rect visibleDisplayFrame = new android.graphics.Rect();
  public int keyboardHeight = 0;

  public boolean isDisabled = false;
  public boolean isReadOnly = false;

  public int heavyFeaturesThreshold = 50000;

  public final Runnable delayedWindowCheck;
  public final AtomicInteger goToLineVersion = new AtomicInteger(0);

  public boolean hasEditorBackgroundColor = false;
  public int editorBackgroundColor = 0x00000000;
  @androidx.annotation.Nullable public android.graphics.Bitmap editorBackgroundBitmap = null;
  public final android.graphics.Rect editorBackgroundDst = new android.graphics.Rect();

  public boolean isPerformanceModeEnabled = false;
  public boolean isStableGlyphPositionsEnabled = true;
  public static final int DEFAULT_TAB_SIZE_SPACES = 4;

  public final android.graphics.Path teardropPath = new android.graphics.Path();

  public float[] guideSeenXBuffer;
  public float[] whitespaceWidthBuffer;
  public float[] whitespaceDotBuffer;
  public float[] measureWidthBuffer;

  public static final class WhitespaceDrawState {
    public int syntaxIndex;
  }

  public final WhitespaceDrawState whitespaceDrawState = new WhitespaceDrawState();

  public EditorView(SodiumEditor editor) {
    this.editor = editor;
    this.background = new ViewBackground(this);
    this.typography = new ViewTypography(this);
    this.navigation = new ViewNavigation(this);
    this.metrics = new ViewMetrics(this);
    this.indentation = new ViewIndentation(this);
    this.wordSelection = new ViewWordSelection(this);
    this.delayedWindowCheck = navigation.delayedWindowCheck;

    hasEditorBackgroundColor = true;
    editorBackgroundColor = 0xFFFFFFFF;
  }

  public void drawEditorBackground(android.graphics.Canvas canvas) {
    background.drawEditorBackground(canvas);
  }

  public void setEditorBackgroundColor(int color) {
    background.setEditorBackgroundColor(color);
  }

  public void clearEditorBackgroundColor() {
    background.clearEditorBackgroundColor();
  }

  public void setEditorBackgroundBitmap(android.graphics.Bitmap bitmap) {
    background.setEditorBackgroundBitmap(bitmap);
  }

  public void clearEditorBackgroundImage() {
    background.clearEditorBackgroundImage();
  }

  public void setEditorBackgroundImageFromAssets(String assetPath) {
    background.setEditorBackgroundImageFromAssets(assetPath);
  }

  public void setEditorBackgroundImageFromFile(String filePath) {
    background.setEditorBackgroundImageFromFile(filePath);
  }

  public void applyTypeface(
      @androidx.annotation.Nullable android.graphics.Typeface typeface, int style) {
    typography.applyTypeface(typeface, style);
  }

  public void setFontFromAssets(String assetPath, int style) {
    typography.setFontFromAssets(assetPath, style);
  }

  public void setFontFromFile(String filePath, int style) {
    typography.setFontFromFile(filePath, style);
  }

  public void applyTextSizePx(float sizePx) {
    typography.applyTextSizePx(sizePx);
  }

  public void applyTextSizePx(float sizePx, boolean deferWrapRebuild) {
    typography.applyTextSizePx(sizePx, deferWrapRebuild);
  }

  public void applyTextSizePxForZoomFrame(float sizePx) {
    typography.applyTextSizePxForZoomFrame(sizePx);
  }

  public void finishZoomTextSizeUpdate() {
    typography.finishZoomTextSizeUpdate();
  }

  public void setPerformanceModeEnabled(boolean enabled) {
    if (this.isPerformanceModeEnabled == enabled) return;
    this.isPerformanceModeEnabled = enabled;
    if (enabled) {
      editor.urlUnderline.setUrlUnderliningEnabled(false);
      editor.pathUnderline.setPathUnderliningEnabled(false);
      editor.colorCodeHighlight.setColorCodeHighlightingEnabled(false);
      editor.symbolsMatch.setSymbolsMatchingEnabled(false);
      editor.bracketGuides.setBracketGuidesEnabled(false);
      editor.indentGuides.setIndentGuidesEnabled(false);
      editor.whitespaceGuides.setWhitespaceGuidesEnabled(false);
      editor.wordWrap.setWordWrapIndicatorEnabled(false);
      editor.autoSuggestion.setAutoSuggestionEnabled(false);
      editor.autoPathSuggestion.setAutoPathSuggestionEnabled(false);
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
    int idx = false ? globalLine : globalLine;
    float top = (idx * editor.textRender.lineHeight) - editor.scroll.scrollY;
    editor.invalidate(
        0,
        (int) Math.floor(top),
        editor.getWidth(),
        (int) Math.ceil(top + editor.textRender.lineHeight));
  }

  public void release() {
    editor.fileIO.cancelAndCloseReader();
    if (editor.charAnimation.charAnimAnimator != null)
      editor.charAnimation.charAnimAnimator.cancel();
    if (editor.charAnimation.delAnimAnimator != null) editor.charAnimation.delAnimAnimator.cancel();
    editor.removeCallbacks(editor.cursorAnimation.cursorAnimStep);
    editor.fileIO.ioThread.quitSafely();
  }

  public void setReadOnly(boolean readOnly) {
    if (isReadOnly == readOnly) return;
    isReadOnly = readOnly;
    if (readOnly) {
      editor.autoSuggestion.clearActiveSuggestion();
      editor.selection.hasSelection = false;
      editor.selection.isSelectAllActive = false;
      editor.selection.isEntireFileSelected = false;
      InputMethodManager imm =
          (InputMethodManager) editor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
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
        (InputMethodManager) editor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.restartInput(editor);
    }
  }

  public void goToLine(int line) {
    navigation.goToLine(line);
  }

  public void goToLine(int line, int col) {
    navigation.goToLine(line, col);
  }

  public void proceedGoToLineClamped(
      final int currentGoToLineVersion, final int targetLine, final int targetCol) {
    navigation.proceedGoToLineClamped(currentGoToLineVersion, targetLine, targetCol);
  }

  public float spToPx(float sp) {
    return sp * editor.getResources().getDisplayMetrics().scaledDensity;
  }

  public float scaleByTextSize(float baseValue, float baseTextSizePx, float newTextSizePx) {
    if (baseTextSizePx <= 0f) return baseValue;
    return baseValue * (newTextSizePx / baseTextSizePx);
  }

  public int getLinesCount() {
    return metrics.getLinesCount();
  }

  public int getLogicalLineLength(int globalLine, String line) {
    return metrics.getLogicalLineLength(globalLine, line);
  }

  public void computeWidthForLine(int globalIndex, String line) {
    metrics.computeWidthForLine(globalIndex, line);
  }

  public float getWidthForLine(int globalIndex, String line) {
    return metrics.getWidthForLine(globalIndex, line);
  }

  public int getCharIndexForX(String text, float x, int globalLine) {
    return metrics.getCharIndexForX(text, x, globalLine);
  }

  public long computeByteOffsetInLineUtf8(String lineText, int charIndex) {
    return metrics.computeByteOffsetInLineUtf8(lineText, charIndex);
  }

  public void updateLocalLine(int localIdx, String text) {
    metrics.updateLocalLine(localIdx, text);
  }

  public static String buildIndentFromWidth(int width) {
    return ViewIndentation.buildIndentFromWidth(width);
  }

  public int getIndentWidth(String line) {
    return indentation.getIndentWidth(line);
  }

  public String getLineLeadingWhitespace(int line) {
    return indentation.getLineLeadingWhitespace(line);
  }

  public int getBraceGuideColumnForLine(
      String line, int globalLine, int braceIndex, int firstNonSpace) {
    return indentation.getBraceGuideColumnForLine(line, globalLine, braceIndex, firstNonSpace);
  }

  public int getPreviousNonEmptyIndentColumn(int line) {
    return indentation.getPreviousNonEmptyIndentColumn(line);
  }

  public int[] computeWordBounds(String line, int pos) {
    return wordSelection.computeWordBounds(line, pos);
  }

  public boolean isWordChar(char c) {
    return wordSelection.isWordChar(c);
  }

  public int[] computeWordBoundsSmart(String line, int pos) {
    return wordSelection.computeWordBoundsSmart(line, pos);
  }
}
