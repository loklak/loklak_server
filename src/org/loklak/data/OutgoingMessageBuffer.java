/**
 *  OutgoingMessageBuffer
 *  Copyright 18.10.2016 by Michael Peter Christen, @0rb1t3r
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package org.loklak.data;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.loklak.objects.MessageEntry;
import org.loklak.objects.Timeline;
import org.loklak.objects.UserEntry;

/**
 * Buffer for outgoing messages which must be pushed to the index with a scheduler
 */
public class OutgoingMessageBuffer {

    private  BlockingQueue<Timeline> pushToBackendTimeline;
    
    public OutgoingMessageBuffer() {
        this.pushToBackendTimeline = new LinkedBlockingQueue<Timeline>();
    }
    
    public void transmitTimelineToBackend(Timeline tl) {
        if (DAO.getConfig("backend", new String[0], ",").length > 0) {
            boolean clone = false;
            for (MessageEntry message: tl) {
                if (!message.getSourceType().propagate()) {clone = true; break;}
            }
            if (clone) {
                Timeline tlc = new Timeline(tl.getOrder(), tl.getScraperInfo());
                for (MessageEntry message: tl) {
                    if (message.getSourceType().propagate()) tlc.add(message, tl.getUser(message));
                }
                if (tlc.size() > 0) this.pushToBackendTimeline.add(tlc);
            } else {
                this.pushToBackendTimeline.add(tl);
            }
        }
    }
    
    public void transmitMessage(final MessageEntry tweet, final UserEntry user) {
        if (!tweet.getSourceType().propagate()) return;
        if (DAO.getConfig("backend", new String[0], ",").length <= 0) return;
        if (!DAO.getConfig("backend.push.enabled", false)) return;
        Timeline tl = this.pushToBackendTimeline.poll();
        if (tl == null) tl = new Timeline(Timeline.Order.CREATED_AT);
        tl.add(tweet, user);
        this.pushToBackendTimeline.add(tl);
    }

    /**
     * if the given list of timelines contain at least the wanted minimum size of messages, they are flushed from the queue
     * and combined into a new timeline
     * @param order
     * @param minsize
     * @return
     */
    public Timeline takeTimelineMin(final Timeline.Order order, final int minsize, final int maxsize) {
        if (timelineSize() < minsize) return new Timeline(order);
        Timeline tl = new Timeline(order);
        try {
            while (this.pushToBackendTimeline.size() > 0) {
                Timeline tl0 = this.pushToBackendTimeline.take();
                if (tl0 == null) return tl;
                tl.putAll(tl0);
                if (tl.size() >= maxsize) break;
            }
            return tl;
        } catch (InterruptedException e) {
            return tl;
        }
    }
    
    public int timelineSize() {
        int c = 0;
        for (Timeline tl: this.pushToBackendTimeline) c += tl.size();
        return c;
    }
}
