package com.yn.sodiumeditor.test;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

final class TestFileLogger {
  private TestFileLogger() {}

  static File newLogFile(String relativePath) throws Exception {
    File file = tryCreateInUserDir(relativePath);
    if (file != null) return file;

    file = tryCreateInCwd(relativePath);
    if (file != null) return file;

    file = tryCreateInTmp(relativePath);
    if (file != null) return file;

    throw new IllegalStateException("Failed to create log file in user.dir, cwd, or tmp.");
  }

  static void append(File file, String msg) throws Exception {
    Files.write(
        file.toPath(),
        (msg + "\n").getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
  }

  static void appendThrowable(File file, Throwable t) throws Exception {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    pw.flush();
    append(file, sw.toString());
  }

  private static File tryCreateInUserDir(String relativePath) {
    String userDir = System.getProperty("user.dir");
    if (userDir == null || userDir.isEmpty()) return null;
    return tryCreate(new File(userDir), relativePath);
  }

  private static File tryCreateInCwd(String relativePath) {
    return tryCreate(new File("").getAbsoluteFile(), relativePath);
  }

  private static File tryCreateInTmp(String relativePath) {
    String tmpDir = System.getProperty("java.io.tmpdir");
    if (tmpDir == null || tmpDir.isEmpty()) return null;
    return tryCreate(new File(tmpDir), relativePath);
  }

  private static File tryCreate(File base, String relativePath) {
    try {
      File file = new File(base, relativePath);
      File parent = file.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) return null;
      Files.write(
          file.toPath(),
          new byte[0],
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
      return file;
    } catch (Throwable ignored) {
      return null;
    }
  }
}
