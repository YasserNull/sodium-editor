package com.yn.sodiumeditor.core;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.yn.sodiumeditor.SodiumEditor;

/**
 * Edge handles custom glow arc effects for overscroll.
 */
public class Edge {
  
  public boolean edgeEffectEnabled = true;
  // --- عدل هذه القيم للتحكم في الحجم والقوة ---
  public float edgeEffectStrength = 0.8f; // قوة السحب (أصغر = أهدأ)
  public int edgeEffectColor = 0x80808080; // لون رمادي نصف شفاف
  private final float MAX_ARC_HEIGHT_PERCENT = 0.12f; // أقصى ارتفاع للقوس (12% من الشاشة)
  // ------------------------------------------

  private final SodiumEditor editor;
  private final Paint paint;
  private final RectF arcRect = new RectF();

  private float distTop, distBottom, distLeft, distRight;
  private boolean pullingTop, pullingBottom, pullingLeft, pullingRight;

  public Edge(SodiumEditor editor) {
    this.editor = editor;
    this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    this.paint.setStyle(Paint.Style.FILL);
    this.paint.setColor(edgeEffectColor);
  }

  public void setEdgeEffectColor(int color) {
    this.edgeEffectColor = color;
    this.paint.setColor(color);
    editor.invalidate();
  }

  public void pullTop(float deltaPx, float touchX) {
    pullingTop = true;
    distBottom = 0; // منع التعارض: صفر الحافة المقابلة
    distTop = Math.min(1.0f, distTop + Math.abs(deltaPx) / editor.getHeight() * edgeEffectStrength);
    editor.postInvalidateOnAnimation();
  }

  public void pullBottom(float deltaPx, float touchX) {
    pullingBottom = true;
    distTop = 0; // منع التعارض
    distBottom = Math.min(1.0f, distBottom + Math.abs(deltaPx) / editor.getHeight() * edgeEffectStrength);
    editor.postInvalidateOnAnimation();
  }

  public void pullLeft(float deltaPx, float touchY) {
    pullingLeft = true;
    distRight = 0; // منع التعارض
    distLeft = Math.min(1.0f, distLeft + Math.abs(deltaPx) / editor.getWidth() * edgeEffectStrength);
    editor.postInvalidateOnAnimation();
  }

  public void pullRight(float deltaPx, float touchY) {
    pullingRight = true;
    distLeft = 0; // منع التعارض
    distRight = Math.min(1.0f, distRight + Math.abs(deltaPx) / editor.getWidth() * edgeEffectStrength);
    editor.postInvalidateOnAnimation();
  }

  public void releaseVertical() {
    pullingTop = false;
    pullingBottom = false;
    editor.postInvalidateOnAnimation();
  }

  public void releaseHorizontal() {
    pullingLeft = false;
    pullingRight = false;
    editor.postInvalidateOnAnimation();
  }

  public void absorbTop(float velocity) {
    distBottom = 0;
    distTop = Math.min(1.0f, Math.abs(velocity) / 5000f);
    editor.postInvalidateOnAnimation();
  }

  public void absorbBottom(float velocity) {
    distTop = 0;
    distBottom = Math.min(1.0f, Math.abs(velocity) / 5000f);
    editor.postInvalidateOnAnimation();
  }

  public void absorbLeft(float velocity) {
    distRight = 0;
    distLeft = Math.min(1.0f, Math.abs(velocity) / 5000f);
    editor.postInvalidateOnAnimation();
  }

  public void absorbRight(float velocity) {
    distLeft = 0;
    distRight = Math.min(1.0f, Math.abs(velocity) / 5000f);
    editor.postInvalidateOnAnimation();
  }

  public void releaseAll() {
    pullingTop = pullingBottom = pullingLeft = pullingRight = false;
    editor.postInvalidateOnAnimation();
  }

  public void draw(Canvas canvas) {
    int w = editor.getWidth();
    int h = editor.getHeight();
    if (w <= 0 || h <= 0) return;

    boolean animating = false;

    // Top
    if (distTop > 0.001f) {
      drawGlowArc(canvas, w * 0.5f, 0, w, distTop, 0);
      if (!pullingTop) distTop *= 0.85f;
      animating = true;
    }

    // Bottom
    if (distBottom > 0.001f) {
      drawGlowArc(canvas, w * 0.5f, h, w, distBottom, 180);
      if (!pullingBottom) distBottom *= 0.85f;
      animating = true;
    }

    // Left
    if (distLeft > 0.001f) {
      drawGlowArc(canvas, 0, h * 0.5f, h, distLeft, 270);
      if (!pullingLeft) distLeft *= 0.85f;
      animating = true;
    }

    // Right
    if (distRight > 0.001f) {
      drawGlowArc(canvas, w, h * 0.5f, h, distRight, 90);
      if (!pullingRight) distRight *= 0.85f;
      animating = true;
    }

    if (animating) {
      editor.postInvalidateOnAnimation();
    }
  }

  private void drawGlowArc(Canvas canvas, float cx, float cy, int size, float dist, float rotation) {
    int save = canvas.save();
    canvas.translate(cx, cy);
    canvas.rotate(rotation);

    // حجم أصغر وأكثر تناسقاً
    float glowHeight = size * MAX_ARC_HEIGHT_PERCENT * dist; 
    float glowWidth = size * 1.2f;

    arcRect.set(-glowWidth / 2, -glowHeight, glowWidth / 2, glowHeight);
    paint.setAlpha((int) (120 * Math.sqrt(dist))); // شفافية أقل للرمادي
    
    canvas.drawArc(arcRect, 0, 180, true, paint);
    canvas.restoreToCount(save);
  }
}
