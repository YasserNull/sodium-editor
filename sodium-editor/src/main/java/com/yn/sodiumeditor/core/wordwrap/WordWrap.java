package com.yn.sodiumeditor.core.wordwrap;

import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.io.EditOp;
import com.yn.sodiumeditor.renderer.TextRender;
import com.yn.sodiumeditor.utils.WordWrapUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main facade for Word Wrap functionality in SodiumEditor.
 */
public class WordWrap {
    private final SodiumEditor editor;
    public final Handler mainHandler;

    // Components
    public final WordWrapIndicator indicator;
    public final WordWrapUtils calculator;
    public final WordWrapMetrics metrics;
    public final WordWrapPosition position;

    // --- State (Kept as fields for project compatibility) ---
    public boolean isWordWrapEnabled = false;
    public int wrapWidthPx = -1;
    public final HashMap<Integer, int[]> wrapCache = new HashMap<>();
    public volatile int[] wrapLineCounts = null;
    public volatile int[] wrapLinePrefix = null;
    public volatile int wrapPrefixValidUpToLine = -1;
    public volatile int totalWrapVisualLines = 0;
    public volatile boolean wrapMetricsReady = false;
    public volatile int wrapMetricsWidth = -1;
    public final AtomicInteger wrapMetricsToken = new AtomicInteger(0);
    public volatile boolean wrapMetricsBuilding = false;
    public final AtomicInteger wrapSnapshotToken = new AtomicInteger(0);
    public volatile boolean wrapSnapshotBuilding = false;
    public volatile int wrapSnapshotWidth = -1;
    public volatile int wrapSnapshotStart = -1;
    public volatile int wrapSnapshotSize = -1;
    public final AtomicInteger wrapPrefixToken = new AtomicInteger(0);
    public volatile boolean wrapPrefixBuilding = false;
    public volatile int wrapPrefixWidth = -1;
    public volatile int wrapPrefixTargetLine = -1;
    public boolean wrapPrefixRebuildPending = false;

    public static class VisualLinePosition {
        public final int line, segment;
        public VisualLinePosition(int l, int s) { line = l; segment = s; }
    }

    public WordWrap(SodiumEditor editor) {
        this.editor = editor;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.indicator = new WordWrapIndicator(editor);
        this.calculator = new WordWrapUtils(editor);
        this.metrics = new WordWrapMetrics(editor, this);
        this.position = new WordWrapPosition(editor, this);
    }

    // ==============================
    // Public API
    // ==============================

    public void setWordWrapEnabled(boolean e) {
        if (this.isWordWrapEnabled == e) return;
        this.isWordWrapEnabled = e; invalidateWrapMetrics();
        if (e) { editor.scroll.scrollX = 0f; editor.scroll.clampScrollX(); editor.windowRender.clearStreamedLineCaches(); editor.windowRender.reloadWindowAroundVisible(false); }
        editor.requestLayout(); editor.invalidate();
    }

    public void setWordWrapIndicatorEnabled(boolean enabled) { indicator.setWordWrapIndicatorEnabled(enabled); }
    public void setWordWrapIndicatorColor(int color) { indicator.setWordWrapIndicatorColor(color); }
    public void setWordWrapIndicatorTextSize(float sizeSp) { indicator.setWordWrapIndicatorTextSize(sizeSp); }

    public float getWrapWidth() { return Math.max(1f, editor.getWidth() - editor.layout.getTextStartX()); }

    public void invalidateWrapMetrics() { invalidateWrapMetrics(true, true); }
    public void invalidateWrapMetrics(boolean clear) { invalidateWrapMetrics(clear, true); }
    public void invalidateWrapMetrics(boolean clear, boolean fullRebuild) {
        wrapCache.clear(); wrapWidthPx = -1; wrapMetricsWidth = -1; wrapMetricsToken.incrementAndGet(); wrapPrefixValidUpToLine = -1;
        if (clear) { wrapLineCounts = null; wrapLinePrefix = null; }
        buildWrapMetricsForWindowSnapshot();
        if (isWordWrapEnabled) {
            if (fullRebuild) scheduleWrapMetricsBuild();
            else {
                int w = Math.max(1, Math.round(getWrapWidth()));
                scheduleWrapMetricsSnapshotIfNeeded(w); scheduleWrapPrefixRebuildUpToWindow();
            }
        }
    }

    // ==============================
    // Bridge Methods (Delegated)
    // ==============================

    public void scheduleWrapMetricsBuild() { metrics.scheduleWrapMetricsBuild(); }
    public void buildWrapMetricsForWindowSnapshot() {
        int total = editor.view.getLinesCount();
        if (total <= 0) total = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size();
        if (total <= 0) { wrapLineCounts = null; wrapLinePrefix = null; totalWrapVisualLines = 0; wrapMetricsReady = true; return; }
        int widthPx = Math.max(1, Math.round(getWrapWidth()));
        int[] counts = (wrapLineCounts != null && wrapLineCounts.length == total) ? wrapLineCounts.clone() : null;
        if (counts == null) { counts = new int[total]; for (int i = 0; i < total; i++) counts[i] = getDefaultWrapCountForLine(i); }
        synchronized (editor.windowRender.linesWindow) {
            int start = editor.windowRender.windowStartLine;
            for (int i = 0; i < editor.windowRender.linesWindow.size(); i++) {
                int gl = start + i; if (gl >= 0 && gl < total) counts[gl] = getWrapCountForFoldAwareLine(gl, editor.windowRender.linesWindow.get(i), widthPx);
            }
        }
        int[] prefix = new int[total + 1]; int run = 0;
        for (int i = 0; i < total; i++) { run += counts[i]; prefix[i + 1] = run; }
        wrapLineCounts = counts; wrapLinePrefix = prefix; totalWrapVisualLines = run; wrapMetricsWidth = widthPx; wrapPrefixValidUpToLine = total - 1; wrapMetricsReady = true;
    }

    public void scheduleWrapMetricsSnapshotIfNeeded(int w) {
        if (shouldSuppressWrapMetricsForFastSelectAll()) return;
        int start; ArrayList<String> snap = new ArrayList<>();
        synchronized (editor.windowRender.linesWindow) {
            start = editor.windowRender.windowStartLine;
            if (!editor.windowRender.linesWindow.isEmpty()) snap.addAll(editor.windowRender.linesWindow);
        }
        if (snap.isEmpty()) return;
        if (wrapSnapshotBuilding && wrapSnapshotWidth == w && wrapSnapshotStart == start && wrapSnapshotSize == snap.size()) return;
        wrapSnapshotWidth = w; wrapSnapshotStart = start; wrapSnapshotSize = snap.size(); wrapSnapshotBuilding = true;
        final int token = wrapSnapshotToken.incrementAndGet();
        final Paint p = new Paint(editor.textRender.paint);
        editor.fileIO.ioHandler.post(() -> {
            int total = editor.view.getLinesCount(); if (total <= 0) total = start + snap.size();
            if (total <= 0) { mainHandler.post(() -> { if (token == wrapSnapshotToken.get()) { wrapMetricsReady = true; wrapSnapshotBuilding = false; } }); return; }
            int[] c = (wrapLineCounts == null || wrapLineCounts.length != total || wrapMetricsWidth != w) ? new int[total] : wrapLineCounts.clone();
            if (c.length != total) { c = new int[total]; for (int i = 0; i < total; i++) c[i] = getDefaultWrapCountForLine(i); }
            for (int i = 0; i < snap.size(); i++) { int gl = start + i; if (gl >= 0 && gl < total) c[gl] = getWrapCountForFoldAwareLine(gl, snap.get(i), w, p); }
            int[] pre = new int[total + 1]; int run = 0; for (int i = 0; i < total; i++) { run += c[i]; pre[i+1] = run; }
            final int[] finalC = c;
            final int[] finalPre = pre;
            final int runF = run;
            mainHandler.post(() -> { if (token == wrapSnapshotToken.get()) { wrapLineCounts = finalC; wrapLinePrefix = finalPre; totalWrapVisualLines = runF; wrapMetricsWidth = w; wrapMetricsReady = true; wrapSnapshotBuilding = false; editor.postInvalidateOnAnimation(); } });
        });
    }

    public void scheduleWrapPrefixRebuildUpToWindow() {
        if (!isWordWrapEnabled || shouldSuppressWrapMetricsForFastSelectAll()) return;
        int total = editor.view.getLinesCount(); if (total <= 0) return;
        int target; synchronized (editor.windowRender.linesWindow) { target = Math.min(total - 1, editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size() - 1); }
        if (target < 0) return;
        int w = Math.max(1, Math.round(getWrapWidth()));
        if (wrapPrefixBuilding && wrapPrefixWidth == w && wrapPrefixTargetLine >= target) return;
        wrapPrefixBuilding = true; wrapPrefixWidth = w; wrapPrefixTargetLine = target;
        if (!editor.scroll.scroller.isFinished()) editor.scroll.scroller.abortAnimation();
        final int token = wrapPrefixToken.incrementAndGet();
        final int[] base = (wrapLineCounts != null && wrapLineCounts.length == total) ? wrapLineCounts.clone() : null;
        VisualLinePosition anchor = getVisualPositionForIndex(Math.max(0, (int) (editor.scroll.scrollY / editor.textRender.lineHeight)));
        final int oldPre = (wrapLinePrefix != null && anchor.line >= 0 && anchor.line < wrapLinePrefix.length) ? wrapLinePrefix[anchor.line] : anchor.line;
        final Paint p = new Paint(editor.textRender.paint);
        editor.fileIO.ioHandler.post(() -> {
            if (token != wrapPrefixToken.get()) return;
	            int[] c = (base != null) ? base : new int[total]; if (base == null) for (int i = 0; i < total; i++) c[i] = getDefaultWrapCountForLine(i);
            if (editor.fileIO.sourceFile == null || !editor.fileIO.sourceFile.exists()) {
                synchronized (editor.windowRender.linesWindow) {
                    if (editor.windowRender.windowStartLine == 0) {
	                        for (int i = 0; i <= Math.min(target, editor.windowRender.linesWindow.size() - 1); i++) c[i] = getWrapCountForFoldAwareLine(i, editor.windowRender.linesWindow.get(i), w, p);
                    } else { mainHandler.post(() -> { if (token == wrapPrefixToken.get()) wrapPrefixBuilding = false; }); return; }
                }
            } else {
                try (java.io.BufferedReader br = editor.fileIO.reopenReaderAtStart()) {
                    for (int i = 0; i <= target; i++) {
                        if (token != wrapPrefixToken.get()) return;
                        String fl = (br != null) ? br.readLine() : null; String line = (fl == null) ? "" : fl;
                        String mod; synchronized (editor.windowRender.modifiedLines) { mod = editor.windowRender.modifiedLines.get(i); }
                        if (mod != null) line = mod;
	                        c[i] = getWrapCountForFoldAwareLine(i, line, w, p);
	                        if (fl == null && mod == null) { while (i <= target) { c[i] = getDefaultWrapCountForLine(i); i++; } break; }
                    }
                } catch (Exception e) { mainHandler.post(() -> { if (token == wrapPrefixToken.get()) wrapPrefixBuilding = false; }); return; }
            }
            int[] pre = new int[total + 1]; int run = 0; for (int i = 0; i < total; i++) { run += c[i]; pre[i + 1] = run; }
            final int[] finalC = c;
            final int[] finalPre = pre;
            final int runF = run; final int newPre = (anchor.line >= 0 && anchor.line < pre.length) ? pre[anchor.line] : oldPre;
            mainHandler.post(() -> {
                if (token != wrapPrefixToken.get() || Math.max(1, Math.round(getWrapWidth())) != w) { wrapPrefixBuilding = false; return; }
                wrapPrefixBuilding = false;
                if (editor.zoom.isZoomGestureActive()) { editor.zoom.pendingWrapPrefixCounts = finalC; editor.zoom.pendingWrapPrefixPrefix = finalPre; editor.zoom.pendingWrapPrefixTotalVisualLines = runF; editor.zoom.pendingWrapPrefixWidthPx = w; editor.zoom.pendingWrapPrefixValidUpToLine = Math.max(wrapPrefixValidUpToLine, Math.min(target, total - 1)); editor.zoom.pendingApplyWrapPrefixUpdate = true; return; }
                wrapLineCounts = finalC; wrapLinePrefix = finalPre; totalWrapVisualLines = runF; wrapMetricsWidth = w; wrapMetricsReady = true; wrapPrefixValidUpToLine = Math.max(wrapPrefixValidUpToLine, Math.min(target, total - 1));
                if (newPre != oldPre) { editor.scroll.scrollY += (newPre - oldPre) * editor.textRender.lineHeight; editor.scroll.clampScrollY(); }
                editor.postInvalidateOnAnimation();
            });
        });
    }

    public void requestWrapPrefixRebuild() { if (!isWordWrapEnabled) return; if (editor.zoom.isScaling || (editor.scaleGestureDetector != null && editor.scaleGestureDetector.isInProgress())) { wrapPrefixRebuildPending = true; return; } scheduleWrapPrefixRebuildUpToWindow(); }
    public void cancelWrapPrefixRebuildForInteraction() { if (!wrapPrefixBuilding) return; wrapPrefixToken.incrementAndGet(); wrapPrefixBuilding = false; wrapPrefixRebuildPending = true; }
    public void cancelWrapWorkForPriority() { if (!isWordWrapEnabled) return; wrapMetricsToken.incrementAndGet(); wrapSnapshotToken.incrementAndGet(); wrapPrefixToken.incrementAndGet(); wrapMetricsBuilding = false; wrapSnapshotBuilding = false; wrapPrefixBuilding = false; }
    public boolean shouldSuppressWrapMetricsForFastSelectAll() { if (!isWordWrapEnabled || (!editor.selection.isSelectAllActive && !editor.selection.isEntireFileSelected)) return false; return !isWrapMetricsUsableForWindow(Math.max(1, Math.round(getWrapWidth()))); }

    public void onLineContentChanged(int gl, @Nullable String text) {
        if (!isWordWrapEnabled) return; wrapCache.remove(gl);
        int w = Math.max(1, Math.round(getWrapWidth()));
        if (!wrapMetricsReady || wrapLineCounts == null || wrapLinePrefix == null || wrapMetricsWidth != w || gl < 0 || gl >= wrapLineCounts.length) { invalidateWrapMetrics(); return; }
	        int nc = getWrapCountForFoldAwareLine(gl, text, w); int oc = wrapLineCounts[gl]; if (nc == oc) return;
        int d = nc - oc; wrapLineCounts[gl] = nc; for (int i = gl + 1; i < wrapLinePrefix.length; i++) wrapLinePrefix[i] += d;
        totalWrapVisualLines += d;
    }

	    public void onLineCountChanged() { if (isWordWrapEnabled) invalidateWrapMetrics(); editor.lineNumber.invalidateLineNumberCache(); }

	    public int computeWrapCountForLine(String line, int w) { return calculator.computeWrapCountForLine(line, w, editor.textRender.paint, true); }

	    private int getDefaultWrapCountForLine(int gl) {
	        if (editor.codeFold.isCodeFoldingEnabled && editor.codeFold.isLineHidden(gl)) return 0;
	        return 1;
	    }

	    private int getWrapCountForFoldAwareLine(int gl, @Nullable String line, int w) {
	        return getWrapCountForFoldAwareLine(gl, line, w, editor.textRender.paint);
	    }

	    private int getWrapCountForFoldAwareLine(int gl, @Nullable String line, int w, Paint paint) {
	        if (editor.codeFold.isCodeFoldingEnabled) {
	            if (editor.codeFold.isLineHidden(gl)) return 0;
	            com.yn.sodiumeditor.core.fold.CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(gl);
	            if (range != null && range.collapsed) return 1;
	        }
	        return Math.max(1, calculator.computeWrapCountForLine(line, w, paint, paint == editor.textRender.paint));
	    }

	    public int[] getWrapStartsForLine(int gl, String line) {
	        if (!isWordWrapEnabled) return new int[]{0};
        int w = Math.max(1, Math.round(getWrapWidth())); if (wrapWidthPx != w) { wrapWidthPx = w; wrapCache.clear(); }
        if (!isWrapCacheableForLine(gl)) { wrapCache.remove(gl); return calculator.computeWrapStarts(line, w, editor.textRender.paint, true); }
        int[] c = wrapCache.get(gl); if (c != null) return c;
        int[] s = calculator.computeWrapStarts(line, w, editor.textRender.paint, true); wrapCache.put(gl, s); return s;
    }

    public boolean isWrapCacheableForLine(int gl) {
        if (gl >= editor.windowRender.windowStartLine && gl < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size()) return true;
        synchronized (editor.windowRender.modifiedLines) { return editor.windowRender.modifiedLines.containsKey(gl); }
    }

    public int getWrapSegmentIndexForChar(int[] s, int ci) { if (s == null || s.length == 0) return 0; int idx = 0; for (int i = 0; i < s.length; i++) { if (s[i] <= ci) idx = i; else break; } return idx; }
    public int getWrapSegmentStart(int[] s, int si) { if (s == null || s.length == 0) return 0; return s[Math.min(Math.max(0, si), s.length - 1)]; }
    public int getWrapSegmentEnd(int[] s, int si, int len) { if (s == null || s.length == 0) return len; int nx = si + 1; return (nx >= 0 && nx < s.length) ? s[nx] : len; }

    public int getTotalVisualLineCount() {
        if (!isWordWrapEnabled) return editor.codeFold.getVisibleLineCount();
	        if (!isWrapMetricsUsableForWindow(Math.max(1, Math.round(getWrapWidth())))) {
	            int total = editor.view.getLinesCount(); if (total <= 0) total = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size();
	            return Math.max(1, editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.getVisibleLineCount() : total);
	        }
        return Math.max(1, totalWrapVisualLines);
    }

    public int getWrapRangeCount(int s, int e) { if (wrapLinePrefix == null) return 0; int t = wrapLinePrefix.length - 1; return wrapLinePrefix[Math.max(0, Math.min(e, t - 1)) + 1] - wrapLinePrefix[Math.max(0, Math.min(s, t - 1))]; }

    public VisualLinePosition getVisualPositionForIndex(int vi) { return position.getVisualPositionForIndex(vi); }
    public int findLineForVisualIndex(int vi) { return position.findLineForVisualIndex(vi); }
    public int getVisualIndexForLineAndChar(int l, int c) { return position.getVisualIndexForLineAndChar(l, c); }
    public EditOp.CursorTarget getCursorTargetForPosition(float x, float y, Map<Integer, String> dl) { return position.getCursorTargetForPosition(x, y, dl); }

    public boolean isWrapMetricsUsableForWindow(int w) {
        if (!isWordWrapEnabled || !wrapMetricsReady || wrapLinePrefix == null || wrapLineCounts == null || wrapMetricsWidth != w) return false;
        int t = editor.view.getLinesCount(); if (t <= 0) t = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size();
        return t > 0 && wrapLineCounts.length == t && wrapLinePrefix.length == t + 1 && wrapPrefixValidUpToLine >= editor.windowRender.getWindowEndLine();
    }

    public boolean isWrapMetricsUsableForLine(int l) { return isWrapMetricsUsableForWindow(Math.max(1, Math.round(getWrapWidth()))) && wrapPrefixValidUpToLine >= l; }

    public boolean patchWrapMetricsForVisualRange(int fv, int lv, @Nullable Map<Integer, String> dl, int w) {
        if (!isWordWrapEnabled || !wrapMetricsReady || wrapLineCounts == null || wrapLinePrefix == null || wrapMetricsWidth != w || wrapLineCounts.length + 1 != wrapLinePrefix.length) return false;
        final VisualLinePosition anchor = getVisualPositionForIndex(fv); boolean changed = false;
        for (int v = Math.max(0, fv); v <= Math.max(fv, lv); v++) {
            VisualLinePosition p = getVisualPositionForIndex(v); if (p.line < 0 || p.line >= wrapLineCounts.length) break;
	            int nc = getWrapCountForFoldAwareLine(p.line, editor.windowRender.getLineTextForRenderWithDirect(p.line, dl), w);
            if (nc == wrapLineCounts[p.line]) continue;
            int d = nc - wrapLineCounts[p.line]; wrapLineCounts[p.line] = nc; for (int i = p.line + 1; i < wrapLinePrefix.length; i++) wrapLinePrefix[i] += d;
            totalWrapVisualLines += d; changed = true;
        }
        if (changed && anchor.line >= 0 && anchor.line < wrapLinePrefix.length) {
            int dv = (wrapLinePrefix[anchor.line] + Math.max(0, anchor.segment)) - fv;
            if (dv != 0) { editor.scroll.scrollY += dv * editor.textRender.lineHeight; editor.scroll.clampScrollY(); }
        }
        return changed;
    }

    public int clampSegmentEndForWrapIndicator(String line, int ss, int se) { if (!isWordWrapEnabled || line == null) return se; int len = line.length(); return Math.max(ss, Math.min(se, len)); }
    public int clampSegmentEndForWrapIndicator(String line, int ss, int se, int w) { if (se <= ss) return se; float res = indicator.wordWrapIndicatorWidth + (indicator.wordWrapIndicatorPadPx * 2f); float av = w - res; if (av <= 0f) return ss; if (editor.textRender.measureTextWithVisualSpaces(line, ss, se, editor.textRender.paint) <= av) return se; int e = se; while (e > ss) { if (editor.textRender.measureTextWithVisualSpaces(line, ss, --e, editor.textRender.paint) <= av) break; } return e; }

    public int getCharIndexForXInRange(String text, int gl, int s, int e, float x) {
        if (text == null || text.isEmpty()) return 0; s = Math.max(0, Math.min(s, text.length())); e = Math.max(s, Math.min(e, text.length()));
        if (editor.textRender.isRtl) { x = editor.highlite.measureHighlightedSegmentWidth(text, gl, s, e) - (x - editor.layout.getRtlSegmentBaseX(text, gl, s, e)); }        if (x <= 0f) return s;
        if (editor.binaryRender.isBinarySafeRenderingEnabled()) { int[] spans = editor.binaryRender.getBinaryTokenSpans(gl); if (spans != null && spans.length > 0) return editor.binaryRender.getCharIndexForXBinary(text, s, e, x, editor.textRender.paint, spans, editor.binaryRender.binaryCaretNotationEnabled ? 0f : editor.binaryRender.binaryTokenPaddingX); }
        int len = e - s; if (len <= 0) return s;
        if (editor.textRender.getVisualSpaceScale() == 1) return Math.min(s + Math.max(0, editor.textRender.paint.breakText(text, s, e, true, x, null)), e);
        if (editor.view.measureWidthBuffer == null || editor.view.measureWidthBuffer.length < len) editor.view.measureWidthBuffer = new float[len];
        editor.textRender.paint.getTextWidths(text, s, e, editor.view.measureWidthBuffer);
        float cur = 0f; for (int i = 0; i < len; i++) { float a = (text.charAt(s + i) == ' ' || text.charAt(s + i) == '\t') ? (text.charAt(s + i) == ' ' ? editor.view.measureWidthBuffer[i] : editor.view.measureWidthBuffer[i] * com.yn.sodiumeditor.core.view.View.DEFAULT_TAB_SIZE_SPACES) * editor.textRender.getVisualSpaceScale() : editor.view.measureWidthBuffer[i]; if (x < cur + a * 0.5f) return s + i; if (x < cur + a) return s + i + 1; cur += a; }        return e;
    }

    public void applyPendingWrapPrefixUpdateIfAny() {
        if (!editor.zoom.pendingApplyWrapPrefixUpdate || !isWordWrapEnabled || editor.zoom.isZoomGestureActive() || editor.zoom.pendingWrapPrefixCounts == null || editor.zoom.pendingWrapPrefixPrefix == null) return;
        int w = Math.max(1, Math.round(getWrapWidth())); if (editor.zoom.pendingWrapPrefixWidthPx != w) { editor.zoom.pendingApplyWrapPrefixUpdate = false; editor.zoom.pendingWrapPrefixCounts = null; editor.zoom.pendingWrapPrefixPrefix = null; return; }
        int afv = Math.max(0, (int) (editor.scroll.scrollY / editor.textRender.lineHeight)); VisualLinePosition a = getVisualPositionForIndex(afv);
        wrapLineCounts = editor.zoom.pendingWrapPrefixCounts; wrapLinePrefix = editor.zoom.pendingWrapPrefixPrefix; totalWrapVisualLines = editor.zoom.pendingWrapPrefixTotalVisualLines; wrapMetricsWidth = editor.zoom.pendingWrapPrefixWidthPx; wrapMetricsReady = true; wrapPrefixValidUpToLine = Math.max(wrapPrefixValidUpToLine, editor.zoom.pendingWrapPrefixValidUpToLine);
        editor.zoom.pendingApplyWrapPrefixUpdate = false; editor.zoom.pendingWrapPrefixCounts = null; editor.zoom.pendingWrapPrefixPrefix = null;
        if (a.line >= 0 && wrapLinePrefix != null && a.line < wrapLinePrefix.length) { int dv = (wrapLinePrefix[a.line] + Math.max(0, a.segment)) - afv; if (dv != 0) { editor.scroll.scrollY += dv * editor.textRender.lineHeight; editor.scroll.clampScrollY(); } }
    }

    public int getGlobalLineForY(float y) { int idx = Math.max(0, (int) (y / editor.textRender.lineHeight)); return isWordWrapEnabled ? getVisualPositionForIndex(idx).line : editor.codeFold.mapVisibleIndexToGlobal(idx); }
}
