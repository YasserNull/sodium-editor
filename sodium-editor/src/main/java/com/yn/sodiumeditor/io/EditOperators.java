package com.yn.sodiumeditor.io;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import com.yn.sodiumeditor.SodiumEditor;
/**
 * EditOperators handles undo/redo operations and edit tracking for SodiumEditor.
 * This includes:
 * - Edit operation recording
 * - Undo/redo stack management
 * - Edit operation serialization/deserialization
 * - File rewrite operations
 * - Byte range computation
 */
public class EditOperators {

  public static final int UNDO_STACK_LIMIT = 200;
  public static final int UNDO_TEXT_LIMIT = 1_000_000;
  public static final int LARGE_PASTE_LINES = 1500;
  public static final int LARGE_PASTE_CHARS = 200_000;
  
  private final SodiumEditor editor;

  // Undo/redo stacks
  public final ArrayDeque<EditOp> undoStack = new ArrayDeque<>();
  public final ArrayDeque<EditOp> redoStack = new ArrayDeque<>();
  public final ArrayDeque<EditOp> pendingEdits = new ArrayDeque<>();
  public final ArrayDeque<EditOp> pendingRedo = new ArrayDeque<>();

  public boolean isApplyingUndoRedo = false;
  public volatile long lastEditTimestamp = 0L;
  public int lineCountDelta = 0;

  // Edit version for tracking
  public final AtomicInteger editVersion = new AtomicInteger(0);

  public EditOperators(SodiumEditor editor) {
    this.editor = editor;
  }

  /**
   * Represents a single edit operation
   */
  public static final class EditOp {
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

  // ==============================
  // JSON Serialization
  // ==============================

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

  public static JSONArray editOpDequeToJson(ArrayDeque<EditOp> deque) throws Exception {
    JSONArray arr = new JSONArray();
    for (EditOp op : deque) {
      arr.put(editOpToJson(op));
    }
    return arr;
  }

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

  public String exportEditCacheJson() {
    try {
      JSONObject root = new JSONObject();
      root.put("undo", editOpDequeToJson(undoStack));
      root.put("redo", editOpDequeToJson(redoStack));
      root.put("pending", editOpDequeToJson(pendingEdits));
      root.put("pendingRedo", editOpDequeToJson(pendingRedo));
      root.put("dirty", !pendingEdits.isEmpty());
      root.put("cursorLine", editor.cursor.cursorLine);
      root.put("cursorChar", editor.cursor.cursorChar);
      root.put("selStartLine", editor.selection.selStartLine);
      root.put("selStartChar", editor.selection.selStartChar);
      root.put("selEndLine", editor.selection.selEndLine);
      root.put("selEndChar", editor.selection.selEndChar);
      root.put("hasSelection", editor.selection.hasSelection);
      return root.toString();
    } catch (Exception e) {
      return "";
    }
  }

  public boolean importEditCacheJson(String json, boolean applyPendingEdits) {
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
        int cLine = root.optInt("cursorLine", editor.cursor.cursorLine);
        int cChar = root.optInt("cursorChar", editor.cursor.cursorChar);
        if (root.optBoolean("hasSelection", false)) {
          int sL = root.optInt("selStartLine", cLine);
          int sC = root.optInt("selStartChar", cChar);
          int eL = root.optInt("selEndLine", cLine);
          int eC = root.optInt("selEndChar", cChar);
          editor.selection.restoreSelection(sL, sC, eL, eC, cLine, cChar);
        } else {
          editor.cursor.setCursorPosition(cLine, cChar);
        }
      }

      editVersion.incrementAndGet();
      editor.lineNumber.invalidateLineNumberCache();
      editor.invalidate();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  // ==============================
  // Undo/Redo Operations
  // ==============================

  public boolean canUndo() {
    return !undoStack.isEmpty();
  }

  public boolean canRedo() {
    return !redoStack.isEmpty();
  }

  public int getUndoStackSize() {
    return undoStack.size();
  }

  public int getPendingEditsCount() {
    return pendingEdits.size();
  }

  public void clearUndoRedoHistory() {
    undoStack.clear();
    redoStack.clear();
    pendingEdits.clear();
    pendingRedo.clear();
  }

  public long getLastEditTimestamp() {
    return lastEditTimestamp;
  }

  public void undo() {
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

  public void redo() {
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

  public void applyEditForUndoRedo(
      int sL, int sC, int eL, int eC, String text, int cursorLine, int cursorChar) {
    editor.selection.setSelectionInternal(sL, sC, eL, eC);
    editor.selection.replaceSelectionWithText(text);
    editor.cursor.setCursorPosition(cursorLine, cursorChar);
    if (editor.wordWrap.isWordWrapEnabled) {
      editor.wordWrap.invalidateWrapMetrics(true);
      editor.wordWrap.requestWrapPrefixRebuild();
    }
    editor.lineNumber.invalidateLineNumberCache();
    editor.invalidate();
  }

  // ==============================
  // Edit Recording
  // ==============================

  public void recordEdit(EditOp op) {
    if (isApplyingUndoRedo) return;
    if (op == null) return;
    editor.markTyping();
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
            "SodiumEditorEdit",
            "merge insert start="
                + op.startLine
                + ":"
                + op.startChar
                + " addLen="
                + op.insertedText.length());
        String beforeText = lastPending.insertedText;
        lastPending.insertedText = lastPending.insertedText + op.insertedText;
        CursorTarget newEnd =
            computeCursorAfterInsert(
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
        "SodiumEditorEdit",
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

  public void recordEditNoUndo(EditOp op) {
    if (isApplyingUndoRedo) return;
    if (op == null) return;
    editor.markTyping();
    // Save-only record for very large edits or unknown removed text.
    pendingEdits.addLast(op);
    pendingRedo.clear();
    redoStack.clear();
    lastEditTimestamp = op.timestamp;
    Log.d(
        "SodiumEditorEdit",
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

  public CursorTarget computeCursorAfterInsert(int baseLine, int baseChar, String insertText) {
    if (insertText == null) insertText = "";
    int newLines = 0;

    int lastNl = insertText.lastIndexOf('\n');
    if (lastNl >= 0) {
      for (int i = 0; i < insertText.length(); i++) {
        if (insertText.charAt(i) == '\n') newLines++;
      }
      int lastSegLen = insertText.length() - lastNl - 1;
      return new CursorTarget(baseLine + newLines, lastSegLen);
    }
    return new CursorTarget(baseLine, baseChar + insertText.length());
  }

  public int countNewlines(@Nullable String text) {
    if (text == null || text.isEmpty()) return 0;
    int count = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') count++;
    }
    return count;
  }

  public boolean hasPendingEdits() {
    return !pendingEdits.isEmpty();
  }

  // ==============================
  // File Rewrite Operations
  // ==============================

  public void applyPendingEditsToFileAsync(@Nullable Runnable onComplete) {
    if (editor.fileIO.sourceFile == null) {
      if (onComplete != null) editor.post(onComplete);
      return;
    }
    if (editor.ime.hasComposing) {
      Log.d("SodiumEditorSave", "commitComposing before save");
      editor.ime.commitComposing(true);
    }
    final ArrayList<EditOp> ops = new ArrayList<>();
    synchronized (pendingEdits) {
      Log.d("SodiumEditorSave", "pendingEdits.size=" + pendingEdits.size());
      ops.addAll(pendingEdits);
      pendingEdits.clear();
      pendingRedo.clear();
    }
    if (ops.isEmpty()) {
      if (onComplete != null) editor.post(onComplete);
      return;
    }
    Log.d("SodiumEditorSave", "Saving pending ops=" + ops.size());
    editor.fileIO.ioHandler.post(
        () -> {
          boolean ok = true;
          for (EditOp op : ops) {
            Log.d(
                "SodiumEditorSave",
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
                editor.fileIO.sourceFile, op.startLine, op.startChar, op.endLine, op.endChar, op.insertedText)) {
              ok = false;
              break;
            }
          }
          final boolean success = ok;
          editor.post(
              () -> {
                if (!success) {
                  // If save failed, mark dirty so user can retry.
                  Log.d("SodiumEditorSave", "Save failed");
                  pendingEdits.addAll(ops);
                } else {
                  Log.d("SodiumEditorSave", "Save success");
                  synchronized (editor.textRender.modifiedLines) {
                    editor.textRender.modifiedLines.clear();
                  }
                  lineCountDelta = 0;
                  editor.lineNumber.invalidateLineNumberCache();
                  editor.requestLayout();
                  editor.invalidate();
                }
                if (onComplete != null) onComplete.run();
              });
        });
  }

  public boolean rewriteReplaceRangeBlocking(
      File inFile, int sL, int sC, int eL, int eC, @Nullable String insertText) {
    if (inFile == null || !inFile.exists()) return false;
    try {
      RangeBytes range = computeByteRangeFastOrScan(inFile, sL, sC, eL, eC);
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

      editor.fileIO.sourceFile = inFile;
      synchronized (editor.fileIO.lineOffsetsLock) {
        editor.fileIO.lineOffsets = new long[0];
      }
      editor.fileIO.isIndexReady = false;
      editor.fileIO.isIndexBuilding = false;
      editor.fileIO.isIndexDisabled = false;
      editor.fileIO.indexDisabledPath = null;
      editor.fileIO.indexDisabledFileLength = -1L;
      editor.fileIO.ioHandler.post(editor.fileIO::buildFileIndex);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public void rewriteReplaceRangeAsync(
      int opToken,
      File inFile,
      int sL,
      int sC,
      int eL,
      int eC,
      String insertText,
      CursorTarget target,
      boolean finishLargeEditUi) {
    editor.fileIO.ioHandler.post(
        () -> {
          try {
            if (inFile == null || !inFile.exists()) {
              editor.post(
                  () -> {
                    if (finishLargeEditUi) editor.loadingCircle.endLargeEditUi(true);
                  });
              return;
            }

            RangeBytes range = computeByteRangeFastOrScan(inFile, sL, sC, eL, eC);
            if (range == null) {
              editor.post(
                  () -> {
                    if (finishLargeEditUi) editor.loadingCircle.endLargeEditUi(true);
                  });
              return;
            }

            File outFile = File.createTempFile("popedit_", ".tmp", editor.getContext().getCacheDir());
            byte[] insertBytes =
                (insertText == null) ? new byte[0] : insertText.getBytes(StandardCharsets.UTF_8);

            try (RandomAccessFile rafIn = new RandomAccessFile(inFile, "r");
                FileChannel inCh = rafIn.getChannel();
                RandomAccessFile rafOut = new RandomAccessFile(outFile, "rw");
                FileChannel outCh = rafOut.getChannel()) {

              long fileLen = rafIn.length();
              long startByte = Math.max(0, Math.min(range.startByte, fileLen));
              long endByte = Math.max(0, Math.min(range.endByte, fileLen));
              if (endByte < startByte) {
                long t = startByte;
                startByte = endByte;
                endByte = t;
              }

              transferRange(inCh, outCh, 0, startByte);

              if (insertBytes.length > 0) {
                outCh.write(ByteBuffer.wrap(insertBytes));
              }

              transferRange(inCh, outCh, endByte, fileLen - endByte);
              outCh.force(true);
            }

            editor.post(
                () -> {
                  if (opToken != editVersion.get()) return;

                  editor.fileIO.invalidatePendingIO();

                  if (inFile != null) {
                    try (FileInputStream fis = new FileInputStream(outFile);
                        FileOutputStream fos = new FileOutputStream(inFile)) {
                      byte[] buf = new byte[8192];
                      int r;
                      while ((r = fis.read(buf)) > 0) {
                        fos.write(buf, 0, r);
                      }
                      fos.flush();
                    } catch (Exception ignore) {
                    }
                    outFile.delete();
                    editor.fileIO.sourceFile = inFile;
                  } else {
                    editor.fileIO.sourceFile = outFile;
                  }
                  editor.fileIO.isFileCleared = false;

                  synchronized (editor.textRender.modifiedLines) {
                    editor.textRender.modifiedLines.clear();
                  }
                  synchronized (editor.textRender.lineWidthCache) {
                    editor.textRender.lineWidthCache.clear();
                  }
                  editor.textRender.currentMaxWindowLineWidth = 0f;
                  editor.textRender.globalMaxLineWidth = 0f;
                  editor.scroll.maxLineWidthForScroll = 0f;
                  editor.scroll.maxTextStartXForScroll = 0f;
                  editor.scroll.maxScrollXForScroll = 0f;
                  lineCountDelta = 0;

                  synchronized (editor.fileIO.lineOffsetsLock) {
                    editor.fileIO.lineOffsets = new long[0];
                  }
                  editor.fileIO.isIndexReady = false;
                  editor.fileIO.isIndexBuilding = false;
                  editor.fileIO.isIndexDisabled = false;
                  editor.fileIO.indexDisabledPath = null;
                  editor.fileIO.indexDisabledFileLength = -1L;
                  editor.fileIO.isEof = false;

                  editor.fileIO.ioHandler.post(editor.fileIO::buildFileIndex);
                  editor.wordWrap.onLineCountChanged();

                  editor.cursor.cursorLine = Math.max(0, target.line);
                  editor.cursor.cursorChar = Math.max(0, target.ch);

                  // لا تعمل "Reload" للنافذة بعد الحذف/الاستبدال إذا كانت النتيجة ضمن النافذة
                  // الحالية.
                  // هذا يمنع دائرة التحميل ويمنع القفز/الزمن الطويل مع الملفات الضخمة.
                  boolean cursorInsideWindow =
                      (editor.cursor.cursorLine >= editor.textRender.windowStartLine
                          && editor.cursor.cursorLine < editor.textRender.windowStartLine + editor.textRender.linesWindow.size());

                  if (cursorInsideWindow) {
                    // النافذة الحالية تم تعديلها مسبقاً (fast path)، فقط أعد حساب العرض وحدث الرسم.
                    synchronized (editor.textRender.linesWindow) {
                      editor.fileIO.isEof = editor.textRender.linesWindow.size() < editor.textRender.windowSize + (editor.textRender.prefetchLines * 2);
                    }
                    editor.recalculateMaxLineWidth();
                    editor.requestFocus();
                    android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                            editor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.restartInput(editor);
                    if (finishLargeEditUi) editor.loadingCircle.endLargeEditUi(false);
                    editor.invalidate();
                  } else {
                    int targetStart = Math.max(0, editor.cursor.cursorLine - editor.textRender.prefetchLines);
                    editor.fileIO.loadWindowAround(
                        targetStart,
                        () -> {
                          String ln = editor.getLineTextForRender(editor.cursor.cursorLine);
                          editor.cursor.cursorChar = Math.min(editor.cursor.cursorChar, ln.length());
                          editor.scroll.clampScrollY();
                          editor.keepCursorVisibleHorizontally();
                          editor.requestFocus();
                          android.view.inputmethod.InputMethodManager imm =
                              (android.view.inputmethod.InputMethodManager)
                                  editor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                          if (imm != null) imm.restartInput(editor);
                          if (finishLargeEditUi) editor.loadingCircle.endLargeEditUi(false);
                          editor.invalidate();
                        },
                        false);
                  }
                });

          } catch (Exception ex) {
            ex.printStackTrace();
            editor.post(
                () -> {
                  if (finishLargeEditUi) editor.loadingCircle.endLargeEditUi(true);
                });
          }
        });
  }

  public void transferRange(FileChannel inCh, FileChannel outCh, long position, long count)
      throws Exception {
    long remaining = count;
    long pos = position;
    while (remaining > 0) {
      long sent = inCh.transferTo(pos, remaining, outCh);
      if (sent <= 0) break;
      pos += sent;
      remaining -= sent;
    }
  }

  // ==============================
  // Byte Range Computation
  // ==============================

  public RangeBytes computeByteRangeFastOrScan(File file, int sL, int sC, int eL, int eC) {
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tl = sL, tc = sC;
      sL = eL;
      sC = eC;
      eL = tl;
      eC = tc;
    }

    if (editor.fileIO.isIndexReady && file != null) {
      RangeBytes fast = computeByteRangeUsingIndex(file, sL, sC, eL, eC);
      if (fast != null) return fast;
    }

    return computeByteRangeByScanning(file, sL, sC, eL, eC);
  }

  public RangeBytes computeByteRangeUsingIndex(File file, int sL, int sC, int eL, int eC) {
    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
      long startLineByte, endLineByte;
      synchronized (editor.fileIO.lineOffsetsLock) {
        if (!editor.fileIO.isIndexReady) return null;
        if (sL < 0 || eL < 0) return null;
        if (sL >= editor.fileIO.lineOffsets.length || eL >= editor.fileIO.lineOffsets.length) return null;
        startLineByte = editor.fileIO.lineOffsets[sL];
        endLineByte = editor.fileIO.lineOffsets[eL];
      }

      String startLineText = editor.fileIO.readLineUtf8AtByte(raf, startLineByte);
      String endLineText = (eL == sL) ? startLineText : editor.fileIO.readLineUtf8AtByte(raf, endLineByte);

      long startByte = startLineByte + editor.computeByteOffsetInLineUtf8(startLineText, sC);
      long endByte = endLineByte + editor.computeByteOffsetInLineUtf8(endLineText, eC);

      return new RangeBytes(startByte, endByte);
    } catch (Exception ignore) {
      return null;
    }
  }

  public RangeBytes computeByteRangeByScanning(File file, int sL, int sC, int eL, int eC) {
    if (comparePos(sL, sC, eL, eC) > 0) {
      int tl = sL, tc = sC;
      sL = eL;
      sC = eC;
      eL = tl;
      eC = tc;
    }

    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
      long[] starts = findTwoLineStartBytesByScanning(raf, sL, eL);
      long startLineByte = starts[0];
      long endLineByte = starts[1];

      String startLineText = editor.fileIO.readLineUtf8AtByte(raf, startLineByte);
      String endLineText = (eL == sL) ? startLineText : editor.fileIO.readLineUtf8AtByte(raf, endLineByte);

      long startByte = startLineByte + editor.computeByteOffsetInLineUtf8(startLineText, sC);
      long endByte = endLineByte + editor.computeByteOffsetInLineUtf8(endLineText, eC);

      return new RangeBytes(startByte, endByte);
    } catch (Exception e) {
      return null;
    }
  }

  public int comparePos(int l1, int c1, int l2, int c2) {
    if (l1 < l2) return -1;
    if (l1 > l2) return 1;
    return Integer.compare(c1, c2);
  }

  public long[] findTwoLineStartBytesByScanning(RandomAccessFile raf, int lineA, int lineB)
      throws Exception {
    if (lineA < 0) lineA = 0;
    if (lineB < 0) lineB = 0;

    int a = Math.min(lineA, lineB);
    int b = Math.max(lineA, lineB);

    long offA = (a == 0) ? 0L : -1L;
    long offB = (b == 0) ? 0L : -1L;

    raf.seek(0);
    byte[] buf = new byte[8192];
    long pos = 0;
    int line = 0;

    while (true) {
      int n = raf.read(buf);
      if (n <= 0) break;

      for (int i = 0; i < n; i++) {
        if (buf[i] == '\n') {
          line++;
          long nextLineStart = pos + i + 1;

          if (line == a && offA < 0) offA = nextLineStart;
          if (line == b && offB < 0) offB = nextLineStart;

          if (offA >= 0 && offB >= 0) {
            if (lineA <= lineB) return new long[] {offA, offB};
            return new long[] {offB, offA};
          }
        }
      }
      pos += n;
    }

    long len = raf.length();
    if (offA < 0) offA = len;
    if (offB < 0) offB = len;

    if (lineA <= lineB) return new long[] {offA, offB};
    return new long[] {offB, offA};
  }

  /**
   * Fallback helper used when the line index is not ready. Returns the byte offset at which the
   * given 0-based line starts. This scans the file sequentially (O(n)) so it should only be used
   * for occasional operations like copy/cut when index isn't available.
   */
  public long findLineStartByteByScanning(RandomAccessFile raf, int targetLine) throws Exception {
    if (targetLine <= 0) return 0L;
    long[] starts = findTwoLineStartBytesByScanning(raf, targetLine, targetLine);
    return (starts != null && starts.length > 0) ? starts[0] : 0L;
  }

  // ==============================
  // Large Edit Detection
  // ==============================

  public boolean isLargePasteText(String text) {
    if (text == null) return false;
    return text.length() > UNDO_TEXT_LIMIT / 2;
  }

  // ==============================
  // Insert/Delete Operations
  // ==============================

  public void insertCharAtCursor(char c) {
    if (editor.isReadOnly) return;
    editor.fileIO.invalidatePendingIOForEdit();
    editVersion.incrementAndGet();

    // FIX: لو فيه تحديد، لازم يكون استبدال ذري (خصوصاً خارج الشاشة)
    if (editor.selection.hasSelection) {
      editor.selection.replaceSelectionWithText(String.valueOf(c));
      return;
    }

    if (editor.ime.hasComposing) {
      editor.ime.hasComposing = false;
      editor.ime.composingLength = 0;
    }

    if (editor.codeFold.isCodeFoldingEnabled) {
      com.yn.sodiumeditor.core.CodeFold.FoldRange hidden =
          editor.codeFold.getCollapsedRangeContainingLine(editor.cursor.cursorLine);
      if (hidden != null) {
        String endLineText = editor.getLineTextForRender(hidden.endLine);
        if (endLineText == null) {
          editor.fileIO.ensureLineInWindow(hidden.endLine, true);
          endLineText = editor.getLineTextForRender(hidden.endLine);
        }
        int closeIdx = editor.codeFold.resolveCloseCharIndex(hidden, endLineText);
        int afterClose = (closeIdx >= 0) ? closeIdx + 1 : (endLineText != null ? endLineText.length() : 0);
        editor.cursor.cursorLine = hidden.endLine;
        editor.cursor.cursorChar = Math.max(afterClose, editor.cursor.cursorChar);
      } else {
        com.yn.sodiumeditor.core.CodeFold.FoldRange start =
            editor.codeFold.getFoldRangeAtStart(editor.cursor.cursorLine);
        if (start != null && start.collapsed && editor.cursor.cursorChar > start.openCharIndex) {
          String endLineText = editor.getLineTextForRender(start.endLine);
          if (endLineText == null) {
            editor.fileIO.ensureLineInWindow(start.endLine, true);
            endLineText = editor.getLineTextForRender(start.endLine);
          }
          int closeIdx = editor.codeFold.resolveCloseCharIndex(start, endLineText);
          int afterClose = (closeIdx >= 0) ? closeIdx + 1 : (endLineText != null ? endLineText.length() : 0);
          editor.cursor.cursorLine = start.endLine;
          editor.cursor.cursorChar = Math.max(afterClose, editor.cursor.cursorChar);
        }
      }
    }

    final int beforeLine = editor.cursor.cursorLine;
    final int beforeChar = editor.cursor.cursorChar;

    editor.fileIO.ensureLineInWindow(editor.cursor.cursorLine, true);
    if (editor.fileIO.isWindowLoading
        && (editor.cursor.cursorLine < editor.textRender.windowStartLine || editor.cursor.cursorLine >= editor.textRender.windowStartLine + editor.textRender.linesWindow.size())) {
      editor.post(() -> insertCharAtCursor(c));
      return;
    }

    int localIdx = editor.cursor.cursorLine - editor.textRender.windowStartLine;
    if (localIdx < 0 || localIdx >= editor.textRender.linesWindow.size()) {
      synchronized (editor.textRender.linesWindow) {
        if (editor.textRender.linesWindow.isEmpty()) editor.textRender.linesWindow.add("");
      }
      localIdx = Math.max(0, Math.min(localIdx, editor.textRender.linesWindow.size() - 1));
    }

    boolean fullInvalidate = false;
    synchronized (editor.textRender.linesWindow) {
      String base = editor.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (c == '\n') {
        fullInvalidate = true;
        int oldLineCount = editor.getLinesCount();
        String before = base.substring(0, Math.min(editor.cursor.cursorChar, base.length()));
        String after = base.substring(Math.min(editor.cursor.cursorChar, base.length()));
        Float oldWidth = editor.textRender.lineWidthCache.get(editor.cursor.cursorLine);

        editor.updateLocalLine(localIdx, before);
        editor.textRender.linesWindow.add(localIdx + 1, after);

        editor.textRender.modifiedLines.put(editor.cursor.cursorLine, before);
        editor.textRender.modifiedLines.put(editor.cursor.cursorLine + 1, after);
        if (editor.codeFold.isCodeFoldingEnabled) {
          boolean preserveFold = false;
          com.yn.sodiumeditor.core.CodeFold.FoldRange range =
              editor.codeFold.getCollapsedRangeContainingLine(editor.cursor.cursorLine);
          if (range == null) {
            range = editor.codeFold.getFoldRangeAtStart(editor.cursor.cursorLine);
          }
          if (range != null && range.collapsed) {
            String endLineText = editor.getLineTextForRender(range.endLine);
            int closeIdx = range.closeCharIndex;
            if (endLineText != null) {
              int resolved = editor.codeFold.resolveCloseCharIndex(range, endLineText);
              if (resolved >= 0) closeIdx = resolved;
            }
            if (editor.cursor.cursorLine == range.endLine) {
              if (closeIdx >= 0) {
                preserveFold = editor.cursor.cursorChar >= closeIdx + 1;
              } else {
                // Unknown close index: keep fold to avoid expanding while editing after placeholder.
                preserveFold = true;
              }
            }
            if (editor.cursor.cursorLine == range.startLine && editor.cursor.cursorChar > range.openCharIndex) {
              preserveFold = true;
            }
          }
          if (!preserveFold) {
            editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine);
            editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine + 1);
          }
        }

        editor.computeWidthForLine(editor.cursor.cursorLine, before);
        editor.computeWidthForLine(editor.cursor.cursorLine + 1, after);

        if (oldWidth != null && oldWidth >= editor.textRender.currentMaxWindowLineWidth)
          editor.fileIO.recalculateMaxLineWidthAsync();
        editor.clearHighlightCaches();
        editor.cursor.cursorLine++;
        editor.cursor.cursorChar = 0;
        lineCountDelta += 1;

        int newLineCount = editor.getLinesCount();
        if (editor.lineNumber.showLineNumbers
            && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
          editor.requestLayout();
        }
        editor.wordWrap.onLineCountChanged();
      } else {
        int pos = Math.max(0, Math.min(editor.cursor.cursorChar, base.length()));
        String modified = base.substring(0, pos) + c + base.substring(pos);
        editor.updateLocalLine(localIdx, modified);
        editor.textRender.modifiedLines.put(editor.cursor.cursorLine, modified);
        if (editor.codeFold.isCodeFoldingEnabled) {
          if (editor.containsBracketChars(String.valueOf(c))) {
            editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine);
          }
          editor.codeFold.adjustFoldRangeForLineEdit(editor.cursor.cursorLine, pos, 1, 0);
        }
        editor.invalidateHighlightCacheForLine(editor.cursor.cursorLine);
        editor.cursor.cursorChar++;
        float newWidth = editor.measureTextWithVisualSpaces(modified, 0, modified.length(), editor.textRender.paint);
        synchronized (editor.textRender.lineWidthCache) {
          editor.textRender.lineWidthCache.put(editor.cursor.cursorLine, newWidth);
        }
        editor.textRender.currentMaxWindowLineWidth = Math.max(editor.textRender.currentMaxWindowLineWidth, newWidth);
        editor.textRender.globalMaxLineWidth = Math.max(editor.textRender.globalMaxLineWidth, editor.textRender.currentMaxWindowLineWidth);
      }
      if (fullInvalidate) {
        editor.invalidate();
      } else {
        editor.invalidateLineGlobal(editor.cursor.cursorLine);
      }
      editor.keepCursorVisibleHorizontally();
    }
    editor.autoCompletion.updateSuggestion();

    EditOp op = new EditOp();
    op.startLine = beforeLine;
    op.startChar = beforeChar;
    op.endLine = beforeLine;
    op.endChar = beforeChar;
    op.removedText = "";
    op.insertedText = String.valueOf(c);
    CursorTarget insertedEnd = computeCursorAfterInsert(beforeLine, beforeChar, op.insertedText);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = editor.cursor.cursorLine;
    op.cursorCharAfter = editor.cursor.cursorChar;
    op.timestamp = System.currentTimeMillis();
    recordEdit(op);
  }

  public void deleteCharAtCursor() {
    if (editor.isReadOnly) return;
    editor.fileIO.invalidatePendingIOForEdit();
    editVersion.incrementAndGet();
    editor.autoCompletion.clearActiveSuggestion();

    if (editor.ime.hasComposing) {
      editor.ime.deleteComposing();
      return;
    }

    final int beforeLine = editor.cursor.cursorLine;
    final int beforeChar = editor.cursor.cursorChar;

    editor.fileIO.ensureLineInWindow(editor.cursor.cursorLine, true);
    if (editor.fileIO.isWindowLoading
        && (editor.cursor.cursorLine < editor.textRender.windowStartLine || editor.cursor.cursorLine >= editor.textRender.windowStartLine + editor.textRender.linesWindow.size())) {
      editor.post(this::deleteCharAtCursor);
      return;
    }

    int localIdx = editor.cursor.cursorLine - editor.textRender.windowStartLine;
    if (localIdx < 0 || localIdx >= editor.textRender.linesWindow.size()) return;

    synchronized (editor.textRender.linesWindow) {
      String base = editor.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (editor.cursor.cursorChar > 0) {
        Float oldWidth = editor.textRender.lineWidthCache.get(editor.cursor.cursorLine);
        int safeStart = Math.max(0, editor.cursor.cursorChar - 1);
        String removed = base.substring(safeStart, Math.min(editor.cursor.cursorChar, base.length()));
        boolean atLineEnd = editor.cursor.cursorChar >= base.length();
        if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
          int[] span = new int[2];
          int probe = Math.max(0, editor.cursor.cursorChar - 1);
          if (editor.binaryRender.findBinaryTokenSpan(base, probe, span)) {
            int s = span[0];
            int e = span[1];
            removed = base.substring(s, Math.min(e, base.length()));
            if (atLineEnd || s < base.length()) {
              android.graphics.Paint p = editor.textRender.getPaintForChar(editor.cursor.cursorLine, s, base);
              editor.charAnimation.startDeleteAnimation(editor.cursor.cursorLine, s, removed, p);
            }
            String modified = base.substring(0, s) + base.substring(Math.min(e, base.length()));
            editor.updateLocalLine(localIdx, modified);
            editor.textRender.modifiedLines.put(editor.cursor.cursorLine, modified);
            if (editor.codeFold.isCodeFoldingEnabled) {
              if (editor.containsBracketChars(removed)) {
                editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine);
              }
              editor.codeFold.adjustFoldRangeForLineEdit(editor.cursor.cursorLine, s, -(e - s), (e - s));
            }
            editor.invalidateHighlightCacheForLine(editor.cursor.cursorLine);
            editor.cursor.cursorChar = s;
            editor.computeWidthForLine(editor.cursor.cursorLine, modified);
            if (oldWidth != null && oldWidth >= editor.textRender.currentMaxWindowLineWidth)
              editor.fileIO.recalculateMaxLineWidthAsync();
            editor.invalidateLineGlobal(editor.cursor.cursorLine);
            editor.selection.clearSelection();
            editor.selection.selecting = false;

            EditOp op = new EditOp();
            op.startLine = beforeLine;
            op.startChar = s;
            op.endLine = beforeLine;
            op.endChar = beforeChar;
            op.removedText = removed;
            op.insertedText = "";
            op.insertedEndLine = beforeLine;
            op.insertedEndChar = s;
            op.cursorLineBefore = beforeLine;
            op.cursorCharBefore = beforeChar;
            op.cursorLineAfter = editor.cursor.cursorLine;
            op.cursorCharAfter = editor.cursor.cursorChar;
            op.timestamp = System.currentTimeMillis();
            recordEdit(op);
            editor.binaryRender.adjustBinaryTokenSpansForEdit(editor.cursor.cursorLine, s, -(e - s), (e - s));
            return;
          }
        }
        if (atLineEnd) {
          android.graphics.Paint p = editor.textRender.getPaintForChar(editor.cursor.cursorLine, safeStart, base);
          editor.charAnimation.startDeleteAnimation(editor.cursor.cursorLine, safeStart, removed, p);
        }
        String modified = base.substring(0, safeStart) + base.substring(editor.cursor.cursorChar);
        editor.updateLocalLine(localIdx, modified);
        editor.textRender.modifiedLines.put(editor.cursor.cursorLine, modified);
        if (editor.codeFold.isCodeFoldingEnabled) {
          if (editor.containsBracketChars(removed)) {
            editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine);
          }
          editor.codeFold.adjustFoldRangeForLineEdit(editor.cursor.cursorLine, safeStart, -1, 1);
        }
        editor.invalidateHighlightCacheForLine(editor.cursor.cursorLine);
        editor.cursor.cursorChar = safeStart;
        editor.computeWidthForLine(editor.cursor.cursorLine, modified);
        if (oldWidth != null && oldWidth >= editor.textRender.currentMaxWindowLineWidth)
          editor.fileIO.recalculateMaxLineWidthAsync();
        editor.invalidateLineGlobal(editor.cursor.cursorLine);
        if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
          editor.binaryRender.adjustBinaryTokenSpansForEdit(editor.cursor.cursorLine, safeStart, -1, 1);
        }

        EditOp op = new EditOp();
        op.startLine = beforeLine;
        op.startChar = safeStart;
        op.endLine = beforeLine;
        op.endChar = beforeChar;
        op.removedText = removed;
        op.insertedText = "";
        op.insertedEndLine = beforeLine;
        op.insertedEndChar = safeStart;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = editor.cursor.cursorLine;
        op.cursorCharAfter = editor.cursor.cursorChar;
        op.timestamp = System.currentTimeMillis();
        recordEdit(op);
      } else if (editor.cursor.cursorLine > 0) {
        int oldLineCount = editor.getLinesCount();
        int prevGlobal = editor.cursor.cursorLine - 1;
        editor.fileIO.ensureLineInWindow(prevGlobal, true);
        int prevLocal = prevGlobal - editor.textRender.windowStartLine;
        if (prevLocal < 0 || prevLocal >= editor.textRender.linesWindow.size()) return;

        String prev = editor.getLineFromWindowLocal(prevLocal);
        if (prev == null) prev = "";

        String merged = prev + base;
        editor.updateLocalLine(prevLocal, merged);
        editor.textRender.modifiedLines.put(prevGlobal, merged);
        if (editor.codeFold.isCodeFoldingEnabled) {
          editor.codeFold.invalidateFoldRangeForLine(prevGlobal);
          editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine);
        }
        editor.clearHighlightCaches();

        if (localIdx < editor.textRender.linesWindow.size()) editor.textRender.linesWindow.remove(localIdx);

        editor.recalculateMaxLineWidth();
        editor.cursor.cursorLine = prevGlobal;
        editor.cursor.cursorChar = prev.length();
        editor.computeWidthForLine(prevGlobal, merged);
        lineCountDelta -= 1;

        int newLineCount = editor.getLinesCount();
        if (editor.lineNumber.showLineNumbers
            && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
          editor.requestLayout();
        }
        editor.wordWrap.onLineCountChanged();
        editor.invalidate();

        EditOp op = new EditOp();
        op.startLine = prevGlobal;
        op.startChar = prev.length();
        op.endLine = beforeLine;
        op.endChar = 0;
        op.removedText = "\n";
        op.insertedText = "";
        op.insertedEndLine = prevGlobal;
        op.insertedEndChar = prev.length();
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = editor.cursor.cursorLine;
        op.cursorCharAfter = editor.cursor.cursorChar;
        op.timestamp = System.currentTimeMillis();
        recordEdit(op);
      }
    }
    editor.autoCompletion.updateSuggestion();
  }

  public void deleteForwardAtCursor() {
    if (editor.isReadOnly) return;
    editor.fileIO.invalidatePendingIOForEdit();
    editVersion.incrementAndGet();
    editor.autoCompletion.clearActiveSuggestion();

    if (editor.ime.hasComposing) {
      editor.ime.deleteComposing();
      return;
    }

    final int beforeLine = editor.cursor.cursorLine;
    final int beforeChar = editor.cursor.cursorChar;

    editor.fileIO.ensureLineInWindow(editor.cursor.cursorLine, true);
    if (editor.fileIO.isWindowLoading
        && (editor.cursor.cursorLine < editor.textRender.windowStartLine || editor.cursor.cursorLine >= editor.textRender.windowStartLine + editor.textRender.linesWindow.size())) {
      editor.post(this::deleteForwardAtCursor);
      return;
    }

    int localIdx = editor.cursor.cursorLine - editor.textRender.windowStartLine;
    synchronized (editor.textRender.linesWindow) {
      String base = editor.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";

      if (editor.cursor.cursorChar < base.length()) {
        Float oldWidth = editor.textRender.lineWidthCache.get(editor.cursor.cursorLine);
        String removed = base.substring(editor.cursor.cursorChar, Math.min(editor.cursor.cursorChar + 1, base.length()));
        boolean atLineEnd = editor.cursor.cursorChar == base.length() - 1;
        if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
          int[] span = new int[2];
          int probe = Math.max(0, editor.cursor.cursorChar);
          if (editor.binaryRender.findBinaryTokenSpan(base, probe, span)) {
            int s = span[0];
            int e = span[1];
            removed = base.substring(s, Math.min(e, base.length()));
            if (atLineEnd || s < base.length()) {
              android.graphics.Paint p = editor.textRender.getPaintForChar(editor.cursor.cursorLine, s, base);
              editor.charAnimation.startDeleteAnimation(editor.cursor.cursorLine, s, removed, p);
            }
            String modified = base.substring(0, s) + base.substring(Math.min(e, base.length()));
            editor.updateLocalLine(localIdx, modified);
            editor.textRender.modifiedLines.put(editor.cursor.cursorLine, modified);
            if (editor.codeFold.isCodeFoldingEnabled) {
              if (editor.containsBracketChars(removed)) {
                editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine);
              }
              editor.codeFold.adjustFoldRangeForLineEdit(editor.cursor.cursorLine, s, -(e - s), (e - s));
            }
            editor.computeWidthForLine(editor.cursor.cursorLine, modified);
            if (oldWidth != null && oldWidth >= editor.textRender.currentMaxWindowLineWidth)
              editor.fileIO.recalculateMaxLineWidthAsync();
            editor.invalidateLineGlobal(editor.cursor.cursorLine);

            EditOp op = new EditOp();
            op.startLine = beforeLine;
            op.startChar = beforeChar;
            op.endLine = beforeLine;
            op.endChar = beforeChar + (e - s);
            op.removedText = removed;
            op.insertedText = "";
            op.insertedEndLine = beforeLine;
            op.insertedEndChar = beforeChar;
            op.cursorLineBefore = beforeLine;
            op.cursorCharBefore = beforeChar;
            op.cursorLineAfter = editor.cursor.cursorLine;
            op.cursorCharAfter = editor.cursor.cursorChar;
            op.timestamp = System.currentTimeMillis();
            recordEdit(op);
            editor.binaryRender.adjustBinaryTokenSpansForEdit(editor.cursor.cursorLine, s, -(e - s), (e - s));
            return;
          }
        }
        if (atLineEnd) {
          android.graphics.Paint p = editor.textRender.getPaintForChar(editor.cursor.cursorLine, editor.cursor.cursorChar, base);
          editor.charAnimation.startDeleteAnimation(editor.cursor.cursorLine, editor.cursor.cursorChar, removed, p);
        }
        String modified = base.substring(0, editor.cursor.cursorChar) + base.substring(editor.cursor.cursorChar + 1);
        editor.updateLocalLine(localIdx, modified);
        editor.textRender.modifiedLines.put(editor.cursor.cursorLine, modified);
        if (editor.codeFold.isCodeFoldingEnabled) {
          if (editor.containsBracketChars(removed)) {
            editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine);
          }
          editor.codeFold.adjustFoldRangeForLineEdit(editor.cursor.cursorLine, editor.cursor.cursorChar, -1, 1);
        }
        editor.computeWidthForLine(editor.cursor.cursorLine, modified);
        if (oldWidth != null && oldWidth >= editor.textRender.currentMaxWindowLineWidth)
          editor.fileIO.recalculateMaxLineWidthAsync();
        editor.invalidateLineGlobal(editor.cursor.cursorLine);
        if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
          editor.binaryRender.adjustBinaryTokenSpansForEdit(editor.cursor.cursorLine, editor.cursor.cursorChar, -1, 1);
        }

        EditOp op = new EditOp();
        op.startLine = beforeLine;
        op.startChar = beforeChar;
        op.endLine = beforeLine;
        op.endChar = beforeChar + 1;
        op.removedText = removed;
        op.insertedText = "";
        op.insertedEndLine = beforeLine;
        op.insertedEndChar = beforeChar;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = editor.cursor.cursorLine;
        op.cursorCharAfter = editor.cursor.cursorChar;
        op.timestamp = System.currentTimeMillis();
        recordEdit(op);
      } else {
        int nextGlobal = editor.cursor.cursorLine + 1;
        if (editor.fileIO.isEof && nextGlobal >= editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) return;

        editor.fileIO.ensureLineInWindow(nextGlobal, true);
        int nextLocal = nextGlobal - editor.textRender.windowStartLine;
        if (nextLocal >= 0 && nextLocal < editor.textRender.linesWindow.size()) {
          String next = editor.getLineFromWindowLocal(nextLocal);
          if (next == null) next = "";
          String merged = base + next;
          editor.updateLocalLine(localIdx, merged);
          editor.textRender.linesWindow.remove(nextLocal);
          editor.textRender.modifiedLines.put(editor.cursor.cursorLine, merged);
          if (editor.codeFold.isCodeFoldingEnabled) {
            editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine);
            editor.codeFold.invalidateFoldRangeForLine(nextGlobal);
          }
          editor.recalculateMaxLineWidth();
          editor.computeWidthForLine(editor.cursor.cursorLine, merged);
          editor.wordWrap.onLineCountChanged();
          editor.invalidate();
          lineCountDelta -= 1;

          EditOp op = new EditOp();
          op.startLine = beforeLine;
          op.startChar = base.length();
          op.endLine = nextGlobal;
          op.endChar = 0;
          op.removedText = "\n";
          op.insertedText = "";
          op.insertedEndLine = beforeLine;
          op.insertedEndChar = base.length();
          op.cursorLineBefore = beforeLine;
          op.cursorCharBefore = beforeChar;
          op.cursorLineAfter = editor.cursor.cursorLine;
          op.cursorCharAfter = editor.cursor.cursorChar;
          op.timestamp = System.currentTimeMillis();
          recordEdit(op);
        }
      }
    }
    editor.autoCompletion.updateSuggestion();
  }

  public void insertStringAtCursor(String text) {
    if (editor.isReadOnly) return;
    if (text == null || text.isEmpty()) return;
    if (editor.selection.hasSelection) {
      editor.selection.replaceSelectionWithText(text);
      return;
    }
    if (text.contains("\n")) {
      for (char c : text.toCharArray()) insertCharAtCursor(c);
      return;
    }
    editor.fileIO.invalidatePendingIOForEdit();
    editVersion.incrementAndGet();

    editor.fileIO.ensureLineInWindow(editor.cursor.cursorLine, true);
    if (editor.fileIO.isWindowLoading
        && (editor.cursor.cursorLine < editor.textRender.windowStartLine || editor.cursor.cursorLine >= editor.textRender.windowStartLine + editor.textRender.linesWindow.size())) {
      editor.post(() -> insertStringAtCursor(text));
      return;
    }

    int localIdx = editor.cursor.cursorLine - editor.textRender.windowStartLine;
    synchronized (editor.textRender.linesWindow) {
      String base = editor.getLineFromWindowLocal(localIdx);
      if (base == null) base = "";
      int pos = Math.max(0, Math.min(editor.cursor.cursorChar, base.length()));
      String modified = base.substring(0, pos) + text + base.substring(pos);
      editor.updateLocalLine(localIdx, modified);
      editor.textRender.modifiedLines.put(editor.cursor.cursorLine, modified);
      if (editor.binaryRender.isBinarySafeRenderingEnabled()) {
        editor.binaryRender.adjustBinaryTokenSpansForEdit(editor.cursor.cursorLine, pos, text.length(), 0);
      }
      editor.invalidateHighlightCacheForLine(editor.cursor.cursorLine);
      if (editor.codeFold.isCodeFoldingEnabled) {
        if (editor.containsBracketChars(text)) {
          editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine);
        }
        editor.codeFold.adjustFoldRangeForLineEdit(editor.cursor.cursorLine, pos, text.length(), 0);
      }
      editor.cursor.cursorChar += text.length();
      editor.computeWidthForLine(editor.cursor.cursorLine, modified);
      editor.recalculateMaxLineWidth();
      editor.keepCursorVisibleHorizontally();
      editor.invalidate();
    }
  }

  public void insertTextAtCursor(String text) {
    if (editor.isReadOnly) return;
    editor.fileIO.invalidatePendingIOForEdit();
    final int opToken = editVersion.incrementAndGet();

    if (text == null) return;
    if (text.isEmpty() && !editor.selection.hasSelection) return;

    // FIX: لو فيه تحديد، لازم يكون replace ذري
    if (editor.selection.hasSelection) {
      editor.selection.replaceSelectionWithText(text);
      return;
    }

    if (editor.ime.hasComposing) {
      editor.ime.hasComposing = false;
      editor.ime.composingLength = 0;
    }

    if (text.isEmpty()) {
      editor.invalidate();
      return;
    }

    final int beforeLine = editor.cursor.cursorLine;
    final int beforeChar = editor.cursor.cursorChar;

    // For very large pastes into a file-backed document, avoid expanding the in-memory window and
    // doing expensive per-line work on the UI thread. Instead, apply the insert via the file rewrite
    // path.
    if (editor.fileIO.sourceFile != null && !editor.fileIO.isFileCleared && isLargePasteText(text)) {
      editor.loadingCircle.beginLargeEditUiIfNeeded(true, editor.cursor.cursorLine, editor.cursor.cursorLine, true);
      editor.caret.mainHandler.removeCallbacks(editor.loadingCircle.largeEditUiWatchdog);
      editor.caret.mainHandler.postDelayed(editor.loadingCircle.largeEditUiWatchdog, 30_000);
      CursorTarget target = computeCursorAfterInsert(editor.cursor.cursorLine, editor.cursor.cursorChar, text);
      final File inFile = editor.fileIO.sourceFile;
      rewriteReplaceRangeAsync(
          opToken, inFile, editor.cursor.cursorLine, editor.cursor.cursorChar, editor.cursor.cursorLine, editor.cursor.cursorChar, text, target, true);
      editor.autoCompletion.updateSuggestion();
      lineCountDelta += countNewlines(text);
      if (text.length() <= UNDO_TEXT_LIMIT) {
        EditOp op = new EditOp();
        op.startLine = beforeLine;
        op.startChar = beforeChar;
        op.endLine = beforeLine;
        op.endChar = beforeChar;
        op.removedText = "";
        op.insertedText = text;
        op.insertedEndLine = target.line;
        op.insertedEndChar = target.ch;
        op.cursorLineBefore = beforeLine;
        op.cursorCharBefore = beforeChar;
        op.cursorLineAfter = target.line;
        op.cursorCharAfter = target.ch;
        op.timestamp = System.currentTimeMillis();
        recordEdit(op);
      }
      return;
    }

    String[] parts = text.split("\n", -1);
    editor.fileIO.ensureLineInWindow(editor.cursor.cursorLine, true);
    if (editor.fileIO.isWindowLoading
        && (editor.cursor.cursorLine < editor.textRender.windowStartLine || editor.cursor.cursorLine >= editor.textRender.windowStartLine + editor.textRender.linesWindow.size())) {
      editor.post(() -> insertTextAtCursor(text));
      return;
    }

    int local = editor.cursor.cursorLine - editor.textRender.windowStartLine;
    if (local < 0 || local >= editor.textRender.linesWindow.size()) {
      synchronized (editor.textRender.linesWindow) {
        if (editor.textRender.linesWindow.isEmpty()) {
          editor.textRender.linesWindow.add("");
          local = 0;
        } else local = Math.max(0, Math.min(local, editor.textRender.linesWindow.size() - 1));
      }
    }

    synchronized (editor.textRender.linesWindow) {
      int oldLineCount = editor.getLinesCount();
      String base = editor.getLineFromWindowLocal(local);
      if (base == null) base = "";
      int pos = Math.max(0, Math.min(editor.cursor.cursorChar, base.length()));
      String left = base.substring(0, pos);
      String right = base.substring(pos);

      if (parts.length == 1) {
        String modified = left + parts[0] + right;
        editor.updateLocalLine(local, modified);
        editor.textRender.modifiedLines.put(editor.cursor.cursorLine, modified);
        if (editor.codeFold.isCodeFoldingEnabled) {
          if (editor.containsBracketChars(parts[0])) {
            editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine);
          }
          editor.codeFold.adjustFoldRangeForLineEdit(editor.cursor.cursorLine, pos, parts[0].length(), 0);
        }
        editor.textRender.lineWidthCache.remove(editor.cursor.cursorLine);
        editor.cursor.cursorChar += parts[0].length();
      } else {
        editor.textRender.lineWidthCache.clear();
        String firstLine = left + parts[0];
        editor.updateLocalLine(local, firstLine);
        editor.textRender.modifiedLines.put(editor.cursor.cursorLine, firstLine);
        if (editor.codeFold.isCodeFoldingEnabled && editor.containsBracketChars(parts[0])) {
          editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine);
        }

        java.util.List<String> linesToInsert = new java.util.ArrayList<>();
        for (int p = 1; p < parts.length - 1; p++) linesToInsert.add(parts[p]);

        String lastPart = parts[parts.length - 1];
        linesToInsert.add(lastPart + right);

        if (!linesToInsert.isEmpty()) editor.textRender.linesWindow.addAll(local + 1, linesToInsert);
        for (int i = 0; i < linesToInsert.size(); i++) {
          editor.textRender.modifiedLines.put(editor.cursor.cursorLine + 1 + i, linesToInsert.get(i));
          if (editor.codeFold.isCodeFoldingEnabled && editor.containsBracketChars(linesToInsert.get(i))) {
            editor.codeFold.invalidateFoldRangeForLine(editor.cursor.cursorLine + 1 + i);
          }
        }

        editor.cursor.cursorLine += (parts.length - 1);
        editor.cursor.cursorChar = lastPart.length();
        lineCountDelta += (parts.length - 1);
      }

      int newLineCount = editor.getLinesCount();
      if (editor.lineNumber.showLineNumbers
          && oldLineCount > 0
          && String.valueOf(oldLineCount).length() != String.valueOf(newLineCount).length()) {
        editor.requestLayout();
      }
      if (parts.length > 1) {
        editor.wordWrap.onLineCountChanged();
      }

      editor.recalculateMaxLineWidth();
      editor.keepCursorVisibleHorizontally();
      editor.caret.resetBlink();
      editor.invalidate();
    }
    editor.autoCompletion.updateSuggestion();

    EditOp op = new EditOp();
    op.startLine = beforeLine;
    op.startChar = beforeChar;
    op.endLine = beforeLine;
    op.endChar = beforeChar;
    op.removedText = "";
    op.insertedText = text;
    CursorTarget insertedEnd = computeCursorAfterInsert(beforeLine, beforeChar, text);
    op.insertedEndLine = insertedEnd.line;
    op.insertedEndChar = insertedEnd.ch;
    op.cursorLineBefore = beforeLine;
    op.cursorCharBefore = beforeChar;
    op.cursorLineAfter = editor.cursor.cursorLine;
    op.cursorCharAfter = editor.cursor.cursorChar;
    op.timestamp = System.currentTimeMillis();
    recordEdit(op);
  }
}
