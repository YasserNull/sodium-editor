package com.yn.simplesodiumeditor;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.view.FilePickerDialog;
import android.util.SparseIntArray;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.io.EditOp;
import com.yn.sodiumeditor.ui.Theme;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

  private SodiumEditor editor;
  private TextView fileNameText;
  private TextView filePathText;
  private ImageButton overflowBtn;
  private ImageButton saveBtn;
  private ImageButton undoBtn;
  private ImageButton redoBtn;
  private ActivityResultLauncher<Intent> openFileLauncher;
  private ActivityResultLauncher<Intent> manageStorageLauncher;
  private ActivityResultLauncher<String[]> requestPermissionLauncher;
  private int currentScrollMode = 2;
  private Uri pendingUri = null;
  private boolean hasFileOpened = false;
  private LinearLayout tabContainer;
  private HorizontalScrollView tabScroll;
  private String currentTheme;
  private final java.util.List<FileTab> openTabs = new java.util.ArrayList<>();
  private int currentTabIndex = -1;
  private final Handler dirtyHandler = new Handler();
  private boolean isSwitchingTab = false;
  private final Runnable dirtyChecker = new Runnable() {
    @Override
    public void run() {
      updateDirtyIndicator();
      updateToolbarButtons();
      dirtyHandler.postDelayed(this, 150);
    }
  };

  private static class FileTab {
    File file;
    String name;
    String path;
    // Editor state cache (in-memory, no reload from disk)
    ArrayDeque<EditOp> undoStack;
    ArrayDeque<EditOp> redoStack;
    ArrayDeque<EditOp> pendingEdits;
    ArrayDeque<EditOp> pendingRedo;
    int lineCountDelta;
    long lastEditTimestamp;
    int cursorLine, cursorChar;
    float scrollX, scrollY;
    int selStartLine, selStartChar, selEndLine, selEndChar;
    boolean hasSelection, selecting;
    // Window content cache
    ArrayList<String> linesWindow;
    int windowStartLine;
    HashMap<Integer, String> modifiedLines;
    SparseIntArray streamedLineLengths;
    SparseIntArray streamedLineSliceStarts;
    boolean isEof, isFileCleared, isIndexReady;
    Charset fileCharset;
    long[] lineOffsets;
    FileTab(File file, String name, String path) {
      this.file = file;
      this.name = name;
      this.path = path;
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    String theme =
        PreferenceManager.getDefaultSharedPreferences(this).getString("theme", "light");
    this.currentTheme = theme;
    applyTheme(theme);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    editor = findViewById(R.id.editor);
    fileNameText = findViewById(R.id.fileNameText);
    filePathText = findViewById(R.id.filePathText);
    overflowBtn = findViewById(R.id.overflowBtn);
    saveBtn = findViewById(R.id.saveBtn);
    undoBtn = findViewById(R.id.undoBtn);
    redoBtn = findViewById(R.id.redoBtn);
    tabContainer = findViewById(R.id.tabContainer);
    tabScroll = findViewById(R.id.tabScroll);

    applyEditorColors(theme);
    styleSystemBars(theme);
    setTabBarColor(theme);

    currentScrollMode =
        Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString("scroll_mode", "2"));
    editor.scroll.setScrollMode(currentScrollMode);

    manageStorageLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                  if (pendingUri != null) {
                    loadUriIntoEditor(pendingUri);
                    pendingUri = null;
                  } else {
                    Toast.makeText(this, "تم منح صلاحية الوصول للملفات", Toast.LENGTH_SHORT).show();
                  }
                } else {
                  Toast.makeText(this, "لم يتم منح صلاحية الوصول للملفات", Toast.LENGTH_SHORT)
                      .show();
                }
              }
            });

    requestPermissionLauncher =
        registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            isGranted -> {
              boolean allGranted = true;
              for (Boolean granted : isGranted.values()) {
                if (!granted) {
                  allGranted = false;
                  break;
                }
              }
              if (allGranted) {
                if (pendingUri != null) {
                  loadUriIntoEditor(pendingUri);
                  pendingUri = null;
                } else {
                  openFilePicker();
                }
              } else {
                Toast.makeText(this, "يجب منح الصلاحيات للوصول للملفات", Toast.LENGTH_SHORT).show();
              }
            });

    openFileLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
              Uri uri = result.getData().getData();
              if (uri == null) return;
              loadUriIntoEditor(uri);
            });

    overflowBtn.setOnClickListener(this::showOverflowMenu);
    saveBtn.setOnClickListener(v -> saveFile());
    undoBtn.setOnClickListener(v -> undo());
    redoBtn.setOnClickListener(v -> redo());

    clearLogcat();
    checkPermissionsAndStart();

    updateToolbarButtons();
    dirtyHandler.post(dirtyChecker);

    if (openTabs.isEmpty()) {
      createTempFileTab();
    }
  }

  private void showOverflowMenu(View anchor) {
    PopupMenu popup = new PopupMenu(this, anchor);
    popup.getMenuInflater().inflate(R.menu.popup_menu, popup.getMenu());
    popup.setOnMenuItemClickListener(this::onOverflowMenuItemClick);
    popup.show();
  }

  private boolean onOverflowMenuItemClick(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.action_new) {
      createTempFileTab();
      return true;
    } else if (id == R.id.action_open) {
      openFilePicker();
      return true;
    } else if (id == R.id.action_close) {
      closeCurrentFile();
      return true;
    } else if (id == R.id.action_settings) {
      openSettings();
      return true;
    } else if (id == R.id.action_log) {
      showLogDialog();
      return true;
    }
    return false;
  }

  private void createTempFileTab() {
    new Thread(
            () -> {
              try {
                File tmpDir = new File(getCacheDir(), "tmp");
                tmpDir.mkdirs();
                File tmpFile = new File(tmpDir, "untitled_" + System.currentTimeMillis() + ".txt");
                FileOutputStream fos = new FileOutputStream(tmpFile);
                fos.write("".getBytes());
                fos.close();
                File finalTmp = tmpFile;
                runOnUiThread(() -> addTab(finalTmp, finalTmp.getName(), finalTmp.getAbsolutePath()));
              } catch (Exception e) {
                runOnUiThread(
                    () ->
                        Toast.makeText(this, "Failed to create temp file", Toast.LENGTH_SHORT)
                            .show());
              }
            })
        .start();
  }

  private void saveFile() {
    editor.editOperators.applyPendingEditsToFileAsync(
        () ->
            runOnUiThread(
                () -> {
                  String name = editor.fileIO.sourceFile != null ? editor.fileIO.sourceFile.getName() : "file";
                  updateDirtyIndicator();
                  Toast.makeText(this, "Saved: " + name, Toast.LENGTH_SHORT).show();
                }));
  }

  private void undo() {
    if (editor.editOperators.canUndo()) {
      editor.editOperators.undo();
      updateDirtyIndicator();
    } else {
      Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
    }
  }

  private void redo() {
    if (editor.editOperators.canRedo()) {
      editor.editOperators.redo();
      updateDirtyIndicator();
    } else {
      Toast.makeText(this, "Nothing to redo", Toast.LENGTH_SHORT).show();
    }
  }

  private void addTab(File file, String name, String path) {
    FileTab tab = new FileTab(file, name, path);
    openTabs.add(tab);
    int index = openTabs.size() - 1;
    tabContainer.addView(buildTabView(index));
    switchToTab(index);
  }

  private void switchToTab(int index) {
    if (index < 0 || index >= openTabs.size() || isSwitchingTab) return;
    isSwitchingTab = true;
    final int targetIndex = index;
    flushAndSaveCurrentTabState(() -> {
      doSwitchToTab(targetIndex);
      isSwitchingTab = false;
    });
  }

  private void doSwitchToTab(int index) {
    editor.caret.isCursorVisible = false;
    FileTab tab = openTabs.get(index);
    if (tab.linesWindow != null) {
      restoreEditorState(tab);
    } else {
      editor.fileIO.loadFromFile(tab.file);
    }
    currentTabIndex = index;
    updateDirtyIndicator();
    updateTabStyles();
    editor.caret.isCursorVisible = true;
    editor.invalidate();
  }

  private void removeTab(int index) {
    if (index < 0 || index >= openTabs.size() || isSwitchingTab) return;
    boolean removingCurrent = index == currentTabIndex;
    if (removingCurrent) {
      isSwitchingTab = true;
      flushAndSaveCurrentTabState(() -> {
        openTabs.remove(index);
        tabContainer.removeViewAt(index);
        if (openTabs.isEmpty()) {
          currentTabIndex = -1;
          editor.fileIO.clearContent();
          updateDirtyIndicator();
          createTempFileTab();
        } else {
          int newIndex = Math.min(index, openTabs.size() - 1);
          doSwitchToTab(newIndex);
        }
        isSwitchingTab = false;
      });
    } else {
      openTabs.remove(index);
      tabContainer.removeViewAt(index);
      if (currentTabIndex > index) currentTabIndex--;
      updateTabStyles();
    }
  }

  private int findTabByPath(String path) {
    for (int i = 0; i < openTabs.size(); i++) {
      if (openTabs.get(i).path.equals(path)) return i;
    }
    return -1;
  }

  private View buildTabView(int index) {
    FileTab tab = openTabs.get(index);
    TextView nameView = new TextView(this);
    nameView.setText(tab.name);
    nameView.setTextSize(13);
    nameView.setPadding(12, 6, 12, 6);
    nameView.setTextColor(getTabTextColor());

    GradientDrawable bg = new GradientDrawable();
    bg.setShape(GradientDrawable.RECTANGLE);
    bg.setCornerRadius(4);
    bg.setColor(getInactiveTabFill());
    bg.setStroke(1, getInactiveTabStroke());
    nameView.setBackground(bg);
    nameView.setClickable(true);
    nameView.setFocusable(true);

    int idx = index;
    nameView.setOnClickListener(v -> switchToTab(idx));

    return nameView;
  }

  private void updateTabStyles() {
    for (int i = 0; i < tabContainer.getChildCount(); i++) {
      View child = tabContainer.getChildAt(i);
      TextView tabText = (TextView) child;
      GradientDrawable bg = (GradientDrawable) child.getBackground();
      if (i == currentTabIndex) {
        bg.setColor(getActiveTabFill());
        bg.setStroke(2, getActiveTabStroke());
      } else {
        bg.setColor(getInactiveTabFill());
        bg.setStroke(1, getInactiveTabStroke());
        tabText.setTextColor(getTabTextColor());
      }
    }
  }

  private int getTabTextColor() {
    switch (currentTheme) {
      case "dark":
      case "black":
        return Color.WHITE;
      default:
        return Color.BLACK;
    }
  }

  private int getActiveTabFill() {
    switch (currentTheme) {
      case "dark": return 0xFF546E7A;
      case "black": return 0xFF424242;
      default: return 0xFFBBDEFB;
    }
  }

  private int getActiveTabStroke() {
    switch (currentTheme) {
      case "dark": return 0xFF80CBC4;
      case "black": return 0xFF80CBC4;
      default: return 0xFF1976D2;
    }
  }

  private int getInactiveTabFill() {
    switch (currentTheme) {
      case "dark": return 0xFF455A64;
      case "black": return 0xFF2C2C2C;
      default: return Color.LTGRAY;
    }
  }

  private int getInactiveTabStroke() {
    switch (currentTheme) {
      case "dark": return 0xFF37474F;
      case "black": return 0xFF1A1A1A;
      default: return Color.GRAY;
    }
  }

  private void updateToolbarButtons() {
    boolean canUndo = editor.editOperators.canUndo();
    boolean canRedo = editor.editOperators.canRedo();
    boolean canSave = editor.editOperators.getPendingEditsCount() > 0;
    undoBtn.setColorFilter(canUndo ? Color.WHITE : 0xFF999999, PorterDuff.Mode.SRC_IN);
    redoBtn.setColorFilter(canRedo ? Color.WHITE : 0xFF999999, PorterDuff.Mode.SRC_IN);
    saveBtn.setColorFilter(canSave ? Color.WHITE : 0xFF999999, PorterDuff.Mode.SRC_IN);
  }

  private boolean isDirty() {
    return editor.editOperators.getPendingEditsCount() > 0;
  }

  private void updateDirtyIndicator() {
    boolean dirty = isDirty();
    FileTab tab = currentTabIndex >= 0 && currentTabIndex < openTabs.size()
        ? openTabs.get(currentTabIndex) : null;
    String title = tab != null ? tab.name : "Sodium Editor";
    String path = tab != null ? tab.path : "";
    if (dirty && tab != null) {
      title = "* " + title;
    }
    fileNameText.setText(title);
    filePathText.setText(path);
    if (!dirty && tab != null) {
      filePathText.setVisibility(View.VISIBLE);
    } else if (tab == null) {
      filePathText.setVisibility(View.GONE);
    }
    updateToolbarButtons();
  }

  private void closeCurrentFile() {
    if (currentTabIndex < 0 || currentTabIndex >= openTabs.size()) return;
    removeTab(currentTabIndex);
  }

  private void flushAndSaveCurrentTabState(Runnable onDone) {
    if (currentTabIndex < 0 || currentTabIndex >= openTabs.size()) {
      onDone.run();
      return;
    }
    FileTab tab = openTabs.get(currentTabIndex);
    if (editor.editOperators.getPendingEditsCount() > 0) {
      editor.editOperators.applyPendingEditsToFileAsync(() ->
          runOnUiThread(() -> {
            captureStateToTab(tab);
            onDone.run();
          })
      );
    } else {
      captureStateToTab(tab);
      onDone.run();
    }
  }

  private void captureStateToTab(FileTab tab) {
    tab.undoStack = new ArrayDeque<>(editor.editOperators.undoStack);
    tab.redoStack = new ArrayDeque<>(editor.editOperators.redoStack);
    tab.pendingEdits = new ArrayDeque<>(editor.editOperators.pendingEdits);
    tab.pendingRedo = new ArrayDeque<>(editor.editOperators.pendingRedo);
    tab.lineCountDelta = editor.editOperators.lineCountDelta;
    tab.lastEditTimestamp = editor.editOperators.lastEditTimestamp;
    tab.cursorLine = editor.cursor.getLine();
    tab.cursorChar = editor.cursor.getChar();
    tab.scrollX = editor.scroll.scrollX;
    tab.scrollY = editor.scroll.scrollY;
    tab.selStartLine = editor.selection.selStartLine;
    tab.selStartChar = editor.selection.selStartChar;
    tab.selEndLine = editor.selection.selEndLine;
    tab.selEndChar = editor.selection.selEndChar;
    tab.hasSelection = editor.selection.hasSelection;
    tab.selecting = editor.selection.selecting;
    // Window content cache
    synchronized (editor.windowRender.linesWindow) {
      tab.linesWindow = new ArrayList<>(editor.windowRender.linesWindow);
    }
    tab.windowStartLine = editor.windowRender.windowStartLine;
    synchronized (editor.windowRender.modifiedLines) {
      tab.modifiedLines = new HashMap<>(editor.windowRender.modifiedLines);
    }
    synchronized (editor.windowRender.streamedLinesLock) {
      tab.streamedLineLengths = editor.windowRender.streamedLineLengths.clone();
      tab.streamedLineSliceStarts = editor.windowRender.streamedLineSliceStarts.clone();
    }
    tab.isEof = editor.fileIO.isEof;
    tab.isFileCleared = editor.fileIO.isFileCleared;
    tab.fileCharset = editor.fileIO.fileCharset;
    tab.isIndexReady = editor.fileIO.isIndexReady;
    synchronized (editor.fileIO.lineOffsetsLock) {
      tab.lineOffsets = editor.fileIO.lineOffsets.clone();
    }
  }

  private void restoreEditorState(FileTab tab) {
    editor.fileIO.invalidatePendingIOForEdit();
    editor.fileIO.sourceFile = tab.file;
    editor.fileIO.isEof = tab.isEof;
    editor.fileIO.isFileCleared = tab.isFileCleared;
    editor.fileIO.fileCharset = tab.fileCharset;
    editor.fileIO.isIndexBuilding = false;
    editor.fileIO.isIndexReady = tab.isIndexReady;
    synchronized (editor.fileIO.lineOffsetsLock) {
      editor.fileIO.lineOffsets = tab.lineOffsets.clone();
    }

    editor.windowRender.windowStartLine = tab.windowStartLine;
    synchronized (editor.windowRender.linesWindow) {
      editor.windowRender.linesWindow.clear();
      editor.windowRender.linesWindow.addAll(tab.linesWindow);
    }
    synchronized (editor.windowRender.modifiedLines) {
      editor.windowRender.modifiedLines.clear();
      editor.windowRender.modifiedLines.putAll(tab.modifiedLines);
    }
    synchronized (editor.windowRender.streamedLinesLock) {
      editor.windowRender.streamedLineLengths.clear();
      editor.windowRender.streamedLineSliceStarts.clear();
      for (int i = 0; i < tab.streamedLineLengths.size(); i++) {
        int key = tab.streamedLineLengths.keyAt(i);
        editor.windowRender.streamedLineLengths.put(key, tab.streamedLineLengths.valueAt(i));
      }
      for (int i = 0; i < tab.streamedLineSliceStarts.size(); i++) {
        int key = tab.streamedLineSliceStarts.keyAt(i);
        editor.windowRender.streamedLineSliceStarts.put(key, tab.streamedLineSliceStarts.valueAt(i));
      }
    }

    editor.windowRender.currentMaxWindowLineWidth = 0f;
    editor.windowRender.globalMaxLineWidth = 0f;
    editor.scroll.maxLineWidthForScroll = 0f;
    editor.windowRender.clearStreamedLineCaches();
    editor.highlite.clearHighlightCaches();
    editor.wordWrap.wrapMetricsReady = false;
    editor.wordWrap.wrapLineCounts = null;
    editor.wordWrap.wrapLinePrefix = null;
    editor.wordWrap.totalWrapVisualLines = 0;
    editor.wordWrap.wrapPrefixValidUpToLine = -1;
    editor.lineNumber.invalidateLineNumberCache();
    synchronized (editor.fileIO.directLineCache) { editor.fileIO.directLineCache.clear(); }
    editor.bracketCache.clear();
    editor.autoCompletion.clearActiveSuggestion();

    // Abort any ongoing scroll animation from previous tab
    if (!editor.scroll.scroller.isFinished()) editor.scroll.scroller.abortAnimation();
    if (editor.scroll.flingStopAnimator != null) {
      editor.scroll.flingStopAnimator.cancel();
      editor.scroll.flingStopAnimator = null;
    }
    editor.scroll.scrollerIsScrolling = false;

    // Set scroll fields directly (avoid keepCursorVisibleHorizontally side effects)
    editor.scroll.scrollX = tab.scrollX;
    editor.scroll.scrollY = tab.scrollY;
    editor.scroll.clampScrollX();
    editor.scroll.clampScrollY();
    // Set cursor fields directly — avoid resetBlink/keepCursorVisibleHorizontally side effects
    editor.cursor.cursorLine = tab.cursorLine;
    editor.cursor.cursorChar = tab.cursorChar;
    // Suppress current-line-slide animation — it would animate from old tab's line
    editor.currentLineHighlight.animation.resetToTarget();
    // Suppress cursor-slide animation — use raw document coordinates until first real move
    editor.cursorAnimation.cancelAnimation();
    editor.cursorAnimation.cursorAnimValid = false;
    if (tab.hasSelection) {
      editor.selection.selStartLine = tab.selStartLine;
      editor.selection.selStartChar = tab.selStartChar;
      editor.selection.selEndLine = tab.selEndLine;
      editor.selection.selEndChar = tab.selEndChar;
      editor.selection.hasSelection = true;
      editor.selection.selecting = true;
    } else {
      editor.selection.clearSelection();
    }

    editor.editOperators.clearUndoRedoHistory();
    editor.editOperators.undoStack.addAll(tab.undoStack);
    editor.editOperators.redoStack.addAll(tab.redoStack);
    editor.editOperators.pendingEdits.addAll(tab.pendingEdits);
    editor.editOperators.pendingRedo.addAll(tab.pendingRedo);
    editor.editOperators.lineCountDelta = tab.lineCountDelta;
    editor.editOperators.lastEditTimestamp = tab.lastEditTimestamp;

    editor.binaryRender.applyBinaryFileFeaturePolicy(editor.fileIO.metadata.isBinaryFile(tab.file));
    editor.loadingCircle.isInitialFileOpenLoading = false;
    editor.view.setDisable(false);
    editor.loadingCircle.showLoadingCircle(false);

    editor.caret.stopBlink();
    if (!tab.isIndexReady) {
      editor.fileIO.ioHandler.post(() -> editor.fileIO.indexer.buildFileIndex());
    }
    editor.requestLayout();
    editor.invalidate();
  }

  private void openSettings() {
    LinearLayout toolbar = findViewById(R.id.toolbar);
    if (toolbar != null) toolbar.setVisibility(View.GONE);
    tabScroll.setVisibility(View.GONE);
    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.editorContainer, new SettingsFragment())
        .addToBackStack("settings")
        .commit();
  }

  public void onScrollModeChanged(int mode) {
    currentScrollMode = mode;
    editor.scroll.setScrollMode(mode);
  }

  private void applyEditorColors(String themeValue) {
    Theme theme;
    switch (themeValue) {
      case "dark":
        theme = Theme.dark();
        break;
      case "black":
        theme = Theme.black();
        break;
      default:
        theme = Theme.white();
        break;
    }
    theme.apply(editor);
  }

  public void onThemeChanged() {
    recreate();
  }

  private void applyTheme(String themeValue) {
    switch (themeValue) {
      case "dark":
        setTheme(R.style.AppTheme_Dark);
        break;
      case "black":
        setTheme(R.style.AppTheme_Black);
        break;
      default:
        setTheme(R.style.AppTheme_White);
        break;
    }
  }

  private void setTabBarColor(String themeValue) {
    int color;
    switch (themeValue) {
      case "dark": color = 0xFF455A64; break;
      case "black": color = 0xFF1A1A1A; break;
      default: color = 0xFFE0E0E0; break;
    }
    tabScroll.setBackgroundColor(color);
  }

  private void styleSystemBars(String themeValue) {
    if ("light".equals(themeValue)) {
      getWindow().setStatusBarColor(0xFF1565C0);
      getWindow().setNavigationBarColor(0xFFFFFFFF);
      if (Build.VERSION.SDK_INT >= 23) {
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
      }
    } else if ("dark".equals(themeValue)) {
      getWindow().setStatusBarColor(0xFF263238);
      getWindow().setNavigationBarColor(0xFF37474F);
    } else {
      getWindow().setStatusBarColor(0xFF000000);
      getWindow().setNavigationBarColor(0xFF000000);
    }
  }

  private void updateFileTitle(String name, String path) {
    fileNameText.setText(name != null ? name : "Sodium Editor");
    filePathText.setText(path != null ? path : "");
    filePathText.setVisibility(path != null ? View.VISIBLE : View.GONE);
  }

  private void checkPermissionsAndStart() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (!Environment.isExternalStorageManager()) {
        new AlertDialog.Builder(this)
            .setTitle("صلاحية الوصول للملفات")
            .setMessage(
                "يحتاج هذا التطبيق لصلاحية الوصول لجميع الملفات ليتمكن من فتح وتحرير الملفات"
                    + " البرمجية.")
            .setPositiveButton(
                "منح",
                (dialog, which) -> {
                  try {
                    Intent intent =
                        new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    manageStorageLauncher.launch(intent);
                  } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    manageStorageLauncher.launch(intent);
                  }
                })
            .setNegativeButton("إلغاء", null)
            .show();
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
          != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestPermissionLauncher.launch(
            new String[] {
              android.Manifest.permission.READ_EXTERNAL_STORAGE,
              android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            });
      }
    }
  }

  private void openFilePicker() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (!Environment.isExternalStorageManager()) {
        checkPermissionsAndStart();
        return;
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
          != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        checkPermissionsAndStart();
        return;
      }
    }

    DialogProperties properties = new DialogProperties();
    properties.selection_mode = DialogConfigs.SINGLE_MODE;
    properties.selection_type = DialogConfigs.FILE_SELECT;
    properties.root = Environment.getExternalStorageDirectory();
    properties.error_dir = new File(DialogConfigs.DEFAULT_DIR);
    properties.offset = new File(DialogConfigs.DEFAULT_DIR);

    FilePickerDialog dialog = new FilePickerDialog(this, properties);
    dialog.setDialogSelectionListener(
        files -> {
          if (files != null && files.length > 0) {
            File file = new File(files[0]);
            int existing = findTabByPath(file.getAbsolutePath());
            if (existing >= 0) {
              switchToTab(existing);
            } else {
              addTab(file, file.getName(), file.getAbsolutePath());
            }
          }
        });
    dialog.show();
  }

  private void clearLogcat() {
    new Thread(
            () -> {
              try {
                ProcessBuilder pb = new ProcessBuilder("logcat", "-c");
                pb.start().waitFor();
              } catch (Exception ignored) {
              }
            })
        .start();
  }

  private void showLogDialog() {
    android.widget.TextView textView = new android.widget.TextView(this);
    textView.setPadding(48, 48, 48, 48);
    textView.setTextSize(12f);
    textView.setMaxLines(30);
    textView.setMovementMethod(new android.text.method.ScrollingMovementMethod());
    textView.setText("Loading logs...");

    android.widget.LinearLayout buttonLayout = new android.widget.LinearLayout(this);
    buttonLayout.setPadding(48, 32, 48, 48);
    buttonLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);

    android.widget.Button tagsButton = new android.widget.Button(this);
    tagsButton.setText("Tags");
    tagsButton.setLayoutParams(
        new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    tagsButton.setPadding(16, 24, 16, 24);

    android.widget.Button clearButton = new android.widget.Button(this);
    clearButton.setText("Clear");
    clearButton.setLayoutParams(
        new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    clearButton.setPadding(16, 24, 16, 24);

    android.widget.Button copyButton = new android.widget.Button(this);
    copyButton.setText("نسخ");
    copyButton.setLayoutParams(
        new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    copyButton.setPadding(16, 24, 16, 24);

    android.widget.Button closeButton = new android.widget.Button(this);
    closeButton.setText("إغلاق");
    closeButton.setLayoutParams(
        new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    closeButton.setPadding(16, 24, 16, 24);

    buttonLayout.addView(tagsButton);
    buttonLayout.addView(clearButton);
    buttonLayout.addView(copyButton);
    buttonLayout.addView(closeButton);

    android.widget.LinearLayout mainLayout = new android.widget.LinearLayout(this);
    mainLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
    mainLayout.addView(
        textView,
        new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            1f));
    mainLayout.addView(buttonLayout);

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Log");
    builder.setView(mainLayout);

    AlertDialog dialog = builder.create();

    final String[] defaultTags = {
    "CursorTarget","ImeScanner","Ime","SodiumInputConnection","ImeContext","DragSelectionHandler",
"GestureHandler",
"OnDoubleTap",
"OnDown",
"OnFling",
"OnKeyDown",
"OnLongPress",
"OnScroll",
"OnSingleTapUp",
"OnTouch",
"PopupInteractionHandler",
"ScrollBarHandler"
    };
    final String[] currentTags = defaultTags.clone();

    tagsButton.setOnClickListener(
        v -> {
          android.widget.EditText tagInput = new android.widget.EditText(this);
          tagInput.setPadding(48, 48, 48, 48);
          tagInput.setText(String.join("|", currentTags));
          tagInput.setHint("Tag1|Tag2|Tag3");

          AlertDialog.Builder tagsBuilder = new AlertDialog.Builder(this);
          tagsBuilder.setTitle("Log Tags");
          tagsBuilder.setMessage("Enter tags separated by | (e.g., SodiumEditor|TestTag)");
          tagsBuilder.setView(tagInput);

          tagsBuilder.setPositiveButton(
              "OK",
              (dlg, which) -> {
                String input = tagInput.getText().toString().trim();
                if (input.isEmpty()) {
                  currentTags[0] = "*";
                } else {
                  currentTags[0] = input;
                }
                loadLogsIntoTextView(textView, currentTags[0]);
              });

          tagsBuilder.setNegativeButton("Cancel", (dlg, which) -> dlg.dismiss());
          tagsBuilder.show();
        });

    loadLogsIntoTextView(textView, String.join("|", currentTags));

    clearButton.setOnClickListener(
        v -> {
          clearLogcat();
          textView.setText("Log cleared");
          Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show();
        });

    copyButton.setOnClickListener(
        v -> {
          ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
          if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("log", textView.getText().toString()));
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
          }
        });

    closeButton.setOnClickListener(v -> dialog.dismiss());

    dialog.show();
  }

  private void loadLogsIntoTextView(android.widget.TextView textView, String tagsFilter) {
    textView.setText("Loading logs...");
    new Thread(
            () -> {
              String output;
              try {
                int pid = Process.myPid();
                java.util.List<String> cmd = new java.util.ArrayList<>();
                cmd.add("logcat");
                cmd.add("-d");
                cmd.add("-v");
                cmd.add("time");
                cmd.add("--pid=" + pid);

                String[] tags = tagsFilter.split("\\|");
                for (String tag : tags) {
                  tag = tag.trim();
                  if (!tag.isEmpty()) {
                    cmd.add(tag + ":V");
                  }
                }
                if (tags.length == 0 || (tags.length == 1 && tags[0].trim().isEmpty())) {
                  cmd.add("*:V");
                } else {
                  cmd.add("*:S");
                }

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                java.lang.Process proc = pb.start();
                StringBuilder sb = new StringBuilder();
                try (java.io.InputStream in = proc.getInputStream()) {
                  byte[] buf = new byte[8192];
                  int read;
                  while ((read = in.read(buf)) != -1) {
                    sb.append(new String(buf, 0, read));
                    if (sb.length() > 500_000) break;
                  }
                }
                proc.waitFor();
                output = sb.toString();
                if (output.isEmpty()) output = "No log output for tags: " + tagsFilter;
              } catch (Exception e) {
                output = "Failed to read logs: " + e.getMessage();
              }
              String finalOutput = output;
              runOnUiThread(
                  () -> {
                    textView.setText(finalOutput);
                    textView.scrollTo(0, 0);
                  });
            })
        .start();
  }

  private void loadUriIntoEditor(Uri uri) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (!Environment.isExternalStorageManager()) {
        pendingUri = uri;
        checkPermissionsAndStart();
        return;
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
          != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        pendingUri = uri;
        requestPermissionLauncher.launch(
            new String[] {
              android.Manifest.permission.READ_EXTERNAL_STORAGE,
              android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            });
        return;
      }
    }

    clearLogcat();

    new Thread(
            () -> {
              try {
                String path = getPathFromUri(uri);
                if (path == null) {
                  runOnUiThread(
                      () ->
                          Toast.makeText(this, "تعذر الحصول على مسار الملف", Toast.LENGTH_SHORT)
                              .show());
                  return;
                }

                File file = new File(path);
                if (!file.exists()) {
                  runOnUiThread(
                      () ->
                          Toast.makeText(this, "الملف غير موجود: " + path, Toast.LENGTH_SHORT)
                              .show());
                  return;
                }

                long size = file.length();
                runOnUiThread(
                    () -> {
                      int existing = findTabByPath(file.getAbsolutePath());
                      if (existing >= 0) {
                        switchToTab(existing);
                      } else {
                        addTab(file, file.getName(), file.getAbsolutePath());
                      }
                    });
              } catch (Exception e) {
                runOnUiThread(
                    () ->
                        Toast.makeText(
                                this, "تعذر فتح الملف: " + e.getMessage(), Toast.LENGTH_SHORT)
                            .show());
              }
            })
        .start();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    dirtyHandler.removeCallbacks(dirtyChecker);
  }

  @Override
  public void onBackPressed() {
    Fragment f = getSupportFragmentManager().findFragmentById(R.id.editorContainer);
    if (f instanceof SettingsFragment) {
      getSupportFragmentManager().popBackStack();
      LinearLayout toolbar = findViewById(R.id.toolbar);
      if (toolbar != null) toolbar.setVisibility(View.VISIBLE);
      tabScroll.setVisibility(View.VISIBLE);
      return;
    }
    super.onBackPressed();
  }

  private String getPathFromUri(Uri uri) {
    if (uri == null) return null;

    if ("file".equals(uri.getScheme())) {
      return uri.getPath();
    }

    if ("content".equals(uri.getScheme())) {
      try (Cursor cursor =
          getContentResolver()
              .query(
                  uri,
                  new String[] {android.provider.MediaStore.Files.FileColumns.DATA},
                  null,
                  null,
                  null)) {
        if (cursor != null && cursor.moveToFirst()) {
          int idx = cursor.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DATA);
          if (idx >= 0) {
            return cursor.getString(idx);
          }
        }
      } catch (Exception ignored) {
      }

      try {
        String docId = android.provider.DocumentsContract.getDocumentId(uri);
        if (docId != null && docId.contains(":")) {
          String[] parts = docId.split(":");
          String type = parts[0];
          String id = parts[1];

          if ("primary".equalsIgnoreCase(type)) {
            return android.os.Environment.getExternalStorageDirectory() + "/" + id;
          }

          java.io.File[] dirs = getExternalFilesDirs(null);
          for (java.io.File dir : dirs) {
            if (dir != null) {
              String path =
                  dir.getAbsolutePath().replace("/Android/data/" + getPackageName() + "/files", "");
              if (dir.exists()) {
                java.io.File target = new java.io.File(path + "/" + id);
                if (target.exists()) {
                  return target.getAbsolutePath();
                }
              }
            }
          }
        }
      } catch (Exception ignored) {
      }
    }

    return uri.getPath();
  }

  private String queryDisplayName(Uri uri) {
    try (Cursor cursor =
        getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (index >= 0) {
          return cursor.getString(index);
        }
      }
    } catch (Exception ignored) {
    }
    return null;
  }
}
