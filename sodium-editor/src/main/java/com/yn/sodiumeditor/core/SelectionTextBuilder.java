package com.yn.sodiumeditor.core;

import androidx.annotation.Nullable;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.List;

/**
 * Core class for building selected text.
 * Handles text extraction from file or window buffer.
 */
public final class SelectionTextBuilder {

    public interface SelectionCallback {
        int comparePos(int sL, int sC, int eL, int eC);
        boolean isFileCleared();
        @Nullable java.io.File getSourceFile();
        boolean isIndexReady();
        long[] getLineOffsets();
        Object getLineOffsetsLock();
        long findLineStartByteByScanning(java.io.RandomAccessFile raf, int line) throws Exception;
        java.util.HashMap<Integer, String> getModifiedLines();
        int getWindowStartLine();
        java.util.List<String> getLinesWindow();
        String getLineTextForRender(int line);
        int getCopyCutMaxChars();
        java.nio.charset.Charset getFileCharset();
    }

    private final SelectionCallback callback;

    public SelectionTextBuilder(SelectionCallback callback) {
        this.callback = callback;
    }

    public String buildSelectedTextBlocking(int sL, int sC, int eL, int eC) {
        if (callback.comparePos(sL, sC, eL, eC) > 0) {
            int tL = sL, tC = sC;
            sL = eL;
            sC = eC;
            eL = tL;
            eC = tC;
        }

        if (callback.getSourceFile() == null || callback.isFileCleared()) {
            return buildSelectedTextFromWindow(sL, sC, eL, eC);
        }

        boolean fullyInWindow = (sL >= callback.getWindowStartLine()) && (eL < callback.getWindowStartLine() + callback.getLinesWindow().size());
        if (fullyInWindow) {
            return buildSelectedTextFromWindow(sL, sC, eL, eC);
        }

        try (RandomAccessFile raf = new RandomAccessFile(callback.getSourceFile(), "r")) {
            long startByte;
            if (callback.isIndexReady()) {
                synchronized (callback.getLineOffsetsLock()) {
                    if (sL >= 0 && sL < callback.getLineOffsets().length) {
                        startByte = callback.getLineOffsets()[sL];
                    } else {
                        startByte = raf.length();
                    }
                }
            } else {
                startByte = callback.findLineStartByteByScanning(raf, sL);
            }

            raf.seek(startByte);
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(raf.getFD()), callback.getFileCharset()), 8192)) {

                StringBuilder sb = new StringBuilder();
                for (int L = sL; L <= eL; L++) {
                    String fileLine = br.readLine();
                    if (fileLine == null) fileLine = "";

                    String ln;
                    synchronized (callback.getModifiedLines()) {
                        ln = callback.getModifiedLines().containsKey(L) ? callback.getModifiedLines().get(L) : fileLine;
                    }
                    if (ln == null) ln = "";

                    int startIdx = (L == sL) ? Math.min(sC, ln.length()) : 0;
                    int endIdx = (L == eL) ? Math.min(eC, ln.length()) : ln.length();
                    if (endIdx > startIdx) sb.append(ln, startIdx, endIdx);
                    if (L < eL) sb.append('\n');

                    if (sb.length() > callback.getCopyCutMaxChars()) {
                        return sb.substring(0, callback.getCopyCutMaxChars());
                    }
                }
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String buildSelectedTextFromWindow(int sL, int sC, int eL, int eC) {
        StringBuilder sb = new StringBuilder();
        synchronized (callback.getLinesWindow()) {
            for (int L = sL; L <= eL; L++) {
                int local = L - callback.getWindowStartLine();
                String ln = (local >= 0 && local < callback.getLinesWindow().size()) ? callback.getLinesWindow().get(local) : "";
                if (ln == null) ln = "";
                int startIdx = (L == sL) ? Math.min(sC, ln.length()) : 0;
                int endIdx = (L == eL) ? Math.min(eC, ln.length()) : ln.length();
                if (endIdx > startIdx) sb.append(ln, startIdx, endIdx);
                if (L < eL) sb.append('\n');

                if (sb.length() > callback.getCopyCutMaxChars()) {
                    return sb.substring(0, callback.getCopyCutMaxChars());
                }
            }
        }
        return sb.toString();
    }
}
