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
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Singleton class for getting system (CPU and memory) information
 * using a JNI module that uses kstat(3KSTAT)
 *
 * @version $Revision$ $Date$
 */
public final class SysStat {

    protected static SysStat sysStat = null;

    private long lastUpdate = 0;

    private long mem_total = 0;
    private long mem_free = 0;
    private long mem_buffers = 0;
    private long mem_cache = 0;

    private long uptime = 0;

    private float load_1m = 0;
    private float load_5m = 0;
    private float load_15m = 0;

    private long time_user = 0;
    private long time_sys = 0;
    private long time_idle = 0;

    private long intr_rate = 0;

    private long nice = 0;

    private static final long UPDATE_INTERVAL = 5000; // ms
    
    protected static final Logger LOG = 
        Logger.getLogger(SysStat.class.getName());		

    /**
     * Get kernel statistics using JNI sysstat.getInfo, but no more
     * than once every 5 seconds
     */
    public static synchronized void update() {
        if (sysStat == null) {
            sysStat = new SysStat();
        }

        long now = System.currentTimeMillis();
        if (now - sysStat.lastUpdate > UPDATE_INTERVAL) {
            if (!sysStat.getInfo())
                throw new RuntimeException("Couldn't get sys stats!");
            sysStat.lastUpdate = now;

            // Maybe subtract the values from the last time to get a
            // running average?
        }
    }

    public static long getMemTotal() {
        update();
        return sysStat.mem_total;
    }
    public static long getMemFree() {
        update();
        return sysStat.mem_free;
    }
    public static long getMemBuffers() {
        update();
        return sysStat.mem_buffers;
    }
    public static long getMemCache() {
        update();
        return sysStat.mem_cache;
    }

    public static float getLoad1Minute() {
        update();
        return sysStat.load_1m;
    }
    public static float getLoad5Minute() {
        update();
        return sysStat.load_5m;
    }
    public static float getLoad15Minute() {
        update();
        return sysStat.load_15m;
    }

    public static long getTimeUser() {
        update();
        return sysStat.time_user;
    }
    public static long getTimeSystem() {
        update();
        return sysStat.time_sys;
    }
    public static long getTimeIdle() {
        update();
        return sysStat.time_idle;
    }

    public static long getIntrRate() {
        update();
        return sysStat.intr_rate;
    }

    public static long getUptimeMillis() {
        update();
        return sysStat.uptime;
    }

    // Private constructor
    private SysStat() {
        lastUpdate = 0;
    }
    
    // The JNI function
    private native boolean getInfo();

    // Initialize the class
    static {
        try {
            System.loadLibrary("sysstat");
            LOG.info("sysstat jni library loaded");
        } catch(UnsatisfiedLinkError ule) {
            LOG.log(Level.SEVERE,
                    "Check LD_LIBRARY_PATH. Can't find " +
                    System.mapLibraryName("sysstat") + " in " +
                    System.getProperty("java.library.path"),
                    ule);
            
        }
    }
}
