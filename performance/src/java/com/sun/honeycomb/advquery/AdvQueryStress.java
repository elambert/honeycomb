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



import java.util.ArrayList;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.test.util.RunCommand;
import com.sun.honeycomb.test.util.HoneycombTestException;
import java.text.DateFormat;
import java.util.Date;


public class AdvQueryStress implements Runnable {
    
    public static final String NO_VERIFICATION_FILE = "NOFILE";

    private int querytype = 0;
    
    private String dataVIP;
    private int numThreads;
    private long minSizeBytes;
    private long maxSizeBytes;
    private long runtimeMillis;
    private long pauseMillis;
    private int resultsGroupSize;
    private String seed;
    private int bytePatternSize;
    private String query;
    private AdvQueryRandomUtil rand = null;
    private ArrayList userMetadataList = null;
    
    private AdvQueryPrintQueryStats statistics = null;
    private String statsFilename = "";
    private String rawStatsFilename = "";
    private int printStatsIntervalMillis;
    public static String verifyMetadataFile = null;
    private static boolean validateResults = false;
    private static String verificationTestId = "";
    private static String namespace = "";
    
    private static void printUsage() {
        System.err.println("NAME");
        System.err.println("       AdvQueryStress - Stress Honeycomb " +
                "complex query operations based on generated MD patterns");
        System.err.println();
        System.err.println("SYNOPSIS");
        System.err.println(
                "       java AdvQueryStress <dataVIP> <num_threads> <seed> ");
        System.err.print("    <query_type> <runtime_seconds> ");
        System.err.print(" <verification-file>  <print_statistics_interval> ");
        System.err.print("<statistics-file-name> <pause_seconds> ");
        System.err.println("<metadata_random_gen_patterns_string>");
        System.err.println();
        System.err.println("DESCRIPTION");
        System.err.println();
        System.err.println("dataVIP - honeycomb cluster data ip address");
        System.err.println("num_threads - number of threads per client");
        System.err.println("              [1, 5, 10 15, 20]");
        System.err.print("seed - The seed for the random metadata generator.");
        System.err.print("       Must match the seed used by the " +
                "AdvQueryStore when it created the data and metadata.");
        System.err.println("       A string of 8 digits separated by \"_\"");
        System.err.println("       For example: 8_2_3_4_5_6_7_9");
        System.err.print("query_type - min size of data objects created");
        System.err.println("runtime_seconds - number of seconds to run load.");
        System.err.print("verification-file - the output log from the ");
        System.err.print(" AdvQueryMDStore run used to create the objects ");
        System.err.print("and metadata. The file will be used to validate ");
        System.err.println("the results of some queries.");
        System.err.print("print_statistics_interval - number of seconds");
        System.err.println(" inbetween printing statistics.");
        System.err.print("statistics_file_name - fullpath name of");
        System.err.println(" statistics output file.");
        System.err.println("");
        System.err.println(" pause_seconds:");
        System.err.println("  The number of seconds to pause inbetween query");
        System.err.println("  operations. A value of 0 means no pause.");
        System.err.println("  A small pause will also be taken before ");
        System.err.println("  fetching the next result group.");
        System.err.println("");
        System.err.println(" result_group_size:");
        System.err.println("   The result group fetch size. Default is 5000.");
        System.err.println("");
        System.err.print("raw_statistics_file_name - fullpath name of");
        System.err.println(" raw query by query statistics output file.");
        System.err.println("");
        System.err.println("metadata_random_gen_patterns_string explanation:");
        System.err.println("  Series of metadata random generation patterns");
        System.err.println("  separated by /. Patterns are similar to those");
        System.err.println("  used by multiload tests.");
        System.err.println();
        System.err.println("  Each pattern is of the format: ");
        System.err.print("       [namespace.metadata-field-name]<fixed-text>");
        System.err.println("<{generation-pattern}><fixed-text>");
        System.err.print("   generation-pattern is enclosed in {} and of ");
        System.err.println("format <printf format>:<list or range of values>");
        System.err.println("  Examples:");
        System.err.print("     [advquery_onetable_noidx.one]");
        System.err.println("fixed{02x:0-255}}");
        System.err.println("       which would generate data in the form of ");
        System.err.println("fixed02, fixedab, fixed0a");
        System.err.println("     [advquery_onetable_noidx.two]{02c:121-131)");
        System.err.println("     [advquery_onetable_noidx.two]FixedDataOnly");
        System.err.println();
        System.err.println("RUNNING PROGRAM");
        System.err.print("       java AdvQueryStress dev315 10 ");
        System.err.print("8_2_3_4_5_6_7_9 STRESS 600 ");
        System.err.print("/mnt/test/verify/storerun.out 60 ");
        System.err.print("/mnt/test/advquery_test/");
        System.err.print("1.cl3.dev315.1x1.COMPLEX.stats ");
        System.err.print("1.cl3.dev315.1x1.COMPLEX.rawstats ");
        System.err.print("\"[advquery_onetable_noidx.one]fixed{02x:0-255}");
        System.err.print("/[advquery_onetable_noidx.two]fixedOnly");
        System.err.println("/[advquery_onetable_noidx.three]{1c:65-90})\"");
    }
    
    
    public static void main(String [] args) throws Throwable {
        
        if (args.length < 11) {
            printUsage();
            System.err.println(" new error: insufficient arguments ("
                    + args.length + ")");
            System.exit(1);
        }
        
        String dataVIP = null;
        int interval = 100;
        int numThreads = 0;
        long runtimeMillis = 0;
        long pauseMillis = 0;
        int resultsGroupSize = 0;
        String query = "COMPLEX";
        String seed = "";
        
        dataVIP = args[0];
        
        try {
            numThreads = Integer.parseInt(args[1]);
            if (numThreads < 0) {
                throw new Throwable();
            }
        } catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: num_threads: " +
                    args[1]);
            System.exit(1);
        }
        
        seed = args[2];
        
        
        query = args[3];
        int queryType = AdvQueryThread.stringToQueryType(query);
        if (queryType < 0) {
            printUsage();
            System.err.println("error: invalid argument : query_type: " +
                    args[3]);
            System.exit(1);
            
        }
        try {
            runtimeMillis = Long.parseLong(args[4]) * 1000;
        } catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: runtime_seconds: " +
                    args[4]);
            System.exit(1);
        }
        
        AdvQueryStress.verifyMetadataFile = args[5];
        if (AdvQueryStress.verifyMetadataFile.compareToIgnoreCase(
                NO_VERIFICATION_FILE) != 0) {
            setTestId();
        }
        
        if (!validateResults) {
            System.err.println("Invalid or missing validation file." +
                    "  Query results will not be validated.");
            System.out.println("Invalid or missing validation file." +
                    "  Query results will not be validated.");
        }
        
        try {
            interval = Integer.parseInt(args[6]) * 1000;
        } catch (Throwable t) {
            printUsage();
            System.err.println(
                    "error: invalid argument print_statistic_interval: "
                    + args[6]);
        }
        
        String statsFilename = args[7];
        String rawStatsFilename = args[8];
        
        try {
            pauseMillis = Long.parseLong(args[9]) * 1000;
        } catch (Throwable t) {
            printUsage();
            System.err.println(
                    "error: invalid argument pause_seconds: "
                    + args[9]);
        }

        try {
            resultsGroupSize = Integer.parseInt(args[10]);
        } catch (Throwable t) {
            printUsage();
            System.err.println(
                    "error: invalid argument result_group_size: "
                    + args[10]);
        }

        String mdPatterns = args[11];
        // remove any newline characers
        mdPatterns = mdPatterns.replaceAll("\n", "");
        mdPatterns = mdPatterns.trim();
                
        AdvQueryStress queryStress = new AdvQueryStress(
                dataVIP, numThreads, seed, query, runtimeMillis,
                pauseMillis, resultsGroupSize, verifyMetadataFile, interval,
                statsFilename, rawStatsFilename, mdPatterns,
                verificationTestId, namespace);
        
        queryStress.run();
    }
    
    /**
     * Creates instance of the AdvQuery tests to run large metadata queries
     * against honeycomb.
     * The AdvQueryMDStore program must be run first to create and load the
     * data and metadata.
     *
     */
    public AdvQueryStress(String dataVIP,
            int numThreads,
            String seed,
            String query,
            long runtimeMillis,
            String verifyMetadataFile,
            int printStatsIntervalMillis,
            String statsFilename,
            String rawStatsFilename,
            String metadataPatterns,
            String testId,
            String namespace)
            throws Throwable    {
        
        this.dataVIP = dataVIP;
        this.query = query;
        this.numThreads = numThreads;
        this.minSizeBytes = minSizeBytes;
        this.maxSizeBytes = maxSizeBytes;
        this.runtimeMillis = runtimeMillis;
        this.pauseMillis = 0;
        this.resultsGroupSize = 0;
        this.bytePatternSize = bytePatternSize;
        this.seed = seed;
        this.statsFilename = statsFilename;
        this.rawStatsFilename = rawStatsFilename;
        this.printStatsIntervalMillis = printStatsIntervalMillis;
        
        try {
            rand = new AdvQueryRandomUtil(this.seed);
        } catch (Exception e) {
            System.err.println("Invalid seed for random number generator: " +
                    this.seed);
            System.err.println(e.getMessage());
            System.exit(1);
        }
        
        userMetadataList = AdvQueryMDPatternElement.
                parseUserSpecifiedMetadataPatterns(metadataPatterns);
        if (userMetadataList == null) {
            System.err.println("Missing or invalid metadata random generation" +
                    " patterns.");
            if (metadataPatterns != null) {
                System.err.println("Error parsing pattern: " +
                        metadataPatterns);
            } else {
                System.err.println("Null metadata pattern.");
            }
            System.exit(1);
        }
    }
    
    /**
     * Creates instance of the AdvQuery tests to run large metadata queries
     * against honeycomb.
     * The AdvQueryMDStore program must be run first to create and load the
     * data and metadata.
     *
     */
    public AdvQueryStress(String dataVIP,
            int numThreads,
            String seed,
            String query,
            long runtimeMillis,
            long pauseMillis,
            int resultsGroupSize,
            String verifyMetadataFile,
            int printStatsIntervalMillis,
            String statsFilename,
            String rawStatsFilename,
            String metadataPatterns,
            String testId,
            String namespace)
            throws Throwable    {
        
        this(dataVIP, numThreads, seed, query, runtimeMillis,
            verifyMetadataFile, printStatsIntervalMillis,
            statsFilename, rawStatsFilename, metadataPatterns,
            testId, namespace);
        this.pauseMillis = pauseMillis;
        this.resultsGroupSize = resultsGroupSize;
    }

    public void run() {
        NameValueObjectArchive archive = null;
        boolean statsTerminated = false;
        try {
            archive = new NameValueObjectArchive(dataVIP);
            int connectionTimeout = 600000; // 10 minutes in ms
            int socketTimeout = 600000; // 10 minutes in ms
            
            archive.setConnectionTimeout(connectionTimeout);
            archive.setSocketTimeout(socketTimeout);
            ArrayList<AdvQueryThread> threads = 
                    new ArrayList<AdvQueryThread>();
            
            AdvQueryMDPatternElement[] mdArray = new
                    AdvQueryMDPatternElement[userMetadataList.size()];
            
            for (int i = 0; i < mdArray.length; i++) {
                mdArray[i] = (AdvQueryMDPatternElement) userMetadataList.get(i);
            }
            
            /*
             * This has to be done the same exact order as in the store
             * operation in order to get the random #'s generators to produce
             * the same results.
             * Of course the original seed has to be the same as well.
             */
            byte[][] seeds = new byte[numThreads][];
            for (int i = 0; i < numThreads; i++) {
                seeds[i] = rand.getNewSeed();
            }
            
            byte[] dataGenSeed = rand.getNewSeed();
            AdvQueryRandomUtil dataPRNG = new AdvQueryRandomUtil(dataGenSeed);
            
            for (int i = 0; i < numThreads; i++) {
                byte[] nextSeed = seeds[i];
                AdvQueryThread queryThread = new AdvQueryThread(archive,
                        query,
                        runtimeMillis,
                        pauseMillis,
                        resultsGroupSize,
                        mdArray,
                        nextSeed,
                        dataPRNG,
                        validateResults,
                        verificationTestId,
                        namespace);
                threads.add(queryThread);
            }
            
            statistics = new AdvQueryPrintQueryStats(printStatsIntervalMillis,
                    runtimeMillis+(printStatsIntervalMillis/2),
                    statsFilename, threads, true);
            statistics.start();
            AdvQueryRawQueryStatistics.createRawQueryStatsFile(
                        rawStatsFilename);
            long staggerStartMillis = pauseMillis / numThreads;
            if (staggerStartMillis > 2000)
                staggerStartMillis = 2000;
            
            long startTime = System.currentTimeMillis();
            DateFormat datePrint = DateFormat.getDateTimeInstance(
                    DateFormat.FULL, DateFormat.LONG);
            
            String dateString = datePrint.format(new Date(startTime));

            for (int i = 0; i < numThreads; i++) {
                if (pauseMillis > 0 && i > 0) {
                    try {
                        Thread.sleep(staggerStartMillis);
                    } catch (InterruptedException ex) {
                        // do nothing;
                    }
                }
                System.out.println(
                        "Starting thread: " + threads.get(i).getName() +
                        "   Time: " + System.currentTimeMillis());
                ((AdvQueryThread) threads.get(i)).start();
            }
            
            long currentTime = 0;
            for (int i = 0; i < numThreads; i++) {
                currentTime = System.currentTimeMillis();
                ((AdvQueryThread)
                threads.get(i)).join(this.runtimeMillis);
            }
            
            statistics.terminate();
            statsTerminated = true;
            statistics.join(this.printStatsIntervalMillis);
        } catch (Throwable throwable) {
            System.err.println("An unexpected error occurred.");
            throwable.printStackTrace(System.err);

        } finally {
            if (!statsTerminated) {
                statistics.terminate();
                try {
                    statistics.join();
                } catch (Throwable th) {}
            }
        }
    }
    
    /**
     *  Get/set the test ID value. This value comes from the output of the
     *  AdvQueryMDStore run. The output log is searched for the test id and
     *  the namespace. Must find this information if want to verify query
     *  results.
     */
    private static void setTestId() {
        
        StringBuffer command = new StringBuffer("grep '#TEST_ID:' ");
        command.append(AdvQueryStress.verifyMetadataFile);
        RunCommand runEnv = new RunCommand();
        String resultsString = "";
        verificationTestId = "";
        try {
            resultsString = runEnv.execWithOutput(command.toString());
            int pos = resultsString.indexOf(":");
            if (pos > -1 && ++pos < resultsString.length()) {
                verificationTestId = resultsString.substring(pos);
                verificationTestId = verificationTestId.trim();
                command = new StringBuffer("grep 'NAMESPACE:' ");
                command.append(AdvQueryStress.verifyMetadataFile);
                resultsString = runEnv.execWithOutput(command.toString());
                pos = resultsString.indexOf(":");
                if (pos > -1 && ++pos < resultsString.length()) {
                    namespace = resultsString.substring(pos);
                    namespace = namespace.trim();
                }
                validateResults = true;
            } else {
                System.err.println("Invalid #TEST_ID or #NAMESPACE value " +
                        "found in verification file: " +
                        AdvQueryStress.verifyMetadataFile +
                        "   No result verification will be performed.");
            }
            System.out.println("TESTID: " + verificationTestId);
            System.out.println("NAMESPACE; " + namespace);
            
        }  catch (HoneycombTestException ex) {
            System.err.println("Can't find #TEST_ID or #NAMESPACE in " +
                    "verification file: " + AdvQueryStress.verifyMetadataFile +
                    "   The verification file may be missing. " +
                    "No result verification will be performed.");
        }
        
    }
}
