package com.yn.sodiumeditor.view;

import android.graphics.Canvas;

final class ViewRender {
  private final SodiumEditorView view;

  ViewRender(SodiumEditorView view) {
    this.view = view;
  }

  void onDraw(Canvas canvas) {
    view.superOnDraw(canvas);
    drawEditorBackground(canvas);
    if (view.scrollManager.stretchOverscrollEnabled
        && (view.scrollManager.stretchX != 0f || view.scrollManager.stretchY != 0f)) {
      float sx = 1f + (view.scrollManager.stretchX * 0.12f * view.scrollManager.stretchOverscrollStrength);
      float sy = 1f + (view.scrollManager.stretchY * 0.12f * view.scrollManager.stretchOverscrollStrength);
      float pivotX =
          (view.scrollManager.stretchDirX < 0)
              ? 0f
              : (view.scrollManager.stretchDirX > 0 ? view.getWidth() : view.getWidth() * 0.5f);
      float pivotY =
          (view.scrollManager.stretchDirY < 0)
              ? 0f
              : (view.scrollManager.stretchDirY > 0 ? view.getHeight() : view.getHeight() * 0.5f);
      canvas.save();
      canvas.scale(sx, sy, pivotX, pivotY);
      drawContent(canvas);
      canvas.restore();
    } else {
      drawContent(canvas);
    }
    view.scrollManager.drawScrollBar(canvas);
  }

  private void drawEditorBackground(Canvas canvas) {
    if (view.hasEditorBackgroundColor) {
      canvas.drawColor(view.editorBackgroundColor);
    }
    if (view.editorBackgroundBitmap != null && !view.editorBackgroundBitmap.isRecycled()) {
      view.editorBackgroundDst.set(0, 0, view.getWidth(), view.getHeight());
      canvas.drawBitmap(view.editorBackgroundBitmap, null, view.editorBackgroundDst, null);
    }
  }

  private void drawContent(Canvas canvas) {
    view.drawContent(canvas);
  }

  private void drawContentWrapped(Canvas canvas) {
    view.drawContentWrapped(canvas);
  }
}
