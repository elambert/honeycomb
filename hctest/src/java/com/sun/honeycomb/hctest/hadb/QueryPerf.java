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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.sql.Connection;
import java.sql.SQLException;

import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.parsers.ParseException;

public class QueryPerf {

	public static void main(String[] args) {
		QueryPerf qp = new QueryPerf();
		try {
			qp.parseArgs(args);
			qp.init();
			qp.run();
			qp.summarize();
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
					m_dbHost = args[++i];
				} else {
					throw new InvalidArgException(
							"You must specify the name of the database host with the "
									+ curArg + "option.");
				}
			} else if (curArg.equalsIgnoreCase("-dbport")) {
				if (i < args.length) {
					m_dbPort = Integer.parseInt(args[++i]);
				} else {
					throw new InvalidArgException(
							"You must specify the database port with the "
									+ curArg + "option.");
				}
			} else if (curArg.equalsIgnoreCase("-fetch")) {
				if (i < args.length) {
					m_fetchSize = Integer.parseInt(args[++i]);
				} else {
					throw new InvalidArgException(
							"You must specify the fetch size with the "
									+ curArg + "option.");
				}
			} else if (curArg.equalsIgnoreCase("-depth")) {
				if (i < args.length) {
					m_maxDepth = Integer.parseInt(args[++i]);
				} else {
					throw new InvalidArgException(
							"You must specify the depth with the " + curArg
									+ "option.");
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
			}  else if (curArg.equalsIgnoreCase("-iters")) {
				if (i < args.length) {
					m_numberOfIters = Integer.parseInt(args[++i]);
				} else {
					throw new InvalidArgException(
							"You must specify the number of threads"
									+ " with the " + curArg + " option.");
				}	
			} else if (curArg.equalsIgnoreCase("-test")) {
				m_testMode = true;
			} else if (curArg.equalsIgnoreCase("-verbose")) {
				m_verboseMode = true;
		    } else {
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
		System.err.println("  -dbport                  port on which the database is listening. Defaults to 15005");
		System.err.println("  -fetch                   fetch size used by result set.Defaults to 1000.");
		System.err.println("  -depth                   Query depth. Defaults to 6.");
		System.err.println("  -threads                 number of threads. Defaults to 1.");
		System.err.println("  -test                    execute in test mode.");
		System.err.println("  -verbose                 Print the query text and times as you execute them");
		System.err.println("  -iters                   Number of iterations to execute. Defaults to 1");
		System.err.println("");
		System.err.println("Note: This utility uses some server code to create the query strings. The code  ");
		System.err.println("      that generates these strings requires that:");
		System.err.println("      You set the System property emulator.root to point to directory that ");
		System.err.println("      contains the following files:");
		System.err.println("      -config/metadata_config_factory.xml");
		System.err.println("      -config/metadata_config.xml");
		}

	public void init() throws SQLException, ClassNotFoundException {
		//Class.forName(Utils.JDBC_DRIVER_CLASS);
		m_masterTokenSet = Utils.generateQueryTokensMasterList();
		m_results = new ArrayList();
		for (int i = 0; i < m_maxDepth; i++) {
			m_results.add(new PerformanceStatistic(
							 					PerformanceStatistic.OP_TYPE_QUERY,
							 					new String("Level " + (i+1) + "Queries"),
							 					true ));
		}
	}
	
	public void run () {
		ArrayList myThreads = new ArrayList();
		Iterator iter = null;
		for (int i = 0; i < m_numberOfThreads; i++) {
			QueryRunner qr = new QueryRunner();
			myThreads.add(qr);
			new Thread(qr).start();
		}
		iter = myThreads.iterator();
		while (iter.hasNext()) {
			QueryRunner curQr = (QueryRunner)iter.next();
			while (!curQr.finished) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException ignored) {}
			}
		}
	}
	
	public void executeQueries(Connection conn, HashSet tokens) throws SQLException {
		String qryText = null;
		QueryToken selectToken = generateNewSelectToken(tokens);
		tokens.add(selectToken);
		try {
			qryText = Utils.generateQry(tokens, m_verboseMode);
		} catch (EMDException emd) {
			emd.printStackTrace();
			throw new SQLException(emd.getMessage());
		} catch (ParseException pe) {
			throw new SQLException(pe.getMessage());
		}
		InstrumentedQuery iq = new InstrumentedQuery();
		iq.setConnection(conn);
		iq.setQryString(qryText);
		iq.setFetchSize(m_fetchSize);
		if (!m_testMode) {
			try {
				iq.executeQry();
			} catch (SQLException sqe) {
				System.err.println("The query " + iq.getQryString() + " resulted in an SQL Exception.");
				throw sqe;
			}
		} else {
			System.out.println(iq.getQryString());
		}
		postResult(tokens.size() - 1, iq);
		if (tokens.size() < m_maxDepth) {
			selectToken.isSelectToken = false;
			String[] values = getValues(selectToken);
			for (int i = 0; i < values.length; i++) {
				selectToken.m_value = values[i];
				executeQueries(conn,(HashSet) tokens.clone());
			}
		}

	}

	private String[] getValues(QueryToken qt) {
		if (qt.m_type.equals(Utils.STRING_TYPE)) {
			return Utils.STRING_VALUES;
		} else if (qt.m_type.equals(Utils.DOUBLE_TYPE)) {
			return Utils.DOUBLE_VALUES;
		} else if (qt.m_type.equals(Utils.LONG_TYPE)) {
			return Utils.LONG_VALUES;
		} else {
			return null; // TODO throw an exception of something
		}
	}

	private QueryToken generateNewSelectToken(HashSet whereClause) {
		QueryToken selectToken;
		while (true) {
			selectToken = (QueryToken) m_masterTokenSet.get(Utils
					.getRandomInt(m_masterTokenSet.size()));
			if (!whereClause.contains(selectToken))
				break;
		}
		selectToken.isSelectToken = true;
		return selectToken;
	}

	private synchronized void postResult(int level, InstrumentedQuery iq) {
		PerformanceStatistic stat = (PerformanceStatistic) m_results.get(level);
		stat.addOperation(iq.getExecTime(), iq.getQryString(), iq.getRecords() );
		if (m_verboseMode) {
			System.out.println(iq.getQryString() + ": " + iq.getExecTime() );
			System.out.println("");
		}
	}

	private void summarize() {
				
		PerformanceStatistic grandTotal = new PerformanceStatistic(PerformanceStatistic.OP_TYPE_QUERY,"Grand Total", true);
		Iterator iter = m_results.iterator();
		while (iter.hasNext()) {
			PerformanceStatistic ps = (PerformanceStatistic)iter.next();
			ps.print();
			grandTotal.add(ps);
		}
		grandTotal.print();
		grandTotal.printDistribution();				
	}

	
	//private String m_dbHost = "hcb101";

	private String m_dbHost = null;
	
	private int m_dbPort = 15005;

	private ArrayList m_masterTokenSet = null;

	private int m_maxDepth = 6;

	private int m_fetchSize = 100;

	private int m_numberOfThreads = 1;

	private ArrayList m_results;

	private boolean m_testMode = false;

	private long m_fastestQry = -1;
	
	private long m_slowestQry = 0;

	private String m_slowestQryText = "";

	private boolean m_verboseMode = false;
	
	private int m_numberOfIters = 1;
	
	/*
	private boolean m_generateWhere = true;
	
	private String m_query = null;
*/	

	class QueryRunner implements Runnable {
		boolean finished = false;
		public void run ()  {
			Connection conn = null;
			try {
				conn = Utils.getConnection();
				for (int i = 0; i < m_numberOfIters; i ++) {
					executeQueries(conn, new HashSet());
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