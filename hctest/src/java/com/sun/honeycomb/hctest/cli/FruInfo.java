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


package com.sun.honeycomb.hctest.cli;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic class used to hold and make available fru information
 * about a component in the system
 */
public class FruInfo {
    
    public static final Pattern FRU_HWSTAT_PATTERN =
	Pattern.compile("([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)\\s+(\\p{Upper}+)\\s*$");

    public static final String FRU_TYPE_NODE = "NODE";
    public static final String FRU_TYPE_DISK = "DISK";
    public static final String FRU_TYPE_SP = "SN";
    public static final String FRU_TYPE_SWITCH = "SWITCH";
    
    public static final String FRU_SWITCH_1 = "SWITCH-1";
    public static final String FRU_SWITCH_2 = "SWITCH-2";

    public static final String ONLINE = "ONLINE";
    public static final String OFFLINE = "OFFLINE";
    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";
    public static final String UNKNOWN = "UNKNOWN";
    
    private String name;
    private String fruId;
    private String status;
    private String type;
    
    /** Creates a new instance of FruInfo */
    public FruInfo() {
    }
    
    /**
     * Creates a new instance of FruInfo.  Fills in the name, fru id, and status
     * based on the hwstat line passed in
     * @param hwstat_line the line of the fru taken from the hwstat output
     * @throws IllegalArgumentException if line doesn't match the expected
     * output format
     */
    public FruInfo(String hwstat_line) {
        Matcher matcher = FRU_HWSTAT_PATTERN.matcher(hwstat_line);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid hwstat fru format: '"
                    + hwstat_line + "'");
        }
        setName(matcher.group(1));
	setFruType(matcher.group(2));
        setFruId(matcher.group(3));
        setStatus(matcher.group(4));
    }

    /*
     * @return String the name of the component
     */
    public String getName() {
        return name;
    }
   

    public void setName(String name) {
        this.name = name;
    }
   
  
    /**
     * @return String the fru id of the component
     */
    public String getFruId() {
        return fruId;
    } 
    
    public void setFruId(String fruId) {
        this.fruId = fruId;
    }
    
   
    /**
     * @return String the status of the component
     */
    public String getStatus() {
        return status;
    }

 
    public void setStatus(String status) {
        this.status = status;
    }
    
  
    /**
     * @return String the fru type oof the component
     */
    public String getFruType() {
	return type;
    }

    public void setFruType(String fruType) {
	this.type = fruType;
    }
    
    /**
     * @return String a printable representation of this object
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append(getName()).append(" ");
	buf.append(getFruType()).append(" ").append(getFruId());
        buf.append(" ").append(getStatus());
        return buf.toString();
    }
}
