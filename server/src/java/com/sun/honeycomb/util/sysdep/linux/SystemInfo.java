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


package com.sun.honeycomb.util.sysdep.linux;

import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.RuntimeException;

/** 
 * Concrete implemention of a SystemInfo for Linux i386
 */
public class SystemInfo extends com.sun.honeycomb.util.sysdep.SystemInfo {

    private static final Logger logger = 
        Logger.getLogger (SystemInfo.class.getName());

    /** HACK: This should come form the underlying OS. Unfortunetly,
              Linux doesn't expose this via /proc. There is a known
              patch to do so: */
    private static final float HZ = 100;

    /** where we get the uptime information */
    private static final String PROC_UPTIME  = "/proc/uptime";

    /** where we get the load information */
    private static final String PROC_LOADAVG = "/proc/loadavg";

    /** where we get the cpu statistics information */
    private static final String PROC_STAT    = "/proc/stat";
   
    // We track some old information for use in calculating deltas
    protected static long  LAST_NOW;
    protected static float LAST_USER;
    protected static float LAST_SYSTEM;
    protected static float LAST_NICE;
    protected static float LAST_IDLE;
    protected static float LAST_INTR;

    protected float _user;
    protected float _system;
    protected float _nice;
    protected float _idle;
    protected float _intr;
    protected float _uptime;
    protected float _load1;
    protected float _load5;
    protected float _load15;


    public SystemInfo() {
        try {
            readUptime();
            readLoad();
            readStat();
        } catch (IOException ioe) {
            logger.severe ("unable to read stats from /proc");
            throw new RuntimeException ("unable to read stats from /proc", ioe);
        }
    }

    /** The raw uptime in millis */
    public int getUptime() {
        return (int) _uptime;
    }

    /** The number of days the system has been up */
    public int getUpDays() {
        return (int) (_uptime / (60 * 60 * 24));
    }

    /** The number of hours the system has been up */
    public int getUpHours() {
        float hours = _uptime / (60 * 60);
        return (int) (hours %= 24);
    }

    /** The number of minutes the system has been up */
    public int getUpMinutes() {
        float minutes = _uptime / 60;
        return (int) (minutes %= 60);
    }

    public float getUserLoad() {
        return _user;
    }
    
    public float getNiceLoad() {
        return _nice;
    }
    
    public float getIdleLoad() {
        return _idle;
    }
    
    public float getSystemLoad() {
        return _system;
    }

    public float getIntr() {
        return _intr;
    }

    public float get1MinLoad() {
        return _load1;
    }

    public float get5MinLoad() {
        return _load5;
    }

    public float get15MinLoad() {
        return _load15;
    }

    protected void readUptime () throws IOException {
        BufferedReader file = null;

        try {
            file = new BufferedReader (new FileReader (PROC_UPTIME));
            String line = file.readLine();

            if (line == null) {
                return;
            }

            String[] parts = line.split ("\\s");

            if (parts.length > 0) {
                _uptime = toFloat (parts[0]);
            }
        } 
        finally {
            if (file != null) {
                try {
                    file.close();
                } catch (Exception e) {
                    // nothing to do here
                }
            }
        }
    }

    protected void readLoad () throws IOException {
        BufferedReader file = null;

        try {
            file = new BufferedReader (new FileReader (PROC_LOADAVG));
            String line = file.readLine();

            String[] loadParts = line.split ("\\s");

            if (loadParts.length >= 3) {
                _load1  = toFloat (loadParts[0]);
                _load5  = toFloat (loadParts[1]);
                _load15 = toFloat (loadParts[2]);
            }
        } 
        finally {
            if (file != null) {
                try {
                    file.close();
                } catch (Exception e) {
                    // nothing to do here
                }
            }
        }
    }

    protected synchronized void readStat () throws IOException {
        BufferedReader file = null;
      
        long now = System.currentTimeMillis();
        long delta = now - LAST_NOW;

        if (delta < 1000) {
            return;
        }
        String line = null;
        try {
            file = new BufferedReader (new FileReader (PROC_STAT));

            while ((line = file.readLine()) != null) {
                if (line.startsWith ("cpu ")) {
                    String[] lineParts = line.split ("\\s+");
                    
                    if (lineParts.length >= 5) {
                        float user   = toFloat (lineParts[1]);
                        float nice   = toFloat (lineParts[2]);
                        float system = toFloat (lineParts[3]);
                        float idle   = toFloat (lineParts[4]);

                        float curLoad = (user + nice + system + idle);
                        float oLoad   = LAST_USER + LAST_NICE + 
                                            LAST_SYSTEM + LAST_IDLE;
                        int   tdelta  = (int) (curLoad - oLoad);

                        if (tdelta < 1) {
                            tdelta = 1;
                        }

                        _user   = (user - LAST_USER) / tdelta * HZ;
                        _nice   = (nice - LAST_NICE) / tdelta * HZ;
                        _system = (system - LAST_SYSTEM) / tdelta * HZ;
                        _idle   = (idle - LAST_IDLE) / tdelta * HZ;

                        LAST_USER   = user;
                        LAST_NICE   = nice;
                        LAST_SYSTEM = system;
                        LAST_IDLE   = idle;
                    }
                }
                else if (line.startsWith ("intr")) {
                    String[] lineParts = line.split ("\\s");

                    if (lineParts.length >= 2) {
                        float intr = toFloat (lineParts[1]);
                        _intr = (intr - LAST_INTR) / (delta / 1000);
                        LAST_INTR = intr;
                    }
                }
            }
        }
        finally {
            LAST_NOW = now;

            if (file != null) {
                try {
                    file.close();
                } catch (Exception e) {
                    // nothing to do here
                }
            }
        }
    }

    /** Turn a String into a float */
    private static float toFloat (String string) {
        return Float.valueOf (string).floatValue();
    }
}

