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
import java.util.Collection;
import java.util.Iterator;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;

import com.sun.honeycomb.hctest.hadb.generators.MetadataGenerator;
import com.sun.honeycomb.hctest.hadb.generators.OfotoGenerator;
import com.sun.honeycomb.hctest.hadb.generators.QASchemaGenerator;
import com.sun.honeycomb.hctest.hadb.generators.StanfordGenerator;
import com.sun.honeycomb.hctest.hadb.schemas.MetadataSchema;
public class Loader {
	
	   public static void main (String [] args) {
	        Loader me = new Loader();
	        try {
	            me.parseArgs(args);
	            me.init();
	            me.loadHadb();
	        } catch (InvalidArgException uae) {
	            System.err.println("Invalid command line argument.");
	            System.err.println(uae.getMessage());
	            System.exit(1);
	        } catch (SQLException sqe) {
	            System.err.println("An error occurred!\n" + sqe.getMessage());
	            System.exit(2);
	        } catch (ClassNotFoundException cnfe) { 
	            System.err.println("Class Not Found Exception: " + cnfe.getMessage());
	            System.exit(2);            
	        } catch (Throwable t) {
	        		System.err.println("Man oh man! Bad things happened.");
	        		t.printStackTrace();
	        		System.exit(2);	        
	        	}
	   
	     }
	    
	    public void loadHadb() throws SQLException, ClassNotFoundException {
	        loadHadb(m_numberOfRecs);
	    }

	    
	    public void loadHadb(long numberOfRecords) throws SQLException, ClassNotFoundException {
	        long i;
	        Collection md;
	        String oid=null;
	        ArrayList insertStats = new ArrayList();
	        long statLevelBreak = (long) numberOfRecords/10;
	        PerformanceStatistic currLevelStats = null;
	        for (i = 0; i < numberOfRecords; i++) {
	        	   if ( i % statLevelBreak ==  0) {
	        		   currLevelStats = new PerformanceStatistic(PerformanceStatistic.OP_TYPE_INSERT,"Records " + i + " - " + (i + statLevelBreak), false);
	        		   insertStats.add(currLevelStats);
	        	   }
	        	   if ( i % 10000 == 0 ) {
	        		   System.err.println("Number of metadata records stored: " + i );
	        	   }
	            oid=Utils.generateOID();
	            md = m_mdGenerator.generateMetaData();
	            if (md == null) {
	            	 System.err.println("Unable to generate metadata");
	            }
	            storeMD(oid, md, m_SqlConn, currLevelStats);
	        }
	        
	        System.out.println("");
	        this.generateStats(insertStats);    
	    }
	  
	    public void usage() {
	         String prog = System.getProperty("program", "java " + Loader.class.getName());
	         System.err.println("");
	         System.err.println("Usage: ");
	         System.err.println("  " + prog + " -attrs <number_of_attrs> -rec <number_of_md_recs> [options] ");
	         System.err.println("");
	         System.err.println("Options are:");
	         System.err.println("  -rec                     number of records to be inserted. Defaults to 1000");
	         System.err.println("  -db                      url used to contact the database.");
	         System.err.println("  -loadPattern             name of pattern used to load the database.");
	         System.err.println("");    
	         System.err.println("The followning jars will need to be on Classpath to run this tool");   
	         System.err.println("honeycomb-hctest.jar");
	         System.err.println("honeycomb-server.jar");
	         System.err.println("jug.jar");
	         System.err.println("jetty-4.2.20.jar");
	         System.err.println("hadbjdbc4.jar");
	         System.err.println("");    
	         System.err.println("Also, you will need to set the following system property -Duid.lib.path=emulator on the command line");
	    }
	    
	    private void init () throws ClassNotFoundException, SQLException, Throwable {
	    		if (m_databaseURL == null) {
	    			m_SqlConn = Utils.getConnection();
	    		} else {
	    			m_SqlConn = Utils.getConnection(m_databaseURL);
	    		}
	    		
	    		m_mdGenerator = getGenerator(m_loadPattern);
	    		m_schema = m_mdGenerator.getSchema();
	    }
	    
	    
	    private void parseArgs(String [] args) throws InvalidArgException {
	        String currentArg = null;
	        for (int i = 0; i < args.length; i++ ) {
	            currentArg=args[i];
	            if (currentArg.equals(CLI_TOKEN_NUM_RECS)) {
	                if (i < args.length) {
	                    m_numberOfRecs=Long.parseLong(args[++i]);
	                } else {
	                    throw new InvalidArgException(
	                            "You must specify the number of records with the "+ 
	                            currentArg + "option.");
	                } 
	            } else if (currentArg.equals(CLI_TOKEN_DB_HOST)) {
	                if (i < args.length) {
	                    m_databaseURL=args[++i];
	                } else {
	                    throw new InvalidArgException(
	                            "You must specify the address of the hadb database with the "+ 
	                            currentArg + "option.");
	                }  
	            } else if (currentArg.equals(CLI_LOAD_PATTERN)) {
	                if (i < args.length) {
	                    m_loadPattern=args[++i];
	                } else {
	                    throw new InvalidArgException(
	                            "You must specify the load pattern you want to use with the "+ 
	                            currentArg + "option.");
	                }  
	            } else if (currentArg.equals(CLI_TOKEN_HELP)) {
	                usage();
	                System.exit(0);
	            } else {
	                throw new InvalidArgException("Unrecognized argument " +currentArg);
	                
	            }
	        }
	    }
	    
	    
	    private void storeMD(String oid, Collection md, Connection conn, PerformanceStatistic stats) throws SQLException {
	        StringBuffer stringInsertStatement = null;
	        StringBuffer doubleInsertStatement = null;
	        StringBuffer longInsertStatement = null;
	        Iterator iter = md.iterator();
	        while (iter.hasNext()) {
	            MetadataAttribute currentAttr = (MetadataAttribute) iter.next();
	            String type = m_schema.getFieldType(currentAttr.getAttrName());
	            if (type.equals(Utils.STRING_TYPE)) {
	                if (stringInsertStatement == null) {
	                    stringInsertStatement=new StringBuffer();
	                    stringInsertStatement.append("('" + currentAttr.getAttrName() + "','" + currentAttr.getAttrValue() 
	                                                                         + "','" + oid + "')");
	                } else {
	                    stringInsertStatement.append(",('" + currentAttr.getAttrName() + "','" + currentAttr.getAttrValue() 
	                                                                         + "','" + oid + "')");
	                }
	            } else if (type.equals(Utils.DOUBLE_TYPE)) {
	                if (doubleInsertStatement == null) {
	                    doubleInsertStatement=new StringBuffer();
	                    doubleInsertStatement.append("('" + currentAttr.getAttrName() + "'," + currentAttr.getAttrValue() 
	                                                                         + ",'" + oid + "')");
	                } else {
	                    doubleInsertStatement.append(",('" + currentAttr.getAttrName() + "'," + currentAttr.getAttrValue() 
	                                                                         + ",'" + oid + "')");
	                }
	            } else if(type.equals(Utils.LONG_TYPE)) {
	                if (longInsertStatement == null) {
	                    longInsertStatement=new StringBuffer();
	                    longInsertStatement.append("('" + currentAttr.getAttrName() + "'," + currentAttr.getAttrValue()
	                                                                         + ",'" + oid + "')");
	                } else {
	                    longInsertStatement.append(",('" + currentAttr.getAttrName() + "'," + currentAttr.getAttrValue()
	                                                                         + ",'" + oid + "')");                    
	                }
	            } else {
	                // do something meaningful here
	            }
	        }
	        
	        if (stringInsertStatement != null) {
	            stats.addOperation(insertRow(conn,"insert into stringattribute values " + stringInsertStatement.toString() + ";"));
	        }
	        if (longInsertStatement != null) {
	            stats.addOperation(insertRow(conn,"insert into longattribute values " + longInsertStatement.toString() + ";"));
	        }
	        if (doubleInsertStatement != null) {
	             stats.addOperation(insertRow(conn,"insert into doubleattribute values " + doubleInsertStatement.toString() + ";"));
	        }
	    }
	    
	    private long insertRow(Connection conn, String text) throws SQLException {
	    		long start = 0;
	    		long end = 0;
	    		if (m_statement == null) {
	    			m_statement = conn.createStatement();
	    		}
	    		start = System.currentTimeMillis();
	    		m_statement.execute(text);
	    		end = System.currentTimeMillis();
	    		return end - start;
	    }
	    
	    private void generateStats(ArrayList stats) {
	    		PerformanceStatistic grandTotal = new PerformanceStatistic(PerformanceStatistic.OP_TYPE_INSERT,"Grand Total", false);
	    		Iterator iter = stats.iterator();
	    		while (iter.hasNext()) {
	    			PerformanceStatistic ps = (PerformanceStatistic)iter.next();
	    			ps.print();
	    			grandTotal.add(ps);
	    		}
	    		grandTotal.print();
	    }
	   
	    private MetadataGenerator getGenerator(String name) throws IllegalStateException, Throwable {
	    		if (name.equalsIgnoreCase("stanford")) {
	    			return new StanfordGenerator();
	    		} else if (name.equalsIgnoreCase("qaschema")) {
	    			return new QASchemaGenerator();
	    		} else if (name.equalsIgnoreCase("ofoto")) {
	    			return new OfotoGenerator();
	    		} else {
	    			throw new IllegalStateException("Unkown generator " + name);
	    		}
	    }
	    
	    
	    
	    class InvalidArgException extends Exception {
	        InvalidArgException (String msg) {
	            super(msg);
	        }
	    }
	    

	    
	    private static final String CLI_TOKEN_NUM_RECS = "-rec";
	    private static final String CLI_TOKEN_DB_HOST = "-db";
	    private static final String CLI_TOKEN_HELP = "-help";
	    private static final String CLI_LOAD_PATTERN = "-loadpattern";
	    private final int m_numberOfElements = Utils.STRING_ATTRS.length + Utils.DOUBLE_ATTRS.length + Utils.LONG_ATTRS.length;
	    private Connection m_SqlConn;
	    private long m_numberOfRecs = 1000;
	    private String m_databaseURL = null;
	    private Statement m_statement = null;
	    private MetadataGenerator m_mdGenerator = null;
	    private String m_loadPattern = "default";
	    private MetadataSchema m_schema = null;
}

