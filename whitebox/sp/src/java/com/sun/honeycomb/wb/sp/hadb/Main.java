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



package com.sun.honeycomb.wb.sp.hadb;

import java.io.IOException;

import com.sun.hadb.adminapi.HADBException;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.wb.sp.hadb.utils.Util;

public class Main {

    public static final String HELP_OPT="--help";
    public static final String HELP_OPT_SHORT="-h";
    public static final String GET_DOMAIN_STAT_OPT="--domainstat";
    public static final String GET_DOMAIN_STAT_OPT_SHORT="-ds";
    public static final String GET_DB_STAT_OPT="--databasestat";
    public static final String GET_DB_STAT_OPT_SHORT="-dbs";
    public static final String COPY_MALOG_OPT="--copyma";
    public static final String COPY_MALOG_OPT_SHORT="-cm";
    public static final String COPY_DBHISTORY_OPT="--copyhistory";
    public static final String COPY_DBHISTORY_OPT_SHORT="-ch";
    public static final String GET_DBHISTORY_OPT="--gethistory";
    public static final String GET_DBHISTORY_OPT_SHORT="-gh";
    public static final String WIPE_HADB="--wipe";
    public static final String WIPE_HADB_SHORT="-W";
    public static final String SANITY_CHECK="--sanity";
    public static final String DEVICE_SIZE="--devsize";
    public static final String ALG_SIZE="--algsize";
    public static final String CONN_TEST="--con";
    
    public static void main (String [] args) {
	try {
	    Main me  = new Main();
	    me.processArgs(args);
	} catch (InvalidArgs ia) {
	    System.err.println("Invalid Command line option");
	    System.err.println(ia.getMessage());
	    System.exit(1);
	}catch (Throwable t) {
	    System.err.println("Unexpected exception:");
	    System.err.println(t.getMessage());
	    t.printStackTrace(System.err);
	    System.exit(1);	    
	}
	System.exit(0);
    }
    
    private static void usage() {
	System.err.println("Usage not yet implemented");
    }
    
    
    private void processArgs(String [] args) 
    throws InvalidArgs, HADBException, IOException, HoneycombTestException {
	for (int i = 0; i < args.length; i++) {
	    String curOption = args[i];
	    if (curOption.equalsIgnoreCase(HELP_OPT) || 
		curOption.equalsIgnoreCase(HELP_OPT_SHORT) ) {
		usage();
		System.exit(0);
	    } else if (curOption.equalsIgnoreCase(GET_DOMAIN_STAT_OPT) || 
		       curOption.equalsIgnoreCase(GET_DOMAIN_STAT_OPT_SHORT) ) {
		Util.printDomainStatus(System.out);
	    } else if (curOption.equalsIgnoreCase(GET_DB_STAT_OPT) || 
		       curOption.equalsIgnoreCase(GET_DB_STAT_OPT_SHORT) ) {
		Util.printDatabaseStatus(System.out);
	    } else if (curOption.equalsIgnoreCase(COPY_MALOG_OPT) || 
		       curOption.equalsIgnoreCase(COPY_MALOG_OPT_SHORT) ) {
		if (++i == args.length) {
		    throw new InvalidArgs("You must specify an output " +
		    		          "directory when you using the option " 
		    		           + curOption);
		}
		Util.copyMALogFiles(16, args[i]);
	    } else if (curOption.equalsIgnoreCase(COPY_DBHISTORY_OPT) || 
		    curOption.equalsIgnoreCase(COPY_DBHISTORY_OPT_SHORT) ) {
		if (++i == args.length) {
		    throw new InvalidArgs("You must specify an output " +
		    		          "directory when you using the option " 
			                  + curOption);
		}
		Util.copyHistoryFiles(16, args[i]);
	    }else if (curOption.equalsIgnoreCase(GET_DBHISTORY_OPT) || 
		      curOption.equalsIgnoreCase(GET_DBHISTORY_OPT_SHORT) ) {
		if (++i == args.length) {
		    throw new InvalidArgs("You must specify an output " +
		    		          "directory when you using the option "
			                  + curOption);
		}
		Util.getHADBHistory("10.123.45.200:"+Util.HADB_DEFAULT_PORT, 
			            args[i]);
	    } else if (curOption.equalsIgnoreCase(WIPE_HADB) || 
		      curOption.equalsIgnoreCase(WIPE_HADB_SHORT) ) {
		Util.wipeCluster(16);
	    } else if (curOption.equalsIgnoreCase(SANITY_CHECK)) {
		
		if (++i  == args.length) {
		    throw new InvalidArgs("You must specify the cluster size " +
	    		          " when you using the option "
		                  + curOption);
		}
		int clusterSize = Integer.parseInt(args[i]);
		
		if (++i  == args.length) {
		    throw new InvalidArgs("You must specify the number of  " +
	    		          "downed nodes when you using the option "
		                  + curOption);
		}
		int downedNodes = Integer.parseInt(args[i]);
		
		if (++i  == args.length) {
		    throw new InvalidArgs("You must specify the number of  " +
	    		          "missing nodes when you using the option "
		                  + curOption);
		}
		int missingNodes = Integer.parseInt(args[i]);
		
		
		if (Util.isDBSane(clusterSize, downedNodes, missingNodes)) {
		    System.out.println("Database is sane");
		} else {
		    System.out.println("Database is not sane");
		}
	    } else if (curOption.equalsIgnoreCase(DEVICE_SIZE)) {
		if (++i  == args.length) {
		    throw new InvalidArgs("You must specify a host  " +
	    		          "when you using the option "
		                  + curOption);
		}
		String host = args[i];
		System.out.println("DB Device Size on " + host + " is " +
			Util.getHADBFileSize(host, Util.DATABASE_FILE_TYPE));
		
	    } else if (curOption.equalsIgnoreCase(ALG_SIZE)) {
		if (++i  == args.length) {
		    throw new InvalidArgs("You must specify a host  " +
	    		          "when you using the option "
		                  + curOption);
		}
		String host = args[i];
		System.out.println("RelAlg Device Size on " + host + " is " +
			Util.getHADBFileSize(host, Util.RELALG_FILE_TYPE));
		
	    } else if (curOption.equalsIgnoreCase(CONN_TEST)) {
		Util.testConnection();
	    } else {
		throw new InvalidArgs("Unkown option " + curOption);
	    }
	}
    }
    
    
    class InvalidArgs extends Exception {
	InvalidArgs (String desc) {
	    super(desc);
	}
    }
    
}
