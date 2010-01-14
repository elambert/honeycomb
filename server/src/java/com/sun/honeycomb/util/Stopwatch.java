/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



package com.sun.honeycomb.util;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

/** this is a static utility class for collecting and printing basic
 * timing info - Treats every thread seperately */

public final class Stopwatch {
    
    private static final Map timers = new HashMap();
    private static Map times = new HashMap();
    private static final Logger LOG = 
        Logger.getLogger(Stopwatch.class.getName());
    
    private static final long now() {
        return System.currentTimeMillis();
    }
    
    /** call this right before you start 'action' to start timing it*/
    public synchronized static final boolean start(String action) {
        String k = new String(Thread.currentThread().getName() + "|" + action);

        Object o = timers.get(k);
        if(o != null) {
            return false;
        }
        timers.put(k, new Long(now()));
        return true;
    }
    
    /** call this right after you finish 'action' to add to it's total time*/
    public synchronized static final boolean stop(String action) {
        long now = now();
        
        String k = new String(Thread.currentThread().getName() + "|" + action);
        
        Object started = timers.remove(k);
        if(started == null) {
            return false;
        }
        Long time = new Long(now - ((Long)started).longValue());
        
        Long oldTotal = (Long) times.remove(k);
        if(oldTotal == null) {
            times.put(k, time);
        } else {  
            times.put(k, new Long(time.longValue() + oldTotal.longValue()));
        }
        
        return true;
    }
    
    /** Call this to reset times for 'action' to 0 & stop any timer for it */
    public synchronized static final void reset(String action) {
        String k = new String(Thread.currentThread().getName() + "|" + action);
        timers.remove(k);
        times.remove(k);
    }

    /** call this to reset all times to 0 and stop all timers for this thread*/
    public synchronized static final void reset() {
        Iterator keys = times.keySet().iterator();
        int i = 0;
        String s = null;
        while(keys.hasNext()) {
            s = (String) keys.next();
            if(s.startsWith(Thread.currentThread().getName())) {
                times.remove(s);
                keys = times.keySet().iterator();  // N^2 - Yuck
            }
        }
        keys = timers.keySet().iterator();
        i = 0;
        s = null;
        while(keys.hasNext()) {
            s = (String) keys.next();
            if(s.startsWith(Thread.currentThread().getName())) {
                timers.remove(s); 
                keys = timers.keySet().iterator(); // N^2 - Yuck
            }
        }
    }
     
  /** call this to reset all times to 0 and stop all timers for all threads*/
    public synchronized static final void reseAllThreadst() {
        timers.clear();
        times.clear();
    }
    
    /** call this to log timing info for a list of actions .
        calculates and prints percentage each makes of total, etc. 
        times are from last time stop was called.  returns false if
        any action passed has not had stop called on it before.
    */
    public synchronized static final boolean log(String[] actions, boolean pretty) {
        long total = 0;
        SortedMap results = new TreeMap();
        if(pretty) {
            // Pretty-print so stats are easy on a human's eyes
            // Sorted by time (so a quick glance reveals the bottlenecks)
            for(int a=0; a<actions.length; a++) {
                String k = new String(Thread.currentThread().getName() + "|" + actions[a]);
                Object o = times.get(k);
                if(o != null) {
                    total += ((Long)o).longValue();
                    results.put(o, actions[a]);
                }
            }
            
            Set sortedTimes = results.keySet();
            Iterator sortedTimesIter = sortedTimes.iterator();
            String result = "\n----- Statistics -----\n";
            while(sortedTimesIter.hasNext()) {
                Long t = (Long) sortedTimesIter.next();
                String action = (String) results.get(t);
                double pct = ((double) t.longValue()) / ((double) total) * 100;
                result = result + 
                    "| " + action + ": " + t + " ms (" + pct + "%)\n";
            }
            result = result + "| Total: " + total + " ms\n-----------------------";
            LOG.info(result);
            
        } else {
            // Print in a compact format optimized for computer processing
            // Sorted alphabetically by action (so like actions line up)
            for(int a=0; a<actions.length; a++) {
                String k = new String(Thread.currentThread().getName() + "|" + actions[a]);
                Object o = times.get(k);
                if(o != null) {
                    total += ((Long)o).longValue();
                    results.put(actions[a], o);
                }
            }
            
            Set sortedTimes = results.keySet();
            Iterator sortedTimesIter = sortedTimes.iterator();
            String head = "SH,";
            String data = "SD,";
            while(sortedTimesIter.hasNext()) {
                String action = (String) sortedTimesIter.next();
                Long t = (Long) results.get(action);
                head = head + action + ",";
                data = data + t + ",";
            }
            LOG.info(head + "\n" + data);
        }
        return true;
    }
    
    /** call this to log timing info for all actions known by Stopwatch */
    public synchronized static final boolean log(boolean pretty) {
        String[] actions = new String[times.size()];
        Iterator keys = times.keySet().iterator();
        int i = 0;
        String s = null;
        while(keys.hasNext()) {
            s = (String) keys.next();
            actions[i++] =  s.substring(s.lastIndexOf((int)'|')+1);      
        }
        return log(actions, pretty);
    }
}
