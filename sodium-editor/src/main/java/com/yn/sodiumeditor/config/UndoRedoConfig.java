package com.yn.sodiumeditor.config;

public final class UndoRedoConfig {

  //================================================================================
  // Undo/Redo Stack Settings
  //================================================================================

  public int maxUndoStackSize = 100;
  public int maxRedoStackSize = 100;
  public boolean mergeSequentialEdits = true;
  public long mergeEditsTimeThresholdMs = 500;

  //================================================================================
  // Edit Types
  //================================================================================

  public boolean trackTextInsertions = true;
  public boolean trackTextDeletions = true;
  public boolean trackTextReplacements = true;
  public boolean trackFormatChanges = false;
  public boolean trackClipboardOperations = false;

  //================================================================================
  // Memory Management
  //================================================================================

  public int maxMemoryUsageBytes = 50 * 1024 * 1024; // 50MB
  public boolean autoTrimOnMemoryPressure = true;
  public float memoryPressureThreshold = 0.8f;

  //================================================================================
  // Edit Batching
  //================================================================================

  public boolean enableBatchEdits = true;
  public int maxBatchSize = 1000;
  public long batchTimeoutMs = 100;

  //================================================================================
  // Selection Tracking
  //================================================================================

  public boolean trackSelectionChanges = false;
  public boolean restoreSelectionOnUndo = true;
  public boolean restoreSelectionOnRedo = true;

  //================================================================================
  // Scroll Position
  //================================================================================

  public boolean restoreScrollPositionOnUndo = false;
  public boolean restoreScrollPositionOnRedo = false;

  //================================================================================
  // Notification Settings
  //================================================================================

  public boolean notifyOnUndo = true;
  public boolean notifyOnRedo = true;
  public boolean notifyOnStackChange = false;

  //================================================================================
  // Debug Settings
  //================================================================================

  public boolean debugLogging = false;
  public boolean trackEditHistory = false;
  public int historyMaxEntries = 1000;

  //================================================================================
  // Methods
  //================================================================================

  public void setMaxUndoStackSize(int size) {
    this.maxUndoStackSize = Math.max(1, Math.min(1000, size));
  }

  public int getMaxUndoStackSize() {
    return maxUndoStackSize;
  }

  public void setMaxRedoStackSize(int size) {
    this.maxRedoStackSize = Math.max(1, Math.min(1000, size));
  }

  public int getMaxRedoStackSize() {
    return maxRedoStackSize;
  }

  public void setMergeSequentialEdits(boolean merge) {
    this.mergeSequentialEdits = merge;
  }

  public boolean shouldMergeSequentialEdits() {
    return mergeSequentialEdits;
  }

  public void setMergeEditsTimeThresholdMs(long thresholdMs) {
    this.mergeEditsTimeThresholdMs = Math.max(0, thresholdMs);
  }

  public long getMergeEditsTimeThresholdMs() {
    return mergeEditsTimeThresholdMs;
  }

  public void setMaxMemoryUsageBytes(int bytes) {
    this.maxMemoryUsageBytes = Math.max(1024 * 1024, bytes); // Min 1MB
  }

  public int getMaxMemoryUsageBytes() {
    return maxMemoryUsageBytes;
  }

  public void setAutoTrimOnMemoryPressure(boolean autoTrim) {
    this.autoTrimOnMemoryPressure = autoTrim;
  }

  public boolean shouldAutoTrimOnMemoryPressure() {
    return autoTrimOnMemoryPressure;
  }

  public void setEnableBatchEdits(boolean enabled) {
    this.enableBatchEdits = enabled;
  }

  public boolean isBatchEditsEnabled() {
    return enableBatchEdits;
  }

  public void setMaxBatchSize(int size) {
    this.maxBatchSize = Math.max(1, size);
  }

  public int getMaxBatchSize() {
    return maxBatchSize;
  }

  public void setRestoreSelectionOnUndo(boolean restore) {
    this.restoreSelectionOnUndo = restore;
  }

  public boolean shouldRestoreSelectionOnUndo() {
    return restoreSelectionOnUndo;
  }

  public void setRestoreSelectionOnRedo(boolean restore) {
    this.restoreSelectionOnRedo = restore;
  }

  public boolean shouldRestoreSelectionOnRedo() {
    return restoreSelectionOnRedo;
  }

  public void setDebugLoggingEnabled(boolean enabled) {
    this.debugLogging = enabled;
  }

  public boolean isDebugLoggingEnabled() {
    return debugLogging;
  }

  public void clearAllSettings() {
    maxUndoStackSize = 100;
    maxRedoStackSize = 100;
    mergeSequentialEdits = true;
    mergeEditsTimeThresholdMs = 500;
    trackTextInsertions = true;
    trackTextDeletions = true;
    trackTextReplacements = true;
    trackFormatChanges = false;
    trackClipboardOperations = false;
    maxMemoryUsageBytes = 50 * 1024 * 1024;
    autoTrimOnMemoryPressure = true;
    enableBatchEdits = true;
    maxBatchSize = 1000;
    restoreSelectionOnUndo = true;
    restoreSelectionOnRedo = true;
    debugLogging = false;
  }
}
