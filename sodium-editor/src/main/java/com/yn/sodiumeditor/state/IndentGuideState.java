package com.yn.sodiumeditor.state;

import java.util.ArrayList;

/**
 * State class for indent guide functionality.
 * Stores indent guide state including intervals and dirty flag.
 */
public class IndentGuideState {

    private static final float DEFAULT_STROKE_WIDTH = 2f;

    private boolean indentGuidesEnabled = false;
    private float baseIndentGuideStrokeWidth = DEFAULT_STROKE_WIDTH;
    private float baseIndentGuideTextSizePx = 0f;
    private boolean indentGuideIntervalsDirty = true;

    public final ArrayList<int[]> indentGuideIntervals = new ArrayList<>();

    public IndentGuideState() {
    }

    public boolean isIndentGuidesEnabled() {
        return indentGuidesEnabled;
    }

    public void setIndentGuidesEnabled(boolean enabled) {
        this.indentGuidesEnabled = enabled;
    }

    public float getBaseIndentGuideStrokeWidth() {
        return baseIndentGuideStrokeWidth;
    }

    public void setBaseIndentGuideStrokeWidth(float width) {
        this.baseIndentGuideStrokeWidth = width;
    }

    public float getBaseIndentGuideTextSizePx() {
        return baseIndentGuideTextSizePx;
    }

    public void setBaseIndentGuideTextSizePx(float sizePx) {
        this.baseIndentGuideTextSizePx = sizePx;
    }

    public boolean isIndentGuideIntervalsDirty() {
        return indentGuideIntervalsDirty;
    }

    public void setIndentGuideIntervalsDirty(boolean dirty) {
        this.indentGuideIntervalsDirty = dirty;
    }

    public void markIntervalsDirty() {
        indentGuideIntervalsDirty = true;
    }

    public void clearIntervals() {
        indentGuideIntervals.clear();
    }

    public void addInterval(int[] interval) {
        indentGuideIntervals.add(interval);
    }

    public int getIntervalsCount() {
        return indentGuideIntervals.size();
    }

    public int[] getIntervalAt(int index) {
        if (index < 0 || index >= indentGuideIntervals.size()) return null;
        return indentGuideIntervals.get(index);
    }

    public void setIntervalAt(int index, int[] interval) {
        if (index >= 0 && index < indentGuideIntervals.size()) {
            indentGuideIntervals.set(index, interval);
        }
    }

    public void removeIntervalAt(int index) {
        if (index >= 0 && index < indentGuideIntervals.size()) {
            indentGuideIntervals.remove(index);
        }
    }

    public void sortIntervals() {
        indentGuideIntervals.sort((a, b) -> Integer.compare(a[0], b[0]));
    }

    public boolean hasIntervals() {
        return !indentGuideIntervals.isEmpty();
    }
}
