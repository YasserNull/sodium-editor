package com.yn.sodiumeditor.view;

import android.graphics.Paint;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import androidx.annotation.Nullable;

final class WordWrapManager {
  int wrapWidthPx = -1;
  final HashMap<Integer, int[]> wrapCache = new HashMap<>();
  volatile int[] wrapLineCounts = null;
  volatile int[] wrapLinePrefix = null;
  volatile int wrapPrefixValidUpToLine = -1;
  volatile int totalWrapVisualLines = 0;
  volatile boolean wrapMetricsReady = false;
  volatile int wrapMetricsWidth = -1;
  final AtomicInteger wrapMetricsToken = new AtomicInteger(0);
  volatile boolean wrapMetricsBuilding = false;
  final AtomicInteger wrapSnapshotToken = new AtomicInteger(0);
  volatile boolean wrapSnapshotBuilding = false;
  volatile int wrapSnapshotWidth = -1;
  volatile int wrapSnapshotStart = -1;
  volatile int wrapSnapshotSize = -1;
  final AtomicInteger wrapPrefixToken = new AtomicInteger(0);
  volatile boolean wrapPrefixBuilding = false;
  volatile int wrapPrefixWidth = -1;
  volatile int wrapPrefixTargetLine = -1;
  boolean wrapPrefixRebuildPending = false;

  boolean isWordWrapIndicatorEnabled = false;
  final Paint wordWrapIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  float wordWrapIndicatorPadPx = 0f;
  float wordWrapIndicatorWidth = 0f;
  float wordWrapIndicatorTextScale = 0.85f;

  @Nullable int[] pendingWrapPrefixCounts = null;
  @Nullable int[] pendingWrapPrefixPrefix = null;
  int pendingWrapPrefixTotalVisualLines = 0;
  int pendingWrapPrefixWidthPx = -1;
  int pendingWrapPrefixValidUpToLine = -1;

  void initIndicatorPaint(Paint basePaint, float density, String indicatorText) {
    wordWrapIndicatorPadPx = 4f * density;
    wordWrapIndicatorPaint.setColor(0xFF9E9E9E);
    wordWrapIndicatorPaint.setAlpha(180);
    wordWrapIndicatorPaint.setTextAlign(Paint.Align.LEFT);
    wordWrapIndicatorPaint.setTextSize(basePaint.getTextSize() * wordWrapIndicatorTextScale);
    wordWrapIndicatorPaint.setTypeface(basePaint.getTypeface());
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(indicatorText);
  }

  void setIndicatorColor(int color) {
    wordWrapIndicatorPaint.setColor(color);
  }

  void updateIndicatorTextSize(float baseTextSizePx, float targetPx, String indicatorText) {
    if (baseTextSizePx > 0f && targetPx > 0f) {
      wordWrapIndicatorTextScale = targetPx / baseTextSizePx;
    } else {
      wordWrapIndicatorTextScale = 0.85f;
    }
    wordWrapIndicatorPaint.setTextSize(baseTextSizePx * wordWrapIndicatorTextScale);
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(indicatorText);
  }

  void updateIndicatorPaintForTextSize(float textSizePx, Paint basePaint, String indicatorText) {
    wordWrapIndicatorPaint.setTextSize(textSizePx * wordWrapIndicatorTextScale);
    wordWrapIndicatorPaint.setTypeface(basePaint.getTypeface());
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(indicatorText);
  }

  void updateIndicatorTypeface(Paint basePaint, String indicatorText) {
    wordWrapIndicatorPaint.setTypeface(basePaint.getTypeface());
    wordWrapIndicatorWidth = wordWrapIndicatorPaint.measureText(indicatorText);
  }
}
