package com.yn.sodiumeditor.core;

import com.yn.sodiumeditor.SodiumEditor;
import com.yn.sodiumeditor.core.SelectionTextRange;
import java.util.ArrayList;

/**
 * Handles smart selection logic like double-tap word/bracket/quote selection.
 */
public class SmartSelection {
    private final SodiumEditor editor;
    private final Selection selection;

    public SmartSelection(SodiumEditor editor, Selection selection) {
        this.editor = editor;
        this.selection = selection;
    }

    public void selectWordAtCursor() {
        String line = editor.textRender.getLineTextForRender(editor.cursor.cursorLine);
        if (line == null || line.isEmpty()) return;
        int pos = Math.max(0, Math.min(editor.cursor.cursorChar, line.length()));
        if (pos == line.length() && pos > 0) pos--;
        if (pos < 0 || pos >= line.length() || Character.isWhitespace(line.charAt(pos))) return;

        int[] bounds = editor.computeWordBounds(line, pos);
        if (bounds != null && bounds[0] != bounds[1]) {
            selection.setSelection(editor.cursor.cursorLine, bounds[0], editor.cursor.cursorLine, bounds[1]);
        }
    }

    public void selectLineAtCursor() {
        String line = editor.textRender.getLineTextForRender(editor.cursor.cursorLine);
        if (line == null) return;
        selection.setSelection(editor.cursor.cursorLine, 0, editor.cursor.cursorLine, line.length());
    }

    public ArrayList<SelectionTextRange> buildDoubleTapCandidates(String line, int charIndex, int wStart, int wEnd) {
        ArrayList<SelectionTextRange> out = new ArrayList<>(6);
        if (line == null) return out;
        int len = line.length();
        selection.wordFinder.addSelectionCandidate(out, wStart, wEnd, len);

        SelectionTextRange quote = selection.quoteFinder.findEnclosingQuoteRange(line, charIndex);
        if (quote != null) {
            selection.wordFinder.addSelectionCandidate(out, quote.start + 1, quote.end, len);
            selection.wordFinder.addSelectionCandidate(out, quote.start, quote.end + 1, len);
        }

        SelectionTextRange bracket = selection.quoteFinder.findEnclosingBracketRange(line, charIndex);
        if (bracket != null) {
            selection.wordFinder.addSelectionCandidate(out, bracket.start + 1, bracket.end, len);
            selection.wordFinder.addSelectionCandidate(out, bracket.start, bracket.end + 1, len);
        }
        return out;
    }

    public boolean applySmartDoubleTapSelection(int line, int charIndex, String lineText) {
        if (lineText == null) return false;
        ArrayList<SelectionTextRange> candidates = selection.wordFinder.buildSmartWordCandidates(lineText, charIndex);
        
        int wStart = -1, wEnd = -1;
        if (!candidates.isEmpty()) {
            wStart = candidates.get(0).start;
            wEnd = candidates.get(0).end;
        }

        SelectionTextRange quote = selection.quoteFinder.findEnclosingQuoteRange(lineText, charIndex);
        if (quote != null) {
            selection.wordFinder.addSelectionCandidate(candidates, quote.start + 1, quote.end, lineText.length());
            selection.wordFinder.addSelectionCandidate(candidates, quote.start, quote.end + 1, lineText.length());
        }

        SelectionTextRange bracket = selection.quoteFinder.findEnclosingBracketRange(lineText, charIndex);
        if (bracket != null) {
            selection.wordFinder.addSelectionCandidate(candidates, bracket.start + 1, bracket.end, lineText.length());
            selection.wordFinder.addSelectionCandidate(candidates, bracket.start, bracket.end + 1, lineText.length());
        }
        
        if (candidates.isEmpty()) return false;

        boolean sameAnchor = (wStart != -1) && (line == selection.lastDoubleTapLine && wStart == selection.lastDoubleTapWordStart && wEnd == selection.lastDoubleTapWordEnd);
        int currentIdx = selection.findSelectionCandidateIndex(line, candidates);
        int nextIdx = (sameAnchor || currentIdx >= 0) ? (currentIdx >= 0 ? (currentIdx + 1) % candidates.size() : 0) : 0;

        SelectionTextRange pick = candidates.get(nextIdx);
        selection.selStartLine = selection.selEndLine = line;
        selection.selStartChar = pick.start;
        selection.selEndChar = pick.end;
        selection.hasSelection = true;
        selection.isSelectAllActive = false;
        selection.isEntireFileSelected = false;
        selection.selecting = true;
        editor.cursor.cursorLine = line;
        editor.cursor.cursorChar = selection.selEndChar;
        selection.lastDoubleTapLine = line;
        selection.lastDoubleTapWordStart = (wStart != -1) ? wStart : pick.start;
        selection.lastDoubleTapWordEnd = (wEnd != -1) ? wEnd : pick.end;
        selection.lastDoubleTapStage = nextIdx;
        selection.syncToState();
        return true;
    }
}
