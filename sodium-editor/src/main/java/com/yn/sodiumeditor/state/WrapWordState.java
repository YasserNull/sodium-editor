package com.yn.sodiumeditor.state;

import java.util.concurrent.atomic.AtomicInteger;

public final class WrapWordState {

  //================================================================================
  // Config
  //================================================================================

  public boolean isWordWrapEnabled = false;
  public int wrapWidthPx = -1;

  //================================================================================
  // Async Tokens
  //================================================================================

  public final AtomicInteger wrapMetricsToken = new AtomicInteger(0);
  public final AtomicInteger wrapSnapshotToken = new AtomicInteger(0);
  public final AtomicInteger wrapPrefixToken = new AtomicInteger(0);

  //================================================================================
  // Build Flags
  //================================================================================

  public volatile boolean wrapMetricsBuilding = false;
  public volatile boolean wrapSnapshotBuilding = false;
  public volatile boolean wrapPrefixBuilding = false;

  //================================================================================
  // Snapshot State
  //================================================================================

  public volatile int wrapSnapshotWidth = -1;
  public volatile int wrapSnapshotStart = -1;
  public volatile int wrapSnapshotSize = -1;

  //================================================================================
  // Prefix State
  //================================================================================

  public volatile int wrapPrefixWidth = -1;
  public volatile int wrapPrefixTargetLine = -1;
  public volatile boolean wrapPrefixRebuildPending = false;
  public volatile int wrapPrefixValidUpToLine = -1;

  //================================================================================
  // Methods
  //================================================================================

  public void resetTokens() {
    wrapMetricsToken.incrementAndGet();
    wrapSnapshotToken.incrementAndGet();
    wrapPrefixToken.incrementAndGet();
  }

  public void cancelAllBuilds() {
    wrapMetricsBuilding = false;
    wrapSnapshotBuilding = false;
    wrapPrefixBuilding = false;
  }

  public void cancelPrefixBuild() {
    wrapPrefixToken.incrementAndGet();
    wrapPrefixBuilding = false;
    wrapPrefixRebuildPending = true;
  }

  public void setWrapWidth(int width) {
    this.wrapWidthPx = width;
  }

  public int getWrapWidth() {
    return wrapWidthPx;
  }

  public boolean isBuilding() {
    return wrapMetricsBuilding || wrapSnapshotBuilding || wrapPrefixBuilding;
  }

  public boolean isPrefixBuilding() {
    return wrapPrefixBuilding;
  }

  public void setPrefixBuilding(boolean building) {
    this.wrapPrefixBuilding = building;
  }

  public void setPrefixState(int width, int targetLine) {
    this.wrapPrefixWidth = width;
    this.wrapPrefixTargetLine = targetLine;
  }

  public boolean shouldRebuildPrefix(int width, int targetLine) {
    if (wrapPrefixBuilding) {
      return wrapPrefixWidth != width || wrapPrefixTargetLine < targetLine;
    }
    return true;
  }
}
