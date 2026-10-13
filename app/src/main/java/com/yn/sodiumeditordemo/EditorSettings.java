package com.yn.sodiumeditordemo;

import android.content.SharedPreferences;
import com.yn.sodiumeditor.SodiumEditor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue of library settings exposed in the app settings screen, grouped in sections. Each
 * entry knows its preference key, default (the library default) and how to apply a value to a
 * {@link SodiumEditor}.
 */
final class EditorSettings {

  interface BoolApplier {
    void apply(SodiumEditor e, boolean v);
  }

  interface NumApplier {
    void apply(SodiumEditor e, double v);
  }

  interface StrApplier {
    void apply(SodiumEditor e, String v);
  }

  static final class Item {
    final String section;
    final String key;
    final String title;
    final boolean isBool;
    final double defNum;
    final boolean defBool;
    final BoolApplier boolApplier;
    final NumApplier numApplier;
    /** 0 = not a string item, 1 = color, 2 = file path. Empty string means "not set". */
    final int strKind;

    final StrApplier strApplier;

    Item(
        String section,
        String key,
        String title,
        boolean isBool,
        boolean defBool,
        double defNum,
        BoolApplier b,
        NumApplier n,
        int strKind,
        StrApplier st) {
      this.section = section;
      this.key = key;
      this.title = title;
      this.isBool = isBool;
      this.defBool = defBool;
      this.defNum = defNum;
      this.boolApplier = b;
      this.numApplier = n;
      this.strKind = strKind;
      this.strApplier = st;
    }
  }

  static final List<Item> ITEMS = new ArrayList<>();

  private static void bool(String s, String key, String title, boolean def, BoolApplier a) {
    ITEMS.add(new Item(s, key, title, true, def, 0, a, null, 0, null));
  }

  private static void num(String s, String key, String title, double def, NumApplier a) {
    ITEMS.add(new Item(s, key, title, false, false, def, null, a, 0, null));
  }

  private interface ColorApplier {
    void apply(SodiumEditor e, int color);
  }

  private static void color(String s, String key, String title, ColorApplier a) {
    ITEMS.add(
        new Item(
            s,
            key,
            title,
            false,
            false,
            0,
            null,
            null,
            1,
            (e, v) -> {
              Integer c = parseColor(v);
              if (c != null) a.apply(e, c);
            }));
  }

  private static void path(String s, String key, String title, StrApplier a) {
    ITEMS.add(new Item(s, key, title, false, false, 0, null, null, 2, a));
  }

  static Integer parseColor(String v) {
    if (v == null || v.trim().isEmpty()) return null;
    try {
      return android.graphics.Color.parseColor(v.trim());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  static {
    String s = "Cursor & Caret";
    bool(s, "caret_enabled", "Show Caret", true, (e, v) -> e.caret.setCaretEnabled(v));
    bool(s, "caret_blink", "Caret Blink", true, (e, v) -> e.caret.setCaretBlinkEnabled(v));
    bool(
        s,
        "caret_pause_blink_typing",
        "Pause Blink While Typing",
        true,
        (e, v) -> e.caret.setPauseBlinkWhileTypingEnabled(v));
    num(s, "caret_blink_period_ms", "Caret Blink Period (ms)", 500, (e, v) -> e.caret.setCaretBlinkPeriodMs((long) v));
    num(
        s,
        "caret_typing_resume_delay_ms",
        "Blink Resume Delay (ms)",
        650,
        (e, v) -> e.caret.setCaretTypingResumeDelayMs((long) v));
    num(s, "caret_width", "Caret Width (px)", 4, (e, v) -> e.caret.setCaretWidth((float) v));
    bool(s, "cursor_handle", "Cursor Handle", true, (e, v) -> e.cursorHandle.setCursorHandleEnabled(v));
    bool(
        s,
        "cursor_handle_hide_typing",
        "Hide Handle While Typing",
        true,
        (e, v) -> e.cursorHandle.setHideWhileTypingEnabled(v));
    bool(
        s,
        "cursor_animation",
        "Cursor Animation",
        true,
        (e, v) -> e.cursorAnimation.setCursorAnimationEnabled(v));

    s = "Selection & Clipboard";
    bool(s, "smart_selection", "Smart Selection", true, (e, v) -> e.selection.setSmartSelectionEnabled(v));
    bool(
        s,
        "long_press_selection",
        "Long Press Selection",
        true,
        (e, v) -> e.selection.setLongPressSelectionEnabled(v));
    bool(
        s,
        "line_number_selection",
        "Select Line By Tapping Line Number",
        true,
        (e, v) -> e.lineNumber.setLineNumberSelectionEnabled(v));
    bool(
        s,
        "selection_animation",
        "Selection Animation",
        true,
        (e, v) -> e.selection.setSelectionAnimationEnabled(v));
    bool(
        s,
        "handle_move_animation",
        "Handle Move Animation",
        true,
        (e, v) -> e.selectionHandles.setHandleMoveAnimationEnabled(v));
    num(s, "copy_cut_max_chars", "Copy/Cut Max Characters", 8_000_000, (e, v) -> e.selection.setCopyCutMaxChars((int) v));
    num(s, "copy_cut_max_lines", "Copy/Cut Max Lines", 20000, (e, v) -> e.selection.setCopyCutMaxLines((long) v));
    num(
        s,
        "hide_copy_cut_max_lines",
        "Hide Copy/Cut Above Lines",
        20000,
        (e, v) -> e.selection.setHideCopyCutMaxLines((int) v));
    num(s, "replace_all_max_count", "Replace All Max Count", 100000, (e, v) -> e.selection.setReplaceAllMaxCount((int) v));

    s = "Highlighting";
    bool(
        s,
        "current_line_highlight",
        "Highlight Current Line",
        true,
        (e, v) -> e.currentLineHighlight.setHighlightCurrentLine(v));
    bool(
        s,
        "current_line_gutter_highlight",
        "Highlight Current Line In Gutter",
        true,
        (e, v) -> e.currentLineHighlight.setCurrentLineGutterHighlightEnabled(v));
    bool(
        s,
        "search_highlight",
        "Highlight Search Matches",
        true,
        (e, v) -> e.search.setSearchHighlightEnabled(v));
    bool(
        s,
        "color_code_highlight",
        "Color Code Preview",
        true,
        (e, v) -> e.colorCodeHighlight.setColorCodeHighlightingEnabled(v));
    bool(s, "url_underline", "Underline URLs", true, (e, v) -> e.urlUnderline.setUrlUnderliningEnabled(v));
    bool(s, "path_underline", "Underline Paths", true, (e, v) -> e.pathUnderline.setPathUnderliningEnabled(v));
    bool(
        s,
        "error_underline",
        "Error Underline",
        true,
        (e, v) -> e.errorUnderline.setErrorUnderlineEnabled(v));
    bool(
        s,
        "backtick_strings",
        "Backtick Strings",
        false,
        (e, v) -> e.highlight.setBacktickStringsEnabled(v));
    bool(
        s,
        "triple_quote_strings",
        "Triple Quote Strings",
        false,
        (e, v) -> e.highlight.setTripleQuoteStringsEnabled(v));
    num(
        s,
        "max_syntax_line_length",
        "Max Syntax Line Length",
        4096,
        (e, v) -> e.highlightRender.setMaxSyntaxLineLength((int) v));
    num(s, "prefetch_cols", "Syntax Prefetch Columns", 100, (e, v) -> e.highlightRender.setPrefetchCols((int) v));

    s = "Auto Editing";
    bool(s, "auto_pairing", "Auto Pairing", true, (e, v) -> e.autoPair.setAutoPairingEnabled(v));
    bool(
        s,
        "auto_brackets_pairing",
        "Auto Pair Brackets",
        true,
        (e, v) -> e.autoPair.setAutoBracketsPairingEnabled(v));
    bool(
        s,
        "auto_comments_pairing",
        "Auto Pair Block Comments",
        true,
        (e, v) -> e.autoPair.setAutoJavaCommentsPairingEnabled(v));
    bool(
        s,
        "auto_strings_pairing",
        "Auto Pair Quotes",
        true,
        (e, v) -> e.autoPair.setAutoStringsPairingEnabled(v));
    bool(
        s,
        "auto_tuple_strings_pairing",
        "Auto Pair Tuple Strings",
        true,
        (e, v) -> e.autoPair.setAutoTupleStringsPairingEnabled(v));
    bool(
        s,
        "auto_bracket_newline",
        "Bracket Newline Expansion",
        true,
        (e, v) -> e.autoBracketNewline.setAutoBracketNewlineEnabled(v));
    bool(
        s,
        "auto_bracket_newline_indent",
        "Indent Inside Brackets On Newline",
        true,
        (e, v) -> e.autoBracketNewlineIndent.setAutoBracketNewlineIndentEnabled(v));
    bool(
        s,
        "auto_indent_closing_bracket",
        "Indent After Closing Bracket",
        true,
        (e, v) -> e.autoIndentAfterClosingBracket.setAutoIndentAfterClosingBracketEnabled(v));
    bool(
        s,
        "base_indent_newline",
        "Keep Indent On Newline",
        true,
        (e, v) -> e.baseIndentOnNewline.setBaseIndentOnNewlineEnabled(v));
    bool(
        s,
        "click_after_end_add_line",
        "Tap After End To Add Line",
        true,
        (e, v) -> e.clickAfterEndToAddLine.setClickAfterEndToAddLineEnabled(v));

    s = "Guides (Advanced)";
    bool(
        s,
        "bracket_guides_offscreen",
        "Bracket Guides For Off-Screen Lines",
        false,
        (e, v) -> e.bracketGuides.setDrawGuidesForOffScreenLines(v));
    bool(
        s,
        "bracket_guides_skip_fast_scroll",
        "Skip Bracket Guides During Fast Scroll",
        false,
        (e, v) -> e.bracketGuides.setSkipGuidesDuringFastScroll(v));
    bool(
        s,
        "bracket_guides_show_fast_scroll",
        "Show Bracket Guides During Fast Scroll",
        true,
        (e, v) -> e.bracketGuides.setShowGuidesDuringFastScroll(v));
    num(
        s,
        "bracket_guides_rebuild_ms",
        "Bracket Guides Rebuild Interval (ms)",
        80,
        (e, v) -> e.bracketGuides.setMinRebuildIntervalMs((long) v));
    num(
        s,
        "bracket_guides_stroke",
        "Bracket Guides Stroke Width",
        4,
        (e, v) -> e.bracketGuides.setBracketGuidesStrokeWidth((float) v));
    num(
        s,
        "indent_guides_stroke",
        "Indent Guides Stroke Width",
        2,
        (e, v) -> e.indentGuides.setIndentGuidesStrokeWidth((float) v));
    num(
        s,
        "symbols_match_stroke",
        "Bracket Match Stroke Width",
        3,
        (e, v) -> e.symbolsMatch.setSymbolsMatchStrokeWidth((float) v));
    num(
        s,
        "whitespace_space_step",
        "Spaces Per Whitespace Dot",
        1,
        (e, v) -> e.whitespaceGuides.setWhitespaceGuidesSpaceStep((int) v));
    num(s, "visual_space_scale", "Visual Space Scale", 2, (e, v) -> e.setVisualSpaceScale((int) v));

    s = "Word Wrap";
    bool(
        s,
        "word_wrap_indicator",
        "Word Wrap Indicator",
        true,
        (e, v) -> e.wordWrap.setWordWrapIndicatorEnabled(v));

    s = "Scrolling & Zoom";
    num(s, "scroll_sensitivity", "Scroll Sensitivity", 1, (e, v) -> e.scroll.setScrollSensitivity((float) v));
    num(s, "fling_sensitivity", "Fling Sensitivity", 1, (e, v) -> e.scroll.setFlingSensitivity((float) v));
    bool(s, "fling_bounce", "Fling Bounce", false, (e, v) -> e.scroll.setFlingBounceEnabled(v));
    bool(
        s,
        "stretch_overscroll",
        "Stretch Overscroll Effect",
        true,
        (e, v) -> e.scroll.stretch.setStretchOverscrollEnabled(v));
    bool(s, "scroll_bar", "Scroll Bar", true, (e, v) -> e.scroll.bar.setScrollBarEnabled(v));
    bool(s, "scroll_bar_fade", "Fade Scroll Bar", true, (e, v) -> e.scroll.bar.setScrollBarFadeEnabled(v));
    num(
        s,
        "scroll_bar_fade_delay_ms",
        "Scroll Bar Fade Delay (ms)",
        1000,
        (e, v) -> e.scroll.bar.setScrollBarFadeDelayMs((long) v));
    num(s, "scroll_bar_width", "Scroll Bar Width (px)", 6, (e, v) -> e.scroll.bar.setScrollBarWidthPx((float) v));
    bool(s, "zoom_enabled", "Pinch To Zoom", true, (e, v) -> e.zoom.setZoomEnabled(v));
    bool(
        s,
        "zoom_defer_wrap_reflow",
        "Defer Word Wrap Reflow While Zooming",
        true,
        (e, v) -> e.zoom.setDeferWordWrapReflowDuringZoom(v));
    bool(
        s,
        "zoom_hide_decorations",
        "Hide Decorations While Zooming",
        false,
        (e, v) -> e.zoom.setHideDecorationsWhileZooming(v));
    num(s, "zoom_step_clamp", "Zoom Step Clamp (sp)", 0.2, (e, v) -> e.zoom.setZoomStepClamp((float) v));

    s = "Animations";
    bool(
        s,
        "char_animation",
        "Typing Animation",
        true,
        (e, v) -> e.charAnimation.setCharAnimation(v, e.charAnimation.charAnimationDurationMs));
    bool(
        s,
        "show_loading_on_open",
        "Loading Indicator On File Open",
        true,
        (e, v) -> e.loadingCircle.setShowLoadingOnFileOpen(v));

    s = "Performance (Advanced)";
    num(s, "render_window_size", "Render Window Size (lines)", 250, (e, v) -> e.windowRender.setWindowSize((int) v));
    num(s, "prefetch_lines", "Prefetch Lines", 250, (e, v) -> e.windowRender.setPrefetchLines((int) v));
    num(
        s,
        "line_width_cache_size",
        "Line Width Cache Size",
        250,
        (e, v) -> e.windowRender.setLineWidthCacheSize((int) v));

    s = "Edge Effects & Popup";
    num(s, "edge_effect_strength", "Edge Effect Strength", 0.8, (e, v) -> e.scroll.edge.setEdgeEffectStrength((float) v));
    num(
        s,
        "edge_effect_arc_percent",
        "Edge Effect Max Arc Height",
        0.12,
        (e, v) -> e.scroll.edge.setEdgeEffectMaxArcHeightPercent((float) v));
    num(
        s,
        "stretch_overscroll_strength",
        "Stretch Overscroll Strength",
        1,
        (e, v) -> e.scroll.stretch.setStretchOverscrollStrength((float) v));
    num(s, "popup_text_size", "Popup Text Size (sp)", 12, (e, v) -> e.popup.setPopupTextSize((float) v));
    bool(
        s,
        "popup_follow_typeface",
        "Popup Uses Editor Font",
        false,
        (e, v) -> e.popup.setPopupTextFollowsEditorTypeface(v));
    color(s, "popup_bg_color", "Popup Background Color", (e, c) -> e.popup.setPopupBackgroundColor(c));
    color(s, "popup_text_color", "Popup Text Color", (e, c) -> e.popup.setPopupTextColor(c));
    color(s, "edge_color", "Edge Effect Color", (e, c) -> e.scroll.edge.setEdgeEffectColor(c));

    s = "Colors (empty = theme default)";
    color(s, "color_background", "Editor Background", (e, c) -> e.view.setEditorBackgroundColor(c));
    color(s, "color_caret", "Caret", (e, c) -> e.caret.setCaretColor(c));
    color(s, "color_cursor_handle", "Cursor Handle", (e, c) -> e.cursorHandle.setCursorHandleColor(c));
    color(s, "color_selection", "Selection", (e, c) -> e.selection.setSelectionColor(c));
    color(
        s,
        "color_selection_handle",
        "Selection Handles",
        (e, c) -> e.selectionHandles.setSelectionHandleColor(c));
    color(s, "color_line_number", "Line Numbers", (e, c) -> e.lineNumber.setLineNumberColor(c));
    color(
        s,
        "color_current_line_number",
        "Current Line Number",
        (e, c) -> e.lineNumber.setCurrentLineNumberColor(c));
    color(s, "color_gutter_bg", "Gutter Background", (e, c) -> e.lineNumber.setGutterBackgroundColor(c));
    color(
        s,
        "color_gutter_separator",
        "Gutter Separator",
        (e, c) -> e.lineNumber.setGutterSeparatorColor(c));
    color(
        s,
        "color_current_line",
        "Current Line Highlight",
        (e, c) -> e.currentLineHighlight.setCurrentLineHighlightColor(c));
    color(s, "color_search", "Search Matches", (e, c) -> e.search.setSearchHighlightColor(c));
    color(
        s,
        "color_search_current",
        "Current Search Match",
        (e, c) -> e.search.setCurrentSearchMatchHighlightColor(c));
    color(s, "color_bracket_guides", "Bracket Guides", (e, c) -> e.bracketGuides.setBracketGuidesColor(c));
    color(s, "color_indent_guides", "Indent Guides", (e, c) -> e.indentGuides.setIndentGuidesColor(c));
    color(s, "color_symbols_match", "Bracket Match", (e, c) -> e.symbolsMatch.setSymbolsMatchColor(c));
    color(
        s,
        "color_whitespace",
        "Whitespace Guides",
        (e, c) -> e.whitespaceGuides.setWhitespaceGuidesColor(c));
    color(s, "color_error_underline", "Error Underline", (e, c) -> e.errorUnderline.setErrorUnderlineColor(c));
    color(s, "color_scroll_bar", "Scroll Bar", (e, c) -> e.scroll.bar.setScrollBarColor(c));
    color(s, "color_loading", "Loading Indicator", (e, c) -> e.loadingCircle.setLoadingCircleColor(c));
    color(
        s,
        "color_word_wrap_indicator",
        "Word Wrap Indicator",
        (e, c) -> e.wordWrap.setWordWrapIndicatorColor(c));
    color(s, "color_binary_token_fill", "Binary Token Fill", (e, c) -> e.binaryRender.setBinaryTokenFillColor(c));
    color(
        s,
        "color_binary_token_stroke",
        "Binary Token Stroke",
        (e, c) -> e.binaryRender.setBinaryTokenStrokeColor(c));
    color(s, "color_binary_token_text", "Binary Token Text", (e, c) -> e.binaryRender.setBinaryTokenTextColor(c));

    s = "Layout & Padding";
    num(s, "padding_left", "Padding Left (px)", 12, (e, v) -> e.layout.setPaddingLeft((float) v));
    num(s, "padding_top", "Padding Top (px)", 0, (e, v) -> e.layout.setPaddingTop((float) v));
    num(s, "padding_right", "Padding Right (px)", 0, (e, v) -> e.layout.setPaddingRight((float) v));
    num(s, "padding_bottom", "Padding Bottom (px)", 0, (e, v) -> e.layout.setPaddingBottom((float) v));
    num(s, "text_start_gap", "Text Start Gap (px)", 12, (e, v) -> e.layout.setTextStartGap((float) v));
    num(
        s,
        "gutter_separator_width",
        "Gutter Separator Width (px)",
        5,
        (e, v) -> e.lineNumber.setGutterSeparatorWidth((float) v));

    s = "Handles & Scroll Bar (Size)";
    num(s, "cursor_handle_width", "Cursor Handle Width", 40, (e, v) -> e.cursorHandle.setCursorHandleWidth((float) v));
    num(s, "cursor_handle_height", "Cursor Handle Height", 40, (e, v) -> e.cursorHandle.setCursorHandleHeight((float) v));
    num(s, "cursor_handle_radius", "Cursor Handle Radius", 16, (e, v) -> e.cursorHandle.setCursorHandleRadius((float) v));
    num(
        s,
        "selection_handle_size",
        "Selection Handle Size",
        40,
        (e, v) -> e.selectionHandles.setHandleSize((float) v, (float) v));
    num(s, "selection_handle_radius", "Selection Handle Radius", 6, (e, v) -> e.selectionHandles.setHandleRadius((float) v));
    num(s, "scroll_bar_min_thumb", "Scroll Bar Min Thumb (px)", 24, (e, v) -> e.scroll.bar.setScrollBarMinThumbPx((float) v));
    num(
        s,
        "scroll_bar_fade_duration_ms",
        "Scroll Bar Fade Duration (ms)",
        200,
        (e, v) -> e.scroll.bar.setScrollBarFadeDurationMs((long) v));
    num(s, "scroll_bar_halo_size", "Scroll Bar Halo Size (px)", 8, (e, v) -> e.scroll.bar.setScrollBarHaloSizePx((float) v));
    num(s, "scroll_bar_corner_radius", "Scroll Bar Corner Radius (px)", 6, (e, v) -> e.scroll.bar.setScrollBarCornerRadiusPx((float) v));
    num(s, "scroll_bar_margin", "Scroll Bar Margin (px)", 2, (e, v) -> e.scroll.bar.setScrollBarMarginPx((float) v));

    s = "Animations (Timing)";
    num(s, "cursor_anim_duration_ms", "Cursor Animation Duration (ms)", 80, (e, v) -> e.cursorAnimation.setAnimationDurationMs((long) v));
    num(
        s,
        "char_anim_duration_ms",
        "Typing Animation Duration (ms)",
        280,
        (e, v) -> e.charAnimation.setCharAnimation(e.charAnimation.isCharAnimationEnabled, (int) v));
    num(s, "loading_radius", "Loading Indicator Radius", 40, (e, v) -> e.loadingCircle.setLoadingCircleRadius((float) v));
    num(s, "loading_stroke", "Loading Indicator Stroke", 8, (e, v) -> e.loadingCircle.setLoadingCircleStrokeWidth((float) v));

    s = "Binary Tokens (Style)";
    num(s, "binary_token_stroke_width", "Token Stroke Width", 1, (e, v) -> e.binaryRender.setBinaryTokenStrokeWidth((float) v));
    num(s, "binary_token_padding", "Token Box Padding", 2, (e, v) -> e.binaryRender.setBinaryTokenBoxPadding((float) v, (float) v));
    num(s, "binary_token_corner_radius", "Token Corner Radius", 10, (e, v) -> e.binaryRender.setBinaryTokenCornerRadius((float) v));

    s = "Editor Behavior (Misc)";
    bool(s, "read_only", "Read Only", false, (e, v) -> e.view.setReadOnly(v));
    bool(
        s,
        "hide_keyboard_focus_loss",
        "Hide Keyboard On Focus Loss",
        true,
        (e, v) -> e.selection.hideKeyboardOnFocusLoss = v);
    bool(s, "search_case_sensitive", "Case Sensitive Search", false, (e, v) -> e.search.searchCaseSensitive = v);
    bool(
        s,
        "stretch_overlay",
        "Stretch Overlay Effect",
        false,
        (e, v) -> e.scroll.stretch.stretchOverlayEnabled = v);
    num(
        s,
        "search_select_all_max",
        "Select All Matches Max",
        100000,
        (e, v) -> e.search.searchSelectAllMaxMatches = (int) v);
    num(s, "direct_line_cache_size", "Direct Line Cache Size", 250, (e, v) -> e.fileIO.setDirectLineCacheMaxSize((int) v));

    num(
        s,
        "ime_context_before",
        "IME Context Before Cursor (chars)",
        2048,
        (e, v) -> e.ime.setImeContextSize((int) v, e.ime.imeExtractedAfterChars));
    num(
        s,
        "ime_context_after",
        "IME Context After Cursor (chars)",
        2048,
        (e, v) -> e.ime.setImeContextSize(e.ime.imeExtractedBeforeChars, (int) v));

    s = "Font & Background";
    path(
        s,
        "font_file",
        "Font File Path",
        (e, v) -> {
          if (!v.trim().isEmpty()) {
            e.view.setFontFromFile(v.trim(), com.yn.sodiumeditor.core.view.FontStyle.STYLE_NORMAL);
          }
        });
    path(
        s,
        "background_image_file",
        "Background Image Path",
        (e, v) -> {
          if (!v.trim().isEmpty()) e.view.setEditorBackgroundImageFromFile(v.trim());
        });
  }

  /** Last applied values per editor instance, so unchanged settings are not re-applied. */
  private final Map<String, Object> lastApplied = new HashMap<>();

  static double getNum(SharedPreferences prefs, Item item) {
    String raw = prefs.getString(item.key, null);
    if (raw == null) return item.defNum;
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException ex) {
      return item.defNum;
    }
  }

  static String formatNum(double v) {
    return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
  }

  /**
   * Applies only values that differ from the last applied (initially the library default).
   *
   * @return true if a color/path override was cleared; the caller should re-apply the theme colors
   *     and then call {@link #reapplyStrings}.
   */
  boolean apply(SodiumEditor editor, SharedPreferences prefs) {
    boolean cleared = false;
    for (Item item : ITEMS) {
      if (item.strKind != 0) {
        String v = prefs.getString(item.key, "");
        Object prev = lastApplied.get(item.key);
        String last = prev == null ? "" : (String) prev;
        if (!v.equals(last)) {
          if (v.isEmpty()) {
            cleared = true;
          } else {
            item.strApplier.apply(editor, v);
          }
        }
        lastApplied.put(item.key, v);
      } else if (item.isBool) {
        boolean v = prefs.getBoolean(item.key, item.defBool);
        Object prev = lastApplied.get(item.key);
        boolean last = prev == null ? item.defBool : (Boolean) prev;
        if (v != last) {
          item.boolApplier.apply(editor, v);
        }
        lastApplied.put(item.key, v);
      } else {
        double v = getNum(prefs, item);
        Object prev = lastApplied.get(item.key);
        double last = prev == null ? item.defNum : (Double) prev;
        if (v != last) {
          item.numApplier.apply(editor, v);
        }
        lastApplied.put(item.key, v);
      }
    }
    return cleared;
  }

  /** Re-applies every non-empty color/path override (after the theme colors were reset). */
  void reapplyStrings(SodiumEditor editor, SharedPreferences prefs) {
    for (Item item : ITEMS) {
      if (item.strKind == 0) continue;
      String v = prefs.getString(item.key, "");
      if (!v.isEmpty()) item.strApplier.apply(editor, v);
    }
  }
}
