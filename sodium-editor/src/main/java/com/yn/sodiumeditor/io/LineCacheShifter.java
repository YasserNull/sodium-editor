package com.yn.sodiumeditor.io;

import android.util.SparseArray;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.HighliteRender;
import com.yn.sodiumeditor.renderer.TextRender;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles shifting of various line-based caches when lines are inserted or deleted.
 */
public class LineCacheShifter {
    private final SodiumEditor editor;

    public LineCacheShifter(SodiumEditor editor) {
        this.editor = editor;
    }

    public void shiftModifiedLines(int startLine, int delta) {
        if (delta == 0) return;
        synchronized (editor.textRender.modifiedLines) {
            if (editor.textRender.modifiedLines.isEmpty()) return;
            LinkedHashMap<Integer, String> shifted = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> entry : editor.textRender.modifiedLines.entrySet()) {
                int line = entry.getKey();
                if (line < startLine) {
                    shifted.put(line, entry.getValue());
                } else {
                    int newLine = line + delta;
                    if (newLine >= 0) {
                        shifted.put(newLine, entry.getValue());
                    }
                }
            }
            editor.textRender.modifiedLines.clear();
            editor.textRender.modifiedLines.putAll(shifted);
        }
        shiftTextRenderCaches(startLine, delta);
    }

    private void shiftTextRenderCaches(int startLine, int delta) {
        SparseArray<Float> lwCache = editor.textRender.lineWidthCache;
        if (lwCache.size() > 0) {
            SparseArray<Float> shiftedLw = new SparseArray<>(lwCache.size());
            for (int i = 0; i < lwCache.size(); i++) {
                int line = lwCache.keyAt(i);
                float val = lwCache.valueAt(i);
                if (line < startLine) shiftedLw.put(line, val);
                else if (line + delta >= 0) shiftedLw.put(line + delta, val);
            }
            lwCache.clear();
            for (int i = 0; i < shiftedLw.size(); i++) {
                lwCache.put(shiftedLw.keyAt(i), shiftedLw.valueAt(i));
            }
        }
        SparseArray<Float> awCache = editor.textRender.avgCharWidthCache;
        if (awCache.size() > 0) {
            SparseArray<Float> shiftedAw = new SparseArray<>(awCache.size());
            for (int i = 0; i < awCache.size(); i++) {
                int line = awCache.keyAt(i);
                float val = awCache.valueAt(i);
                if (line < startLine) shiftedAw.put(line, val);
                else if (line + delta >= 0) shiftedAw.put(line + delta, val);
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
                    else if (line + delta >= 0) shifted.put(line + delta, entry.getValue());
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
                    else if (line + delta >= 0) shifted.put(line + delta, entry.getValue());
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
                    else if (line + delta >= 0) shifted.put(line + delta, entry.getValue());
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
                    else if (line + delta >= 0) shifted.put(line + delta, entry.getValue());
                }
                editor.pathUnderline.pathUnderlineCache.clear();
                editor.pathUnderline.pathUnderlineCache.putAll(shifted);
            }
        }
    }
}
