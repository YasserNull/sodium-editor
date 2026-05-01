package com.yn.sodiumeditor.io;

import android.util.SparseArray;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.HighliteRender;
import com.yn.sodiumeditor.renderer.TextRender;
import com.yn.sodiumeditor.utils.FunctionLog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles shifting of various line-based caches when lines are inserted or deleted.
 */
public class LineCacheShifter {
    private final SodiumEditor editor;

    public LineCacheShifter(SodiumEditor editor) {
        FunctionLog.f("LineCacheShifter", "LineCacheShifter", editor);
        this.editor = editor;
    }

    public void shiftModifiedLines(int startLine, int delta) {
        FunctionLog.f("LineCacheShifter", "shiftModifiedLines", startLine, delta);
        if (delta == 0) return;
        synchronized (editor.windowRender.modifiedLines) {
            if (!editor.windowRender.modifiedLines.isEmpty()) {
                LinkedHashMap<Integer, String> shifted = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> entry : editor.windowRender.modifiedLines.entrySet()) {
                    int line = entry.getKey();
                    if (line < startLine) {
                        shifted.put(line, entry.getValue());
                    } else {
                        if (delta < 0 && line < startLine - delta) continue;
                        int newLine = line + delta;
                        if (newLine >= 0) shifted.put(newLine, entry.getValue());
                    }
                }
                editor.windowRender.modifiedLines.clear();
                editor.windowRender.modifiedLines.putAll(shifted);
            }
        }
        shiftTextRenderCaches(startLine, delta);
        shiftStreamedLineCaches(startLine, delta);
        editor.binaryRender.shiftBinaryTokenSpans(startLine, delta);
        editor.bracketGuides.shiftBracketGuideCaches(startLine, delta);
        
        synchronized (editor.fileIO.directLineCache) {
            editor.fileIO.directLineCache.clear();
        }
        // Important: don't auto-reload the window from disk while there are pending
        // structural edits (line insert/delete). Reloading from the unchanged file
        // reintroduces deleted/shifted lines before edits are saved.
    }

    private void shiftStreamedLineCaches(int startLine, int delta) {
        FunctionLog.f("LineCacheShifter", "shiftStreamedLineCaches", startLine, delta);
        synchronized (editor.windowRender.streamedLinesLockLinesLock) {
            android.util.SparseIntArray len = editor.windowRender.streamedLinesLockLineLengths;
            android.util.SparseIntArray start = editor.windowRender.streamedLinesLockLineSliceStarts;
            if (len.size() > 0) {
                android.util.SparseIntArray newLen = new android.util.SparseIntArray();
                android.util.SparseIntArray newStart = new android.util.SparseIntArray();
                for (int i = 0; i < len.size(); i++) {
                    int line = len.keyAt(i);
                    if (line < startLine) {
                        newLen.put(line, len.valueAt(i));
                        newStart.put(line, start.get(line));
                    } else {
                        if (delta < 0 && line < startLine - delta) continue;
                        if (line + delta >= 0) {
                            newLen.put(line + delta, len.valueAt(i));
                            newStart.put(line + delta, start.get(line));
                        }
                    }
                }
                len.clear(); start.clear();
                for (int i = 0; i < newLen.size(); i++) {
                    len.put(newLen.keyAt(i), newLen.valueAt(i));
                    start.put(newStart.keyAt(i), newStart.valueAt(i));
                }
            }
        }
        synchronized (editor.windowRender.streamedLinesLock) {
            android.util.SparseIntArray len = editor.windowRender.streamedLineLengths;
            android.util.SparseIntArray start = editor.windowRender.streamedLineSliceStarts;
            if (len.size() > 0) {
                android.util.SparseIntArray newLen = new android.util.SparseIntArray();
                android.util.SparseIntArray newStart = new android.util.SparseIntArray();
                for (int i = 0; i < len.size(); i++) {
                    int line = len.keyAt(i);
                    if (line < startLine) {
                        newLen.put(line, len.valueAt(i));
                        newStart.put(line, start.get(line));
                    } else {
                        if (delta < 0 && line < startLine - delta) continue;
                        if (line + delta >= 0) {
                            newLen.put(line + delta, len.valueAt(i));
                            newStart.put(line + delta, start.get(line));
                        }
                    }
                }
                len.clear(); start.clear();
                for (int i = 0; i < newLen.size(); i++) {
                    len.put(newLen.keyAt(i), newLen.valueAt(i));
                    start.put(newStart.keyAt(i), newStart.valueAt(i));
                }
            }
        }
    }

    private void shiftTextRenderCaches(int startLine, int delta) {
        FunctionLog.f("LineCacheShifter", "shiftTextRenderCaches", startLine, delta);
        SparseArray<Float> lwCache = editor.windowRender.lineWidthCache;
        if (lwCache.size() > 0) {
            SparseArray<Float> shiftedLw = new SparseArray<>(lwCache.size());
            for (int i = 0; i < lwCache.size(); i++) {
                int line = lwCache.keyAt(i);
                float val = lwCache.valueAt(i);
                if (line < startLine) shiftedLw.put(line, val);
                else {
                    if (delta < 0 && line < startLine - delta) continue;
                    if (line + delta >= 0) shiftedLw.put(line + delta, val);
                }
            }
            lwCache.clear();
            for (int i = 0; i < shiftedLw.size(); i++) {
                lwCache.put(shiftedLw.keyAt(i), shiftedLw.valueAt(i));
            }
        }
        SparseArray<Float> awCache = editor.windowRender.avgCharWidthCache;
        if (awCache.size() > 0) {
            SparseArray<Float> shiftedAw = new SparseArray<>(awCache.size());
            for (int i = 0; i < awCache.size(); i++) {
                int line = awCache.keyAt(i);
                float val = awCache.valueAt(i);
                if (line < startLine) shiftedAw.put(line, val);
                else {
                    if (delta < 0 && line < startLine - delta) continue;
                    if (line + delta >= 0) shiftedAw.put(line + delta, val);
                }
            }
            awCache.clear();
            for (int i = 0; i < shiftedAw.size(); i++) {
                awCache.put(shiftedAw.keyAt(i), shiftedAw.valueAt(i));
            }
        }
        // Shift Highlight Cache
        synchronized (editor.highlite.highlightCache) {
            if (!editor.highlite.highlightCache.isEmpty()) {
                LinkedHashMap<Integer, List<HighliteRender.HighlightSpan>> shifted = new LinkedHashMap<>();
                for (Map.Entry<Integer, List<HighliteRender.HighlightSpan>> entry : editor.highlite.highlightCache.entrySet()) {
                    int line = entry.getKey();
                    if (line < startLine) shifted.put(line, entry.getValue());
                    else {
                        if (delta < 0 && line < startLine - delta) continue;
                        if (line + delta >= 0) shifted.put(line + delta, entry.getValue());
                    }
                }
                editor.highlite.highlightCache.clear();
                editor.highlite.highlightCache.putAll(shifted);
            }
        }
        // Shift Decoration Caches
        synchronized (editor.colorCodeHighlight.colorCodeBgCache) {
            if (!editor.colorCodeHighlight.colorCodeBgCache.isEmpty()) {
                LinkedHashMap<Integer, int[]> shifted = new LinkedHashMap<>();
                for (Map.Entry<Integer, int[]> entry : editor.colorCodeHighlight.colorCodeBgCache.entrySet()) {
                    int line = entry.getKey();
                    if (line < startLine) shifted.put(line, entry.getValue());
                    else {
                        if (delta < 0 && line < startLine - delta) continue;
                        if (line + delta >= 0) shifted.put(line + delta, entry.getValue());
                    }
                }
                editor.colorCodeHighlight.colorCodeBgCache.clear();
                editor.colorCodeHighlight.colorCodeBgCache.putAll(shifted);
            }
        }
        synchronized (editor.urlUnderline.urlUnderlineCache) {
            if (!editor.urlUnderline.urlUnderlineCache.isEmpty()) {
                LinkedHashMap<Integer, List<TextRender.UnderlineSpan>> shifted = new LinkedHashMap<>();
                for (Map.Entry<Integer, List<TextRender.UnderlineSpan>> entry : editor.urlUnderline.urlUnderlineCache.entrySet()) {
                    int line = entry.getKey();
                    if (line < startLine) shifted.put(line, entry.getValue());
                    else {
                        if (delta < 0 && line < startLine - delta) continue;
                        if (line + delta >= 0) shifted.put(line + delta, entry.getValue());
                    }
                }
                editor.urlUnderline.urlUnderlineCache.clear();
                editor.urlUnderline.urlUnderlineCache.putAll(shifted);
            }
        }
        synchronized (editor.pathUnderline.pathUnderlineCache) {
            if (!editor.pathUnderline.pathUnderlineCache.isEmpty()) {
                LinkedHashMap<Integer, List<TextRender.UnderlineSpan>> shifted = new LinkedHashMap<>();
                for (Map.Entry<Integer, List<TextRender.UnderlineSpan>> entry : editor.pathUnderline.pathUnderlineCache.entrySet()) {
                    int line = entry.getKey();
                    if (line < startLine) shifted.put(line, entry.getValue());
                    else {
                        if (delta < 0 && line < startLine - delta) continue;
                        if (line + delta >= 0) shifted.put(line + delta, entry.getValue());
                    }
                }
                editor.pathUnderline.pathUnderlineCache.clear();
                editor.pathUnderline.pathUnderlineCache.putAll(shifted);
            }
        }
    }
}
