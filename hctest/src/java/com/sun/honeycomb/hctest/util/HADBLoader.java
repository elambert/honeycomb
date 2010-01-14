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

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.sql.ResultSet;

import com.sun.honeycomb.common.NewObjectIdentifier;


public class HADBLoader {
    
    /** Creates a new instance of Main */
    public HADBLoader () {
        
    }
    
    public static void main (String [] args) {
        HADBLoader me = new HADBLoader();
        try {
            me.parseArgs(args);
            me.init();
            me.loadHadb();
        } catch (InvalidArgException uae) {
            System.out.println("Invalid command line argument.");
            System.out.println(uae.getMessage());
            System.exit(1);
        } catch (SQLException sqe) {
            System.out.println("An error occurred!\n" + sqe.getMessage());
            System.exit(2);
        } catch (ClassNotFoundException cnfe) { 
            System.out.println("An error occurred!\n" + cnfe.getMessage());
            System.exit(2);            
        }
   
     }
    
    public void loadHadb() throws SQLException, ClassNotFoundException {
        loadHadb(m_numberOfRecs, m_numberOfAttrs);
    }

    
    public void loadHadb(long numberOfRecords, int numberOfAttrs) throws SQLException, ClassNotFoundException {
        long i;
        ArrayList md;
        String oid=null;
        long start = 0;
        long end = 0;
        start=System.currentTimeMillis();
        for (i =0; i < numberOfRecords; i++) {
            oid=generateOID();
            md = generateMetaData(numberOfAttrs);
            storeMD(oid, md, m_SqlConn);
            //storeMDPrepared(oid, md, m_SqlConn);
            System.out.print("Number of metadata records stored: " + i + "    \r");
        }
        end=System.currentTimeMillis();
        System.out.print("Number of metadata records stored: " + i + "    \r");
        System.out.println("");
        System.out.println("Done!");
        System.out.println("Time in seconds: "+ ((int)(end - start) / 1000 )  );
    }
  
    public void usage() {
         String prog = System.getProperty("program", "java " + HADBLoader.class.getName());
         System.err.println("");
         System.err.println("Usage: ");
         System.err.println("  " + prog + " -attrs <number_of_attrs> -res <number_of_md_recs> [options] ");
         System.err.println("");
         System.err.println("Options are:");
         System.err.println("  -attrs                   number of attributes included in each record. Defaults to 10");
         System.err.println("  -rec                     number of records to be inserted. Defaults to 1000");
         System.err.println("  -db                      name of host on which database is running.Defaults to hcb101");
         System.err.println("  -dpport                  port on which the database is listening. Defaults to 15005");
         System.err.println("");    
         System.err.println("The followning jars will need to be on Classpath to run this tool");   
         System.err.println("honeycomb-hctest.jar");
         System.err.println("honeycomb-server.jar");
         System.err.println("jug.jar");
         System.err.println("jetty-4.2.20.jar");
         System.err.println("hadbjdbc4.jar");
         System.err.println("");    
    }
    
    private void init () throws ClassNotFoundException, SQLException {
	Class.forName("com.sun.hadb.jdbc.Driver");        
	String SqlUrl = "jdbc:sun:hadb:system+superduper@" + m_databaseHost +":" + m_databasePort;
        m_SqlConn = DriverManager.getConnection(SqlUrl);
        
        for (int i = 0; i < STRING_ATTRS.length; i++) {
            m_AvailAttrs.add(new HADBLoader.NameValuePair(STRING_ATTRS[i],STRING_TYPE));
        }
        
        for (int j = 0; j < LONG_ATTRS.length; j++) {
            m_AvailAttrs.add(new HADBLoader.NameValuePair(LONG_ATTRS[j],LONG_TYPE));
        }
        
        for (int k = 0; k < DOUBLE_ATTRS.length; k++) {
            m_AvailAttrs.add(new HADBLoader.NameValuePair(DOUBLE_ATTRS[k],DOUBLE_TYPE));
        }
        
        m_stringPs = m_SqlConn.prepareStatement("insert into stringattribute values (?,?,?)");
        m_longPs = m_SqlConn.prepareStatement("insert into longattribute values (?,?,?)");
        m_doublePs = m_SqlConn.prepareStatement("insert into doubleattribute values (?,?,?)");
    }
    
    
    private void parseArgs(String [] args) throws InvalidArgException {
        String currentArg = null;
        for (int i = 0; i < args.length; i++ ) {
            currentArg=args[i];
            if (currentArg.equals(CLI_TOKEN_NUM_ATTRS)) {
                if (i < args.length) {
                    m_numberOfAttrs=Integer.parseInt(args[++i]);
                } else {
                    throw new InvalidArgException(
                            "You must specify the number of attributes with the "+ 
                            currentArg + "option.");
                }                
            } else if (currentArg.equals(CLI_TOKEN_NUM_RECS)) {
                if (i < args.length) {
                    m_numberOfRecs=Long.parseLong(args[++i]);
                } else {
                    throw new InvalidArgException(
                            "You must specify the number of records with the "+ 
                            currentArg + "option.");
                } 
            } else if (currentArg.equals(CLI_TOKEN_DB_HOST)) {
                if (i < args.length) {
                    m_databaseHost=args[++i];
                } else {
                    throw new InvalidArgException(
                            "You must specify the address of the hadb database with the "+ 
                            currentArg + "option.");
                }  
            } else if (currentArg.equals(CLI_TOKEN_DB_PORT)) {
                if (i < args.length) {
                    m_databasePort=args[++i];
                } else {
                    throw new InvalidArgException(
                            "You must specify the port for the hadb database with the "+ 
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
    
    private void printMetaData(String oid, ArrayList md) {
        System.out.println("OID: " + oid);
        Iterator iter = md.iterator();
        while (iter.hasNext()) {
            HADBLoader.MetaDataAttribute curMd = (HADBLoader.MetaDataAttribute)iter.next();
            System.out.println(curMd.name + " = " + curMd.value );
        }
        System.out.println("\n");
        
    }
    
    

    /**
     * Generates a radom set of Name/Value pairs to be inserted 
     * into HADB
     */
    private ArrayList generateMetaData(int setSize) {
        int key = -1;
        m_AttrsSet.clear();
        
        // reinit selected array
        for (int i = 0; i < m_AvailAttrs.size(); i++) {
            m_selectedElements[i]=0;
        }
        
        //select elements
        for (int j=0; j < setSize; j++) {
            do {
                key = getRandomInt(m_AvailAttrs.size());
            } while (m_selectedElements[key]!=0);
            m_selectedElements[key]=1;
            m_AttrsSet.add(new HADBLoader.MetaDataAttribute((HADBLoader.NameValuePair)m_AvailAttrs.get(key)));
        }
        
        return m_AttrsSet;
    }
    
    
    private String generateOID() {
        int layout = getRandomInt(1000);
        int chunkNumber = 1;
        byte type = (byte)0x01;
        return (new NewObjectIdentifier(layout,type,chunkNumber, null)).toString();
    }
    
    
    private String generateAttributeValue(HADBLoader.NameValuePair attribute) {
        if (attribute.value.equals(STRING_TYPE)) {
            return STRING_VALUES[getRandomInt(STRING_VALUES.length)];
        } else if (attribute.value.equals(DOUBLE_TYPE)) {
            return DOUBLE_VALUES[getRandomInt(DOUBLE_VALUES.length)];
        } else if (attribute.value.equals(LONG_TYPE)) {
            return LONG_VALUES[getRandomInt(LONG_VALUES.length)];            
        } else {
            return null; // TODO I should throw an exception
        }
        
    }
    
    private int getRandomInt (int ceiling) {
        return (int)Math.round(java.lang.Math.random() * (ceiling - 1));   
    }

   private void storeMDPrepared (String oid, ArrayList md, Connection conn) throws SQLException {
        Iterator iter = md.iterator();
        while (iter.hasNext()) {
            HADBLoader.MetaDataAttribute currentAttr = (HADBLoader.MetaDataAttribute) iter.next();
            if (currentAttr.type.equals(STRING_TYPE)) {
                m_stringPs.setString(1,currentAttr.name);
                m_stringPs.setString(2,currentAttr.value);                
                m_stringPs.setString(3,oid);       
                m_stringPs.execute();
            } else if (currentAttr.type.equals(DOUBLE_TYPE)) {
                m_doublePs.setString(1,currentAttr.name);
                m_doublePs.setDouble(2,Double.parseDouble(currentAttr.value));                
                m_doublePs.setString(3,oid);
                m_doublePs.execute();
            } else if (currentAttr.type.equals(LONG_TYPE)) {
                m_longPs.setString(1,currentAttr.name);
                m_longPs.setLong(2,Long.parseLong(currentAttr.value));                
                m_longPs.setString(3,oid);
                m_longPs.execute();
            } else {
            }
        }
   }
    
    private void storeMD(String oid, ArrayList md, Connection conn) throws SQLException {
        Statement statement = null;
        StringBuffer stringInsertStatement = null;
        StringBuffer doubleInsertStatement = null;
        StringBuffer longInsertStatement = null;
        Iterator iter = md.iterator();
        while (iter.hasNext()) {
            HADBLoader.MetaDataAttribute currentAttr = (HADBLoader.MetaDataAttribute) iter.next();
            if (currentAttr.type.equals(STRING_TYPE)) {
                if (stringInsertStatement == null) {
                    stringInsertStatement=new StringBuffer();
                    stringInsertStatement.append("('" + currentAttr.name + "','" + currentAttr.value 
                                                                         + "','" + oid + "')");
                } else {
                    stringInsertStatement.append(",('" + currentAttr.name + "','" + currentAttr.value 
                                                                         + "','" + oid + "')");
                }
            } else if (currentAttr.type.equals(DOUBLE_TYPE)) {
                if (doubleInsertStatement == null) {
                    doubleInsertStatement=new StringBuffer();
                    doubleInsertStatement.append("('" + currentAttr.name + "'," + currentAttr.value 
                                                                         + ",'" + oid + "')");
                } else {
                    doubleInsertStatement.append(",('" + currentAttr.name + "'," + currentAttr.value 
                                                                         + ",'" + oid + "')");
                }
            } else if( currentAttr.type.equals(LONG_TYPE)) {
                if (longInsertStatement == null) {
                    longInsertStatement=new StringBuffer();
                    longInsertStatement.append("('" + currentAttr.name + "'," + currentAttr.value 
                                                                         + ",'" + oid + "')");
                } else {
                    longInsertStatement.append(",('" + currentAttr.name + "'," + currentAttr.value 
                                                                         + ",'" + oid + "')");                    
                }
            } else {
                // do something meaningful here
            }
        }
        
        if (stringInsertStatement != null) {
            statement = conn.createStatement();
            //System.out.println("insert into stringattribute values " + stringInsertStatement.toString() + ";");
            statement.execute("insert into stringattribute values " + stringInsertStatement.toString() + ";");

        }
        if (longInsertStatement != null) {
            statement = conn.createStatement();
            //System.out.println("insert into longattribute values " + longInsertStatement.toString() + ";");
            statement.execute("insert into longattribute values " + longInsertStatement.toString() + ";");
        }
        if (doubleInsertStatement != null) {
            statement = conn.createStatement();
            //System.out.println("insert into doubleattribute values " + doubleInsertStatement.toString() + ";");
            statement.execute("insert into doubleattribute values " + doubleInsertStatement.toString() + ";");
        }
    }
    
    
    class MetaDataAttribute {
        MetaDataAttribute (HADBLoader.NameValuePair nvp) {
            type = nvp.value;
            name = nvp.name;
            value = generateAttributeValue(nvp);
        }
        String type;
        String name;
        String value;
    }
    
    class NameValuePair {
        NameValuePair (String n, String v) {
            name=n;
            value=v;
        }
        String name;
        String value;
    }
    
    class InvalidArgException extends Exception {
        InvalidArgException (String msg) {
            super(msg);
        }
    }
    
    public static final String STRING_TYPE="String";
    public static final String DOUBLE_TYPE="Double";
    public static final String LONG_TYPE="Long";
    public static final String [] STRING_VALUES = { "", "YES", "No", "MaybeSo","AVALUE"};
    public static final String [] LONG_VALUES = {"-7","0","42",Long.toString(Long.MIN_VALUE), Long.toString(Long.MAX_VALUE)};
    public static final String [] DOUBLE_VALUES = {"3.14","0","-16.03","98.6","71"};
    public static final String [] STRING_ATTRS = { "test_id", "stringnull","system_filepath","initchar","sha1",
                                                   "word","archive","stringlarge","first","sixth"};
    public static final String [] LONG_ATTRS = {"date","longsmall","longnull","filecurrsize","longlarge"};
    public static final String [] DOUBLE_ATTRS = {"doublechanged","doublelarge","doubleneagative","doublechunked","doublesmall"};
    private static final String CLI_TOKEN_NUM_ATTRS = "-attrs";
    private static final String CLI_TOKEN_NUM_RECS = "-rec";
    private static final String CLI_TOKEN_DB_HOST = "-db";
    private static final String CLI_TOKEN_DB_PORT = "-dbport";
    private static final String CLI_TOKEN_HELP = "-help";
    private final int m_numberOfElements = 20;
    private int [] m_selectedElements = new int [m_numberOfElements];
    private ArrayList m_AttrsSet = new ArrayList();
    private ArrayList m_AvailAttrs = new ArrayList();
    private Connection m_SqlConn;
    private long m_numberOfRecs = 1000;
    private int m_numberOfAttrs = 10;
    private long m_insertTime = 0;
    private long m_numRecsInserted = 0;
    private String m_databaseHost = "hcb101";
    private String m_databasePort = "15005";
    private PreparedStatement m_stringPs = null;
    private PreparedStatement m_doublePs = null;
    private PreparedStatement m_longPs = null;
}
