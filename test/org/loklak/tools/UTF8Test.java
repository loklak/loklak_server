package org.loklak.tools;

import org.loklak.tools.UTF8;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
/**
 * These unit-tests test org.loklak.tools.UTF8.java
 */
public class UTF8Test{

	@Test
	public void testGetBytes(){
		UTF8 utf8 = new UTF8();
		String str = "loklak-server";
		byte [] returnVal = utf8.getBytes(str);
		String s = "";
		for(int i=0; i<returnVal.length; ++i){
			s += String.valueOf(returnVal[i]);
		}
		assertEquals("1081111071089710745115101114118101114", s);
	}

	@Test
	public void testGetBytes2() {
		StringBuilder str = new StringBuilder("loklak-server");
		UTF8 utf8 = new UTF8();
		byte[] returnVal = utf8.getBytes(str);
		String s = "";
		for(int i=0; i<returnVal.length; ++i){
			s += String.valueOf(returnVal[i]);
		}
		assertEquals("1081111071089710745115101114118101114", s);
	}

	@Test
	public void testBytestoString() {
		UTF8 utf8 = new UTF8();
		byte[] param = "loklak-server".getBytes();
		String returnVal = utf8.String(param);
		assertEquals("loklak-server", returnVal);
	}

	@Test
	public void testBytestoString2() {
		UTF8 utf8 = new UTF8();
		byte[] param = "loklak-server".getBytes();
		int offset = 0;
		int length = param.length;
		String returnVal = utf8.String(param, offset, length);
		assertEquals("loklak-server", returnVal);
	}
}
