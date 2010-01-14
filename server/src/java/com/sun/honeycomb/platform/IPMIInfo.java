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


package com.sun.honeycomb.platform;

import com.sun.honeycomb.util.SysStat;
import com.sun.honeycomb.util.Exec;
import com.sun.honeycomb.util.Ipmi;

import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.RuntimeException;

/**
 * IPMIInfo is an object containing sensors information from the DMI
 * table. This object is the returned value of getIPMIInfo() platform
 * RMI call.
 * WARNING - ipmitool has a high impact on CPU usage 
 */
public class IPMIInfo implements java.io.Serializable {

    private static final transient Logger logger =
        Logger.getLogger (IPMIInfo.class.getName());

    /* avoid running ipmitool too often */
    private static final int REFRESH_INTERVAL = (5 * 60 * 1000); //5mn
    
    
    public static final int vDDR = 0;
    public static final int vCPU = 1;
    public static final int v3_3 = 2;
    public static final int v5   = 3;
    public static final int v12  = 4;
    public static final int vBattery = 5;
    public static final int tempCPU = 6;
    public static final int tempSys = 7;
    public static final int sysFan4 = 8;
    public static final int sysFan3 = 9;
    public static final int sysFan1 = 10;
    public static final int sysFan2 = 11;
    public static final int sysFan5 = 12;

    private final String[] ipmi = new String[13];
    private final String cache = "/tmp/.sdr-cache";

    
    /******************
     * private access
     ******************/
    
    private IPMIInfo() {
        logger.info("Initialising sensor data...");
        try {
            Ipmi.ipmi("sdr dump " + cache);
        } catch (IOException e) {
            e.printStackTrace();
        }
        updateInfo();
    }

    private void updateInfo() {
        BufferedReader output = null;
        String line;

        try {
            output = Ipmi.ipmi("-S " + cache + " sdr");

            for (int i = 0; (line = output.readLine()) != null &&
                             i < ipmi.length ; i++) {
                // This works around a Java RE bug
                line = line.replace('|', ':');
                ipmi[i] = line.split(":")[1].trim();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                output.close();
            } catch (IOException e) {}
        }
    }

    /*******************
     * Package access
     *******************/
    
    private static IPMIInfo theInfo = null;
    private static long refreshTimeout;
    public static synchronized IPMIInfo getInfo() {

        if (theInfo == null) {
            theInfo = new com.sun.honeycomb.platform.IPMIInfo();
            refreshTimeout = System.currentTimeMillis() + REFRESH_INTERVAL;
        } else if ((refreshTimeout - System.currentTimeMillis()) <= 0) {
            theInfo.updateInfo();
            refreshTimeout = System.currentTimeMillis() + REFRESH_INTERVAL;
        }

        return theInfo;
    }
    
    /*******************
     * Public access
     *******************/
    
    public String getValue(int index) {
        if (index >= 0 && index < ipmi.length) {
            return ipmi[index];
        }
        return "unknown";
    }
    
     public String getDdrVoltage() {
         return ipmi[vDDR];
     }

     public String getCpuVoltage() {
         return ipmi[vCPU];
     }
     
     public String getThreeVCC() {
         return ipmi[v3_3];
     }
     
     public String getFiveVCC() {
         return ipmi[v5];
     }
     
     public String getTwelveVCC(){
         return ipmi[v12];
     }
     
     public String getBatteryVoltage() {
         return ipmi[vBattery];
     }
     
     public String getCpuTemperature() {
         return ipmi[tempCPU];
     }
     
     public String getSystemTemperature() {
         return ipmi[tempSys];
     }
     
     /*
      * This does not seem to be implemented !!! 
      */
     public String getCpuFanSpeed() {
         // return ipmi[cpuFan];
         return "unknown";
     }
     
     public String getSystemFan1Speed() {
         return ipmi[sysFan1];
     }
     
     public String getSystemFan2Speed() {
         return ipmi[sysFan2];
     }
     
     public String getSystemFan3Speed() {
         return ipmi[sysFan3];
     }
    
     public String getSystemFan4Speed() {
         return ipmi[sysFan4];
     }
     public String getSystemFan5Speed() {
         return ipmi[sysFan5];
     }
 }
