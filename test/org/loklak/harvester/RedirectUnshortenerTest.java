package org.loklak.harvester;

import java.util.HashMap;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import org.loklak.harvester.RedirectUnshortener;


public class RedirectUnshortenerTest {

    @Test
    public void testRedirectUnshortener() {
        HashMap<String, String> shortlinkMap = new HashMap<>();

        shortlinkMap.put("https://goo.gl/r4pNHk", "https://www.youtube.com/watch?v=RRlOCHD-p8Q");
        shortlinkMap.put("http://tinyurl.com/loklak-server", "https://github.com/loklak/loklak_server/wiki");
        shortlinkMap.put("https://t.co/raRRie3ado", "https://medium.com/javascript-scene/angular-2-vs-react-the-ultimate-dance-off-60e7dfbc379c");
        shortlinkMap.put("http://bit.ly/2tvkGa1", "https://medium.com/@keekri17/my-adventures-at-foss-asia-d1a5d462b792");
        shortlinkMap.put("http://ow.ly/P9SX30ddSTI", "https://github.com/loklak/loklak_server/issues/1284");
        shortlinkMap.put("http://bit.do/blog-fossasia", "http://blog.fossasia.org/");
        shortlinkMap.put("http://fb.me/4lcXZsyyO", "https://www.facebook.com/permalink.php?story_fbid=1550262581900846&id=1381813572079082");
        shortlinkMap.put("http://wp.me/sf2B5-shorten", "http://en.blog.wordpress.com/2009/08/14/shorten/");
        shortlinkMap.put("https://is.gd/gyk3VT", "https://github.com/fossasia/");
        shortlinkMap.put("https://is.gd/Lros16", "https://twitter.com/lklknt");

        for (HashMap.Entry<String, String> entry: shortlinkMap.entrySet()) {
            String unshortenedURL = RedirectUnshortener.unShorten(entry.getKey());
            assertEquals(entry.getValue(), unshortenedURL);
        }
    }

}
