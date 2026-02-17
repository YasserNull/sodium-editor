package com.yn.sodiumeditor.view;

import android.util.Log;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

final class UndoRedo {
  private static final int UNDO_STACK_LIMIT = 200;
  private static final int UNDO_TEXT_LIMIT = 1_000_000;

  private final SodiumEditorView view;
  private final AtomicInteger editVersion = new AtomicInteger(0);
  private final ArrayDeque<EditOp> undoStack = new ArrayDeque<>();
  private final ArrayDeque<EditOp> redoStack = new ArrayDeque<>();
  private final ArrayDeque<EditOp> pendingEdits = new ArrayDeque<>();
  private final ArrayDeque<EditOp> pendingRedo = new ArrayDeque<>();
  private boolean isApplyingUndoRedo = false;
  private volatile long lastEditTimestamp = 0L;
  private int lineCountDelta = 0;
  @Nullable private EditOp composingPendingOp = null;

  UndoRedo(SodiumEditorView view) {
    this.view = view;
  }

  static final class EditOp {
    int startLine;
    int startChar;
    int endLine;
    int endChar;
    int insertedEndLine;
    int insertedEndChar;
    String removedText;
    String insertedText;
    int cursorLineBefore;
    int cursorCharBefore;
    int cursorLineAfter;
    int cursorCharAfter;
    long timestamp;
  }

  int getEditVersion() {
    return editVersion.get();
  }

  int getUndoTextLimit() {
    return UNDO_TEXT_LIMIT;
  }

  int incrementEditVersion() {
    return editVersion.incrementAndGet();
  }

  void resetLineCountDelta() {
    lineCountDelta = 0;
  }

  int getLineCountDelta() {
    return lineCountDelta;
  }

  void setLineCountDelta(int value) {
    lineCountDelta = value;
  }

  void addLineCountDelta(int delta) {
    lineCountDelta += delta;
  }

  boolean isApplyingUndoRedo() {
    return isApplyingUndoRedo;
  }

  long getLastEditTimestamp() {
    return lastEditTimestamp;
  }

  boolean hasPendingEdits() {
    return !pendingEdits.isEmpty();
  }

  boolean canUndo() {
    return !undoStack.isEmpty();
  }

  boolean canRedo() {
    return !redoStack.isEmpty();
  }

  int getUndoStackSize() {
    return undoStack.size();
  }

  int getPendingEditsCount() {
    return pendingEdits.size();
  }

  void clearUndoRedoHistory() {
    undoStack.clear();
    redoStack.clear();
    pendingEdits.clear();
    pendingRedo.clear();
  }

  void clearComposingPendingOp() {
    composingPendingOp = null;
  }

  String exportEditCacheJson() {
    try {
      JSONObject root = new JSONObject();
      root.put("undo", editOpDequeToJson(undoStack));
      root.put("redo", editOpDequeToJson(redoStack));
      root.put("pending", editOpDequeToJson(pendingEdits));
      root.put("pendingRedo", editOpDequeToJson(pendingRedo));
      root.put("dirty", !pendingEdits.isEmpty());
      root.put("cursorLine", view.cursorManager.getLine());
      root.put("cursorChar", view.cursorManager.getChar());
      root.put("selStartLine", view.selectionManager.selStartLine);
      root.put("selStartChar", view.selectionManager.selStartChar);
      root.put("selEndLine", view.selectionManager.selEndLine);
      root.put("selEndChar", view.selectionManager.selEndChar);
      root.put("hasSelection", view.selectionManager.hasSelection());
      return root.toString();
    } catch (Exception e) {
      return "";
    }
  }

  boolean importEditCacheJson(String json, boolean applyPendingEdits) {
    if (json == null || json.isEmpty()) return false;
    try {
      JSONObject root = new JSONObject(json);
      ArrayList<EditOp> undo = editOpListFromJson(root.optJSONArray("undo"));
      ArrayList<EditOp> redo = editOpListFromJson(root.optJSONArray("redo"));
      ArrayList<EditOp> pending = editOpListFromJson(root.optJSONArray("pending"));
      ArrayList<EditOp> pendingRedoList = editOpListFromJson(root.optJSONArray("pendingRedo"));

      if (applyPendingEdits) {
        isApplyingUndoRedo = true;
        for (EditOp op : pending) {
          applyEditForUndoRedo(
              op.startLine,
              op.startChar,
              op.endLine,
              op.endChar,
              op.insertedText == null ? "" : op.insertedText,
              op.cursorLineAfter,
              op.cursorCharAfter);
        }
        isApplyingUndoRedo = false;
      }

      undoStack.clear();
      redoStack.clear();
      pendingEdits.clear();
      pendingRedo.clear();
      for (EditOp op : undo) undoStack.addLast(op);
      for (EditOp op : redo) redoStack.addLast(op);
      for (EditOp op : pending) pendingEdits.addLast(op);
      for (EditOp op : pendingRedoList) pendingRedo.addLast(op);

      if (root.has("cursorLine") && root.has("cursorChar")) {
        int cLine = root.optInt("cursorLine", view.cursorManager.getLine());
        int cChar = root.optInt("cursorChar", view.cursorManager.getChar());
        if (root.optBoolean("hasSelection", false)) {
          int sL = root.optInt("selStartLine", cLine);
          int sC = root.optInt("selStartChar", cChar);
          int eL = root.optInt("selEndLine", cLine);
          int eC = root.optInt("selEndChar", cChar);
          view.restoreSelection(sL, sC, eL, eC, cLine, cChar);
        } else {
          view.cursorManager.setPosition(cLine, cChar);
        }
      }

      editVersion.incrementAndGet();
      view.lineNumberManager.invalidateCache();
      view.invalidate();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  void applyPendingEditsToFileAsync(@Nullable Runnable onComplete) {
    if (view.sourceFile == null) {
      if (onComplete != null) view.post(onComplete);
      return;
    }
    if (view.cursorManager.getHasComposing()) {
      Log.d("SodiumEditorViewSave", "commitComposing before save");
      view.cursorManager.commitComposing(true);
    }
    final ArrayList<EditOp> ops = new ArrayList<>();
    synchronized (pendingEdits) {
      Log.d("SodiumEditorViewSave", "pendingEdits.size=" + pendingEdits.size());
      ops.addAll(pendingEdits);
      pendingEdits.clear();
      pendingRedo.clear();
    }
    if (ops.isEmpty()) {
      if (onComplete != null) view.post(onComplete);
      return;
    }
    Log.d("SodiumEditorViewSave", "Saving pending ops=" + ops.size());
    view.getIoHandlerForUndo()
        .post(
            () -> {
              boolean ok = true;
              for (EditOp op : ops) {
                Log.d(
                    "SodiumEditorViewSave",
                    "Op s="
                        + op.startLine
                        + ":"
                        + op.startChar
                        + " e="
                        + op.endLine
                        + ":"
                        + op.endChar
                        + " insertLen="
                        + (op.insertedText == null ? 0 : op.insertedText.length())
                        + " removeLen="
                        + (op.removedText == null ? 0 : op.removedText.length()));
                if (!rewriteReplaceRangeBlocking(
                    view.sourceFile,
                    op.startLine,
                    op.startChar,
                    op.endLine,
                    op.endChar,
                    op.insertedText)) {
                  ok = false;
                  break;
                }
              }
              final boolean success = ok;
              view.post(
                  () -> {
                    if (!success) {
                      Log.d("SodiumEditorViewSave", "Save failed");
                      pendingEdits.addAll(ops);
                    } else {
                      Log.d("SodiumEditorViewSave", "Save success");
                      synchronized (view.modifiedLines) {
                        view.modifiedLines.clear();
                      }
                      lineCountDelta = 0;
                      view.lineNumberManager.invalidateCache();
                      view.requestLayout();
                      view.invalidate();
                    }
                    if (onComplete != null) onComplete.run();
                  });
            });
  }

  void recordEdit(EditOp op) {
    if (isApplyingUndoRedo) return;
    if (op == null) return;
    boolean tooLarge =
        (op.removedText != null && op.removedText.length() > UNDO_TEXT_LIMIT)
            || (op.insertedText != null && op.insertedText.length() > UNDO_TEXT_LIMIT);
    if (tooLarge) {
      recordEditNoUndo(op);
      return;
    }

    boolean insertOnly =
        (op.removedText == null || op.removedText.isEmpty())
            && op.insertedText != null
            && !op.insertedText.isEmpty();

    if (insertOnly) {
      EditOp lastPending = pendingEdits.peekLast();
      if (lastPending != null
          && (lastPending.removedText == null || lastPending.removedText.isEmpty())
          && lastPending.insertedText != null
          && !lastPending.insertedText.isEmpty()
          && lastPending.insertedEndLine == op.startLine
          && lastPending.insertedEndChar == op.startChar) {
        Log.d(
            "SodiumEditorViewEdit",
            "merge insert start="
                + op.startLine
                + ":"
                + op.startChar
                + " addLen="
                + op.insertedText.length());
        String beforeText = lastPending.insertedText;
        lastPending.insertedText = lastPending.insertedText + op.insertedText;
        SodiumEditorView.CursorTarget newEnd =
            view.computeCursorAfterInsertForUndo(
                lastPending.startLine, lastPending.startChar, lastPending.insertedText);
        lastPending.insertedEndLine = newEnd.line;
        lastPending.insertedEndChar = newEnd.ch;
        lastPending.cursorLineAfter = op.cursorLineAfter;
        lastPending.cursorCharAfter = op.cursorCharAfter;
        lastPending.timestamp = op.timestamp;

        EditOp lastUndo = undoStack.peekLast();
        if (lastUndo != null
            && lastUndo.startLine == lastPending.startLine
            && lastUndo.startChar == lastPending.startChar
            && lastUndo.endLine == lastPending.endLine
            && lastUndo.endChar == lastPending.endChar
            && lastUndo.insertedText != null
            && lastUndo.insertedText.equals(beforeText)) {
          lastUndo.insertedText = lastPending.insertedText;
          lastUndo.insertedEndLine = lastPending.insertedEndLine;
          lastUndo.insertedEndChar = lastPending.insertedEndChar;
          lastUndo.cursorLineAfter = lastPending.cursorLineAfter;
          lastUndo.cursorCharAfter = lastPending.cursorCharAfter;
          lastUndo.timestamp = lastPending.timestamp;
        }

        redoStack.clear();
        pendingRedo.clear();
        lastEditTimestamp = op.timestamp;
        return;
      }
    }

    undoStack.addLast(op);
    while (undoStack.size() > UNDO_STACK_LIMIT) {
      undoStack.removeFirst();
    }
    redoStack.clear();
    pendingEdits.addLast(op);
    pendingRedo.clear();
    lastEditTimestamp = op.timestamp;
    Log.d(
        "SodiumEditorViewEdit",
        "record op s="
            + op.startLine
            + ":"
            + op.startChar
            + " e="
            + op.endLine
            + ":"
            + op.endChar
            + " insertLen="
            + (op.insertedText == null ? 0 : op.insertedText.length())
            + " removeLen="
            + (op.removedText == null ? 0 : op.removedText.length())
            + " pending="
            + pendingEdits.size());
  }

  void recordEditNoUndo(EditOp op) {
    if (isApplyingUndoRedo) return;
    if (op == null) return;
    pendingEdits.addLast(op);
    pendingRedo.clear();
    redoStack.clear();
    lastEditTimestamp = op.timestamp;
    Log.d(
        "SodiumEditorViewEdit",
        "record save-only op s="
            + op.startLine
            + ":"
            + op.startChar
            + " e="
            + op.endLine
            + ":"
            + op.endChar
            + " insertLen="
            + (op.insertedText == null ? 0 : op.insertedText.length())
            + " removeLen="
            + (op.removedText == null ? 0 : op.removedText.length())
            + " pending="
            + pendingEdits.size());
  }

  void recordReplaceSelectionEdit(
      int sL,
      int sC,
      int eL,
      int eC,
      @Nullable String removedText,
      @Nullable String insertText,
      int beforeLine,
      int beforeChar) {
    String insert = (insertText == null) ? "" : insertText;
    if (removedText == null) {
      EditOp op = new EditOp();
      op.startLine = sL;
      op.startChar = sC;
      op.endLine = eL;
      op.endChar = eC;
      op.removedText = null;
      op.insertedText = insert;
      SodiumEditorView.CursorTarget insertedEnd =
          view.computeCursorAfterInsertForUndo(sL, sC, insert);
      op.insertedEndLine = insertedEnd.line;
      op.insertedEndChar = insertedEnd.ch;
      op.cursorLineBefore = beforeLine;
      op.cursorCharBefore = beforeChar;
      op.cursorLineAfter = view.cursorManager.getLine();
      op.cursorCharAfter = view.cursorManager.getChar();
      op.timestamp = System.currentTimeMillis();
      recordEditNoUndo(op);
      return;
    }
    if (removedText.length() > UNDO_TEXT_LIMIT || insert.length() > UNDO_TEXT_LIMIT) {
      EditOp op = new EditOp();
      op.startLine = sL;
      op.startChar = sC;
      op.endLine = eL;
      op.endChar = eC;
      op.removedText = null;
      op.insertedText = insert;
      SodiumEditorView.CursorTarget insertedEnd =
          view.computeCursorAfterInsertForUndo(sL, sC, insert);
      op.insertedEndLine = insertedEnd.line;
      op.insertedEndChar = insertedEnd.ch;
      op.cursorLineBefore = beforeLine;
      op.cursorCharBefore = beforeChar;
      op.cursorLineAfter = view.cursorManager.getLine();
      op.cursorCharAfter = view.cursorManager.getChar();
      op.timestamp = System.currentTimeMillis();
      recordEditNoUndo(op);
      return;
    }
    EditOp op = new EditOp();
    op.startLine = sL;
    op.startChar = sC;
    op.endLine = eL;
    op.endChar = eC;
    op.removedText = removedText;
    op.insertedText = insert;
    SodiumEditorView.CursorTarget insertedEnd = view.computeCursorAfterInsertForUndo(sL, sC, insert);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = view.cursorManager.getLine();
    op.cursorCharAfter = view.cursorManager.getChar();
    op.timestamp = System.currentTimeMillis();
    recordEdit(op);
  }

  void updateComposingPendingOp(@Nullable String text, int beforeLine, int beforeChar) {
    if (!view.cursorManager.getHasComposing()) return;
    if (text == null) text = "";
    if (text.length() > UNDO_TEXT_LIMIT) return;

    int startLine = view.cursorManager.getComposingStartActive() ? view.cursorManager.getComposingStartLine() : view.cursorManager.getComposingLine();
    int startChar = view.cursorManager.getComposingStartActive() ? view.cursorManager.getComposingStartChar() : view.cursorManager.getComposingOffset();

    if (composingPendingOp == null) {
      if (text.isEmpty()) return;
      EditOp op = new EditOp();
      op.startLine = startLine;
      op.startChar = startChar;
      op.endLine = startLine;
      op.endChar = startChar;
      op.removedText = "";
      op.insertedText = text;
      SodiumEditorView.CursorTarget insertedEnd =
          view.computeCursorAfterInsertForUndo(startLine, startChar, text);
      op.insertedEndLine = insertedEnd.line;
      op.insertedEndChar = insertedEnd.ch;
      op.cursorLineBefore = beforeLine;
      op.cursorCharBefore = beforeChar;
      op.cursorLineAfter = view.cursorManager.getLine();
      op.cursorCharAfter = view.cursorManager.getChar();
      op.timestamp = System.currentTimeMillis();
      lineCountDelta += view.countNewlinesForUndo(text);
      composingPendingOp = op;
      undoStack.addLast(op);
      while (undoStack.size() > UNDO_STACK_LIMIT) {
        undoStack.removeFirst();
      }
      redoStack.clear();
      pendingEdits.addLast(op);
      pendingRedo.clear();
      lastEditTimestamp = op.timestamp;
      Log.d(
          "SodiumEditorViewCompose",
          "start composing op s=" + startLine + ":" + startChar + " textLen=" + text.length());
      return;
    }

    String prev = composingPendingOp.insertedText == null ? "" : composingPendingOp.insertedText;
    int prevNewlines = view.countNewlinesForUndo(prev);
    int newNewlines = view.countNewlinesForUndo(text);
    lineCountDelta += (newNewlines - prevNewlines);

    composingPendingOp.insertedText = text;
    SodiumEditorView.CursorTarget insertedEnd =
        view.computeCursorAfterInsertForUndo(startLine, startChar, text);
    composingPendingOp.insertedEndLine = insertedEnd.line;
    composingPendingOp.insertedEndChar = insertedEnd.ch;
    composingPendingOp.cursorLineAfter = view.cursorManager.getLine();
    composingPendingOp.cursorCharAfter = view.cursorManager.getChar();
    composingPendingOp.timestamp = System.currentTimeMillis();
    lastEditTimestamp = composingPendingOp.timestamp;

    Log.d("SodiumEditorViewCompose", "update composing op textLen=" + text.length());

    if (text.isEmpty()) {
      pendingEdits.remove(composingPendingOp);
      undoStack.remove(composingPendingOp);
      composingPendingOp = null;
      Log.d("SodiumEditorViewCompose", "remove composing op (empty)");
    }
  }

  void undo() {
    if (undoStack.isEmpty()) return;
    EditOp op = undoStack.removeLast();
    redoStack.addLast(op);
    if (!pendingEdits.isEmpty()) {
      pendingEdits.removeLast();
      pendingRedo.addLast(op);
    }
    isApplyingUndoRedo = true;
    applyEditForUndoRedo(
        op.startLine,
        op.startChar,
        op.insertedEndLine,
        op.insertedEndChar,
        op.removedText == null ? "" : op.removedText,
        op.cursorLineBefore,
        op.cursorCharBefore);
    isApplyingUndoRedo = false;
  }

  void redo() {
    if (redoStack.isEmpty()) return;
    EditOp op = redoStack.removeLast();
    undoStack.addLast(op);
    if (!pendingRedo.isEmpty()) {
      pendingRedo.removeLast();
      pendingEdits.addLast(op);
    }
    isApplyingUndoRedo = true;
    applyEditForUndoRedo(
        op.startLine,
        op.startChar,
        op.endLine,
        op.endChar,
        op.insertedText == null ? "" : op.insertedText,
        op.cursorLineAfter,
        op.cursorCharAfter);
    isApplyingUndoRedo = false;
  }

  void applyEditForUndoRedo(
      int sL, int sC, int eL, int eC, String text, int cursorLine, int cursorChar) {
    view.setSelectionInternal(sL, sC, eL, eC);
    view.replaceSelectionWithText(text);
    view.cursorManager.setPosition(cursorLine, cursorChar);
    if (view.wordWrapManager.isWordWrapEnabled) {
      view.wordWrapManager.invalidateWrapMetrics(view, true);
      view.wordWrapManager.requestWrapPrefixRebuild(view);
    }
    view.lineNumberManager.invalidateCache();
    view.invalidate();
  }

  private static JSONObject editOpToJson(EditOp op) throws Exception {
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

  private static EditOp editOpFromJson(JSONObject obj) throws Exception {
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

  private static JSONArray editOpDequeToJson(ArrayDeque<EditOp> deque) throws Exception {
    JSONArray arr = new JSONArray();
    for (EditOp op : deque) {
      arr.put(editOpToJson(op));
    }
    return arr;
  }

  private static ArrayList<EditOp> editOpListFromJson(JSONArray arr) throws Exception {
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

  private boolean rewriteReplaceRangeBlocking(
      File inFile, int sL, int sC, int eL, int eC, @Nullable String insertText) {
    if (inFile == null || !inFile.exists()) return false;
    try {
      FileManager.RangeBytes range = view.fileManager.computeByteRangeFastOrScanForUndo(inFile, sL, sC, eL, eC);
      if (range == null) return false;
      byte[] insertBytes =
          (insertText == null) ? new byte[0] : insertText.getBytes(StandardCharsets.UTF_8);
      final int BUF_SIZE = 1024 * 1024;

      try (RandomAccessFile raf = new RandomAccessFile(inFile, "rw");
          FileChannel ch = raf.getChannel()) {

        long fileLen = ch.size();
        long startByte = Math.max(0, Math.min(range.startByte, fileLen));
        long endByte = Math.max(0, Math.min(range.endByte, fileLen));
        if (endByte < startByte) {
          long t = startByte;
          startByte = endByte;
          endByte = t;
        }

        long removeLen = endByte - startByte;
        long diff = (long) insertBytes.length - removeLen;

        if (diff > 0) {
          raf.setLength(fileLen + diff);
          ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE);
          for (long pos = fileLen; pos > endByte; ) {
            long readPos = Math.max(endByte, pos - BUF_SIZE);
            int size = (int) (pos - readPos);
            buf.clear();
            buf.limit(size);
            ch.read(buf, readPos);
            buf.flip();
            ch.write(buf, readPos + diff);
            pos = readPos;
          }
        } else if (diff < 0) {
          ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE);
          for (long pos = endByte; pos < fileLen; ) {
            int size = (int) Math.min(BUF_SIZE, fileLen - pos);
            buf.clear();
            buf.limit(size);
            ch.read(buf, pos);
            buf.flip();
            ch.write(buf, pos + diff);
            pos += size;
          }
          raf.setLength(fileLen + diff);
        }

        if (insertBytes.length > 0) {
          ch.write(ByteBuffer.wrap(insertBytes), startByte);
        }
        ch.force(true);
      }

      view.onUndoRedoRewriteSuccess(inFile);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
