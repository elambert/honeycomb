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



package com.sun.honeycomb.hctest.hadb;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;


public class DeletePerf {

	public static void main(String[] args) {
		DeletePerf dp = new DeletePerf();
		try {
			dp.parseArgs(args);
			dp.init();
			dp.run();
			dp.summarize();
		} catch (InvalidArgException iae) {
			System.err.println("Command line error " + iae.getMessage());
			System.exit(1);
		} catch (SQLException sqe) {
			System.err.println("SQLException encountered: " + sqe.getMessage());
			sqe.printStackTrace();
			System.exit(2);
		} catch (ClassNotFoundException cfne) {
			System.err.println("Class Not Found Exception: " + cfne.getMessage());
			System.err.println("Missing JDBC Driver on class path maybe?");
			System.exit(2);
		}
		System.exit(0);
	}

	public void parseArgs(String[] args) throws InvalidArgException {
		for (int i = 0; i < args.length; i++) {
			String curArg = args[i];
			if (curArg.equalsIgnoreCase("-db")) {
				if (i < args.length) {
					m_dbURL = args[++i];
				} else {
					throw new InvalidArgException(
							"You must specify the name of the database host with the "
									+ curArg + "option.");
				}
			} else if (curArg.equalsIgnoreCase("-help")) {
				usage();
				System.exit(0);
			} else if (curArg.equalsIgnoreCase("-threads")) {
				if (i < args.length) {
					m_numberOfThreads = Integer.parseInt(args[++i]);
				} else {
					throw new InvalidArgException(
							"You must specify the number of threads"
									+ " with the " + curArg + " option.");
				}
			}  else if (curArg.equalsIgnoreCase("-deletes")) {
				if (i < args.length) {
					m_numberOfDeletes = Integer.parseInt(args[++i]);
				} else {
					throw new InvalidArgException(
							"You must specify the number of deletes"
									+ " with the " + curArg + " option.");
				}	
			} else if (curArg.equalsIgnoreCase("-test")) {
				m_testMode = true;
			} else if (curArg.equalsIgnoreCase("-verbose")) {
				m_verboseMode = true;
		    }else {
				throw new InvalidArgException("Unknow option " + curArg);
			}
		}
	}


	public static void usage() {
		String prog = System.getProperty("program", "java "
				+ QueryPerf.class.getName());
		System.err.println("");
		System.err.println("Usage: ");
		System.err.println("  " + prog + " [options] ");
		System.err.println("");
		System.err.println("Options are:");
		System.err.println("  -db                      name of host on which database is running.Defaults to hcb101");
		System.err.println("  -threads                 number of threads. Defaults to 1.");
		System.err.println("  -test                    execute in test mode.");
		System.err.println("  -verbose                 Print the query text and times as you execute them");
		System.err.println("  -deletes                 Number of records to delete. Defaults to 1");
		System.err.println("");
		}
	
	public void init() throws SQLException, ClassNotFoundException {
		if (m_dbURL == null ) {
			m_connection = Utils.getConnection();
		} else {
			m_connection = Utils.getConnection(m_dbURL);
		}
		m_oids = Utils.getOIDs(m_numberOfDeletes, m_connection);
	}
	
	public void run () {
		ArrayList myThreads = new ArrayList();
		Iterator iter = null;
		for (int i = 0; i < m_numberOfThreads; i++) {
			DeleteRunner dr = new DeleteRunner();
			myThreads.add(dr);
			new Thread(dr).start();
		}
		iter = myThreads.iterator();
		while (iter.hasNext()) {
			DeleteRunner curDl = (DeleteRunner)iter.next();
			while (!curDl.finished) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException ignored) {}
			}
		}
	}
	
	public void executeDeletes(Connection conn, PerformanceStatistic stat) throws SQLException {
		String curOid = getNextOID();
		if (curOid == null) {
			System.out.println("no more!");
			return;
		}
		System.out.println("gonna delete " + curOid);
		Statement stmt = conn.createStatement();
		stat.addOperation(executeDeleteOID(curOid,stmt),"Deleting oid " + curOid);
		stmt.close();

	}
	
	public long executeDeleteOID (String OID, Statement stmt)  throws SQLException {
		long execTime = 0;
		String whereClause = "where objectid='"+OID+"'";
		String dltString = "delete from stringattribute " + whereClause;
		String dltLong = "delete from longattribute " + whereClause;
		String dltDbl = "delete from doubleattribute " + whereClause;
		
		if (m_testMode) {
			System.out.println(dltString);
			System.out.println(dltLong);
			System.out.println(dltDbl);
		}
		if (!m_testMode ){
		
			
		
			
			// delete from long
			if (m_verboseMode) {
				System.out.println(dltLong);
			}
			long start = System.currentTimeMillis();
			stmt.execute(dltLong);
			execTime += System.currentTimeMillis() - start;
			
//			 delete from string
			if (m_verboseMode) {
				System.out.println(dltString);
			}
			start = System.currentTimeMillis();
			stmt.execute(dltString);
			execTime = System.currentTimeMillis() - start;
//			 delete from double
			if (m_verboseMode) {
				System.out.println(dltDbl);
			}
			start = System.currentTimeMillis();
			stmt.execute(dltDbl);
			execTime += System.currentTimeMillis() - start;
		}
		return execTime;
	}
	
	
	private void summarize() {
		PerformanceStatistic grandTotal = new PerformanceStatistic(PerformanceStatistic.OP_TYPE_DELETE,"Grand Total", false);
		Iterator iter = m_results.iterator();
		while (iter.hasNext()) {
			PerformanceStatistic ps = (PerformanceStatistic)iter.next();
			ps.print();
			grandTotal.add(ps);
		}
		grandTotal.print();
	}
	
	private synchronized String getNextOID () {
		if (m_oidIter == null ) {
			m_oidIter = m_oids.iterator();
		}
		if (m_oidIter.hasNext()) {
			return (String) m_oidIter.next();
		} else {
			return null;
		}
	}
	
	private String m_dbURL=null;
	private int m_numberOfThreads = 1;
	private long m_numberOfDeletes = 1;
	private boolean m_testMode = false;
	private boolean m_verboseMode = false;
	private Connection m_connection = null;
	private ArrayList m_oids = null;
	private Iterator m_oidIter = null;
	private ArrayList m_results = new ArrayList();
	
	
	class DeleteRunner implements Runnable {
		boolean finished = false;
		public void run ()  {
			PerformanceStatistic stat = new PerformanceStatistic(PerformanceStatistic.OP_TYPE_DELETE,
															"deleteThread",true);
			Connection conn = null;
			try {
				if (m_dbURL == null) {
					conn = Utils.getConnection();
				} else {
					conn = Utils.getConnection(m_dbURL);
				}
				for (int i = 0; i < m_numberOfDeletes; i ++) {
					executeDeletes(conn,stat);
				}
				conn.close();
			} catch (SQLException sqe) {
				System.err.println(sqe.getMessage());
				System.err.println(sqe.getCause());
				sqe.printStackTrace();
			} catch (ClassNotFoundException cne) {
				System.err.println("Class Not Found Exception. JDBC Driver not on class path?");
				System.err.println(cne.getMessage());
			} finally {
				m_results.add(stat);
				finished=true;
			}
		}
		
	}

	class InvalidArgException extends Exception {
		InvalidArgException(String msg) {
		super(msg);
		}
	}

}