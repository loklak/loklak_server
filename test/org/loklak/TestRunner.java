package org.loklak;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.loklak.harvester.TwitterScraperTest;
import org.loklak.harvester.TwitterScraper;

/*
    TestRunner for harvesters
*/
@RunWith(Suite.class)
@Suite.SuiteClasses({
    org.loklak.harvester.TwitterScraperTest.class,
})
public class TestRunner {
}

