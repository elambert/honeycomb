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



// Overview
// --------
//
// This test program is used for uploading and retrieving files from a
// honeycomb system.  The program has the following features:
// - generates a predictable data set for a given set of input parameters,
//    which is useful for reproducing problems
// - generates a unique data set, which is needed to be able to upload a
//    large number of files to the archive
// - generates files of a variety of sizes starting at a given size (-G),
//    which is useful because we anticipate having a relatively uniform
//    data set on a given system.  A varied data set can be achieved by
//    invoking multiple copies of this program with different starting sizes.
//    Alternately, a fixed size file can be repeatedly uploaded (the default
//    behavior).
// - generates files without having to use much local storage on the client.
//    Local storage requirements default to 2*file_size, even though hundreds
//    of files are uploaded to the system.  If you specify not to copy the
//    file (-O), then only 1*file_size is needed.  If you retrieve
//    the file after store, an additional copy is used (this is not the
//    default but can be enabled by -Y.)
// - generated files store a trail of valid object ids internally, which can 
//    be extracted and used for querying the archive to retrieve the stored
//    files.  No additional storing of the object ids is required.
//
// There are two basic modes of operation for this program: store mode and
// retrieve mode.
//
//
// Store Mode
// ----------
//
// - creates a file or uses a copy of a specified file to upload to the
// storage archive
// - takes the OID of the stored file and replaces the bytes of the existing
// file with the OID returned by store.  If -G was specified, it appends that
// OID to the end of the file that was stored.  In both modes, we store the
// file again.  We keep a record of the OID in the file so that we can
// later retrieve the files associated with the OID we stored.
// - This second store gets a new OID for the object, and the process
// is repeated to store an unlimited number of objects to the archive
//
//
// Retrieve Mode
// -------------
//
// - takes the name of a file that was generated using the Store Mode of
// this program
// - finds the place where the OIDs of the objects stored during Store
// Mode were inserted to the stored file
// - issues a retrieve for each of the OIDs it extracts from the
// given file
//
//
// Query Mode
// ----------
//
// - retrieves the EMD from the file we uploaded and does some queries
// to test that the right things are stored.  The schema and views that
// are required for using this test program are declared in the file
//
//     $CVS_BASE/honeycomb/test/src/dtet/etc/schema.properties
//
// Examples
// --------
//
// NOTE: Currently, the TestStoreRetrieve class is packaged in the
// honeycomb-test.jar file.  The test jar file gets built by 'ant test.jar'.
// To invoke this test program, you can create a wrapper such as the following
// that will set up the classpath corectly and invoke the TestStoreRetrieve
// class:
/*
#! /bin/sh -x

CVS_BASE=/home/srgordon/cvs/new-sf/honeycomb

java -classpath $CVS_BASE/test/dist/honeycomb-test.jar:\
$CVS_BASE/dist/lib/honeycomb-client.jar:\
  com.sun.honeycomb.test.TestStoreRetrieve $*
*/
//
//
// The below examples omit this longer syntax in favor of focusing on the
// options passed to the class.
//
// 1.  java TestStoreRetrieve -S -b 1048576 -s hcb136,hcb137 -t 3000
//
// Creates a file of size 1Mb (1024*1024=1048576).  In a loop,
// repeatedly store new files based on that seed file.  To stop the upload,
// you must use control-C (or you can specify -i to tell it how many files to
// upload).  You may see a store error reported as a result of interrupting
// the program. (I'll work on fixing that). The new files that are stored
// will append the OID of the most recently stored file to the end of
// the file and store it again.  The filename used for store is the default,
// which for this case is /tmp/upload_file-a-1048576-hcb136,hcb137-store-0
// (the default basename + initial file character + size + server + store +
// location where stored OID will be written).
// The -s indicates the server to write to.
// The -t indicates that we should sleep 3000 milliseconds between stores.
//
//
// 2.  java TestStoreRetrieve -S -b 10 -c p -i 5 -k /tmp/file -G -s hcb136
//
// Creates a file of size 10 bytes (in this example, the initial
// file will contain 10 'p' characters).  That file is uploaded, and then
// we append (-G option) the OID of the original file to the file and store
// the file again.  Because -i is specified with a value of 5, we will stop
// after uploading 5 files.  The file name used for the store will be
// /tmp/file-p-10-hcb136-store-10 (the -k specifies the basename for the file
// and the initial cahracter + size + server + store + location where stored
// OIDs will be written).
// See example 3 for information on how to retrieve these files
//
//
// 3.  java TestStoreRetrieve -R -u -d /tmp/download \
//     -f /tmp/file-p-10-hcb136-store-10 -s hcb136
//
// Retrieve the 5 files stored in example 2 to unique filenames
// in the directory /tmp/download.  The filenames will be the HASH
// of the object that was retrieved.  The directory will be created if
// it does not exist.  The file name is parsed so we know to ignore the first
// 10 characters (the original file's length) of the file when looking for 
// the OIDs to retrieve.  If we hadn't specified the -u option, we'd
// download a single file called /tmp/download.
//
//
// 4.  java TestStoreRetrieve -S -f /bin/ls -k /tmp/ls-upload -i 7 -s hcb137
//
// Use an existing file (/bin/ls) as the first file to upload instead
// of creating one.  Specify a different filename pattern for the copy of the
// file to upload using -k, because normal users can't write to /bin (the 
// default location for the copy is in the same directory as the original).
// A copy is needed because we don't want to corrupt /bin/ls during the test.
// Only upload 7 files.  The file that is stored will be named
// /tmp/ls-upload-67668-hcb137-store-0 (67668 is the size of /bin/ls)
//
//
// 5.  java TestStoreRetrieve -R -f /tmp/ls-upload-67668-hcb137-store-0 \
//     -s hcb137
//
// Retrieve the files from example 4, but don't store them uniquely.
// Simply clobber the file used for retrieve with each retrieve request.
// The file will be located in the default location
// /tmp/ls-upload-67668-hcb137-store-0-retrieve
// (The default location is ${origfile}-retrive)
//
//
// 6.  java TestStoreRetrieve -R -f /tmp/ls-upload-67668-hcb137-store-0 \
//     -d /dev/null -X
//
// Retrieve the files from example 4, but send the output to /dev/null
// so nothing is written.  In this case, you must disable verification
// of HASH (-X) because you cannot read the downloaded file after the
// retrieve to do the verification.  The read from /dev/null will cause
// the program to hang.


// XXX - todo - cleanup
// - optimization--investigate ways to improve file create/read/write/etc
// - improve exception handling -- it's currently very ad hoc
// - consistency of naming, formating, checkstyle, pmd
// - handle interrupt signal?
// - use java logging?

// rfes
// - throughput stats
// - mode for varied file sizes?
// - better error handling with curl
// - delete some of the fragments before doing the retrieve
// - mark some disks as down for store/retrieve
// - delete objects from archive
// - mode to ignore errors and "keep going"
// - mode to continue from where we left off in store mode
// - mode for varied reliability

package com.sun.honeycomb.hctest.sg;

import java.io.*;
import java.lang.*;
import java.util.*;
import java.net.InetAddress;
import com.sun.honeycomb.common.ByteArrays;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.client.*;
import java.text.SimpleDateFormat;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.hctest.sg.TestRun;
// import java.util.logging.Logger;
// import java.util.logging.Level;

/**
 * Class to create and upload unique files in a predictable,
 * repeatable way.
 */
public class TestStoreRetrieve {

    // XXX future
    // private static final Logger LOG = 
    //     Logger.getLogger(TestStoreRetrieve.class.getName());

    // Default values for some of the options
    private static final int DEFAULTSLEEP = 0;
    private static final int DEFAULTRETRIES = 0;
    private static final int DEFAULTRETRYSLEEP = 10000; // 10 seconds
    private static final String DEFAULTSERVER = "localhost";
    private static final int DEFAULTPORT = 8080;
    private static final int DEFAULTBYTES = 5000; // Increased for EMD
    private static final int DEFAULTDATAFRAGS = 6;
    private static final int DEFAULTPARITYFRAGS = 4;
    private static final char DEFAULTCHAR = 'a';
    private static final String DEFAULTSTOREFILENAME = "/tmp/upload_file";
    private static final int UNSETTIMEOUT = -1;
    private static final int DEFAULTACTIVEHOSTTIMEOUT = UNSETTIMEOUT;
    private static final int DEFAULTSOCKETTIMEOUT = UNSETTIMEOUT;
    private static final int DEFAULTCONNECTIONTIMEOUT = UNSETTIMEOUT;
    private static final int DEFAULTMAXRESULTS =
        HoneycombTestConstants.DEFAULT_MAX_RESULTS_CONSERVATIVE;

    // Strings appended to filenames during the test during storeMode
    private static final String STRINGFORSTORE = "-store-";
    private static final String STRINGFORSTORERETRIEVE = "-verifystore";

    // Strings appended to filenames during the test during retrieveMode
    private static final String STRINGFORRETRIEVE = "-retrieve";
    private static final int HASHLENGTH = 40;
    private static final int OIDLENGTH = 48; //XXX
    private static final int OIDMINLENGTH = 7; //XXX
    private static final String ENDOIDS = "\n\n";

    // Other constant values
    private static final int INVALIDSIZE = -1;
    private static final int OAFRAGMENTEDSIZE = (1024 * 512);// Transformer.java
    private static final int MAXRANDOMBYTES = 2000000;
    private static final int EMDSIZE = 2000; // first bytes of file are EMD
    private static final int NORESULTSCHECK = -1;
    private static final int USECHUNKTRANSFER = -1;
    private static final int INFINITE = -1;
    private static final String EMPTYSTRING = "";
    private static final String EMD_UNDEFINED = "UNDEFINED"; // XXX "",null,spc
    private static final String EMD_NULL = "NULL"; // for the first object
    private static final String EMD_OID = 
        HoneycombTestConstants.MD_OBJECT_ID; // for unique results
    private static final char MINEMDCHAR = 'A'; // XXX use var for these?
    private static final char MAXEMDCHAR = 'z'; // We skip non-letter chars
                                                // manually so these values
                                                // shouldn't be changed w/o
                                                // other code changing below.
                                                // Also, we expect 52 single
                                                // letter words.
    private static final String EMDVALSEP = "=";
    private static final String EMDENTRYSEP = "\t";
    private static final int ALLOWDIFF = 1;     // These 3 values declare the
    private static final int DISALLOWDIFF = 2;  // expectation on the results
    private static final int EXPECTMINIMUM = 3; // from the query/unique query

    private static final String USAGE =
        "Usage: (Note that args and options must be space separated)\n" +
        "For setting which mode the program is in:\n" +
        "  -S (store mode -- creates or uses a given file and uploads that\n" +
        "     file and similar, unique files to the system, recording the\n" +
        "     IDs of the stored objects in the file itself.  The -R option\n" +
        "     can be used to retrieve those objects.)\n" +
        "  -R (retrieve mode -- takes a file that has been stored by this\n" +
        "     program and retrieves files previously stored with that seed)\n" +
        "  -Q (query mode -- takes a file that has been stored by this\n" +
        "     program and queries for metadata that was stored)\n" +
        "  -D (delete mode -- takes a file that has been stored by this\n" +
        "     program and deletes it from the archive.  Specify this with\n" +
        "     -Q or -R to verify queries and retrieves don't return objects\n" +
        "     that should have been deleted)\n" +
        "  -e (run the test against the emulator)" +
        "For changing logging verbosity (-S, -R, -Q, -D):\n" +
        "  -q (quiet mode, no info or debugging msgs)\n" +
        "  -v (verbose mode, info and debugging msgs)\n" +
        "For changing the test exit and log behavior and audit:\n" +
        "  -T (run in the context of a Test Suite)\n" +
        "  -A clustername (used for Audit)\n" +
        "For affecting general behavior (-S, -R, -D):\n" +
        "  -C (to use curl instead of the HC client API)\n" +
        "  -F (to use NFS instead of the HC client API)\n" +
        "  -P nfs_path (what is the root dir where the test should act)\n" +
        "  -t sleep_msecs_between_loops (default is " + DEFAULTSLEEP + ")\n" +
        "  -i number_of_files_to_store_or_retrieve (default is infinite)\n" +
        "  -r number_of_retries (default is " + DEFAULTRETRIES + ")\n" +
        "  -w wait_msecs_btwn_retries (default " + DEFAULTRETRYSLEEP + ")\n" +
        "For connecting to the server (all):\n" +
        "  -s server,server,... (defaults to " + DEFAULTSERVER +"; multiple\n" +
        "     servers can be separated by ',' for client load balancing)\n" +
        "  -p port (defaults to " + DEFAULTPORT + ")\n" +
        "  -a activehost_timeout (defaults to API default; specified in ms)\n" +
        "  -n connect_timeout (defaults to API default; specified in ms)\n" +
        "  -o socket_timeout (defaults to API default; specified in ms)\n" +
        "  -I (Initialize client connection each call; defaults to false)\n" +
        "For general store mode options (-S only):\n" +
        "  -G (grow the file instead of keeping it a fixed size)\n" +
        "  -N data_frags (defaults to " + DEFAULTDATAFRAGS + ")\n" +
        "  -M parity_frags (defaults to " + DEFAULTPARITYFRAGS + ")\n" +
        "  -B (don't pass the size to the server--use chunk transfer)\n" +
        "  -E (allow object exists; continue as though we stored the file)\n" +
        "  -z (do not set any extended MD during store)\n" +
        "For initial file creation (-S only):\n" +
        "  -b bytes_of_original_data (defaults to " + DEFAULTBYTES + ")\n" +
        "  -c initial_char (defaults to \'" + DEFAULTCHAR + "\')\n" +
        "  -g (don't generate file w/ random bytes, only use initial_char)\n" +
        "  -x (use random file size; overrides -b if it has been specified)\n" +
        "  -y (use random character; overrides -c if it has been specified)\n" +
        "  -k basename_of_upload (defaults to " + DEFAULTSTOREFILENAME + ")\n" +
        "  -O (OVERWRITES an existing file that was created by this program\n" +
        "     when it was invoked with the same arguments.  Default is to\n" +
        "     return an error and not overwrite the file.\n" +
        "For circumventing file creation (-S only):\n" +
        "  -f original_file (no default--overrides file creation)\n" +
        "  -K (suppresses copying the specified file.  By default,\n" +
        "     the file is copied because the contents of the file are\n" +
        "     corrupted by this test program.  Use this option with care,\n" +
        "     because it will OVERWRITE the specified file)\n" +
        "  -k basename_of_upload (default adds \"" + STRINGFORSTORE + 
              "$len\" to original_file)\n" +
        "  -O (OVERWRITES an existing file that was created by this program\n" +
        "     when it was invoked with the same arguments.  Default is to\n" +
        "     return an error and not overwrite the file.\n" +
        "For retrieve mode (-R only):\n" +
        "  -f original_file (no default--should be a file created by this " +
              "test program)\n" +
        "  -b bytes_of_original_data (defaults to parsing the filename.\n" +
        "     Files created by this program end in \"" +
              STRINGFORSTORE + "$len\" and that\n" +
        "     original length is extracted and used)\n" +
        "  -d download_location (default: $original_file" +
              STRINGFORRETRIEVE + ")\n" +
        "  -u (retrieve objects to unique files. Default is not to do this.\n" +
        "     The -d option will be interpreted as a directory in this case\n" +
        "For affecting verification (-S and -R)\n" +
        "  -X (disables verifying HASH on store and retrieve)\n" + 
        "  -Y (enables retrieving all files after they are stored)\n" + 
        "  -Z (enables re-storing all files after they are retrieved\n" +
        "     to verify that the object exists in the archive)\n" + 
        "For affecting queries (-Q only)\n" +
        "  -m max_results_per_query (set max number of results for each\n" +
        "     query done on the system.  Uses cookies.\n" +
        "     Default is " + DEFAULTMAXRESULTS + ")\n" +
        "Help:\n" +
        "  -h (prints usage text)";

    // Which mode is the program in?
    private static boolean storeMode = false;
    private static boolean retrieveMode = false;
    private static boolean queryMode = false;
    private static boolean deleteMode = false;
    private static boolean runAgainstEmulator = false;
    public static boolean regressionMode = false;
    private static String invocationArgs = null;

    // For logging verbosity
    private static boolean logInfoMsgs = true;
    private static boolean logDebugMsgs = false;
    public static boolean useLogClass = false;

    // Exit behavior
    private static boolean throwInsteadOfExit = false;

    // for Audit
    private static String clusterName = null;

    // General behavior common to both modes
    private static boolean startOverWhenDone = true;
    private static int currLoop = 1;
    private static String loopString = "";
    private static int sleepBetweenIterations = DEFAULTSLEEP;
    private static int maxIterations = INFINITE;
    private static int numRetries = DEFAULTRETRIES;
    private static int sleepBetweenRetries = DEFAULTRETRYSLEEP;
    private static int initialOffset = 0; // where we start writing
    private static int minBytes = 0; // the minimum bytes for store
    // MINBYTESWITHEMD = EMD + OID + HASH + ENDOIDS
    public static final int minBytesWithEMD = EMDSIZE + 1 + OIDLENGTH +
        HASHLENGTH + 2;

    // For grow/non-grow of file in store mode
    private static boolean growFile = false;
    private static long currOffset = 0;
    private static long currIteration = 0;

    // For EMD
    private static boolean useEMD = true;
    private static StringBuffer currWord = null;
    private static HashMap emdHM = null;
    private static String uploadClient = null;
    private static Random rand = null;

    // For statistics--note that these are very, very rough stats
    private static long testStartTime; // for stats from the beginning
    private static long totalStoreTime = 0; // for stats for all iterations
    private static long totalRetrieveTime = 0;
    private static long totalDeleteTime = 0;
    private static long storedBytes = 0;
    private static long retrievedBytes = 0;

    // For the client API or curl
    private static boolean useCurl = false;
    //private static NameValueObjectArchive archive = null;
    private static HoneycombTestClient htc = null;  // library allows audit
    private static boolean reinitConnection = false; // reinit between calls
    // XXX private static ObjectReliability reliability = null;
    private static int dataFrags = DEFAULTDATAFRAGS;
    private static int parityFrags = DEFAULTPARITYFRAGS;

    // For API only
    private static boolean passSize = true;
    private static boolean allowObjectExists = false;

    // For connecting to the server
    private static String server = DEFAULTSERVER;
    private static String[] servers = {server};
    private static String serverToUse = ""; // single svr to use for new api
    private static int port = DEFAULTPORT;
    private static int activeHostTimeout = DEFAULTACTIVEHOSTTIMEOUT;
    private static int connectionTimeout = DEFAULTCONNECTIONTIMEOUT;
    private static int socketTimeout = DEFAULTSOCKETTIMEOUT;

    // File and object manipulation (storeMode only)
    public static String uploadFilename;
    private static String uploadBaseFilename = EMPTYSTRING;
    private static boolean overwriteFile = false;
    private static boolean suppressCopy = false;

    // For file creation (storeMode only)
    private static char initChar = DEFAULTCHAR;
    private static boolean useRandomBytes = false;
    private static boolean useRandomChar = false;
    private static boolean generateConstantFile = false;

    // For using existing file specified by user (storeMode only)
    private static boolean createNewFile = true;

    // For retrieving files generated by this program (retrieveMode only)
    private static boolean saveRetrievedFilesUniquely = false;
    private static String retrieveTarget = EMPTYSTRING;
    private static boolean testRangeRetrieve = true; // XXX expose

    // For retrieve, delete, etc
    private static RandomAccessFile rafTestResultFile = null;

    // For store and retrieve modes
    private static String originalFilename = EMPTYSTRING;
    private static long originalSize = INVALIDSIZE;

    // For NFS mode (both store, retrieve, and delete)
    private static boolean useNFS = false;
    private static String nfsBasePath = null;
    private static String nfsTestrunDir = null;
    private static int nfsMaxFilesPerDir = 50; // XXX config var
    private static int nfsMaxDirDepth = 5; // XXX config var
    private static int nfsCurrFilesPerDir = 0; // current number of file in dir
    private static int nfsCurrDirDepth = 0; // current dir depth
    private static String nfsCurrDir = null; // current directory to use
    private static int nfsCurrDirNum = 0; // incremented to create dirname N

    private static int HC_MAX_MD_STRLEN = -1;

    // For query mode
    private static int maxResults = DEFAULTMAXRESULTS;

    // For verification after actions
    private static boolean verifyHash = true;
    private static boolean doRetrieveAfterStore = false;
    private static boolean doReStore = false;

    /**
     * This routine uploads and retrieves unique files to a honeycomb system,
     * depending on the options specified on the command line.
     */
    public static void main(String args[])
        throws Throwable {

        try {
            parseArgs(args);
        } catch (ArgException e) {
            printError("ERROR: " + e.getMessage());
            printError(USAGE);
            die();
        }

        printDate("\n---> Starting test at ");
        testStartTime = TestLibrary.msecsNow();

        // Note that delete mode can be specified with the other modes
        // to indicate the files have been deleted...
        if ((storeMode && (retrieveMode || queryMode)) ||
            (retrieveMode && (storeMode || queryMode)) ||
            (queryMode && (storeMode || retrieveMode))) {
            printError("ERROR: can't specify more than one of -S, -R, -Q");
            printError(USAGE);
            die();
        }

        // ... the one exception to the above is store mode, which doesn't
        // make sense with delete mode
        if (storeMode && deleteMode) {
            printError("ERROR: can't specify more than one of -S, -D");
            printError(USAGE);
            die();
        }

        // Add an early check for this case so we don't try to initialize
        // the client NVOA object without a proper address in the case
        // where the user has specified no options
        if (!(storeMode || retrieveMode || queryMode || deleteMode)) {
            printError("ERROR: must specify at least one of -S, -R, -Q, -D");
            printError(USAGE);
            die();
        }


        HC_MAX_MD_STRLEN = (runAgainstEmulator) ? 
            HoneycombTestConstants.HC_MAX_MD_STRLEN_EMULATOR :
            HoneycombTestConstants.HC_MAX_MD_STRLEN_CLUSTER;
          
        printInfo("STEPH HC_MAX_MD_STRLEN = " + HC_MAX_MD_STRLEN +
                  ", args = " + Arrays.toString(args));

        if (!useCurl && !useNFS) {
            serverToUse = servers[0];  // initialize to the default
            // XXX We can only pass one server to the new api,
            // so if there are more, than try to fake load-balance.
            if (servers.length > 1) {
                printError("XXX load balancing is not supported in the " +
                    "client library any more");

                serverToUse = TestLibrary.pickRandom(servers);
                // pick a random server from the list and use it 
                printError("XXX using only server " + serverToUse); 
            }

            // Allow audit to work.
            if (clusterName != null) {
                printInfo("Using cluster name " + clusterName + " for audit");
                TestRunner.setProperty(HoneycombTestConstants.PROPERTY_CLUSTER,
                    clusterName);
            } else {
                printError("No cluster name was given...audit not enabled");
            }

            //archive = new TestNVOA(serverToUse, port);
            htc = new HoneycombTestClient(serverToUse, port);

            if (activeHostTimeout != UNSETTIMEOUT) {
                printInfo("Note: setting activeHostTimeout no longer allowed"); 
                // archive.setActiveHostTimeout(activeHostTimeout);
            }

            if (socketTimeout != UNSETTIMEOUT) {
                printInfo("Note: setting socketTimeout no longer allowed in test"); 
                // archive.setSocketTimeout(socketTimeout);
            }

            if (connectionTimeout != UNSETTIMEOUT) {
                printInfo("Note: setting connectionTimeout no longer allowed in test"); 
                // archive.setConnectionTimeout(connectionTimeout);
            }
        }

        // Note that delete mode can be specified simultaneously to affect
        // the way retrieves and queries behave.  It doesn't affect store mode
        if (storeMode) {
            storeMode();
        } else if (retrieveMode) {
            retrieveMode();
        } else if (queryMode) {
            queryMode();
        } else if (deleteMode) {
            deleteMode();
        } else {
            printError("ERROR: must specify either -S or -R or -Q or -D");
            printError(USAGE);
            die();
        }

        // Nothing left to do, so execute exit code
        done();
    }

    // Ugh, this is a hack to reinitialize the static vars for re-use...
    public static void reinitialize() {
        storeMode = false;
        retrieveMode = false;
        queryMode = false;
        deleteMode = false;
        regressionMode = false;
        invocationArgs = null;

        // For logging verbosity
        logInfoMsgs = true;
        logDebugMsgs = false;
        TestLibrary.logInfoMsgs = true;
        TestLibrary.logDebugMsgs = false;

        // Exit behavior
        throwInsteadOfExit = false;
        useLogClass = false;
        TestLibrary.useLogClass = false;

        // General behavior common to both modes
        startOverWhenDone = true;
        currLoop = 1;
        loopString = "";
        sleepBetweenIterations = DEFAULTSLEEP;
        maxIterations = INFINITE;
        numRetries = DEFAULTRETRIES;
        sleepBetweenRetries = DEFAULTRETRYSLEEP;
        initialOffset = 0; // where we start writing
        minBytes = 0; // the minimum bytes for store

        // For grow/non-grow of file in store mode
        growFile = false;
        currOffset = 0;
        currIteration = 0;

        // For EMD
        useEMD = true;
        currWord = null;
        emdHM = null;
        uploadClient = null;
        rand = null;

        // For statistics--note that these are very, very rough stats
        //testStartTime; // for stats from the beginning
        totalStoreTime = 0; // for stats for all iterations
        totalRetrieveTime = 0;
        totalDeleteTime = 0;
        storedBytes = 0;
        retrievedBytes = 0;

        // For the client API or curl
        useCurl = false;
        htc = null;
        reinitConnection = false;
        //archive = null;
        // XXX private static ObjectReliability reliability = null;
        dataFrags = DEFAULTDATAFRAGS;
        parityFrags = DEFAULTPARITYFRAGS;

        // For API only
        passSize = true;
        allowObjectExists = false;

        // For connecting to the server
        server = DEFAULTSERVER;
        //servers = {server};
        serverToUse = ""; // single svr to use for new api
        port = DEFAULTPORT;
        activeHostTimeout = DEFAULTACTIVEHOSTTIMEOUT;
        connectionTimeout = DEFAULTCONNECTIONTIMEOUT;
        socketTimeout = DEFAULTSOCKETTIMEOUT;

        // File and object manipulation (storeMode only)
        //uploadFilename;
        uploadBaseFilename = EMPTYSTRING;
        overwriteFile = false;
        suppressCopy = false;

        // For file creation (storeMode only)
        initChar = DEFAULTCHAR;
        useRandomBytes = false;
        useRandomChar = false;
        generateConstantFile = false;

        // For using existing file specified by user (storeMode only)
        createNewFile = true;

        // For retrieving files generated by this program (retrieveMode only)
        saveRetrievedFilesUniquely = false;
        retrieveTarget = EMPTYSTRING;
        testRangeRetrieve = true; // XXX expose

        // For retrieve, delete, etc
        rafTestResultFile = null;

        // For store and retrieve modes
        originalFilename = EMPTYSTRING;
        originalSize = INVALIDSIZE;

        // For NFS mode (both store, retrieve, and delete)
        useNFS = false;
        nfsBasePath = null;
        nfsTestrunDir = null;
        nfsMaxFilesPerDir = 50; // XXX config var
        nfsMaxDirDepth = 5; // XXX config var
        nfsCurrFilesPerDir = 0; // current number of file in dir
        nfsCurrDirDepth = 0; // current dir depth
        nfsCurrDir = null; // current directory to use
        nfsCurrDirNum = 0; // incremented to create dirname N


        // For query mode
        maxResults = DEFAULTMAXRESULTS;

        // For verification after actions
        verifyHash = true;
        doRetrieveAfterStore = false;
        doReStore = false;
    }

    /**
     * In store mode, we generate unique files and upload them to the system.
     */
    public static void storeMode() throws IOException, HoneycombTestException {
        if (useEMD && (useCurl || useNFS)) {
            printInfo("Warning: Can't pass EMD in Curl or NFS mode");
            useEMD = false;
        }

        // whereToStartRetrieve encodes in the filename where OIDs of the files
        // stored as part of this test run begin.  If we do not grow the file,
        // they start at the first byte of the file.  If we do grow the file,
        // they start after the initial bytes of the file.  Either way, we
        // still always encode the original size of the file in the filename
        // (sometimes redundantly) to avoid clobbering an existing testfile
        // in non-grow mode in the case where we change the size and
        // restart the test.
        if (useEMD) {
            initialOffset = EMDSIZE + 1;
            minBytes = minBytesWithEMD;
        } else {
            initialOffset = 0;
            minBytes = 0;
        }
        long whereToStartRetrieve = initialOffset;
        currOffset = initialOffset;

        // Initialize filenames.
        //
        // We add the length to the filename to enable extracting it later.
        // This is useful so we can find the OIDs that will be
        // stored at the end of the file as part of this test and later
        // issue retrieves for those object IDs.
        if (createNewFile) {
            // Set random file size
            if (useRandomBytes) {
                originalSize = (long)(Math.random() * MAXRANDOMBYTES);

                // We always want to pick a file size that will be fragmented.
                // Adding the OAFRAGMENTEDSIZE to a random file size that is too
                // small will help create a random distribution of sizes between
                // OAFRAGMENTEDSIZE and MAXRANDOMBYTES.
                if (originalSize < OAFRAGMENTEDSIZE) {
                    originalSize += OAFRAGMENTEDSIZE;
                }
            }

            // Set random character to use
            if (useRandomChar) {
                int randint = (int)(Math.random() * Character.MAX_RADIX);
                initChar = Character.forDigit(randint, Character.MAX_RADIX);
            }

            if (generateConstantFile) {
                TestLibrary.useRandomBytes = false;
            }

            if (originalSize == INVALIDSIZE) {
                originalSize = DEFAULTBYTES;
            }

            if (uploadBaseFilename.equals(EMPTYSTRING)) {
                uploadBaseFilename = DEFAULTSTOREFILENAME;
            }

            if (growFile) {
                whereToStartRetrieve = originalSize;
            }

            uploadFilename = uploadBaseFilename + "-" + initChar + "-" +
                originalSize + "-" + server + STRINGFORSTORE +
                whereToStartRetrieve;
        } else {
            // We are using an existing file.
            File f = new File(originalFilename);  // XXX check exceptions
            originalSize = f.length();

            if (growFile) {
                whereToStartRetrieve = originalSize;
            }

            if (uploadBaseFilename.equals(EMPTYSTRING)) {
                // construct default name based on original filename
                uploadFilename = originalFilename + "-" + originalSize + "-" +
                    server + STRINGFORSTORE + whereToStartRetrieve;
            } else {
                // use user specified base filename
                uploadFilename = uploadBaseFilename + "-" + originalSize + "-"
                    + server + STRINGFORSTORE + whereToStartRetrieve;
            }
        }

        if (originalSize < minBytes) {
            printError("ERROR: Can't specify file of less than " +
                minBytes + " bytes.\n" +
                "File of size " + originalSize + " was specified. " +
                "Either pick a larger size or omit EMD at store time (-z)");
            printError(USAGE);
            die();
        }

        // If the user has not specified a file to use with -f,
        // we create a file using the number of bytes, base filename, and
        // initial character that was given to us.
        //
        // Otherwise, we will copy/rename the file given to us and upload that.
        try {
            if (createNewFile) {
                TestLibrary.addNewLines = true;
                TestLibrary.createFile(uploadFilename, originalSize, initChar,
                    overwriteFile);
            } else if (suppressCopy) {
                // We won't copy the file, but we still want to
                // rename the file so that it has a name that is compatible
                // with retrieve mode.  The assumption is that this file
                // isn't important because we are going to corrupt it anyway
                // by adding OIDs to it, so renaming it should be fine, too.
                TestLibrary.renameFile(originalFilename, uploadFilename);
            } else {
                // The user has given us a filename to use for the upload.
                // Make a copy of the file so we don't corrupt what they pass
                // us.
                TestLibrary.copyFile(originalFilename, uploadFilename,
                    overwriteFile);
            }
        } catch (Throwable e) {
            printError("ERROR: Failed to create file before store: " +
                e.getMessage());
            // XXX fix!
            printError("Make sure the file " +
                originalFilename + " exists and is readable");
            printError("Make sure the file " + uploadFilename + 
                " can be written (ie, it is in a writable directory) and " +
                "if it already exists, is a writable file. (You can " +
                "specify the -k option to specify a different " +
                "destination path for the file being copied.)");
            die();
        }

        // Initialize the starting point in the file in case we don't actually
        // do any stores.  This will allow us to detect this case and
        // exit the retrieves without retrieving any files.  This is only needed
        // for non-grow mode because we'll be able to detect 0 stores in grow
        // mode very easily.  We don't adjust the currOffset because we want
        // to overwrite this during the first store.
        if (!growFile) {
            TestLibrary.replaceBytesInFile(ENDOIDS, currOffset,
                uploadFilename);
        }

        // Initialize EMD.  Do this now so we can read back the EMD even if
        // we don't do stores.
        if (useEMD) {
            initEMD();
        }

        if (useNFS) {
            initNFSMode();
        }

        printVariables(); // for debugging only

        // Some generally helpful info.  Once people get to know this tool
        // then these can be debug messages.
        printInfo("File used for store is " + uploadFilename);
        if (useNFS) {
            printInfo("Use -R -F -f " + uploadFilename); 
            printInfo("to retrieve objects stored.");
        } else {
            printInfo("Use -R -f " + uploadFilename + " -s " + server); 
            printInfo("to retrieve objects stored.");
        }
        if (useEMD && !useNFS && !useCurl) {
            printInfo("Use -Q -f " + uploadFilename + " -s " + server); 
            printInfo("to test EMD for objects stored.");
        }
        if (useNFS) {
            printInfo("Use -D -F -f " + uploadFilename);
            printInfo("to delete objects stored.");
        } else {
            printInfo("Use -D -f " + uploadFilename + " -s " + server); 
            printInfo("to delete objects stored.");
        }
        printInfo("Use -h to see all options.");

        FileID storeResult = new FileID();

        while (currIteration++ < maxIterations || maxIterations == INFINITE) {
            printDate("\n-- starting iteration " + currIteration +
                " -- ");

            if (useEMD) {
                updateEMD(storeResult);
            }

            // Store the object
            storeResult = storeFile(uploadFilename);

            if (growFile) {
                // Generate a new file for uploading by adding the OID of the
                // most recently stored file to the end of the stored file.
                // These OIDs will be extracted from this file under 
                // Retrieve Mode.
                TestLibrary.appendFile(storeResult.toFileString(),
                    uploadFilename);
            } else {
                // Replace bytes in the existing file with the OID we just
                // stored
                String s = storeResult.toFileString() + ENDOIDS;
                if (s.length() + currOffset > originalSize) {
                    printDebug("Reset offset from " + currOffset + " to " +
                        initialOffset);
                    currOffset = initialOffset;
                }

                TestLibrary.replaceBytesInFile(s, currOffset, uploadFilename);
                currOffset += s.length() - 1; // only count one newline
            }

            sleep(sleepBetweenIterations);
        }

        printInfo("\nDone: completed storing " + maxIterations + " files");
    }

    /**
     * Examines a file that was stored under Store Mode and attempts
     * to retrieve all the objects that were uploaded and whose
     * OIDs are stored in the given file.
     */
    public static void retrieveMode()
        throws IOException, HoneycombTestException {
        if (originalFilename.equals(EMPTYSTRING)) {
            printError("ERROR: must specify a filename with -f");
            printError(USAGE);
            die();
        }

        // Use default filename if no target was specified
        if (retrieveTarget.equals(EMPTYSTRING)) {
            retrieveTarget = originalFilename + STRINGFORRETRIEVE;
        }

        // Use a directory if we are storing multiple files
        if (saveRetrievedFilesUniquely) {
            retrieveTarget += File.separatorChar;
        }

        TestLibrary.validatePath(retrieveTarget);

        printVariables(); // for debugging only

        while (true) {
            // Check if we've completed the max number of iterations
            if (++currIteration > maxIterations &&
                maxIterations != INFINITE) {
                printInfo("\nDone: Completed " + maxIterations + " retrieves");
                break;
            }

            // If we've run out of FIDs, we're done, unless we should start over
            FileID extractedFID = getNextFIDFromFile();
            if (extractedFID == null) {
                printInfo("\nDone: Completed " + --currIteration +
                    " retrieves");
                if (startOverWhenDone && !deleteMode) {
                    printInfo("\n\n--> END OF FILE REACHED; STARTING OVER\n\n");
                    currIteration = 0;
                    currLoop++;
                    loopString = " (retrieve loop " + currLoop + ")";
                    // cached retrieves return quickly...take a breather so
                    // the test watcher can see what is going on
                    int n = 3; // how long to sleep in seconds
                    printInfo("Sleeping for " + n +
                        " seconds before starting loop " + currLoop);
                    sleep(n * 1000);
                    continue;
                }
                break;
            }

            printDate("\n-- starting iteration " +
                currIteration + loopString + " -- ");
            // Get a unique filename if needed
            String fname = retrieveTarget;
            if (saveRetrievedFilesUniquely) {
                fname += extractedFID.getFilename();
            }

            // XXX check for non-writable retrieve file
            retrieveFile(extractedFID, fname);

            sleep(sleepBetweenIterations);
        }
    }

    /**
     * Try to delete all the files that were stored in the testrun.
     */
    public static void deleteMode() throws Throwable {
        if (originalFilename.equals(EMPTYSTRING)) {
            printError("ERROR: must specify a filename with -f");
            printError(USAGE);
            die();
        }

        printVariables(); // for debugging only

        FileID extractedFID;
        while ((extractedFID = getNextFIDFromFile()) != null) {
            if (extractedFID == null) {
                printInfo("\nDone: Completed " + currIteration + " deletes");
                break;
            }

            // Check if we've completed the max number of iterations
            if (++currIteration > maxIterations &&
                maxIterations != INFINITE) {
                printInfo("\nDone: Completed " + maxIterations + " deletes");
                break;
            }

            printDate("\n-- starting iteration " +
                currIteration + " -- ");

            deleteFile(extractedFID);

            sleep(sleepBetweenIterations);
        }

        // after deleting all the oids, try queries and retrieves
        printInfo("\n--> After deleting objects, trying retrieve");
        currIteration = 0;
        retrieveMode();

        // we don't support queries in curl or NFS modes
        if (!useNFS && !useCurl) {
            printInfo("\n--> After deleting objects, trying query");
            currIteration = 0;
            queryMode();
        }
    }

    /**
     * This routine is called to extract the FIDs from a test file
     * for use in retrieve, delete, etc.  It returns null when there are
     * no more FIDs
     */
    public static FileID getNextFIDFromFile() throws HoneycombTestException {
        String line = null;
        FileID nextFID = null;

        try {
            // initialize the raf we'll use to extract FIDs
            if (rafTestResultFile == null) {
                rafTestResultFile = new RandomAccessFile(originalFilename, "r");

                // extract number of bytes to skip from filename if needed
                if (originalSize == INVALIDSIZE) {
                    // We expect the filename to end in STRINGFORSTORE$len
                    int index = originalFilename.lastIndexOf(STRINGFORSTORE);
                    index += STRINGFORSTORE.length();
                    String numbytes = originalFilename.substring(index);
                    try {
                        originalSize = Long.parseLong(numbytes);
                    } catch (NumberFormatException e) {
                        printError("ERROR: Couldn't extract data length for " +
                            originalFilename); 
                        printError("Try using -b to specify it manually");
                        die();
                    }
                }

                rafTestResultFile.seek(originalSize);
            }

            // For debugging reasons, calculate where we are reading from
            long currentFileOffset = rafTestResultFile.getFilePointer();

            // Each line of the file is a FID for us
            line = rafTestResultFile.readLine();

            printDebug("Read " + line + " from offset " +
                currentFileOffset + " of " + originalFilename);

            // Check for the "end" of the FID markers
            if (line == null || line.equals("")) {
                rafTestResultFile.close();
                rafTestResultFile = null;
                return (null);
            }

            nextFID = new FileID(line);
            if (!nextFID.validate()) {
                printError("ERROR: OID extracted from " + originalFilename +
                    " was invalid:" + nextFID);
                printError("Approximate offset into " + originalFilename +
                    " where bad OID was found is " +
                    rafTestResultFile.getFilePointer());
                die();
            }
        } catch (IOException e) {
            printError("ERROR: Failure reading file " + originalFilename +
                ": " + e.getMessage());
            die();
        }

        return (nextFID);
    }

    private static long hmGetLong(HashMap hm, String name) throws HoneycombTestException {
        Object o = hm.get(name);
        if (! (o instanceof Long)) {
            printError("ERROR: not a Long: " + name);
            die();
        }
        Long l = (Long) o;
        return l.longValue();
    }
    private static String hmGetString(HashMap hm, String name) throws HoneycombTestException {
        Object o = hm.get(name);
        if (! (o instanceof String)) {
            printError("ERROR: not a String: " + name);
            die();
        }
        return (String) o;
    }
    private static double hmGetDouble(HashMap hm, String name) throws HoneycombTestException {
        Object o = hm.get(name);
        if (! (o instanceof Double)) {
            printError("ERROR: not a Double: " + name);
            die();
        }
        Double d = (Double) o;
        return d.doubleValue();
    }


    public static void queryMode() throws Throwable {
        QueryResult qr;
        HashMap omd = null;
        HashMap file_hm = null;
        String q;
        ArrayList qList = new ArrayList();

        if (useCurl || useNFS) {
            printError("ERROR: cannot use curl or NFS in query mode");
            die();
        }

        if (originalFilename.equals(EMPTYSTRING)) {
            printError("ERROR: must specify a filename with -f");
            printError(USAGE);
            die();
        }

        printVariables(); // for debugging only

        file_hm = readEMDFromFile();

        // these fields should uniquely identify this run
        long timestart = hmGetLong(file_hm, "timestart");
        long fileorigsize = hmGetLong(file_hm, "fileorigsize");
        String client = hmGetString(file_hm, "client");
        String filename = hmGetString(file_hm, "filename");

        // a query string that should limit results to this test run only
        String thisTestRun = "\"timestart\"=" + timestart;
        thisTestRun += " AND ";
        thisTestRun += "\"fileorigsize\"=" + fileorigsize;
        thisTestRun += " AND ";
        thisTestRun += "\"client\"='" + client + "'";
        thisTestRun += " AND ";
        thisTestRun += "\"filename\"='" + filename + "'";

	long iterationFromDB = hmGetLong(file_hm, "iteration");
        long iterationNum = iterationFromDB;

        // This record may or may not have been stored before
        // we stopped the test
        q = "\"iteration\"=" + iterationNum; 
        q += " AND ";
        q += thisTestRun;
        printInfo("Checking to see if we stored object with iteration " +
            iterationNum);
        qr = queryTest(q, maxResults, NORESULTSCHECK, ALLOWDIFF);

        if (qr.num == 0) {
            // We might have stopped the test before storing this file
            // so we decrement the number of expected files
            if (iterationNum > 0) {
                iterationNum--;
                printInfo("Couldn't find object with iteration " + iterationFromDB +
                    " so lowered the expected objects to " + iterationNum);
            }
        }

        // This clause allows the test to be run while store is in progress
        String limitIteration = "\"iteration\"<" + (iterationNum + 1);

        // Get the EMD for the iteration number we assume is our maximum
        q = "\"iteration\"=" + iterationNum;
        q += " AND ";
        q += thisTestRun;
        qr = query(q, maxResults,
            (iterationNum == 0 || deleteMode ? 0 : 1));

        // We expect this query to have one result unless the test didn't
        // complete one iteration or we've deleted objects
        if (qr.num > 0 && qr.qrs.next()) {
            // XXX PORT first res or second?  above next()
            omd = getMetadata(qr.qrs.getObjectIdentifier().toString());
        } else {
            if (iterationNum > 0 && !deleteMode) {
                printError("Didn't get at least one result as expected for " +
                    "query with where iterationNum is " + iterationNum + ": " +
                    q);
                die();
            }
        }

        // Get the last OID we stored, ask for MD for that, and repeat
        // XXX want to validate can't do getMetadata on all deleted objs
        // must read oids from the file to do this.
        if (iterationNum > 0 && !deleteMode) {
            String prevOID = hmGetString(omd, "prevSHA1"); // XXX - new field

            long origOldIterationNum = hmGetLong(omd, "iteration");
            long currOldIterationNum = origOldIterationNum;
            long stopIterationNum = 0;

            if (origOldIterationNum != iterationNum) {
                printError("ERROR: expected to have iteration=" + iterationNum +
                    " but it is " + origOldIterationNum + " for " + prevOID);
                die();
            }

            if (maxIterations != INFINITE) {
                stopIterationNum = origOldIterationNum - maxIterations;
                if (stopIterationNum < 0) {
                    printInfo("Ignoring -i option " + maxIterations +
                        ":  only " + iterationNum + " iterations are possible");
                    stopIterationNum = 0;
                } else {
                    printInfo("Limiting number of EMD queries to " +
                        maxIterations);
                }
            }

            printDate("Starting querying for EMD at ");
            printInfo("Starting at iteration " + origOldIterationNum +
                " and stopping at " + stopIterationNum + ".  There are " +
                iterationNum + " total " + "iterations possible");

            while (prevOID != null && !prevOID.equals(EMD_NULL) &&
                stopIterationNum < currOldIterationNum) {

                printDebug("-- prevOID for iteration " + currOldIterationNum +
                    " was " + prevOID + "; querying for MD -- ");

                omd = getMetadata(prevOID);
                prevOID = hmGetString(omd, "prevSHA1"); // XXX - new field
		long previous = currOldIterationNum - 1;
                currOldIterationNum = hmGetLong(omd, "iteration"); 
                if (currOldIterationNum != previous) {
                    printError("ERROR: expected to retrieve old iteration " +
                        "value " + previous + " but got " + 
                         currOldIterationNum + " instead");
                    die();
                }

                if (currOldIterationNum % 100 == 0) {
                    // print occasional status
                    printDate("Still querying...currently on " +
                        currOldIterationNum + " as of ");
                }
            }

            printDate("Finished querying for EMD in this file at ");
            printInfo("");
        } else {
            printInfo("Skipping loop to query for all EMD because no files " +
                "were stored or we are in deleteMode");
        }

        //
        // Query for all results for this specific test run
        //
        q = limitIteration;
        q += " AND ";
        q += thisTestRun;
        qr = queryTest(q, maxResults, (int)iterationNum, DISALLOWDIFF);

        //
        // Query for all results for this specific test run
        // but expect one less result because we are using <
        //
        q = "\"iteration\"<" + iterationNum;
        q += " AND ";
        q += thisTestRun;
        qr = queryTest(q, maxResults, (int)iterationNum - 1, DISALLOWDIFF);

        //
        // Query for all one char words from this test run.
        // There should be 52 or less, depending on how many
        // iterations we've completed (assuming MAXEMDCHAR is
        // z and MINEMDCHAR is A).
        //
        q = "\"wordlength\"=" + 1;
        q += " AND ";
        q += limitIteration;
        q += " AND ";
        q += thisTestRun;

        // XXX Ugh, it is lame to hard code this, but getNumericValue doesn't
        // distinguish between case and I'm not sure how to do this.
        int maxOneWordFiles = 52;
        // int maxOneWordFiles = Character.getNumericValue(MAXEMDCHAR) -
        //    Character.getNumericValue(MINEMDCHAR) + 1 - nonAlphaChars();
        long expectedOneWordFiles = (maxOneWordFiles > iterationNum ?
            iterationNum : maxOneWordFiles);
        qr = queryTest(q, maxResults, (int)expectedOneWordFiles, DISALLOWDIFF);

        q = "\"wordlength\">" + 1;
        q += " AND ";
        q += limitIteration;
        q += " AND ";
        q += thisTestRun;
        qr = queryTest(q, maxResults, NORESULTSCHECK, DISALLOWDIFF);

        //
        // Expect 0 results from non-overlapping range
        //
        q = "\"wordlength\">" + 2;
        q += " AND ";
        q += "\"wordlength\"<" + 2;
        qr = queryTest(q, maxResults, 0, DISALLOWDIFF);

        //
        // Queries for first = and != A should be total stored
        //
        printInfo("Testing query: 'first = A' and 'first != A'. " +
                  "Expected number of results = " + (int)iterationNum);

        qList.clear();

        q = "\"first\"='A'";
        q += " AND ";
        q += limitIteration;
        q += " AND ";
        q += thisTestRun;
        qList.add(q);

        // Not equals can be expressed as != or <>. Testing !=.
        q = "\"first\"!='A'";
        q += " AND ";
        q += limitIteration;
        q += " AND ";
        q += thisTestRun;
        qList.add(q);

        queryTotalTest(qList, maxResults, (int)iterationNum);

        //
        // Queries for first = and <> A should be total stored
        //
        printInfo("Testing query: 'first = A' and 'first <> A'. " +
                  "Expected number of results = " + (int)iterationNum);

        qList.clear();

        q = "\"first\"='A'";
        q += " AND ";
        q += limitIteration;
        q += " AND ";
        q += thisTestRun;
        qList.add(q);

        // Not equals can be expressed as != or <>. Testing <>.
        q = "\"first\"<>'A'";
        q += " AND ";
        q += limitIteration;
        q += " AND ";
        q += thisTestRun;
        qList.add(q);

        queryTotalTest(qList, maxResults, (int)iterationNum);

        //
        // Queries from < > and = 0.5 should sum to total stored
        //
        qList.clear();

        q = "\"doublechanged\"<0.5";
        q += " AND ";
        q += limitIteration;
        q += " AND ";
        q += thisTestRun;
        qList.add(q);

        q = "\"doublechanged\">0.5";
        q += " AND ";
        q += limitIteration;
        q += " AND ";
        q += thisTestRun;
        qList.add(q);

        q = "\"doublechanged\"=0.5";
        q += " AND ";
        q += limitIteration;
        q += " AND ";
        q += thisTestRun;
        qList.add(q);

        queryTotalTest(qList, maxResults, (int)iterationNum);

        //
        // Query with ()
        //
        q = "(\"wordlength\"=1";
        q += " OR ";
        q += "\"wordlength\"=2";
        q += " OR ";
        q += "\"wordlength\"=3";
        q += " OR ";
        q += "\"wordlength\"=4";
        q += " OR ";
        q += "\"wordlength\"=5";
        q += " OR ";
        q += "\"wordlength\"=6)";
        q += " AND ";
        q += limitIteration;
        q += " AND ";
        q += thisTestRun;
        qr = queryTest(q, maxResults, (int)iterationNum, DISALLOWDIFF);

        //
        // Query with OR clauses
        // 
        q = "\"wordlength\"=6";
        q += " OR ";
        q += "\"wordlength\"=5";
        q += " OR ";
        q += "\"wordlength\"=4";
        q += " OR ";
        q += "\"wordlength\"=3";
        q += " OR ";
        q += "\"wordlength\"=2";
        q += " OR ";
        q += "\"wordlength\"=1";
        // This test is a bit slow to be run in the regression test
        if (!regressionMode) {
            qr = queryTest(q, maxResults, (int)iterationNum, EXPECTMINIMUM);
        } else {
            printInfo("Skipping long running query " + q + " in regression mode");
        }
    }

    // Calls both query and selectUnique (on oid field) and expects the
    // results will be the same for both.
    public static QueryResult queryTest(String q, int maxResults,
        int expectResults, int resultRequirements)
        throws Throwable {
        int queryRes = 0;
        // If we are just verifying a minimum number of results
        // then we skip the results check for the query but verify
        // we got at least the expected minimum later
        int qExpectResults = (resultRequirements == EXPECTMINIMUM ?
            NORESULTSCHECK : expectResults);

        // What is the maximum results we'd expect--need this until
        // cookies work
        int maxExpectedResults = (qExpectResults > expectResults ?
            qExpectResults : expectResults);

        // XXX need cookies for query
        if (maxResults < maxExpectedResults) {
            printError("XXX Cookies aren't supported yet, so can't give " +
                "maxResults of " + maxResults + " and expected results of " +
                maxExpectedResults + ".  Will re-adjust maxResults to be " +
                maxExpectedResults + " and allow test to continue");

            maxResults = maxExpectedResults;
        }

        QueryResult qr = query(q, maxResults, qExpectResults);

        queryRes = qr.num;


        // Things are different in delete mode...we expect 0 results
        if (deleteMode) {
            if (resultRequirements == EXPECTMINIMUM ||
                resultRequirements == NORESULTSCHECK) {
                printInfo("Can't validate queryTest results after delete " +
                    "for this query; assuming " + queryRes + " results okay");
            } else if (queryRes > 0) {
                printError("ERROR: after delete, queryTest " + q +
                    " returned more than 0 objects");
                die();
            } else {
                printInfo("As expected after delete, queryTest returned " +
                    "0 objects");
            }
            return (qr);
        }

        // Did we get the number of results we expected?
        if (expectResults != NORESULTSCHECK &&
            resultRequirements != EXPECTMINIMUM &&
            expectResults != queryRes) {
            printError("ERROR: Got " + queryRes + ", expected " +
                expectResults + ", results for unique and non-unique query " +
                q + " with max " + maxResults + "\n");
            die();
        } else if (resultRequirements == EXPECTMINIMUM &&   
                   queryRes < expectResults) {
            printError("ERROR: Got " + queryRes + " for query" +
                ", expected at least " +
                expectResults + " results for unique and non-unique query " +
                q + " with max " + maxResults + "\n");
            die();
        } else if (resultRequirements == EXPECTMINIMUM &&
                   queryRes >= expectResults ) {
            printInfo("Got " + queryRes + " for query" +
                ", expected at least " +
                expectResults + " results for unique and non-unique query " +
                q + " with max " + maxResults + "\n");
        } else if (expectResults != NORESULTSCHECK) {
            printInfo("Got " + queryRes + " as expected for unique and " +
                "non-unique query " + q + " with max " + maxResults + "\n");
        } else {
            printInfo("Got " + queryRes + " OIDs from query " + q + 
                " with max " + maxResults + " (no expectation on results)\n");
        }

        return (qr);
    }

    static class SortEntry 
        implements Comparable {
        static final int ORIGIN_OID_LIST        = 1;
        static final int ORIGIN_STRING_LIST     = 2;

        String oid;
        int origin;
        int position;

        SortEntry(String newOid,
                  int newOrigin,
                  int newPosition) {
            oid = newOid;
            origin = newOrigin;
            position = newPosition;
        }

        public int compareTo(Object o) {
            if (!(o instanceof SortEntry)) {
                printInfo("Unexpected comparison between a SortEntry object and a "+
                          o.getClass().getName()+" object");
                return(-1);
            }

            return(((SortEntry)o).oid.compareTo(oid));
        }

        public String toString() {
            StringBuffer result = new StringBuffer();

            result.append("Object id : "+oid+"\n");
            result.append("Origin : ");
            switch (origin) {
            case ORIGIN_OID_LIST:
                result.append("OID_LIST");
                break;

            case ORIGIN_STRING_LIST:
                result.append("STRING_LIST");
                break;
            }
            result.append("\n");
            result.append("Position : "+position);

            return(result.toString());
        }
    }

    static class ElemSort {
        int firstOffset;
        int lastOffset;

        ElemSort(int newFirstOffset,
                 int newLastOffset) {
            firstOffset = newFirstOffset;
            lastOffset = newLastOffset;
        }
    }

    private static void elemSort(SortEntry[] entries,
                                 ElemSort elem,
                                 ArrayList operations) {
        SortEntry buffer;

        if (elem.lastOffset - elem.firstOffset <= 0) {
            return;
        }

        if (elem.lastOffset - elem.firstOffset == 1) {
            if (entries[elem.firstOffset].compareTo(entries[elem.lastOffset]) > 0) {
                buffer = entries[elem.firstOffset];
                entries[elem.firstOffset] = entries[elem.lastOffset];
                entries[elem.lastOffset] = buffer;
            }
        }

        int pivotOffset = (elem.lastOffset+elem.firstOffset)/2;

        for (int i=0; i<pivotOffset; i++) {
            if (entries[i].compareTo(entries[pivotOffset]) > 0) {
                if (pivotOffset == i+1) {
                    buffer = entries[pivotOffset];
                    entries[pivotOffset] = entries[i];
                    entries[i] = buffer;
                } else {
                    buffer = entries[pivotOffset];
                    entries[pivotOffset] = entries[i];
                    entries[i] = entries[pivotOffset-1];
                    entries[pivotOffset-1] = buffer;
                    i--;
                }
                pivotOffset--;
            }
        }

        for (int i=pivotOffset+1; i<=elem.lastOffset; i++) {
            if (entries[i].compareTo(entries[pivotOffset]) < 0) {
                if (pivotOffset+1 == i) {
                    buffer = entries[pivotOffset];
                    entries[pivotOffset] = entries[i];
                    entries[i] = buffer;
                } else {
                    buffer = entries[pivotOffset];
                    entries[pivotOffset] = entries[i];
                    entries[i] = entries[pivotOffset+1];
                    entries[pivotOffset+1] = buffer;
                    i--;
                }
                pivotOffset++;
            }
        }
        
        if (pivotOffset-1 > elem.firstOffset) {
            operations.add(new ElemSort(elem.firstOffset, pivotOffset-1));
        }
        if (elem.lastOffset > pivotOffset+1) {
            operations.add(new ElemSort(pivotOffset+1, elem.lastOffset));
        }
    }

    private static void sort(SortEntry[] entries) {
        ArrayList operations = new ArrayList();
        operations.add(new ElemSort(0, entries.length-1));

        while (operations.size() > 0) {
            ElemSort elem = (ElemSort)operations.remove(0);
            elemSort(entries, elem, operations);
        }
    }

    // Takes a list of queries whose total results should be expectResults
    public static void queryTotalTest(ArrayList qList, int maxResults,
        int expectResults) throws Throwable {
        QueryResult qr = null;
        int totalRes = 0;

        // XXX need cookies for query
        if (maxResults < expectResults) {
            printError("XXX Cookies aren't supported yet, so can't give " +
                "maxResults of " + maxResults + " and expected results of " +
                expectResults + ".  Will re-adjust maxResults to be " +
                expectResults + " and allow test to continue");

            maxResults = expectResults;
        }


        if (expectResults == NORESULTSCHECK) {
            printError("Can't call queryTotalTest without expected results");
            die();
        }

        Iterator i = qList.iterator();
        while (i.hasNext()) {
            String q = (String)i.next();
            qr = queryTest(q, maxResults, NORESULTSCHECK, DISALLOWDIFF);
            totalRes += qr.num;
        }

        // Things are different in delete mode...we expect 0 results
        if (deleteMode) {
            if (totalRes != 0) {
                printError("ERROR: after delete, query returned " +
                    totalRes + " total results, should have been 0");
                die();
            } else {
                printInfo("As expected after delete, query total test " +
                    "returned 0 objects");
            }
            return;
        }

        if (totalRes != expectResults) {
            printError("ERROR: Got " + totalRes + " results, expected " +
                expectResults + " TOTAL results for the queries " + qList);
            die();
        } else {
            printInfo("Got " + totalRes + " TOTAL results as expected for " +
                "the queries " + qList + "\n");
        }
    }

    public static class QueryResult {
        public QueryResultSet qrs = null;
        public int num = -1;
        public QueryResult() {
        }
    }

    public static QueryResult query(String q, int maxResults,
        int expectResults) throws Throwable {
        QueryResult qr = new QueryResult();
        CmdResult cr = null;
        long startTime = 0;
        long elapsedTime = 0;

        printDate("\nExecuting query " + q + " with maxResults " +
            maxResults + " at ");

        try {
            if (reinitConnection) {
               htc = new HoneycombTestClient(serverToUse);
            }
            startTime = TestLibrary.msecsNow();
            cr = htc.query(q, maxResults);
            elapsedTime = TestLibrary.msecsNow() - startTime;
            qr.qrs = (QueryResultSet)cr.rs;
            qr.num = htc.queryResultCount(cr);
        } catch (HoneycombTestException e) {
            printError("ERROR: failed to query " + q + ": " + e.getMessage());
            e.printStackTrace();
            die();
        } catch (Throwable e) {
            printError("ERROR: failed to query " + q +
                " (unexpected exception): " + e.toString());
            e.printStackTrace();
            die();
        }

        // Things are different in delete mode...we expect 0 results
        if (deleteMode) {
            if (expectResults == EXPECTMINIMUM ||
                expectResults == NORESULTSCHECK) {
                printInfo("Can't validate query results after delete " +
                    "for this query; assuming " + qr.num + " results okay");
            } else if (qr.num != 0) {
                printError("ERROR: after delete, query returned " +
                    qr.num + " results, should have been 0");
                die();
            } else {
                printInfo("As expected after delete, query returned 0 objects");
            }
            return(qr);
        }

        if (expectResults != NORESULTSCHECK && expectResults != qr.num) {
            printError("ERROR: expected " + expectResults +
                " results but got " + qr.num + " for query " + q + "\n");
            // XXX This might generate a ton of output, but for debugging
            // during development, this is useful and I'll leave it in
            int i = 0;
            while (qr.qrs.next()) {
                if (i++ == expectResults) {
                    printDebug("--- extra results ---");
                }
                printDebug("[" + i + "]" + qr.qrs.getObjectIdentifier());
            }
            die();
        } else if (expectResults != NORESULTSCHECK) {
            printDebug("Got " + qr.num +
                " results as expected for query " + q +
                " with maxResults " + maxResults +
                " in " + elapsedTime + " msecs\n");
        } else {
            printDebug("Got " + qr.num + " results from query " + q + 
                " with maxResults " + maxResults +
                " in " + elapsedTime + " msecs\n");
        }

        return(qr);
    }

    public static HashMap getMetadata(String oid)
        throws HoneycombTestException {
        HashMap omd = null;
        CmdResult cr = null;

        printDebug("Trying to get metadata for " + oid);
        try {
            if (reinitConnection) {
               htc = new HoneycombTestClient(serverToUse);
            }
            cr = htc.getMetadata(oid);
            omd = cr.mdMap;
            printDebug("Got the following metadata for " + oid);
            printDebug(omd.toString() + "\n");
         } catch (HoneycombTestException e) {
             printError("ERROR: failed to get metadata for " + oid + ": " +
                 e.getMessage());
             e.printStackTrace();
             die();
        } catch (Throwable e) {
            printError("ERROR: failed to get metadata for " + oid +
                " (unexpected exception): " + e.toString());
            e.printStackTrace();
            die();
        }

        return (omd);
    }


    /**
     * Stores a file and returns the FileID for the given file
     * upon successful upload.  Also does the requested verification.
     */
    public static FileID storeFile(String fileToStore)
        throws IOException, HoneycombTestException {
        FileID storeResult;
        String computedHash = null;
        long iterationStartTime = 0;
        long iterationEndTime = 0;

        // Calculate expected Hash of file locally before passing
        // to server so we can verify the server gives us back the
        // expected ID
        if (verifyHash) {
            computedHash = TestLibrary.getHash(fileToStore);
        }

        do {
            // We purposely reset the time if we retry
            iterationStartTime = TestLibrary.msecsNow();

            // Do the store using the chosen method
            if (useCurl) {
                storeResult = storeFileWithCurl(fileToStore);
            } else if (useNFS) {
                storeResult = storeFileWithNFS(fileToStore, computedHash);
            } else {
                storeResult = storeFileWithAPI(fileToStore);
            }

            iterationEndTime = TestLibrary.msecsNow();

            // XXX until we get Hash from API, we'll add it ourselves if we've
            // computed it already
            if (storeResult.hash == null && computedHash != null) {
                printInfo("XXX Manually adding Hash to FileID since we " +
                    "didn't get it as a return from store");
                storeResult.hash = computedHash;
            }

            // Make sure we get back the right thing from store
            if (!storeResult.validate()) {
                printError("ERROR: store returned invalid OID: " + storeResult);
                storeResult = null; // enable the retry code
            }
        } while (storeResult == null && shouldRetry(false));

        if (storeResult == null) {
            printError("ERROR: failed to store file " + fileToStore);
            printError("Verify that servers " + server + " are up");
            die();
        }

        long thisIterationTime = iterationEndTime - iterationStartTime;
        totalStoreTime += thisIterationTime; 
        long fsize = TestLibrary.fileSize(fileToStore);
        storedBytes += fsize;

        // The calc_rate script parses this string.  Don't change it.
        printInfo("Received OID from store: " + storeResult.toFileString() +
            ". Total bytes stored " + storedBytes + ", total msecs " +
            totalStoreTime + " (" + fsize + " bytes, " +
            thisIterationTime + " msecs this store, server " + serverToUse +
            ")");

        // Verify the content 
        // XXX this isn't meaningful until API returns the Hash!
        if (verifyHash && storeResult.hash == null) {
            printError("Can't verify Hash during store.  Hash not set.");
        } else if (verifyHash && !computedHash.equals(storeResult.hash)) {
            printError("ERROR: Mismatch between Hash returned from " + 
                "cluster for file " + fileToStore);
            printError("Hash returned from server: " + storeResult.hash);
            printError("Hash computed locally:     " + computedHash);
            die();
        }

        // Check if we can retrieve the file we just stored
        String retrieveFilename = uploadFilename + STRINGFORSTORERETRIEVE;
        if (doRetrieveAfterStore) {
            retrieveAfterStore(storeResult, fileToStore, retrieveFilename);
        }

        // See what happens if we store this file again.  We'll no longer
        // get ObjectExists...We should get the same Hash, but diff OID.
        if (doReStore) {
            reStoreFile(fileToStore, storeResult);
            if (doRetrieveAfterStore) {
                reStoreFile(retrieveFilename, storeResult);
            }
        }

        return (storeResult);
    }

    /**
     * Uses API instead of curl for store.  This method can return a reset()
     * FileID to indicate error.  This routine should not return null.
     */
    public static FileID storeFileWithAPI(String fileToStore) {
        SystemRecord sysRec = null;
        CmdResult cr = null;
        FileID storeResult = new FileID();

        try {
            if (reinitConnection) {
               htc = new HoneycombTestClient(serverToUse);
            }
            if (useEMD) {
                printDebug("Using API to store " + fileToStore +
                    " with EMD\n" + emdHM);
                cr = htc.store(fileToStore, emdHM);
            } else {
                printDebug("Using API to store (no EMD) " + fileToStore);
                cr = htc.store(fileToStore, null);
            }

            sysRec = cr.sr;

            if (sysRec != null) {
                storeResult.oid = sysRec.getObjectIdentifier();

                String dataHash =
                    ByteArrays.toHexString(sysRec.getDataDigest());

                storeResult.hash = dataHash;

                printInfo("Got data hash from API: " + dataHash);
                printInfo("Got oid from API: " + storeResult.oid);
            }

            printInfo("Store API returned SystemRecord: " + sysRec);
            printInfo("Store API returned ID: " + storeResult);
        } catch (HoneycombTestException e) {
            printError("ERROR: failed to store file: " + e.getMessage());
            e.printStackTrace();
        } catch (Throwable e) {
            printError("ERROR: failed to store file (unexpected exception): " +
                e.toString());
            e.printStackTrace();
        }

        return (storeResult);
    }

    /**
     * Uses curl instead of the public API for store.  This method can return a
     * reset() FileID to indicate error.  This routine should not return null.
     */
    public static FileID storeFileWithCurl(String fileToStore)
        throws IOException, HoneycombTestException {
        // Simulate client load balancing with curl
        serverToUse = TestLibrary.pickRandom(servers);
        String storeCmd = "curl -s -S http://" + serverToUse + ":" + port + 
            "/store  -T " + fileToStore;

        if (useEMD) {
            printInfo("Warning: In curl mode, EMD is not passed");
        }

        printInfo("Store Command: " + storeCmd);

        // XXX Eventually, change invokeCmd to return stderr and stdout
        // so we can use that here, too.  But we need stdout now...
        Process p = null;
        BufferedReader br = null;
        BufferedReader err = null;

        try {
            p = Runtime.getRuntime().exec(storeCmd);
            br = new BufferedReader(
                new InputStreamReader(p.getInputStream()));
            err = new BufferedReader(
                new InputStreamReader(p.getErrorStream()));
        } catch (Throwable e) {
            printError("ERROR: failed to invoke curl: " +
                e.getMessage());
            e.printStackTrace();
            die();
        }

        String line = null;
        FileID storeResult = new FileID();
        boolean error = false;

        while ((line = br.readLine()) != null) {
            if (storeResult.oid != null) {
                // We shouldn't get more than one line. We expect one line
                // that is the OID
                printError("ERROR: Read multiple lines of store output:");
                printError((storeResult.oid).toString());
                printError(line);
                while ((line = br.readLine()) != null) {
                    printError(line);
                }
                error = true; // die later, but print stderr first
                break;
            }

            // Take the line representing this store and create
            // an object identifier from it
            // XXX this assumes NewObjectIdentifier toString doesn't
            // include newlines!
            printDebug("Store via curl returned " + line);
            try {
                storeResult.oid = new ObjectIdentifier(line);
            } catch (Throwable e) {
                printError("Unexpected excpetion when creating OID from \"" +
                    line + "\": " + e.getMessage()); 
                e.printStackTrace();
                error = true; // die later, but print stderr first
                break;
            }
        }

        // print stderr to help understand the problem
        if (error || storeResult.oid == null) {
            String stdoutString;
            while ((stdoutString = br.readLine()) != null) {
                printError("stdout: " + stdoutString);
            }

            String errString;
            while ((errString = err.readLine()) != null) {
                printError("stderr: " + errString);
            }
        }
        br.close();
        err.close();

        boolean finished = false;
        while (!finished) {
            try {
                p.waitFor();
                finished = true;
            } catch (InterruptedException e) {
                printInfo("Interrupted: " + e.getMessage()); 
            }
        }

        // If there was an error, return an invalid OID
        if (error) {
            printError("ERROR: Store failed with curl.  OID received: " +
                storeResult);
            printError("Resetting store result to trigger retry if configured");
            storeResult.reset();
        }

        return (storeResult);
    }

    /**
     * Uses NFS instead of the public API for store.  This method can return a
     * reset() FileID to indicate error.  This routine should not return null.
     */
    public static FileID storeFileWithNFS(String fileToStore, String hash)
        throws IOException, HoneycombTestException {
        // XXX what options to copy?  -p? 
        String nfsDirToUse = getNFSDir();
        String fileHash = hash;
        if (fileHash == null) {
            fileHash = TestLibrary.getHash(fileToStore);
        }
        
        // XXX what filename to use?  Currently it is the file hash
        String copyTarget = nfsDirToUse + File.separatorChar + fileHash;
        String storeCmd = "cp " + fileToStore + " " + copyTarget;

        FileID storeResult = new FileID();
        storeResult.hash = fileHash; // XXX will NFS "return" this somehow?
        storeResult.nfsPath = copyTarget; // XXX we don't have OIDs...

        printInfo("Store Command: " + storeCmd);

        if (!invokeSystemCmd(storeCmd)) {
            // An error occured during store, so reset the result
            // to indicate a failure occured.
            storeResult.reset();
        }

        return (storeResult);
    }

    /**
     * Retrieves the file for the given OID
     */
    public static void retrieveFile(FileID storeFID, String fileToRetrieve)
        throws IOException, HoneycombTestException {

        long iterationStartTime = 0;
        long iterationEndTime = 0;
        boolean succeeded = false;

        do {
            // We purposely reset the time if we retry
            iterationStartTime = TestLibrary.msecsNow();

            if (useCurl) {
                succeeded = retrieveFileWithCurl(storeFID, fileToRetrieve);
            } else if (useNFS) {
                succeeded = retrieveFileWithNFS(storeFID, fileToRetrieve);
            } else {
                succeeded = retrieveFileWithAPI(storeFID, fileToRetrieve);
            }

            // if we've deleted the file, we don't expect this to succeed
            if (deleteMode) {
                break;
            }

            iterationEndTime = TestLibrary.msecsNow();
        } while (!succeeded && shouldRetry(false));

        if (deleteMode) {
            if (succeeded) {
                printError("ERROR: we retrieved " + storeFID + " but we " +
                    "should have failed because it should have been deleted");
                die();
            } else {
                printInfo("As expected, failed to retrieve deleted object " +
                    storeFID + " since it should have been deleted");
            }
            return;
        }

        // We only retry for server exceptions.  If we get back a corrupt
        // file below, we don't want to ignore it.
        if (!succeeded) {
            printError("ERROR: failed to retrieve file: " + storeFID);
            die();
        }


        long thisIterationTime = iterationEndTime - iterationStartTime;
        totalRetrieveTime += thisIterationTime; 
        long fsize = TestLibrary.fileSize(fileToRetrieve);
        retrievedBytes += fsize;

        // The calc_rate script parses this string.  Don't change it.
        printInfo("Retrieved file with OID " + storeFID.toFileString() +
            ". Total bytes retrieved " + retrievedBytes + ", total msecs " +
            totalRetrieveTime + " (" + fsize + " bytes, " +
            thisIterationTime + " msecs this retrieve, server " + serverToUse +
            ")");

        if (verifyHash) {
            // Get the Hash of the file we just retrieve and see if it
            // matches the Hash of the file we asked to retrieve
            String retrievedHash = TestLibrary.getHash(fileToRetrieve);
            if (storeFID.hash == null) {
                printError("Can't verify Hash during retrive.  Hash not set.");
            } else if (!retrievedHash.equals(storeFID.hash)) {
                printError("ERROR: Hash is different for retrieved file.");
                printError("Hash we asked to retrieve: " + storeFID.hash);
                printError("Hash computed from file: " + retrievedHash);
                die();
            }
        }

        if (doReStore) {
            reStoreFile(fileToRetrieve, storeFID);
        }
    }

    /**
     * Retrieve a file using the API instead of the curl.
     */
    public static boolean retrieveFileWithAPI(FileID storeFID,
        String fileToRetrieve) throws HoneycombTestException {

        printInfo("Retrieving object " + storeFID.toFileString() +
            " to " + fileToRetrieve);

        // XXX future:  try retrieving with certain fields from OID missing
        try {
            if (reinitConnection) {
               htc = new HoneycombTestClient(serverToUse);
            }
            if (testRangeRetrieve) {
                // XXX future
                // - get md to learn size
                // - pick random number of chunks to read file in
                // - properly handle last bytes
                htc.retrieve((storeFID.oid).toString(), fileToRetrieve);
            } else {
                htc.retrieve((storeFID.oid).toString(), fileToRetrieve);
            }
        } catch (HoneycombTestException e) {
            printError("ERROR: failed to retrieve object: " + e.getMessage());
            e.printStackTrace();
            return (false);
        } catch (Throwable e) {
            printError("ERROR: failed to retrieve file " +
                "(unexpected exception): " + e.toString());
            e.printStackTrace();
            return (false);
        }

        return (true);
    }

    /**
     * Retrieve a file using curl instead of the client API.
     */
    public static boolean retrieveFileWithCurl(FileID storeFID,
        String fileToRetrieve) throws IOException, HoneycombTestException {
        // XXX check for error from curl command
        // We currently don't distinguish getting an error back
        // and getting the content back, though we detect it by
        // computing the hash of the expected contents.

        // Simulate client load balancing with curl
        String serverToUse = TestLibrary.pickRandom(servers);
        String retrieveCmd = "curl -s -o " + fileToRetrieve + " http://" + 
            serverToUse + ":" + port + "/retrieve?id=" + storeFID.oid;
        printInfo("Retrieve Command: " + retrieveCmd);

        return (invokeSystemCmd(retrieveCmd));
    }

    /**
     * Retrieve a file using NFS instead of the client API.
     */
    public static boolean retrieveFileWithNFS(FileID storeFID,
        String fileToRetrieve) throws IOException, HoneycombTestException {
        // We currently don't distinguish getting an error back
        // and getting the content back, though we detect it by
        // computing the hash of the expected contents.

        // XXX cp options?
        String retrieveCmd = "cp " + storeFID.nfsPath + " " + fileToRetrieve;
        printInfo("Retrieve Command: " + retrieveCmd);

        return (invokeSystemCmd(retrieveCmd));
    }

    /**
     * Deletes the file for the given OID
     */
    public static void deleteFile(FileID storeFID)
        throws IOException, HoneycombTestException {
        long iterationStartTime = 0;
        long iterationEndTime = 0;
        boolean succeeded = false;

        do {
            // We purposely reset the time if we retry
            iterationStartTime = TestLibrary.msecsNow();

            if (useCurl) {
                // succeeded = deleteFileWithCurl(storeFID);
                printError("ERROR: delete with curl is not yet implemented");
                die();
            } else if (useNFS) {
                succeeded = deleteFileWithNFS(storeFID);
            } else {
                succeeded = deleteFileWithAPI(storeFID);
            }

            iterationEndTime = TestLibrary.msecsNow();
        } while (!succeeded && shouldRetry(false));

        // We only retry for server exceptions.  If we get back a corrupt
        // file below, we don't want to ignore it.
        if (!succeeded) {
            printError("ERROR: failed to delete file: " + storeFID);
            die();
        }

        long thisIterationTime = iterationEndTime - iterationStartTime;
        totalDeleteTime += thisIterationTime; 
        // long fsize = TestLibrary.fileSize(fileToDelete);
        // deletedBytes += fsize;

        // The calc_rate script parses this string.  Don't change it.
        printInfo("Deleted file with OID " + storeFID.toFileString() +
            ". Total bytes deleted " + "0" + ", total msecs " +
            totalDeleteTime + " (" + "0" + " bytes, " +
            thisIterationTime + " msecs this delete, server " + serverToUse +
            ")");

        /* XXX
        if (tryRetrieveAfterDelete) {
        }
        */
    }

    /**
     * Delete a file using the client API.
     */
    public static boolean deleteFileWithAPI(FileID storeFID) {
        printInfo("Deleting object " + storeFID.oid);

        try {
            if (reinitConnection) {
               htc = new HoneycombTestClient(serverToUse);
            }
            htc.delete((storeFID.oid).toString());
        } catch (HoneycombTestException e) {
            // XXX detect and allow NoSuchObjectException for idempotency?
            //printInfo("Received NoSuchObjectException...considering success");
            printError("ERROR: failed to retrieve object: " + e.getMessage());
            e.printStackTrace();
            return (false);
        } catch (Throwable e) {
            printError("ERROR: failed to retrieve file " +
                "(unexpected exception): " + e.toString());
            e.printStackTrace();
            return (false);
        }

        printInfo("Successfully deleted object " + storeFID.oid);
        return (true);
    }

    /**
     * Delete a file using NFS instead of the client API.
     */
    public static boolean deleteFileWithNFS(FileID storeFID)
        throws IOException, HoneycombTestException {
        // XXX rm options?  use mv to delete and retrieve?
        String deleteCmd = "rm -f " + storeFID.nfsPath;
        printInfo("Delete Command: " + deleteCmd);

        boolean rc = invokeSystemCmd(deleteCmd);
        if (rc) {
            // verify it is really gone
            String lsCmd = "ls storeFID.nfsPath";

            // we expect the ls command to fail
            // and we return success if it did
            return(!invokeSystemCmd(lsCmd, true));
        }

        return (false);
    }



    /**
     * Routine to invoke a UNIX command.  Returns true if the invocation
     * succeeded.
     * XXX We should return stdout and stderr from this like in the dTET tests.
     */
    public static boolean invokeSystemCmd(String cmd, boolean allowFailure)
        throws IOException, HoneycombTestException {
        Process p = null;
        BufferedReader br = null;
        BufferedReader err = null;

        try {
            p = Runtime.getRuntime().exec(cmd);
            br = new BufferedReader(
                new InputStreamReader(p.getInputStream()));
            err = new BufferedReader(
                new InputStreamReader(p.getErrorStream()));
        } catch (Throwable e) {
            printError("ERROR: failed to invoke cmd \"" + cmd + "\": " +
                e.getMessage());
            e.printStackTrace();
            die();
        }

        // print stderr and stdout to help understand the problem
        String stdoutString;
        while ((stdoutString = br.readLine()) != null) {
            printError("stdout: " + stdoutString);
        }

        String errString;
        while ((errString = err.readLine()) != null) {
            printError("stderr: " + errString);
        }
        br.close();
        err.close();

        boolean finished = false;
        while (!finished) {
            try {
                p.waitFor();
                finished = true;
            } catch (InterruptedException e) {
                printInfo("Interrupted: " + e.getMessage()); 
            }
        }

        if (p.exitValue() != 0) {
            String s = (allowFailure ? "WARNING" : "ERROR");
            printError(s + ": exit status of \"" + cmd + "\" was " +
                p.exitValue());
            return (false);
        }

        return (true);
    }

    /**
     * Convenience wrapper around invoke that requires command success.
     */
    public static boolean invokeSystemCmd(String cmd)
        throws IOException, HoneycombTestException {
        return (invokeSystemCmd(cmd, false));
    }

    /**
     * Routine for re-storing a file after a store.  We no longer get
     * ObjectExists, but we can verify that the Hash is the same and the
     * OID is different.  This is for verification purpose only.
     */
    public static void reStoreFile(String fileToReStore, FileID storeFID)
        throws IOException, HoneycombTestException {
        FileID reStoreResult;
        if (useCurl) {
            reStoreResult = storeFileWithCurl(fileToReStore);
        } else if (useNFS) {
            // XXX Currently not supported
            reStoreResult = null;
            printError("It is not supported to reStore using NFS");
            die();
        } else {
            // XXX change this to use exceptions from API
            reStoreResult = storeFileWithAPI(fileToReStore);
        }

        // Verify Hash is the same
        if ((reStoreResult.hash).equals(storeFID.hash)) {
            printDebug("Hash is the same as expected after re-store");
        } else {
            printError("ERROR: file was re-uploaded with different Hash");
            printError(fileToReStore + " was stored originally with Hash " +
                storeFID.hash);
            printError(fileToReStore + " was stored again with Hash " +
                reStoreResult.hash);
            die();
        }

        // Verify OID is different
        if ((reStoreResult.oid).equals(storeFID.oid)) {
            printError("ERROR: file was re-uploaded with same oid");
            printError(fileToReStore + " was stored originally with OID " +
                storeFID.oid);
            printError(fileToReStore + " was stored again with OID " +
                reStoreResult.oid);
            die();
        } else {
            printDebug("OID is different as expected after re-store");
        }
    }

    /**
     * Retrieve the file immediate after the store to make sure it is there.
     * This is for verification purpose only.
     */
    public static void retrieveAfterStore(FileID storeFID,
        String originalFile, String retrievedFile)
        throws IOException, HoneycombTestException { 

        boolean succeeded = false;

        // XXX NFS?
        if (useCurl) {
            succeeded = retrieveFileWithCurl(storeFID, retrievedFile);
        } else if (useNFS) {
            // XXX Currently not supported
            printError("It is not supported to retreiveAfterStore using NFS");
            die();
        } else {
            succeeded = retrieveFileWithAPI(storeFID, retrievedFile);
        }

        // We don't retry here yet.  We can add this later, but
        // this isn't a commonly hit case
        if (!succeeded) {
            printError("ERROR: failed to retreive " + storeFID +
                " after store");
            die();
        }

        if (!TestLibrary.isEquivalent(originalFile, retrievedFile)) {
            printError("ERROR: Files " + originalFile + " and " + 
                retrievedFile + " differ");
            die();
        }
    }

    /**
     * Constrain double per hadb limitations.
     */
    public static Double getDoubleValue(double d) {

        if (d < HoneycombTestConstants.HC_MIN_DOUBLE) {
            d = HoneycombTestConstants.HC_MIN_DOUBLE;
        } else if (d > HoneycombTestConstants.HC_MAX_DOUBLE) {
            d = HoneycombTestConstants.HC_MAX_DOUBLE;
        }

        return (new Double(d));
    }

    /**
     * Initialize the EMD
     */
    public static void initEMD() throws IOException, HoneycombTestException {
        emdHM = new HashMap();

        emdHM.put("stringorigargs", invocationArgs);
        emdHM.put("filename", uploadFilename);
        // XXX Bug 745: means we must use strings instead of other Objs.
        emdHM.put("fileorigsize", new Long(originalSize));
        emdHM.put("filecurrsize", new Long(originalSize));
        emdHM.put("iteration", new Long(0l));
        emdHM.put("prevSHA1", (String)EMD_NULL);
        emdHM.put("timestart", new Long(TestLibrary.msecsNow()));
        emdHM.put("timenow", new Long(TestLibrary.msecsNow()));

        rand = new Random();
        emdHM.put("doublefixed", getDoubleValue(rand.nextDouble()));
        emdHM.put("doublenegative", getDoubleValue(rand.nextDouble() * -1));
        emdHM.put("doublechunked", getDoubleValue(rand.nextDouble()));
        emdHM.put("doublechanged", getDoubleValue(0.5d));
        emdHM.put("doublesmall", getDoubleValue(Double.MIN_VALUE));
        emdHM.put("doublelarge", getDoubleValue(Double.MAX_VALUE));

	//--> Blob support removed
        // XXX what to do for blobs?
//         emdHM.put("blobfixed", Double.toString(rand.nextDouble()));
//         emdHM.put("blobnegative", Double.toString(rand.nextDouble() * -1));
//         emdHM.put("blobchunked", Double.toString(rand.nextDouble()));
//         emdHM.put("blobchanged", Double.toString(rand.nextDouble()));
//         emdHM.put("blobsmall", Double.toString(Double.MIN_VALUE));
//         emdHM.put("bloblarge", Double.toString(Double.MAX_VALUE));

        emdHM.put("longsmall", new Long(Long.MIN_VALUE));
        emdHM.put("longlarge", new Long(Long.MAX_VALUE));

        if (createNewFile) {
            emdHM.put("initchar", Character.toString(initChar));
        } else {
            emdHM.put("initchar", EMD_UNDEFINED);
        }

        InetAddress ia = InetAddress.getLocalHost();
        uploadClient = ia.getHostAddress();
        emdHM.put("client", uploadClient);
        
        currWord = new StringBuffer();
        updateWordInMap();

        emdHM.put("stringspaces", currWord + " " + currWord);
        // XXX add / after bug 822
        emdHM.put("stringweirdchars",
            "Not used yet!  But why not? *(){}[].,<>?\\|%^&; OK.");
            // XXX Bug 6314228: xml escaping of metadata values is not robust
            // for all strings
            //"Some weird chars: \\\" \\a*(){}[].,\\<<>/?\\|\"\'~`+!@\\//#$-_%^&;");

        emdHM.put("stringlarge", EMD_UNDEFINED);
        String s = emdHM.toString();
        if(s.length() > HC_MAX_MD_STRLEN) {
            s = s.substring(0, HC_MAX_MD_STRLEN);
        }
        emdHM.put("stringlarge", s);

        writeEMDToFile();
    }

    public static void updateEMD(FileID storeFID)
        throws IOException, HoneycombTestException {
        getNextWord(); // sets currWord
        updateWordInMap();

        emdHM.put("iteration", new Long(currIteration));
        emdHM.put("timenow", new Long(TestLibrary.msecsNow()));
        emdHM.put("prevSHA1",
            (storeFID.oid == null ? EMD_NULL : storeFID.oid.toString()));
            // XXX new field

        double d;
        emdHM.put("doublechanged", getDoubleValue(rand.nextDouble()));
        emdHM.put("doublenegative", getDoubleValue(rand.nextDouble() * -1));
        d = hmGetDouble(emdHM, "doublesmall");
        // XXX these grow to fast! same with blob below
        emdHM.put("doublesmall", getDoubleValue(d * 2));
        Double dd = (Double)emdHM.get("doublelarge");
        dd = getDoubleValue(dd.doubleValue() / 2);
        emdHM.put("doublelarge", dd);

	// --> BLOB support removed
//         emdHM.put("blobchanged", rand.nextDouble());
//         emdHM.put("blobnegative", rand.nextDouble() * -1);
//         d = emdHM.getDouble("blobsmall");
//         emdHM.put("blobsmall", Double.toString(d * 2));
//         d = Double.parseDouble((String)emdHM.get("bloblarge"));
//         emdHM.put("bloblarge", Double.toString(d / 2));

        Long ll = (Long) emdHM.get("longsmall");
        ll = new Long(ll.longValue()+1);
        emdHM.put("longsmall", ll);
        ll = (Long) emdHM.get("longlarge");
        ll =  new Long(ll.longValue() - 1);
        emdHM.put("longlarge", ll);

        // only change these some of the time
        if (currIteration % 100 == 0) { // XXX make 100 a var?
            emdHM.put("doublechunked", getDoubleValue(rand.nextDouble()));
            //emdHM.put("blobchunked", rand.nextDouble());
        }

        if (growFile) {
            emdHM.put("filecurrsize",
                new Long(TestLibrary.fileSize(uploadFilename)));
        }

        emdHM.put("stringspaces", currWord + " " + currWord);

        // Don't want this to keep growing
        emdHM.put("stringlarge", EMD_UNDEFINED);
        String s = emdHM.toString();
        if(s.length() > HC_MAX_MD_STRLEN) {
            s = s.substring(0, HC_MAX_MD_STRLEN);
        }
        emdHM.put("stringlarge", s);

        writeEMDToFile();
    }

    private static char typeID(Object o) throws HoneycombTestException {
        if (o instanceof String)
            return 's';
        if (o instanceof Long)
            return 'l';
        if (o instanceof Double)
            return 'd';
        if (o instanceof Byte)
            return 'b';
        throw new HoneycombTestException(
                         "map->nvr: unsupported type class=" + 
                                             o.getClass().getName());
    }

    public static void writeEMDToFile()
        throws IOException, HoneycombTestException {

        // XXX maybe it would be easier to just serialize
        //  a copy of the hashmap with the 'stringlarge' deleted
        StringBuffer emd = new StringBuffer();
        Set s = emdHM.entrySet();
        Iterator it = s.iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry) it.next();
            String field = (String) e.getKey();
            Object value = e.getValue();

            // we don't write the large string to the file because it is
            // redundant and consumes too much space
            if (field != null && field.equals("stringlarge")) {
                continue;
            }

            emd.append(field).append(EMDVALSEP).append(typeID(value));
            emd.append(EMDVALSEP).append(value.toString()).append(EMDENTRYSEP);
        }

        // a newline marks the end of the EMD
        emd.append("\n");

        if (emd.length() > EMDSIZE) {
            printError("ERROR: EMD is larger than expected.");
            printError("Expected less than " + EMDSIZE + " bytes but found " +
                emd.length());
            die();
        }

        // XXX also check for EMDENTRYSEP in the data fields/values?
        String emds = emd.toString();
        TestLibrary.replaceBytesInFile(emds, 0, uploadFilename);
        printDebug("EMD as a string in the file is\n" + emds);
    }

    public static HashMap readEMDFromFile()
        throws IOException, HoneycombTestException {
        HashMap hm = new HashMap();
        File f = new File(originalFilename);

        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "r");
        } catch (FileNotFoundException e) {
            printError("ERROR: File " + f + " not found");
            die();
        }

        String s = raf.readLine();
        String[] entries = s.split(EMDENTRYSEP);
        if (entries == null || entries.length <= 2) {
            printError("ERROR: failed to find complete EMD data in the file");
            printError("Make sure that -z was not specified during store");
            printError("EMD must be used in order to use -Q");
            die();
        }

        for (int i = 0; i < entries.length; i++) {
            String[] nv = entries[i].split(EMDVALSEP);
            if (nv.length < 1) {
                printError("ERROR: failed to parse EMD entry " + entries[i]);
                printError("Make sure that -z was not specified during store");
                printError("EMD must be used in order to use -Q");
                die();
            } else if (nv.length < 2) {
                hm.put(nv[0], ""); // XXX should this be UNDEFINED?
            } else if (nv.length != 3) {
                printError("ERROR: failed to parse EMD entry " + entries[i]);
                printError("Make sure that -z was not specified during store");
                printError("EMD must be used in order to use -Q");
                die();
            } else {
                // parse type
                if (nv[1].equals("s")) {
                    hm.put(nv[0], nv[2]);
                } else if (nv[1].equals("l")) {
                    hm.put(nv[0], Long.decode(nv[2]));
                } else if (nv[1].equals("d")) {
                    hm.put(nv[0], Double.valueOf(nv[2]));
                } else if (nv[1].equals("b")) {
                    hm.put(nv[0], Byte.decode(nv[2]));
                } else {
                    printError("ERROR: failed to parse EMD entry " + entries[i]);
                    printError("Make sure that -z was not specified during store");
                    printError("EMD must be used in order to use -Q");
                    die();
                }
            }
        }

        printDebug("Read the follow EMD from file " + originalFilename);
        printDebug("\t" + hm);
        return (hm);
    }

    // Avoid non-alpha characters.  This function is only called when we
    // know we can increase the character at the given position.
    public static void increaseCharAt(int index) throws HoneycombTestException {
        char curr = currWord.charAt(index);
        char next = (char)(curr + 1);

        // try to catch the error if we have a misconfig
        if (MINEMDCHAR != 'A' || MAXEMDCHAR != 'z') {
            printError("it is not supported currently to have values for " +
                "min/max char that are not A and z");
            die();
        }

        // verify assumption that we aren't asked to advanced past MAXEMDCHAR
        if (curr >= MAXEMDCHAR) {
            printError("Can't increase a character " + curr +
                " greater than " + MAXEMDCHAR);
            die();
        }

        // make sure that we are in the alpha range
        while (next > 'Z' && next < 'a') {
            next = (char)(next + 1);
        }

        currWord.setCharAt(index, next);
    }

    // This is a bit of a hack, but assuming A-z, we return the count of the
    // non-ascii chars in between, the chars we skip above.
    public static int nonAlphaChars() throws HoneycombTestException {
        // try to catch the error if we have a misconfig
        if (MINEMDCHAR != 'A' || MAXEMDCHAR != 'z') {
            printError("it is not supported currently to have values for " +
                "min/max char that are not A and z");
            die();
        }

        // ASCII 91-96 inclusive
        return (6);
    }

    public static void getNextWord() throws HoneycombTestException {
        int length = currWord.length();

        // Special case the initial word
        if (length == 0) {
            currWord.append(MINEMDCHAR);
            return;
        }

        // check if the last character can be increased
        if (currWord.charAt(length - 1) >= MAXEMDCHAR) {
            // Find an index before the last char that can be incremented.
            // If there are none, add a new char and reset all other chars.
            int j = length - 1;
            boolean found = false;
            while (j >= 0) {
                if (currWord.charAt(j) < MAXEMDCHAR) {
                    // we can just adjust this char
                    increaseCharAt(j);
                    found = true;
                    break;
                } else {
                    // reset the char at this index and keep looking
                    currWord.setCharAt(j, MINEMDCHAR);
                }
                j--;
            }
            if (!found) {
                // add a new letter
                currWord.append(MINEMDCHAR);
            }
        } else {
            increaseCharAt(length - 1); // increase the last char
        }
    }

    public static void updateWordInMap() {
        int length = currWord.length();
        emdHM.put("word", currWord.toString());
        emdHM.put("wordlength", new Long(length));

        if (length >= 1)
            emdHM.put("first", currWord.substring(0,1));
        else
            emdHM.put("first", EMD_UNDEFINED);

        if (length >= 2)
            emdHM.put("second", currWord.substring(1,2));
        else
            emdHM.put("second", EMD_UNDEFINED);

        if (length >= 3)
            emdHM.put("third", currWord.substring(2,3));
        else
            emdHM.put("third", EMD_UNDEFINED);

        if (length >= 4)
            emdHM.put("fourth", currWord.substring(3,4));
        else
            emdHM.put("fourth", EMD_UNDEFINED);

        if (length >= 5)
            emdHM.put("fifth", currWord.substring(4,5));
        else
            emdHM.put("fifth", EMD_UNDEFINED);
        
        if (length >= 6)
            emdHM.put("sixth", currWord.substring(5,6));
        else
            emdHM.put("sixth", EMD_UNDEFINED);
    }

    // Initialize the target directory
    public static void initNFSMode() throws HoneycombTestException {
        if (nfsBasePath == null) {
            printError("ERROR: -P is required to specify NFS path in NFS mode");
            die();
        }

        // XXX how to validate that we have a good, NFS path?
        File nfsBasePathFile = new File(nfsBasePath);
        if (!nfsBasePathFile.isDirectory()) {
            printError("ERROR: " + nfsBasePath + " is not a directory");
            die();
        }

        // Add a uniquifying dir with timestamp for this testrun
        //    /<nfspath>/05.11.04_18.03.44_initsize<initsize>/...
        Date d = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("MM.dd.yy'_'HH:mm:ss");
        nfsTestrunDir = nfsBasePath + File.separatorChar +
            sdf.format(d) + "_" + "InitialSize" + originalSize;
        nfsCurrDir = nfsTestrunDir + File.separatorChar + nfsCurrDirNum++;

        printDebug("Using " + nfsTestrunDir + " as base directory for this " +
            "testrun. First dir to write to is " + nfsCurrDir);

        File nfsTestrunDirFile = new File(nfsTestrunDir);
        if (!nfsTestrunDirFile.mkdir()) {
            printError("ERROR: Failed to mkdir " + nfsTestrunDirFile);
            die();
        }

        File nfsCurrDirFile = new File(nfsCurrDir);
        if (!nfsCurrDirFile.mkdir()) {
            printError("ERROR: Failed to mkdir " + nfsCurrDirFile);
            die();
        }
    }

    // Use the parameters for files per dir and dir depth to figure out
    // where to store the next file
    public static String getNFSDir() throws HoneycombTestException {
        if (++nfsCurrFilesPerDir > nfsMaxFilesPerDir) {
            nfsCurrFilesPerDir = 1; // We'll write the curr file to this dir

            // We need to create a new directory
            // Determine if we are at our maximum dir depth
            if (++nfsCurrDirDepth >= nfsMaxDirDepth) {
                // create new dir off the base of the test dir
                nfsCurrDir = nfsTestrunDir + File.separatorChar +
                    nfsCurrDirNum++;
                nfsCurrDirDepth = 0;
            } else {
                // create new dir as a subdir of the current directory
                nfsCurrDir = nfsCurrDir + File.separatorChar + nfsCurrDirNum++;
            }

            File nfsCurrDirFile = new File(nfsCurrDir);
            if (!nfsCurrDirFile.mkdirs()) {
                printError("ERROR: Failed to mkdir " + nfsCurrDirFile);
                die();
            } else {
                printDebug("Created dir " + nfsCurrDirFile +
                    ", nfsCurrFilesPerDir " + nfsCurrFilesPerDir +
                    ", nfsCurrDirDepth " + nfsCurrDirDepth);
            }
        } else {
            printDebug("No new directory needed, nfsCurrFilesPerDir " +
                nfsCurrFilesPerDir + ", nfsCurrDirDepth " + nfsCurrDirDepth);
        }

        return (nfsCurrDir);
    }

    /**
     * This is a class that contains data about files stored by this
     * program.
     */
    public static class FileID {
        public static ObjectIdentifier oid;
        public static String nfsPath; // XXX We can't get OIDs yet via NFS
                                      // so we use the nfsPath as the OID
                                      // for now.
        public static String hash; // currently SHA1...maybe diff in future?
        private static final String delimiter = ",";

        FileID() {
            oid = null;
            nfsPath = null;
            hash = null;
        }

        FileID(String p, String h, HashMap m) {
            oid = null;
            nfsPath = p;
            hash = h;
        }

        FileID(ObjectIdentifier o, String h, HashMap m) {
            oid = o;
            nfsPath = null;
            hash = h;
        }

        // This assumes we have something that was generated by toFileString()
        FileID(String s) throws HoneycombTestException {
            String tokens[] = s.split(delimiter);
            if (tokens == null || tokens.length != 2) {
                printError("ERROR: failed to parse " + s + " into a FileID");
                die();
            }

            if (useNFS) {
                nfsPath = tokens[0];
            } else {
                printDebug("Trying to create new OID with string " + tokens[0]);
                oid = new ObjectIdentifier(tokens[0]);
            }
            hash = tokens[1];

            if (!validate()) {
                printError("ERROR: failed to construct valid OID from string: "
                    + s);
                die();
            }
        }

        public String toString() {
            if (useNFS) {
                return (nfsPath + delimiter + hash);
            } else  {
                return (oid + delimiter + hash);
            }
        }

        // This is what we embed in each file after store.
        // It is also used when we want a complete string to reference
        // the object
        public String toFileString() {
            if (useNFS) {
                return (nfsPath + delimiter + hash);
            } else  {
                return (oid + delimiter + hash);
            }
        }
        
        // When we do unique retrieves, we need a filename for each file
        public String getFilename() {
            if (useNFS) {
                return (hash);
            } else  {
                return (oid + delimiter + hash);
            }
        }

        // Check if FileID object we created looks right.
        // This is mostly used after calling the constructer with a
        // string to see if we were able to parse everything okay.
        public boolean validate() {
            if (useNFS) {
                if (nfsPath == null) {
                    printError("ERROR: NFS path was null");
                    return (false);
                }
            } else {
                // verify OID
                // XXX for now we just make sure it is longer than the
                // string null, but this needs to change
                if (oid == null || oid.toString().length() < OIDMINLENGTH) {
                    printError("ERROR: Failed to validate OID");
                    printError("Found invalid oid: " + oid);
                    return (false);
                }
            }

            // verify hash
            if (hash == null || (hash != null && hash.length() != HASHLENGTH)) {
                printError("ERROR: Failed to validate hash");
                if (hash != null) {
                    printError("Found invalid hash of length " +
                        hash.length() + " when we expected " +
                        "something of length " + HASHLENGTH);
                    if (hash.length() > 100) {
                        printError("The first chars from the string " +
                            "found are " + hash.substring(0, 99));
                    } else {
                        printError("The string found was " + hash);
                    }
                } else {
                    printError("Found invalid hash of null");
                }
                return (false);
            }

            return (true);
        }

        // reinitialize the object
        public void reset() {
            oid = null;
            nfsPath = null;
            hash = null;
        }
    }

    /**
     * For debugging only:  prints the current values of important variables.
     */
    public static void printVariables() {
        printDebug(TestStoreRetrieve.class.getName() + " current options");
        printDebug("General Options:");
        printDebug("  sleep_msecs_between_loops=" + sleepBetweenIterations);
        printDebug("  max_iterations=" + maxIterations);
        printDebug("  start_over_when_done=" + startOverWhenDone);
        printDebug("  num_retries=" + numRetries);
        printDebug("  sleep_msecs_between_retries=" + sleepBetweenRetries);
        printDebug("  use_curl=" + useCurl);
        printDebug("  use_nfs=" + useNFS);
        printDebug("  throw_instead_of_exit=" + throwInsteadOfExit);
        printDebug("  use_log_class=" + useLogClass);
        printDebug("  print_debug_msgs=" + logDebugMsgs);
        printDebug("  print_info_msgs=" + logInfoMsgs);
        printDebug("  testlib.print_debug_msgs=" + TestLibrary.logDebugMsgs);
        printDebug("  testlib.print_info_msgs=" + TestLibrary.logInfoMsgs);
        printDebug("  testlib.use_log_class=" + TestLibrary.useLogClass);

        if (useNFS) {
            printDebug("    nfs_path=" + nfsTestrunDir);
        }
        printDebug("Network options:");
        printDebug("  servers=" + server);
        printDebug("  port=" + port);
        printDebug("  activehost_timeout(-1=API default)=" + activeHostTimeout);
        printDebug("  connection_timeout(-1=API default)=" + connectionTimeout);
        printDebug("  socket_timeout(-1=API default)=" + socketTimeout);
        printDebug("Verification options:");
        printDebug("  verifyHash=" + verifyHash);
        printDebug("  do_retrieves_after_store=" + doRetrieveAfterStore);
        printDebug("  do_re-store=" + doReStore);
        if (storeMode) {
            printDebug("StoreMode Options:");
            printDebug("  Reliability options:");
            printDebug("   data_frag=" + dataFrags);
            printDebug("   parity_frag=" + parityFrags);
            printDebug("  pass_size=" + passSize);
            printDebug("  allow_obj_exists=" + allowObjectExists);
            printDebug("  grow_file=" + growFile);
            printDebug("  use_emd=" + useEMD);
            if (createNewFile) {
                printDebug("  Will create file:");
                printDebug("    bytes=" + originalSize);
                printDebug("    initial_character=" + initChar);
                printDebug("    base_filename=" + uploadBaseFilename);
                printDebug("    file_to_store=" + uploadFilename);
                printDebug("    overwrite_file=" + overwriteFile);
                printDebug("    random_size=" + useRandomBytes);
                printDebug("    random_char=" + useRandomChar);
                printDebug("    gen_constant_file=" + generateConstantFile);
            } else {
                printDebug("  Will use existing file:");
                printDebug("    original_file=" + originalFilename);
                printDebug("    overwrite_file=" + overwriteFile);
                printDebug("    suppress_copy=" + suppressCopy);
                printDebug("    duplicate_file=" + uploadBaseFilename);
                printDebug("    file_to_store=" + uploadFilename);
            }
        }
        if (retrieveMode) {
            printDebug("RetrieveMode Options:");
            printDebug("  File to extract object IDs from=" + originalFilename);
            printDebug("  Target for retrieved files=" + retrieveTarget);
            printDebug("  Length of original file data=" + originalSize);
            printDebug("  Store files retrieved uniquely=" +
                saveRetrievedFilesUniquely);
        }
        if (queryMode) {
            printDebug("QueryMode Options:");
            printDebug("  max_results_per_query=" + maxResults);
        }
        if (deleteMode) {
            printDebug("DeleteMode Options:");
            printDebug("  Currently none!");
        }
        printDebug(" ");
    }

    //
    // Routines that handle error, info, and debugging messages
    //
    public static void printError(String s) {
        if (useLogClass) {
            Log.ERROR(s);
        } else {
            System.out.println(s);
        }
    }

    public static void printInfo(String s) {
        if (useLogClass) {
            Log.INFO(s);
        } else {
            if (logInfoMsgs) {
                System.out.println(s);
            }
        }
    }

    public static void printDebug(String s) {
        if (useLogClass) {
            Log.DEBUG(s);
        } else {
            if (logDebugMsgs) {
                System.out.println(s);
            }
        }
    }

    public static void printDate(String s) {
        Date d = new Date();
        printInfo(s + d.toString());
    }

    public static void sleep(int sleepTime) {
        try {
            if (sleepTime > 0) {
                printDebug("Sleeping for " + sleepTime + " msecs");
            }
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            printDebug("Sleep Interrupted: " + e.getMessage());
        } 
    }

    public static void done() throws HoneycombTestException {
        printDate("<--- Exiting test with SUCCESS " +
            "(total execution time was " +
            (TestLibrary.msecsNow() - testStartTime) + " milliseconds) at ");
        if (throwInsteadOfExit) {
            throw new HoneycombTestException(TestRun.PASS);
        }
        System.exit(0); // Success
    }

    public static void die() throws HoneycombTestException {
        printDate("<--- Exiting test with ERROR " +
            "(total execution time was " +
            (TestLibrary.msecsNow() - testStartTime) + " milliseconds) at ");
        if (throwInsteadOfExit) {
            throw new HoneycombTestException(TestRun.FAIL);
        }
        System.exit(1); // Failure
    }

    /**
     * Hacky way to retry actions...we should really look at the errors
     * more closely because some actions shouldn't be retried. 
     *
     * checkOnly indicates that we should see if we have retries left but
     * don't take any action.  If false, we should do the decrement and other
     * retry actions.
     *
     * XXX - this isn't supported for any of the verification
     * store/retrieve actions.
     */
    public static boolean shouldRetry(boolean checkOnly) {
        printDebug("numRetries=" + numRetries + ", checkOnly=" + checkOnly);
        if (numRetries == INFINITE) {
            if (!checkOnly) {
                doRetryActions();
            }
            return (true);
        } else if (!checkOnly && (numRetries > 0)) {
            numRetries--;
            doRetryActions();
            return (true);
        }

        return (numRetries > 0);
    }

    public static void doRetryActions() {
        printDate("\n--> Will retry failed action for " +
            "iteration " + currIteration + " after " +
            sleepBetweenRetries + " milliseconds to server " + serverToUse +
            ".  Current time is ");
        sleep(sleepBetweenRetries);
        if (numRetries == INFINITE) {
            printInfo("--> Retries left: INFINITE\n");
        } else {
            printInfo("--> Retries left: " + numRetries + "\n");
        }
    }

    /**
     * Exception class for handling the parsing of arguments.
     */
    public static class ArgException extends Exception {
        ArgException(String s) {
            super(s);
        }

        ArgException(String s, Exception e) {
            super(s, e);
        }
    }

    /**
     * Examine the arguments passed to this program and set variables
     * accordingly.
     */
    public static void parseArgs(String args[]) throws ArgException {
        // XXX Maybe use some form of getopt instead?
        String opt;
        int ival = 0;
        long lval = 0;
        String sval = null;
        char c = '\0';
        String[] saval = {};

        // For reference, print out the arg string.  This is useful primarily
        // when output is redirected to a log file.
        String argString = new String("");
        for (int i = 0; i < args.length; i++) {
            argString += args[i] + " ";

            // Hack to use Log class if -T is an arg before we print anything
            if (args[i].equals("-T")) {
                useLogClass = true;
                TestLibrary.useLogClass = true;
            }
        }
        invocationArgs = argString;

        printInfo("StoreRetrieve called with args '" + argString + "'");

        Vector optsWithNoOperands = new Vector();
        Vector optsWithIntOperands  = new Vector();
        Vector optsWithLongOperands  = new Vector();
        Vector optsWithStringOperands = new Vector();
        Vector optsWithCharOperands = new Vector();
        Vector optsWithStringArrayOperands = new Vector();

        // Initialize these lists
        optsWithNoOperands.add("-S");
        optsWithNoOperands.add("-R");
        optsWithNoOperands.add("-Q");
        optsWithNoOperands.add("-e");
        optsWithNoOperands.add("-D");
        optsWithNoOperands.add("-F");
        optsWithNoOperands.add("-I");
        optsWithNoOperands.add("-q");
        optsWithNoOperands.add("-v");
        optsWithNoOperands.add("-C");
        optsWithNoOperands.add("-T");
        optsWithNoOperands.add("-G");
        optsWithNoOperands.add("-B");
        optsWithNoOperands.add("-E");
        optsWithNoOperands.add("-O");
        optsWithNoOperands.add("-K");
        optsWithNoOperands.add("-u");
        optsWithNoOperands.add("-X");
        optsWithNoOperands.add("-Y");
        optsWithNoOperands.add("-Z");
        optsWithNoOperands.add("-h");
        optsWithNoOperands.add("-x");
        optsWithNoOperands.add("-y");
        optsWithNoOperands.add("-z");

        optsWithIntOperands.add("-t");
        optsWithIntOperands.add("-i");
        optsWithIntOperands.add("-r");
        optsWithIntOperands.add("-w");
        optsWithIntOperands.add("-p");
        optsWithIntOperands.add("-a");
        optsWithIntOperands.add("-n");
        optsWithIntOperands.add("-o");
        optsWithIntOperands.add("-N");
        optsWithIntOperands.add("-M");
        optsWithIntOperands.add("-m");

        optsWithLongOperands.add("-b");

        optsWithStringOperands.add("-f");
        optsWithStringOperands.add("-k");
        optsWithStringOperands.add("-d");
        optsWithStringOperands.add("-A");
        optsWithStringOperands.add("-P");

        optsWithCharOperands.add("-c");

        optsWithStringArrayOperands.add("-s");

        for (int i = 0; i < args.length; i++) {
            opt = args[i];

            if (optsWithIntOperands.contains(opt)) {
                try {
                    ival = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    throw new ArgException("failed to parse arg for " + opt +
                        " as an int", e);
                } catch (IndexOutOfBoundsException e) {
                    throw new ArgException(opt + " requires an argument", e);
                }
            } else if (optsWithLongOperands.contains(opt)) {
                try {
                    lval = Long.parseLong(args[++i]);
                } catch (NumberFormatException e) {
                    throw new ArgException("failed to parse arg for " + opt +
                        " as a long", e);
                } catch (IndexOutOfBoundsException e) {
                    throw new ArgException(opt + " requires an argument", e);
                }
            } else if (optsWithStringOperands.contains(opt)) {
                try {
                    sval = args[++i];
                } catch (IndexOutOfBoundsException e) {
                    throw new ArgException(opt + " requires an argument", e);
                }
            } else if (optsWithCharOperands.contains(opt)) {
                try {
                    sval = args[++i];
                    if (sval.length() != 1) {
                        throw new ArgException(opt + " must be a single char");
                    }
                    c = sval.charAt(0);
                } catch (IndexOutOfBoundsException e) {
                    throw new ArgException(opt + " requires an argument", e);
                }
            } else if (optsWithStringArrayOperands.contains(opt)) {
                try {
                    sval = args[++i];
                    saval = sval.split(",");
                    if (saval.length < 1) {
                        throw new ArgException(opt + " needs >= 1 entry");
                    }
                } catch (IndexOutOfBoundsException e) {
                    throw new ArgException(opt + " requires an argument", e);
                }
             }

            // No args
            if (opt.equals("-S")) {        // Store mode
                storeMode = true;
            } else if (opt.equals("-R")) { // Retrieve mode
                retrieveMode = true;
            } else if (opt.equals("-Q")) { // Query mode
                queryMode = true;
            } else if (opt.equals("-D")) { // Delete mode
                deleteMode = true;
            } else if (opt.equals("-e")) { // run against the emulator
                runAgainstEmulator = true;
            } else if (opt.equals("-F")) { // NFS mode
                useNFS = true;
            } else if (opt.equals("-I")) { // Initialize connection each time
                reinitConnection = true;
            } else if (opt.equals("-q")) { // disable logging
                logDebugMsgs = false;
                logInfoMsgs = false;
                TestLibrary.logDebugMsgs = false;
                TestLibrary.logInfoMsgs = false;
            } else if (opt.equals("-v")) { // enable verbose logging
                logDebugMsgs = true;
                logInfoMsgs = true;
                TestLibrary.logDebugMsgs = true;
                TestLibrary.logInfoMsgs = true;
            } else if (opt.equals("-T")) { // run in context of Test suite
                throwInsteadOfExit = true;
                useLogClass = true;
                TestLibrary.useLogClass = true;
            } else if (opt.equals("-O")) { // overwrite file
                overwriteFile = true;
            } else if (opt.equals("-K")) { // suppress copy
                suppressCopy = true;
            } else if (opt.equals("-u")) { // don't overwrite retrieved file
                saveRetrievedFilesUniquely = true;
            } else if (opt.equals("-X")) { // don't verify object Hash
                verifyHash = false;
            } else if (opt.equals("-Y")) { // retrieve after every store
                doRetrieveAfterStore = true;
            } else if (opt.equals("-Z")) { // re-store after the action 
                doReStore = true;
            } else if (opt.equals("-C")) { // use curl
                useCurl = true;
            } else if (opt.equals("-G")) { // grow the file as we store
                growFile = true;
            } else if (opt.equals("-B")) { // use -1 for the size
                passSize = false;
            } else if (opt.equals("-E")) { // allow object exists
                allowObjectExists = true;
            } else if (opt.equals("-x")) { // use random number of bytes
                useRandomBytes = true;
            } else if (opt.equals("-y")) { // use random character
                useRandomChar = true;
            } else if (opt.equals("-g")) { // generate file w/ constant char
                generateConstantFile = true;
            } else if (opt.equals("-z")) { // don't specify MD during store
                useEMD = false;
            } else if (opt.equals("-h")) { // help
                // XXX avoid printing "error" in main
                throw new ArgException("Help text is as follows");

            // Int args
            } else if (opt.equals("-t")) { // sleep between intervals
                sleepBetweenIterations = ival;
            } else if (opt.equals("-i")) { // how many objs to store/retrieve
                maxIterations = ival;
                startOverWhenDone = false;
            } else if (opt.equals("-r")) { // how many retries upon failure
                numRetries = ival;
            } else if (opt.equals("-w")) { // how long to sleep btwn retries
                sleepBetweenRetries = ival;
            } else if (opt.equals("-p")) { // port
                port = ival;
            } else if (opt.equals("-a")) { // active host timeout
                activeHostTimeout = ival;
            } else if (opt.equals("-n")) { // connection timeout
                connectionTimeout = ival;
            } else if (opt.equals("-o")) { // socket timeout
                socketTimeout = ival;
            } else if (opt.equals("-N")) { // data frags
                dataFrags = ival;
            } else if (opt.equals("-M")) { // parity frags
                parityFrags = ival;
            } else if (opt.equals("-m")) { // number of objs allowed per query
               maxResults  = ival;

            // Long args
            } else if (opt.equals("-b")) { // size of created file
                originalSize = lval;

            // String args
            } else if (opt.equals("-f")) { // original file
                originalFilename = sval;
                createNewFile = false;
            } else if (opt.equals("-k")) { // base name of file to upload
                uploadBaseFilename = sval;
            } else if (opt.equals("-d")) { // name to use for download
                retrieveTarget = sval;
            } else if (opt.equals("-A")) { // cluster name for Audit
                clusterName = sval;
            } else if (opt.equals("-P")) { // NFS path to use for store/retr
                nfsBasePath = sval;
                nfsTestrunDir = sval;

            // Char args
            } else if (opt.equals("-c")) { // character to use for file
                initChar = c;

            // String array args
            } else if (opt.equals("-s")) { // server
                server = sval; // flat string
                servers = saval; // array

            // Error case
            } else {
                throw new ArgException("invalid argument: " + opt);
            }
        }
    }
}
