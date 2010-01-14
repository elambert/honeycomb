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

import com.sun.hadb.adminapi.DomainMember;
import com.sun.hadb.mgt.ReleaseInfo;
import com.sun.honeycomb.test.util.Log;

/**
 * This class is a "snap shot" of an HADB DomainMember object. 
 * It is used to represent the state of an HADB DomainMember object at
 * a particular point in time (that point in time being when the 
 * instance of this class was created).
 *
 * @author elambert
 *
 */

public class HADBDomainMember implements Comparable {
    
    /**
     * @param dm The DomainMember to be snap-shoted.
     */
    public HADBDomainMember (DomainMember dm) {
	if (dm != null) {
	    m_isRunning = dm.isRunning();
	    m_isEnabled = dm.isEnabled();
	    m_hostName = dm.getHostName();
	    ReleaseInfo ri = dm.getReleaseInfo();
	    if (ri != null) {
		m_releaseInfo = ri.toString();
	    }
	}
    }
    
    /**
     * 
     * @return true if this DomainMember was in a running 
     * state when the snap-shot was taken, else returns false.
     */
    public boolean isRunning() {
	return m_isRunning;
    }
    
    /**
     * 
     * @return true if this DomainMember was in an enabled 
     * state when the snap-shot was taken, else returns false.
     */
    public boolean isEnabled() {
	return m_isEnabled;
    }
    
    /**
     * 
     * @return The name of the host upon which this DomainMember executes.
     */
    public String getHostName () {
	return m_hostName;
    }
    
    /**
     * 
     * @return Version and release information of the version of HADB
     * in which this DomainMember was executing.
     */
    public String getReleaseInfo () {
	return m_releaseInfo;
    }
    
    
    public String toString() {
	return m_hostName + " (running=" + m_isRunning + ", enabled=" 
	+ m_isEnabled + ",release=" + m_releaseInfo + ")";
    }
    
    /**
     * @return true if the object parameter to this method is of type
     * HADBDomainMember and it is equal to this instance.
     * 
     * Two instances of HADBDomainMember are considered equal when the 
     * following criteria has been met:
     * -The host names for both HADBDomainMembers are equal
     * -The running states for both HADBDomainMembers are equal
     * -The enabled states for both HADBDomainMembers are equal
     */
    public boolean equals (Object that) {
	HADBDomainMember thatDomainMember = null;
	if (that == null) {
	    Log.WARN("Operand is null.");
	    return false;
	}
	if (that instanceof HADBDomainMember) {
	    return false;
	} 
	
	thatDomainMember = (HADBDomainMember) that;
	if (! m_hostName.equals(thatDomainMember.m_hostName)) {
	    Log.INFO("Hostnames do not match.");
	    Log.INFO("This hostname = " + m_hostName);
	    Log.INFO("That hostname = " + thatDomainMember.m_hostName);
	    return false;
	}
	
	if (m_isRunning != thatDomainMember.m_isRunning) {
	    Log.INFO("DomainMembers do not have equal running states.");
	    Log.INFO("this running state = " + m_isRunning);
	    Log.INFO("thar running state = " + thatDomainMember.m_isRunning);
	    return false;
	}
	
	if (m_isEnabled != thatDomainMember.m_isEnabled) {
	    Log.INFO("DomainMembers do not have equal enabled states.");
	    Log.INFO("This enabled state = " + m_isEnabled);
	    Log.INFO("That enanled state = " + thatDomainMember.m_isEnabled);
	    return false;
	}
	
	return true;
    }
    
    
    public int compareTo(Object o) {
	
	if (o == null) {
	    throw new NullPointerException("The object I am comparing against" +
	    		" is null");
	}
	
	HADBDomainMember that = (HADBDomainMember)o;
	
	return this.m_hostName.compareTo(that.m_hostName);
	
    }
    
    private boolean m_isRunning = false;
    private boolean m_isEnabled = false;
    private String m_hostName = "";
    private String m_releaseInfo = "unkown";

}
