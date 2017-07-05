package org.loklak;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.loklak.harvester.TwitterScraperTest;
import org.loklak.harvester.YoutubeScraperTest;
import org.loklak.api.search.GithubProfileScraperTest;

/*
    TestRunner for harvesters
*/
@RunWith(Suite.class)
@Suite.SuiteClasses({
    TwitterScraperTest.class,
    YoutubeScraperTest.class,
    GithubProfileScraperTest.class
})
public class TestRunner {
}

