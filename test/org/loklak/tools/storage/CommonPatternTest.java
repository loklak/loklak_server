package org.loklak.tools.storage;

import org.loklak.tools.CommonPattern;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
/**
 * This test file tests org.loklak.tools.CommonPattern.java
 */
public class CommonPatternTest {
    
    @Test
    public void commonPattern() {

        CommonPattern commonPattern = new CommonPattern();
        
        assertEquals(" ", commonPattern.SPACE.toString());
        assertEquals(",", commonPattern.COMMA.toString());
        assertEquals(";", commonPattern.SEMICOLON.toString());
        assertEquals(":", commonPattern.DOUBLEPOINT.toString());
        assertEquals("/", commonPattern.SLASH.toString());
        assertEquals("\\|", commonPattern.PIPE.toString());
        assertEquals("\\\\", commonPattern.BACKSLASH.toString());
        assertEquals("\\?", commonPattern.QUESTION.toString());
        assertEquals("&", commonPattern.AMP.toString());
        assertEquals("\\.", commonPattern.DOT.toString());
        assertEquals("\n", commonPattern.NEWLINE.toString());
        assertEquals("_", commonPattern.UNDERSCORE.toString());
        assertEquals("\t", commonPattern.TAB.toString());
    }
}
