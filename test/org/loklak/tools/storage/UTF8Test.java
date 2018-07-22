package org.loklak.tools.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import org.junit.Test;
import org.loklak.tools.UTF8;

public class UTF8Test {

    private UTF8 utf8 = new UTF8();
    private final byte[] ll_param = "loklak".getBytes();

    @Test
    public void stringTest1() {
        byte[] passed_param1 = null;
        byte[] passed_param2 = ll_param;
        String return_val1 = utf8.String(passed_param1);
        String return_val2 = utf8.String(passed_param2);
        assertEquals("", return_val1);
        assertEquals("loklak", return_val2);
    }

    @Test
    public void stringTest2() {
        byte[] passed_param = ll_param;
        int offset = 0;
        int length = passed_param.length;
        String return_val = utf8.String(passed_param, offset, length);
        assertEquals("loklak", return_val);
    }

    @Test
    public void getBytesTest1() {
        String passed_param1 = null;
        String passed_param2 = "loklak";
        byte[] return_val1 = utf8.getBytes(passed_param1);
        byte[] return_val2 = utf8.getBytes(passed_param2);
        assertArrayEquals(null, return_val1);
        assertArrayEquals(ll_param, return_val2);
    }

    @Test
    public void getBytesTest2() {
        StringBuilder passed_param1 = null;
        StringBuilder passed_param2 = new StringBuilder("loklak");
        byte[] return_val1 = utf8.getBytes(passed_param1);
        byte[] return_val2 = utf8.getBytes(passed_param2);
        assertArrayEquals(null, return_val1);
        assertArrayEquals(ll_param, return_val2);
    }
}
