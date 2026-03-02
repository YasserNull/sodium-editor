package com.yn.sodiumeditor.renderer.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Paint;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.config.CharAnimationConfig;

public final class CharAnimator {
    private final SodiumEditor view;
    private final CharAnimationConfig config;

    private long lastCharAnimUptime = 0L;
    @Nullable private String lastComposingTextForCharAnim;

    private int charAnimLine = -1;
    private int charAnimStartChar = 0;
    private int charAnimEndChar = 0;
    private float charAnimAlpha = 0f;
    @Nullable private ValueAnimator charAnimAnimator;

    private int delAnimLine = -1;
    private int delAnimAtChar = 0;
    @Nullable private String delAnimText;
    @Nullable private Paint delAnimPaint;
    private float delAnimAlpha = 0f;
    @Nullable private ValueAnimator delAnimAnimator;

    public CharAnimator(SodiumEditor view, CharAnimationConfig config) {
        this.view = view;
        this.config = config;
    }

    public void startCharAnimationFromText(@Nullable CharSequence committedText) {
        if (!config.isEnabled()) return;
        if (committedText == null) return;

        final int targetLine = view.cursorState.getCursorLine();
        final int targetEndChar = view.cursorState.getCursorChar();

        int extractedCodePoint = -1;
        int extractedCharCount = 0;
        int i = committedText.length();
        while (i > 0) {
            int codePoint = Character.codePointBefore(committedText, i);
            i -= Character.charCount(codePoint);

            if (codePoint == '\n' || codePoint == '\r') continue;
            if (Character.isWhitespace(codePoint)) continue;

            extractedCodePoint = codePoint;
            extractedCharCount = Character.charCount(codePoint);
            break;
        }
        if (extractedCodePoint == -1) return;

        final int finalCharCount = extractedCharCount;
        long now = SystemClock.uptimeMillis();
        long delta = (lastCharAnimUptime == 0L) ? Long.MAX_VALUE : (now - lastCharAnimUptime);
        lastCharAnimUptime = now;
        final int animDuration =
                (delta <= config.getFastThresholdMs())
                        ? Math.max(1, Math.min(config.getDurationMs(), config.getFastDurationMs()))
                        : Math.max(1, config.getDurationMs());

        Runnable start =
                () -> {
                    if (delAnimAnimator != null) delAnimAnimator.cancel();
                    if (charAnimAnimator != null) charAnimAnimator.cancel();
                    charAnimLine = targetLine;
                    charAnimEndChar = Math.max(0, targetEndChar);
                    charAnimStartChar = Math.max(0, charAnimEndChar - finalCharCount);
                    charAnimAlpha = 0.2f;
                    view.invalidateLineGlobal(charAnimLine);

                    charAnimAnimator = ValueAnimator.ofFloat(0.2f, 1f);
                    charAnimAnimator.setDuration(animDuration);
                    charAnimAnimator.addUpdateListener(
                            a -> {
                                Object v = a.getAnimatedValue();
                                charAnimAlpha = (v instanceof Float) ? (Float) v : 0f;
                                view.invalidateLineGlobal(charAnimLine);
                            });
                    charAnimAnimator.addListener(
                            new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    charAnimAlpha = 0f;
                                    charAnimLine = -1;
                                    view.invalidate();
                                }

                                @Override
                                public void onAnimationCancel(Animator animation) {
                                    charAnimAlpha = 0f;
                                    charAnimLine = -1;
                                    view.invalidate();
                                }
                            });
                    charAnimAnimator.start();
                };
        if (Looper.myLooper() == Looper.getMainLooper()) start.run();
        else view.post(start);
    }

    public void startDeleteAnimation(
            int targetLine, int atChar, @Nullable String removedText, @Nullable Paint paintToUse) {
        if (!config.isEnabled()) return;
        if (removedText == null || removedText.isEmpty()) return;

        final int lineForAnim = targetLine;
        final int atForAnim = Math.max(0, atChar);
        final String textForAnim = removedText;
        final Paint p = (paintToUse != null) ? paintToUse : view.paint;
        long now = SystemClock.uptimeMillis();
        long delta = (lastCharAnimUptime == 0L) ? Long.MAX_VALUE : (now - lastCharAnimUptime);
        lastCharAnimUptime = now;
        final int animDuration =
                (delta <= config.getFastThresholdMs())
                        ? Math.max(1, Math.min(config.getDurationMs(), config.getFastDurationMs()))
                        : Math.max(1, config.getDurationMs());

        Runnable start =
                () -> {
                    if (charAnimAnimator != null) charAnimAnimator.cancel();
                    if (delAnimAnimator != null) delAnimAnimator.cancel();
                    delAnimLine = lineForAnim;
                    delAnimAtChar = atForAnim;
                    delAnimText = textForAnim;
                    delAnimPaint = p;
                    delAnimAlpha = 1f;
                    view.invalidateLineGlobal(lineForAnim);

                    delAnimAnimator = ValueAnimator.ofFloat(1f, 0f);
                    delAnimAnimator.setDuration(animDuration);
                    delAnimAnimator.addUpdateListener(
                            a -> {
                                Object v = a.getAnimatedValue();
                                delAnimAlpha = (v instanceof Float) ? (Float) v : 0f;
                                view.invalidateLineGlobal(lineForAnim);
                            });
                    delAnimAnimator.addListener(
                            new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    delAnimAlpha = 0f;
                                    delAnimLine = -1;
                                    delAnimAtChar = 0;
                                    delAnimText = null;
                                    delAnimPaint = null;
                                    view.invalidate();
                                }

                                @Override
                                public void onAnimationCancel(Animator animation) {
                                    delAnimAlpha = 0f;
                                    delAnimLine = -1;
                                    delAnimAtChar = 0;
                                    delAnimText = null;
                                    delAnimPaint = null;
                                    view.invalidate();
                                }
                            });
                    delAnimAnimator.start();
                };

        if (Looper.myLooper() == Looper.getMainLooper()) start.run();
        else view.post(start);
    }

    public void release() {
        if (charAnimAnimator != null) charAnimAnimator.cancel();
        if (delAnimAnimator != null) delAnimAnimator.cancel();
    }

    public void reset() {
        if (charAnimAnimator != null) charAnimAnimator.cancel();
        charAnimAnimator = null;
        charAnimAlpha = 0f;
        charAnimLine = -1;
        charAnimStartChar = 0;
        charAnimEndChar = 0;
        lastComposingTextForCharAnim = null;
        if (delAnimAnimator != null) delAnimAnimator.cancel();
        delAnimAnimator = null;
        delAnimAlpha = 0f;
        delAnimLine = -1;
        delAnimAtChar = 0;
        delAnimText = null;
        delAnimPaint = null;
    }

    @Nullable
    public String getLastComposingTextForCharAnim() {
        return lastComposingTextForCharAnim;
    }

    public void setLastComposingTextForCharAnim(@Nullable String text) {
        lastComposingTextForCharAnim = text;
    }

    public void clearLastComposingTextForCharAnim() {
        lastComposingTextForCharAnim = null;
    }

    public int getCharAnimLine() {
        return charAnimLine;
    }

    public int getCharAnimStartChar() {
        return charAnimStartChar;
    }

    public int getCharAnimEndChar() {
        return charAnimEndChar;
    }

    public float getCharAnimAlpha() {
        return charAnimAlpha;
    }

    public int getDelAnimLine() {
        return delAnimLine;
    }

    public int getDelAnimAtChar() {
        return delAnimAtChar;
    }

    @Nullable
    public String getDelAnimText() {
        return delAnimText;
    }

    @Nullable
    public Paint getDelAnimPaint() {
        return delAnimPaint;
    }

    public float getDelAnimAlpha() {
        return delAnimAlpha;
    }

    public boolean getDelAnimTextIsForLine(int line) {
        return delAnimLine == line;
    }

    public boolean getCharAnimTextIsForLine(int line) {
        return charAnimLine == line;
    }

    public Paint getTempPaint() {
        return charAnimTmpPaint;
    }

    private final Paint charAnimTmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
}
