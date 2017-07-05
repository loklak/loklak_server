package org.loklak.http;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;

public class ClientConnectionTest {

    @Test
    public void metaUrlExtractorTest() throws IOException {
        HashMap<String, String> linkMap = new HashMap<>();

        linkMap.put("http://yacy.net", "http://yacy.net/en/index.html");

        for (HashMap.Entry<String, String> pair : linkMap.entrySet()) {
            assertEquals(pair.getValue(), ClientConnection.getRedirect(pair.getKey()));
        }
    }

}
