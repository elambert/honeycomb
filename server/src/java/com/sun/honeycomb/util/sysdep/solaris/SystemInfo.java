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


package com.sun.honeycomb.util.sysdep.solaris;

import com.sun.honeycomb.util.SysStat;

import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.RuntimeException;

/** 
 * Concrete implemention of a SystemInfo for Solaris x86
 */
public class SystemInfo extends com.sun.honeycomb.util.sysdep.SystemInfo {

    private static final Logger logger = 
        Logger.getLogger (SystemInfo.class.getName());

    // We track some old information for use in calculating deltas
    protected static long  LAST_NOW;
    protected static float LAST_USER;
    protected static float LAST_SYSTEM;
    protected static float LAST_NICE;
    protected static float LAST_IDLE;
    protected static float LAST_INTR;

    // These are in arbitrary units (approx. 100 ms)
    protected long _user;
    protected long _system;
    protected long _nice;
    protected long _idle;

    protected float _intr;      // Interrupts/sec

    protected long _uptime;     // millis

    protected float _load1;
    protected float _load5;
    protected float _load15;


    public SystemInfo() {
	_uptime = SysStat.getUptimeMillis();

	_load1  = SysStat.getLoad1Minute();
	_load5  = SysStat.getLoad5Minute();
	_load15 = SysStat.getLoad15Minute();

	_user   = SysStat.getTimeUser();
	_nice   = 0;
	_system = SysStat.getTimeSystem();
	_idle   = SysStat.getTimeIdle();

        _intr   = SysStat.getIntrRate();
    }

    /** The uptime in milliseconds */
    public int getUptime() {
        return (int) _uptime;
    }

    /**
     * The number of days,hours,minutes,seconds the system has been
     * up: this returns the days
     */
    public int getUpDays() {
        return (int)(_uptime / 86400000);
    }
    /**
     * The number of days,hours,minutes,seconds the system has been
     * up: this returns the hours
     */
    public int getUpHours() {
        return (int)((_uptime % 86400000) / 3600000);
    }
    /**
     * The number of days,hours,minutes,seconds the system has been
     * up: this returns the minutes
     */
    public int getUpMinutes() {
        return (int)((_uptime % 3600000) / 60000);
    }
    /**
     * The number of days,hours,minutes,seconds the system has been
     * up: this returns the seconds
     */
    public int getUpSeconds() {
        return (int)((_uptime % 60000) / 1000);
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

    /** Turn a String into a float */
    private static float toFloat (String string) {
        return Float.valueOf(string).floatValue();
    }
}

