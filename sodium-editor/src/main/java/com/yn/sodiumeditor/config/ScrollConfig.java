package com.yn.sodiumeditor;

public final class ScrollConfig {
    // Scroll modes
    public static final int SCROLL_MODE_SINGLE_AXIS = 0;
    public static final int SCROLL_MODE_GRID = 1;
    public static final int SCROLL_MODE_FREE = 2;

    // Scroll state
    public float scrollY = 0f;
    public float scrollX = 0f;
    public float maxLineWidthForScroll = 0f;
    public float maxTextStartXForScroll = 0f;
    public float maxScrollXForScroll = 0f;
    public float dragMaxScrollX = -1f;
    public boolean scrollerIsScrolling = false;
    public float autoScrollX = 0f;
    public float autoScrollY = 0f;
    public int scrollMode = SCROLL_MODE_FREE;
    public int scrollLockAxis = 0;

    // Sensitivity settings
    public float scrollSensitivity = 1f;
    public float flingSensitivity = 1f;

    // Fling bounce settings
    public boolean flingBounceEnabled = false;
    public int flingBounceOverScrollPx = -1;
    public float flingBounceOverScrollFactor = 0.2f;

    // Stretch overscroll settings
    public boolean stretchOverscrollEnabled = false;
    public float stretchOverscrollStrength = 1f;

    // Scrollbar settings
    public boolean scrollBarEnabled = true;
    public int scrollBarColor = 0x80FFFFFF;
    public float scrollBarWidthPx = 6f;
    public float scrollBarMinThumbPx = 24f;
    public float scrollBarCornerRadiusPx = 6f;
    public float scrollBarMarginPx = 2f;
    public boolean scrollBarFadeEnabled = true;
    public long scrollBarFadeDelayMs = 1000;
    public long scrollBarFadeDurationMs = 200;
    public float scrollBarAlpha = 0f;
    public int scrollBarHaloColor = 0x40FFFFFF;
    public float scrollBarHaloSizePx = 8f;

    // Scrollbar interaction state
    public boolean draggingScrollBar = false;
    public float scrollBarDragOffset = 0f;

    public void resetStretchState() {
        stretchX = 0f;
        stretchY = 0f;
        stretchDirX = 0;
        stretchDirY = 0;
    }

    public float stretchX = 0f;
    public float stretchY = 0f;
    public int stretchDirX = 0;
    public int stretchDirY = 0;
}
