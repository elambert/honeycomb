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



package com.sun.honeycomb.wb.sp.hadb.utils;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.sun.hadb.adminapi.DomainMember;
import com.sun.hadb.adminapi.HADBException;
import com.sun.hadb.adminapi.Node;
import com.sun.hadb.mgt.NodeRole;
import com.sun.hadb.mgt.NodeState;
import com.sun.honeycomb.test.SolarisNode;
import com.sun.honeycomb.test.util.ExitStatus;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.RunCommand;
import com.sun.honeycomb.test.util.Util;


/**
 * This class is a "snap shot" of an HADB Database Node object. 
 * It is used to represent the state of an HADB Database Node object at
 * a particular point in time (that point in time being when the object 
 * was created).
 *
 * @author elambert
 */
public class HADBNode  implements Comparable {
    
    /**
     * Create a new HADBNode instance.
     * @param n The Database node being snap-shotted.
     * @throws HADBException
     */
    public HADBNode(Node n) throws HADBException {
	m_role = n.getNodeRole();
	m_state = n.getNodeState();
	DomainMember dm = n.getDomainMember();
	if (dm != null) {
	    m_host = dm.getHostName();
	}
	Node mn = n.getMirrorNode();
	if (mn != null) {
	    m_mirrorNodePhysicalNum = mn.getPhysicalNumber();
	}
	m_physicalNum = n.getPhysicalNumber();
	m_configVersion = n.getConfigVersion();
	m_druNumber = n.getDruNumber();
	m_logicalNumber = n.getLogicalNumber();
	m_node = new SolarisNode(m_host, "ssh");
    }
    
    public String toString() {
	return m_host+":"+m_physicalNum + "(dru="+m_druNumber+",logicalNum="+
			m_logicalNumber +",configVersion="+m_configVersion +
			",mirror="+m_mirrorNodePhysicalNum +",role="+m_role+
			",state=" + m_state + ")";
    }
    
    /**
     * @param that Object being compared for equality.
     * 
     * @return true if the two objects are equal. More
     * specifically, will return true if
     * -that is not null
     * -that is instanceof HADBNode.
     * -Node hostnames are equal
     * -Node physical numbers are equal
     * -Node roles are equal
     * -Node states are equal
     * -Both nodes mirror the same physical node
     */
    public boolean equals (Object that) {
	HADBNode thatNode = null;
	
	if (! (that instanceof HADBNode)) {
	    Log.DEBUG("object is not an instance of HADBNode");
	    return false;
	}
	
	thatNode = (HADBNode) that;
	
	if(!this.m_host.equals(thatNode.m_host)) {
	    Log.DEBUG("Host names are not equal");
	    return false;
	}
	
	if (this.m_physicalNum != thatNode.m_physicalNum) {
	    Log.DEBUG("Physical numbers are not equal");
	    return false;
	}
	
	if (! (this.m_role.equals(thatNode.m_role))) {
	    Log.DEBUG("Node roles are not equal");
	    return false;
	}
	
	if (! (this.m_state.equals(thatNode.m_state))) {
	    Log.DEBUG("Node states are not equal");
	    return false;
	}
	
	if (this.m_mirrorNodePhysicalNum != thatNode.m_mirrorNodePhysicalNum) {
	    Log.DEBUG("mirror nodes are not equal");
	    return false;
	}
	
	return true;
    }
    
    /**
     * 
     * @return the physical node number of the Node 
     * at the time the snap-shot was taken. 
     */
    public int getPhysicalNode () {
	return m_physicalNum;
    }
    
    /**
     * 
     * @return the physical node number of this Nodes 
     * mirror at the time the snap-shot was taken.
     */
    public int getMirrorNode () {
	return m_mirrorNodePhysicalNum;
    }
    
    /**
     * 
     * @return the name of the host upon which this 
     * node resides.
     */
    public String getHost () {
	return m_host;
    }
    
    /**
     * 
     * @return The Role which this node was playing 
     * at the time the snap-shot was taken. Generally
     * this is either active or spare.
     */
    public NodeRole getRole () {
	return m_role;
    }
    
    /**
     * 
     * @return The State which this node was in when 
     * the snap shot was taken.
     */
    public NodeState getState () {
	return m_state;
    }
    
    
    public int getConfigVersion () {
	return m_configVersion;
    }
    
    /**
     * @return the number of the disaster replacment
     * unit (DRU) which this node was participating 
     * in at the time of the snap-shot.
     */
    public int getDRUNumber () {
	return m_druNumber;
    }
    
    /**
     * 
     * @return  the logical node number of the Node 
     * at the time the snap-shot was taken. 
     */
    public int getLogicalNumber () {
	return m_logicalNumber;
    }
    
    
    public int compareTo(Object o) {
	HADBNode that = (HADBNode) o;
	return this.m_host.compareTo(that.m_host);
    }
    
    public boolean isUp() {
	return m_node.ping();
    }
    
     
    // This method relies on the assumption that when we wipe the database
    // we move the existing hadb log and history files of each node to 
    // /data/?/hadb-logs/${NOW}. Where ${NOW} is a directory, the name of 
    // which is a timestamp representing the time the wipe ocurred.
    // This method takes advantage of this by first seeing if there is 
    // a hadb-logs directory, if none are present than no wipe has ocurred.
    // It then looks at the timestamps of the directories in hadb-logs. If
    // none of them are after the time passed into the method, then no
    // wipe has ocurred since that time.
    public boolean hasBeenWiped(long startTime) throws IllegalStateException {
	
	//TODO log time of wipe
	
	if (!isUp()) {
	    throw new IllegalStateException("Node " + m_host + " is not up.");
	}
	
	// where should the db files be on this node
	ManagementConfig mc = new ManagementConfig(m_host);
	try {
	    mc.load();
	} catch (IOException ioe) {
	    Log.INFO("Unable to retrieve Hadb Management config from "+m_host);
	    throw new IllegalStateException(ioe.getMessage());
	}
	
	String hadb_dir = mc.getDatabaseDevicePath();
	String hadbLogsDir =hadb_dir + "/../" + HADB_LOGS_DIRNAME;
	ExitStatus status = null;
	RunCommand rc = new RunCommand();
	
	// check if hadbLogsDir exists
	try {
	    status = rc.sshCmdStructuredOutput(m_host,"test -d " + hadbLogsDir);
	} catch (HoneycombTestException hte) {
	    throw new IllegalStateException("Unable to determine if " +
		    hadbLogsDir + " exists: " + hte.getMessage());
	}
	
	// if I dont have the /data/?/hadb-logs dir we have not wiped
	if (status.getReturnCode() != 0) {
	    Log.INFO("No wipe found on " + m_host + ". No " + hadbLogsDir + 
		    " found.");
	    return false;
	}
	
	try {
	    status =  rc.sshCmdStructuredOutput(m_host,"ls -1r " + hadbLogsDir);
	} catch (HoneycombTestException hte) {
	    throw new IllegalStateException("Unable to list contents of " +
		    hadbLogsDir + ": " + hte.getMessage() + " on " + m_host);
	}
	
	if (status.getReturnCode() != 0 ) {
	    Log.WARN("Non-zero return code while listing contents of " + 
		    hadbLogsDir + " on " + m_host);
	    Log.WARN("Return Code: " + status.getReturnCode());
	    Log.WARN("Output: " + status.getOutputString()); //err & out 
	}
	
	String wipeInstances = (String) status.getOutStrings().get(0);
	Date wipeTime = null;
	SimpleDateFormat formatter = 
		new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
	    
	try {
	    wipeTime = formatter.parse(wipeInstances);
	} catch (ParseException pe) {
		Log.WARN("Unable to parse wipe time : " + wipeInstances);
		return false;
	}
	if (wipeTime.getTime() >= startTime) {
	    Log.INFO("Found One! Wipe " + wipeInstances + 
		    " happened after start time " +
            Util.msecDateStringVerbose(startTime) +
            " on host " + m_host + "  in dir " + hadbLogsDir);
	    return true;
	} else {
	    Log.INFO("Wipe " + wipeInstances + " happened before start time " +
            Util.msecDateStringVerbose(startTime) +
            " on host " + m_host + " in dir " + hadbLogsDir);
	}
	
	return false;
    }
    
    
    private NodeRole m_role = null;
    private NodeState m_state = null;
    private String m_host = null;
    private int m_mirrorNodePhysicalNum = -1;
    private int m_physicalNum = -1;
    private int m_configVersion = -1;
    private int m_druNumber = -1;
    private int m_logicalNumber = -1;
    private String HADB_LOGS_DIRNAME = "hadb-logs";
    private SolarisNode m_node = null;
}
