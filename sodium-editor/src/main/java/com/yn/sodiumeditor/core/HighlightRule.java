package com.yn.sodiumeditor.core;

import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.regex.Pattern;

/**
 * Represents a highlight rule for syntax highlighting.
 */
public class HighlightRule {

    public enum HighlightRuleType {
        REGEX,
        STRING,
        BLOCK_COMMENT,
        LINE_COMMENT
    }

    // Style constants (copied from SodiumEditor)
    public static final int STYLE_NORMAL = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_BOLD_ITALIC = 3;

    public final HighlightRuleType type;
    public final Pattern pattern;
    public final Paint paint;
    public int style;
    public final boolean underline;

    public HighlightRule(
            String regex,
            int style,
            int color,
            float textSize,
            Typeface typeface,
            boolean underline,
            HighlightRuleType type) {
        this.type = type;
        if (type == HighlightRuleType.REGEX) {
            this.pattern = Pattern.compile(regex);
        } else {
            this.pattern = null;
        }
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setColor(color);
        this.paint.setTextSize(textSize);
        this.style = style;
        this.underline = underline;

        int typefaceStyle;
        switch (style) {
            case STYLE_BOLD:
                typefaceStyle = Typeface.BOLD;
                break;
            case STYLE_ITALIC:
                typefaceStyle = Typeface.ITALIC;
                break;
            case STYLE_BOLD_ITALIC:
                typefaceStyle = Typeface.BOLD_ITALIC;
                break;
            default:
                typefaceStyle = Typeface.NORMAL;
                break;
        }
        this.paint.setTypeface(Typeface.create(typeface, typefaceStyle));
        this.paint.setUnderlineText(underline);
    }

    public void updateTextSize(float sizePx) {
        paint.setTextSize(sizePx);
    }

    public void updateTypeface(Typeface typeface) {
        int typefaceStyle;
        switch (style) {
            case STYLE_BOLD:
                typefaceStyle = Typeface.BOLD;
                break;
            case STYLE_ITALIC:
                typefaceStyle = Typeface.ITALIC;
                break;
            case STYLE_BOLD_ITALIC:
                typefaceStyle = Typeface.BOLD_ITALIC;
                break;
            default:
                typefaceStyle = Typeface.NORMAL;
                break;
        }
        this.paint.setTypeface(Typeface.create(typeface, typefaceStyle));
    }
}
