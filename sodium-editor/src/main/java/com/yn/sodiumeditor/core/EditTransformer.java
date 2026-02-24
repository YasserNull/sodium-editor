package com.yn.sodiumeditor.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.ArrayDeque;

/**
 * Transforms EditOp objects to and from JSON format for persistence.
 */
public final class EditTransformer {

    private EditTransformer() {
        // Utility class, prevent instantiation
    }

    /**
     * Converts a single EditOp to JSON.
     */
    public static JSONObject editOpToJson(EditOp op) throws Exception {
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
        return obj;
    }

    /**
     * Parses a JSON object into an EditOp.
     */
    public static EditOp editOpFromJson(JSONObject obj) throws Exception {
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
        return op;
    }

    /**
     * Converts an ArrayDeque of EditOp to JSON.
     */
    public static JSONArray editOpDequeToJson(ArrayDeque<EditOp> deque) throws Exception {
        JSONArray arr = new JSONArray();
        for (EditOp op : deque) {
            arr.put(editOpToJson(op));
        }
        return arr;
    }

    /**
     * Parses a JSON array into an ArrayList of EditOp.
     */
    public static ArrayList<EditOp> editOpListFromJson(JSONArray arr) throws Exception {
        ArrayList<EditOp> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            Object item = arr.opt(i);
            if (item instanceof JSONObject) {
                list.add(editOpFromJson((JSONObject) item));
            }
        }
        return list;
    }
}
