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

import com.sun.hadb.adminapi.Database;
import com.sun.hadb.adminapi.HADBException;
import com.sun.hadb.adminapi.ManagementDomain;
import com.sun.hadb.adminapi.Node;
import com.sun.hadb.mgt.DatabaseState;
import com.sun.honeycomb.test.util.Log;

/**
 * This class is a "snap shot" of an HADB Database object. 
 * It is used to represent the state of an HADB Database object at
 * a particular point in time (that point in time being when the initialize 
 * method is invoked.
 *
 * @author elambert
 */

public class HADBDatabase {
    
    /**
     * Initialize this object to represent the state of a particular 
     * HADB Database.
     * 
     * @param db the Name of the database to be snap-shotted
     * @param md The ManagementDomain which contains the database
     * 
     * @throws HADBException
     */
    public void initialize(String dbName, ManagementDomain md) 
    throws HADBException, IllegalStateException {
	m_md = md;
	if (m_md == null) {
	    throw new IllegalStateException ("The management domain is null");
	}
	Database db = m_md.getDatabase(dbName);
	if (db == null) {
	    throw new IllegalStateException ("no database called " 
		    + dbName + " found");
	}
	m_state = db.getDatabaseState();
	m_name = db.getDatabaseName();
	Iterator iter = db.getNodes().iterator();
	while (iter.hasNext()){
	    Node cn = (Node)iter.next();
	    m_nodes.put(new Integer(cn.getPhysicalNumber()), new HADBNode(cn));
	}
	m_isInitialized = true;
    }
    
    
    /**
     * @return true if that equals this object.
     * More specifically, the two objects are equal
     * when:
     * -that is not null
     * -that is instanceof HADBDatabase
     * -both databases are in the same state
     * -the set of nodes contained by both databases
     *  are equal. 
     */
    public boolean equals(Object that) {
	if (!m_isInitialized) {
	    Log.WARN("equals called on an unitialized HADB Database Snapshot");
	    return (false);
	}
	HADBDatabase thatDB = null;
	if (!(that instanceof HADBDatabase)) {
	    return false;
	}
	thatDB = (HADBDatabase) that;
	if (!m_state.equals(thatDB.m_state)) {
	    Log.INFO("Databases are in different states. " +
		    m_state + " : " + thatDB.m_state);
	    return false;
	}
	
	Iterator iter = m_nodes.values().iterator();
	while (iter.hasNext()) {
	    HADBNode curNode = (HADBNode)iter.next();
	    HADBNode thatNode = (HADBNode) thatDB.m_nodes.get(new Integer(curNode.getPhysicalNode()));
	    if (thatNode == null) {
		Log.INFO("An HADBNode appears in one database but not the other." + 
			" Physical Number for the missing node is " + curNode.getPhysicalNode());
		return false;
	    }
	    if (!curNode.equals(thatNode)) {
		Log.INFO("Physical Nodes "+ curNode.getPhysicalNode() + " do not match");
		Log.INFO("Value of Node in left database: " + curNode);
		Log.INFO("Value of Node in right database: " + thatNode);
		return false;
	    }
	}
	
	if (thatDB.m_nodes.values().size() != m_nodes.values().size()) {
	    Log.INFO("Right database has more nodes than left!");
	    Log.INFO("Numb of nodes in right database = " + thatDB.m_nodes.values().size());
	    Log.INFO("Numb of nodes in left database = " + m_nodes.values().size());
	    return false;
	}
	
	return true;
    }
    
   
    public String toString () throws IllegalStateException {
	isInitialized();
	return new String(m_name +" (State="+m_state+")");
    }
    
    
    /**
     * 
     * @return The state of the database at the time 
     * the snap-shot was taken.
     */
    public DatabaseState getState () throws IllegalStateException {
	isInitialized();
	return m_state;
    }
    
    
    /**
     * 
     * @return The name of the database.
     */
    public String getName () throws IllegalStateException {
	isInitialized();
	return m_name;
    }
    
    /**
     * 
     * @return A set which contains a copy of the nodes 
     * which participted in the database at the time of 
     * the snap-shot. The elements of this set are of 
     * type HADBNode.
     */
    public Set getNodes () throws IllegalStateException {
	isInitialized();
	return new TreeSet(m_nodes.values());
    }
    
    public boolean hasBeenWiped(long time) throws IllegalStateException {
	int numberOfNodesWiped = 0;
	int wipeThreshold = (m_nodes.size()/2);
	
	isInitialized();
	
	Iterator iter = getNodes().iterator();
	while (iter.hasNext()) {
	    HADBNode node = (HADBNode)iter.next();
	    if (node.isUp() && node.hasBeenWiped(time)) {
		numberOfNodesWiped++;
	    }
	}
	
	if (numberOfNodesWiped > wipeThreshold) {
	    Log.INFO("Number of wiped nodes (" + numberOfNodesWiped + 
		    ") exceeds threshold (" + wipeThreshold + 
		    ") . It appears we have wiped");
	    return true;
	} 
	if (numberOfNodesWiped > 0 ) {
	    Log.WARN("Found " + numberOfNodesWiped + 
		    " wipedNodes. This value is below our threshold of " 
		    + wipeThreshold);
	}
	return false;
    }
    
    private void isInitialized() throws IllegalStateException {
	if (!m_isInitialized) {
	    throw new IllegalStateException("The HADB Database snap" +
	    		" shot has not been initialized.");
	}
    }
    
    private boolean m_isInitialized = false;
    private ManagementDomain m_md = null;
    private DatabaseState m_state = null;
    private HashMap m_nodes = new HashMap();
    private String m_name = null;
    
}
