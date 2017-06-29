package org.loklak.harvester.strategy;

import org.loklak.data.DAO;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;

public class PriorityKaizenHarvester extends KaizenHarvester {

    private static class PriorityKaizenQueries extends KaizenQueries {

        private Comparator<ScoreWrapper> scoreComparator = (scoreWrapper, t1) -> (int) (scoreWrapper.score - t1.score);

        private Queue<ScoreWrapper> queue;
        private int maxSize;

        private class ScoreWrapper {

            private double score;
            private String query;

            ScoreWrapper(String m, double score) {
                this.query = m;
                this.score = score;
            }

        }

        public PriorityKaizenQueries(int size) {
            this.maxSize = size;
            queue = new PriorityQueue<>(size, scoreComparator);
        }

        @Override
        public boolean addQuery(String query, double score) {
            ScoreWrapper sw = new ScoreWrapper(query, score);
            if (this.queue.contains(sw)) {
                return false;
            }
            try {
                this.queue.add(sw);
                return true;
            } catch (IllegalStateException e) {
                return false;
            }
        }

        @Override
        public String getQuery() {
            return this.queue.poll().query;
        }

        @Override
        public int getSize() {
            return this.queue.size();
        }

        @Override
        public int getMaxSize() {
            return this.maxSize;
        }
    }

    public PriorityKaizenHarvester() {
        super(new PriorityKaizenQueries(DAO.getConfig("harvester.kaizen.queries_limit", 500)));
    }
}
