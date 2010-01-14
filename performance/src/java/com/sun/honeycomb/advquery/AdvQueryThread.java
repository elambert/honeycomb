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



import java.util.Random;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.QueryResultSet;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.RunCommand;
import com.sun.honeycomb.test.util.ExitStatus;

import java.net.InetAddress;
import java.util.Date;

/**
 *  Thread instances for running Advanced Query, query tests.
 *  Setup and execution of query test threads. The query type,
 *  run time and other characteristics are determined by the
 *  values supplied when instantiating a instance.
 */
public class AdvQueryThread extends Thread {
    
    private static final String PRINT_SUMMARY_LOCK = "PrintSummaryLock";
    
    public static final int TOO_MANY_RESULTS = 300000;
    public static final int RESULTS_GROUP_SIZE = 5000;
    private int resultsGroupSize = RESULTS_GROUP_SIZE;
    private NameValueObjectArchive archive;
    private long runtimeMillis;
    private long pauseMillis;
    // reset Random number generator seed after this timeout vaue
    private static long cycleRandomNumElapsedTimeMillis
            = 60 * 60 * 1000;
    
    private Random randSize;
    private Random randId;
    private AdvQueryRandomUtil prng = null;
    
    private byte[] seed;
    private static final boolean timeoutSeed = true;
    private boolean withMD;
    private AdvQueryMDPatternElement[] userMetadataPatterns = null;
    private long queryCount = 0;
    private long queryVerifyCount = 0;
    private long notSureFetchTotal = 0;
    private double serviceTime = 0;
    private AdvQueryRandomUtil dataPRNG = null; // not used
    private boolean validateResults = true;
    
    private static String hostname;
    
    /**
     * Query Types:
     * Lists the types of queries available for testing.
     */
    public final static int SIMPLE = 0;
    public final static int COMPLEX = 0;  
    public final static int COMPLEX2 = 1;
    public final static int COMPLEX3 = 2;
    public final static int COMPLEX4 = 3;
    public final static int COMPLEX5 = 4;
    public final static int COMPLEX6 = 5;
    public final static int UNIQUE = 6;
    public final static int ALL = 7;
    public final static int EMPTY = 8;
    public final static int MANY = 9;
    public final static int MAX_FETCH_LIMIT = 10;
    public final static int OR_EQUAL = 11;
    public final static int MIXED_STRESS = 12;
    public final static int MIXED_ADVQUERY = 13;
    public final static int MAX_QUERY_TYPE = MIXED_ADVQUERY;
    
    public final static int[] MIXED_STRESS_QUERIES =
    { COMPLEX2, OR_EQUAL,COMPLEX6,  MANY, COMPLEX5, UNIQUE};
    public final static int[] MIXED_ADVQUERY_QUERIES =
    { COMPLEX, OR_EQUAL, COMPLEX2, COMPLEX5, COMPLEX6, UNIQUE};
    
    private String testId = "";
    private String namespace = "";
    
    private int currentMixedQuery = -1;
    private int[] mixedQueryList = null;
    private int queryType = COMPLEX;
    private static final int SINGLE_QUERY_TYPE = -1;
    private int mixedQueryType = SINGLE_QUERY_TYPE;
    private final static  String[] QUERY_TYPES = { "Complex",
    "Complex2", "Complex3", "Complex4", "Complex5", "Complex6", "Unique",
    "All", "Empty", "MANY", "MAX_FETCH_LIMIT", "OREQUAL",
    "MIXED_STRESS", "MIXED_ADVQUERY"};
    
    // Indicates whether can verify query results or not
    private static final boolean[] verifiable = { true,
    false, false, false, false, true, true, false, true, false,
    false, true, false, false};
    
    private AdvQueryPerformanceStatistics reportStats =
            new AdvQueryPerformanceStatistics();
    private AdvQueryPerformanceStatistics stats = null;
    private long startTime;
    private static final String EXEC_QUERY = "Query";
    private static final String EXEC_FETCH = "Fetch";

    static {
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("error: unable to determine local hostname.");
            System.err.println("setting hostname attribute to 'Default Host'");
            hostname = "Default Host";
        }
    }
    
    /**
     *  Creates a thread that runs an advanced query test for the
     *  specified time limit.
     *  @param archive      named object archive object shared among threads
     *  @param querystring  string representation of the query type to run
     *  @param runtimeMillis    run time in milliseconds
     *  @param pauseMillis  pause between queries and fetches (optional testing)
     *  @param resultGroupSize  result group fetch size
     *  @param mdList       metadata random data generation patterns 
     *  specifies the data generation pattern for each metadata attribute
     *  @param seed         seed value for random number generator
     *  @param dataPRNG     random num generator instance
     *  @param validateResults  true if attempt to validate query results by
     *  comparing the # objects returned against the # stored
     *  @param testId       test id used for store test run, required if
     *  validating query results
     *  @param namespace    namespace containing the metadata attributes 
     *  (required only if results are being validated)
     *
     *  @throws HoneycombTestException if invalid parameter values 
     */
    public AdvQueryThread(NameValueObjectArchive archive,
            String querystring,
            long runtimeMillis,
            long pauseMillis,
            int resultsGroupSize,
            AdvQueryMDPatternElement[] mdList,
            byte[] seed,
            AdvQueryRandomUtil dataPRNG,
            boolean validateResults,
            String testId,
            String namespace) throws HoneycombTestException {
        
        this.archive = archive;
        this.seed = seed;
        this.testId = testId;
        this.namespace = namespace;
        
        this.runtimeMillis = runtimeMillis;
        this.pauseMillis = pauseMillis;
        if (resultsGroupSize > 0) {
            this.resultsGroupSize = resultsGroupSize;
        }

        if (this.pauseMillis > 0) {
            System.out.println("PAUSE TIME: " + this.pauseMillis);
        }
        prng = new AdvQueryRandomUtil(seed);
        this.dataPRNG = dataPRNG;
        this.queryType = stringToQueryType(querystring);
        if (queryType == MIXED_STRESS) {
            mixedQueryType = queryType;
            mixedQueryList = this.MIXED_STRESS_QUERIES;
        } else if (queryType == MIXED_ADVQUERY) {
            mixedQueryType = queryType;
            mixedQueryList = MIXED_ADVQUERY_QUERIES;
        }
        if (mdList != null & mdList.length > 0) {
            userMetadataPatterns = new AdvQueryMDPatternElement[mdList.length];
            for (int i = 0; i < mdList.length; i++) {
                userMetadataPatterns[i] =
                        new AdvQueryMDPatternElement(mdList[i]);
            }
        }
        
        this.validateResults = validateResults;
        
    }
    
    public void run() {
        startTime = System.currentTimeMillis();
        long startTimeRandomGen = startTime;
        
        // These are the min/max values for the entire run
        long minfirst = Long.MAX_VALUE;
        long maxfirst = Long.MIN_VALUE;
        long minfetch = Long.MAX_VALUE;
        long maxfetch = Long.MIN_VALUE;
        
        long batchops = 0;
        String query = null;
        
        String opType = EXEC_QUERY;
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        stats = new AdvQueryPerformanceStatistics();
        boolean printExampleQuery = true;
        boolean checkPause = false;
        int queryResultsGroupSize = this.resultsGroupSize;
        while (
                (runtimeMillis < 0 ||
                runtimeMillis > elapsedTime)) {
            
            if (pauseMillis > 0 && checkPause) {
                try {
                    System.out.println("Pausing thread[" + this.getName() +
                            "] at " + System.currentTimeMillis());
                    sleep(pauseMillis);
                } catch (InterruptedException ex) {
                    // do nothing;
                }
            }
            
            checkPause = true;
            
            if (mixedQueryType != SINGLE_QUERY_TYPE) {
                queryType = getNextQueryType();
            }
            if (queryType == this.MAX_FETCH_LIMIT) {
                queryResultsGroupSize = TOO_MANY_RESULTS;
            } else {
                queryResultsGroupSize = this.resultsGroupSize;
            }
            
            long numResults = 0;    // number of records found for this query
            long queryFetches = 0;  // testing if diff between numResults and
                                    // queryFetches.
            long firsttime = 0;
            long queryFetchTime = 0;
            
            try {
                batchops = 0; // reset
                stats.batchops = 0;
                query = generateQuery();
                if (query == null) {
                    break;
                }
                if (printExampleQuery) {
                    printExampleQuery = false;
                    System.out.println("Running query: " +
                            this.QUERY_TYPES[queryType] + "\n" +
                            "Result count verification is: " +
                            this.verifiable[queryType] +
                            "\nExample query string: " + query.toString()
                            + "\n");
                }
                
                opType = EXEC_QUERY;
                queryCount++;
                long t0 = System.currentTimeMillis();
                QueryResultSet matches =
                        archive.query(query, queryResultsGroupSize);
                
                /* Keep running totals/min/max for first op latency */
                firsttime = System.currentTimeMillis() - t0;
                queryFetchTime = firsttime;
                stats.totalfirsts++;
                stats.totalfirsttime += firsttime;
                stats.totalfirstsquares += firsttime * firsttime;
                
                
                if (firsttime < minfirst) {
                    minfirst = firsttime;
                }
                
                if (firsttime > maxfirst) {
                    maxfirst = firsttime;
                }
                
                synchronized (this) {
                    if (firsttime < stats.minfirst) {
                        stats.minfirst = firsttime;
                    }
                    
                    if (firsttime > stats.maxfirst) {
                        stats.maxfirst = firsttime;
                    }
                }
                
                opType = EXEC_FETCH;
                t0 = System.currentTimeMillis();
                while (matches.next() &&
                      (runtimeMillis < 0 || runtimeMillis > elapsedTime)) {
                    long t1 = System.currentTimeMillis();
                    long elapsed = t1-t0;
                    queryFetchTime += elapsed;
                    numResults++;
                    batchops++;
                    
                    /* keep running totals/max/min for fetches */
                    /*
                     * Determine fetches based on if we've gotten the number of
                     * results in the queryResultsGroupSize*/
                    if (batchops > queryResultsGroupSize) {
                        if (elapsed < 1) {
                            notSureFetchTotal++;
                        } else {
                            if (elapsed < minfetch) {
                                minfetch = elapsed;
                            }
                            if (elapsed > maxfetch) {
                                maxfetch = elapsed;
                            }
                            
                            synchronized (this) {
                                if (elapsed < stats.minfetch) {
                                    stats.minfetch = elapsed;
                                }
                                if (elapsed > stats.maxfetch) {
                                    stats.maxfetch = elapsed;
                                }
                            }
                            stats.totalfetches++;
                            stats.totalfetchtime += elapsed;
                            stats.totalfetchsquares += elapsed*elapsed;
                            stats.batchops = 1;
                            queryFetches++;
                            batchops = 1;   // reset
                        }
                    }
                    
                    stats.totalops++;
                    t0 = System.currentTimeMillis();
                    stats.elapsed = t0 - startTime;
                    stats.aveops = stats.totalops/(stats.elapsed/1000.0);
                    elapsedTime = System.currentTimeMillis() - startTime;
                    updateQueryPerformanceStats(stats);
                    if (pauseMillis > 0 && 
                            numResults % queryResultsGroupSize == 0) {
                        System.out.println(
                                "Pausing fetch - thread[" + this.getName() +
                                "] numResults: " + numResults + 
                                " at " + System.currentTimeMillis());
                        try {
                            this.sleep(pauseMillis);
                        } catch (Exception ex) {
                            // Do nothing
                        }
                    }
                    
                }   // end while records to fetch
                
                // Print raw stats for query
                AdvQueryRawQueryStatistics.printQueryStats(
                        AdvQueryRawQueryStatistics.QueryStatus.OK,
                        numResults, queryFetchTime, firsttime);
                // Verify query results. If run time is up skip
                // validation - may not have fetched all results
                if (runtimeMillis > elapsedTime &&
                    !verifyQueryResults(numResults, query)) {
                    stats.errorCount++;
                }
                
            } catch (Throwable throwable) {
                printError(
                        "An unexpected error has occured while executing " +
                        opType + " operation. Num results: " + numResults +
                        "  Group size: " + queryResultsGroupSize + 
                        "  total errors:  " +
                        ++stats.errorCount);
                // TODO make sure values will be filled in at this point
                // Print raw stats for query
                AdvQueryRawQueryStatistics.printQueryStats(
                        AdvQueryRawQueryStatistics.QueryStatus.ERR,
                        numResults, queryFetchTime, firsttime);

                try {
                    System.err.println(throwable.getMessage());
                    if (throwable.getMessage().indexOf(
                            "SocketException") == -1) {
                        throwable.printStackTrace();
                    }
                } catch (Throwable thr) {}
            }
            elapsedTime = System.currentTimeMillis() - startTime;
            stats.elapsed = elapsedTime;
            updateQueryPerformanceStats(stats);
            // After hit limit, reset random number generator
            if ((System.currentTimeMillis() - startTimeRandomGen)  >
                    AdvQueryThread.cycleRandomNumElapsedTimeMillis) {
                try {
                    prng = new AdvQueryRandomUtil(seed);
                    // Not an error but output to error stream
                    printError("Resetting random number generator.");
                } catch (HoneycombTestException ex) {
                    printError("Could not reset the random number generator.");
                }
                startTimeRandomGen = System.currentTimeMillis();
            }
            
        }   // end while testing queries
        
        /* Print out statistics */
        synchronized (PRINT_SUMMARY_LOCK) {
            long elapsed = stats.elapsed;
            
            stats.aveops = stats.totalops/(elapsed/1000.0);
            
            long threadID = Thread.currentThread().getId();
            stats.threadID = Thread.currentThread().getId();
            
            
            System.out.println("\n[" + threadID + "] Thread Name: "
                    + getName() + "  Thread ID: " + threadID);
            System.out.println("[" + threadID + "] Query type: "
                    + queryTypeToString());
            System.out.println("[" + threadID + "] Average results/sec: "
                    + stats.aveops);
            System.out.println("[" + threadID + "] Total results: "
                    + stats.totalops + ", Total time: " + elapsed + "ms");
            System.out.println("[" + threadID + "] Total errors: "
                    + stats.errorCount);
            System.out.println("[" + threadID + "] Total Queries: "
                    + queryCount);
            if (validateResults) {
                System.out.println("[" + threadID + "] Total Queries Validated: "
                    + queryVerifyCount);
            }
            System.out.println("[" + threadID + "] Not Sure Fetch Warnings:"
                    + notSureFetchTotal);
            
            /* Calculate stats for first op */
            if (stats.totalfirsts > 0) {
                stats.avefirsts = stats.totalfirsttime/stats.totalfirsts;
            }
            if (minfirst == Long.MAX_VALUE) {
                minfirst = 0;
            }
            if (maxfirst == Long.MIN_VALUE) {
                maxfirst = 0;
            }
            System.out.println("[" + threadID +
                    "] First query call latency (average): " +
                    stats.avefirsts + " ms");
            System.out.println("[" + threadID + "] Total first query calls: " +
                    stats.totalfirsts + ", Total time: " +
                    stats.totalfirsttime + "ms");
            System.out.println("[" + threadID + "] Max first call: " +
                    maxfirst + "ms, Min first call: " + minfirst + "ms.");
            
            if (stats.totalfirsts > 1) {
                double stddevfirsts =
                        java.lang.Math.sqrt((stats.totalfirstsquares -
                        ((stats.totalfirsttime * stats.totalfirsttime)/
                        stats.totalfirsts))/(stats.totalfirsts - 1));
                System.out.println("[" + threadID +
                        "] Standard Deviation first call: " + stddevfirsts);
            }
            
            if (minfetch == Long.MAX_VALUE) {
                minfetch = 0;
            }
            if (maxfetch == Long.MIN_VALUE) {
                maxfetch = 0;
            }
            
            if (stats.totalfetches > 0) {
                /* Calculate stats for subsequent fetches */
                stats.avefetches = stats.totalfetchtime/stats.totalfetches;
                System.out.println("[" + threadID +
                        "] Latency for subsequent fetch (average) " +
                        stats.avefetches + " ms");
                System.out.println("[" + threadID + "] Max fetch latency: " +
                        maxfetch + "ms, Min fetch latency: " +
                        minfetch + "ms");
                System.out.println("[" + threadID + "] Total fetch calls: " +
                        stats.totalfetches + ", Total fetch time: " +
                        stats.totalfetchtime + "ms");
                
                if (stats.totalfetches > 1) {
                    double stddevfetches =
                            java.lang.Math.sqrt((stats.totalfetchsquares -
                            ((stats.totalfetchtime * stats.totalfetchtime)/
                            stats.totalfetches))/(stats.totalfetches - 1));
                    
                    System.out.println("[" + threadID +
                            "] Standard Deviation fetch: " + stddevfetches);
                }
            }
            
        }
    }
    
    /*
     *  Cycles through the various query types for mixed queries.
     */
    private int getNextQueryType() {
        currentMixedQuery = ++currentMixedQuery % (mixedQueryList.length-1);
        return this.mixedQueryList[currentMixedQuery];
    }
    /**
     *  Generates a query string based on the query type and on the
     *  random data generation patterns for each metadata attribute.
     *
     *  @return a correctly formatted query string of the specified type
     *  that can be passed to the honeycomb api
     */
    public String generateQuery() throws HoneycombTestException {
        // Generate MD values
        AdvQueryManageMetadata manageMD = AdvQueryManageMetadata.createInstance(
                prng,
                dataPRNG,
                userMetadataPatterns,
                startTime,
                stats.totalops);
        
        String[] mdValues = manageMD.getGeneratedValues();
        
        /*
         * COMPLEX/SIMPLE - Query of the form: 
         * mdfield[0]=mdValues[0] AND NOT mdfield[20]=mdValues[20]
         *
         * Example query string: ( "advquery_three_tables.one"='fixede1' 
         * AND NOT "advquery_three_tables.twenty_one"='prefix600postfix' )
         *
         * Verifiable: YES
         *      via: grep mdValues[0] <store-out-file> |
         *          grep -c -v mdValues[20]
         *
         * Returns ~300-400 objects per query, assuming 120,000 objects stored
         * using the default metadata pattern file
         */
        if (queryType == SIMPLE || queryType == COMPLEX) {
            
            StringBuffer buffer = new StringBuffer();
            buffer = this.createNewQueryBuffer();
            appendAttrQuery(buffer, 0, "=", mdValues);
            buffer.append(" AND NOT ");
            appendAttrQuery(buffer, 20, "=", mdValues);
            buffer.append(" )");
            return buffer.toString();
            
        /*
         * OR_EQUAL
         * Query of the form: mdfield[0]=mdValues[0]
         * OR mdfield[20]=mdValues[20] OR mdfield[17]=mdValues[17]
         *
         * Example query string: ( "advquery_onetable_noidx.one"='fixede1' OR 
         * "advquery_onetable_noidx.twenty_one"='prefix600postfix' OR 
         * "advquery_onetable_noidx.eighteen"='8d0rs5ale08' )
         *
         * Verifiable: YES
         *      via: grep mdValues[0] <store-out-file> | 
         *          grep -c -v mdValues[17]
         *
         * Returns ~18,700 objects per query, assuming 120,000 objects stored
         * using the default metadata pattern file
         */
        } else if (queryType == OR_EQUAL) {
            StringBuffer buffer = new StringBuffer();
            buffer = this.createNewQueryBuffer();

            appendAttrQuery(buffer, 0, "=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 20, "=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 17, "=", mdValues);
            buffer.append(" )");
            return buffer.toString();

        /*
         * EMPTY
         * This should return no values because field 1 should be a fixed
         * value for all records. Added the other fields just to make it
         * interesting.
         *
         * Verifiable: YES - no objects should be returned
         */
        } else if (queryType == EMPTY) {
            StringBuffer buffer =  this.createNewQueryBuffer();
            for (int i = 0; i < this.userMetadataPatterns.length; i++) {
                appendAttrQuery(buffer, i, "=", mdValues);
                if (i < userMetadataPatterns.length - 1) {
                    buffer.append(" OR ");
                }
            }
            //buffer.append(" AND NOT ");
            buffer.append(") AND NOT ( ");
            appendAttrQuery(buffer, 1, "=", mdValues);
            buffer.append(")");
            return buffer.toString();
            
        /*
         * COMPLEX2
         * Query of the form:
         *      mdfield[7]>mdValues[7] OR
         *      mdfield[20]<mdValues[20] 
         *      mdfield[12]=mdValues[12] AND NOT
         *      mdfield[2]>mdValues[2]
         *
         * Example query string: "advquery_three_tables.eight">'4cstandard' OR 
         * "advquery_three_tables.twenty_one"<'prefix600postfix' OR 
         * "advquery_three_tables.thirteen"=
         *      'LongFielddlpgmw2g3ddlpgmw2g3ddlpgmw2g3ddlpgValue' AND NOT 
         * "advquery_three_tables.three">'M'
         *
         * Verifiable: NO
         *
         * Returns ~40K-99K objects per query, assuming 120,000 objects stored
         * using the default metadata pattern file
         */
            
        } else if (queryType == COMPLEX2) {
            StringBuffer buffer = new StringBuffer("");
            appendAttrQuery(buffer, 7, ">", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 20, "<", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 12, "=", mdValues);
            buffer.append(" AND NOT ");
            appendAttrQuery(buffer, 2, ">", mdValues);
            return buffer.toString();
            
        /*
         * COMPLEX3
         * Query of the form:
         * ( ( (
         *  mdfield[0] > mdValues[0]  OR
         *  mdfield[5]=mdValues[5] OR
         *  mdfield[21]<mdValues[21] ) AND NOT
         * (
         *  mdfield[20]=mdValues[20] OR
         *  mdfield[4]=mdValues[4] ) AND NOT
         * (
         *  mdfield[1]>mdValues[1]
         * )))
         * Example query string: 
         * ( ( ("advquery_three_tables.one">'fixede1' OR 
         * "advquery_three_tables.six"='2320' OR 
         * "advquery_three_tables.twenty_two"<'M') AND NOT 
         * ("advquery_three_tables.twenty_one"<='prefix600postfix' OR 
         * "advquery_three_tables.five"='928') AND NOT 
         * ("advquery_three_tables.two">'fixedOnly') ) )
         *
         * Verifiable: NO
         *
         * Returns a wide range or record set sizes from 0, 422, 3225 to 65K
         * objects per quer, assuming 120,000 objects stored using the 
         * default metadata pattern file. Most of the record set sizes 
         * returned are > 40K.
         * 
         */
        } else if (queryType == COMPLEX3) {
            StringBuffer buffer =  this.createNewQueryBuffer();
            appendAttrQuery(buffer, 0, ">", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 5, "=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 21, "<", mdValues);
            buffer.append(")");
            buffer.append(" AND NOT (");
            appendAttrQuery(buffer, 20, "<=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 4, "=", mdValues);
            buffer.append(") AND NOT (");
            appendAttrQuery(buffer, 1, ">", mdValues);
            buffer.append(")");
            return buffer.toString();
            
        /*
         * COMPLEX4
         * Query of the form:
         * ( ( 
         * mdfield[0] >=mdValues[0]  OR
         * mdfield[2]=mdValues[2] OR
         * mdfield[9]<=mdValues[9] ) AND NOT
         * (
         * mdfield[7]<=mdValues[7] OR
         * mdfield[12]>=mdValues[12]) AND 
         * (
         * mdfield[1]>=mdValues[1] OR
         * mdfield[6]<=mdValue[6])))
         *
         * Example query string: 
         * (  ( ("advquery_three_tables.one">='fixede1' OR 
         *      "advquery_three_tables.three"='M' OR 
         *      "advquery_three_tables.ten"<='Long10Value') AND NOT 
         *  ("advquery_three_tables.eight"<='4cstandard' OR 
         *   "advquery_three_tables.thirteen">=
         *      'LongFielddlpgmw2g3ddlpgmw2g3ddlpgmw2g3ddlpgValue') AND 
         *  ("advquery_three_tables.two">='fixedOnly' OR 
         *   "advquery_three_tables.seven"<='30xkppsfri30xkp') ) )
         *
         * Verifiable: NO
         *
         * Returns a wide range or record set sizes from 50, 1917 to 63K
         * objects per query, assuming 120,000 objects stored using the
         * default metadata pattern file. Most of the record set sizes 
         * returned are > 15K.
         */
        } else if (queryType == COMPLEX4) {
            StringBuffer buffer =  this.createNewQueryBuffer();
            appendAttrQuery(buffer, 0, ">=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 2, "=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 9, "<=", mdValues);
            buffer.append(")");
            buffer.append(" AND NOT (");
            appendAttrQuery(buffer, 7, "<=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 12, ">=", mdValues);
            buffer.append(") AND (");
            appendAttrQuery(buffer, 1, ">=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 6, "<=", mdValues);
            buffer.append(")");
            return buffer.toString();
          
        /*
         * COMPLEX5
         * Query of the form:
         * ( 
         *  ( 
         *      mdfield[19]>mdValues[19]  OR
         *      mdfield[18]!=mdValues[18] OR
         *      mdfield[15]<=mdValues[15] ) AND NOT
         *  (
         *      mdfield[10]=mdValues[10] OR
         *      mdfield[12]!=mdValues[12]) AND 
         *  (
         *      mdfield[16]>=mdValues[16] OR
         *      mdfield[6]<=mdValue[6] OR
         *      mdfield[0]!=mdValue[0] OR
         *      mdfield[8]<=mdValue[8]) AND NOT
         *  (   
         *      mdfield[2]!=mdValue[2] OR
         *      mdfield[1]!=mdValue[1])
         * )
         *
         * Example query string:
         *  ( 
         *    ("advquery_three_tables.twenty"='975' OR 
         *      "advquery_three_tables.nineteen"!='2150' OR 
         *      "advquery_three_tables.fifteen"<='Long31Value') AND 
         *    ("advquery_three_tables.eleven"='8d0zte9dju8d0' OR 
         *      "advquery_three_tables.thirteen"!=
         *      'LongFielddlpgmw2g3ddlpgmw2g3ddlpgmw2g3ddlpgValue') AND 
         *    ("advquery_three_tables.seventeen">='8fstandard' OR 
         *      "advquery_three_tables.seven"<='30xkppsfri30xkp' OR 
         *      "advquery_three_tables.fourteen"='qauf85yftsqauf85yft' OR
         *      "advquery_three_tables.one"!='fixede1' OR 
         *      "advquery_three_tables.nine"<='kyfg3fuml8kyfg3fu')
         *  ) AND NOT 
         *  ("advquery_three_tables.three"!='M' OR 
         *      "advquery_three_tables.two"!='fixedOnly' )
         *
         * Verifiable: NO
         *
         * Returns record set sizes of ~55k objects per query,
         * assuming 120,000 objects stored using the default metadata pattern
         * file.
         */
        } else if (queryType == COMPLEX5) {
            
            StringBuffer buffer =  this.createNewQueryBuffer();
            buffer.append(" (");
            appendAttrQuery(buffer, 19, "=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 18, "!=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 14, "<=", mdValues);
            buffer.append(")");
            buffer.append(" AND (");
            appendAttrQuery(buffer, 10, "=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 12, "!=", mdValues);
            buffer.append(") AND (");
            appendAttrQuery(buffer, 16, ">=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 6, "<=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 13, "=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 0, "!=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 8, "<=", mdValues);
            buffer.append(") )");            
            buffer.append(" AND NOT (");
            appendAttrQuery(buffer, 2, "!=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 1, "!=", mdValues);
            buffer.append(" )");
            
            return buffer.toString();
            
        /*
         * COMPLEX6
         *      The mdfield[9] value is the only one that determines whether
                an object matches the query. The rest of the query terms
         *      always match.
         *
         * Query of the form:
         *  (   mdfield[0]>=mdValues[0]  OR
         *      mdfield[0]<=mdValues[0]) AND
         *  (   mdfield[3]>=mdValues[3]  OR
         *      mdfield[3]<=mdValues[3]) AND
         *  (   mdfield[9] BETWEEN mdValues[9] AND mdValues[9]) AND 
         *  (   mdfield[1]>=mdValues[1] OR
         *      mdfield[1]<=mdValues[1]) AND 
         *  (   mdfield[2]>=mdValues[2] OR
         *      mdfield[2]<=mdValues[2]) AND 
         *  (   mdfield[4]>=mdValues[4] OR
         *      mdfield[4]<=mdValues[4]) AND 
         *  (   mdfield[23]>=mdValues[23] OR
         *      mdfield[23]<=mdValues[23]) AND 
         *  (
         *      mdfield[16]>=mdValues[16] OR
         *      mdfield[6]<=mdValue[6] OR
         *      mdfield[0]!=mdValue[0] OR
         *      mdfield[8]<=mdValue[8]) AND NOT
         *  (   
         *      mdfield[2]!=mdValue[2] OR
         *      mdfield[1]!=mdValue[1])
         * )
         *
         * Example query string:
         *  ( "advquery_three_tables.one">='fixede1' OR 
         *      "advquery_three_tables.one"<='fixede1') AND 
         *  ("advquery_three_tables.four">='prefix600postfix' OR
         *   "advquery_three_tables.four"<='prefix600postfix') AND 
         *  ("advquery_three_tables.ten" BETWEEN 
         *      'Long10Value' AND 'Long10Value') AND 
         *  ("advquery_three_tables.two">='fixedOnly' OR 
         *      "advquery_three_tables.two"<='fixedOnly') AND 
         *  ("advquery_three_tables.three">='M' OR 
         *      "advquery_three_tables.three"<='M') AND 
         *  ("advquery_three_tables.five">='928' OR 
         *      "advquery_three_tables.five"<='928') AND 
         *  ("advquery_three_tables.twenty_four">='fixed66' OR 
         *      "advquery_three_tables.twenty_four"<='fixed66') AND
         *
         *
         * Verifiable: YES
         *      via: grep mdValues[9] <store-out-file> | 
         *
         * Returns record set sizes of ~4,500 objects per query 
         * assuming 120,000 objects stored using the default metadata pattern
         * file.
         */
        } else if (queryType == COMPLEX6) {
            StringBuffer buffer =  this.createNewQueryBuffer();
            appendAttrQuery(buffer, 0, ">=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 0, "<=", mdValues);
            buffer.append(") AND (");
            appendAttrQuery(buffer, 3, ">=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 3, "<=", mdValues);
            buffer.append(") AND (");
            appendAttrQuery(buffer, 9, " BETWEEN ", mdValues);
            buffer.append(") AND (");
            appendAttrQuery(buffer, 1, ">=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 1, "<=", mdValues);
            buffer.append(") AND (");
            appendAttrQuery(buffer, 2, ">=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 2, "<=", mdValues);
            buffer.append(") AND (");
            appendAttrQuery(buffer, 4, ">=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, 4, "<=", mdValues);
            
            int lastEntry = mdValues.length - 1;
            buffer.append(") AND (");
            appendAttrQuery(buffer, lastEntry, ">=", mdValues);
            buffer.append(" OR ");
            appendAttrQuery(buffer, lastEntry, "<=", mdValues);
            buffer.append(")");

            return buffer.toString();
            
        /*
         * Query of the form:
         * mdfield[6] = mdValues[6]  AND
         * mdfield[7]=mdValues[7] AND
         * mdfield[8]=mdValues[8] AND
         *  ...
         * mdfield[25]=mdValues[25]
         *
         * Verifiable:
         * Note: This query will most likely just return a single record
         * due to the number of fields used and the lack of duplicate random
         * values.
         *
         */
        } else if (queryType == UNIQUE) {
            StringBuffer buffer =  createNewQueryBuffer();
            int lastIndex = userMetadataPatterns.length;
            for (int i = 0; i < lastIndex; i++) {
                appendAttrQuery(buffer, i, "=", mdValues);
                if (i < lastIndex-1) {
                    buffer.append(" AND ");
                }
            }
            buffer.append(")");
            return buffer.toString();
            
        } else if ((queryType == MANY) ||
                (queryType == MAX_FETCH_LIMIT)) {
            return "system.test.type_string LIKE '%AdvQuery%'";
            
        } else if (queryType == ALL) {
            return "system.test.type_string LIKE '%AdvQuery%'";
        } else {
            printError("Invalid query type");
        }
        return null;
    }
    
    /*
     * Creates a new query string and puts in the first two mandatory fields
     */
    private StringBuffer createNewQueryBuffer() {
        
        StringBuffer buffer = new StringBuffer("( ");
        
        if (validateResults && testId.length() > 0) {
            StringBuffer attrName = new StringBuffer();
            if (namespace.length() > 0) {
                attrName.append(namespace);
                attrName.append(".");
            }
            attrName.append("test_id");
            buffer.append(attr(attrName.toString()));
            buffer.append("=");
            buffer.append(val(testId));
            buffer.append(" ) AND ( ");
        }
        return buffer;
        
    }
    /*
     * Appends the metadata comparision operation to the query string passed in.
     */
    private void appendAttrQuery(StringBuffer buffer, int index,
            String operator, String[] values) {
        buffer.append(attr(userMetadataPatterns[index].getMetadataFieldName()));
        buffer.append(operator);
        buffer.append(val(values[index]));
        if (operator.indexOf("BETWEEN") != -1) {
            buffer.append(" AND ");
            buffer.append(val(values[index]));
        }
    }
    
    /*
     * Appends the metadata comparision operation to the query string passed in.
     */
    private void appendLikeAttrQuery(StringBuffer buffer, int index,
            String operator, String[] values) {
        buffer.append(attr(userMetadataPatterns[index].getMetadataFieldName()));
        buffer.append(operator);
        buffer.append(val(values[index]));
    }

    private int getRandom(int max) {
        return prng.getInteger(max);
    }
    
    /*
     * Prints out error message including the thread name.
     */
    private void printError(String errorMsg) {
        System.err.println("Thread [" + Thread.currentThread().getId() +
                "]: " + Thread.currentThread().getName() +
                "  " + errorMsg);
    }
    
    /*
     * Atrribute in query must be enclosed in double quotes
     */
    private String attr(String attrName) {
        return "\"" + attrName + "\"";
    }
    
    /*
     * Value in query must be in single quotes
     */
    private String val(String value) {
        return "'" + value + "'";
    }
    
    /**
     *  Determines whether query is supported.
     *  @param queryType    integer query type value to check
     *  @return boolean    true if query type is supported, false otherwise
     */
    public boolean queryTypeSupported(int queryType) {
        boolean supported = false;
        if (queryType <= MAX_QUERY_TYPE && queryType >= 0) {
            supported = true;
        }
        return supported;
    }
    
    /**
     *  Returns the string equivalent for the query.
     */
    public String queryTypeToString() {
        if (queryType <= MAX_QUERY_TYPE && queryType >= 0) {
            return this.QUERY_TYPES[queryType];
        } else {
            printError("Invalid query type");
            return null;
        }
    }
    
    /**
     * Converts the string query type to the integer constant value.
     */
    public static int stringToQueryType(String qry) {
        int queryIdx = -1; // non found
        for (int i = 0; i <= MAX_QUERY_TYPE; i++) {
            if (qry.equalsIgnoreCase(QUERY_TYPES[i])) {
                queryIdx = i;
                break;
            }
        }
        return queryIdx;
        
    }
    
    /**
     *  Retrieves the current performance statistics.
     */
    public AdvQueryPerformanceStatistics getQueryPerformanceStats() {
        AdvQueryPerformanceStatistics newStats = null;
        synchronized (this) {
            newStats = new AdvQueryPerformanceStatistics(reportStats);
            // reset interval min/max values
            stats.minfetch = Long.MAX_VALUE;
            stats.maxfetch = Long.MIN_VALUE;
            stats.minfirst = Long.MAX_VALUE;
            stats.maxfirst = Long.MIN_VALUE;
        }
        return newStats;
    }
    
    /**
     *  Updates the performance statisitcs.
     */
    private void updateQueryPerformanceStats(
            AdvQueryPerformanceStatistics localStats) {
        AdvQueryPerformanceStatistics newStats = null;
        synchronized (this) {
            reportStats.setStatistics(localStats);
        }
    }
    
    /*
     *  Verifies query results if possible. Greps thorough verification
     *  file and determines if the number of results matches the number
     *  of objects returned by querying honeycomb.
     */
    private boolean verifyQueryResults(long numResults, String query) {
        boolean goodResult = false;
        if (! (verifiable[queryType] && validateResults)) {
            return true;
        }
        
        StringBuffer command;
        long numFound = 0;
        String results = "";
        RunCommand runEnv = null;
        
        /*
         * Only get here if can verify query
         */
        switch (queryType) {
            case OR_EQUAL :
                /*
                 * Query of the form: mdfield[0]=mdValues[0]
                 * OR mdfield[20]=mdValues[20]
                 * OR mdfield[20]=mdValues[17]
                 *
                 * Verifiable via: grep -c -F -e mdValues[0]
                 *                 -e  mdValues[20] 
                 *                 -e mdValues[17] <store-out-file>
                 */
                command = new StringBuffer("grep -c -F -e '/MDField:");
                command.append(
                        userMetadataPatterns[0].getMetadataFieldName());
                command.append(":");
                command.append(
                        userMetadataPatterns[0].getGeneratedPattern());
                command.append(":' ");
                
                command.append(" -e '/MDField:");
                command.append(
                        userMetadataPatterns[20].getMetadataFieldName());
                command.append(":");
                command.append(
                        userMetadataPatterns[20].getGeneratedPattern());
                command.append(":' ");
                
                command.append(" -e '/MDField:");
                command.append(
                        userMetadataPatterns[17].getMetadataFieldName());
                command.append(":");
                command.append(
                        userMetadataPatterns[17].getGeneratedPattern());
                command.append(":' ");
                command.append(AdvQueryStress.verifyMetadataFile);

                runEnv = new RunCommand();
                goodResult = runGrep(runEnv, command.toString(),
                    numResults);
                break;
                
            case COMPLEX :
                /*
                 * Query of the form: mdfield[0]=mdValues[0]
                 * AND NOT mdfield[20]=mdValues[20]
                 *
                 * Verifiable via: grep mdValues[0] <store-out-file> |
                 * grep -c -v mdValues[20]
                 */
                command = new StringBuffer("grep '/MDField:");
                command.append(userMetadataPatterns[0].getMetadataFieldName());
                command.append(":");
                command.append(userMetadataPatterns[0].getGeneratedPattern());
                command.append(":' ");
                command.append(AdvQueryStress.verifyMetadataFile);
                command.append(" | grep -c -v '");
                command.append(userMetadataPatterns[20].getMetadataFieldName());
                command.append(":");
                command.append(userMetadataPatterns[20].getGeneratedPattern());
                command.append(":' ");

                runEnv = new RunCommand();
                goodResult = runGrep(runEnv, command.toString(),
                        numResults);
                break;
                
            case COMPLEX6 :
                /*
                 * Verifiable via: grep -c mdValues[9] <store-out-file>
                 * The mdfield[9] value is the only one that determines whether
                 * an object matches the query. The rest of the query terms
                 * always match.
                 */
                command = new StringBuffer("grep -c '/MDField:");
                command.append(userMetadataPatterns[9].getMetadataFieldName());
                command.append(":");
                command.append(userMetadataPatterns[9].getGeneratedPattern());
                command.append(":' ");
                command.append(AdvQueryStress.verifyMetadataFile);
                runEnv = new RunCommand();
                goodResult = runGrep(runEnv, command.toString(),
                        numResults);
                break;

            case UNIQUE:
                if (numResults < 1) {
                    printError("Error unique record not found for query: "
                            + query);
                } else if (numResults > 1) {
                    printError("Error more than one unique record found "
                            + " for query: " + query);
                } else {
                    goodResult = true;
                }                
                break;
                
            case EMPTY:
                if (numResults > 0) {
                    printError("Error records found for query: "
                            + query);
                } else {
                    goodResult = true;
                }                
                break;
                    
            default:
                printError(
                    "Test error: No code to validate query type: " +
                    queryType + " query string: " + 
                    this.queryTypeToString() +
                    ".\n Turning result validation off for test run.");
                validateResults = false;
                break;
        } // end switch
        
        if (goodResult) {
            queryVerifyCount++;
        }
        return goodResult;
        
    }
    
    /*
     *  When running grep if it does not find anything it returns 1.
     *  The RunCommand exec commands see this as an unexpected error and
     *  throw an exception. So check to see if the return code was 1 for
     *  grep. A value of 1 means nothing was found but the command
     *  completed successfully.
     */
    private boolean runGrep(RunCommand runEnv, String command,
            long numExpected) {
        boolean resultsMatch = false;
        long numFound = 0;
        String resultsString = "";
        try {
            resultsString = runEnv.execWithOutput(command);
            numFound = Long.parseLong(resultsString.trim());
            
        }  catch (HoneycombTestException ex) {
            int returnCode = -1;
            if (ex.exitStatus != null) {
                ExitStatus es = ex.exitStatus;
                returnCode = es.getReturnCode();
            } else {
                // This is not the best but the HoneycombException returned
                // does not have the ExitStatus instance varb set so parse
                // the error string.
                String msg = ex.getMessage();
                int pos = msg.indexOf("exit code=");
                if (pos != -1) {
                    msg = msg.substring(pos+10);
                    pos = msg.indexOf(",");
                    if (pos > -1) {
                        msg = msg.substring(0, pos);
                    }
                    try {
                        returnCode = Integer.parseInt(msg.trim());
                    } catch (NumberFormatException nfe) {}
                }
            }
            if (returnCode != 1 && returnCode != 0) {
                printError("Error attempting to validate results: " +
                        ex.getMessage());
                return false;
            }
        } catch (NumberFormatException ex) {
            printError("Error attempting to validate results. " +
                    "  Command:" + command.toString() +
                    "  returned: " + resultsString);
            return false;
        }
        
        // Compare number found with number expected
        if (numFound == numExpected) {
            resultsMatch = true;
            // Debugging
            //System.out.println("Verifying results - numFound: " + numFound + 
            //        "   numExpected: " + numExpected);
        } else {
            printError(
                    "Invalid number of results received. "
                    + command
                    + "   Honeycomb returned: " + numExpected
                    + "   Number in log: " + numFound);
        }
        
        return resultsMatch;
    }
}
