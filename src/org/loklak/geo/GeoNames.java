/**
 *  GeoNames.java
 *  Copyright 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 16.05.2010 on http://yacy.net
 *
 *  This file is part of YaCy Content Integration
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package org.loklak.geo;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.loklak.data.DAO;
import org.loklak.tools.CommonPattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GeoNames {

    private final Map<Integer, GeoLocation> id2loc;
    private final HashMap<Integer, List<Integer>> hash2ids;
    private final Set<Integer> stopwordHashes;
    private final Map<String, double[]> countryCenter; // mapping from the  ISO-3166 country code to [longitude, latitude], the country central
    private Map<String, String> iso3166toCountry;

    public static class CountryBounds {
        public double lon_west = 0.0;
        public double lon_east = 0.0;
        public double lat_north = 0.0;
        public double lat_south = 0.0;
        public void extend(GeoLocation loc) {
            if (loc.lon() < lon_west) {
                lon_west = loc.lon();
            }
            if (loc.lon() > lon_east) {
                lon_east = loc.lon();
            }
            if (loc.lat() > lat_north) {
                lat_north = loc.lat();
            }
            if (loc.lat() < lat_south) {
                lat_south = loc.lat();
            }
        }
    }

    public String getCountryName(String iso3166cc) {
        return this.iso3166toCountry.get(iso3166cc.toUpperCase());
    }

    public GeoNames(final File cities1000_zip, final File iso3166json, long minPopulation) throws IOException{

        // load iso3166 info
        this.iso3166toCountry = new HashMap<>();
        try {
            //String jsonString = new String(Files.readAllBytes(iso3166json.toPath()), StandardCharsets.UTF_8);
            ObjectMapper jsonMapper = new ObjectMapper(DAO.jsonFactory);
            JsonNode j = jsonMapper.readTree(iso3166json);
            for (JsonNode n: j) {
                // contains name,alpha-2,alpha-3,country-code,iso_3166-2,region-code,sub-region-code
                String name = n.get("name").textValue();
                String cc = n.get("alpha-2").textValue();
                this.iso3166toCountry.put(cc, name);
            }
        } catch (IOException e) {
            this.iso3166toCountry = new HashMap<String, String>();
        }

        // this is a processing of the cities1000.zip file from http://download.geonames.org/export/dump/

        this.id2loc = new HashMap<>();
        this.hash2ids = new HashMap<>();
        this.stopwordHashes = new HashSet<>();
        this.countryCenter = new HashMap<>();
        Map<String, CountryBounds> countryBounds = new HashMap<>();

        if ( cities1000_zip == null || !cities1000_zip.exists() ) {
        	throw new IOException("GeoNames: file does not exist!");
        }
        ZipFile zf = null;
        BufferedReader reader = null;
        try {
            zf = new ZipFile(cities1000_zip);
            String entryName = cities1000_zip.getName();
            entryName = entryName.substring(0, entryName.length() - 3) + "txt";
            final ZipEntry ze = zf.getEntry(entryName);
            final InputStream is = zf.getInputStream(ze);
            reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        } catch (final IOException e ) {
        	throw new IOException("GeoNames: Error when decompressing cities1000.zip!", e);
        }

/* parse this fields:
---------------------------------------------------
00 geonameid         : integer id of record in geonames database
01 name              : name of geographical point (utf8) varchar(200)
02 asciiname         : name of geographical point in plain ascii characters, varchar(200)
03 alternatenames    : alternatenames, comma separated varchar(5000)
04 latitude          : latitude in decimal degrees (wgs84)
05 longitude         : longitude in decimal degrees (wgs84)
06 feature class     : see http://www.geonames.org/export/codes.html, char(1)
07 feature code      : see http://www.geonames.org/export/codes.html, varchar(10)
08 country code      : ISO-3166 2-letter country code, 2 characters
09 cc2               : alternate country codes, comma separated, ISO-3166 2-letter country code, 60 characters
10 admin1 code       : fipscode (subject to change to iso code), see exceptions below, see file admin1Codes.txt for display names of this code; varchar(20)
11 admin2 code       : code for the second administrative division, a county in the US, see file admin2Codes.txt; varchar(80)
12 admin3 code       : code for third level administrative division, varchar(20)
13 admin4 code       : code for fourth level administrative division, varchar(20)
14 population        : bigint (8 byte int)
15 elevation         : in meters, integer
16 dem               : digital elevation model, srtm3 or gtopo30, average elevation of 3''x3'' (ca 90mx90m) or 30''x30'' (ca 900mx900m) area in meters, integer. srtm processed by cgiar/ciat.
17 timezone          : the timezone id (see file timeZone.txt) varchar(40)
18 modification date : date of last modification in yyyy-MM-dd format
*/
        try {
            String line;
            String[] fields;
            while ( (line = reader.readLine()) != null ) {
                if ( line.isEmpty() ) {
                    continue;
                }
                fields = CommonPattern.TAB.split(line);
                final long population = Long.parseLong(fields[14]);
                if (minPopulation > 0 && population < minPopulation) {
                    continue;
                }
                final int geonameid = Integer.parseInt(fields[0]);
                Set<String> locnames = new LinkedHashSet<>();
                locnames.add(fields[1]);
                locnames.add(fields[2]);
                for (final String s : CommonPattern.COMMA.split(fields[3])) {
                    locnames.add(s);
                }
                ArrayList<String> locnamess = new ArrayList<>(locnames.size());
                locnamess.addAll(locnames);
                String cc = fields[8]; //ISO-3166

                final GeoLocation geoLocation = new GeoLocation(Float.parseFloat(fields[4]), Float.parseFloat(fields[5]), locnamess, cc);
                geoLocation.setPopulation(population);
                this.id2loc.put(geonameid, geoLocation);
                for (final String name : locnames) {
                    if (name.length() < 4) {
                        continue;
                    }
                    String normalized = normalize(name);
                    int lochash = normalized.hashCode();
                    List<Integer> locs = this.hash2ids.get(lochash);
                    if (locs == null) {
                        locs = new ArrayList<Integer>(1); this.hash2ids.put(lochash, locs);
                    }
                    if (!locs.contains(geonameid)) {
                        locs.add(geonameid);
                    }
                }

                // update the country bounds
                CountryBounds bounds = countryBounds.get(cc);
                if (bounds == null) {
                    bounds = new CountryBounds(); countryBounds.put(cc, bounds);
                }
                bounds.extend(geoLocation);
            }
            if (reader != null) {
                reader.close();
            }
            if (zf != null) {
                zf.close();
            }
        } catch (final IOException e ) {}

        // calculate the center of the countries
        for (Map.Entry<String, CountryBounds> country: countryBounds.entrySet()) {
            this.countryCenter.put(country.getKey(), new double[]{(country.getValue().lon_west - country.getValue().lon_east) / 2.0, (country.getValue().lat_north - country.getValue().lat_south) / 2.0}); // [longitude, latitude]
        }

        // finally create a statistic which names appear very often to have fill-word heuristic
        TreeMap<Integer, Set<Integer>> stat = new TreeMap<>(); // a mapping from number of occurrences of location name hashes to a set of location name hashes
        for (Map.Entry<Integer, List<Integer>> entry: this.hash2ids.entrySet()) {
            int occurrences = entry.getValue().size();
            Set<Integer> hashes = stat.get(occurrences);
            if (hashes == null) {
                hashes = new HashSet<Integer>(); stat.put(occurrences, hashes);
            }
            hashes.add(entry.getKey());
        }
        // we consider 3/4 of this list as fill-word (approx 300): those with the most occurrences
        int good = stat.size() / 4;
        Iterator<Map.Entry<Integer, Set<Integer>>> i = stat.entrySet().iterator();
        for (int j = 0; j < good; j++) {
            i.next(); // 'eat away' the good entries.
        }
        while (i.hasNext()) {
            Set<Integer> morehashes = i.next().getValue();
            this.stopwordHashes.addAll(morehashes);
        }
    }

    /**
     * Analyse a text for the presence of a location name
     * @param text to be analyzed
     * @param tags which could be location names (in favor of texts in the text string)
     * @param maxlength the maximum number of words in the text that are tried to be combined to a location name
     * @param salt a salt which is used for fuzzyness of the mark location
     * @return
     */
    public GeoMark analyse(final String text, final String[] tags, final int maxlength, final String salt) {
        GeoLocation loc = geocode(text, tags, maxlength);
        if (loc != null) {
            return new GeoMark(loc, salt);
        }
        return reverse_geocode(text);
    }

    /**
     * find the geolocation for coordinates in a text
     * @param text
     * @return the location if one was found or null;
     */
    private GeoMark reverse_geocode(final String text) {
        for (String t: text.split(" ")) {
            // test if t is possibly a coordinate
            if (t.length() < 9) {
                continue;
            }
            String[] c = t.split(",");
            if (c.length != 2) {
                continue;
            }
            try {
                // expected: lat,lon
                double lat = Double.parseDouble(c[0]);
                double lon = Double.parseDouble(c[1]);
                GeoMark mark = cityNear(lat, lon);
                if (mark == null) {
                    continue;
                }
                return mark;
            } catch (NumberFormatException e) {
                continue;
            }
           // iPhone: 37.313690,-122.022911 as well
        }
        return null;
    }

    /**
     * try to find a place close to the given location
     * @param lat
     * @param lon
     * @return
     */
    public GeoMark cityNear(final double lat, final double lon) {
        if (lat < -90.0d || lat > 90.0d) {
            return null;
        }
        if (lon < -180.0d || lon > 180.0d) {
            return null;
        }
        double mind = 40000000.0d;
        GeoLocation ming = null;
        for (GeoLocation g: id2loc.values()) {
            double d = IntegerGeoPoint.distance(lat, lon, g.lat(), g.lon());
            if (d < mind) {
                mind = d;
                ming = g;
            }
        }
        return new GeoMark(ming, lat, lon);
    }

    public List<GeoLocation> citiesInBB(double lon_west, double lat_south, double lon_east, double lat_north) {
        if (lat_south < -90.0d || lat_south > 90.0d) {
            return null;
        }
        if (lat_north < -90.0d || lat_north > 90.0d) {
            return null;
        }
        if (lon_west < -180.0d || lon_west > 180.0d) {
            return null;
        }
        if (lon_east < -180.0d || lon_east > 180.0d) {
            return null;
        }
        List<GeoLocation> l = new ArrayList<>();
        for (GeoLocation g: id2loc.values()) {
            if (g.lat() < lat_south) {
                continue;
            }
            if (g.lat() > lat_north) {
                continue;
            }
            if (g.lon() < lon_west) {
                continue;
            }
            if (g.lon() > lon_east) {
                continue;
            }
            l.add(g);
        }
        return l;
    }

    public GeoLocation getLargestCity(double lon_west, double lat_south, double lon_east, double lat_north) {
        assert lon_west < lon_east;
        assert lat_north > lat_south;
        // find largest city around to compute a 'near:' operator for twitter
        List<GeoLocation> cities = citiesInBB(lon_west, lat_south, lon_east, lat_north);
        GeoLocation largestCity = null;
        for (GeoLocation city: cities) {
            if (largestCity == null || city.getPopulation() > largestCity.getPopulation()) {
                largestCity = city;
            }
        }
        return largestCity;
    }

    /**
     * find the geolocation for place names given in a text and/or hashtags
     * @param text
     * @param tags
     * @param maxlength the maximum number of words in the text that are tried to be combined to a location name
     * @return the location if one was found or null;
     */
    private GeoLocation geocode(final String text, final String[] tags, final int maxlength) {
        // first attempt: use the tags to get a location. We prefer small population because it is more specific
        LinkedHashMap<Integer, String> mix = nomix(tags);
        GeoMatch geolocTag = geomatch(mix, false);

        // second attempt: use a mix of words from the input text. We prefer large population because that produces better hits
        mix = mix(split(text), maxlength);
        GeoMatch geolocText = geomatch(mix, true);

        // full fail case:
        if (geolocTag == null && geolocText == null) {
            return null;
        }

        // evaluate the result
        if (geolocText == null) {
            return geolocTag.loc;
        }
        Integer geolocTextHash = geolocText == null ? null : normalize(geolocText.name).hashCode();
        boolean geolocTextIsStopword = geolocTextHash == null ? true : this.stopwordHashes.contains(geolocTextHash);
        if (geolocTag == null) {
            return geolocTextIsStopword ? null : geolocText.loc; // if we have only a match in the text but that is a stopword, we omit the result completely (too bad)
        }

        // simple case: both are equal
        if (geolocText.equals(geolocTag)) {
            return geolocTag.loc;
        }
        // special case: names are equal, but not location. This is a glitch in the prefer-population difference, in this case we prefer the largest place
        if (geolocText.name.equals(geolocTag.name)) {
            return geolocText.loc;
        }

        // in case that both location types are detected, evaluate the nature of the location names, consider:
        // (1) number of words of the name, (2) stop word characteristics, (3) population of location, (4) length of name string

        // (1) number of words
        if (normalize(geolocText.name).indexOf(' ') > 0) {
            return geolocText.loc; // this one is more specific
        }

        // (2) stop word characteristic
        Integer geolocTagHash = geolocTag == null ? null : normalize(geolocTag.name).hashCode();
        boolean geolocTagIsStopword = geolocTagHash == null ? true : this.stopwordHashes.contains(geolocTagHash);
        if ( geolocTagIsStopword && !geolocTextIsStopword) {
            return geolocText.loc;
        }
        if (!geolocTagIsStopword &&  geolocTextIsStopword) {
            return geolocTag.loc;
        }

        int pivotpopulation = 100000;
        // (3) in case that the places have too less population we give up. Danger would be high to make a mistake.
        if (geolocTag.loc.getPopulation() < pivotpopulation && geolocText.loc.getPopulation() < pivotpopulation) {
            return null;
        }

        // (4) length of name string
        int pivotlength = 6; // one name must be larger then the pivot, has larger population than the other place and the other place name must be smaller than the pivot
        if (geolocTag.name.length() > pivotlength && // we prefer tag names over text names by omitting the population constraint here
            geolocText.name.length() < pivotlength) {
            return geolocTag.loc;
        }
        if (geolocText.name.length() > pivotlength &&
                geolocText.loc.getPopulation() > geolocTag.loc.getPopulation() &&
                geolocTag.name.length() < pivotlength) {
            return geolocText.loc;
        }

        // finally decide on population
        return geolocTag.loc.getPopulation() >= geolocText.loc.getPopulation() || geolocTextIsStopword ? geolocTag.loc : geolocText.loc;
    }

    private static class GeoMatch {
        public final String name;
        public final GeoLocation loc;
        public GeoMatch(final String name, final GeoLocation loc) {
            this.name = name;
            this.loc = loc;
        }
    }

    /**
     * Match a given sequence mix with geolocations. First all locations matching with sequences larger than one
     * word are collected. If the result of this collection is not empty, the largest plase (measured by population)
     * is returned. If no such location can be found, matching with single-word locations is attempted and then also
     * the largest place is returned.
     * @param mix the sequence mix
     * @return the largest place, matching with the mix, several-word matchings preferred
     */
    private GeoMatch geomatch(LinkedHashMap<Integer, String> mix, final boolean preferLargePopulation) {
        TreeMap<Long, GeoMatch> cand = new TreeMap<>();
        int hitcount = 0;
        for (Map.Entry<Integer, String> entry: mix.entrySet()) {
            if (cand.size() > 0 && entry.getValue().indexOf(' ') < 0) {
            return preferNonStopwordLocation(cand.values(), preferLargePopulation); // if we have location matches for place names with more than one word, return the largest place (measured by the population)
            }
            List<Integer> locs = this.hash2ids.get(entry.getKey());
            if (locs == null || locs.size() == 0) {
                continue;
            }
            for (Integer i: locs) {
                GeoLocation loc = this.id2loc.get(i);
                if (loc != null) {
                    for (String name: loc.getNames()) {
                        if (normalize(entry.getValue()).equals(normalize(name))) {
                            cand.put(hitcount++ -loc.getPopulation(), new GeoMatch(entry.getValue(), loc));
                            break;
                        }
                    }
                }
            }
        }
        // finally return the largest place (if any found)
        return cand.size() > 0 ? preferNonStopwordLocation(cand.values(), preferLargePopulation) : null;
    }

    private GeoMatch preferNonStopwordLocation(Collection<GeoMatch> geolocs, final boolean preferLargePopulation) {
        if (!preferLargePopulation) {
            // reverse the list
            List<GeoMatch> a = new ArrayList<>(geolocs.size());
            for (GeoMatch g: geolocs) a.add(0, g);
            geolocs = a;
        }
        if (geolocs == null || geolocs.size() == 0) {
            return null;
        }
        for (GeoMatch loc: geolocs) {
            for (String name: loc.loc.getNames()) {
                if (!this.stopwordHashes.contains(normalize(name).hashCode())) {
                    return loc;
                }
            }
        }
        return geolocs.iterator().next();
    }

    /**
     * Helper function which generates the same result as the mix method using a list of single-word tags.
     * Because these words are not sorted in any way the creation of the mix result is much easier.
     * @param tags
     * @return a hastable where the key is the hash code of the lowercase of the tags and the value is the original tag
     */
    public static LinkedHashMap<Integer, String> nomix(final String[] tags) {
        LinkedHashMap<Integer, String> r = new LinkedHashMap<>();
        if (tags != null) for (String t: tags) r.put(normalize(t).hashCode(), t);
        return r;
    }

    /**
     * Create sequences of words from a word token list. The sequence has also normalized (lowercased) names and original Text.
     * The creates sequences is a full set of all sequence combinations which are possible from the given tokens.
     * The text sequences are then reverse-sorted by the length of the combines text sequence. The result is a hashtable
     * where the key is the hash of the lowercase text sequence and the value is the original text without lowercase.
     * This allows a rapid matching with stored word sequences using the hash of the sequence
     * @param text
     * @param maxlength the maximum sequence length, counts number of words
     * @return an ordered hashtable where the order is the reverse length of the sequence, the key is the hash of the lowercase string sequence and the value is the original word sequence
     */
    public static LinkedHashMap<Integer, String> mix(final ArrayList<Map.Entry<String, String>> text, final int maxlength) {
        Map<Integer, Map<Integer, String>> a = new TreeMap<>(); // must be a TreeMap provide order on reverse word count
        for (int i = 0; i < text.size(); i++) {
            for (int x = 1; x <= Math.min(text.size() - i, maxlength); x++) {
                StringBuilder o = new StringBuilder(10 * x);
                StringBuilder l = new StringBuilder(10 * x);
                for (int j = 0; j < x; j++) {
                    Map.Entry<String, String> word = text.get(i + j);
                    if (j != 0) {
                        l.append(' ');
                        o.append(' ');
                    }
                    l.append(word.getKey());
                    o.append(word.getValue());
                }
                Map<Integer, String> m = a.get(-x);
                if (m == null) {
                    m = new HashMap<>(); a.put(-x, m);
                }
                m.put(l.toString().hashCode(), o.toString());
            }
        }

        // now order the maps by the number of words
        LinkedHashMap<Integer, String> r = new LinkedHashMap<>();
        for (Map<Integer, String> m: a.values()) r.putAll(m);
        return r;
    }

    /**
     * Split the text into word tokens. The tokens are lower-cased. To maintain the original spelling
     * of the word without lowercasing them, the original word is attached too.
     * @param text
     * @return a List of Map.Entry objects where the key is the lower-cased word token and the value is the original word
     */
    public static ArrayList<Map.Entry<String, String>> split(final String text) {
        ArrayList<Map.Entry<String, String>> a = new ArrayList<>(1 + text.length() / 4);
        final StringBuilder o = new StringBuilder();
        final StringBuilder l = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                o.append(c);
                l.append(Character.toLowerCase(c));
                continue;
            }
            // if it is not letter or digit, we split it.
            if (o.length() > 0) {
                a.add(new AbstractMap.SimpleEntry<String, String>(l.toString(), o.toString()));
                o.setLength(0);
                l.setLength(0);
            }
        }
        if (o.length() > 0) {
            a.add(new AbstractMap.SimpleEntry<String, String>(l.toString(), o.toString())); o.setLength(0); l.setLength(0);
        }
        return a;
    }

    public LinkedHashSet<String> suggest(String q, int count, int distance) {
        TreeMap<Long, String> a = new TreeMap<>();
        String ql = normalize(q);
        boolean exact = false;
        String exactTerm = null;
        seekloop: for (GeoLocation g: id2loc.values()) {
            termloop: for (String n: g.getNames()) {
                if (n.length() > 3 && n.length() < ql.length() * 4) {
                    String nn = normalize(n);
                    if (!exact && nn.equals(ql)) {
                        exact = true;
                        exactTerm = n;
                        continue seekloop;
                    }
                    // starts-with:
                    if (nn.startsWith(ql)) {
                        a.put(g.getPopulation() + a.size(), n);
                        if (a.size() > count * 2) break seekloop;
                    }
                    // distance
                    
                    if (nn.length() == ql.length()) {
                        int errorcount = 0;
                        for (int i = 0; i < nn.length(); i++) {
                            if (nn.charAt(i) != ql.charAt(i)) {
                                errorcount ++;
                                if (errorcount > distance) {
                                    continue termloop;
                                }
                            }
                        }
                        a.put(g.getPopulation() + a.size(), n);
                        if (a.size() > count * 2) {
                            break seekloop;
                        }
                    }
                }
            }
        }
        // order by population
        LinkedHashSet<String> list = new LinkedHashSet<>();
        int i = 0;
        if (exact) {
            list.add(exactTerm);
        }
        for (Long p: a.descendingKeySet()) {
            list.add(a.get(p));
            if (i >= list.size()) {
                break;
            }
        }
        return list;
    }
    
    public static String normalize(final String text) {
        final StringBuilder l = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                l.append(Character.toLowerCase(c));
            } else {
                if (l.length() > 0 && l.charAt(l.length() - 1) != ' ') l.append(' ');
            }
        }
        if (l.length() > 0 && l.charAt(l.length() - 1) == ' ') l.setLength(l.length() - 1);
        return l.toString();
    }

    public double[] getCountryCenter(String cc) {
        return countryCenter.get(cc);
    }

    public static void main(String[] args) {
        ArrayList<Map.Entry<String, String>> split = split("Hoc est Corpus-meus");
        LinkedHashMap<Integer, String> mix = mix(split, 3);
        for (Map.Entry<Integer, String> entry: mix.entrySet()) {
            System.out.println("code:" + entry.getKey() + "; string:" + entry.getValue());
        }
    }

}
