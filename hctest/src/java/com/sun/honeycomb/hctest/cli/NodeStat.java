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

import com.sun.honeycomb.hctest.util.HoneycombTestConstants;

public class NodeStat
{
    public static final String ONLINE_NODE = FruInfo.ONLINE;
    public static final String OFFLINE_NODE = FruInfo.OFFLINE; 
    
    public int nodeId;
    public boolean isEligible;
    public boolean isInCluster;
    public boolean isMaster;
    public boolean isViceMaster;
    public static final Pattern NODE_HWSTAT_PATTERN =
	Pattern.compile("^NODE-([0-9]+)(\\s+)NODE(\\s+)([^\\s]+)(\\s+)(\\p{Upper}+)(\\s*)$");
    
    public DiskStat [] disks;
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("NODE-");
        sb.append(Integer.toString(nodeId));
        sb.append("\t");
        sb.append("NODE");
        sb.append("\t");
        sb.append("[");
        sb.append(isInCluster ? ONLINE_NODE : OFFLINE_NODE);
//        sb.append(isMaster ? " MASTER" : "");
//        sb.append(isViceMaster ? " VICE-MASTER" : "");
//        sb.append(isEligible ? " ELIGIBLE" : " NOT-ELIGIBLE");
        sb.append("]");
        return sb.toString();
    }
    
    protected NodeStat(String spec) 
    {
        disks = new DiskStat[HoneycombTestConstants.DISKS_PER_NODE];

        // NODE-101                NODE    -       Online
        Pattern regexp =   NODE_HWSTAT_PATTERN;
        Matcher matcher = regexp.matcher(spec);
        if (!matcher.matches()) {
            throw new RuntimeException("Unable to parse node stat output: <" 
		+ spec + ">");
        }
        
        int group = 1;
        this.nodeId = Integer.parseInt(matcher.group(1));
        this.isInCluster = (matcher.group(6).equals(ONLINE_NODE));

        /*
        if (matcher.group(4) != null) {
            this.isMaster = (matcher.group(group).equals("MASTER"));
            this.isViceMaster = (matcher.group(group).equals("VICE-MASTER"));
            group++;
            group++;
        }

        this.isEligible = (matcher.group(group).equals("ELIGIBLE"));

        StringTokenizer st = new StringTokenizer(spec);
        String fruId = st.nextToken();
        String type = st.nextToken();
        String status = st.nextToken();
        status = status.substring(1, status.length()-1); // strip the []s
        String nodeId = fruId.split("-")[1];
        String [] statusTokens = status.split(" ");
        System.out.println("XXX");
        for (int i=0; i < statusTokens.length; i++) {
            System.out.println(statusTokens[i]);
        }
        int token_i = 0;
        this.isInCluster = (statusTokens[token_i].equals("IN-CLUSTER"));
        token_i++;
        if (statusTokens.length > 2) {
            this.isMaster = (statusTokens[token_i].equals("MASTER"));
            this.isViceMaster = (statusTokens[token_i].equals("VICE-MASTER"));
            token_i++;
        }
        this.isEligible = (statusTokens[token_i].equals("ELIGIBLE"));
        */
    }

    protected void addDisk(int diskId, DiskStat disk) 
    {
        this.disks[diskId] = disk;
    }

    public static void main(String [] args) throws Throwable
    {
        //String spec = "NODE-101       NODE    [IN-CLUSTER MASTER ELIGIBLE]";
        //String spec = "NODE-101       NODE    [IN-CLUSTER ELIGIBLE]";
        String spec = "NODE-101                NODE    -       [Online]";
        //Pattern regexp = Pattern.compile("NODE-([0-9]+)\\s+NODE\\s+\\[([^\\s]+)\\s+([^\\s]+)([^\\s]+)?\\]");
        //Pattern regexp = Pattern.compile("^NODE-([0-9]+)\\s+NODE\\s+\\[([^\\s]+)\\s+([^\\s]+)(\\s+([^\\s]+))?\\]$");
        Pattern regexp = Pattern.compile("^NODE-([0-9]+)\\s+NODE.*\\[([^\\s]+)\\]$");
        Matcher matcher = regexp.matcher(spec);
        if (!matcher.matches()) {
            throw new RuntimeException("Unable to parse node stat output: " + spec);
        }
        System.out.println("matches!");
        for (int i=0; i <= matcher.groupCount(); i++) {
            System.out.println(Integer.toString(i) + ": " + matcher.group(i));
        }
    }
}
