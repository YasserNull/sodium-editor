package com.yn.sodiumeditor.renderer;

import androidx.annotation.Nullable;
import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.highlight.Highlite;
import com.yn.sodiumeditor.utils.HighlightUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Manages caching of highlight spans and line states.
 */
public class HighlightCacheManager {
    private final SodiumEditor editor;
    private final Highlite highlite;

    public final LinkedHashMap<Integer, List<HighliteRender.HighlightSpan>> highlightCache = new LinkedHashMap<Integer, List<HighliteRender.HighlightSpan>>(1000, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, List<HighliteRender.HighlightSpan>> eldest) { return size() > 1000; }
    };
    public final LinkedHashMap<Integer, Boolean> blockCommentEndStateCache = new LinkedHashMap<Integer, Boolean>(1000, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) { return size() > 1000; }
    };
    public final LinkedHashMap<Integer, Integer> stringEndStateCache = new LinkedHashMap<Integer, Integer>(1000, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) { return size() > 1000; }
    };

    public HighlightCacheManager(SodiumEditor editor, Highlite highlite) {
        this.editor = editor;
        this.highlite = highlite;
    }

    public void ensureHighlightCacheForVisibleRange(int startLine, int endLine, @Nullable Map<Integer, String> directLines) {
        if (highlite.rules.highlightRules.isEmpty()) return;
        boolean needRegex = !highlite.rules.regexHighlightRules.isEmpty();
        boolean inBlock = false; int strState = 0;

        for (int i = startLine; i <= endLine; i++) {
            List<HighliteRender.HighlightSpan> cached = highlightCache.get(i);
            if (cached != null && !needRegex) continue;
            if (cached != null) {
                boolean cInBlock = blockCommentEndStateCache.getOrDefault(i, false);
                int cStrState = stringEndStateCache.getOrDefault(i, 0);
                if (cInBlock == inBlock && cStrState == strState) continue;
            }

            String line = editor.windowRender.getLineTextForRenderWithDirect(i, directLines);
            if (line == null) line = "";

            HighliteRender.HighlightLineState sState = getLineStateAtStart(i);
            HighliteRender.HighlightRule sRule = highlite.rules.stringHighlightRule != null ? highlite.rules.stringHighlightRule : highlite.rules.whitespaceStringRule;
            HighliteRender.HighlightRule bRule = highlite.rules.blockCommentHighlightRule != null ? highlite.rules.blockCommentHighlightRule : highlite.rules.whitespaceCommentRule;

            HighliteRender.LineParseResult res = highlite.parser.parseLineForSyntax(line, sState.inBlockComment, sState.stringState, sRule, bRule, true);
            List<HighliteRender.HighlightSpan> spans = new ArrayList<>(res.spans);

            if (needRegex && !line.isEmpty()) {
                for (HighliteRender.HighlightRule rule : highlite.rules.regexHighlightRules) {
                    Matcher m = rule.pattern.matcher(line);
                    while (m.find()) {
                        if (m.start() == m.end()) continue;
                        HighliteRender.HighlightSpan span = new HighliteRender.HighlightSpan(m.start(), m.end(), rule.paint);
                        if (!HighlightUtils.hasOverlap(span, spans)) spans.add(span);
                    }
                }
            }
            if (spans.size() > 1) Collections.sort(spans, (s1, s2) -> Integer.compare(s1.start, s2.start));
            highlightCache.put(i, spans);
            if (highlite.isBlockCommentsEnabled) blockCommentEndStateCache.put(i, res.endsInBlockComment);
            stringEndStateCache.put(i, res.endsInStringState);
            inBlock = res.endsInBlockComment; strState = res.endsInStringState;
        }
    }

    public HighliteRender.HighlightLineState getLineStateAtStart(int globalLine) {
        if (globalLine <= editor.windowRender.windowStartLine) return new HighliteRender.HighlightLineState(false, 0);
        Boolean cBlock = blockCommentEndStateCache.get(globalLine - 1);
        Integer cStr = stringEndStateCache.get(globalLine - 1);
        if (cBlock != null && cStr != null) return new HighliteRender.HighlightLineState(cBlock, cStr);

        boolean inBlock = false; int strState = 0;
        for (int line = editor.windowRender.windowStartLine; line < globalLine; line++) {
            Boolean cb = blockCommentEndStateCache.get(line);
            Integer cs = stringEndStateCache.get(line);
            if (cb != null && cs != null) { inBlock = cb; strState = cs; continue; }
            String txt = editor.windowRender.getLineTextForRender(line);
            HighliteRender.LineParseResult res = highlite.parser.parseLineForSyntax(txt == null ? "" : txt, inBlock, strState, null, null, false);
            inBlock = res.endsInBlockComment; strState = res.endsInStringState;
            blockCommentEndStateCache.put(line, inBlock); stringEndStateCache.put(line, strState);
        }
        return new HighliteRender.HighlightLineState(inBlock, strState);
    }
}
