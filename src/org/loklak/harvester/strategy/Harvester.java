package org.loklak.harvester.strategy;

public interface Harvester {
    /**
     * This method is in-charge of harvesting and scraping messages
     * @return the amount of messages that was harvested or -1 if it isn't harvesting
     */
    int harvest();

    /**
     * This method is executed when Loklak is shutting down
     */
    void stop();
}
