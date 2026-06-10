package com.yn.sodiumeditor.core.highlight;

import android.graphics.Paint;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.StringEndResult;
import com.yn.sodiumeditor.renderer.HighlightRender;
import com.yn.sodiumeditor.utils.HighlightUtils;
import java.util.ArrayList;
import java.util.List;

/** Core engine for parsing lines into highlight spans. */
public class HighlightParser {
  private final SodiumEditor editor;
  private final Highlight highlight;

  public HighlightParser(SodiumEditor editor, Highlight highlight) {
    this.editor = editor;
    this.highlight = highlight;
  }

  public HighlightRender.LineParseResult parseLineForSyntax(
      String line,
      boolean inBlock,
      int strState,
      HighlightRender.HighlightRule strRule,
      HighlightRender.HighlightRule blockRule,
      boolean collectSpans) {

    List<HighlightRender.HighlightSpan> spans = new ArrayList<>();
    int len = line.length();
    int i = 0;

    if (!highlight.isBlockCommentsEnabled) inBlock = false;
    Highlight.StringHighlightConfig activeStateConfig =
        highlight.getStringHighlightConfigForState(strState);
    if (activeStateConfig != null && !activeStateConfig.multiLine) strState = 0;
    if (activeStateConfig == null
        && strState == com.yn.sodiumeditor.core.highlight.Highlight.STRING_STATE_BACKTICK
        && !highlight.isBacktickStringsEnabled) strState = 0;
    if (activeStateConfig == null
        && strState == com.yn.sodiumeditor.core.highlight.Highlight.STRING_STATE_TRIPLE
        && !highlight.isTripleQuoteStringsEnabled) strState = 0;
    if (activeStateConfig == null
        && strState != 0
        && !highlight.isMultiLineStringsEnabled
        && strState != com.yn.sodiumeditor.core.highlight.Highlight.STRING_STATE_TRIPLE)
      strState = 0;

    while (i < len) {
      if (inBlock) {
        int end = highlight.findConfiguredBlockCommentEnd(line, i);
        if (end < 0) {
          if (collectSpans && blockRule != null && highlight.isBlockCommentsEnabled && len > 0)
            spans.add(new HighlightRender.HighlightSpan(0, len, blockRule.paint));
          return new HighlightRender.LineParseResult(spans, true, 0);
        }
        int blockEnd = end + highlight.blockCommentEndDelimiter.length();
        if (collectSpans && blockRule != null && highlight.isBlockCommentsEnabled)
          spans.add(new HighlightRender.HighlightSpan(0, blockEnd, blockRule.paint));
        i = blockEnd;
        inBlock = false;
        continue;
      }

      if (strState != 0) {
        Highlight.StringHighlightConfig config = highlight.getStringHighlightConfigForState(strState);
        StringEndResult res =
            config != null
                ? findStringEndForConfig(line, i, config)
                : findStringEndForState(line, i, strState);
        HighlightRender.HighlightRule activeStringRule = config != null ? config.rule : strRule;
        if (res.found) {
          if (collectSpans && activeStringRule != null)
            spans.add(new HighlightRender.HighlightSpan(0, res.endIndex, activeStringRule.paint));
          i = res.endIndex;
          strState = 0;
          continue;
        }
        if (collectSpans && activeStringRule != null && len > 0)
          spans.add(new HighlightRender.HighlightSpan(0, len, activeStringRule.paint));
        return new HighlightRender.LineParseResult(
            spans, false, config == null || config.multiLine ? strState : 0);
      }

      if (isLineCommentStart(line, i)) {
        if (collectSpans && len > i) {
          Paint p =
              (highlight.rules.lineCommentHighlightRule != null)
                  ? highlight.rules.lineCommentHighlightRule.paint
                  : ((blockRule != null) ? blockRule.paint : editor.textRender.paint);
          spans.add(new HighlightRender.HighlightSpan(i, len, p));
        }
        return new HighlightRender.LineParseResult(spans, false, 0);
      }

      char c = line.charAt(i);
      Highlight.StringHighlightConfig configuredString = highlight.findStringHighlightStart(line, i);
      if (configuredString != null) {
        int start = i + configuredString.delimiter.length();
        int end = highlight.findConfiguredStringEnd(line, start, configuredString.delimiter);
        if (end >= 0) {
          int stringEnd = end + configuredString.delimiter.length();
          if (collectSpans)
            spans.add(new HighlightRender.HighlightSpan(i, stringEnd, configuredString.rule.paint));
          i = stringEnd;
          continue;
        }
        if (collectSpans && len > i)
          spans.add(new HighlightRender.HighlightSpan(i, len, configuredString.rule.paint));
        return new HighlightRender.LineParseResult(
            spans, false, configuredString.multiLine ? configuredString.state : 0);
      }

      if (isTripleQuoteStart(line, i) && !HighlightUtils.isEscaped(line, i)) {
        int end = HighlightUtils.findTripleQuoteEnd(line, i + 3);
        if (end >= 0) {
          if (collectSpans && strRule != null)
            spans.add(new HighlightRender.HighlightSpan(i, end + 3, strRule.paint));
          i = end + 3;
          continue;
        }
        if (highlight.isTripleQuoteStringsEnabled) {
          if (collectSpans && strRule != null && len > 0)
            spans.add(new HighlightRender.HighlightSpan(i, len, strRule.paint));
          return new HighlightRender.LineParseResult(
              spans, false, com.yn.sodiumeditor.core.highlight.Highlight.STRING_STATE_TRIPLE);
        }
      }

      if (isStringDelimiter(c) && !HighlightUtils.isEscaped(line, i)) {
        int end = HighlightUtils.findStringEnd(line, i + 1, c);
        if (end >= 0) {
          if (collectSpans && strRule != null)
            spans.add(new HighlightRender.HighlightSpan(i, end + 1, strRule.paint));
          i = end + 1;
          continue;
        }
        if (highlight.isMultiLineStringsEnabled) {
          if (collectSpans && strRule != null && len > 0)
            spans.add(new HighlightRender.HighlightSpan(i, len, strRule.paint));
          return new HighlightRender.LineParseResult(spans, false, getStringStateForDelimiter(c));
        }
      }

      if (highlight.isBlockCommentsEnabled && highlight.isConfiguredBlockCommentStart(line, i)) {
        int end =
            highlight.findConfiguredBlockCommentEnd(
                line, i + highlight.blockCommentStartDelimiter.length());
        if (end < 0) {
          if (collectSpans && blockRule != null && len > 0)
            spans.add(new HighlightRender.HighlightSpan(i, len, blockRule.paint));
          return new HighlightRender.LineParseResult(spans, true, 0);
        }
        int blockEnd = end + highlight.blockCommentEndDelimiter.length();
        if (collectSpans && blockRule != null)
          spans.add(new HighlightRender.HighlightSpan(i, blockEnd, blockRule.paint));
        i = blockEnd;
        continue;
      }
      i++;
    }
    return new HighlightRender.LineParseResult(spans, inBlock, strState);
  }

  public boolean isLineCommentStart(String line, int idx) {
    if (idx < 0 || idx >= line.length() || highlight.rules.lineCommentDelimiters.isEmpty())
      return false;
    for (String token : highlight.rules.lineCommentDelimiters) {
      int tLen = token.length();
      if (tLen == 0 || idx + tLen > line.length()) continue;
      if (line.regionMatches(idx, token, 0, tLen) && !HighlightUtils.isTokenEscaped(line, idx))
        return true;
    }
    return false;
  }

  public boolean isStringDelimiter(char c) {
    return c == '"' || c == '\'' || (c == '`' && highlight.isBacktickStringsEnabled);
  }

  public boolean isTripleQuoteStart(String line, int idx) {
    return highlight.isTripleQuoteStringsEnabled
        && idx + 2 < line.length()
        && line.charAt(idx) == '"'
        && line.charAt(idx + 1) == '"'
        && line.charAt(idx + 2) == '"';
  }

  public int getStringStateForDelimiter(char c) {
    if (c == '"') return com.yn.sodiumeditor.core.highlight.Highlight.STRING_STATE_DOUBLE;
    if (c == '\'') return com.yn.sodiumeditor.core.highlight.Highlight.STRING_STATE_SINGLE;
    return com.yn.sodiumeditor.core.highlight.Highlight.STRING_STATE_BACKTICK;
  }

  public StringEndResult findStringEndForState(String line, int start, int state) {
    if (state == com.yn.sodiumeditor.core.highlight.Highlight.STRING_STATE_TRIPLE) {
      int end = HighlightUtils.findTripleQuoteEnd(line, start);
      return new StringEndResult(end >= 0, end >= 0 ? end + 3 : start);
    }
    char d =
        (state == com.yn.sodiumeditor.core.highlight.Highlight.STRING_STATE_SINGLE)
            ? '\''
            : (state == com.yn.sodiumeditor.core.highlight.Highlight.STRING_STATE_BACKTICK
                ? '`'
                : '"');
    int end = HighlightUtils.findStringEnd(line, start, d);
    return new StringEndResult(end >= 0, end >= 0 ? end + 1 : start);
  }

  public StringEndResult findStringEndForConfig(
      String line, int start, Highlight.StringHighlightConfig config) {
    int end = highlight.findConfiguredStringEnd(line, start, config.delimiter);
    return new StringEndResult(end >= 0, end >= 0 ? end + config.delimiter.length() : start);
  }
}
