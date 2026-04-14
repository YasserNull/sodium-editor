package com.yn.sodiumeditor.utils;

import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.TextRender;
import java.util.ArrayList;

/**
 * Low-level utilities for word wrapping calculations.
 */
public class WordWrapUtils {
    private final SodiumEditor editor;

    public WordWrapUtils(SodiumEditor editor) {
        this.editor = editor;
    }

    public int computeWrapCountForLine(String line, int widthPx, Paint p, boolean useSharedBuffer) {
        return Math.max(1, computeWrapStarts(line, widthPx, p, useSharedBuffer).length);
    }

    public int[] computeWrapStarts(String line, int widthPx, Paint p, boolean useSharedBuffer) {
        if (line == null || line.length() == 0) return new int[]{0};
        if (shouldUseBreakTextWrap(line)) return computeWrapStartsWithBreakText(line, widthPx, p);

        int len = line.length();
        float[] widths;
        if (useSharedBuffer) {
            if (editor.textRender.measureWidthBuffer == null || editor.textRender.measureWidthBuffer.length < len) {
                editor.textRender.measureWidthBuffer = new float[len];
            }
            widths = editor.textRender.measureWidthBuffer;
        } else { widths = new float[len]; }

        p.getTextWidths(line, 0, len, widths);
        float[] adv = new float[len];
        for (int i = 0; i < len; i++) adv[i] = getCharAdvanceWidth(line.charAt(i), widths[i], p);

        ArrayList<Integer> starts = new ArrayList<>();
        int i = 0; starts.add(0);
        while (i < len) {
            float w = 0f; int lastBreak = -1; int j = i;
            for (; j < len; j++) {
                float a = adv[j];
                if (w + a > widthPx && j > i) break;
                w += a;
                if (Character.isWhitespace(line.charAt(j))) lastBreak = j;
            }
            if (j >= len) break;
            int next = (lastBreak >= i) ? lastBreak + 1 : Math.max(i + 1, j);
            if (next <= i) next = i + 1;
            starts.add(next); i = next;
        }
        int[] out = new int[starts.size()];
        for (int k = 0; k < starts.size(); k++) out[k] = starts.get(k);
        return out;
    }

    private boolean shouldUseBreakTextWrap(String line) {
        return editor.getVisualSpaceScale() == 1 && line.indexOf('\t') < 0;
    }

    public int[] computeWrapStartsWithBreakText(String line, int widthPx, Paint p) {
        int len = line.length();
        ArrayList<Integer> starts = new ArrayList<>();
        int i = 0; starts.add(0);
        while (i < len) {
            int count = p.breakText(line, i, len, true, widthPx, null);
            if (count <= 0) count = 1;
            int end = i + count;
            if (end >= len) break;
            int lastBreak = -1;
            for (int j = end - 1; j >= i; j--) {
                if (Character.isWhitespace(line.charAt(j))) { lastBreak = j; break; }
            }
            int next = (lastBreak >= i) ? lastBreak + 1 : end;
            if (next <= i) next = i + 1;
            starts.add(next); i = next;
        }
        int[] out = new int[starts.size()];
        for (int k = 0; k < starts.size(); k++) out[k] = starts.get(k);
        return out;
    }

    private float getCharAdvanceWidth(char c, float measuredWidth, Paint p) {
        if (c == ' ') return editor.getVisualSpaceScale() != 1 ? measuredWidth * editor.getVisualSpaceScale() : measuredWidth;
        if (c == '\t') {
            float tabWidth = measuredWidth * TextRender.DEFAULT_TAB_SIZE_SPACES;
            return editor.getVisualSpaceScale() != 1 ? tabWidth * editor.getVisualSpaceScale() : tabWidth;
        }
        return measuredWidth;
    }
}
