package com.yn.sodiumeditor.core;

import android.graphics.Paint;
import android.graphics.Typeface;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.renderer.HighliteRender;
import com.yn.sodiumeditor.utils.HighlightUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages syntax highlighting rules and delimiters.
 */
public class HighlightRules {
    private final SodiumEditor editor;
    private final Highlite highlite;

    public final List<HighliteRender.HighlightRule> highlightRules = new ArrayList<>();
    public final ArrayList<HighliteRender.HighlightRule> regexHighlightRules = new ArrayList<>();
    public final ArrayList<String> lineCommentDelimiters = new ArrayList<>();
    
    public HighliteRender.HighlightRule stringHighlightRule;
    public HighliteRender.HighlightRule blockCommentHighlightRule;
    public HighliteRender.HighlightRule lineCommentHighlightRule;
    public HighliteRender.HighlightRule whitespaceStringRule;
    public HighliteRender.HighlightRule whitespaceCommentRule;

    public HighlightRules(SodiumEditor editor, Highlite highlite) {
        this.editor = editor;
        this.highlite = highlite;
        initWhitespaceRules();
    }

    private void initWhitespaceRules() {
        float size = editor.textRender.paint.getTextSize();
        Typeface tf = editor.textRender.paint.getTypeface();
        whitespaceStringRule = new HighliteRender.HighlightRule("", SodiumEditor.STYLE_NORMAL, 0xFF000000, size, tf, false, HighliteRender.HighlightRuleType.STRING);
        whitespaceCommentRule = new HighliteRender.HighlightRule("", SodiumEditor.STYLE_NORMAL, 0xFF000000, size, tf, false, HighliteRender.HighlightRuleType.BLOCK_COMMENT);
    }

    public void addHighlightRule(String regex, int style, int color, boolean underline) {
        HighliteRender.HighlightRuleType type = HighliteRender.HighlightRuleType.REGEX;
        if (regex.equals(Highlite.RULE_STRING)) type = HighliteRender.HighlightRuleType.STRING;
        else if (regex.equals(Highlite.RULE_BLOCK_COMMENT)) type = HighliteRender.HighlightRuleType.BLOCK_COMMENT;
        else if (HighlightUtils.isLineCommentRegex(regex)) type = HighliteRender.HighlightRuleType.LINE_COMMENT;

        HighliteRender.HighlightRule rule = new HighliteRender.HighlightRule(regex, style, color, editor.textRender.paint.getTextSize(), editor.textRender.paint.getTypeface(), underline, type);
        if (type == HighliteRender.HighlightRuleType.LINE_COMMENT) {
            addLineCommentDelimiter(extractLineCommentDelimiter(regex));
            lineCommentHighlightRule = rule;
        } else {
            highlightRules.add(rule);
            if (type == HighliteRender.HighlightRuleType.STRING) stringHighlightRule = rule;
            else if (type == HighliteRender.HighlightRuleType.BLOCK_COMMENT) blockCommentHighlightRule = rule;
            else regexHighlightRules.add(rule);
        }
        highlite.clearHighlightCaches();
    }

    public void clearHighlightRules() {
        highlightRules.clear(); regexHighlightRules.clear(); lineCommentDelimiters.clear();
        stringHighlightRule = null; blockCommentHighlightRule = null; lineCommentHighlightRule = null;
        highlite.clearHighlightCaches();
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
        if (d != null && !d.isEmpty() && !lineCommentDelimiters.contains(d)) lineCommentDelimiters.add(d);
    }

    public boolean isEmpty() {
        return highlightRules.isEmpty() && regexHighlightRules.isEmpty() && lineCommentDelimiters.isEmpty();
    }
    public List<HighliteRender.HighlightSpan> calculateSyntaxSpansForLine(String line, int globalLine) {
    if (editor.getLogicalLineLength(globalLine, line) > editor.highliteRender.maxSyntaxLineLength) {
      return Collections.emptyList();
    }
    if (line.isEmpty()) {
      return Collections.emptyList();
    }

    HighliteRender.HighlightLineState startState = highlite.getLineStateAtStart(globalLine);
    HighliteRender.LineParseResult parseResult =
        highlite.parseLineForSyntax(
            line,
            startState.inBlockComment,
            startState.stringState,
            whitespaceStringRule,
            whitespaceCommentRule,
            true);

    if (globalLine >= editor.textRender.windowStartLine && globalLine < editor.textRender.windowStartLine + editor.textRender.linesWindow.size()) {
      if (highlite.isBlockCommentsEnabled) {
        highlite.blockCommentEndStateCache.put(globalLine, parseResult.endsInBlockComment);
      }
      highlite.stringEndStateCache.put(globalLine, parseResult.endsInStringState);
    }

    List<HighliteRender.HighlightSpan> spans = parseResult.spans;
    if (spans.size() > 1) {
      Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
    }
    return spans;
  }

  public List<HighliteRender.HighlightSpan> getWhitespaceGuideSyntaxSpans(String line, int globalLine) {
    HighliteRender.HighlightRule stringRule = highlite.stringHighlightRule;
    HighliteRender.HighlightRule commentRule = highlite.blockCommentHighlightRule;
    if (stringRule == null && commentRule == null) {
      return calculateSyntaxSpansForLine(line, globalLine);
    }

    List<HighliteRender.HighlightSpan> spans = highlite.highlightCache.get(globalLine);
    if (spans == null) {
      spans = highlite.calculateSpansForLine(line, globalLine);
      highlite.highlightCache.put(globalLine, spans);
    }
    if (spans.isEmpty()) return Collections.emptyList();

    Paint stringPaint = (stringRule != null) ? stringRule.paint : null;
    Paint commentPaint = (commentRule != null) ? commentRule.paint : null;
    if (stringPaint == null && commentPaint == null) return Collections.emptyList();

    ArrayList<HighliteRender.HighlightSpan> syntaxSpans = null;
    for (HighliteRender.HighlightSpan span : spans) {
      if (span.paint == stringPaint || span.paint == commentPaint) {
        if (syntaxSpans == null) syntaxSpans = new ArrayList<>();
        syntaxSpans.add(span);
      }
    }
    return syntaxSpans != null ? syntaxSpans : Collections.emptyList();
  }
}
