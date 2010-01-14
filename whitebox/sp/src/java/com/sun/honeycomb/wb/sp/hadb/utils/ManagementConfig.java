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


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import com.sun.honeycomb.test.SolarisNode;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.RunCommand;

/**
 * An internal representation of the Management Config
 * file used to configure the HADB Management Agent.
 * @author elambert
 *
 */

public class ManagementConfig {
    
    public static final String INTERFACE_PROPERTY="ma.server.mainternal.interfaces";
    public static final String DBCONFIG_PROPERTY="ma.server.dbconfigpath";
    public static final String REP_PATH_PROPERTY="repository.dr.path";
    public static final String LOGFILE_LEVEL_PROPERTY="logfile.loglevel";
    public static final String LOGFILE_NAME_PROPERTY="logfile.name";
    public static final String DBDEVICE_PATH_PROPERTY="ma.server.dbdevicepath";
    public static final String DBHISTORY_PATH_PROPERTY="ma.server.dbhistorypath";
    public static final String LOGLEVEL_ALL="ALL";
    public static final String LOGLEVEL_SEVERE="SEVERE";
    public static final String LOGLEVEL_WARNING="WARNING";
    public static final String LOGLEVEL_INFO="INFO";
    public static final String LOGLEVEL_FINE="FINE";
    public static final String LOGLEVEL_FINER="FINER";
    public static final String LOGLEVEL_FINEST="FINEST";
    public static final String LOGLEVEL_OFF="OFF";
    public static final String LOGLEVEL_DEFAULT=LOGLEVEL_INFO;
    public static final String MGT_CFG_FILE="/config/SUNWhadb/mgt.cfg";
    
    
    private Properties m_props = new Properties();
    private String m_nodeName;
    private RunCommand m_rc = new RunCommand();
    
    
    /**
     * Create a new Management Configuration instance.
     * 
     * @param node The host name of the node upon which
     * The Management will run.
     */
    public ManagementConfig (String node) {
	m_nodeName=node;
	m_props = new Properties();
    }
    
    
    /**
     * Retrieve Management Configuration File from the node
     * and loads it's values.
     * 
     * @throws IOException if the node is not reachable or
     * when problems are encountered reading the file.
     */
    public void load() throws IOException {
	
	SolarisNode sn = new SolarisNode(m_nodeName,"ssh");
	if (!sn.ping()) {
	    throw new IOException("Node " + m_nodeName + " is not available." + 
		    "Does not respond to pings");
	}
	File temp=File.createTempFile("mgt", "cfg"); 
	transferFile(m_nodeName+":"+MGT_CFG_FILE, temp.getAbsolutePath());
	m_props = new Properties();
	FileInputStream fis = new FileInputStream(temp);
	m_props.load(fis);
	fis.close();
	temp.delete();
    }
    
    
    /**
     * 
     * @return A property object containing the management
     * configuration values for this node.
     */
    public Properties getProperties () {
	if (m_props == null) {
	    return null;
	}
	return (Properties) m_props.clone();
    }
    
    
    /**
     * Writes a new managment configuration file as represented by this
     * object onto the node. 
     * 
     * @throws IOException if the node can not be reached or if
     * problems are encountered writing the file.
     */
    public void write () throws IOException {
	SolarisNode sn = new SolarisNode(m_nodeName,"ssh");
	if (!sn.ping()) {
	    throw new IOException("Node " + m_nodeName + " is not available." + 
		    "Does not respond to pings");
	}
	File temp=File.createTempFile("mgt", "cfg");
	FileOutputStream fos = new FileOutputStream(temp);
	m_props.store(fos, "");
	fos
	.close();
	transferFile(temp.getAbsolutePath(),m_nodeName+":"+MGT_CFG_FILE);
	temp.delete();
	
    }
    
    
    /**
     * 
     * @return the network interface to be used by
     * the Management Agent.
     */
    public String getMAInterface () {
	return getPropValue(INTERFACE_PROPERTY);
    }
    
    
    /**
     * 
     * @return the path to the Database Configuration
     * directory.
     */
    public String getDatabaseConfigPath () {
	return getPropValue(DBCONFIG_PROPERTY);
    }
    
    
    /**
     * 
     * @return the path to the Database Repository
     * directory.
     */
    public String getRepositoryPath () {
	return getPropValue(REP_PATH_PROPERTY);
    }
    
    
    /**
     * 
     * @return the logging level to be used by 
     * the Management Agent. The legal values are
     * ALL
     * SEVERE
     * WARNING
     * INFO
     * FINE
     * FINER
     * FINEST
     * DEFAULT
     * OFF
     */
    public String getLogLevel () {
	return getPropValue(LOGFILE_LEVEL_PROPERTY);
    }
    
    
    /**
     * 
     * @return the path to the Management Agent log file
     */
    public String getLogFilePath () {
	return getPropValue(LOGFILE_NAME_PROPERTY);
    }
    
    
    /**
     * 
     * @return the path to the Database device directory
     */
    public String getDatabaseDevicePath () {
	return getPropValue(DBDEVICE_PATH_PROPERTY);
    }
    
    
    /**
     * 
     * @return the path to the Database history file directory
     */
    public String getDatabaseHistoryPath () {
	return getPropValue(DBHISTORY_PATH_PROPERTY);
    }
    
    
    /**
     * Set the address of the network interface to be used
     * by the Management Agent on this node.
     * 
     * @param value name of interface to be used
     */
    public void setMAInterface (String value) {
	m_props.setProperty(INTERFACE_PROPERTY, value);
    }
    
    
    /**
     * Set the path to the Database Configuration 
     * directory to be used by the Management Agent
     * on this node.
     * 
     * @param value Path to Database Configuration 
     * directory.
     */
    public void setDatabaseConfigPath (String value) {
	m_props.setProperty(DBCONFIG_PROPERTY,value);
    }
    
    /**
     * Set Path to Database Repository directory.
     * @param value
     */
    public void setRepositoryPath (String value) {
	m_props.setProperty(REP_PATH_PROPERTY,value);
    }
    
    /**
     * Set Logging Level of Management Agent for this 
     * node. Legal value are :
     * ALL
     * SEVERE
     * WARNING
     * INFO
     * FINE
     * FINER
     * FINEST
     * DEFAULT
     * OFF
     * 
     * @param value
     */
    public void setLogLevel (String value) {
	m_props.setProperty(LOGFILE_LEVEL_PROPERTY,value);
    }
    
    
    /**
     * Set path to log file used by Managment Agent 
     * on this node.
     * @param value
     */
    public void setLogFilePath (String value) {
	m_props.setProperty(LOGFILE_NAME_PROPERTY,value);
    }
    
    
    /**
     * Set path to Database device directory used by 
     * Management Agent on this node.
     * @param value
     */
    public void setDatabaseDevicePath (String value) {
	m_props.setProperty(DBDEVICE_PATH_PROPERTY,value);
    }
    
    
    /**
     * Set path to Database history file directory used by 
     * Management Agent on this node.
     * @param value
     */
    public void setDatabaseHistoryPath (String value) {
	m_props.setProperty(DBHISTORY_PATH_PROPERTY,value);
    }
     
    
    public static void main (String [] args) {
	ManagementConfig me = new ManagementConfig("hcb101");
	Properties p = null;
	
	String testInterface="10.123.45.101/26";
	String testdbConfig="/data/0/hadb/dbdef";
	String testRep="/data/0/hadb/repository";
	String testLogLevel="DEFAULT";
	String testLogFile="/data/0/hadb/log/ma.log";
	String testDevicePath="/data/0/hadb";
	String testHistory="/data/0/hadb/history";

	
	//make sure that default props are empty and not null
	System.out.println("==Testing==");
	p=me.getProperties();
	if (p == null ) {
	    System.out.println("ERROR! Default properties is null");
	    System.exit(1);
	}
	if (!p.isEmpty()) {
	    System.out.println("ERROR! Default properties is not empty");
	    System.exit(1);
	}
	
	//set some properties and make sure they are set
	me.setDatabaseConfigPath(testdbConfig);
	if (!me.getDatabaseConfigPath().equals(testdbConfig)) {
	    System.out.println("ERROR! retreived value for dbconfig does not equal what we set it to");
	    System.exit(1);
	} 
	
	me.setMAInterface(testInterface);
	if (!me.getMAInterface().equals(testInterface)) {
	    System.out.println("ERROR! retreived value for interface does not equal what we set it to");
	    System.exit(1);
	}
	
	me.setRepositoryPath(testRep);
	if (!me.getRepositoryPath().equals(testRep)) {
	    System.out.println("ERROR! retreived value for repsitory does not equal what we set it to");
	    System.exit(1);
	} 
	
	me.setLogLevel(testLogLevel);
	if (!me.getLogLevel().equals(testLogLevel)) {
	    System.out.println("ERROR! retreived value for log level does not equal what we set it to");
	    System.exit(1);
	} 
	
	me.setLogFilePath(testLogFile);
	if (!me.getLogFilePath().equals(testLogFile)) {
	    System.out.println("ERROR! retreived value for log file does not equal what we set it to");
	    System.exit(1);
	} 
	
	me.setDatabaseDevicePath(testDevicePath);
	if (!me.getDatabaseDevicePath().equals(testDevicePath)) {
	    System.out.println("ERROR! retreived value for device path does not equal what we set it to");
	    System.exit(1);
	} 
	
	me.setDatabaseHistoryPath(testHistory);
	if (!me.getDatabaseHistoryPath().equals(testHistory)) {
	    System.out.println("ERROR! retreived value for history does not equal what we set it to");
	    System.exit(1);
	} 

	System.out.println("   + Set/get tests: PASSED");
	
	Properties oldp = me.getProperties();
	try {
	    me.write();
	} catch (IOException ioe) {
	    System.err.println("Caught an IOException while writing props to node " + me.m_nodeName );
	    ioe.printStackTrace(System.err);
	    System.exit(1);
	}
	System.out.println("   + write tests:   PASSED");
	
	try {
	    me.load();
	}catch (IOException ioe) {
	    System.err.println("Caught an IOException while loading props from node " + me.m_nodeName );
	    ioe.printStackTrace(System.err);
	    System.exit(1);
	}
	System.out.println("   + load tests:    PASSED");
	
	if (!me.getProperties().equals(oldp)) {
	    System.err.println("The property file retrieved from the node does not match the one I generated");
	    System.err.println("===generated props===");
	    oldp.list(System.err);
	    System.err.println("===retreived props===");
	    me.getProperties().list(System.err);
	    System.exit(1);
	}
	System.out.println("   + compare tests: PASSED");
	System.out.println("ALL TESTS PASSED!");
	System.exit(0);
	
    }
    
    
    private void transferFile (String src, String dest) throws IOException{
	try { 
	    m_rc.scpCmd(src, dest);
	} catch (HoneycombTestException hte) {
	    IOException ioe = new IOException("Unable to copy file from node "+m_nodeName);
	    try {
		ioe.initCause(hte);
	    } catch (Throwable ignored) {}
	    throw ioe;
	}
    }
    
    
    private String getPropValue(String prop) {
	if (m_props == null) {
	    return null;
	}
	return m_props.getProperty(prop);
    }
    
}
