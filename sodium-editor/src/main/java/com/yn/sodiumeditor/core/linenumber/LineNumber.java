package com.yn.sodiumeditor.core.linenumber;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.LineNumberCache;
import com.yn.sodiumeditor.renderer.LineNumberRender;
import com.yn.sodiumeditor.utils.GutterUtils;
import com.yn.sodiumeditor.core.wordwrap.WordWrap;
import com.yn.sodiumeditor.utils.FunctionLog;

/**
 * Main facade for line number management, drawing, and selection.
 */
public class LineNumber {
    public final SodiumEditor editor;

    // Components
    public final GutterUtils utils;
    public final LineNumberSelection selection;
    public final LineNumberCache cache;
    public final LineNumberRender render;

    // --- Line Number State ---
    public boolean showLineNumbers = true;
    public boolean lineNumberSelectionEnabled = true;
    public float lineNumbersGutterWidth = 0f;
    public final Paint lineNumbersPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint gutterPaint = new Paint();
    public final Paint gutterSeparatorPaint = new Paint();
    public final Paint currentLinePaint = new Paint();
    public boolean highlightCurrentLineInGutter = true;
    public float gutterSeparatorWidth = 5f;
    public int currentLineNumberColor = 0xFF2196F3;
    public final char[] lineNumberChars = new char[16];
    public static final float GUTTER_TEXT_PADDING = 20f;

    // --- Cache Fields (Restored for compatibility with TextRender) ---
    public Bitmap lineNumberCacheBitmap;
    public Canvas lineNumberCacheCanvas;
    public int lineNumberCacheWidth = 0;
    public int lineNumberCacheHeight = 0;
    public int lineNumberCacheFirstIndex = -1;
    public int lineNumberCacheLastIndex = -1;
    public float lineNumberCacheBaseScrollY = 0f;
    public float lineNumberCacheTextSize = -1f;
    @Nullable public Typeface lineNumberCacheTypeface;
    public boolean lineNumberCacheRtl = false;
    public boolean lineNumberCacheWrapped = false;
    public boolean lineNumberCacheCodeFolding = false;
    public float lineNumberCacheGutterWidth = 0f;
    public float lineNumberCacheFoldMarkerWidth = 0f;
    public float lineNumberCacheLineHeight = 0f;
    public int lineNumberCacheColor = 0;

    public LineNumber(SodiumEditor editor) {
        FunctionLog.f("LineNumber", "LineNumber", editor);
        this.editor = editor;
        this.utils = new GutterUtils(editor, this);
        this.selection = new LineNumberSelection(editor, this);
        this.cache = new LineNumberCache(editor, this);
        this.render = new LineNumberRender(editor, this);

        lineNumbersPaint.setTextAlign(Paint.Align.RIGHT);
        lineNumbersPaint.setColor(0xFF888888);
        lineNumbersPaint.setTextSize(editor.textRender.paint.getTextSize());
        lineNumbersPaint.setTypeface(editor.textRender.paint.getTypeface());
        gutterPaint.setColor(0xFFFAFAFA);
        gutterSeparatorPaint.setColor(0xFF555555);
    }

    // ==============================
    // Public API (State management)
    // ==============================

    public void setShowLineNumbers(boolean show) {
        FunctionLog.f("LineNumber", "setShowLineNumbers", show);
        if (this.showLineNumbers == show) return;
        this.showLineNumbers = show;
        if (!show) {
            if (editor.codeFold.isCodeFoldingEnabled) editor.codeFold.setCodeFoldingEnabled(false);
            if (highlightCurrentLineInGutter) setCurrentLineGutterHighlightEnabled(false);
        }
        invalidateLineNumberCache(); editor.requestLayout();
    }

    public void invalidateLineNumberCache() {
        FunctionLog.f("LineNumber", "invalidateLineNumberCache");
        cache.invalidate();
    }

    public boolean getShowLineNumbers() {
        FunctionLog.f("LineNumber", "getShowLineNumbers");
        return showLineNumbers;
    }

    public void setLineNumberColor(int c) {
        FunctionLog.f("LineNumber", "setLineNumberColor", c);
        lineNumbersPaint.setColor(c); invalidateLineNumberCache(); if (showLineNumbers) editor.invalidate();
    }

    public int getLineNumberColor() {
        FunctionLog.f("LineNumber", "getLineNumberColor");
        return lineNumbersPaint.getColor();
    }

    public void setCurrentLineGutterHighlightEnabled(boolean e) {
        FunctionLog.f("LineNumber", "setCurrentLineGutterHighlightEnabled", e);
        highlightCurrentLineInGutter = e; if (showLineNumbers) editor.invalidate();
    }

    public boolean isCurrentLineGutterHighlightEnabled() {
        FunctionLog.f("LineNumber", "isCurrentLineGutterHighlightEnabled");
        return highlightCurrentLineInGutter;
    }

    public void setLineNumberSelectionEnabled(boolean e) {
        FunctionLog.f("LineNumber", "setLineNumberSelectionEnabled", e);
        lineNumberSelectionEnabled = e;
    }

    public boolean isLineNumberSelectionEnabled() {
        FunctionLog.f("LineNumber", "isLineNumberSelectionEnabled");
        return lineNumberSelectionEnabled;
    }

    public void setGutterBackgroundColor(int c) {
        FunctionLog.f("LineNumber", "setGutterBackgroundColor", c);
        gutterPaint.setColor(c); if (showLineNumbers) editor.invalidate();
    }

    public int getGutterBackgroundColor() {
        FunctionLog.f("LineNumber", "getGutterBackgroundColor");
        return gutterPaint.getColor();
    }

    public void setGutterSeparatorColor(int c) {
        FunctionLog.f("LineNumber", "setGutterSeparatorColor", c);
        gutterSeparatorPaint.setColor(c); if (showLineNumbers) editor.invalidate();
    }

    public int getGutterSeparatorColor() {
        FunctionLog.f("LineNumber", "getGutterSeparatorColor");
        return gutterSeparatorPaint.getColor();
    }

    public void setGutterSeparatorWidth(float w) {
        FunctionLog.f("LineNumber", "setGutterSeparatorWidth", w);
        gutterSeparatorWidth = Math.max(0f, w); editor.requestLayout(); if (showLineNumbers) editor.invalidate();
    }

    public float getGutterSeparatorWidth() {
        FunctionLog.f("LineNumber", "getGutterSeparatorWidth");
        return gutterSeparatorWidth;
    }

    public void setCurrentLineNumberColor(int c) {
        FunctionLog.f("LineNumber", "setCurrentLineNumberColor", c);
        currentLineNumberColor = c; if (showLineNumbers) editor.invalidate();
    }

    public int getCurrentLineNumberColor() {
        FunctionLog.f("LineNumber", "getCurrentLineNumberColor");
        return currentLineNumberColor;
    }

    public void setLineNumberTextSize(float s) {
        FunctionLog.f("LineNumber", "setLineNumberTextSize", s);
        lineNumbersPaint.setTextSize(s); invalidateLineNumberCache(); editor.requestLayout(); if (showLineNumbers) editor.invalidate();
    }

    public float getLineNumberTextSize() {
        FunctionLog.f("LineNumber", "getLineNumberTextSize");
        return lineNumbersPaint.getTextSize();
    }

    public void setLineNumberTypeface(@Nullable Typeface tf) {
        FunctionLog.f("LineNumber", "setLineNumberTypeface", tf);
        lineNumbersPaint.setTypeface(tf != null ? tf : editor.textRender.baseTypeface); invalidateLineNumberCache(); editor.requestLayout(); if (showLineNumbers) editor.invalidate();
    }

    @Nullable public Typeface getLineNumberTypeface() {
        FunctionLog.f("LineNumber", "getLineNumberTypeface");
        return lineNumbersPaint.getTypeface();
    }

    // ==============================
    // Bridge Methods (Delegated)
    // ==============================

    public int writeIntToChars(int v, char[] c) {
        FunctionLog.f("LineNumber", "writeIntToChars", v, c);
        return utils.writeIntToChars(v, c);
    }

    public boolean isInLineNumberGutter(float x) {
        FunctionLog.f("LineNumber", "isInLineNumberGutter", x);
        return selection.isInLineNumberGutter(x);
    }

    public void beginLineNumberSelection(int l) {
        FunctionLog.f("LineNumber", "beginLineNumberSelection", l);
        selection.beginLineNumberSelection(l);
    }

    public void updateLineNumberSelection(int l) {
        FunctionLog.f("LineNumber", "updateLineNumberSelection", l);
        selection.updateLineNumberSelection(l);
    }

    public void endLineNumberSelection() {
        FunctionLog.f("LineNumber", "endLineNumberSelection");
        selection.endLineNumberSelection();
    }

    public void updateGutterWidth() {
        FunctionLog.f("LineNumber", "updateGutterWidth");
        float old = lineNumbersGutterWidth;
        lineNumbersGutterWidth = utils.calculateGutterWidth();
        if (editor.wordWrap.isWordWrapEnabled && Math.abs(lineNumbersGutterWidth - old) > 0.1f) {
            editor.wordWrap.invalidateWrapMetrics(true); editor.wordWrap.requestWrapPrefixRebuild();
        }
        if (Math.abs(lineNumbersGutterWidth - old) > 0.1f) { invalidateLineNumberCache(); editor.requestLayout(); editor.invalidate(); }
    }

    public void drawCurrentLineHighlightInGutter(Canvas c, float t, float b) {
        FunctionLog.f("LineNumber", "drawCurrentLineHighlightInGutter", c, t, b);
        render.drawCurrentLineHighlightInGutter(c, t, b);
    }

    public void drawLineNumbersDirectUnwrapped(Canvas c, int fI, int lI, int fL, int lL) {
        FunctionLog.f("LineNumber", "drawLineNumbersDirectUnwrapped", c, fI, lI, fL, lL);
        render.drawLineNumbersDirectUnwrapped(c, fI, lI, fL, lL);
    }

    public void drawLineNumbersDirectWrapped(Canvas c, int fV, int lV) {
        FunctionLog.f("LineNumber", "drawLineNumbersDirectWrapped", c, fV, lV);
        render.drawLineNumbersDirectWrapped(c, fV, lV);
    }

    public boolean shouldUseLineNumberCache() {
        FunctionLog.f("LineNumber", "shouldUseLineNumberCache");
        return cache.shouldUseCache();
    }

    public void ensureLineNumberCacheBitmap(int w, int h) {
        FunctionLog.f("LineNumber", "ensureLineNumberCacheBitmap", w, h);
        cache.ensureBitmap(w, h);
    }

    public void drawCurrentlineNumberUnwrapped(Canvas c, int fI, int lI) {
        FunctionLog.f("LineNumber", "drawCurrentlineNumberUnwrapped", c, fI, lI);
        drawCurrentLineNumberUnwrapped(c, fI, lI);
    }

    public void drawCurrentlineNumberWrapped(Canvas c, int fV, int lV) {
        FunctionLog.f("LineNumber", "drawCurrentlineNumberWrapped", c, fV, lV);
        drawCurrentLineNumberWrapped(c, fV, lV);
    }

    public float getGutterStartX() {
        FunctionLog.f("LineNumber", "getGutterStartX");
        return editor.textRender.isRtl ? editor.getWidth() - lineNumbersGutterWidth : 0;
    }

    public void drawLineNumbersCachedUnwrapped(Canvas canvas, int fI, int lI, int fL, int lL) {
        FunctionLog.f("LineNumber", "drawLineNumbersCachedUnwrapped", canvas, fI, lI, fL, lL);
        if (!cache.shouldUseCache()) { render.drawLineNumbersDirectUnwrapped(canvas, fI, lI, fL, lL); return; }
        int drawLastI = editor.codeFold.isCodeFoldingEnabled ? Math.min(lI + 1, Math.max(0, editor.codeFold.getVisibleLineCount() - 1)) : lI;
        int drawLastL = !editor.codeFold.isCodeFoldingEnabled ? Math.min(lL + 1, Math.max(0, editor.view.getLinesCount() - 1)) : lL;
        int gw = Math.max(1, Math.round(lineNumbersGutterWidth));
        float pad = editor.textRender.lineHeight;
        int h = editor.getHeight() + Math.round(pad * 2f);
        float baseY = (float) Math.floor(editor.scroll.scrollY / pad) * pad - pad;

        if (cache.needsRebuild(gw, h, fI, drawLastI, baseY, false)) {
            cache.ensureBitmap(gw, h);
            if (lineNumberCacheCanvas == null) { render.drawLineNumbersDirectUnwrapped(canvas, fI, lI, fL, lL); return; }
            lineNumberCacheBitmap.eraseColor(0);
            float lineNumX = render.getLineNumXUnwrapped() - getGutterStartX();
            if (editor.codeFold.isCodeFoldingEnabled) {
                for (int v = fI; v <= drawLastI; v++) render.drawSingleLineNumber(lineNumberCacheCanvas, editor.codeFold.mapVisibleIndexToGlobal(v) + 1, lineNumX, v * pad - baseY);
            } else {
                for (int i = fL; i <= drawLastL; i++) render.drawSingleLineNumber(lineNumberCacheCanvas, i + 1, lineNumX, i * pad - baseY);
            }
            cache.updateMetadata(fI, drawLastI, baseY, false);
        }
        if (lineNumberCacheBitmap != null) canvas.drawBitmap(lineNumberCacheBitmap, getGutterStartX(), baseY - editor.scroll.scrollY, null);
        drawCurrentLineNumberUnwrapped(canvas, fI, lI);
    }

    public void drawLineNumbersCachedWrapped(Canvas canvas, int fV, int lV) {
        FunctionLog.f("LineNumber", "drawLineNumbersCachedWrapped", canvas, fV, lV);
        if (!cache.shouldUseCache()) { render.drawLineNumbersDirectWrapped(canvas, fV, lV); return; }
        int drawLastV = Math.min(lV + 1, Math.max(0, editor.wordWrap.getTotalVisualLineCount() - 1));
        int gw = Math.max(1, Math.round(lineNumbersGutterWidth));
        float pad = editor.textRender.lineHeight;
        int h = editor.getHeight() + Math.round(pad * 2f);
        float baseY = (float) Math.floor(editor.scroll.scrollY / pad) * pad - pad;

        if (cache.needsRebuild(gw, h, fV, drawLastV, baseY, true)) {
            cache.ensureBitmap(gw, h);
            if (lineNumberCacheCanvas == null) { render.drawLineNumbersDirectWrapped(canvas, fV, lV); return; }
            lineNumberCacheBitmap.eraseColor(0);
            float lineNumX = render.getLineNumXWrapped() - getGutterStartX();
            for (int v = fV; v <= drawLastV; v++) {
                WordWrap.VisualLinePosition p = editor.wordWrap.getVisualPositionForIndex(v);
                if (p.segment == 0) render.drawSingleLineNumber(lineNumberCacheCanvas, p.line + 1, lineNumX, v * pad - baseY);
            }
            cache.updateMetadata(fV, drawLastV, baseY, true);
        }
        if (lineNumberCacheBitmap != null) canvas.drawBitmap(lineNumberCacheBitmap, getGutterStartX(), baseY - editor.scroll.scrollY, null);
        drawCurrentLineNumberWrapped(canvas, fV, lV);
    }

    public void drawCurrentLineNumberUnwrapped(Canvas canvas, int fI, int lI) {
        FunctionLog.f("LineNumber", "drawCurrentLineNumberUnwrapped", canvas, fI, lI);
        if (!showLineNumbers || editor.selection.hasSelection) return;
        if (editor.codeFold.isCodeFoldingEnabled && editor.codeFold.isLineHiddenByFold(editor.cursor.cursorLine)) return;
        int vI = editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.getVisibleIndexForGlobalLine(editor.cursor.cursorLine) : editor.cursor.cursorLine;
        if (vI < fI || vI > lI) return;
        int orig = lineNumbersPaint.getColor(); lineNumbersPaint.setColor(currentLineNumberColor);
        render.drawSingleLineNumber(canvas, editor.cursor.cursorLine + 1, render.getLineNumXUnwrapped(), vI * editor.textRender.lineHeight - editor.scroll.scrollY);
        lineNumbersPaint.setColor(orig);
    }

    public void drawCurrentLineNumberWrapped(Canvas canvas, int fV, int lV) {
        FunctionLog.f("LineNumber", "drawCurrentLineNumberWrapped", canvas, fV, lV);
        if (!showLineNumbers || editor.selection.hasSelection) return;
        int vI = editor.wordWrap.getVisualIndexForLineAndChar(editor.cursor.cursorLine, 0);
        if (vI < fV || vI > lV) return;
        int orig = lineNumbersPaint.getColor(); lineNumbersPaint.setColor(currentLineNumberColor);
        render.drawSingleLineNumber(canvas, editor.cursor.cursorLine + 1, render.getLineNumXWrapped(), vI * editor.textRender.lineHeight - editor.scroll.scrollY);
        lineNumbersPaint.setColor(orig);
    }
}
