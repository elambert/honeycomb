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


/**
 * Honecomb object and metadata creation and store program. Loads db for
 * AdvQuery tests.
 *
 */
public class AdvQueryMDStore implements Runnable {
    private String dataVIP;
    private int numThreads;
    private long minSizeBytes;
    private long maxSizeBytes;
    private long runtimeMillis;
    // Number of milliseconds to pause inbetween stores.
    // Value of zero means no pause;
    private long pauseMillis;
    private String seed;
    private int bytePatternSize;
    private AdvQueryRandomUtil rand = null;
    private ArrayList userMetadataList = null;
    
    private AdvQueryPrintStats statistics = null;
    private String statsFilename = "";
    private int printStatsIntervalMillis;
    
    
    private static void printUsage() {
        System.err.println("NAME");
        System.err.println(
                "       AdvQueryMDStore - Loads HoneyComb database with ");
        System.err.println(" randomly generated metadata. Must run this load");
        System.err.println("before can run AdvQuery query tests.");
        System.err.println("\nIMPORTANT: The same seed value and metadata ");
        System.err.println("must be used on for storing and querying.");
        System.err.println();
        System.err.println("SYNOPSIS");
        System.err.println(
                "       java AdvQueryMDStore <dataVIP> <num_threads> <seed> ");
        System.err.println(
                "            <min_size_bytes> <max_size_bytes> <repeat_size> ");
        System.err.println(
                "            <runtime_seconds> <print_statistics_interval> ");
        System.err.print(
                "            <statistics_file_name>");
        System.err.println(" <metadata_random_gen_patterns_string>");
        System.err.println();
        System.err.println("DESCRIPTION");
        System.err.println();
        System.err.println("dataVIP - honeycomb cluster data ip address");
        System.err.println("num_threads - number of threads per client");
        System.err.println("              [1, 5, 10 15, 20]");
        System.err.println("seed - The seed for the random data generator.");
        System.err.println("       String of 8 digits separated by \"_\"");
        System.err.println("       For example: 8_2_3_4_5_6_7_9");
        System.err.print("min_size_bytes - min size of data objects created");
        System.err.println(" Accepted values: [1024, 1024000]");
        System.err.print("max_size_bytes - max size of data objects created");
        System.err.println(" Accepted values: [1020, 1024000]");
        System.err.print("repeat_size - size in bytes of repeat pattern for");
        System.err.println(" data objects created.");
        System.err.println("runtime_seconds - number of seconds to run load.");
        System.err.print("print_statistics_interval - number of seconds");
        System.err.println(" inbetween printing statistics.");
        System.err.print("statistics_file_name - fullpath name of");
        System.err.println(" statistics output file.");
        System.err.println("");
        System.err.println(" pause_seconds:");
        System.err.println("  The number of seconds to pause inbetween store");
        System.err.println("  operations. A value of 0 means no pause.");
        System.err.println("");
        System.err.println("metadata_random_gen_patterns_string explanation:");
        System.err.println("   A series of metadata random generation patterns");
        System.err.println("   separated by /. Patterns are similar to those");
        System.err.println("   used by multiload tests.");
        System.err.println();
        System.err.println("   Each pattern is of the format: ");
        System.err.print("       [namespace.metadata-field-name]<fixed-text>");
        System.err.println("<{generation-pattern}><fixed-text>");
        System.err.print("   generation-pattern is enclosed in {} and of ");
        System.err.println("format <printf format>:<list or range of values>");
        System.err.println("   Examples:");
        System.err.print("     [advquery_onetable_noidx.one]");
        System.err.println("fixed{02x:0-255}}");
        System.err.println("       which would generate data in the form of ");
        System.err.println("fixed02, fixedab, fixed0a");
        System.err.println("     [advquery_onetable_noidx.two]{02c:121-131)");
        System.err.println("     [advquery_onetable_noidx.two]FixedDataOnly");
        System.err.println();
        System.err.println("RUNNING PROGRAM");
        System.err.print("       java AdvQueryMDStore dev315 10 ");
        System.err.print("8_2_3_4_5_6_7_9 1024 1024 1024 600 60 ");
        System.err.print("/mnt/test/advquery_test/");
        System.err.print("1.cl3.dev315.1x1.COMPLEX.stats");
        System.err.print("\"[advquery_onetable_noidx.one]fixed{02x:0-255}");
        System.err.print("/[advquery_onetable_noidx.two]fixedOnly");
        System.err.println("/[advquery_onetable_noidx.three]{1c:65-90})\"");
    }
    
    public static void main(String [] args) throws Throwable {
        if (args.length != 11) {
            printUsage();
            System.err.println("error: insufficient arguments (" +
                    args.length + ")");
            System.exit(1);
        }
        
        String dataVIP = null;
        int numThreads = 0;
        long minSizeBytes = 0;
        long maxSizeBytes = 0;
        int bytePatternSize = 0;
        long runtimeMillis = 0;
        long pauseMillis = 0;
        String seed = "";
        int interval = 6000;
        
        dataVIP=args[0];
        
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
        
        try {
            minSizeBytes = Long.parseLong(args[3]);
            if (minSizeBytes < 0) {
                throw new Throwable();
            }
        } catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: min_size_bytes: " +
                    args[3]);
            System.exit(1);
        }
        
        try {
            maxSizeBytes = Long.parseLong(args[4]);
            if (maxSizeBytes < 0) {
                throw new Throwable();
            }
        } catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: max_size_bytes: " +
                    args[4]);
            System.exit(1);
        }
        
        if (minSizeBytes > maxSizeBytes) {
            printUsage();
            System.err.println("error: invalid arguments: " +
                    "min_size_bytes > max_size_bytes: " +
                    args[3] + " > " + args[4]);
            System.exit(1);
        }
        
        try {
            bytePatternSize = Integer.parseInt(args[5]);
            if (bytePatternSize < 0) {
                throw new Throwable();
            }
        } catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: pattern_size: " +
                    args[5]);
            System.exit(1);
        }
        
        if (bytePatternSize > minSizeBytes) {
            printUsage();
            System.err.println("error: invalid arguments: " +
                    "pattern_size >min_size_bytes : " +
                    args[5] + " > " + args[3]);
            System.exit(1);
        }
        
        try {
            runtimeMillis = Long.parseLong(args[6]) * 1000;
        } catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: runtime_seconds: " +
                    args[6]);
            System.exit(1);
        }
        
        try {
            interval = Integer.parseInt(args[7]) * 1000;
        } catch (Throwable t) {
            printUsage();
            System.err.println(
                    "error: invalid argument print_statistic_interval: " +
                    args[7]);
        }
        
        String statsFilename = args[8];
        
        try {
            pauseMillis = Long.parseLong(args[9]) * 1000;
        } catch (Throwable t) {
            printUsage();
            System.err.println("error: invalid argument: pause_seconds: " +
                    args[9]);
            System.exit(1);
        }
        
        if (runtimeMillis != 0 && (pauseMillis > runtimeMillis)) {
            printUsage();
            System.err.print("error: pause_seconds must be less than");
            System.err.println(" runtime_seconds");
        }
        
        String mdPatterns = args[10];
        // remove any newline characters from pattern
        mdPatterns = mdPatterns.replaceAll("\n", "");
        mdPatterns = mdPatterns.trim();
        // Output patterns used to log and error log
        System.out.println("#PATTERN: " + mdPatterns);
        System.err.println("#PATTERN: " + mdPatterns);
        
        AdvQueryMDStore mdStoreStress = new AdvQueryMDStore(
                dataVIP, numThreads, seed, minSizeBytes, maxSizeBytes,
                bytePatternSize, runtimeMillis, interval, statsFilename,
                mdPatterns);
        mdStoreStress.run();
    }
    
    /**
     *  Creates instance of AdvQueryMDStore to run MD Store/Load for
     *  AdvQuery tests.
     */
    public AdvQueryMDStore(String dataVIP,
            int numThreads,
            String seed,
            long minSizeBytes,
            long maxSizeBytes,
            int bytePatternSize,
            long runtimeMillis,
            int printStatsIntervalMillis,
            String statsFilename,
            String metadataPatterns)
            throws Throwable    {
        
        this.dataVIP=dataVIP;
        this.numThreads=numThreads;
        this.minSizeBytes=minSizeBytes;
        this.maxSizeBytes=maxSizeBytes;
        this.runtimeMillis=runtimeMillis;
        this.bytePatternSize = bytePatternSize;
        this.seed = seed;
        this.statsFilename = statsFilename;
        this.printStatsIntervalMillis = printStatsIntervalMillis;
        
        try {
            rand = new AdvQueryRandomUtil(this.seed);
        } catch (Exception e) {
            System.err.println("Invalid seed for random number generator: " +
                    this.seed);
            System.err.println(e.getMessage());
            System.exit(1);
        }
        
        System.err.println("PatternString: " + metadataPatterns);
        userMetadataList = AdvQueryMDPatternElement.
                parseUserSpecifiedMetadataPatterns(metadataPatterns);     
        
        
    }
    
    /**
     *  Runs the AdvQueryMDStore test/load program.
     *
     */
    public void run() {
        try {
            
            NameValueObjectArchive archive = new NameValueObjectArchive(dataVIP);
            
            int connectionTimeout = 600000; // 10 minutes in ms
            int socketTimeout = 600000; // 10 minutes in ms
            
            archive.setConnectionTimeout(connectionTimeout);
            archive.setSocketTimeout(socketTimeout);
            
            long startTime = AdvQueryManageMetadata.getStartTime();
            System.err.println("#TEST_START_TIME: " + startTime);
            System.out.println("#TEST_START_TIME: " + startTime);
            String testid = AdvQueryManageMetadata.getTestid();
            System.out.println("#TEST_ID: " + testid);
            System.err.println("#TEST_ID: " + testid);
            System.out.println("#TEST_SEED: " + seed);
            System.err.println("#TEST_SEED: " + seed);
                        
            ArrayList<AdvQueryMDStoreThread> threads =
                    new ArrayList<AdvQueryMDStoreThread>();
            AdvQueryMDPatternElement[] mdArray = new
                    AdvQueryMDPatternElement[userMetadataList.size()];
            for (int i = 0; i < mdArray.length; i++) {
                mdArray[i] = (AdvQueryMDPatternElement) userMetadataList.get(i);
            }
            String namespace = mdArray[0].getNamespace();
            System.out.println("#NAMESPACE: " + namespace);
            System.err.println("#NAMESPACE: " + namespace);
            System.out.println("#NUMBER_OF_THREADS: " + numThreads);
            System.err.println("#NUMBER_OF_THREADS: " + numThreads);
            
            AdvQueryManageMetadata.init(namespace);
            
            byte[][] seeds = new byte[numThreads][];
            for (int i = 0; i < numThreads; i++) {
                seeds[i] = rand.getNewSeed();
            }
            byte[] dataGenSeed = rand.getNewSeed();
            AdvQueryRandomUtil dataPRNG = new AdvQueryRandomUtil(dataGenSeed);

            for (int i = 0; i < numThreads; i++) {
                byte[] nextSeed = seeds[i];
                AdvQueryMDStoreThread storeThread = 
                        new AdvQueryMDStoreThread(archive,
                        minSizeBytes,
                        maxSizeBytes,
                        bytePatternSize,
                        runtimeMillis,
                        true,
                        mdArray,
                        nextSeed,
                        dataPRNG);
                threads.add(storeThread);
            }

            statistics = new AdvQueryPrintStats(printStatsIntervalMillis,
                    runtimeMillis, statsFilename, threads, true);
            
            statistics.start();
            for (int i = 0; i < numThreads; i++) {
                ((AdvQueryMDStoreThread) threads.get(i)).start();
            }
            for (int i = 0; i < numThreads; i++) {
                ((AdvQueryMDStoreThread) threads.get(i)).join();
            }
            
            statistics.join();
            
            long endTime = System.currentTimeMillis();
            // Get the smallest # of objects stored by a thread
            long minNumObjectsStored = Long.MAX_VALUE;
            long value = 0;
            for (int i = 0; i < numThreads; i++) {
                value = ((AdvQueryMDStoreThread) threads.get(i)).
                        getStoreCount();
                if (value < minNumObjectsStored) {
                    minNumObjectsStored = value;
                }
            }
            
            if (minNumObjectsStored == Long.MAX_VALUE) {
                minNumObjectsStored = 0;
            }
            System.err.println("#NUMBER_OBJECTS_STORED_PER_THREAD: " +
                    minNumObjectsStored);
            System.out.println("#NUMBER_OBJECTS_STORED_PER_THREAD: " +
                    minNumObjectsStored);
            System.err.println("#TEST_END_TIME: " + endTime);
            System.out.println("#TEST_END_TIME: " + endTime);

            
        } catch (Throwable throwable) {
            System.err.println("An unexpected error occurred.");
            throwable.printStackTrace(System.err);
        }
    }
}
