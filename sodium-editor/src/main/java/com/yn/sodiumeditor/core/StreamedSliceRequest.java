package com.yn.sodiumeditor.core;

public final class StreamedSliceRequest {
  public final int line;
  public final int start;
  public final int end;

  public StreamedSliceRequest(int line, int start, int end) {
    this.line = line;
    this.start = start;
    this.end = end;
  }
}
