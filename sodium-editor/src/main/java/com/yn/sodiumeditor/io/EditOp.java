package com.yn.sodiumeditor.io;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * Data structures and JSON serialization for edit operations.
 */
public class EditOp {
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
    public File removedTextBackupFile;
    public boolean entireFileDelete;
    public boolean pendingUndoOfSavedOp;
    public EditOp originalOp;

    /**
     * Represents a cursor target position
     */
    public static final class CursorTarget {
        public final int line;
        public final int ch;

        public CursorTarget(int line, int ch) {
            this.line = line;
            this.ch = ch;
        }
    }

    /**
     * Represents a byte range in a file
     */
    public static final class RangeBytes {
        public final long startByte, endByte;

        public RangeBytes(long s, long e) {
            startByte = s;
            endByte = e;
        }
    }

    public static JSONObject toJson(EditOp op) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("startLine", op.startLine);
        obj.put("startChar", op.startChar);
        obj.put("endLine", op.endLine);
        obj.put("endChar", op.endChar);
        obj.put("insertedEndLine", op.insertedEndLine);
        obj.put("insertedEndChar", op.insertedEndChar);
        obj.put("removedText", op.removedText == null ? JSONObject.NULL : op.removedText);
        obj.put("insertedText", op.insertedText == null ? JSONObject.NULL : op.insertedText);
        obj.put("cursorLineBefore", op.cursorLineBefore);
        obj.put("cursorCharBefore", op.cursorCharBefore);
        obj.put("cursorLineAfter", op.cursorLineAfter);
        obj.put("cursorCharAfter", op.cursorCharAfter);
        obj.put("timestamp", op.timestamp);
        obj.put("removedTextBackupFile", op.removedTextBackupFile == null ? JSONObject.NULL : op.removedTextBackupFile.getAbsolutePath());
        obj.put("entireFileDelete", op.entireFileDelete);
        obj.put("pendingUndoOfSavedOp", op.pendingUndoOfSavedOp);
        return obj;
    }

    public static EditOp fromJson(JSONObject obj) throws Exception {
        EditOp op = new EditOp();
        op.startLine = obj.optInt("startLine", 0);
        op.startChar = obj.optInt("startChar", 0);
        op.endLine = obj.optInt("endLine", 0);
        op.endChar = obj.optInt("endChar", 0);
        op.insertedEndLine = obj.optInt("insertedEndLine", 0);
        op.insertedEndChar = obj.optInt("insertedEndChar", 0);
        op.removedText = obj.isNull("removedText") ? null : obj.optString("removedText", "");
        op.insertedText = obj.isNull("insertedText") ? null : obj.optString("insertedText", "");
        op.cursorLineBefore = obj.optInt("cursorLineBefore", 0);
        op.cursorCharBefore = obj.optInt("cursorCharBefore", 0);
        op.cursorLineAfter = obj.optInt("cursorLineAfter", 0);
        op.cursorCharAfter = obj.optInt("cursorCharAfter", 0);
        op.timestamp = obj.optLong("timestamp", 0L);
        op.removedTextBackupFile = obj.isNull("removedTextBackupFile") ? null : new File(obj.optString("removedTextBackupFile", ""));
        op.entireFileDelete = obj.optBoolean("entireFileDelete", false);
        op.pendingUndoOfSavedOp = obj.optBoolean("pendingUndoOfSavedOp", false);
        return op;
    }

    public static JSONArray dequeToJson(ArrayDeque<EditOp> deque) throws Exception {
        JSONArray arr = new JSONArray();
        for (EditOp op : deque) {
            arr.put(toJson(op));
        }
        return arr;
    }

    public static ArrayList<EditOp> listFromJson(JSONArray arr) throws Exception {
        ArrayList<EditOp> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            Object item = arr.opt(i);
            if (item instanceof JSONObject) {
                list.add(fromJson((JSONObject) item));
            }
        }
        return list;
    }
}
