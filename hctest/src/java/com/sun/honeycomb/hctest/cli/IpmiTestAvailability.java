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

import com.sun.honeycomb.testcmd.common.HoneycombTestException;
import java.util.Iterator;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tests for the availablity of Ipmi on a system.  If Ipmi is unavailable for
 * a system the SMBC version for the system will be listed as unavailable.
 *
 * NOTE: That if the node is down the version will also be listed as 
 * unavailable.  This code will not test whether the node is up or not.
 * For simplicity all nodes are assumed up.
 */
public class IpmiTestAvailability
{
    
    static final Pattern NODE_NAME = Pattern.compile("NODE-(1[0-1][0-9]):");
    
    static final Pattern SMDC_VERSION  = Pattern.compile("SMDC version: (.*)");
    
    HashMap map = new HashMap();
    
    /**
     * Creates a new instance of IpmiTestAvailability
     * @param versionCmdOutput the output of the version command
     */
    public IpmiTestAvailability(String versionCmdOutput)
    {
	this(versionCmdOutput.split("\n"));
    }
    
    /**
     * Creates a new instance of IpmiTestAvailability
     * @param output the output of the version command
     */
    public IpmiTestAvailability(String[] output)
    {
	
	int node=0;
	for (int i=0; i < output.length; i++) {
	    String line = output[i].trim();
	    
	    Matcher m = NODE_NAME.matcher(line);
	    if (m.matches()) {
		node = Integer.parseInt(m.group(1));
	    }
	    if (node != 0) {
		m = SMDC_VERSION.matcher(line);
		if (m.matches()) {
		    String smdcVersion = m.group(1);
		    map.put(Integer.valueOf(node), 
			    Boolean.valueOf(!smdcVersion.equals("Unavailable")));
		    node = 0;
		}
            }
	}
    }
    
    /**
     * Check whether ipmi is available for a particular node
     * @param nodeId id of the node to check
     * @return boolean true if IPMI is available for the specified node,
     * false otherwise
     */
    public boolean isIpmiAvailable(int nodeId)
    throws HoneycombTestException 
    {
	Boolean available = (Boolean)map.get(Integer.valueOf(nodeId));
	if (available == null) 
	    throw new HoneycombTestException(
		"Failed to retrieve IPMI info for NODE-"+ nodeId);
	return available.booleanValue();
    }
    
}
