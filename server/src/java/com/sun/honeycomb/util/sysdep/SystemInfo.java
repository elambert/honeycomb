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


package com.sun.honeycomb.util.sysdep;

import com.sun.honeycomb.alert.AlertType;
import com.sun.honeycomb.alert.AlertComponent;
import com.sun.honeycomb.alert.AlertException;

import java.text.DecimalFormat;

/** 
 * Interface that defines the contract that classes must implement if they 
 * provide CPU and load information for a machine. 
 */
public abstract class SystemInfo implements AlertComponent,
                                            java.io.Serializable {

    /** The raw uptime in millis */
    public abstract int getUptime();

    /** The number of days the system has been up */
    public abstract int getUpDays();

    /** The number of hours the system has been up */
    public abstract int getUpHours();

    /** The number of minutes the system has been up */
    public abstract int getUpMinutes();

    /** Get the load on the system spent in user processes */
    public abstract float getUserLoad();
    
    /** Get the load on the system spent in nice processes */
    public abstract float getNiceLoad();
    
    /** Get the amount of load time spent idle */
    public abstract float getIdleLoad();
    
    /** Get the amount of load time spent in system processes */
    public abstract float getSystemLoad();

    /** Get a 1 minute load average */
    public abstract float get1MinLoad();

    /** Get a 5 minute load average */
    public abstract float get5MinLoad();

    /** Get a 15 minute load average */
    public abstract float get15MinLoad();

    /** Get the number of interrupts / second */
    public abstract float getIntr();

    // Factory
    private static String os = null;
    public static synchronized SystemInfo getInfo() {
        if (os == null)
            os = System.getProperty("os.name").toLowerCase();

        if (os.equalsIgnoreCase("sunos") || os.equalsIgnoreCase("solaris"))
            return new com.sun.honeycomb.util.sysdep.solaris.SystemInfo();
        else if (os.equalsIgnoreCase("linux"))
            return new com.sun.honeycomb.util.sysdep.linux.SystemInfo();
        else
            return null;
    }

   /*
     * Alert API
     *
     * Exports:
     * - upTime (millisecond)
     * - userLoad
     * - niceLoad
     * - idleLoad
     * - systemLoad
     * - nbIntrPerSec
     */
    public int getNbChildren() {
        return 8;
    }

    public AlertProperty getPropertyChild(int index)  
            throws AlertException {
        AlertProperty prop = null;
        if (index == 0) {
            prop = new AlertProperty("upTime", AlertType.INT);
        } else if (index == 1) {
            prop = new AlertProperty("userLoad", AlertType.FLOAT);
        } else if (index == 2) {
            prop = new AlertProperty("niceLoad", AlertType.FLOAT);
        } else if (index == 3) {
            prop = new AlertProperty("idleLoad", AlertType.FLOAT);
        } else if (index == 4) {
            prop = new AlertProperty("systemLoad", AlertType.FLOAT);
        } else if (index == 5) {
            prop = new AlertProperty("nbIntrPerSec", AlertType.FLOAT);
        } else if (index == 6) {
            prop = new AlertProperty("loadAverages", AlertType.STRING);
        } else if (index == 7) {
            prop = new AlertProperty("loadStats", AlertType.STRING);
        } else {
            throw new AlertException("index " + index + "does not exist");
        }
        return prop;
    }

    public int getPropertyValueInt(String property)  
            throws AlertException {
        if (property.equals("upTime")) {
            return (getUptime());
        } else {
            throw new AlertException("property " + property +
                                     " does not exist");
        }

    }
    public float getPropertyValueFloat(String property)  
            throws AlertException {
        if (property.equals("userLoad")) {
            return (getUserLoad());
        } else if (property.equals("niceLoad")) {
            return (getNiceLoad());
        } else if (property.equals("idleLoad")) {
            return (getIdleLoad());
        } else if (property.equals("systemLoad")) {
            return (getSystemLoad());
        } else if (property.equals("nbIntrPerSec")) {
            return (getIntr());
        } else {
            throw new AlertException("property " + property +
                                     " does not exist");
        }
    }


    /* Default implementation. */
    public boolean getPropertyValueBoolean(String property)  
        throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }
    public double getPropertyValueDouble(String property)  
        throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }
    private static DecimalFormat d = new DecimalFormat ("#######0.0#");
    public String getPropertyValueString(String property)  
        throws AlertException {
        if (property.equals("loadAverages")) {
            return d.format(get1MinLoad()) + " " +
                   d.format(get5MinLoad()) + " " +
                   d.format(get15MinLoad());
        } else if (property.equals("loadStats")) {
            return "" + getSystemLoad() + ", " + get1MinLoad() + ", "
                      + get5MinLoad() + ", " + get15MinLoad();  
        }
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }
    public long getPropertyValueLong(String property)  
        throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }
    public AlertComponent getPropertyValueComponent(String property)  
        throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }    
}

