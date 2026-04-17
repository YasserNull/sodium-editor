package com.yn.sodiumeditor.core.view.events;

import com.yn.sodiumeditor.SodiumEditor;

public class onMeasure {
    private final SodiumEditor editor;

    public onMeasure(SodiumEditor editor) {
        this.editor = editor;
    }

    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float oldGutterWidth = editor.lineNumber.lineNumbersGutterWidth;
        if (editor.lineNumber.showLineNumbers) {
            int maxLines = 0;
            if (editor.fileIO.isIndexReady) {
                maxLines = editor.fileIO.lineOffsets.length;
            } else if (editor.fileIO.isEof) {
                maxLines = editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size();
            } else {
                maxLines = 999999; // Wider fallback for width calculation until index is ready
            }
            String maxLineNum = String.valueOf(maxLines);
            float baseWidth = editor.lineNumber.lineNumbersPaint.measureText(maxLineNum) + (editor.lineNumber.GUTTER_TEXT_PADDING * 2);
            if (editor.codeFold.isCodeFoldingEnabled) {
                editor.codeFold.animation.foldMarkerGutterWidth =
                        editor.codeFold.animation.foldMarkerPaint.measureText("v") + editor.codeFold.animation.foldMarkerSpacing + editor.codeFold.animation.foldMarkerEdgePadding;
            } else {
                editor.codeFold.animation.foldMarkerGutterWidth = 0f;
            }
            editor.lineNumber.lineNumbersGutterWidth = baseWidth + editor.codeFold.animation.foldMarkerGutterWidth + editor.lineNumber.gutterSeparatorWidth;
        } else {
            editor.lineNumber.lineNumbersGutterWidth = 0f;
        }

        if (editor.wordWrap.isWordWrapEnabled && Math.abs(editor.lineNumber.lineNumbersGutterWidth - oldGutterWidth) > 0.1f) {
            editor.wordWrap.invalidateWrapMetrics(true);
            editor.wordWrap.requestWrapPrefixRebuild();
        }
        if (Math.abs(editor.lineNumber.lineNumbersGutterWidth - oldGutterWidth) > 0.1f) {
            editor.lineNumber.invalidateLineNumberCache();
        }
    }
}
