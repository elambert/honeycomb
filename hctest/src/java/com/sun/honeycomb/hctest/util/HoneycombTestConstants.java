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

import java.util.Properties;
import com.sun.honeycomb.test.util.TestConstants;
import com.sun.honeycomb.test.util.NameValue;

/** 
 *  Honeycomb-specific test properties
 *
 *  See also: hctest/cases/storepatterns/Settings.java
 */

public class HoneycombTestConstants extends TestConstants {

    ////////////////////////////////////////////////////////
    // Test Properties controlled from command line

    // honeycomb test harness
    public static final String PROPERTY_CLUSTER = "cluster";
    public static final String PROPERTY_DATA_VIP = "dataVIP";
    public static final String PROPERTY_DATA_PORT = "dataport";
    public static final String PROPERTY_ADMIN_VIP = "adminVIP";
    public static final String PROPERTY_SP_IP = "spIP";
    public static final String PROPERTY_ALTERNATE_IP = "alternateIP";
    public static final String PROPERTY_AUDIT_IP = "auditIP";
    public static final String PROPERTY_DATA_VIP_ADDR = "dataVIPaddr";
    public static final String PROPERTY_ADMIN_VIP_ADDR = "adminVIPaddr";
    public static final String PROPERTY_SP_IP_ADDR = "spIPaddr";
    public static final String PROPERTY_AUDIT_IP_ADDR = "auditIPaddr";
    public static final String PROPERTY_AUDIT_DB = "auditDB";
    public static final String PROPERTY_AUDIT_DISABLE = "auditdisable";
    public static final String PROPERTY_CLIENTS = "clients";
    public final static String PROPERTY_TESTBED = "testbed";
    public final static String PROPERTY_RMI = "rmi";
    public final static String PROPERTY_STATS = "stats";
    public final static String PROPERTY_NOLOG = "nolog";
    public final static String PROPERTY_NOHASH = "nohash";
    public final static String PROPERTY_RUN = "run";
    public static final String PROPERTY_WDLOG = "wdlog";
    
    // basic test properties
    public static final String PROPERTY_FILESIZE = "filesize";
    public static final String PROPERTY_PLATFORM = "platform";
    public static final String PROPERTY_SEED = "seed";
    public static final String PROPERTY_TIMEOUT = "timeout";
    public static final String PROPERTY_RUN_DURATION = "duration";
    public static final String PROPERTY_INTERVAL = "interval";
    public static final String PROPERTY_ROOT = "root";
    public static final String PROPERTY_ITERATIONS = "iterations";
    public static final String PROPERTY_CLIENTS_PER_HOST = "clientsperhost";
    public static final String PROPERTY_ROTATE_CLIENTS = "rotateclients";
    public static final String PROPERTY_DATA_MODE = "datamode";
    public static final String PROPERTY_NODES = "nodes";
    public static final String PROPERTY_NFILES = "nfiles";
    public static final String PROPERTY_CONNECT_TIMEOUT = "connecttimeout";
    public static final String PROPERTY_SOCKET_TIMEOUT = "sockettimeout";

    // snapshot values
    public static final String PROPERTY_SNAPSHOT = "snapshot";
    public static final String PROPERTY_DELETEDATA = "deletedata";
    public static final String PROPERTY_CREATEDATA = "createdata";
    public static final String PROPERTY_SETUPCLUSTER = "setupcluster";
    
    public static final String PROPERTY_FAULTTYPE = "faulttype";
    public static final String PROPERTY_FAULTMODE = "faultmode";
    
    // datamode values
    public static final String PROPERTY_ROUND_ROBIN_DATA_MODE = "roundrobinnodes";
    public static final String PROPERTY_RANDOM_DATA_MODE = "randomnodes";

    // properties used at runtime for tests
    public static final String PROPERTY_STORED_OID1 = "storedOID1";
    public static final String PROPERTY_QUERY_OID1 = "queryOID1";
    public static final String PROPERTY_FILENAME_OID1 = "filenameOID1";
    public static final String PROPERTY_OID = "oid";
    public static final String PROPERTY_HASH = "hash";

    // different cluster runtime and failure modes
    public static final String PROPERTY_CMM_ONLY = "cmmonly";
    public static final String PROPERTY_CMM_ONLY_WITH_SNIFFER = "cmmonlywithsniffer"; // cmm-only on single node
    public static final String PROPERTY_CMM_SINGLE = "cmmsingle"; // cmm-only on single node
    public static final String PROPERTY_NODE_MGR = "nodemgr"; // runs CMM + NodeMgr
    public static final String PROPERTY_NO_CLUSTER = "nocluster"; 
    public static final String PROPERTY_FAIL_MODE = "fail"; 
    public static final String PROPERTY_CMM_FLAKY_CONNECTION_INTERVAL = "flakyinterval";
	public static final String PROPERTY_CMM_REST_INTERVAL = "restinterval";
	public static final String PROPERTY_CMM_JVM_NAME = "jvmname";
	public static final String PROPERTY_CMM_RUN_LEVEL = "runlevel";
	public static final String PROPERTY_CMM_FAULT_TYPE = "faulttype";
	public static final String PROPERTY_CMM_PAUSE = "pause";
	public static final String PROPERTY_CMM_REPEAT = "repeat";
	public static final String PROPERTY_CMM_QUORUM = "quorum";

    // misc properties used by specific tests
    public static final String PROPERTY_HOW_MANY_OBJS = "objects";
    public static final String PROPERTY_TEST_MODE = "testmode";
    public static final String PROPERTY_STRICT = "strict";
    public static final String PROPERTY_HOW_MANY_FRAGS = "frags";
    public static final String PROPERTY_PICK_FRAGS = "pickfrags";
    public static final String PROPERTY_FSSETTLE = "fssettle";
    public static final String PROPERTY_SLEEP_TIME = "sleeptime";
    public static final String PROPERTY_WHICH_FRAGS = "whichfrags";
    public static final String PROPERTY_NO_QUERY = "noquery";
    public static final String PROPERTY_NO_RETRIEVE = "noretrieve";
    public static final String PROPERTY_NO_DELETE = "nodelete";
    public static final String PROPERTY_NO_RETRIEVEMD = "noretrievemd";
    public static final String PROPERTY_NO_SECONDDELETE = "noseconddelete";
    public static final String PROPERTY_NO_ADDMD = "noaddmd";
    public static final String PROPERTY_NO_RESTORE = "norestore";
    public static final String PROPERTY_SHORTRUN = "shortrun";
    public static final String PROPERTY_LONGRUN = "longrun";
    public static final String PROPERTY_DONTSKIP = "dontskip";
    public static final String PROPERTY_PERCENT_MD = "percentmd";
    public static final String PROPERTY_REFCHECK = "refcheck";
    public static final String PROPERTY_BOUNCE = "bounce";
    public static final String PROPERTY_OFFLINE_NODES = "offlinenodes";
    public static final String PROPERTY_OFFLINE_DISKS = "offlinedisks";
    public static final String PROPERTY_NO_FRAGMENT_VALIDATE = "noValidate";
    public static final String PROPERTY_VERBOSE = "verbose";
    public static final String PROPERTY_FULL_HEAL = "fullheal";
    public static final String PROPERTY_RECOVERYCOMMUTES = "recoverycommutes";
    public static final String PROPERTY_STARTINGFILESIZE = "startingfilesize";
    public static final String PROPERTY_ENDINGFILESIZE = "endingfilesize";
    public static final String PROPERTY_ALLFILESIZES = "allfilesizes";
    public static final String PROPERTY_REUSE_FILES = "reusefiles";
    public static final String PROPERTY_SKIPPEDRESULTS = "skippedresults";
    public static final String PROPERTY_SKIPPEDAUDITTEST = "skippedaudittest";
    public static final String PROPERTY_NO_HADB = "nohadb";
    
    // blockstore properties
    public static final String PROPERTY_RECYCLE_OIDS = "recycleoids";
    public static final String PROPERTY_THREADED_AUDIT = "threadedaudit";
    public static final String PROPERTY_QUERY_TYPE = "querytype";
    public static final String PROPERTY_NO_LOCKING = "nolocking";
    public static final String PROPERTY_MIN_MD_FIELDS = "minmdfields";
    public static final String PROPERTY_MAX_MD_FIELDS = "maxmdfields";
    public static final String PROPERTY_MD_TYPES = "mdtypes";
    public static final String PROPERTY_LOGSCRAPER_ON = "logscraper";
    public static final String PROPERTY_LOGSCRAPER_RE = "regexp";
    public static final String PROPERTY_EXTRA_ARGUMENTS = "extraArgs";
    
    // requires three ips (admin, data, SP) to run cellcfg test in cli suite
    public static final String PROPERTY_CLI_CELLCFG_IPS = "cellcfgips";   
    
    // multicell properties
    public static final String PROPERTY_MULTICELl_NEW_CELLS = "newcells";   
    public static final String PROPERTY_MULTICELl_LOOP_NO = "loop";   
    
    // Constants
    public static final int TAG_SIZE = 4;

    // poor man's enum for failure modes
    public static final int FAIL_HC = 0;
    public static final int PKILL_JAVA = 1;
    public static final int FAIL_NETWORK = 2;

    //////////////////////////////////////////////
    // Help for Honeycomb Test Properties

    public HoneycombTestConstants() {
        // Yes, this constructor's only purpose in life is to 
        // set up help strings for test properties.
        super();

        NameValue[] propHelp = {
            new NameValue(PROPERTY_CLUSTER, "Name of your cluster (eg: " + PROPERTY_CLUSTER + 
                          "=dev101). Will automatically set properties dataVIP, adminVIP and"+
                          " spIP to default values: <cluster>-data, <cluster>-admin, "+
                          "<cluster>-cheat. If some of your cluster's IPs are not standard,"+
                          " you can override the defaults by setting properties explicitly."),
            
            new NameValue(PROPERTY_DATA_VIP, "Your cluster's external IP or hostname for "+
                          "data requests (eg: " + PROPERTY_DATA_VIP + "=dev101-data)."),
            
            new NameValue(PROPERTY_DATA_PORT, "Your cluster's data port, 8080 by default "+
                          "(eg: " + PROPERTY_DATA_PORT + "=8081)."),

            new NameValue(PROPERTY_ADMIN_VIP, "Your cluster's external IP or hostname for "+
                          "administrative operations (eg: " + PROPERTY_ADMIN_VIP + "=dev101-admin)."),
            
            new NameValue(PROPERTY_SP_IP, "Your cluster's service processor IP or hostname (eg: " + 
                          PROPERTY_SP_IP + "=dev101-sp). Used for collection of cluster logs, and "+
                          "by testware RMI if RMI is enabled."),
            
            new NameValue(PROPERTY_TESTBED, "Name of your testbed for database recording purposes, "+
                          "defaults to cluster name (eg: " + PROPERTY_TESTBED + "=dev101)."),
            
            new NameValue(PROPERTY_CONNECT_TIMEOUT, "This property is used in the API to set " +
                          "the connection timeout in msecs.  It can be set to 0 to specify " +
                          "no timeout.  Default is " + DEFAULT_CONNECT_TIMEOUT + ". (eg: " +
                          PROPERTY_CONNECT_TIMEOUT + "=" + DEFAULT_CONNECT_TIMEOUT + ")"),

            new NameValue(PROPERTY_SOCKET_TIMEOUT, "This property is used in the API to set " +
                          "the socket timeout in msecs.  It can be set to 0 to specify " +
                          "no timeout.  Default is " + DEFAULT_SOCKET_TIMEOUT + ". (eg: " +
                          PROPERTY_SOCKET_TIMEOUT + "=" + DEFAULT_SOCKET_TIMEOUT + ")"),

            new NameValue(PROPERTY_NO_CLUSTER, "Set this property if honeycomb is not running " +
                          "on the cluster at the beginning of the test (ie the test will start it)."),

            new NameValue(PROPERTY_CLIENTS, "Comma-separated list of client machine IPs or "+
                          "hostnames (eg: " + PROPERTY_CLIENTS + "=node1,node2,node3). The tests "+
                          "will run from these clients."),
            
            new NameValue(PROPERTY_NODES, "Number of nodes in the cluster. " +
                          "Honored by some tests. Defaults to 8."),

            new NameValue(PROPERTY_ITERATIONS, "Count of iterations. " +
                          "Honored by some tests. Defaults to 1, ie single pass."),

            new NameValue(PROPERTY_RMI, "Enable use of RMI mechanism in the testware (" + 
                          PROPERTY_RMI + "=on). Distributed client tests require RMI on."),
            
            new NameValue(PROPERTY_STATS, "Change frequency of collecting cluster statistics (" + 
                          PROPERTY_STATS + "=no|run|suite|test). Default is no: do not collect "+
                          "cluster statistics at all, since it slows down tests."),
            
            new NameValue(PROPERTY_NOLOG, "Disable archiving of cluster logs (" + PROPERTY_NOLOG + 
                          "=true). By default, cluster logs are collected and archived at the end"+
                          " of the test run. "),
            new NameValue(PROPERTY_NO_FRAGMENT_VALIDATE, "Disable harvest and database analysis"),
            
            new NameValue(PROPERTY_SKIPPEDAUDITTEST, "This property is used in CLI test " +
                          "to skip audit log varification. The cluster needs to have a " +
                          "mailbox on hclog301 in order to run audit log verification as" +
                          "CLI test uses hclog301.sfbay.sun.com as the mail server. " +
                          "Clusters in the SF test lab already have mailboxes on " +
                          "hclog301, named after the cluster. Set this property if " +
                          "the cluster is not in .")

            
        };

        propertyUsage = propHelp;
    }

    ///////////////////////////////////////////////////////////////////////
    // basic constants
    //
    public static final long MILLISECOND = 1;
    public static final long SECOND = MILLISECOND*1000;

    public static final int DEFAULT_DATA_PORT = 8080;

    // client timeout (overriding default infinite timeout in client library)
    public static final int DEFAULT_CONNECT_TIMEOUT = 300*1000; // 5-minute
    public static final int DEFAULT_SOCKET_TIMEOUT = 300*1000;  // 5-minute

    public static final String NFS_BASE_MOUNTPOINT = "/tmp/"; // end in '/'
    public static final long INVALID_SEED = -1;

    // Constants for the filesize
    public static final long INVALID_FILESIZE = -1;
    public static final long DEFAULT_FILESIZE_XSMALL = 1;
    public static final long DEFAULT_FILESIZE_SMALL = 500;
    public static final long DEFAULT_FILESIZE_MEDIUMSMALL = 40000; // should be > 5K
    public static final long DEFAULT_FILESIZE_MEDIUM = 3000000;
    public static final long DEFAULT_FILESIZE_LARGE = 200000000;
    public static final long DEFAULT_FILESIZE_XLARGE = 1000000000;
    public static final long DEFAULT_FILESIZE_XXLARGE = 10000000000L;
    public static final long ONE_MEGABYTE = 1 * 1024 * 1024;
    public static final long ONE_GIGABYTE = 1 * 1024 * 1024 * 1024;
    public static final long MAX_QUICK_FILESIZE = 10 * ONE_MEGABYTE;
    public static final long MAX_FILESIZE = 100 * ONE_GIGABYTE;

    public static final String CLI_DISK_DELIMITER = "-";
    public static final String LAYOUT_DISK_DELIMITER = ":";

    public static final String DATA_FROM_STREAM = "[data_from_stream]"; 

    public static int MAX_ALLOCATE = 1048576; // 1Mb limit for buffers

    // Query constants
    public static final int DEFAULT_MAX_RESULTS = 10000000;
    // the "conservative" use is for when clients my have small
    // amounts of memory but the result set might be large
    public static final int DEFAULT_MAX_RESULTS_CONSERVATIVE = 2000;
    public static final int USE_DEFAULT_MAX_RESULTS = -1;

    public static final int MAX_CLUSTER = 16; // max cluster size

    // OA related constants -- these are closely tied to values in OAClient.java
    public static final String CURRENT_HASH_ALG = "SHA";
    public static final int CURRENT_HASH_SIZE = 20;
    public static final int OA_DATA_FRAGS = 5;
    public static final int OA_PARITY_FRAGS = 2;
    public static final int OA_TOTAL_FRAGS = OA_DATA_FRAGS + OA_PARITY_FRAGS;
    public static final int INVALID_FRAG = -1;
    //
    // === nomenclature ===
    //
    // 'fragment size' of 64k is actually the size of each data write to
    // the frag file. Max frag file size is apparently 3200x64k + any
    // parity and header/footer data. 'block size' thus refers to one
    // stripe of 64k across the fragment files. 'chunks' are also referred
    // to as 'extents'.
    //
    public static final long OA_FRAGMENT_SIZE = 64 * 1024;  // 64k
    public static final long OA_BLOCK_SIZE = OA_FRAGMENT_SIZE * OA_DATA_FRAGS;
    public static final long OA_MAX_CHUNK_SIZE = OA_BLOCK_SIZE * 3200;
    public static final long OA_MAX_SIZE = OA_MAX_CHUNK_SIZE * 100;
    public static final long OA_DELETED_SIZE = 372;

    

    // misc internal-to-cluster
    public static final String BASE_IP = "10.123.45.";
    public static final String ADMIN_VIP = BASE_IP + "200";

    // MD related constants
    public static final String MD_OBJECT_ID = "system.object_id";
    public static final String MD_INVALID_OID = "null";
    public static final String MD_VALID_FIELD = "filename";
    public static final String MD_VALID_FIELD1 = "stringorigargs";
    public static final String MD_VALID_FIELD2 = "initchar";
    public static final String MD_ATTR_SIZE1 = "fileorigsize";
    public static final String MD_ATTR_SIZE2 = "filecurrsize";
    public static final String MD_DOUBLE_FIELD1 = "doublefixed";
    public static final String MD_DOUBLE_FIELD2 = "doublechunked";
    public static final String MD_LONG_FIELD1 = "wordlength";
    public static final String MD_LONG_FIELD2 = "iteration";

    public static final String MD_DATE_FIELD1 = "system.test.type_date";
    public static final String MD_DATE_FIELD2 = "";
    public static final String MD_TIME_FIELD1 = "system.test.type_time";
    public static final String MD_TIME_FIELD2 = "";
    public static final String MD_TIMESTAMP_FIELD1 = "system.test.type_timestamp";
    public static final String MD_TIMESTAMP_FIELD2 = "";
    public static final String MD_CHAR_FIELD1 = "system.test.type_char";
    public static final String MD_CHAR_FIELD2 = "";
    public static final String MD_CHAR_FIELD3 = "charweirdchars";
    public static final String MD_BINARY_FIELD1 = "system.test.type_binary";
    public static final String MD_BINARY_FIELD2 = "";
    public static final String MD_BINARY_FIELD3 = "binaryweirdchars";

    //Compare the following fields to metadata_config_utf8.xml
    public static final String MD_UNICODE_FIELD1 = 
        "utf8.\u043c\u043d\u0435 \u043d\u0435 \u0432\u0440\u0435\u0434\u0438\u0442";
    public static final String MD_UNICODE_FIELD2 = 
        "utf8.\u0986\u09ae\u09be\u09b0 \u0995\u09cb\u09a8\u09cb";
    public static final String MD_UNICODE_FIELD3 = 
        "utf8.\u305d\u308c\u306f\u79c1\u3092\u50b7\u3064\u3051\u307e\u305b\u3093";
    public static final String MD_UNICODE_FIELD4 = 
        "utf8.\u6211\u80fd\u541e\u4e0b\u73bb\u7483\u800c\u4e0d\u4f24\u8eab\u4f53";
    public static final String MD_UNICODE_FIELD5 = "stringweirdchars";

    public static final String NFS_VIEWPATH_SIZEOID = "sizeOid";
    public static final String NFS_VIEWPATH_RANDOMDOUBLEOID = "randomDoubleOid";
    
    public static final String PLATFORM_SOLARIS = "solaris";
    public static final String PLATFORM_LINUX = "linux";
    public static final String PLATFORM_DEFAULT = PLATFORM_LINUX;

    // audit constants
    public static final String AUDIT_STORE = "store";
    public static final String AUDIT_ADD_MD = "addmd";
    public static final String AUDIT_DELETE = "delete";
    public static final String AUDIT_RETRIEVE = "retrieve";

    // honeycomb limitations
    public static final double HC_MIN_DOUBLE = 1e-301d;
    public static final double HC_MAX_DOUBLE = 9e302d;
    public static final int HC_MAX_MD_STRLEN_CLUSTER = 510; 
    public static final int HC_MAX_MD_STRLEN_EMULATOR = 256; 


    // cluster startup related
    public static final String STARTUP_ORDER = "startorder";
    public static final String STARTUP_SKEW = "startskew";
    public static final String STARTUP_MAX_SKEW = "startmaxskew";
    public static final String CMM_START_TIMEOUT = "starttimeout";
    public static final String CMM_STOP_TIMEOUT = "stoptimeout";

    // CM/quorum related
    public static final double QUORUM_PERCENT = 0.75;
    public static final int DISKS_PER_NODE = 4;
    public static final String NODE_CONFIG_XML = "ncfg";

    // error message related constants
    public static final String INTERNAL_OID_REGEXP = ".*?-*?-.*?-.*?";
    public static final String DELETE_REGEXP = ".*?delete.*?";
    public static final String CTX_REGEXP = ".*?ctx.*?";
    public static final String REFCNT_REGEXP = ".*?refcnt.*?";

    // fragmemt tests
    public static final String DELETE_DOUBLE = "deletedouble";
    public static final String DELETE_SINGLE = "deletesingle";
    public static final String CORRUPT_FRAGMENT = "corruptfragment";
    public static final String DO_HEALBACK = "healback";

    // Configuration update tests
    public static final String CLI_CHANGE_ITERATIONS = "clichangeiterations";
    public static final String [] CLI_BANNER = 
    {"********************************************************************",
     "* CAUTION: This command is intended for Sun Customer Service only. *",
     "********************************************************************"};

    
}
