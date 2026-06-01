package com.yn.sodiumeditordemo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import java.util.ArrayList;
import java.io.File;
import java.util.List;

public class TerminalActivity extends AppCompatActivity
    implements TerminalSessionClient, TerminalViewClient {

  private static final String TAG = "SodiumTerminal";
  public static final String EXTRA_RUN_COMMAND = "com.yn.sodiumeditordemo.RUN_COMMAND";
  private static final int TERMINAL_BACKGROUND_COLOR = Color.BLACK;
  private static final List<TerminalSession> sharedSessions = new ArrayList<>();
  private static int currentSessionIndex = -1;

  private TerminalView terminalView;
  private TerminalSession terminalSession;
  private TextView sessionTitleView;
  private boolean sessionFinished = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().setStatusBarColor(TERMINAL_BACKGROUND_COLOR);
    getWindow().setNavigationBarColor(TERMINAL_BACKGROUND_COLOR);

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(TERMINAL_BACKGROUND_COLOR);
    root.addView(
        buildToolbar(),
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

    terminalView = new TerminalView(this, null);
    terminalView.setTerminalViewClient(this);
    terminalView.setTextSize(14f * getResources().getDisplayMetrics().scaledDensity);
    terminalView.setFocusable(true);
    terminalView.setFocusableInTouchMode(true);
    terminalView.setBackgroundColor(TERMINAL_BACKGROUND_COLOR);

    root.addView(
        terminalView,
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    setContentView(root);
    applySystemWindowInsets(root);

    terminalSession = getOrCreateSession(getIntent());
    terminalSession.updateTerminalSessionClient(this);
    terminalView.attachSession(terminalSession);
    updateToolbarSessionTitle();
    terminalView.requestFocus();
    terminalView.post(
        () -> {
          terminalView.updateSize();
          showKeyboard();
        });
  }

  private LinearLayout buildToolbar() {
    LinearLayout toolbar = new LinearLayout(this);
    toolbar.setOrientation(LinearLayout.HORIZONTAL);
    toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);
    toolbar.setPadding(dp(4), 0, dp(4), 0);
    toolbar.setBackgroundColor(TERMINAL_BACKGROUND_COLOR);

    ImageButton back = buildToolbarIconButton(R.drawable.ic_terminal_back, "Back");
    back.setOnClickListener(v -> finish());
    toolbar.addView(back);

    sessionTitleView = new TextView(this);
    sessionTitleView.setTextColor(Color.WHITE);
    sessionTitleView.setTextSize(14f);
    sessionTitleView.setSingleLine(true);
    sessionTitleView.setGravity(android.view.Gravity.CENTER_VERTICAL);
    sessionTitleView.setPadding(dp(4), 0, dp(12), 0);
    toolbar.addView(
        sessionTitleView,
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

    View spacer = new View(this);
    toolbar.addView(
        spacer,
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

    ImageButton kill = buildToolbarIconButton(R.drawable.ic_terminal_kill, "Kill session");
    kill.setOnClickListener(v -> showKillSessionDialog());
    toolbar.addView(kill);

    ImageButton sessions = buildToolbarIconButton(R.drawable.ic_terminal_sessions, "Switch session");
    sessions.setOnClickListener(v -> showSessionPickerDialog());
    toolbar.addView(sessions);

    return toolbar;
  }

  private ImageButton buildToolbarIconButton(int iconRes, String contentDescription) {
    ImageButton button = new ImageButton(this);
    button.setImageResource(iconRes);
    button.setContentDescription(contentDescription);
    button.setColorFilter(Color.WHITE);
    button.setBackgroundColor(TERMINAL_BACKGROUND_COLOR);
    button.setPadding(dp(10), dp(10), dp(10), dp(10));
    button.setLayoutParams(
        new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT));
    return button;
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (terminalSession != null) {
      terminalSession.updateTerminalSessionClient(new DetachedTerminalSessionClient());
    }
    terminalSession = getOrCreateSession(intent);
    terminalSession.updateTerminalSessionClient(this);
    terminalView.attachSession(terminalSession);
    terminalView.updateSize();
    updateToolbarSessionTitle();
  }

  @Override
  protected void onDestroy() {
    if (terminalSession != null) {
      terminalSession.updateTerminalSessionClient(new DetachedTerminalSessionClient());
      terminalSession = null;
    }
    super.onDestroy();
  }

  private TerminalSession getOrCreateSession(Intent intent) {
    String runCommand = intent == null ? null : intent.getStringExtra(EXTRA_RUN_COMMAND);
    if (runCommand != null && !runCommand.isEmpty()) {
      intent.removeExtra(EXTRA_RUN_COMMAND);
      killAllSharedSessions();
      terminalSession = createRunSession(runCommand);
      sessionFinished = false;
      currentSessionIndex = -1;
      return terminalSession;
    }
    removeFinishedSharedSessions();
    if (sharedSessions.isEmpty()) {
      TerminalSession session = createSession();
      sharedSessions.add(session);
      currentSessionIndex = 0;
    } else if (currentSessionIndex < 0 || currentSessionIndex >= sharedSessions.size()) {
      currentSessionIndex = 0;
    }
    sessionFinished = false;
    return sharedSessions.get(currentSessionIndex);
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

  private TerminalSession createRunSession(String command) {
    File cwd = getFilesDir();
    String shellPath = "/system/bin/sh";
    String[] args = new String[] {"sh", "-c", command};
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

  private void switchToSession(int index) {
    switchToSession(index, false);
  }

  private void switchToSession(int index, boolean showToast) {
    if (index < 0 || index >= sharedSessions.size()) return;
    if (terminalSession != null) {
      terminalSession.updateTerminalSessionClient(new DetachedTerminalSessionClient());
    }
    currentSessionIndex = index;
    terminalSession = sharedSessions.get(index);
    terminalSession.updateTerminalSessionClient(this);
    sessionFinished = !terminalSession.isRunning();
    terminalView.attachSession(terminalSession);
    terminalView.updateSize();
    terminalView.requestFocus();
    updateToolbarSessionTitle();
    if (showToast) {
      showToast("Changed to session " + (index + 1));
    }
  }

  private void createAndSwitchToNewSession() {
    if (terminalSession != null) {
      terminalSession.updateTerminalSessionClient(new DetachedTerminalSessionClient());
    }
    TerminalSession session = createSession();
    sharedSessions.add(session);
    switchToSession(sharedSessions.size() - 1, true);
  }

  private void showKillSessionDialog() {
    if (terminalSession == null || !terminalSession.isRunning()) return;
    TextView message = buildDarkDialogText("هل تريد قتل الجلسة الحالية؟");
    AlertDialog dialog = new AlertDialog.Builder(this)
        .setCustomTitle(buildDarkDialogTitle("Kill session"))
        .setView(message)
        .setPositiveButton("نعم", (dialogInterface, which) -> killCurrentSession())
        .setNegativeButton("لا", null)
        .create();
    dialog.setOnShowListener(shownDialog -> styleDarkDialog(dialog));
    dialog.show();
  }

  private void killCurrentSession() {
    if (terminalSession == null) return;
    TerminalSession killed = terminalSession;
    int killedNumber = Math.max(1, sharedSessions.indexOf(killed) + 1);
    if (killed.isRunning()) killed.finishIfRunning();
    int removedIndex = sharedSessions.indexOf(killed);
    if (removedIndex >= 0) {
      sharedSessions.remove(removedIndex);
    }
    showToast("Killed session " + killedNumber);
    if (sharedSessions.isEmpty()) {
      currentSessionIndex = -1;
      terminalSession = null;
      finish();
      return;
    }
    int nextIndex = Math.max(0, Math.min(removedIndex, sharedSessions.size() - 1));
    switchToSession(nextIndex);
  }

  private void showSessionPickerDialog() {
    removeFinishedSharedSessions();
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setBackgroundColor(0xFF121212);
    int pad = dp(16);
    content.setPadding(pad, pad, pad, 0);

    RadioGroup group = new RadioGroup(this);
    group.setOrientation(RadioGroup.VERTICAL);
    for (int i = 0; i < sharedSessions.size(); i++) {
      RadioButton button = new RadioButton(this);
      button.setId(i + 1);
      button.setText(getSessionLabel(i, sharedSessions.get(i)));
      button.setTextColor(Color.WHITE);
      button.setChecked(i == currentSessionIndex);
      group.addView(button);
    }
    content.addView(group);

    Button newButton = new Button(this);
    newButton.setText("New");
    newButton.setTextColor(Color.WHITE);
    newButton.setBackgroundColor(0xFF222222);
    content.addView(
        newButton,
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    AlertDialog dialog = new AlertDialog.Builder(this)
        .setCustomTitle(buildDarkDialogTitle("Sessions"))
        .setView(content)
        .create();
    group.setOnCheckedChangeListener(
        (radioGroup, checkedId) -> {
          int index = checkedId - 1;
          dialog.dismiss();
          switchToSession(index, true);
        });
    newButton.setOnClickListener(
        v -> {
          dialog.dismiss();
          createAndSwitchToNewSession();
        });
    dialog.setOnShowListener(shownDialog -> styleDarkDialog(dialog));
    dialog.show();
  }

  private void updateToolbarSessionTitle() {
    if (sessionTitleView == null) return;
    int index = sharedSessions.indexOf(terminalSession);
    int sessionNumber = index >= 0 ? index + 1 : Math.max(1, currentSessionIndex + 1);
    sessionTitleView.setText("session " + sessionNumber);
  }

  private TextView buildDarkDialogTitle(String title) {
    TextView titleView = new TextView(this);
    titleView.setText(title);
    titleView.setTextColor(Color.WHITE);
    titleView.setTextSize(20f);
    titleView.setPadding(dp(24), dp(20), dp(24), dp(8));
    titleView.setBackgroundColor(0xFF121212);
    return titleView;
  }

  private TextView buildDarkDialogText(String text) {
    TextView textView = new TextView(this);
    textView.setText(text);
    textView.setTextColor(Color.WHITE);
    textView.setTextSize(16f);
    textView.setPadding(dp(24), dp(8), dp(24), dp(8));
    textView.setBackgroundColor(0xFF121212);
    return textView;
  }

  private void styleDarkDialog(AlertDialog dialog) {
    if (dialog.getWindow() != null) {
      dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0xFF121212));
    }
    Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
    if (positiveButton != null) positiveButton.setTextColor(Color.WHITE);
    Button negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
    if (negativeButton != null) negativeButton.setTextColor(Color.WHITE);
  }

  private void showToast(String message) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
  }

  private String getSessionLabel(int index, TerminalSession session) {
    String title = session.getTitle();
    if (title == null || title.trim().isEmpty()) title = "Session " + (index + 1);
    return title + (session.isRunning() ? "" : " (finished)");
  }

  private void removeFinishedSharedSessions() {
    for (int i = sharedSessions.size() - 1; i >= 0; i--) {
      TerminalSession session = sharedSessions.get(i);
      if (!session.isRunning()) {
        sharedSessions.remove(i);
        if (currentSessionIndex >= i) currentSessionIndex--;
      }
    }
    if (currentSessionIndex < 0 && !sharedSessions.isEmpty()) currentSessionIndex = 0;
  }

  private void killAllSharedSessions() {
    for (TerminalSession session : sharedSessions) {
      if (session.isRunning()) session.finishIfRunning();
      session.updateTerminalSessionClient(new DetachedTerminalSessionClient());
    }
    sharedSessions.clear();
    currentSessionIndex = -1;
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

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
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
    if (terminalSession == finishedSession) {
      sessionFinished = true;
    }
    int index = sharedSessions.indexOf(finishedSession);
    if (index >= 0 && finishedSession != terminalSession) {
      sharedSessions.remove(index);
      if (currentSessionIndex >= index) currentSessionIndex--;
    }
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
    if (sessionFinished
        && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
      finish();
      return true;
    }
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
    if (sessionFinished && (codePoint == '\n' || codePoint == '\r')) {
      finish();
      return true;
    }
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
      int index = sharedSessions.indexOf(finishedSession);
      if (index >= 0) {
        sharedSessions.remove(index);
        if (currentSessionIndex >= index) currentSessionIndex--;
      }
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
