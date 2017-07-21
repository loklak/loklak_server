package org.loklak.harvester;


import org.json.JSONObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import org.loklak.data.DAO;
import org.loklak.http.ClientConnection;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author evrnsky
 * @version 0.1
 * @since 17.04.2017
 *
 * This is unit test for YoutubeScrapper.
 */
public class YoutubeScraperTest {

    /**
     * When try parse video from input stream should check that video parsed.
     * @throws IOException if some problem with open stream for reading data.
     */
    @Test
    public void parseFromInputStreamTest() throws IOException {
        YoutubeScraper ytubeScrape = new YoutubeScraper();
        String url = "https://www.youtube.com/watch?v=KVGRN7Z7T1A";
        InputStream fis = null;

        try {
            fis = new URL(url).openStream();

        } catch (IOException e) {
            DAO.log("YoutubeScraperTest.parseFromInputStreamTest() failed to connect to network. url:" + url);
        }

        JSONObject video = ytubeScrape.parseVideo(fis, "url", url);
        assertThat(video.get("html_title").toString(), is("[\"Iggy Azalea - Team (Explicit) - YouTube\"]"));
    }

    /**
     * When try parse video from buffered reader should check that method return valid json.
     * @throws IOException if some error happened with open stream for reading data.
     */
    @Test
    public void parseFromBufferedReaderTest() throws IOException {
        YoutubeScraper ytubeScrape = new YoutubeScraper();
        String url = "https://www.youtube.com/watch?v=KVGRN7Z7T1A";
        BufferedReader br = null;

        try {
            ClientConnection connection = new ClientConnection(url);
            //Check Network issue
            assertThat(connection.getStatusCode(), is(200));

            br = ytubeScrape.getHtml(connection);
        } catch (IOException e) {
            DAO.log("YoutubeScraperTest.parseFromBufferedReaderTest()() failed to connect to network. url:" + url);
        }

        JSONObject video = ytubeScrape.parseVideo(br, "url", "https://www.youtube.com/watch?v=KVGRN7Z7T1A");
        assertThat(video.get("html_title").toString(), is("[\"Iggy Azalea - Team (Explicit) - YouTube\"]"));
    }

}
