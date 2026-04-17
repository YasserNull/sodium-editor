package com.yn.sodiumeditor.core.highlight; 
import com.yn.sodiumeditor.SodiumEditor;
import android.graphics.Paint;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.yn.sodiumeditor.renderer.TextRender;
/**
 * Manages URL underlining for the SodiumEditor.
 * Detects and underlines URLs in text.
 */
public class UrlUnderline {

  private final SodiumEditor editor;

  // URL underlining state
  public boolean isUrlUnderliningEnabled = true;
  @Nullable public Pattern urlUnderlinePattern = UrlUnderline.DEFAULT_URL_UNDERLINE_PATTERN;
  public final Paint urlUnderlineTmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  // URL underline cache
  public final LinkedHashMap<Integer, List<TextRender.UnderlineSpan>> urlUnderlineCache =
      new LinkedHashMap<Integer, List<TextRender.UnderlineSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<TextRender.UnderlineSpan>> eldest) {
          return size() > 1000;
        }
      };

  // Default URL pattern
  public static final Pattern DEFAULT_URL_UNDERLINE_PATTERN = Pattern.compile("https?://[^\\s]+");

  public UrlUnderline(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Enables or disables URL underlining.
   */
  public void setUrlUnderliningEnabled(boolean enabled) {
    if (this.isUrlUnderliningEnabled == enabled) return;
    this.isUrlUnderliningEnabled = enabled;
    urlUnderlineCache.clear();
    editor.invalidate();
  }

  /**
   * Sets the URL underlining regex pattern.
   */
  public void setUrlUnderliningRegex(@Nullable String regex) {
    if (regex == null || regex.trim().isEmpty()) {
      this.urlUnderlinePattern = null;
    } else {
      this.urlUnderlinePattern = Pattern.compile(regex);
    }
    urlUnderlineCache.clear();
    editor.invalidate();
  }

  /**
   * Clears URL underline cache.
   */
  public void clearUrlUnderlineCache() {
    urlUnderlineCache.clear();
  }

  /**
   * Clears URL underline cache for a specific line.
   */
  public void clearUrlUnderlineCacheForLine(int line) {
    urlUnderlineCache.remove(line);
  }

  /**
   * Gets URL underline spans for a line.
   */
  public List<TextRender.UnderlineSpan> getUrlUnderlineSpansForLine(String line, int globalLine) {
    if (!isUrlUnderliningEnabled || urlUnderlinePattern == null) return null;
    List<TextRender.UnderlineSpan> cached = urlUnderlineCache.get(globalLine);
    if (cached != null) return cached;

    List<TextRender.UnderlineSpan> spans = new ArrayList<>();
    Matcher matcher = urlUnderlinePattern.matcher(line);
    while (matcher.find()) {
      int start = matcher.start();
      int end = matcher.end();
      end = trimUrlUnderlineEnd(line, start, end);
      if (end > start) {
        spans.add(new TextRender.UnderlineSpan(start, end, false));
      }
    }
    urlUnderlineCache.put(globalLine, spans);
    return spans;
  }

  /**
   * Trims URL underline end to exclude trailing punctuation.
   */
  public static int trimUrlUnderlineEnd(String line, int start, int end) {
    while (end > start) {
      char c = line.charAt(end - 1);
      if (c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?' || c == ')' || c == ']' || c == '}') {
        end--;
      } else {
        break;
      }
    }
    return end;
  }

  /**
   * Ensures URL underline cache for a line.
   */
  public void ensureUrlUnderlineCacheForLine(String line, int globalLine) {
    if (!isUrlUnderliningEnabled || urlUnderlinePattern == null) return;
    if (urlUnderlineCache.get(globalLine) != null) return;
    getUrlUnderlineSpansForLine(line, globalLine);
  }

  /**
   * Checks if URL underlining is active.
   */
  public boolean isUrlUnderliningActive() {
    return isUrlUnderliningEnabled && urlUnderlinePattern != null;
  }
}
