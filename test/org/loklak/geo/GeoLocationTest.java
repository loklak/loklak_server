package org.loklak.geo;

import org.loklak.geo.GeoLocation;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collection;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * This is a test file for testing methods of org.loklak.geo.GeoLocation.java
 */
public class GeoLocationTest {

    @Test
    public void geoLocation() {
        
        Collection<String> names =  new ArrayList<String>();
        names.add("Taj Mahal");
        
        final double lat = 27.175015;
        final double lon = 78.042155;
        final String iso3166cc = "IN-UP";

        /** Population value is just a testing value. It may differ from actual population. */
        long population = 400;
        int kmValue = 3025;
        
        GeoLocation geoLocation = new GeoLocation(lat, lon, names, iso3166cc);
        geoLocation.setPopulation(population);
        
        Collection<String> returnedNames =  geoLocation.getNames();
        String returnedISO3166cc = geoLocation.getISO3166cc();
        long returnedPopulation = geoLocation.getPopulation();
        int returnedKmValue = geoLocation.degreeToKm(27.175015);
        String returnedString = geoLocation.toString();
        
        assertNotNull(returnedNames);
        assertNotNull(returnedISO3166cc);
        assertNotNull(returnedPopulation);
        assertNotNull(returnedKmValue);
        assertNotNull(returnedString);
        assertEquals(names, returnedNames);
        assertEquals(iso3166cc, returnedISO3166cc);
        assertEquals(population, returnedPopulation);
        assertEquals(kmValue, returnedKmValue);
    }
}
