package com.yn.sodiumeditor.core.highlite;
import com.yn.sodiumeditor.SodiumEditor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages color code highlighting for the SodiumEditor.
 * Highlights hex color literals like #RRGGBB, #AARRGGBB, 0xRRGGBB, etc.
 */
public class ColorCodeHighlight {

  private final SodiumEditor editor;

  // Color code highlighting state
  public boolean isColorHighlightingEnabled = true;

  // Color code background cache
  public final LinkedHashMap<Integer, int[]> colorCodeBgCache =
      new LinkedHashMap<Integer, int[]>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(LinkedHashMap.Entry<Integer, int[]> eldest) {
          return size() > 256;
        }
      };

  // Color overlay paint
  public final Paint colorOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  // Color hex pattern
  public static final Pattern COLOR_HEX_PATTERN =
      Pattern.compile(
          "(#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8}))\\b|(\\b0x[a-fA-F0-9]{6,8}\\b)",
          Pattern.CASE_INSENSITIVE);

  public ColorCodeHighlight(SodiumEditor editor) {
    this.editor = editor;
    colorOverlayPaint.setStyle(Paint.Style.FILL);
  }

  /**
   * Enables or disables color code highlighting.
   */
  public void setColorCodeHighlightingEnabled(boolean enabled) {
    if (this.isColorHighlightingEnabled == enabled) return;
    this.isColorHighlightingEnabled = enabled;
    colorCodeBgCache.clear();
    editor.invalidate();
  }

  /**
   * Clears color code cache for a specific line.
   */
  public void clearColorCodeCacheForLine(int line) {
    colorCodeBgCache.remove(line);
  }

  /**
   * Clears all color code caches.
   */
  public void clearColorCodeCaches() {
    colorCodeBgCache.clear();
  }

  /**
   * Draws color code backgrounds for a line.
   */
  public void drawColorCodeBackgrounds(Canvas canvas, String line, int globalLine) {
    if (!isColorHighlightingEnabled || line.isEmpty()) {
      return;
    }

    if (line.indexOf('#') < 0 && line.indexOf('0') < 0) return;

    int[] triples = colorCodeBgCache.get(globalLine);
    if (triples == null) {
      ArrayList<Integer> tmp = null;
      Matcher matcher = COLOR_HEX_PATTERN.matcher(line);
      while (matcher.find()) {
        String colorString = matcher.group(0);
        if (colorString == null || colorString.isEmpty()) continue;

        int color;
        try {
          if (colorString.startsWith("0x") || colorString.startsWith("0X")) {
            String hex = colorString.substring(2);
            if (hex.length() == 6) hex = "FF" + hex;
            color = (int) Long.parseLong(hex, 16);
          } else {
            color = Color.parseColor(colorString);
          }
        } catch (Exception e) {
          continue;
        }

        int backgroundColor = (color & 0x00FFFFFF) | (0xC0 << 24);
        if (tmp == null) tmp = new ArrayList<>();
        tmp.add(matcher.start());
        tmp.add(matcher.end());
        tmp.add(backgroundColor);
      }
      if (tmp == null || tmp.isEmpty()) {
        triples = new int[0];
      } else {
        triples = new int[tmp.size()];
        for (int i = 0; i < tmp.size(); i++) triples[i] = tmp.get(i);
      }
      colorCodeBgCache.put(globalLine, triples);
    }

    if (triples.length == 0) return;

    float top = editor.textRender.getDrawLineTop(globalLine);
    float bottom = top + editor.textRender.lineHeight;
    colorOverlayPaint.setStyle(Paint.Style.FILL);
    for (int i = 0; i + 2 < triples.length; i += 3) {
      int start = triples[i];
      int end = triples[i + 1];
      int backgroundColor = triples[i + 2];

      float left = editor.measureText(line, start, globalLine);
      float right = editor.measureText(line, end, globalLine);
      colorOverlayPaint.setColor(backgroundColor);
      canvas.drawRect(left, top, right, bottom, colorOverlayPaint);
    }
  }
}
