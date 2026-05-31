package com.yn.sodiumeditordemo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import java.io.File;

public class TerminalActivity extends AppCompatActivity
    implements TerminalSessionClient, TerminalViewClient {

  private static final String TAG = "SodiumTerminal";
  private static final int TERMINAL_BACKGROUND_COLOR = Color.BLACK;
  private static TerminalSession sharedSession;

  private TerminalView terminalView;
  private TerminalSession terminalSession;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().setStatusBarColor(TERMINAL_BACKGROUND_COLOR);
    getWindow().setNavigationBarColor(TERMINAL_BACKGROUND_COLOR);

    terminalView = new TerminalView(this, null);
    terminalView.setTerminalViewClient(this);
    terminalView.setTextSize(14f * getResources().getDisplayMetrics().scaledDensity);
    terminalView.setFocusable(true);
    terminalView.setFocusableInTouchMode(true);
    terminalView.setBackgroundColor(TERMINAL_BACKGROUND_COLOR);

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(TERMINAL_BACKGROUND_COLOR);
    root.addView(
        terminalView,
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    setContentView(root);
    applySystemWindowInsets(root);

    terminalSession = getOrCreateSession();
    terminalSession.updateTerminalSessionClient(this);
    terminalView.attachSession(terminalSession);
    terminalView.requestFocus();
    terminalView.post(
        () -> {
          terminalView.updateSize();
          showKeyboard();
        });
  }

  @Override
  protected void onDestroy() {
    if (terminalSession != null) {
      terminalSession.updateTerminalSessionClient(new DetachedTerminalSessionClient());
      terminalSession = null;
    }
    super.onDestroy();
  }

  private TerminalSession getOrCreateSession() {
    if (sharedSession == null || !sharedSession.isRunning()) {
      sharedSession = createSession();
    }
    return sharedSession;
  }

  private TerminalSession createSession() {
    File cwd = getFilesDir();
    String shellPath = "/system/bin/sh";
    String[] args = new String[] {"sh"};
    String[] env =
        new String[] {
          "HOME=" + cwd.getAbsolutePath(),
          "PWD=" + cwd.getAbsolutePath(),
          "TMPDIR=" + getCacheDir().getAbsolutePath(),
          "PATH=/system/bin:/system/xbin",
          "TERM=xterm-256color",
          "COLORTERM=truecolor"
        };
    return new TerminalSession(shellPath, cwd.getAbsolutePath(), args, env, 2000, this);
  }

  private void showKeyboard() {
    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    if (imm != null) imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT);
  }

  private void applySystemWindowInsets(View root) {
    if (Build.VERSION.SDK_INT < 20 || root == null) return;
    int initialLeft = root.getPaddingLeft();
    int initialTop = root.getPaddingTop();
    int initialRight = root.getPaddingRight();
    int initialBottom = root.getPaddingBottom();
    root.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          view.setPadding(
              initialLeft + insets.getSystemWindowInsetLeft(),
              initialTop + insets.getSystemWindowInsetTop(),
              initialRight + insets.getSystemWindowInsetRight(),
              initialBottom + insets.getSystemWindowInsetBottom());
          return insets;
        });
    root.requestApplyInsets();
  }

  @Override
  public void onTextChanged(@NonNull TerminalSession changedSession) {
    terminalView.onScreenUpdated();
  }

  @Override
  public void onTitleChanged(@NonNull TerminalSession changedSession) {
    String title = changedSession.getTitle();
    setTitle(title == null || title.isEmpty() ? "Terminal" : title);
  }

  @Override
  public void onSessionFinished(@NonNull TerminalSession finishedSession) {
    terminalView.onScreenUpdated();
  }

  @Override
  public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("terminal", text));
  }

  @Override
  public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
    if (session == null) return;
    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null) return;
    CharSequence text = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
    if (text != null) session.write(text.toString());
  }

  @Override
  public void onBell(@NonNull TerminalSession session) {}

  @Override
  public void onColorsChanged(@NonNull TerminalSession session) {
    terminalView.invalidate();
  }

  @Override
  public void onTerminalCursorStateChange(boolean state) {
    terminalView.invalidate();
  }

  @Override
  public void setTerminalShellPid(@NonNull TerminalSession session, int pid) {}

  @Override
  public Integer getTerminalCursorStyle() {
    return null;
  }

  @Override
  public float onScale(float scale) {
    return Math.max(0.75f, Math.min(2.0f, scale));
  }

  @Override
  public void onSingleTapUp(MotionEvent e) {
    showKeyboard();
  }

  @Override
  public boolean shouldBackButtonBeMappedToEscape() {
    return false;
  }

  @Override
  public boolean shouldEnforceCharBasedInput() {
    return false;
  }

  @Override
  public boolean shouldUseCtrlSpaceWorkaround() {
    return false;
  }

  @Override
  public boolean isTerminalViewSelected() {
    return true;
  }

  @Override
  public void copyModeChanged(boolean copyMode) {}

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) {
    return false;
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent e) {
    return false;
  }

  @Override
  public boolean onLongPress(MotionEvent event) {
    return false;
  }

  @Override
  public boolean readControlKey() {
    return false;
  }

  @Override
  public boolean readAltKey() {
    return false;
  }

  @Override
  public boolean readShiftKey() {
    return false;
  }

  @Override
  public boolean readFnKey() {
    return false;
  }

  @Override
  public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
    return false;
  }

  @Override
  public void onEmulatorSet() {
    terminalView.setTerminalCursorBlinkerRate(600);
    terminalView.setTerminalCursorBlinkerState(true, true);
  }

  @Override
  public void logError(String tag, String message) {
    Log.e(tag, message);
  }

  @Override
  public void logWarn(String tag, String message) {
    Log.w(tag, message);
  }

  @Override
  public void logInfo(String tag, String message) {
    Log.i(tag, message);
  }

  @Override
  public void logDebug(String tag, String message) {
    Log.d(tag, message);
  }

  @Override
  public void logVerbose(String tag, String message) {
    Log.v(tag, message);
  }

  @Override
  public void logStackTraceWithMessage(String tag, String message, Exception e) {
    Log.e(tag, message, e);
  }

  @Override
  public void logStackTrace(String tag, Exception e) {
    Log.e(tag, "Terminal error", e);
  }

  private static class DetachedTerminalSessionClient implements TerminalSessionClient {
    @Override
    public void onTextChanged(@NonNull TerminalSession changedSession) {}

    @Override
    public void onTitleChanged(@NonNull TerminalSession changedSession) {}

    @Override
    public void onSessionFinished(@NonNull TerminalSession finishedSession) {
      if (sharedSession == finishedSession) sharedSession = null;
    }

    @Override
    public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {}

    @Override
    public void onPasteTextFromClipboard(@Nullable TerminalSession session) {}

    @Override
    public void onBell(@NonNull TerminalSession session) {}

    @Override
    public void onColorsChanged(@NonNull TerminalSession session) {}

    @Override
    public void onTerminalCursorStateChange(boolean state) {}

    @Override
    public void setTerminalShellPid(@NonNull TerminalSession session, int pid) {}

    @Override
    public Integer getTerminalCursorStyle() {
      return null;
    }

    @Override
    public void logError(String tag, String message) {
      Log.e(tag, message);
    }

    @Override
    public void logWarn(String tag, String message) {
      Log.w(tag, message);
    }

    @Override
    public void logInfo(String tag, String message) {
      Log.i(tag, message);
    }

    @Override
    public void logDebug(String tag, String message) {
      Log.d(tag, message);
    }

    @Override
    public void logVerbose(String tag, String message) {
      Log.v(tag, message);
    }

    @Override
    public void logStackTraceWithMessage(String tag, String message, Exception e) {
      Log.e(tag, message, e);
    }

    @Override
    public void logStackTrace(String tag, Exception e) {
      Log.e(tag, "Terminal error", e);
    }
  }
}
