package com.yn.sodiumeditor.core.highlight; 
import com.yn.sodiumeditor.SodiumEditor;
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
import com.yn.sodiumeditor.renderer.TextRender;
import com.yn.sodiumeditor.utils.FunctionLog;
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
    FunctionLog.f("PathUnderline", "PathUnderline", editor);
    this.editor = editor;
  }

  /**
   * Enables or disables path underlining.
   */
  public void setPathUnderliningEnabled(boolean enabled) {
    FunctionLog.f("PathUnderline", "setPathUnderliningEnabled", enabled);
    if (this.isPathUnderliningEnabled == enabled) return;
    this.isPathUnderliningEnabled = enabled;
    clearAllCaches();
    editor.invalidate();
  }

  /**
   * Clears path underline cache.
   */
  public void clearPathUnderlineCache() {
    FunctionLog.f("PathUnderline", "clearPathUnderlineCache");
    pathUnderlineCache.clear();
  }

  /**
   * Clears path underline cache for a specific line.
   */
  public void clearPathUnderlineCacheForLine(int line) {
    FunctionLog.f("PathUnderline", "clearPathUnderlineCacheForLine", line);
    pathUnderlineCache.remove(line);
  }

  /**
   * Gets path underline spans for a line.
   * Only returns spans for paths that have been validated as existing.
   */
  public List<TextRender.UnderlineSpan> getPathUnderlineSpansForLine(String line, int globalLine) {
    FunctionLog.f("PathUnderline", "getPathUnderlineSpansForLine", line, globalLine);
    if (!isPathUnderliningEnabled || pathUnderlinePattern == null) {
      if (editor.DEBUG_RENDER_LOGS) {
        android.util.Log.d("PathUnderline", "getPathUnderlineSpansForLine line=" + globalLine + " disabled=" + !isPathUnderliningEnabled + " pattern=" + (pathUnderlinePattern == null));
      }
      return null;
    }
    List<TextRender.UnderlineSpan> cached = pathUnderlineCache.get(globalLine);
    if (cached != null) return cached;

    if (editor.DEBUG_RENDER_LOGS) {
      android.util.Log.d("PathUnderline", "getPathUnderlineSpansForLine line=" + globalLine + " cache miss, searching for paths");
    }

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
        String path = line.substring(s, e);
        // Only underline if path exists (check cache first)
        Boolean exists = pathValidationCache.get(path);
        if (editor.DEBUG_RENDER_LOGS) {
          android.util.Log.d("PathUnderline", "found path=\"" + path + "\" exists=" + exists + " pending=" + pendingPathValidations.contains(path));
        }
        if (exists != null && exists) {
          spans.add(new TextRender.UnderlineSpan(s, e, true));
          if (editor.DEBUG_RENDER_LOGS) {
            android.util.Log.d("PathUnderline", "added underline span for path=\"" + path + "\"");
          }
        } else if (exists == null && !pendingPathValidations.contains(path)) {
          // Path not validated yet - validate in background
          validatePathInBackground(path, globalLine);
          if (editor.DEBUG_RENDER_LOGS) {
            android.util.Log.d("PathUnderline", "queued path=\"" + path + "\" for validation");
          }
        }
        // If exists == false, don't underline (path doesn't exist)
      }
    }
    // Always cache the result (even if empty) to avoid re-validation
    pathUnderlineCache.put(globalLine, spans);
    if (editor.DEBUG_RENDER_LOGS) {
      android.util.Log.d("PathUnderline", "cached " + spans.size() + " spans for line=" + globalLine);
    }
    return spans;
  }

  /**
   * Ensures path underline cache for a line.
   */
  public void ensurePathUnderlineCacheForLine(String line, int globalLine) {
    FunctionLog.f("PathUnderline", "ensurePathUnderlineCacheForLine", line, globalLine);
    if (!isPathUnderliningEnabled || pathUnderlinePattern == null) return;
    if (pathUnderlineCache.get(globalLine) != null) return;
    getPathUnderlineSpansForLine(line, globalLine);
  }

  /**
   * Invalidates path underline cache for a line (e.g., when file system changes).
   */
  public void invalidatePathUnderlineCacheForLine(int line) {
    FunctionLog.f("PathUnderline", "invalidatePathUnderlineCacheForLine", line);
    pathUnderlineCache.remove(line);
  }

  /**
   * Clears all path underline caches and validation cache.
   */
  public void clearAllCaches() {
    FunctionLog.f("PathUnderline", "clearAllCaches");
    pathUnderlineCache.clear();
    pathValidationCache.clear();
    pendingPathValidations.clear();
  }

  /**
   * Checks if path underlining is active.
   */
  public boolean isPathUnderliningActive() {
    FunctionLog.f("PathUnderline", "isPathUnderliningActive");
    return isPathUnderliningEnabled && pathUnderlinePattern != null;
  }

  /**
   * Validates a path in the background.
   */
  public void validatePathInBackground(final String path, final int lineToInvalidate) {
    FunctionLog.f("PathUnderline", "validatePathInBackground", path, lineToInvalidate);
    // Avoid queueing the same path if it's already being checked
    if (pendingPathValidations.contains(path)) {
      return;
    }
    pendingPathValidations.add(path);

    editor.fileIO.ioHandler.post(
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

            // Only redraw if path exists (no need to redraw for non-existing paths)
            // The cache will be updated on next draw call
            if (exists) {
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
    FunctionLog.f("PathUnderline", "getPathUnderlinePaint");
    return pathUnderlineTmpPaint;
  }
}
