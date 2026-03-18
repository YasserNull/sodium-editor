package com.yn.sodiumeditor;

import android.graphics.Paint;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.yn.sodiumeditor.TextRender;
/**
 * Manages path underlining for the SodiumEditor.
 * Detects and underlines file paths in text.
 */
public class PathUnderline {

  private final SodiumEditor editor;

  // Path underlining state
  public boolean isPathUnderliningEnabled = true;
  @Nullable public Pattern pathUnderlinePattern = Pattern.compile("/[^\\s,;()'\"]+");
  public final Paint pathUnderlineTmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  // Path underline cache
  public final LinkedHashMap<Integer, List<TextRender.UnderlineSpan>> pathUnderlineCache =
      new LinkedHashMap<Integer, List<TextRender.UnderlineSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<TextRender.UnderlineSpan>> eldest) {
          return size() > 1000;
        }
      };

  // Path validation cache
  public final ConcurrentHashMap<String, Boolean> pathValidationCache = new ConcurrentHashMap<>();
  public final Set<String> pendingPathValidations = Collections.synchronizedSet(new HashSet<>());

  public PathUnderline(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Enables or disables path underlining.
   */
  public void setPathUnderliningEnabled(boolean enabled) {
    if (this.isPathUnderliningEnabled == enabled) return;
    this.isPathUnderliningEnabled = enabled;
    // Clear all caches when state changes to ensure fresh checks.
    pathUnderlineCache.clear();
    pathValidationCache.clear();
    pendingPathValidations.clear();
    editor.invalidate();
  }

  /**
   * Clears path underline cache.
   */
  public void clearPathUnderlineCache() {
    pathUnderlineCache.clear();
  }

  /**
   * Clears path underline cache for a specific line.
   */
  public void clearPathUnderlineCacheForLine(int line) {
    pathUnderlineCache.remove(line);
  }

  /**
   * Gets path underline spans for a line.
   */
  public List<TextRender.UnderlineSpan> getPathUnderlineSpansForLine(String line, int globalLine) {
    if (!isPathUnderliningEnabled || pathUnderlinePattern == null) return null;
    List<TextRender.UnderlineSpan> cached = pathUnderlineCache.get(globalLine);
    if (cached != null) return cached;

    List<TextRender.UnderlineSpan> spans = new ArrayList<>();
    Matcher m = pathUnderlinePattern.matcher(line);
    while (m.find()) {
      int s = m.start();
      int e = m.end();
      // Trim trailing punctuation
      while (e > s) {
        char c = line.charAt(e - 1);
        if (c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?') {
          e--;
        } else {
          break;
        }
      }
      if (e > s) {
        spans.add(new TextRender.UnderlineSpan(s, e, true));
      }
    }
    pathUnderlineCache.put(globalLine, spans);
    return spans;
  }

  /**
   * Ensures path underline cache for a line.
   */
  public void ensurePathUnderlineCacheForLine(String line, int globalLine) {
    if (!isPathUnderliningEnabled || pathUnderlinePattern == null) return;
    if (pathUnderlineCache.get(globalLine) != null) return;
    getPathUnderlineSpansForLine(line, globalLine);
  }

  /**
   * Checks if path underlining is active.
   */
  public boolean isPathUnderliningActive() {
    return isPathUnderliningEnabled && pathUnderlinePattern != null;
  }

  /**
   * Validates a path in the background.
   */
  public void validatePathInBackground(final String path, final int lineToInvalidate) {
    // Avoid queueing the same path if it's already being checked
    if (pendingPathValidations.contains(path)) {
      return;
    }
    pendingPathValidations.add(path);

    editor.ioHandler.post(
        () -> {
          boolean exists = false;
          try {
            java.io.File file = new java.io.File(path);
            exists = file.exists();
          } catch (Exception e) {
            // Ignore security exceptions or other errors
          } finally {
            pathValidationCache.put(path, exists);
            pendingPathValidations.remove(path);

            if (exists) {
              // Invalidate caches for the line and trigger a redraw
              editor.caret.mainHandler.post(
                  () -> {
                    pathUnderlineCache.remove(lineToInvalidate);
                    editor.invalidate();
                  });
            }
          }
        });
  }

  /**
   * Gets the path underline paint.
   */
  public Paint getPathUnderlinePaint() {
    return pathUnderlineTmpPaint;
  }
}
