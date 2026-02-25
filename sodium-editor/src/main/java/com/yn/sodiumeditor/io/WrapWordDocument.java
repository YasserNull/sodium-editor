package com.yn.sodiumeditor.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

public final class WrapWordDocument {

  //================================================================================
  // Dependencies
  //================================================================================

  private Map<Integer, String> modifiedLines;

  public WrapWordDocument(Map<Integer, String> modifiedLines) {
    this.modifiedLines = modifiedLines;
  }

  public void setModifiedLinesRef(Map<Integer, String> modifiedLines) {
    this.modifiedLines = modifiedLines;
  }

  //================================================================================
  // Read File and Process Lines
  //================================================================================

  public void processFileLines(
      File file,
      int totalLines,
      LineCallback callback
  ) throws IOException {
    BufferedReader reader = null;
    try {
      reader = openReader(file);
      int lineIndex = 0;

      while (true) {
        String fileLine = (reader != null) ? reader.readLine() : null;
        String modifiedLine = getModifiedLine(lineIndex);
        String line = (modifiedLine != null) ? modifiedLine : (fileLine != null ? fileLine : "");
        boolean isModified = (modifiedLine != null);

        callback.onLine(lineIndex, line, isModified);
        lineIndex++;

        if (fileLine == null && !isModified) {
          break;
        }

        if (lineIndex >= totalLines && fileLine == null) {
          break;
        }
      }

      callback.onComplete();
    } catch (IOException e) {
      callback.onError(e);
      throw e;
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException ignored) {
        }
      }
    }
  }

  //================================================================================
  // Get Line Text
  //================================================================================

  public String getLineText(int lineIndex, BufferedReader reader) throws IOException {
    String modifiedLine = getModifiedLine(lineIndex);
    if (modifiedLine != null) {
      return modifiedLine;
    }

    if (reader != null) {
      String fileLine = reader.readLine();
      return (fileLine != null) ? fileLine : "";
    }

    return "";
  }

  //================================================================================
  // Modified Lines Check
  //================================================================================

  public boolean isModifiedLine(int lineIndex) {
    synchronized (modifiedLines) {
      return modifiedLines.containsKey(lineIndex);
    }
  }

  public String getModifiedLine(int lineIndex) {
    synchronized (modifiedLines) {
      return modifiedLines.get(lineIndex);
    }
  }

  //================================================================================
  // Open Reader
  //================================================================================

  public BufferedReader openReader(File file) throws IOException {
    return new BufferedReader(new FileReader(file));
  }

  //================================================================================
  // Line Callback Interface
  //================================================================================

  public interface LineCallback {
    void onLine(int lineIndex, String line, boolean isModified);
    void onComplete();
    void onError(Exception e);
  }
}
