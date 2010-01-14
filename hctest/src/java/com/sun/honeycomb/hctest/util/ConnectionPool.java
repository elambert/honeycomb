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

 

package com.sun.honeycomb.hctest.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import com.sun.honeycomb.hctest.Audit;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class ConnectionPool {

   private Vector connections;
   private String url, user, password;

   final private long timeout=60000;
   final private int poolsize=10;
   
   private static Hashtable instances = new Hashtable();
   private static ConnectionReaper reaper;
   
   private ConnectionPool(String url, String user, String password) {
      this.url = url;
      this.user = user;
      this.password = password;
      this.connections = new Vector(poolsize);
   }
   
   private static Boolean runOnce = Boolean.FALSE;
   private static void initConnectionPool() throws HoneycombTestException {
	    synchronized (runOnce){
	    	if (!runOnce.booleanValue()) {
	    		runOnce = Boolean.TRUE;

	    		// ConnectionPool cleanup thread.
				reaper = new ConnectionReaper(instances);
				reaper.start();
				
				//
				// load driver - it registers w/ jdbc
				//
				try {
					Class.forName("org.postgresql.Driver");
				} catch (ClassNotFoundException e) {
					throw new HoneycombTestException(e);
				}
	    	}
	    }
	}
   
   public static ConnectionPool getInstance(String dbhost, String cluster)
			throws HoneycombTestException {
		String key = dbhost + ":" + cluster;

		initConnectionPool();

		ConnectionPool instance = null;
		synchronized (instances) {
			instance = (ConnectionPool) ConnectionPool.instances.get(key);
			if (instance == null) {
				String url = "jdbc:postgresql://" + dbhost + "/" + cluster;
				instances.put(key, (instance = new ConnectionPool(url, cluster,"")));
			}
		}

		return instance;
	}

	public static ConnectionPool getInstance(String cluster)
			throws HoneycombTestException {
            String audithost = System.getProperty(HCLocale.PROPERTY_DBHOST);
            if (audithost == null) {
                throw new HoneycombTestException(
                                             "System property not defined: " +
                                             HCLocale.PROPERTY_DBHOST);
            }
	    return getInstance(audithost, cluster);
	}

   public synchronized void reapConnections() {

      long stale = System.currentTimeMillis() - timeout;
      Enumeration connlist = connections.elements();
    
      while((connlist != null) && (connlist.hasMoreElements())) {
          ConnectionWrapper conn = (ConnectionWrapper)connlist.nextElement();

          if((conn.inUse()) && (stale >conn.getLastUse()) && 
                                            (!conn.validate())) {
 	      removeConnection(conn);
         }
      }
   }

   public synchronized void closeConnections() {
        
      Enumeration connlist = connections.elements();

      while((connlist != null) && (connlist.hasMoreElements())) {
          ConnectionWrapper conn = (ConnectionWrapper)connlist.nextElement();
          removeConnection(conn);
      }
   }

   private synchronized void removeConnection(ConnectionWrapper conn) {
       connections.removeElement(conn);
       try {
			conn.close();
		} catch (SQLException e) {
			Log.ERROR("Exception closing connection: " + Log.stackTrace(e));			
		}
   }

   public synchronized long countOpenConnections() {

       ConnectionWrapper c;
       long count = 0;
       for(int i = 0; i < connections.size(); i++) {
           c = (ConnectionWrapper)connections.elementAt(i);
           if (c.inUse()) {
              count++;
           }
       }
       
       return count;
   }
   
   public synchronized long countExistingConnections() {       
       return connections.size();
   }

   public synchronized ConnectionWrapper getConnection() throws SQLException {

       ConnectionWrapper c;
       for(int i = 0; i < connections.size(); i++) {
           c = (ConnectionWrapper)connections.elementAt(i);
           if (c.lease()) {
              return c;
           }
       }

       Connection conn = DriverManager.getConnection(url, user, password);
       conn.setAutoCommit(false);
       conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
       c = new ConnectionWrapper(conn, this);
       c.lease();
       connections.addElement(c);
       return c;
  } 

   public synchronized void returnConnection(ConnectionWrapper conn) {
      conn.expireLease();
   }
   
   public static void stopReaperThread(){
	   if (reaper != null)
		   reaper.timeToStop();
   }
}
