package com.yn.sodiumeditor.state;

/**
 * State class for whitespace guide functionality.
 * Stores whitespace guide state including visibility, space step, and buffers.
 */
public class WhitespaceGuideState {

    private boolean whitespaceGuidesEnabled = false;
    private int whitespaceGuideSpaceStep = 1;

    // Buffers for reuse
    public float[] whitespaceWidthBuffer;
    public float[] whitespaceDotBuffer;
    public float[] measureWidthBuffer;

    // Draw state
    public int syntaxIndex = 0;

    public WhitespaceGuideState() {
    }

    public boolean isWhitespaceGuidesEnabled() {
        return whitespaceGuidesEnabled;
    }

    public void setWhitespaceGuidesEnabled(boolean enabled) {
        this.whitespaceGuidesEnabled = enabled;
    }

    public int getSpaceStep() {
        return whitespaceGuideSpaceStep;
    }

    public boolean setSpaceStep(int step) {
        int safeStep = Math.max(1, step);
        if (whitespaceGuideSpaceStep == safeStep) return false;
        whitespaceGuideSpaceStep = safeStep;
        return true;
    }

    public float[] ensureMeasureWidthBuffer(int len) {
        if (measureWidthBuffer == null || measureWidthBuffer.length < len) {
            measureWidthBuffer = new float[len];
        }
        return measureWidthBuffer;
    }

    public float[] ensureWhitespaceWidthBuffer(int len) {
        if (whitespaceWidthBuffer == null || whitespaceWidthBuffer.length < len) {
            whitespaceWidthBuffer = new float[len];
        }
        return whitespaceWidthBuffer;
    }

    public float[] ensureWhitespaceDotBuffer(int len) {
        if (whitespaceDotBuffer == null || whitespaceDotBuffer.length < len) {
            float[] expanded = new float[len];
            if (whitespaceDotBuffer != null && whitespaceDotBuffer.length > 0) {
                System.arraycopy(
                        whitespaceDotBuffer, 0, expanded, 0, Math.min(whitespaceDotBuffer.length, len));
            }
            whitespaceDotBuffer = expanded;
        }
        return whitespaceDotBuffer;
    }

    public void resetSyntaxIndex() {
        syntaxIndex = 0;
    }

    public void incrementSyntaxIndex() {
        syntaxIndex++;
    }

    public int getSyntaxIndex() {
        return syntaxIndex;
    }

    public float getCharAdvanceWidth(char c, float measuredWidth, android.graphics.Paint p, int tabSpaces) {
        if (c == ' ') {
            return measuredWidth;
        }
        if (c == '\t') {
            return getVisualTabWidth(p, tabSpaces);
        }
        return measuredWidth;
    }

    public float getVisualSpaceWidth(android.graphics.Paint p) {
        return p.measureText(" ");
    }

    public float getVisualTabWidth(android.graphics.Paint p, int tabSpaces) {
        return getVisualSpaceWidth(p) * tabSpaces;
    }
}
