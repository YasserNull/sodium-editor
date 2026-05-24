package com.yn.sodiumeditor.test;

import static org.junit.Assert.assertTrue;

import com.yn.sodiumeditor.utils.TextArabicUtils;
import org.junit.Test;

/** Guards connected Arabic-script input such as Arabic and Persian from typed char animation. */
public class CharAnimationArabicScriptInputGuardTest {

    @Test
    public void containsArabicScript_shouldDetectArabicAndPersianLetters() {
        String arabic = "مرحبا";
        String persian = "پژگچ";

        assertTrue(TextArabicUtils.containsArabicScript(arabic, 0, arabic.length()));
        assertTrue(TextArabicUtils.containsArabicScript(persian, 0, persian.length()));
    }
}
