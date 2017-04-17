package org.loklak.harvester;


import org.json.JSONObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

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
    public void whenTryParseVideoFromInputStreamShouldCheckThatJSONObjectGood() throws IOException {
        JSONObject video = YoutubeScraper.parseVideo(new URL("https://www.youtube.com/watch?v=KVGRN7Z7T1A").openStream());
        assertThat(video.get("html_title").toString(), is("[\"Iggy Azalea - Team (Explicit) - YouTube\"]"));
    }

    /**
     * When try parse video from buffered reader should check that method return valid json.
     * @throws IOException if some error happened with open stream for reading data.
     */
    @Test
    public void whneTryParseVideoFromBufferedReaderShouldCheckThatIsGoodJsonObjectReturn() throws IOException {
        JSONObject video = YoutubeScraper.parseVideo(new BufferedReader(new InputStreamReader(new URL("https://www.youtube.com/watch?v=KVGRN7Z7T1A").openStream())));
        assertThat(video.get("html_title").toString(), is("[\"Iggy Azalea - Team (Explicit) - YouTube\"]"));
    }
}
