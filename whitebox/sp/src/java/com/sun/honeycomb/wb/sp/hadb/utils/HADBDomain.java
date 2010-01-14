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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import com.sun.hadb.adminapi.DomainMember;
import com.sun.hadb.adminapi.HADBException;
import com.sun.hadb.adminapi.ManagementDomain;
import com.sun.honeycomb.test.util.Log;

/**
 * This class is a "snap shot" of an HADB Manangement Domain object. 
 * It is used to represent the state of an HADB Management Domain object at
 * a particular point in time (that point in time being when the initialize 
 * method was invoked).
 *
 * @author elambert
 *
 */
public class HADBDomain {
    
    private HashMap m_domainMembersMap = new HashMap();
    private boolean m_initialized = false;
    
    
    public void initialize(ManagementDomain md) 
    throws HADBException, IllegalStateException{
	this.initialize(md,16);
    }
    
    
    /**
     * Initialize this object to represent the state of a particular 
     * Management Domain.
     *  
     * @param md The ManagementDomain which we are snap-shotting
     * 
     * @throws HADBException
     */
    public void initialize(ManagementDomain md, int clusterSize) 
    throws HADBException, IllegalStateException {
	if (md == null) {
	    throw new IllegalStateException("Management domain is null");
	}
	Iterator iter = md.getDomainMembers().iterator();
	while (iter.hasNext()) {
	    DomainMember dm = (DomainMember) iter.next();
	    m_domainMembersMap.put(dm.getHostName(),new HADBDomainMember(dm));
	    m_initialized = true;
	}
    }
    
    /**
     * @return the Set of members which made up this domain at the time
     * the time the initialize method was called. Each object in this 
     * Set are of type HADBDomainMember. 
     * 
     * Will return an empty set if initialize has not been called.
     */
    public Set getDomainMembers() throws IllegalStateException {
	isInitialized();
	return new TreeSet(m_domainMembersMap.values());
    }

    /**
     * @return true if the object parameter to this method is of type
     * HADBDomain and it is equal to this instance.
     * 
     * Two instances of HADBDomain are considered equal when the criteria 
     * has been met:
     * -all HADBDomainMembers which exist in this instance are present in the
     * other instances and vice-versa.
     * -Each HADBDomainMember in this instance is equal to its counter-part  
     * in the other instance. @see HADBDomainMember.equals(Object)
     */
    public boolean equals(Object that) {
	if (!m_initialized) {
	    Log.WARN("equals called on uninitialize HADB domain snapshot");
	    return false;
	}
	HADBDomain thatDomain = null;
	
	if (! (that instanceof HADBDomain)) {
	    return false;
	}
	
	thatDomain = (HADBDomain)that;
	int thisSize = this.m_domainMembersMap.values().size();
	int thatSize = thatDomain.m_domainMembersMap.values().size();
	
	if (thisSize !=  thatSize) {
	    Log.INFO("The membership size between the two domains differ.");
	    Log.INFO("This domain has " + thisSize + " members");
	    Log.INFO("That domain has " + thatSize + " members");
	    return false;
	}
	
	Iterator iter = thatDomain.m_domainMembersMap.values().iterator();
	while (iter.hasNext()) {
	    HADBDomainMember curMember = (HADBDomainMember)iter.next();
	    HADBDomainMember myMember = (HADBDomainMember)m_domainMembersMap.get(curMember.getHostName());
	    if (myMember == null) {
		Log.INFO("A Member exists in one domain but not the other.");
		Log.INFO("Missing member is " + myMember);
		return false;
	    }
	    if (!myMember.equals(curMember)) {
		Log.INFO("DomainMember "+ myMember.getHostName() +
			" exists in both domains but are not equal.");
		Log.INFO("Value of member in left domain is " + myMember);
		Log.INFO("Value of member in right domain is " + curMember);
		return false;
	    }
	}
	
	return true;
    }
    
    private void isInitialized () throws IllegalStateException {
	if (!m_initialized) {
	    throw new IllegalStateException("HADB Domain snapshot has not " +
	    		"been initialized");
	}
    }
    
    

}
