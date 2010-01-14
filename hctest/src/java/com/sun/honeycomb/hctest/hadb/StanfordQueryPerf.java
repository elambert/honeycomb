package com.sun.honeycomb.hctest.hadb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.parsers.ParseException;
import com.sun.honeycomb.hadb.convert.QueryConvert;

public class StanfordQueryPerf {
	public static void main(String[] args) {
		StanfordQueryPerf qp = new StanfordQueryPerf();
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
					m_dbURL = args[++i];
				} else {
					throw new InvalidArgException(
							"You must specify the URL for the database with the "
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
			} else if (curArg.equalsIgnoreCase("-noconvert")) {
				m_noConvert = true;
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
				+ StanfordQueryPerf.class.getName());
		System.err.println("");
		System.err.println("Usage: ");
		System.err.println("  " + prog + " [options] ");
		System.err.println("");
		System.err.println("Options are:");
		System.err.println("  -db                      name of host on which database is running.Defaults to hcb101");
		System.err.println("  -fetch                   fetch size used by result set.Defaults to 1000.");
		System.err.println("  -threads                 number of threads. Defaults to 1.");
		System.err.println("  -test                    execute in test mode.");
		System.err.println("  -verbose                 Print the query text and times as you execute them");
		System.err.println("  -iters                   Number of iterations to execute. Defaults to 1");
		System.err.println("  -noconvert               Don't use QueryConvert to create query text");
		System.err.println("");
		System.err.println("Note: This utility uses some server code to create the query strings. The code  ");
		System.err.println("      that generates these strings requires that:");
		System.err.println("      You set the System property emulator.root to point to directory that ");
		System.err.println("      contains the following files:");
		System.err.println("      -config/metadata_config_factory.xml");
		System.err.println("      -config/metadata_config.xml");
		}

	public void init() throws SQLException, ClassNotFoundException {
		loadStanfordOidList(m_numberOfIters);
		m_stat = new PerformanceStatistic(PerformanceStatistic.OP_TYPE_QUERY,new String("Standford Query"),true);
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
	
	public void executeQueries(Connection conn) throws SQLException {
		Iterator iter = m_stanfordOids.iterator();
		String curOid = null;
		while (iter.hasNext()) {
			curOid = (String)iter.next();
			
		}
		StringBuffer qryText = new StringBuffer();
		try {
			if (m_noConvert) {
				qryText.append("select objectid from stringattribute where attrname='oid' and attrvalue = '" + curOid + "';");
			} else {
				QueryConvert qc = new QueryConvert(null,"oid = '" + curOid +"'", null);
				qc.convert(qryText);
			}
		} catch (EMDException emd) {
			emd.printStackTrace();
			throw new SQLException(emd.getMessage());
		} catch (ParseException pe) {
			throw new SQLException(pe.getMessage());
		}
		InstrumentedQuery iq = new InstrumentedQuery();
		iq.setConnection(conn);
		iq.setQryString(qryText.toString());
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
		postResult(iq);

	}

	


	private synchronized void postResult(InstrumentedQuery iq) {
		m_stat.addOperation(iq.getExecTime(), iq.getQryString(), iq.getRecords() );
		if (m_verboseMode) {
			System.out.println(iq.getQryString() + ": " + iq.getExecTime() );
			System.out.println("");
		}
	}

	private void summarize() {	
		m_stat.print();
		m_stat.printDistribution();				
	}

	private synchronized void loadStanfordOidList (int size) throws SQLException, ClassNotFoundException {
		Connection feederConn = Utils.getConnection();
		Statement feederStatement = feederConn.createStatement();
		ResultSet feederResultSet = feederStatement.executeQuery(
					"select attrvalue from stringattribute where attrname='oid'");
		int i = 0;
		while (feederResultSet.next()  && i++ < size) {
			String sOid = feederResultSet.getString(1);
			System.out.println("Adding " + sOid);
			if (sOid == null) {
				break;
			}
			m_stanfordOids.add(sOid);
		}
		feederStatement.close();
		feederConn.close();
	}
	

	

	private int m_fetchSize = 100;

	private int m_numberOfThreads = 1;


	private boolean m_testMode = false;

	private boolean m_verboseMode = false;
	
	private int m_numberOfIters = 1;
	
	private ArrayList m_stanfordOids = new ArrayList();
	
	private PerformanceStatistic m_stat = null;
	
	private String m_dbURL = null;

	private boolean m_noConvert = false;
	
	class QueryRunner implements Runnable {
		boolean finished = false;
		public void run ()  {
			Connection conn = null;
			try {
				if (m_dbURL == null) {
					conn = Utils.getConnection();
				} else {
					conn = Utils.getConnection(m_dbURL);
				}
				for (int i = 0; i < m_numberOfIters; i ++) {
					executeQueries(conn);
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
