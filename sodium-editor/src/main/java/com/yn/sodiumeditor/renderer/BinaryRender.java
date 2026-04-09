package com.yn.sodiumeditor.renderer;

import android.util.SparseArray;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.BinaryTokenConverter;
import com.yn.sodiumeditor.io.BinaryFileReader;
import com.yn.sodiumeditor.renderer.draw.BinaryLineDrawer;

/**
 * BinaryRender is the main entry point for binary-safe rendering.
 * This class delegates to specialized components:
 * - BinaryTokenConverter: Token conversion and lookup tables
 * - BinaryFileReader: File reading operations
 * - BinaryLineDrawer: Drawing operations
 */
public class BinaryRender {

    private final SodiumEditor editor;

    // Delegate components
    public final BinaryTokenConverter tokenConverter;
    public final BinaryFileReader fileReader;
    public final BinaryLineDrawer lineDrawer;

    // State
    public boolean binarySafeRenderingEnabled = false;
    private final SparseArray<int[]> binaryTokenSpans = new SparseArray<>();

    // Exposed configuration fields for external access
    public boolean binaryCaretNotationEnabled = false;
    public float binaryTokenPaddingX = 2f;

    public BinaryRender(SodiumEditor editor) {
        this.editor = editor;
        this.tokenConverter = new BinaryTokenConverter();
        this.fileReader = new BinaryFileReader(editor, tokenConverter);
        this.lineDrawer = new BinaryLineDrawer(editor, tokenConverter);
    }

    // ── Public API ─────────────────────────────────────────────────────────────
    public void setBinarySafeRenderingEnabled(boolean enabled) {
        binarySafeRenderingEnabled = enabled;
        synchronized (editor.textRender.lineWidthCache) {
            editor.textRender.lineWidthCache.clear();
        }
        editor.textRender.currentMaxWindowLineWidth = 0f;
        editor.textRender.globalMaxLineWidth        = 0f;
        editor.scroll.maxLineWidthForScroll         = 0f;
        editor.scroll.maxTextStartXForScroll        = 0f;
        editor.scroll.maxScrollXForScroll           = 0f;
        editor.invalidateHighlightEnsureRange();
        editor.bracketGuides.invalidateBracketGuideCache();
        if (editor.wordWrap.isWordWrapEnabled)
            editor.wordWrap.invalidateWrapMetrics(true);
        editor.wordWrap.requestWrapPrefixRebuild();
        editor.textRender.reloadWindowAroundVisible(false);
        editor.invalidate();
    }

    public boolean isBinarySafeRenderingEnabled() { return binarySafeRenderingEnabled; }

    // ── Token Box Configuration ────────────────────────────────────────────────
    public void setBinaryTokenBoxEnabled(boolean enabled) {
        lineDrawer.setBinaryTokenBoxEnabled(enabled);
    }

    public void setBinaryTokenFillColor(int color) {
        lineDrawer.setBinaryTokenFillColor(color);
    }

    public void setBinaryTokenStrokeColor(int color) {
        lineDrawer.setBinaryTokenStrokeColor(color);
    }

    public void setBinaryTokenStrokeWidth(float widthPx) {
        lineDrawer.setBinaryTokenStrokeWidth(widthPx);
    }

    public void setBinaryTokenBoxPadding(float paddingX, float paddingY) {
        binaryTokenPaddingX = paddingX;
        lineDrawer.setBinaryTokenBoxPadding(paddingX, paddingY);
    }

    public void setBinaryHexTokensEnabled(boolean enabled) {
        tokenConverter.setBinaryHexTokensEnabled(enabled);
        editor.invalidate();
    }

    public void setBinaryTokenCornerRadius(float radiusPx) {
        lineDrawer.setBinaryTokenCornerRadius(radiusPx);
    }

    public void setBinaryTokenTextColor(int color) {
        lineDrawer.setBinaryTokenTextColor(color);
    }

    public void setBinaryCaretNotationEnabled(boolean enabled) {
        binaryCaretNotationEnabled = enabled;
        tokenConverter.setBinaryCaretNotationEnabled(enabled);
        lineDrawer.setBinaryCaretNotationEnabled(enabled);
        editor.invalidate();
    }

    // ── Cached Character Width ─────────────────────────────────────────────────
    public void updateCachedCharWidth(android.graphics.Paint paint) {
        lineDrawer.updateCachedCharWidth(paint);
    }

    public float getCachedCharWidth() {
        return lineDrawer.getCachedCharWidth();
    }

    public void setCachedCharWidth(float width) {
        lineDrawer.setCachedCharWidth(width);
    }

    // ── Token Spans ────────────────────────────────────────────────────────────
    public int[] getBinaryTokenSpans(int lineIndex) {
        return binaryTokenSpans.get(lineIndex);
    }

    public void clearBinaryTokenSpansForLine(int lineIndex) {
        binaryTokenSpans.remove(lineIndex);
    }

    /**
     * Adjust binary token spans after an edit operation.
     * @param lineIndex the line index
     * @param editPos the position where the edit occurred
     * @param delta the change in length (negative for deletion, positive for insertion)
     * @param insertLen the length of inserted text (0 for deletion)
     */
    public void adjustBinaryTokenSpansForEdit(int lineIndex, int editPos, int delta, int insertLen) {
        int[] spans = binaryTokenSpans.get(lineIndex);
        if (spans == null || spans.length == 0) return;

        // Create new spans array
        int[] newSpans = new int[spans.length];
        int newCount = 0;

        for (int i = 0; i < spans.length; i += 2) {
            int s = spans[i];
            int e = spans[i + 1];

            // Skip spans that are completely before the edit position
            if (e <= editPos) {
                newSpans[newCount++] = s;
                newSpans[newCount++] = e;
                continue;
            }

            // Adjust spans that are after or at the edit position
            if (s >= editPos) {
                int newS = Math.max(editPos, s + delta);
                int newE = Math.max(newS, e + delta);
                if (newE > newS) {
                    newSpans[newCount++] = newS;
                    newSpans[newCount++] = newE;
                }
            } else {
                // Span overlaps with edit position
                int newS = s;
                int newE = Math.max(newS, e + delta);
                if (newE > newS) {
                    newSpans[newCount++] = newS;
                    newSpans[newCount++] = newE;
                }
            }
        }

        // Update spans array
        if (newCount == 0) {
            binaryTokenSpans.remove(lineIndex);
        } else if (newCount != spans.length) {
            int[] compact = new int[newCount];
            System.arraycopy(newSpans, 0, compact, 0, newCount);
            binaryTokenSpans.put(lineIndex, compact);
        } else {
            System.arraycopy(newSpans, 0, spans, 0, newCount);
        }
    }

    // ── Conversion ─────────────────────────────────────────────────────────────
    public String bytesToControlVisible(byte[] buf, int len) {
        return tokenConverter.bytesToControlVisible(buf, len);
    }

    public String bytesToControlVisibleAndCacheSpans(byte[] buf, int len, int lineIndex) {
        return tokenConverter.bytesToControlVisibleAndCacheSpans(buf, len, lineIndex, binaryTokenSpans);
    }

    // ── File Reading ───────────────────────────────────────────────────────────
    public String readLineWithBinarySafe(
        java.io.RandomAccessFile raf, int line, long fileLen, java.nio.charset.Charset fileCharset) throws Exception {
        return fileReader.readLineWithBinarySafe(raf, line, fileLen, fileCharset, binarySafeRenderingEnabled);
    }

    public String readLineSliceAtByte(
        java.io.RandomAccessFile raf, long lineStart, long lineByteLen,
        int startChar, int endChar, java.nio.charset.Charset fileCharset) throws Exception {
        return fileReader.readLineSliceAtByte(raf, lineStart, lineByteLen, startChar, endChar, fileCharset, binarySafeRenderingEnabled);
    }

    public SodiumEditor.StreamedCharSlice readLineSliceByChars(
        java.io.RandomAccessFile raf, long lineStart,
        int startChar, int endChar,
        boolean needTotalLength, java.nio.charset.Charset fileCharset) throws Exception {
        return fileReader.readLineSliceByChars(raf, lineStart, startChar, endChar, needTotalLength, fileCharset,
            binarySafeRenderingEnabled, tokenConverter.isBinaryHexTokensEnabled());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    public String escapeControlChar(char c) {
        return tokenConverter.escapeControlChar(c);
    }

    public boolean needsEscaping(char c) { return tokenConverter.needsEscaping(c); }

    public int getDisplayWidth(char c) {
        return tokenConverter.getDisplayWidth(c, binarySafeRenderingEnabled);
    }

    public int matchBinaryToken(String line, int index) {
        return tokenConverter.matchBinaryToken(line, index);
    }

    public boolean findBinaryTokenSpan(String line, int index, int[] outStartEnd) {
        return tokenConverter.findBinaryTokenSpan(line, index, outStartEnd);
    }

    public int snapBinaryCursor(String line, int index) {
        return index;
    }

    public int snapBinaryCursor(String line, int index, int lineIndex) {
        return lineDrawer.snapBinaryCursor(line, index, lineIndex, binaryTokenSpans);
    }

    public int getCharIndexForXBinary(
        String line, int start, int end, float x, android.graphics.Paint paint, int[] spans, float padX) {
        return lineDrawer.getCharIndexForXBinary(line, start, end, x, paint, spans, padX);
    }

    public float getXForCharBinary(String line, int charIndex, android.graphics.Paint paint, int[] spans, float padX) {
        return lineDrawer.getXForCharBinary(line, charIndex, paint, spans, padX);
    }

    public boolean findBinaryTokenSpanInSpans(int[] spans, int index, int[] outStartEnd) {
        return tokenConverter.findBinaryTokenSpanInSpans(spans, index, outStartEnd);
    }

    // ── Drawing ────────────────────────────────────────────────────────────────
    public void drawBinaryLine(android.graphics.Canvas canvas, String line, int globalLine, float y, android.graphics.Paint defaultPaint) {
        lineDrawer.drawBinaryLine(canvas, line, globalLine, y, defaultPaint, binaryTokenSpans);
    }

    public void drawBinaryLineSlice(android.graphics.Canvas canvas, String line, int globalLine, int relStart, int relEnd, int sliceStart, float y, android.graphics.Paint defaultPaint) {
        lineDrawer.drawBinaryLineSlice(canvas, line, globalLine, relStart, relEnd, sliceStart, y, defaultPaint, binaryTokenSpans);
    }

    public android.graphics.Paint getBinaryTokenFillPaint() { return lineDrawer.getBinaryTokenFillPaint(); }
    public android.graphics.Paint getBinaryTokenStrokePaint() { return lineDrawer.getBinaryTokenStrokePaint(); }
}
