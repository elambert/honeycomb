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


import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiskStat extends FruInfo
{
    public static final int INVALID_NODE = -1;
    public static final int INVALID_DISK = -1;
     
    String nodeDiskString;
    int nodeId = INVALID_NODE;
    int diskId = INVALID_DISK;
    String fruId = "";
    boolean isEnabled;
    String status;
    
    private static final Pattern DISK_HWSTAT_PATTERN1 =
	Pattern.compile("^DISK-([^\\s]+)\\s+DISK\\s+(.*)$");
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("DISK-");
        sb.append(nodeId >= 0 ? Integer.toString(nodeId) : "?");
        sb.append(":");
        sb.append(diskId >= 0 ? Integer.toString(diskId) : "?");
        sb.append("\t");
        sb.append("DISK");
        sb.append("\t");
        sb.append("[");
        sb.append(isEnabled ? ENABLED : DISABLED);
        sb.append(" ");
        sb.append(status);
        sb.append("]");
        return sb.toString();
    }
    
    protected DiskStat(String spec) 
    {
	super(spec);

        // During happy case, line looks like this:
	// DISK-101:0  DISK      ATA_____HITACHI_HDS7250S______KRVN63ZAGM96HD        ENABLED

        Matcher matcher = DISK_HWSTAT_PATTERN1.matcher(spec);
        if (!matcher.matches()) {
            throw new RuntimeException("Unable to parse disk stat output: <" 
		+ spec + ">");
        }

        // handle case where nodeDiskString is 'null'

        nodeDiskString = matcher.group(1);
        if (nodeDiskString.indexOf(":") != -1) {
            String[] strings = nodeDiskString.split(":");
            try {
                nodeId = Integer.parseInt(strings[0]);
            } catch (NumberFormatException nfe) {
                nodeId = INVALID_NODE;
            }
            try {
                diskId = Integer.parseInt(strings[1]);
            } catch (NumberFormatException nfe) {
                diskId = INVALID_DISK;
            }


        }
	
        isEnabled = getStatus().equals(ENABLED);
    }
    
    public int getNodeId() {
	return nodeId;
    }
    
    public String getDiskId() {
	return nodeDiskString;
    }
    
    public int getSubDiskId() {
	return diskId;
    }
}

