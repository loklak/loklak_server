package org.loklak.harvester;

import org.loklak.api.p2p.PushServlet;
import org.loklak.data.DAO;
import org.loklak.objects.Timeline;

public class PushThread implements Runnable {
    private String peer;
    private Timeline tl;
    public PushThread(String peer, Timeline tl) {
        this.peer = peer;
        this.tl = tl;
    }
    @Override
    public void run() {
        boolean success = false;
        for (int i = 0; i < 5; i++) {
            try {
                long start = System.currentTimeMillis();
                success = PushServlet.push(new String[]{peer}, tl);
                if (success) {
                    DAO.log("retrieval of " + tl.size() + " new messages for q = " + tl.getQuery() +
                            ", pushed to backend synchronously in " + (System.currentTimeMillis() - start) + " ms; amount = " + tl.size());
                    return;
                }
            } catch (Throwable e) {
                DAO.log("failed synchronous push to backend, attempt " + i);
                try {Thread.sleep((i + 1) * 3000);} catch (InterruptedException e1) {}
            }
        }
        String q = tl.getQuery();
        tl.setQuery(null);
        DAO.outgoingMessages.transmitTimelineToBackend(tl);
        DAO.log("retrieval of " + tl.size() + " new messages for q = " + q + ", scheduled push");
    }

}