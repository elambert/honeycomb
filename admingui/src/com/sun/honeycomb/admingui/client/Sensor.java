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

 

package com.sun.honeycomb.admingui.client;

/**
 * encapsulates node sensor information
 */
public class Sensor {
 
    // sensor type. used as an index in the 'ranges' array below
    public static final int DDR_V   = 0;
    public static final int CPU_V   = 1; 
    public static final int MB3V_V  = 2;
    public static final int MB5V_V  = 3;
    public static final int MB12V_V = 4;
    public static final int BAT_V   = 5;
    public static final int CPU_T   = 6;
    public static final int SYS_T   = 7;
    public static final int SYSF1_F = 8;
    public static final int SYSF2_F = 9;
    public static final int SYSF3_F = 10;
    public static final int SYSF4_F = 11;
    public static final int SYSF5_F = 12;     

    // range values
    public static final float ranges[][] =
        new float[][] { {2.496f, 2.704f}, // DDR
                        {0.796f, 1.556f}, // CPU
                        {3.146f, 3.454f}, // 3.3V
                        {4.732f, 5.252f}, // 5V
                        {11.403f, 12.600f}, // 12V
                        {2.001f, 3.454f}, // BAT
                        {0, 75}, // CPU temp
                        {0, 55}, // SYS temp
                        {990, 22950}, // fan rpm
                        {990, 22950}, // fan rpm
                        {990, 22950}, // fan rpm
                        {990, 22950}, // fan rpm
                        {990, 22950}};
    
    protected int id;
    protected String value;
    
    public static final int OK = 0;
    public static final int OUT_OF_RANGE = 1;
    public static final int UNKNOWN = 2;
            
    protected int status;

    public float getMin() { return ranges[id][0]; }
    public float getMax() { return ranges[id][1]; }

    public Sensor(int id, String value) {
        this.id = id;
        this.value = value;
        this.status = UNKNOWN;
        if (value == null)
            return;
        // remove unit which is currently part of value e.g. "4 degrees C"
        String val = value.replaceFirst(" .*", "");
        try {
            float numval = Float.parseFloat(val);
            if (id < DDR_V || id > SYSF5_F)
                System.out.println("unknown sensor id " + id);
            else
                status = (numval > ranges[id][0] && numval < ranges[id][1])
                         ? OK : OUT_OF_RANGE;
            /*
            switch (id) {
                case DDR_V:
                    status = (numval > 2.496 && numval < 2.704)
                       ? OK : OUT_OF_RANGE;
                    break;
                case CPU_V:
                    status = (numval > 0.796 && numval < 1.556)
                       ? OK : OUT_OF_RANGE;
                    break;
                case MB3V_V:
                    status = (numval > 3.146 && numval < 3.454)
                       ? OK : OUT_OF_RANGE;
                    break;
                case MB5V_V:;
                    status = (numval > 4.732 && numval < 5.252)
                       ? OK : OUT_OF_RANGE;
                    break;
                case MB12V_V:
                    status = (numval > 11.403 && numval < 12.600)
                       ? OK : OUT_OF_RANGE;
                    break;
                case BAT_V:
                    status = (numval > 2.001 && numval < 3.454)
                       ? OK : OUT_OF_RANGE;
                    break;
                case CPU_T:
                    status = (numval > 0 && numval < 75)
                       ? OK : OUT_OF_RANGE;
                    break;
                case SYS_T:
                    status = (numval > 0 && numval < 55)
                       ? OK : OUT_OF_RANGE;
                    break;
                case SYSF1_F:
                case SYSF2_F:
                case SYSF3_F:
                case SYSF4_F:
                case SYSF5_F:
                    status = (numval > 990 && numval < 22950)
                       ? OK : OUT_OF_RANGE;   
                    break;
                default:
                    System.out.println("unknown sensor id " + id);
                    break;
            }*/
        } catch (Exception e) { System.out.println(e); }

    }
    
    public int getID() { return id; }
    public String getValue() { return value; }
    public int getStatus() { return status; }
    
    public String toString() {
        return new String("sensor{" + id + "," + value + "," + status + "}");
    }
}
