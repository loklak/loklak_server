package org.loklak;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.loklak.harvester.TwitterScraperTest;
import org.loklak.harvester.YoutubeScraperTest;

/*
    TestRunner for harvesters
*/
@RunWith(Suite.class)
@Suite.SuiteClasses({
    TwitterScraperTest.class,
        YoutubeScraperTest.class
})
public class TestRunner {
}

