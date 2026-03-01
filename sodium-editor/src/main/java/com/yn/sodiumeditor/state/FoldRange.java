package com.yn.sodiumeditor.state;

/**
 * Represents a fold range in the editor.
 * Stores information about a collapsible code block.
 */
public class FoldRange {
    public final int startLine;
    public final int endLine;
    public final int openCharIndex;
    public final char openChar;
    public final char closeChar;
    public final boolean isBlockComment;
    public final boolean isIndentFold;
    public boolean collapsed;

    public FoldRange(int startLine, int endLine, int openCharIndex, char openChar, char closeChar, boolean isBlockComment, boolean isIndentFold) {
        this.startLine = startLine;
        this.endLine = endLine;
        this.openCharIndex = openCharIndex;
        this.openChar = openChar;
        this.closeChar = closeChar;
        this.isBlockComment = isBlockComment;
        this.isIndentFold = isIndentFold;
        this.collapsed = false;
    }
}
