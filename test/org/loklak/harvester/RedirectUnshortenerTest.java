package org.loklak.harvester;

import java.util.HashMap;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import org.loklak.harvester.RedirectUnshortener;


public class RedirectUnshortenerTest {

    @Test
    public void testRedirectUnshortener() {
        HashMap<String, String> shortlinkMap = new HashMap<>();
        shortlinkMap.put("http://tmblr.co/Z6YPNx1jL1hHK", "http://myjourneymyway.tumblr.com/post/117390619732/first-time-making-loklak-my-grandma-would-be#_=_");
        shortlinkMap.put("http://dlvr.it/8kTDbJ", "http://islamtimes.org/en/doc/news/442892/?utm_source=dlvr.it&utm_medium=twitter");
        shortlinkMap.put("http://j.mp/1vfXKr0", "http://yacy.net/en/index.html?utm_content=buffera7e9f&utm_medium=social&utm_source=twitter.com&utm_campaign=buffer");
        shortlinkMap.put("http://goo.gl/R9CVuz", "http://www.savingsgator.com/coupon/ebay-new-authentic-gucci-crystal-gg-canvasleather-joy-boston-bag-handbag/");
        shortlinkMap.put("http://tinyurl.com/pcp7fu4", "http://www.giladiskon.com/deals/blitzmegaplex.com/Blitzmegaplex-Special-Member-Card-Promo-Diskon-50");
        shortlinkMap.put("http://t.co/E3w7s2qdBT", "http://mostviralfeed.com/what-lady-gaga-actually-looks-like");
        shortlinkMap.put("http://bit.ly/1h9gTTT", "http://www.ebay.com/sch/i.html?_from=R40&_nkw=Pontiac+Catalina+1963&_in_kw=1&_ex_kw=&_sacat=0&_okw=&_oexkw=&_adv=1&_udlo=&_udhi=&LH_BIN=1&LH_PayPal=1&LH_Time=1&_ftrt=901&_ftrv=1&_sabdlo=&_sabdhi=&_samilow=&_samihi=&_sadis=200&_fpos=&_fsct=&LH_SALE_CURRENCY=0&_fss=1&_saslop=1&_sasl=&_fsradio=LH_SellerWithStore%3D1&_sop=12&_dmd=1&_ipg=50&rmvSB=true");
        shortlinkMap.put("http://ow.ly/JtOPA", "http://yacy.net/en/index.html");
        shortlinkMap.put("http://bit.do/ZwrT", "http://yacy.net/en/index.html");
        shortlinkMap.put("http://fb.me/4lcXZsyyO", "https://www.facebook.com/permalink.php?story_fbid=1550262581900846&id=1381813572079082");
        shortlinkMap.put("http://wp.me/p4yQu6-za0", "https://www.oklahomafoodandmusic.com/?p=135160");

        for (HashMap.Entry<String, String> entry: shortlinkMap.entrySet()) {
            String unshortenedURL = RedirectUnshortener.unShorten(entry.getKey());
            assertEquals(entry.getValue(), unshortenedURL);
        }
    }

}
