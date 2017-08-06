/**
 *  MessageEntry
 *  Copyright 22.02.2015 by Michael Peter Christen, @0rb1t3r
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; wo even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package org.loklak.objects;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.loklak.api.search.ShortlinkFromTweetServlet;
import org.loklak.data.Classifier;
import org.loklak.data.DAO;
import org.loklak.data.Classifier.Category;
import org.loklak.data.Classifier.Context;
import org.loklak.geo.GeoMark;
import org.loklak.geo.LocationSource;
import org.loklak.harvester.Post;
import org.loklak.objects.QueryEntry.PlaceContext;
import org.loklak.tools.bayes.Classification;

import org.unbescape.html.HtmlEscape;

public class MessageEntry extends AbstractObjectEntry {

    public static final String RICH_TEXT_SEPARATOR = "\n***\n";
    public Map<Context, Classification<String, Category>> classifier;

    // two or more
    public final static Pattern SPACEX_PATTERN = Pattern.compile("  +");
    // right boundary must be space or ) since others may appear in urls
    public final static Pattern URL_PATTERN = Pattern.compile("(?:\\b|^)(https?://[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|])");
    // left boundary must be space since the @ is itself a boundary
    public final static Pattern USER_PATTERN = Pattern.compile("(?:[ (]|^)(@..*?)(?:\\b|$)");
    // left boundary must be a space since the # is itself a boundary
    final static Pattern HASHTAG_PATTERN = Pattern.compile("(?:[ (]|^)(#..*?)(?:\\b|$)");
    public final static Pattern A_END_TAG = Pattern.compile("</a>");
    public final static Pattern QUOT_TAG = Pattern.compile("&quot;");
    public final static Pattern AMP_TAG = Pattern.compile("&amp;");

    public Classifier.Category getClassifier(Classifier.Context context) {
        if (this.classifier == null) return null;
        Classification<String, Category> classification = this.classifier.get(context);
        if (classification == null) return null;
        return classification.getCategory() == Classifier.Category.NONE ? null : classification.getCategory();
    }

    public double getClassifierProbability(Classifier.Context context) {
        if (this.classifier == null) return 0.0d;
        Classification<String, Category> classification = this.classifier.get(context);
        if (classification == null) return 0.0d;
        return classification.getProbability();
    }

    public static List<String> extract(String text, Pattern p, int regexGroup) {
        Matcher m = p.matcher(text);
        List<String> dataList = new ArrayList<String>();

        while (m.find()) {
            dataList.add(m.group(regexGroup));
        }
        for (String r: dataList) {
            text.replaceAll(r, "");
        }
        return dataList;
    }

   public List<String> extractLinks(String text) {
        return extract(text, URL_PATTERN, 1);
    }

    public List<String> extractUsers(String text) {
        return extract(text, USER_PATTERN, 1);
    }

    public List<String> extractHashtags(String text) {
        return extract(text, HASHTAG_PATTERN, 1);
    }

    public List<String> extractHosts(List<String> links) {
        List<String> hosts = new ArrayList<String>();
        for (String u: links) {
            try {
                URL url = new URL(u);
                hosts.add(url.getHost().toLowerCase());
            } catch (MalformedURLException e) {}
        }
        return hosts;
    }

    public JSONObject toJSON() {
        return this;
    }

    public static String html2utf8(String s) {
        String unescape = HtmlEscape.unescapeHtml(s);
        unescape = A_END_TAG.matcher(unescape).replaceAll("");
        unescape = unescape.trim().replaceAll(" +", " ");
        return unescape;
    }

    public static String html2utf8Custom(String str) {
        String s = str;
        int p, q;
        // hex coding &#
        try {
            while ((p = s.indexOf("&#")) >= 0) {
                q = s.indexOf(';', p + 2);
                if (q < p) break;
                String charcode = s.substring(p + 2, q);
                int unicode = s.charAt(0) == 'x' ? Integer.parseInt(charcode.substring(1), 16) : Integer.parseInt(charcode);
                s = s.substring(0, p) + ((unicode == 10 || unicode == 13) ? "\n" : ((char) unicode)) + s.substring(q + 1);
            }
        } catch (Throwable e) {
            DAO.severe(e);
        }
        // octal coding \\u
        try {
            while ((p = s.indexOf("\\u")) >= 0 && s.length() >= p + 6) {
                char r = ((char) Integer.parseInt(s.substring(p + 2, p + 6), 8));
                if (r < ' ') r = ' ';
                s = s.substring(0, p) + r + s.substring(p + 6);
            }
        } catch (Throwable e) {
            DAO.severe(e);
        }
        // remove tags
        s = A_END_TAG.matcher(s).replaceAll("");
        s = QUOT_TAG.matcher(s).replaceAll("\"");
        s = AMP_TAG.matcher(s).replaceAll("&");
        // remove funny symbols
        StringBuilder clean = new StringBuilder(s.length() + 5);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (((int) c) == 8232 || c == '\n' || c == '\r') clean.append("\n");
            else if (c < ' ') clean.append(' ');
            else clean.append(c);
        }
        // remove double spaces
        return clean.toString().trim().replaceAll(" +", " ");
    }

    public Set<String> getLinksVideo(List<String> links, Set<String> videoList) {
        boolean endLink = false;
        boolean withLinkTerm = false;

        if(videoList == null) {
            videoList = new HashSet<String>();
        }
        for (String link: links) {
            endLink = link.endsWith(".mp4") || link.endsWith(".m4v");
            withLinkTerm = link.indexOf("vimeo.com") > 0
                    || link.indexOf("youtube.com") > 0
                    || link.indexOf("youtu.be") > 0
                    || link.indexOf("vine.co") > 0
                    || link.indexOf("ted.com") > 0;

            if (endLink || withLinkTerm) {
                videoList.add(link);
            }
        }
        return videoList;
    }

    public Set<String> getLinksAudio(List<String> links, Set<String> audioLinks) {
        boolean endLink = false;
        boolean withLinkTerm = false;

        if(audioLinks == null) {
            audioLinks = new HashSet<String>();
        }
        for (String link: links) {
            endLink = link.endsWith(".mp3");
            withLinkTerm = link.indexOf("soundcloud.com") > 0;

            if (withLinkTerm || endLink) audioLinks.add(link);
        }
        return audioLinks;
    }

    public Set<String> getLinksImage(List<String> links, Set<String> imageLinks) {
        boolean endLink = false;
        boolean withLinkTerm = false;

        if(imageLinks == null) {
            imageLinks = new HashSet<String>();
        }
        for (String link: links) {
            endLink = link.endsWith(".jpg")
                    || link.endsWith(".jpeg")
                    || link.endsWith(".png")
                    || link.endsWith(".gif");
            //TODO: need to fix this
            withLinkTerm = link.indexOf("flickr.com") > 0
                    || link.indexOf("instagram.com") > 0
                    || link.indexOf("imgur.com") > 0
                    || link.indexOf("giphy.com") > 0
                    || link.indexOf("pic.twitter.com") > 0;
            
            if (endLink || withLinkTerm) imageLinks.add(link);
        }
        return imageLinks;
    }

    public TextLinkMap getText(final int iflinkexceedslength, final String urlstub, String text, String[] linksArray, String postId) {
        // check if we shall replace shortlinks
        TextLinkMap tlm = new TextLinkMap();
        tlm.text = text;
        String[] links = linksArray;
        if (links != null) {
            for (int nth = 0; nth < links.length; nth++) {
                String link = links[nth];
                if (link.length() > iflinkexceedslength) {
                    // Here is bug/ quickfix to solve bug. Need to fix for issue https://github.com/loklak/loklak_server/issues/1346
                    //if (!DAO.existMessage(this.getPostId())) break;
                    String shortlink = urlstub + "/x?id=" + postId +
                        (nth == 0 ? "" : ShortlinkFromTweetServlet.SHORTLINK_COUNTER_SEPERATOR + Integer.toString(nth));
                    if (shortlink.length() < link.length()) {
                        tlm.text = tlm.text.replace(link, shortlink);
                        if (!shortlink.equals(link)) {
                            int stublen = shortlink.length() + 3;
                            if (link.length() >= stublen) link = link.substring(0, shortlink.length()) + "...";
                            tlm.short2long.put(shortlink, link);
                        }
                    }
                }
            }
        }
        return tlm;
    }

    public static class TextLinkMap {
        public String text;
        public JSONObject short2long;
        public TextLinkMap() {
            text = "";
            this.short2long = new JSONObject(true);
        }
        public String toString() {
            return this.text;
        }
    }
}
