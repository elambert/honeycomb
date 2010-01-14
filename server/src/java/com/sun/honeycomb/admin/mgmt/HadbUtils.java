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



package com.sun.honeycomb.admin.mgmt;

import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.hadb.adminapi.Database;
import com.sun.hadb.adminapi.HADBException;
import com.sun.hadb.adminapi.MAConnection;
import com.sun.hadb.adminapi.MAConnectionFactory;
import com.sun.hadb.adminapi.ManagementDomain;
import com.sun.hadb.mgt.DatabaseState;

/**
 * Takes the HADB status-getting methods out of HCHadbAdapter.java
 * that are needed for Upgrader, and puts them somewhere where both
 * HCHadbAdapter and Upgrader can reach them.
 */

public class HadbUtils {
    private static MAConnection hadbMaConnections = null; 

    private static final Logger logger = 
	Logger.getLogger(HadbUtils.class.getName());

    /*
     * Method to check if HADB is HAFaultTolerant (ie cluster is
     * "sane")
     * @return boolean true if HADB is HAFaultTolerant, false
     * otherwise
     */
    public static boolean getClusterSane() {
	String status = getHadbStatus();
	return status.equals("HAFaultTolerant");
    }

    /*
     * Method to check status of HADB
     * @return String representing the database status
     */
    public static String getHadbStatus() {
	String state = "unavailable";
	ManagementDomain md = null;
	Database db = null;
	try {
	    initHadbConnection();
	    md = hadbMaConnections.getDomain();
	    if (md != null) {
		db = md.getDatabase("honeycomb");
		DatabaseState dbs = null;
		if (db != null) {
		    dbs= db.getDatabaseState();
		    state = dbs.toString();
		}
	    }
	} catch (HADBException he) {
	    logger.log(Level.WARNING, "Encountered an exception while retrieving HADB" +
			   " status ", he);
	} finally {
	    closeHadbConnection();
	}
	return state;
    }

    /*
     * Private method used by getHadbStatus to connect to the database
     */
    private static void initHadbConnection () throws HADBException {
        if (hadbMaConnections != null) {
            return;
        }
	
        int hadb_port = 1862;
        int numCellNodes = Utils.getNumNodes();
        StringBuffer url = new StringBuffer();
	
        for (int i = 1; i < numCellNodes; i++) {
            String ip = "10.123.45." + (100 + i);
            if (url.length() == 0) {
                url.append(ip);
            } else {
                url.append(","+ip);
            }
        }

        url.append(":"+hadb_port);
        hadbMaConnections = MAConnectionFactory.connect(url.toString(),
                                                        "admin","admin");
    }
    
    /*
     * Private method used by getHadbStatus() to close the connection
     * to the database
     */
    private static void closeHadbConnection () {
        if (hadbMaConnections != null ) {
            hadbMaConnections.close();
            hadbMaConnections = null;
        }
    } 
}
