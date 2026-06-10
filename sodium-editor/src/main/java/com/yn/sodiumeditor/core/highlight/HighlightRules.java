package com.yn.sodiumeditor.core.highlight;

import android.graphics.Paint;
import android.graphics.Typeface;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.HighlightRender;
import com.yn.sodiumeditor.utils.HighlightUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Manages syntax highlighting rules and delimiters. */
public class HighlightRules {
  private final SodiumEditor editor;
  private final Highlight highlight;

  public final List<HighlightRender.HighlightRule> highlightRules = new ArrayList<>();
  public final ArrayList<HighlightRender.HighlightRule> regexHighlightRules = new ArrayList<>();
  public final ArrayList<String> lineCommentDelimiters = new ArrayList<>();

  public HighlightRender.HighlightRule stringHighlightRule;
  public HighlightRender.HighlightRule blockCommentHighlightRule;
  public HighlightRender.HighlightRule lineCommentHighlightRule;
  public HighlightRender.HighlightRule whitespaceStringRule;
  public HighlightRender.HighlightRule whitespaceCommentRule;

  public HighlightRules(SodiumEditor editor, Highlight highlight) {
    this.editor = editor;
    this.highlight = highlight;
    initWhitespaceRules();
  }

  private void initWhitespaceRules() {
    float size = editor.textRender.paint.getTextSize();
    Typeface tf = editor.textRender.paint.getTypeface();
    whitespaceStringRule =
        new HighlightRender.HighlightRule(
            "",
            com.yn.sodiumeditor.core.view.FontStyle.STYLE_NORMAL,
            0xFF000000,
            size,
            tf,
            false,
            HighlightRender.HighlightRuleType.STRING);
    whitespaceCommentRule =
        new HighlightRender.HighlightRule(
            "",
            com.yn.sodiumeditor.core.view.FontStyle.STYLE_NORMAL,
            0xFF000000,
            size,
            tf,
            false,
            HighlightRender.HighlightRuleType.BLOCK_COMMENT);
  }

  public void addHighlightRule(String regex, int style, int color, boolean underline) {
    HighlightRender.HighlightRuleType type = HighlightRender.HighlightRuleType.REGEX;
    if (regex.equals(Highlight.RULE_STRING)) type = HighlightRender.HighlightRuleType.STRING;
    else if (regex.equals(Highlight.RULE_BLOCK_COMMENT))
      type = HighlightRender.HighlightRuleType.BLOCK_COMMENT;
    else if (HighlightUtils.isLineCommentRegex(regex))
      type = HighlightRender.HighlightRuleType.LINE_COMMENT;

    HighlightRender.HighlightRule rule =
        new HighlightRender.HighlightRule(
            regex,
            style,
            color,
            editor.textRender.paint.getTextSize(),
            editor.textRender.paint.getTypeface(),
            underline,
            type);
    if (type == HighlightRender.HighlightRuleType.LINE_COMMENT) {
      addLineCommentDelimiter(extractLineCommentDelimiter(regex));
      if (lineCommentHighlightRule != null) highlightRules.remove(lineCommentHighlightRule);
      lineCommentHighlightRule = rule;
      highlightRules.add(rule);
    } else {
      highlightRules.add(rule);
      if (type == HighlightRender.HighlightRuleType.STRING) stringHighlightRule = rule;
      else if (type == HighlightRender.HighlightRuleType.BLOCK_COMMENT)
        blockCommentHighlightRule = rule;
      else regexHighlightRules.add(rule);
    }
    highlight.clearHighlightCaches();
  }

  public void clearHighlightRules() {
    highlightRules.clear();
    regexHighlightRules.clear();
    lineCommentDelimiters.clear();
    stringHighlightRule = null;
    blockCommentHighlightRule = null;
    lineCommentHighlightRule = null;
    highlight.clearHighlightCaches();
  }

  private String extractLineCommentDelimiter(String regex) {
    if (regex == null) return "";
    if (regex.contains("//")) return "//";
    if (regex.contains("#")) return "#";
    if (regex.contains("--")) return "--";
    if (regex.contains(";")) return ";";
    return "";
  }

  private void addLineCommentDelimiter(String d) {
    if (d != null && !d.isEmpty() && !lineCommentDelimiters.contains(d))
      lineCommentDelimiters.add(d);
  }

  public boolean isEmpty() {
    return highlightRules.isEmpty()
        && regexHighlightRules.isEmpty()
        && lineCommentDelimiters.isEmpty()
        && stringHighlightRule == null
        && blockCommentHighlightRule == null
        && lineCommentHighlightRule == null;
  }

  public List<HighlightRender.HighlightSpan> calculateSyntaxSpansForLine(
      String line, int globalLine) {
    if (editor.view.getLogicalLineLength(globalLine, line)
        > editor.highlightRender.maxSyntaxLineLength) {
      return Collections.emptyList();
    }
    if (line.isEmpty()) {
      return Collections.emptyList();
    }

    HighlightRender.HighlightLineState startState = highlight.getLineStateAtStart(globalLine);
    HighlightRender.LineParseResult parseResult =
        highlight.parseLineForSyntax(
            line,
            startState.inBlockComment,
            startState.stringState,
            whitespaceStringRule,
            whitespaceCommentRule,
            true);

    if (globalLine >= editor.windowRender.windowStartLine
        && globalLine
            < editor.windowRender.windowStartLine + editor.windowRender.linesWindow.size()) {
      if (highlight.isBlockCommentsEnabled) {
        highlight.blockCommentEndStateCache.put(globalLine, parseResult.endsInBlockComment);
      }
      highlight.stringEndStateCache.put(globalLine, parseResult.endsInStringState);
    }

    List<HighlightRender.HighlightSpan> spans = parseResult.spans;
    if (spans.size() > 1) {
      Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
    }
    return spans;
  }

  public List<HighlightRender.HighlightSpan> getWhitespaceGuideSyntaxSpans(
      String line, int globalLine) {
    HighlightRender.HighlightRule stringRule = highlight.stringHighlightRule;
    HighlightRender.HighlightRule commentRule = highlight.blockCommentHighlightRule;
    if (stringRule == null && commentRule == null) {
      return calculateSyntaxSpansForLine(line, globalLine);
    }

    List<HighlightRender.HighlightSpan> spans = highlight.highlightCache.get(globalLine);
    if (spans == null) {
      spans = highlight.calculateSpansForLine(line, globalLine);
      highlight.highlightCache.put(globalLine, spans);
    }
    if (spans.isEmpty()) return Collections.emptyList();

    Paint stringPaint = (stringRule != null) ? stringRule.paint : null;
    Paint commentPaint = (commentRule != null) ? commentRule.paint : null;
    if (stringPaint == null && commentPaint == null) return Collections.emptyList();

    ArrayList<HighlightRender.HighlightSpan> syntaxSpans = null;
    for (HighlightRender.HighlightSpan span : spans) {
      if (span.paint == stringPaint || span.paint == commentPaint) {
        if (syntaxSpans == null) syntaxSpans = new ArrayList<>();
        syntaxSpans.add(span);
      }
    }
    return syntaxSpans != null ? syntaxSpans : Collections.emptyList();
  }
}
