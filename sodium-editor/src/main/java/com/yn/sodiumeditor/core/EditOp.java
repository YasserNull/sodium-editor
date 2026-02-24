package com.yn.sodiumeditor.core;

/**
 * Represents a single edit operation in the editor.
 * Contains all information needed to undo/redo a text modification.
 */
public final class EditOp {
    public int startLine;
    public int startChar;
    public int endLine;
    public int endChar;
    public int insertedEndLine;
    public int insertedEndChar;
    public String removedText;
    public String insertedText;
    public int cursorLineBefore;
    public int cursorCharBefore;
    public int cursorLineAfter;
    public int cursorCharAfter;
    public long timestamp;
}
