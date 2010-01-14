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



import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 *  Prints out raw/individual query statistics. Supports printing
 *  stats on a per query basis to an output file.
 */
public class AdvQueryRawQueryStatistics {
    
    // Determines whether the statistics output file has been created.
    private static boolean statsFileCreated = false;
    private static PrintWriter outputStats;
    private static String filename;
    
    /**
     * Query completion statuses.
     */
    public static enum QueryStatus {OK, ERR};
    private static final String QUERY_OK = "OK";
    private static final String QUERY_ERROR = "ERR";
    
    /* Creates a new instance of AdvQueryRawQueryStatistics */
    private AdvQueryRawQueryStatistics() {
    }
    
    /*
     * Prints out the headers.
     */
    private static void printHeader() {
        outputStats.println("#     Time   Status        Objects     Elap_Time "
                + "     FF_Latency    Thrd");
        outputStats.println("#                                      Millisecs "
                + "     Millisecs         ");
        outputStats.flush();
    }

    /**
     *  Prints out statistics on the processing of a single query.
     *  Must have created & initialized the raw statistics file
     *  before calling this method.
     *  @param status               completion status for the query
     *  @param numObjectsReturned   number of objects processed returned
     *  by the query
     *  @param queryDuration        time (in milliseconds) it took to execute
     *  the query and retrieve all the objects.
     *  @param firstFetchLatency    time (in milliseconds) to fetch the first
     *  set of objects (i.e. first result group fetch)    
     */
    public static void printQueryStats(QueryStatus status,
            long numObjectsReturned, long queryDuration, 
            long firstFetchLatency) {
        long currentTime = System.currentTimeMillis();
         
        try {
            outputStats.format("%1$10d      %2$3.3s     %3$10d    %4$10d" +
                    "      %5$10d     %6$3s\n",
                currentTime/1000,
                (status == QueryStatus.OK ? QUERY_OK : QUERY_ERROR),
                numObjectsReturned, queryDuration, firstFetchLatency,
                Thread.currentThread().getId());
           outputStats.flush();
        } catch (Exception ex) {
            System.err.println("Error printing statistics to file");
            try { 
                ex.printStackTrace(System.err);
                outputStats.flush();
            } catch (Exception e) {}
        }
             
    }
    /**
     *  Creates the raw query statistics output file. Only a single file
     *  is created per run and is used by all threads.
     *  @param outputFilename   the full path name of the raw stats file
     *  to be created
     *  @exception FileNotFoundException    if can't create file
     */
    public static synchronized boolean 
            createRawQueryStatsFile(String outputFilename)
            throws FileNotFoundException {
        if (!statsFileCreated) {
            statsFileCreated = true;
            filename = outputFilename;
            outputStats = new PrintWriter(filename);
            printHeader();
        }
        return statsFileCreated;
    }
   
    /**
     *  Prints out sample statistics line to /tmp/raw_stats_test.out.
     *  Used to test/debug statistics output format.
     *  @param args No arguments required
     */
    public static void main(String[] args) {
        try {
            AdvQueryRawQueryStatistics.createRawQueryStatsFile(
                "/tmp/raw_stats_test.out");
            AdvQueryRawQueryStatistics.printQueryStats(
                    QueryStatus.OK, 12000, 9500, 1005);
            AdvQueryRawQueryStatistics.printQueryStats(
                    QueryStatus.ERR, 0, 500, 100);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
