package com.yn.sodiumeditor.input;

import android.graphics.RectF;
import android.os.Handler;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.state.HandleState;

/**
 * Input handler for handle drag operations.
 * Handles hit testing, drag updates, and auto-scroll triggering.
 */
public final class HandleDragHandler {

    private final SodiumEditor view;
    private final HandleState handleState;
    private final Handler mainHandler;

    public HandleDragHandler(SodiumEditor view, HandleState handleState, Handler mainHandler) {
        this.view = view;
        this.handleState = handleState;
        this.mainHandler = mainHandler;
    }

    public int hitTestHandle(float gx, float gy, boolean hasSelection, boolean focused) {
        RectF leftRect = handleState.getLeftHandleRect();
        RectF rightRect = handleState.getRightHandleRect();
        RectF cursorRect = handleState.getCursorHandleRect();

        if (hasSelection && leftRect.contains(gx, gy)) return HandleState.HANDLE_LEFT;
        if (hasSelection && rightRect.contains(gx, gy)) return HandleState.HANDLE_RIGHT;
        if (focused && !hasSelection && cursorRect.contains(gx, gy)) return HandleState.HANDLE_CURSOR;
        return HandleState.HANDLE_NONE;
    }

    public void handleDrag(float touchX, float touchY) {
        updateHandlePosition(touchX, touchY);
        handleState.setLastDragTouchX(touchX);
        handleState.setLastDragTouchY(touchY);

        int handle = handleState.getDraggingHandle();
        if (handle == HandleState.HANDLE_LEFT || handle == HandleState.HANDLE_RIGHT) {
            view.popupTouchHandler.showPopupAtSelection();
        }

        float scrollMargin = view.lineHeight * 2f;
        float scrollSpeed = Math.max(4f, view.lineHeight * 0.35f);
        view.scrollManager.autoScrollX = 0;
        view.scrollManager.autoScrollY = 0;

        if (touchY < scrollMargin) view.scrollManager.autoScrollY = -scrollSpeed;
        else if (touchY > (view.getHeight() - view.keyboardHeight) - scrollMargin) {
            view.scrollManager.autoScrollY = scrollSpeed;
        }
        if (touchX < scrollMargin) view.scrollManager.autoScrollX = -scrollSpeed;
        else if (touchX > view.getWidth() - scrollMargin) view.scrollManager.autoScrollX = scrollSpeed;
        if (view.isRtl && !view.wrapWordState.isWordWrapEnabled) {
            view.scrollManager.autoScrollX = -view.scrollManager.autoScrollX;
        }

        if (view.scrollManager.autoScrollX > 0 && handleState.isLastDragAtLineEnd()) {
            view.scrollManager.autoScrollX = 0;
        }
        if (view.scrollManager.autoScrollX < 0 && handleState.isLastDragAtLineStart()) {
            view.scrollManager.autoScrollX = 0;
        }

        if (view.scrollManager.autoScrollX != 0 || view.scrollManager.autoScrollY != 0) {
            mainHandler.post(autoScrollRunnable);
        } else {
            mainHandler.removeCallbacks(autoScrollRunnable);
        }

        view.invalidate();
    }

    public void onTouchMove(float touchX, float touchY) {
        handleState.setLastDragTouchX(touchX);
        handleState.setLastDragTouchY(touchY);
    }

    private void updateHandlePosition(float touchX, float touchY) {
        if (view.selectionState.isSelectAllActive() || view.selectionState.isEntireFileSelected()) {
            view.selectionState.setSelectAllState(false, false);
            view.popupTouchHandler.showPopupAtSelection();
        }

        com.yn.sodiumeditor.core.EditorTextInserter.CursorTarget target = view.getCursorTargetForPosition(touchX, touchY, null);
        int line = target.line;

        if (view.isEof) {
            int lastValidLine = view.windowStartLine + view.linesWindow.size() - 1;
            if (line > lastValidLine) line = lastValidLine;
        }

        view.scrollManager.ensureLineInWindow(line, true);
        String ln = view.viewRender.textRender.getLineTextForRender(line);
        int clamped = Math.max(0, Math.min(target.ch, ln.length()));
        handleState.setLastDragAtLineStart(clamped == 0);
        handleState.setLastDragAtLineEnd(clamped == ln.length());

        int handle = handleState.getDraggingHandle();
        if (handle == HandleState.HANDLE_LEFT) {
            if (view.isRtl) {
                view.selectionState.selEndLine = line;
                view.selectionState.selEndChar = clamped;
            } else {
                view.selectionState.selStartLine = line;
                view.selectionState.selStartChar = clamped;
            }
        } else if (handle == HandleState.HANDLE_RIGHT) {
            if (view.isRtl) {
                view.selectionState.selStartLine = line;
                view.selectionState.selStartChar = clamped;
            } else {
                view.selectionState.selEndLine = line;
                view.selectionState.selEndChar = clamped;
            }
        } else if (handle == HandleState.HANDLE_CURSOR) {
            view.cursorState.setCursorPosition(line, clamped);
            view.scrollManager.keepCursorVisibleHorizontally();
        }
    }

    private final Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!handleState.isDragging()) return;
            if (view.scrollManager.autoScrollX != 0 || view.scrollManager.autoScrollY != 0) {
                view.scrollManager.scrollX += view.scrollManager.autoScrollX;
                float nextY = view.scrollManager.scrollY + view.scrollManager.autoScrollY;
                if (!view.isIndexReady && !view.isEof && view.isWindowLoading) {
                    float effectiveHeight =
                            (view.keyboardHeight > 0) ? view.getHeight() - view.keyboardHeight : view.getHeight();
                    float winTop = view.windowStartLine * view.lineHeight;
                    float winBottom = (view.windowStartLine + view.linesWindow.size()) * view.lineHeight;
                    float maxY = Math.max(0f, winBottom - effectiveHeight);
                    if (view.scrollManager.autoScrollY > 0 && nextY > maxY) nextY = maxY;
                    if (view.scrollManager.autoScrollY < 0 && nextY < winTop) nextY = winTop;
                }
                view.scrollManager.scrollY = nextY;
                view.scrollManager.clampScrollX();
                view.scrollManager.clampScrollY();
                updateHandlePosition(handleState.getLastDragTouchX(), handleState.getLastDragTouchY());
                int handle = handleState.getDraggingHandle();
                if (handle == HandleState.HANDLE_LEFT || handle == HandleState.HANDLE_RIGHT) {
                    view.popupTouchHandler.showPopupAtSelection();
                }
                view.viewRender.checkAndLoadWindow();
                view.invalidate();
                mainHandler.postDelayed(this, 16);
            }
        }
    };

    public void stopDragging() {
        handleState.stopDragging();
        view.scrollManager.autoScrollX = 0;
        view.scrollManager.autoScrollY = 0;
        mainHandler.removeCallbacks(autoScrollRunnable);
    }

    public void stopAutoScroll() {
        view.scrollManager.autoScrollX = 0;
        view.scrollManager.autoScrollY = 0;
        mainHandler.removeCallbacks(autoScrollRunnable);
    }
}
