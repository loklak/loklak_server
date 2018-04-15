package org.loklak.tools.storage;

import org.loklak.tools.ASCII;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
/**
 * These unit-tests test org.loklak.tools.ASCII.java
 */
public class AsciiTest {
    /**
    * This method tests 'compare' method of org.loklak.tools.ASCII.java
    */
    @Test
    public void testCompare() {

        ASCII ascii = new ASCII(true);

        String s0 = "abc";
        String s1 = "xyz";
        int returnedValue1 = ascii.compare(s0, s1);
        int returnedValue2 = ascii.compare(s1, s0);
        int returnedValue3 = ascii.compare(null, null);
        int returnedValue4 = ascii.compare(s0, null);
        int returnedValue5 = ascii.compare(null, s1);
        assertEquals(returnedValue1, -1);
        assertEquals(returnedValue2, 1);
        assertEquals(returnedValue3, 0);
        assertEquals(returnedValue4, 1);
        assertEquals(returnedValue5, -1);
    }

    /**
    * This method tests 'equals' method of org.loklak.tools.ASCII.java
    */
    @Test
    public void testEquals() {

        ASCII ascii = new ASCII(true);

        String s0 = "abc";
        String s1 = "xyz";
        boolean returnedValue1 = ascii.equals(s0, s1);
        boolean returnedValue2 = ascii.equals(s1, s0);
        boolean returnedValue3 = ascii.equals(null, null);
        boolean returnedValue4 = ascii.equals(s0, null);
        boolean returnedValue5 = ascii.equals(null, s1);
        assertEquals(returnedValue1, false);
        assertEquals(returnedValue2, false);
        assertEquals(returnedValue3, true);
        assertEquals(returnedValue4, false);
        assertEquals(returnedValue5, false);
    }
}
