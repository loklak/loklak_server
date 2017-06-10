package org.loklak.harvester.strategy;

import java.util.HashSet;

/**
 * KaizenQueries are objects that holds the query strings for KaizenHarvester.
 */
public abstract class KaizenQueries {

    public boolean addQuery(String query) {
        return this.addQuery(query, 0.5);
    }

    public abstract boolean addQuery(String query, double score);

    public abstract String getQuery();

    public abstract int getSize();

    public abstract int getMaxSize();

    public boolean isEmpty() {
        return this.getSize() == 0;
    }

    public static KaizenQueries getDefaultKaizenQueries(int qLimit) {
        return new KaizenQueries() {

            HashSet<String> queries = new HashSet<>();
            int queryLimit = qLimit;

            @Override
            public boolean addQuery(String query, double score) {
                if (this.queryLimit > 0 && this.queries.size() > this.queryLimit)
                    return false;

                if (queries.contains(query))
                    return false;

                this.queries.add(query);
                return true;
            }

            @Override
            public String getQuery() {
                String query = this.queries.iterator().next();
                this.queries.remove(query);
                return query;
            }

            @Override
            public int getSize() {
                return this.queries.size();
            }

            @Override
            public int getMaxSize() {
                return this.queryLimit;
            }
        };
    }
}
