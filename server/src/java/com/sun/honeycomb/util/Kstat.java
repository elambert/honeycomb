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

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

import java.util.logging.Logger;
import java.util.logging.Level;

public final class Kstat {
    // The types of kstats
    public static final int KSTAT_RAW   = 0;
    public static final int KSTAT_NAMED = 1;
    public static final int KSTAT_INTR  = 2;
    public static final int KSTAT_IO    = 3;
    public static final int KSTAT_TIMER = 4;

    private long crTime;
    private int kId;
    private String moduleName;
    private int instance;
    private String name;
    private int type;
    private String cls;
    private long snapTime;

    private byte[] rawData = null;
    private long[] interruptCounts = null;
    private Map namedStats = null;
    private Map timers = null;
    private IOStat theIOStat = null;

    // Used when building up the Kstat object from the results of the
    // native method
    private static Kstat theKstat;

    protected static final Logger LOG = 
        Logger.getLogger(Kstat.class.getName());		

    /**
     * This interface is used by the DiskMonitor: the kstat's name
     * is computed.
     */
    public static Kstat get(String module, int instance) {
        // I don't know what the trailing "e" means (for disks) [shamim]
        return get(module, instance,  module + instance + ",e");
    }

    /**
     * Factory method: calls the JNI method, which talks to the kernel
     * and calls back here to create a Kstat object; if all went well,
     * the Kstat object is returned
     */
    public static synchronized Kstat get(String module, int instance,
                                         String name) {
        theKstat = null;

        // Get kstat and grab all the values from it
        if (!Kstat.getKstat(module, instance, name))
            return null;

        return theKstat;
    }

    public int id()            { return kId; }
    public int type()          { return type; }
    public long creationTime() { return crTime; }
    public long snapshotTime() { return snapTime; }
    public String module()     { return moduleName; }
    public int instance()      { return instance; }
    public String name()       { return name; }
    public String cls()        { return cls; }

    //////////////////////////////////////////////////////////////////////
    // KSTAT_RAW

    public byte[] getRaw() {
        assert type == KSTAT_RAW;
        return rawData;
    }
   
    //////////////////////////////////////////////////////////////////////
    // KSTAT_NAMED

    public Object getStat(String name) {
        // Can be String or Long depending on the kstat
        assert type == KSTAT_NAMED;
        return namedStats.get(name);
    }

    //////////////////////////////////////////////////////////////////////
    // KSTAT_INTR

    public static final int KSTAT_INTR_HARD     = 0;
    public static final int KSTAT_INTR_SOFT     = 1;
    public static final int KSTAT_INTR_WATCHDOG = 2;
    public static final int KSTAT_INTR_SPURIOUS = 3;
    public static final int KSTAT_INTR_MULTSVC  = 4;
    public static final int KSTAT_NUM_INTRS     = 5;
    public static final String[] interruptNames = {
        "hard", "soft", "watchdog", "spurious", "multsvc"
    };

    public long getInterruptCount(int type) {
        assert type == KSTAT_INTR;
        assert type >= 0 && type < KSTAT_NUM_INTRS;
        return interruptCounts[type];
    }

    //////////////////////////////////////////////////////////////////////
    // KSTAT_IO

    public IOStat getIOStat() {
        assert type == KSTAT_IO;
        return theIOStat;
    }

    public class IOStat {
        // All time values are in ns from some unspecified datum in the past
        // (usually since boot time)

        public long reads() {
            return reads;
        }
        public long writes() {
            return writes;
        }
        public long bytesRead() {
            return nread;
        }
        public long bytesWritten() {
            return nwritten;
        }
        public long cumulativeWaitTime() {
            return wtime;
        }
        public long cumulativeWaitQLengthTime() {
            return wlentime;
        }
        public long lastWaitQUpdate() {
            return wlastupdate;
        }
        public long cumulativeRunTime() {
            return rtime;
        }
        public long cumulativeRunQLengthTime() {
            return rlentime;
        }
        public long lastRunQUpdate() {
            return rlastupdate;
        }
        public long waitCount() {
            return wcnt;
        }
        public long runCount() {
            return rcnt;
        }

        IOStat(long nread, long nwritten,
               long reads, long writes,
               long wtime, long wlentime, long wlastupdate,
               long rtime, long rlentime, long rlastupdate,
               long wcnt, long rcnt) {
            this.nread = nread;
            this.nwritten = nwritten;
            this.reads = reads;
            this.writes = writes;
            this.wtime = wtime;
            this.wlentime = wlentime;
            this.wlastupdate = wlastupdate;
            this.rtime = rtime;
            this.rlentime = rlentime;
            this.rlastupdate = rlastupdate;
            this.wcnt = wcnt;
            this.rcnt = rcnt;
        }

        public String toString() {
            return "(nread:" + nread + " nwritten:" + nwritten +
                " reads:" + reads + " writes:" + writes +
                " wtime:" + wtime + " wlentime:" + wlentime +
                " wlastupdate:" + wlastupdate +
                " rtime:" + rtime + " rlentime:" + rlentime +
                " rlastupdate:" + rlastupdate +
                " wcnt:" + wcnt + " rcnt:" + rcnt + ")";
        }

        private long nread;
        private long nwritten;
        private long reads;
        private long writes;
        private long wtime;
        private long wlentime;
        private long wlastupdate;
        private long rtime;
        private long rlentime;
        private long rlastupdate;
        private long wcnt;
        private long rcnt;

    }
 
    //////////////////////////////////////////////////////////////////////
    // KSTAT_TIMER

    public Timer getTimer(String name) {
        assert type == KSTAT_TIMER;
        return (Timer) timers.get(name);
    }

    public class Timer {
        // All time values are in ns from some unspecified datum in the past
        // (usually since boot time)

        public String name() {
            return name;
        }
        public long numEvents() {
            return num_events;
        }
        public long elapsedTime() {
            return elapsed_time;
        }
        public long minTime() {
            return min_time;
        }
        public long maxTime() {
            return max_time;
        }
        public long startTime() {
            return start_time;
        }
        public long stopTime() {
            return stop_time;
        }

        Timer(String name, long num_events,
              long elapsed_time,
              long min_time, long max_time,
              long start_time, long stop_time) {
            this.name = name;
            this.num_events = num_events;
            this.elapsed_time = elapsed_time;
            this.min_time = min_time;
            this.max_time = max_time;
            this.start_time = start_time;
            this.stop_time = stop_time;
        }

        public String toString() {
            return "(" + name + " num:" + num_events +
                " elapsed:" + elapsed_time +
                " min:" + min_time + " max:" + max_time +
                " start:" + start_time + " stop:" + stop_time + ")";
                
        }

        private String name;
        private long num_events;
        private long elapsed_time;
        private long min_time;
        private long max_time;
        private long start_time;
        private long stop_time;
    }

    // End public section
    //////////////////////////////////////////////////////////////////////

    // Private constructor and methods

    Kstat(long crTime, int kid,
          String module, int instance, String name,
          int type, String cls, long snapTime) {

        this.crTime = crTime;
        this.kId = kid;
        this.moduleName = module;
        this.instance = instance;
        this.name = name;
        this.type = type;
        this.cls = cls;
        this.snapTime = snapTime;

        if (type == KSTAT_TIMER)
            timers = new HashMap();

        if (type == KSTAT_NAMED)
            namedStats = new HashMap();

        if (type == KSTAT_INTR)
            interruptCounts = new long[5];
    }

    private void setRaw(byte[] rawData) {
        assert type == KSTAT_RAW;
        this.rawData = rawData;
    }
    private void setInterrupts(long hard, long soft,
                                    long watchdog, long spurious,
                                    long multsvc) {
        assert type == KSTAT_INTR;

        interruptCounts[KSTAT_INTR_HARD] = hard;
        interruptCounts[KSTAT_INTR_SOFT] = soft;
        interruptCounts[KSTAT_INTR_WATCHDOG] = watchdog;
        interruptCounts[KSTAT_INTR_SPURIOUS] = spurious;
        interruptCounts[KSTAT_INTR_MULTSVC] = multsvc;
    }
    private void setIOStats(long nread, long nwritten,
                            long reads, long writes,
                            long wtime, long wlentime, long wlastupdate,
                            long rtime, long rlentime, long rlastupdate,
                            long wcnt, long rcnt) {
        assert type == KSTAT_IO;
        this.theIOStat = new IOStat(nread, nwritten, reads, writes,
                                    wtime, wlentime, wlastupdate,
                                    rtime, rlentime, rlastupdate,
                                    wcnt, rcnt);
    }
    private void addNamedStat(String name, Object value) {
        assert type == KSTAT_NAMED;
        namedStats.put(name, value);

    }
    private void addTimer(String name, long num_events,
                          long elapsed_time,
                          long min_time, long max_time,
                          long start_time, long stop_time) {
        assert type == KSTAT_TIMER;
        timers.put(name, new Timer(name, num_events, elapsed_time,
                                   min_time, max_time,
                                   start_time, stop_time));
    }

    //////////////////////////////////////////////////////////////////////
    // JNI stuff

    private static void kstatAttrs(long crTime, int kid,
                            String module, int instance, String name,
                            int type, String cls, long snapTime) {
        theKstat = new Kstat(crTime, kid, module, instance, name,
                             type, cls, snapTime);
    }

    private static void setRawData(byte[] data) {
        theKstat.setRaw(data);
    }

    private static void addNamedString(String name, String value) {
        theKstat.addNamedStat(name, value);
    }

    private static void addNamedLong(String name, long value) {
        theKstat.addNamedStat(name, new Long(value));
    }

    private static void setInterruptCounts(long hard, long soft,
                                           long watchdog, long spurious,
                                           long multsvc) {
        theKstat.setInterrupts(hard, soft, watchdog, spurious, multsvc);
    }

    private static void setIOStat(long nread, long nwritten,
                                  long reads, long writes,
                                  long wtime, long wlentime, long wlastupdate,
                                  long rtime, long rlentime, long rlastupdate,
                                  long wcnt, long rcnt) {
        theKstat.setIOStats(nread, nwritten, reads, writes,
                                   wtime, wlentime, wlastupdate,
                                   rtime, rlentime, rlastupdate,
                                   wcnt, rcnt);
    }

    private static void newTimer(String name, long num_events,
                                 long elapsed_time,
                                 long min_time, long max_time,
                                 long start_time, long stop_time) {
        theKstat.addTimer(name, num_events, elapsed_time,
                          min_time, max_time,
                          start_time, stop_time);
    }
    
    // JNI Stub
    private static native boolean getKstat(String module, int instance,
                                           String name);

    // Initialize the class
    static {
        try {
            System.loadLibrary("jkstat");
            LOG.info("kstat JNI library loaded");
        } catch(UnsatisfiedLinkError ule) {
            LOG.log(Level.SEVERE,
                    "Check LD_LIBRARY_PATH. Can't find " +
                    System.mapLibraryName("jkstat") + " in " +
                    System.getProperty("java.library.path"),
                    ule);
            
        }
    }

    public String toString() {
        String ret = "{" + kId;
        ret += " " + moduleName + ":" + instance + ":" + name;
        ret += " b. " + crTime + " t=" + snapTime;
        ret += " class \"" + cls + "\" type " + type;

        switch (type) {
        case KSTAT_RAW:
            ret += " (raw) 0x";
            for (int i = 0; i < rawData.length; i++)
                ret += Integer.toHexString(rawData[i] & 0xff);
            break;

        case KSTAT_NAMED:
            ret += " (named)";
            for (Iterator i = namedStats.keySet().iterator(); i.hasNext(); ) {
                String key = (String) i.next();
                Object value = namedStats.get(key);
                ret += " " + key + "=\"" + value + "\"";
            }
            break;

        case KSTAT_IO:
            ret += " (io) " + theIOStat.toString();
            break;

        case KSTAT_TIMER:
            ret += " (timer)";
            for (Iterator i = timers.keySet().iterator(); i.hasNext(); ) {
                String timerName = (String) i.next();
                Timer timer = (Timer) timers.get(timerName);
                ret += " " + timer.toString();
            }
            break;

        case KSTAT_INTR:
            ret += " (intr)";
            for (int i = 0; i < KSTAT_NUM_INTRS; i++)
                ret += " " + interruptNames[i] + ":" + interruptCounts[i];
            break;

        default:
            ret += " (unknown type " + type + ")";
            break;
        }

        return ret + " }";
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Kstat module:instance:name");
            System.exit(1);
        }
        String[] arr = args[0].split(":");
        if (arr.length != 3) {
            System.err.println("Need 3 components in \"" + args[0] + "\"");
            System.exit(1);
        }

        String module = arr[0];
        String name = arr[2];
        int instance = -1;
        try {
            instance = Integer.parseInt(arr[1]);
        }
        catch (NumberFormatException e) {
            System.err.println("Couldn't convert " + arr[1] + " to integer");
            System.exit(1);
        }
        Kstat stat = Kstat.get(module, instance, name);
        if (stat == null)
            System.out.println("No such kstat: " + args[0]);
        else
            System.out.println(stat);
    }
}
