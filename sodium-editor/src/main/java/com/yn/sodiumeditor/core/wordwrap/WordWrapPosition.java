package com.yn.sodiumeditor.core.wordwrap;

import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.io.EditOp;
import java.util.Map;

/**
 * Handles coordinate transformations between visual and global line/char indices.
 */
public class WordWrapPosition {
    private final SodiumEditor editor;
    private final WordWrap wordWrap;

    public WordWrapPosition(SodiumEditor editor, WordWrap wordWrap) {
        this.editor = editor;
        this.wordWrap = wordWrap;
    }

    public WordWrap.VisualLinePosition getVisualPositionForIndex(int visualIndex) {
        int widthPx = Math.max(1, Math.round(wordWrap.getWrapWidth()));
        if (!wordWrap.isWrapMetricsUsableForWindow(widthPx)) {
            if (wordWrap.isWordWrapEnabled) return getVisualPositionForIndexFallback(visualIndex);
            return new WordWrap.VisualLinePosition(editor.codeFold.mapVisibleIndexToGlobal(visualIndex), 0);
        }
        int v = Math.max(0, Math.min(visualIndex, Math.max(0, wordWrap.totalWrapVisualLines - 1)));
        int line = findLineForVisualIndex(v);
        return new WordWrap.VisualLinePosition(line, v - wordWrap.wrapLinePrefix[line]);
    }

	    public WordWrap.VisualLinePosition getVisualPositionForIndexFallback(int visualIndex) {
	        int idx = Math.max(0, visualIndex);
	        int baseLine = Math.max(0, editor.windowRender.windowStartLine);
	        int baseVisual = (wordWrap.wrapLinePrefix != null && wordWrap.wrapPrefixValidUpToLine >= baseLine && baseLine < wordWrap.wrapLinePrefix.length) ? wordWrap.wrapLinePrefix[baseLine] : baseLine;
        int remaining = idx - baseVisual;
        if (remaining <= 0) return new WordWrap.VisualLinePosition(baseLine, 0);

	        int line = baseLine;
	        int windowEnd = Math.max(baseLine, editor.windowRender.getWindowEndLine());
	        while (line <= windowEnd) {
	            if (editor.codeFold.isCodeFoldingEnabled && editor.codeFold.isLineHidden(line)) {
	                line++;
	                continue;
	            }
	            int[] starts = wordWrap.getWrapStartsForLine(line, editor.windowRender.getLineTextForRender(line));
	            int segCount = editor.codeFold.isCodeFoldingEnabled
	                    && editor.codeFold.getFoldRangeAtStart(line) != null
	                    && editor.codeFold.getFoldRangeAtStart(line).collapsed
	                    ? 1
	                    : Math.max(1, starts.length);
	            if (remaining < segCount) return new WordWrap.VisualLinePosition(line, Math.max(0, Math.min(remaining, segCount - 1)));
	            remaining -= segCount; line++;
	        }
	        return new WordWrap.VisualLinePosition(windowEnd, 0);
	    }

    public int findLineForVisualIndex(int visualIndex) {
        if (wordWrap.wrapLinePrefix == null || wordWrap.wrapLinePrefix.length == 0) return 0;
        int lo = 0, hi = wordWrap.wrapLinePrefix.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (wordWrap.wrapLinePrefix[mid] <= visualIndex) lo = mid + 1;
            else hi = mid;
        }
        return Math.min(Math.max(0, lo - 1), wordWrap.wrapLinePrefix.length - 2);
    }

	    public int getVisualIndexForLineAndChar(int line, int ch) {
	        if (editor.codeFold.isCodeFoldingEnabled) {
	            com.yn.sodiumeditor.core.fold.CodeFold.FoldRange hidden =
	                editor.codeFold.getCollapsedRangeContainingLine(line);
	            if (hidden != null) {
	                line = hidden.startLine;
	                ch = 0;
	            }
	        }
	        if (!wordWrap.isWrapMetricsUsableForLine(line)) {
	            return editor.codeFold.isCodeFoldingEnabled ? editor.codeFold.getVisibleIndexForGlobalLine(line) : Math.max(0, line);
	        }
	        int totalLines = wordWrap.wrapLinePrefix.length - 1;
	        int safeLine = Math.max(0, Math.min(line, Math.max(0, totalLines - 1)));
	        if (editor.codeFold.isCodeFoldingEnabled) {
	            com.yn.sodiumeditor.core.fold.CodeFold.FoldRange range =
	                editor.codeFold.getFoldRangeAtStart(safeLine);
	            if (range != null && range.collapsed) return wordWrap.wrapLinePrefix[safeLine];
	        }
	        String text = editor.windowRender.getLineTextForRender(safeLine);
	        if (text == null) text = "";
	        int[] starts = wordWrap.getWrapStartsForLine(safeLine, text);
	        int seg = wordWrap.getWrapSegmentIndexForChar(starts, Math.max(0, Math.min(ch, text.length())));
	        int visualIndex = wordWrap.wrapLinePrefix[safeLine] + seg;
	        return visualIndex;
	    }

    public EditOp.CursorTarget getCursorTargetForPosition(float viewX, float viewY, @Nullable Map<Integer, String> directLines) {
        float y = viewY + editor.scroll.scrollY;
        int visualIndex = Math.max(0, (int) (y / editor.textRender.lineHeight));
        WordWrap.VisualLinePosition pos = wordWrap.isWordWrapEnabled ? getVisualPositionForIndex(visualIndex) : new WordWrap.VisualLinePosition(editor.codeFold.mapVisibleIndexToGlobal(visualIndex), 0);
        if (editor.codeFold.isCodeFoldingEnabled) {
            com.yn.sodiumeditor.core.fold.CodeFold.FoldRange hidden =
                editor.codeFold.getCollapsedRangeContainingLine(pos.line);
            if (hidden != null) {
                pos = new WordWrap.VisualLinePosition(hidden.startLine, 0);
            }
        }
        String line = editor.windowRender.getLineTextForRenderWithDirect(pos.line, directLines);
        float x = editor.scroll.viewToTextX(viewX);
        if (!wordWrap.isWordWrapEnabled) {
            int charIdx = editor.textRender.getCharIndexForX(line, x, pos.line);
            int clamped = Math.max(0, Math.min(charIdx, editor.view.getLogicalLineLength(pos.line, line)));
            return new EditOp.CursorTarget(pos.line, editor.binaryRender.isBinarySafeRenderingEnabled() ? editor.binaryRender.snapBinaryCursor(line, clamped, pos.line) : clamped);
        }
        int[] starts = wordWrap.getWrapStartsForLine(pos.line, line);
        int seg = Math.min(Math.max(0, pos.segment), Math.max(0, starts.length - 1));
        int charIdx = wordWrap.getCharIndexForXInRange(line, pos.line, wordWrap.getWrapSegmentStart(starts, seg), wordWrap.getWrapSegmentEnd(starts, seg, line.length()), x);
        int clamped = Math.max(0, Math.min(charIdx, (line == null ? 0 : line.length())));
        return new EditOp.CursorTarget(pos.line, editor.binaryRender.isBinarySafeRenderingEnabled() ? editor.binaryRender.snapBinaryCursor(line, clamped, pos.line) : clamped);
    }
}
