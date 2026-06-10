package com.yn.sodiumeditor.core.view.events;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OnDrawClearPolicyTest {

  @Test
  public void shouldClear_whenBackgroundIsTransparent() {
    assertTrue(onDraw.shouldClearBeforeDraw(false, null));
  }

  @Test
  public void shouldNotClear_whenBackgroundColorSet() {
    assertFalse(onDraw.shouldClearBeforeDraw(true, null));
  }

  @Test
  public void shouldNotClear_whenBackgroundBitmapSet() {
    assertFalse(onDraw.shouldClearBeforeDraw(false, new Object()));
  }
}
