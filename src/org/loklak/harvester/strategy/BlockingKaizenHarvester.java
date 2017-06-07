package org.loklak.harvester.strategy;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.loklak.data.DAO;

public class BlockingKaizenHarvester extends KaizenHarvester {

    /**
     * This class uses blocking queue to store queries which need to be processed.
     */
    public BlockingKaizenHarvester() {
        super(new KaizenQueries() {
            private int maxSize = DAO.getConfig("harvester.kaizen.queries_limit", 500);
            private BlockingQueue<String> queries = new ArrayBlockingQueue<>(maxSize);
            private int blockingTimeout = DAO.getConfig("harvester.blocking_kaizen.block_time", 120);

            @Override
            public boolean addQuery(String query) {
                if (this.queries.contains(query)) {
                    return false;
                }
                try {
                    /*
                    * We need to associate a timeout with the addition process as there are chances (though little)
                    *  that all threads are trying to add their queries at once. In that case, one of them would timeout and
                    *  drop the query, and let others submit theirs.
                    * */
                    this.queries.offer(query, this.blockingTimeout, TimeUnit.SECONDS);
                    return true;
                } catch (InterruptedException e) {
                    DAO.severe("BlockingKaizen Couldn't add query: " + query, e);
                    return false;
                }
            }

            @Override
            public String getQuery() {
                try {
                    return this.queries.take();  // will never get blocked as shallHarvest would return False if empty
                } catch (InterruptedException e) {
                    DAO.severe("BlockingKaizen Couldn't get any query", e);
                    return null;
                }
            }

            @Override
            public int getSize() {
                return this.queries.size();
            }

            @Override
            public int getMaxSize() {
                return this.maxSize;
            }
        });
    }

}
