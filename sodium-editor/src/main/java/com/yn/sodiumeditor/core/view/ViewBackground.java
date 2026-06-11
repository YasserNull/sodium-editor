package com.yn.sodiumeditor.core.view;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;

public class ViewBackground {
  private final EditorView view;

  ViewBackground(EditorView view) {
    this.view = view;
  }

  public void drawEditorBackground(android.graphics.Canvas canvas) {
    if (view.hasEditorBackgroundColor) {
      canvas.drawColor(view.editorBackgroundColor);
    }
    if (view.editorBackgroundBitmap != null && !view.editorBackgroundBitmap.isRecycled()) {
      view.editorBackgroundDst.set(0, 0, view.editor.getWidth(), view.editor.getHeight());
      canvas.drawBitmap(view.editorBackgroundBitmap, null, view.editorBackgroundDst, null);
    }
  }

  public void setEditorBackgroundColor(int color) {
    view.hasEditorBackgroundColor = true;
    view.editorBackgroundColor = color;
    view.editor.invalidate();
  }

  public void clearEditorBackgroundColor() {
    view.hasEditorBackgroundColor = false;
    view.editor.invalidate();
  }

  public void setEditorBackgroundBitmap(Bitmap bitmap) {
    if (view.editorBackgroundBitmap != null && !view.editorBackgroundBitmap.isRecycled()) {
      view.editorBackgroundBitmap.recycle();
    }
    view.editorBackgroundBitmap = bitmap;
    view.editor.invalidate();
  }

  public void clearEditorBackgroundImage() {
    if (view.editorBackgroundBitmap != null && !view.editorBackgroundBitmap.isRecycled()) {
      view.editorBackgroundBitmap.recycle();
    }
    view.editorBackgroundBitmap = null;
    view.editor.invalidate();
  }

  public void setEditorBackgroundImageFromAssets(String assetPath) {
    if (assetPath == null) return;
    try (InputStream input = view.editor.getContext().getAssets().open(assetPath)) {
      Bitmap bmp = BitmapFactory.decodeStream(input);
      if (bmp != null) {
        view.editor.textRender.setEditorBackgroundBitmap(bmp);
      }
    } catch (Exception e) {
    }
  }

  public void setEditorBackgroundImageFromFile(String filePath) {
    if (filePath == null) return;
    try {
      Bitmap bmp = BitmapFactory.decodeFile(filePath);
      if (bmp != null) {
        view.editor.textRender.setEditorBackgroundBitmap(bmp);
      }
    } catch (Exception e) {
    }
  }
}
