package com.yn.sodiumeditor.input.events;

import android.view.MotionEvent;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.fold.CodeFold;
import com.yn.sodiumeditor.io.EditOp;

/**
 * Handles selection dragging and auto-scroll for SodiumEditor.
 */
public class DragSelectionHandler {
    private final SodiumEditor editor;

    public DragSelectionHandler(SodiumEditor editor) {
        this.editor = editor;
    }

    public boolean handleActionDown(MotionEvent event) {
        float ex = event.getX(), ey = event.getY();
        if (editor.selection.hasSelection) {
            editor.selectionHandles.updateHandlesPosition();
        }
        editor.cursorHandle.updateCursorHandlePosition();

        if (editor.selection.hasSelection && editor.selectionHandles.hitTestLeft(ex, ey)) {
            editor.selectionHandles.draggingHandle = 1;
            return true;
        } else if (editor.selection.hasSelection && editor.selectionHandles.hitTestRight(ex, ey)) {
            editor.selectionHandles.draggingHandle = 2;
            return true;
        } else if (editor.isFocused() && !editor.selection.hasSelection && editor.cursorHandle.hitTest(ex, ey)) {
            editor.selectionHandles.draggingHandle = 3;
            return true;
        }
        return false;
    }

    public boolean handleActionMove(MotionEvent event) {
        float ex = event.getX(), ey = event.getY();
        
        if (editor.selection.longPressSelecting
            && editor.selectionHandles.draggingHandle == 0
            && !editor.selection.isLineNumberSelecting) {
            
            float moveX, moveY;
            boolean isTwoFinger = editor.selection.state.longPressEndPointerId != -1;
            
            if (isTwoFinger) {
                int pointerIndex = event.findPointerIndex(editor.selection.state.longPressEndPointerId);
                if (pointerIndex == -1) return true;
                moveX = event.getX(pointerIndex);
                moveY = event.getY(pointerIndex);
            } else {
                if (!editor.selection.longPressFreeForm) {
                    float dx = ex - editor.onTouch.downX;
                    float dy = ey - editor.onTouch.downY;
                    if (dx * dx + dy * dy > editor.onTouch.touchSlop * editor.onTouch.touchSlop * 4) {
                        editor.selection.state.longPressFreeForm = true;
                        editor.selection.syncFromState();
                        editor.selection.hasSelection = false;
                        editor.selection.selecting = true;
                        editor.popup.hidePopup();
                    } else {
                        return true;
                    }
                }
                moveX = ex;
                moveY = ey;
            }

            EditOp.CursorTarget target = editor.wordWrap.getCursorTargetForPosition(moveX, moveY, null);
            int line = target.line;
            editor.fileIO.ensureLineInWindow(line, true);
            String ln = editor.windowRender.getLineTextForRender(line);
            int clamped = Math.max(0, Math.min(target.ch, (ln == null) ? 0 : ln.length()));
            
            // Code folding logic
            if (editor.codeFold.isCodeFoldingEnabled) {
                clamped = handleCodeFoldSelection(line, ln, moveX, clamped);
            }
            
            editor.selection.updateLongPressSelection(line, clamped);
            editor.popup.hidePopup();
            updateAutoScroll(moveX, moveY);
            editor.invalidate();
            return true;
        }

        if (editor.selectionHandles.draggingHandle != 0) {
            updateHandlePosition(ex, ey);
            if (editor.selectionHandles.draggingHandle == 1 || editor.selectionHandles.draggingHandle == 2) {
                editor.popup.showPopupAtSelection();
            }
            updateAutoScroll(ex, ey);
            editor.invalidate();
            return true;
        }
        return false;
    }

    public void handleActionUpOrCancel() {
        editor.caret.mainHandler.removeCallbacks(editor.scroll.autoScrollRunnable);        if (editor.selectionHandles.draggingHandle != 0) {
            if (editor.selectionHandles.draggingHandle == 3) {
                updateHandlePosition(editor.onTouch.lastTouchX, editor.onTouch.lastTouchY);
                editor.cursorAnimation.snapToPosition(editor.caret.getCaretDocumentX(), editor.caret.getCaretDocumentY());
            }
            if (editor.selectionHandles.draggingHandle == 1 || editor.selectionHandles.draggingHandle == 2) {
                editor.popup.showPopupAtSelection();
            }
            editor.selectionHandles.draggingHandle = 0;
            editor.invalidate();
        }
    }

    private void updateAutoScroll(float x, float y) {
        float scrollMargin = editor.textRender.lineHeight * 2f;
        float scrollSpeed = Math.max(4f, editor.textRender.lineHeight * 0.35f);
        editor.scroll.autoScrollY = 0;
        editor.scroll.autoScrollX = 0;
        
        if (y < scrollMargin) editor.scroll.autoScrollY = -scrollSpeed;
        else if (y > (editor.getHeight() - editor.view.keyboardHeight) - scrollMargin) editor.scroll.autoScrollY = scrollSpeed;
        
        if (x < scrollMargin) editor.scroll.autoScrollX = -scrollSpeed;
        else if (x > editor.getWidth() - scrollMargin) editor.scroll.autoScrollX = scrollSpeed;
        
        if (editor.textRender.isRtl && !editor.wordWrap.isWordWrapEnabled) editor.scroll.autoScrollX = -editor.scroll.autoScrollX;

        if (editor.selectionHandles.draggingHandle != 0) {
            if (editor.scroll.autoScrollX > 0 && editor.selectionHandles.lastDragAtLineEnd) editor.scroll.autoScrollX = 0;
            if (editor.scroll.autoScrollX < 0 && editor.selectionHandles.lastDragAtLineStart) editor.scroll.autoScrollX = 0;
        }

        if (editor.scroll.autoScrollX != 0 || editor.scroll.autoScrollY != 0) {
            editor.caret.mainHandler.post(editor.scroll.autoScrollRunnable);
        } else {
            editor.caret.mainHandler.removeCallbacks(editor.scroll.autoScrollRunnable);
        }
    }

    public void updateHandlePosition(float touchX, float touchY) {
        if (editor.selection.isSelectAllActive || editor.selection.isEntireFileSelected) {
            editor.selection.isSelectAllActive = false;
            editor.selection.isEntireFileSelected = false;
            editor.popup.showPopupAtSelection();
        }

        EditOp.CursorTarget target = editor.wordWrap.getCursorTargetForPosition(touchX, touchY, null);
        int line = target.line;

        if (editor.fileIO.isEof) {
            int lastValidLine = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size() - 1;
            if (line > lastValidLine) line = lastValidLine;
        }

        editor.fileIO.ensureLineInWindow(line, true);
        String ln = editor.windowRender.getLineTextForRender(line);
        int clamped = Math.max(0, Math.min(target.ch, (ln == null) ? 0 : ln.length()));

        if (editor.codeFold.isCodeFoldingEnabled) {
            CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(line);
            if (range != null && range.collapsed) {
                float xLocal = editor.scroll.viewToTextX(touchX);
                float x;
                if (editor.wordWrap.isWordWrapEnabled) {
                    int[] starts = editor.wordWrap.getWrapStartsForLine(line, ln);
                    int seg = editor.wordWrap.getWrapSegmentIndexForChar(starts, Math.max(0, Math.min(clamped, ln.length())));
                    int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
                    x = xLocal + editor.textRender.measureTextWithVisualSpaces(ln, 0, segStart, editor.textRender.paint);
                } else {
                    x = xLocal;
                }

                int prefixEnd;
                if (range.isBlockComment) {
                    prefixEnd = Math.min(range.openCharIndex + 2, (ln == null) ? 0 : ln.length());
                } else if (range.isIndentFold) {
                    prefixEnd = (ln == null) ? 0 : ln.length();
                } else {
                    prefixEnd = Math.min(range.openCharIndex + 1, (ln == null) ? 0 : ln.length());
                }
                float xStart = editor.highlite.measureHighlightedSegmentWidth(ln, line, 0, prefixEnd);
                float placeholderWidth = Math.max(0f, editor.textRender.paint.measureText(CodeFold.FOLD_PLACEHOLDER_TEXT));
                float closeStart = xStart + placeholderWidth;
                float closeWidth = range.isBlockComment ? editor.textRender.paint.measureText("*/") : editor.textRender.paint.measureText(String.valueOf(range.closeChar));
                String endLineText = editor.windowRender.getLineTextForRender(range.endLine);
                int closeIdx = editor.codeFold.resolveCloseCharIndex(range, endLineText);
                int suffixStart = range.isBlockComment ? (closeIdx >= 0 ? closeIdx + 2 : (endLineText != null ? endLineText.length() : 0)) : (closeIdx >= 0 ? closeIdx + 1 : (endLineText != null ? endLineText.length() : 0));

                if (x <= xStart) {
                    line = range.startLine;
                    clamped = Math.max(0, range.openCharIndex);
                } else if (x <= closeStart + closeWidth || endLineText == null) {
                    line = range.endLine;
                    clamped = (closeIdx >= 0) ? (closeIdx + 1) : 0;
                } else {
                    float xSuffix = Math.max(0f, x - (closeStart + closeWidth));
                    int idx = editor.wordWrap.getCharIndexForXInRange(endLineText, range.endLine, Math.max(0, Math.min(suffixStart, endLineText.length())), endLineText.length(), xSuffix);
                    line = range.endLine;
                    clamped = Math.max(suffixStart, Math.min(idx, endLineText.length()));
                }
                ln = editor.windowRender.getLineTextForRender(line);
                clamped = Math.max(0, Math.min(clamped, (ln == null) ? 0 : ln.length()));
            }
        }
        
        editor.selectionHandles.lastDragAtLineStart = (clamped == 0);
        editor.selectionHandles.lastDragAtLineEnd = (ln != null && clamped == ln.length());

        if (editor.selectionHandles.draggingHandle == 1) {
            if (editor.textRender.isRtl) { editor.selection.selEndLine = line; editor.selection.selEndChar = clamped; }
            else { editor.selection.selStartLine = line; editor.selection.selStartChar = clamped; }
        } else if (editor.selectionHandles.draggingHandle == 2) {
            if (editor.textRender.isRtl) { editor.selection.selStartLine = line; editor.selection.selStartChar = clamped; }
            else { editor.selection.selEndLine = line; editor.selection.selEndChar = clamped; }
        } else if (editor.selectionHandles.draggingHandle == 3) {
            editor.cursor.setCursorPosition(line, clamped);
            editor.scroll.keepCursorVisibleHorizontally();
        }
    }

    private int handleCodeFoldSelection(int line, String ln, float moveX, int clamped) {
        CodeFold.FoldRange range = editor.codeFold.getFoldRangeAtStart(line);
        if (range != null && range.collapsed) {
            float[] bounds = new float[2];
            if (editor.codeFold.getFoldPlaceholderBounds(line, ln, bounds)) {
                float x = editor.scroll.viewToTextX(moveX);
                if (editor.wordWrap.isWordWrapEnabled) {
                    int[] starts = editor.wordWrap.getWrapStartsForLine(line, ln);
                    int seg = editor.wordWrap.getWrapSegmentIndexForChar(starts, Math.max(0, Math.min(clamped, ln.length())));
                    int segStart = editor.wordWrap.getWrapSegmentStart(starts, seg);
                    x = x + editor.textRender.measureTextWithVisualSpaces(ln, 0, segStart, editor.textRender.paint);
                }
                float xStart = bounds[0];
                float placeholderWidth = Math.max(0f, editor.textRender.paint.measureText(CodeFold.FOLD_PLACEHOLDER_TEXT));
                float closeStart = xStart + placeholderWidth;
                String endLineText = editor.windowRender.getLineTextForRender(range.endLine);
                float closeWidth = editor.textRender.paint.measureText(String.valueOf(range.closeChar));
                int closeIdx = editor.codeFold.resolveCloseCharIndex(range, endLineText);
                int suffixStart = range.isBlockComment ? (closeIdx >= 0 ? closeIdx + 2 : -1) : (closeIdx >= 0 ? closeIdx + 1 : -1);
                
                if (x <= xStart) return Math.max(0, range.openCharIndex);
                else if (x <= closeStart + closeWidth || suffixStart < 0 || endLineText == null) return (closeIdx >= 0) ? (closeIdx + 1) : 0;
                else {
                    float xSuffix = Math.max(0f, x - (closeStart + closeWidth));
                    int idx = editor.wordWrap.getCharIndexForXInRange(endLineText, range.endLine, suffixStart, endLineText.length(), xSuffix);
                    return Math.max(suffixStart, Math.min(idx, endLineText.length()));
                }
            }
        }
        return clamped;
    }
}
