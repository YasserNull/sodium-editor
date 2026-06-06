package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertEquals;

import com.yn.sodiumeditor.io.ByteRangeLocator;
import com.yn.sodiumeditor.io.EditOp;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

/** Reproduces select-all delete leaving the first line on disk. */
public class ByteRangeSelectAllRegressionTest {

  @Test
  public void computeByteRangeByScanning_selectAllFromLineZeroStartsAtFileStart() throws Exception {
    File file = File.createTempFile("sodium-select-all", ".txt");
    file.deleteOnExit();
    String text = "package com.yn.sodiumeditor;\n\nimport android.view.View;\n";
    Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));

    ByteRangeLocator locator = new ByteRangeLocator(null);
    EditOp.RangeBytes range = locator.computeByteRangeByScanning(file, 0, 0, 3, 0);

    assertEquals("BUG: select-all delete must start removing at byte 0.", 0L, range.startByte);
    assertEquals(
        "BUG: select-all delete ending at the trailing empty line should remove the whole file.",
        file.length(),
        range.endByte);
  }
}
