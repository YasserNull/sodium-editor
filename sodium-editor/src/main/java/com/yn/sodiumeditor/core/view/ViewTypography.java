package com.yn.sodiumeditor.core.view;

import android.graphics.Typeface;

public class ViewTypography {
  private final EditorView view;

  ViewTypography(EditorView view) {
    this.view = view;
  }

  public void applyTypeface(@androidx.annotation.Nullable Typeface typeface, int style) {
    if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
      final Typeface tf = typeface;
      final int st = style;
      view.editor.post(() -> applyTypeface(tf, st));
      return;
    }
    Typeface safeBase = (typeface != null) ? typeface : Typeface.DEFAULT;
    view.editor.textRender.baseTypeface = safeBase;
    int typefaceStyle;
    switch (style) {
      case FontStyle.STYLE_BOLD:
        typefaceStyle = Typeface.BOLD;
        break;
      case FontStyle.STYLE_ITALIC:
        typefaceStyle = Typeface.ITALIC;
        break;
      case FontStyle.STYLE_BOLD_ITALIC:
        typefaceStyle = Typeface.BOLD_ITALIC;
        break;
      default:
        typefaceStyle = Typeface.NORMAL;
        break;
    }
    Typeface finalTypeface = Typeface.create(safeBase, typefaceStyle);
    view.editor.textRender.paint.setTypeface(finalTypeface);
    view.editor.autoSuggestion.suggestionPaint.setTypeface(finalTypeface);
    view.editor.lineNumber.lineNumbersPaint.setTypeface(finalTypeface);
    view.editor.wordWrap.indicator.wordWrapIndicatorPaint.setTypeface(finalTypeface);
    if (view.editor.highlightRules.whitespaceStringRule != null)
      view.editor.highlightRules.whitespaceStringRule.updateTypeface(safeBase);
    if (view.editor.highlightRules.whitespaceCommentRule != null)
      view.editor.highlightRules.whitespaceCommentRule.updateTypeface(safeBase);
    if (view.editor.highlightRules.lineCommentHighlightRule != null)
      view.editor.highlightRules.lineCommentHighlightRule.updateTypeface(safeBase);
    for (com.yn.sodiumeditor.renderer.HighlightRender.HighlightRule rule :
        view.editor.highlight.highlightRules) {
      rule.updateTypeface(safeBase);
    }
    view.editor.highlight.clearHighlightCaches();

    view.editor.textRender.lineHeight = view.editor.textRender.paint.getFontSpacing();
    view.editor.whitespaceGuides.updateMetrics();
    view.editor.lineNumber.invalidateLineNumberCache();
    view.editor.wordWrap.indicator.wordWrapIndicatorWidth =
        view.editor.wordWrap.indicator.wordWrapIndicatorPaint.measureText(
            view.editor.wordWrap.indicator.getWordWrapIndicatorText());

    view.editor.textRender.clearCachesOnTypefaceChange();
    view.editor.windowRender.lineWidthCache.clear();
    view.editor.windowRender.avgCharWidthCache.clear();

    view.editor.windowRender.currentMaxWindowLineWidth = 0f;
    view.editor.windowRender.globalMaxLineWidth = 0f;
    view.editor.scroll.maxLineWidthForScroll = 0f;
    view.editor.scroll.maxTextStartXForScroll = 0f;
    view.editor.scroll.maxScrollXForScroll = 0f;
    view.editor.windowRender.recalculateMaxLineWidth();

    view.editor.requestLayout();
    if (view.editor.wordWrap.isWordWrapEnabled) view.editor.wordWrap.invalidateWrapMetrics(true);
    view.editor.wordWrap.requestWrapPrefixRebuild();
    view.editor.invalidate();
  }

  public void setFontFromAssets(String assetPath, int style) {
    try {
      Typeface tf = Typeface.createFromAsset(view.editor.getContext().getAssets(), assetPath);
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

  public void applyTextSizePx(float sizePx) {
    applyTextSizePx(sizePx, false);
  }

  public void applyTextSizePx(float sizePx, boolean deferWrapRebuild) {
    float oldSize = view.editor.textRender.paint.getTextSize();
    if (Math.abs(sizePx - oldSize) < 0.1f) return;

    view.editor.textRender.paint.setTextSize(sizePx);
    view.editor.binaryRender.updateCachedCharWidth(view.editor.textRender.paint);

    if (!view.editor.autoSuggestion.isSuggestionTextSizeCustom) {
      view.editor.autoSuggestion.suggestionTextSizeScale = 1f;
    }
    view.editor.autoSuggestion.suggestionPaint.setTextSize(
        sizePx * view.editor.autoSuggestion.suggestionTextSizeScale);
    view.editor.lineNumber.lineNumbersPaint.setTextSize(sizePx);
    view.editor.wordWrap.indicator.wordWrapIndicatorPaint.setTextSize(
        sizePx * view.editor.wordWrap.indicator.wordWrapIndicatorTextScale);
    view.editor.wordWrap.indicator.wordWrapIndicatorPaint.setTypeface(
        view.editor.textRender.paint.getTypeface());
    view.editor.wordWrap.indicator.wordWrapIndicatorWidth =
        view.editor.wordWrap.indicator.wordWrapIndicatorPaint.measureText(
            view.editor.wordWrap.indicator.getWordWrapIndicatorText());
    view.editor.textRender.lineHeight = view.editor.textRender.paint.getFontSpacing();
    view.editor.windowRender.recalculateMaxLineWidth();
    view.editor.whitespaceGuides.updateMetrics();
    view.editor.cursorHandle.updateHandleMetricsForTextSize(sizePx);
    view.editor.selectionHandles.updateHandleMetricsForTextSize(sizePx);
    view.editor.lineNumber.invalidateLineNumberCache();

    view.editor.textRender.clearCachesOnTypefaceChange();
    view.editor.windowRender.lineWidthCache.clear();
    view.editor.windowRender.avgCharWidthCache.clear();

    for (com.yn.sodiumeditor.renderer.HighlightRender.HighlightRule rule :
        view.editor.highlight.highlightRules) {
      rule.updateTextSize(sizePx);
    }
    if (view.editor.highlightRules.whitespaceStringRule != null)
      view.editor.highlightRules.whitespaceStringRule.updateTextSize(sizePx);
    if (view.editor.highlightRules.whitespaceCommentRule != null)
      view.editor.highlightRules.whitespaceCommentRule.updateTextSize(sizePx);
    if (view.editor.highlightRules.lineCommentHighlightRule != null)
      view.editor.highlightRules.lineCommentHighlightRule.updateTextSize(sizePx);
    view.editor.highlight.clearHighlightCaches();

    float scale = sizePx / oldSize;
    view.editor.windowRender.currentMaxWindowLineWidth *= scale;
    view.editor.windowRender.globalMaxLineWidth *= scale;
    view.editor.scroll.maxLineWidthForScroll *= scale;
    view.editor.scroll.maxScrollXForScroll *= scale;
    view.editor.scroll.maxTextStartXForScroll = 0f;
    if (scale < 1f) {
      view.editor.scroll.maxLineWidthForScroll = 0f;
      view.editor.scroll.maxScrollXForScroll = 0f;
    }

    view.editor.requestLayout();
    if (view.editor.wordWrap.isWordWrapEnabled)
      view.editor.wordWrap.invalidateWrapMetrics(true, !deferWrapRebuild);
    view.editor.wordWrap.requestWrapPrefixRebuild();
    view.editor.invalidate();
  }

  public void applyTextSizePxForZoomFrame(float sizePx) {
    float oldSize = view.editor.textRender.paint.getTextSize();
    if (Math.abs(sizePx - oldSize) < 0.1f) return;

    view.editor.textRender.paint.setTextSize(sizePx);
    view.editor.binaryRender.updateCachedCharWidth(view.editor.textRender.paint);

    if (!view.editor.autoSuggestion.isSuggestionTextSizeCustom) {
      view.editor.autoSuggestion.suggestionTextSizeScale = 1f;
    }
    view.editor.autoSuggestion.suggestionPaint.setTextSize(
        sizePx * view.editor.autoSuggestion.suggestionTextSizeScale);
    view.editor.lineNumber.lineNumbersPaint.setTextSize(sizePx);
    view.editor.wordWrap.indicator.wordWrapIndicatorPaint.setTextSize(
        sizePx * view.editor.wordWrap.indicator.wordWrapIndicatorTextScale);
    view.editor.wordWrap.indicator.wordWrapIndicatorPaint.setTypeface(
        view.editor.textRender.paint.getTypeface());
    view.editor.wordWrap.indicator.wordWrapIndicatorWidth =
        view.editor.wordWrap.indicator.wordWrapIndicatorPaint.measureText(
            view.editor.wordWrap.indicator.getWordWrapIndicatorText());
    view.editor.textRender.lineHeight = view.editor.textRender.paint.getFontSpacing();
    view.editor.whitespaceGuides.updateMetrics();
    view.editor.cursorHandle.updateHandleMetricsForTextSize(sizePx);
    view.editor.selectionHandles.updateHandleMetricsForTextSize(sizePx);
    view.editor.lineNumber.invalidateLineNumberCache();
    view.editor.textRender.clearCachesOnTypefaceChange();

    for (com.yn.sodiumeditor.renderer.HighlightRender.HighlightRule rule :
        view.editor.highlight.highlightRules) {
      rule.updateTextSize(sizePx);
    }
    if (view.editor.highlightRules.whitespaceStringRule != null)
      view.editor.highlightRules.whitespaceStringRule.updateTextSize(sizePx);
    if (view.editor.highlightRules.whitespaceCommentRule != null)
      view.editor.highlightRules.whitespaceCommentRule.updateTextSize(sizePx);
    if (view.editor.highlightRules.lineCommentHighlightRule != null)
      view.editor.highlightRules.lineCommentHighlightRule.updateTextSize(sizePx);

    float scale = sizePx / oldSize;
    view.editor.windowRender.currentMaxWindowLineWidth *= scale;
    view.editor.windowRender.globalMaxLineWidth *= scale;
    view.editor.scroll.maxLineWidthForScroll *= scale;
    view.editor.scroll.maxScrollXForScroll *= scale;
    view.editor.scroll.maxTextStartXForScroll = 0f;
    clearVisibleLineMetricCaches();
    view.editor.invalidate();
  }

  public void finishZoomTextSizeUpdate() {
    synchronized (view.editor.windowRender.lineWidthCache) {
      view.editor.windowRender.lineWidthCache.clear();
    }
    synchronized (view.editor.windowRender.avgCharWidthCache) {
      view.editor.windowRender.avgCharWidthCache.clear();
    }
    view.editor.textRender.clearCachesOnTypefaceChange();
    view.editor.lineNumber.invalidateLineNumberCache();
    view.editor.requestLayout();
    view.editor.invalidate();
  }

  public void clearVisibleLineMetricCaches() {
    if (view.editor.textRender.lineHeight <= 0f) return;
    int firstVisibleIndex =
        Math.max(0, (int) (view.editor.scroll.scrollY / view.editor.textRender.lineHeight));
    int lastVisibleIndex =
        firstVisibleIndex
            + (int) Math.ceil(view.editor.getHeight() / view.editor.textRender.lineHeight)
            + 1;
    int firstLine;
    int lastLine;
    if (view.editor.wordWrap.isWordWrapEnabled) {
      firstLine = view.editor.wordWrap.getVisualPositionForIndex(firstVisibleIndex).line;
      lastLine = view.editor.wordWrap.getVisualPositionForIndex(lastVisibleIndex).line;
    } else {
      firstLine = firstVisibleIndex;
      lastLine = lastVisibleIndex;
    }
    synchronized (view.editor.windowRender.lineWidthCache) {
      for (int line = Math.max(0, firstLine); line <= Math.max(firstLine, lastLine); line++) {
        view.editor.windowRender.lineWidthCache.remove(line);
      }
    }
    synchronized (view.editor.windowRender.avgCharWidthCache) {
      for (int line = Math.max(0, firstLine); line <= Math.max(firstLine, lastLine); line++) {
        view.editor.windowRender.avgCharWidthCache.remove(line);
      }
    }
  }
}
