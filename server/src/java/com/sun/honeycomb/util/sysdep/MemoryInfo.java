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

/** 
 * Interface for providing Memory information for a host.
 * OS/HW specific
 */
public abstract class MemoryInfo implements AlertComponent,
                                            java.io.Serializable {

    public abstract long getTotal();
    public abstract long getFree();
    public abstract long getBuffers();
    public abstract long getCached();

    private static MemoryInfo theInfo = null;
    public static synchronized MemoryInfo getInfo() {

        if (theInfo == null) {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.equals("sunos") || os.equals("solaris"))
                theInfo = new com.sun.honeycomb.util.sysdep.solaris.MemoryInfo();
            else if (os.equals("linux"))
                theInfo = new com.sun.honeycomb.util.sysdep.linux.MemoryInfo();
        }

        return theInfo;
    }

   /*
     * Alert API
     * Only exports free and total memory
     */
    public int getNbChildren() {
        return 3;
    }

    public AlertProperty getPropertyChild(int index)  
            throws AlertException {
        AlertProperty prop = null;
        if (index == 0) {
            prop = new AlertProperty("totalMemory", AlertType.LONG);
        } else if (index == 1) {
            prop = new AlertProperty("freeMemory", AlertType.LONG);
        } else if (index == 2) {
            prop = new AlertProperty("memoryStats", AlertType.STRING);
        } else {
            throw new AlertException("index " + index + "does not exist");
        }
        return prop;
    }
    public long getPropertyValueLong(String property)  
            throws AlertException {
        if (property.equals("totalMemory")) {
            return getTotal();
        } else if (property.equals("freeMemory")) {
            return getFree();            
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
    public int getPropertyValueInt(String property)  
            throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }
    public float getPropertyValueFloat(String property)  
            throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }
    public double getPropertyValueDouble(String property)  
            throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }
    /* This Alert String is used perfstats command */
    public String getPropertyValueString(String property)  
            throws AlertException {
        if (property.equals("memoryStats")) {
            return "0, " + getFree() + ", " +getTotal() + ", 0, " + System.currentTimeMillis();
        } else {
            throw new AlertException("property " + property +
                                     " does not exist");
        }    
    }
    public AlertComponent getPropertyValueComponent(String property)  
            throws AlertException {
        throw new AlertException("property " + property +
                                 " does not exist/wrong type");
    }    
}
