package com.yn.sodiumeditor.renderer;

import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.highlight.Highlight;
import com.yn.sodiumeditor.utils.HighlightUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/** Manages caching of highlight spans and line states. */
public class HighlightCacheManager {
  private final SodiumEditor editor;
  private final Highlight highlight;

  public final LinkedHashMap<Integer, List<HighlightRender.HighlightSpan>> highlightCache =
      new LinkedHashMap<Integer, List<HighlightRender.HighlightSpan>>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(
            Map.Entry<Integer, List<HighlightRender.HighlightSpan>> eldest) {
          return size() > 1000;
        }
      };
  public final LinkedHashMap<Integer, Boolean> blockCommentEndStateCache =
      new LinkedHashMap<Integer, Boolean>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
          return size() > 1000;
        }
      };
  public final LinkedHashMap<Integer, Integer> stringEndStateCache =
      new LinkedHashMap<Integer, Integer>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
          return size() > 1000;
        }
      };

  public HighlightCacheManager(SodiumEditor editor, Highlight highlight) {
    this.editor = editor;
    this.highlight = highlight;
  }

  public void ensureHighlightCacheForVisibleRange(
      int startLine, int endLine, @Nullable Map<Integer, String> directLines) {
    if (highlight.rules.isEmpty()) return;
    boolean needRegex = !highlight.rules.regexHighlightRules.isEmpty();
    HighlightRender.HighlightLineState rangeStartState = getLineStateAtStart(startLine);
    boolean inBlock = rangeStartState.inBlockComment;
    int strState = rangeStartState.stringState;

    for (int i = startLine; i <= endLine; i++) {
      String line = editor.windowRender.getLineTextForRenderWithDirect(i, directLines);
      if (line == null) line = "";

      HighlightRender.HighlightRule sRule = highlight.rules.stringHighlightRule;
      HighlightRender.HighlightRule bRule = highlight.rules.blockCommentHighlightRule;

      HighlightRender.LineParseResult res =
          highlight.parser.parseLineForSyntax(line, inBlock, strState, sRule, bRule, true);
      List<HighlightRender.HighlightSpan> spans = new ArrayList<>(res.spans);

      if (needRegex && !line.isEmpty()) {
        for (HighlightRender.HighlightRule rule : highlight.rules.regexHighlightRules) {
          Matcher m = rule.pattern.matcher(line);
          while (m.find()) {
            if (m.start() == m.end()) continue;
            HighlightRender.HighlightSpan span =
                new HighlightRender.HighlightSpan(m.start(), m.end(), rule.paint);
            if (!HighlightUtils.hasOverlap(span, spans)) spans.add(span);
          }
        }
      }
      if (spans.size() > 1)
        Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
      highlightCache.put(i, spans);
      if (highlight.isBlockCommentsEnabled) blockCommentEndStateCache.put(i, res.endsInBlockComment);
      stringEndStateCache.put(i, res.endsInStringState);
      inBlock = res.endsInBlockComment;
      strState = res.endsInStringState;
    }
  }

  public HighlightRender.HighlightLineState getLineStateAtStart(int globalLine) {
    if (globalLine <= editor.windowRender.windowStartLine)
      return new HighlightRender.HighlightLineState(false, 0);
    Boolean cBlock = blockCommentEndStateCache.get(globalLine - 1);
    Integer cStr = stringEndStateCache.get(globalLine - 1);
    if (cBlock != null && cStr != null) return new HighlightRender.HighlightLineState(cBlock, cStr);

    boolean inBlock = false;
    int strState = 0;
    for (int line = editor.windowRender.windowStartLine; line < globalLine; line++) {
      Boolean cb = blockCommentEndStateCache.get(line);
      Integer cs = stringEndStateCache.get(line);
      if (cb != null && cs != null) {
        inBlock = cb;
        strState = cs;
        continue;
      }
      String txt = editor.windowRender.getLineTextForRender(line);
      HighlightRender.LineParseResult res =
          highlight.parser.parseLineForSyntax(
              txt == null ? "" : txt, inBlock, strState, null, null, false);
      inBlock = res.endsInBlockComment;
      strState = res.endsInStringState;
      blockCommentEndStateCache.put(line, inBlock);
      stringEndStateCache.put(line, strState);
    }
    return new HighlightRender.HighlightLineState(inBlock, strState);
  }
}
