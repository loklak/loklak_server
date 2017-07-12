package org.loklak.harvester.strategy;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.loklak.harvester.strategy.PriorityKaizenHarvester.PriorityKaizenQueries;

public class PriorityKaizenHarvesterTest {

    @Test
    public void testPriority() {
        PriorityKaizenQueries queries = new PriorityKaizenQueries(2);
        queries.addQuery("abc", 0.5);
        queries.addQuery("ghi", 0.8);
        queries.addQuery("def", 0.6);
        assertEquals("ghi", queries.getQuery());
        assertEquals("def", queries.getQuery());
        assertEquals("abc", queries.getQuery());
        assertEquals(0, queries.getSize());
    }

    @Test
    public void testDuplicate() {
        PriorityKaizenQueries queries = new PriorityKaizenQueries(2);
        queries.addQuery("abc", 0.5);
        queries.addQuery("abc", 0.6);
        assertEquals(1, queries.getSize());
    }

    @Test
    public void testMaxSize() {
        PriorityKaizenQueries queries = new PriorityKaizenQueries(10);
        assertEquals(10, queries.getMaxSize());
    }

}
