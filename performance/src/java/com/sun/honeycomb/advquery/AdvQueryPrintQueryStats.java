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
 *  Prints out Query statistics periodically based on  user input interval
 */
public class AdvQueryPrintQueryStats extends Thread {
    
    private long runtimeMillis = 0;
    private long numErrors = 0;

    private boolean withMD;
    private long itemCount = 0;
    private long interval = 600; // # of milliseconds
    AdvQueryThread[] threads = null;
    private boolean cancelThread = false;
    private boolean cancelStatsPrinted = false;
    private File outFile = null;
    private PrintWriter output = null;
    private String filename = "";
    private long lastPrintTime = 0;
    private long startTime = 0;
    private double serviceTime = 0;
    private AdvQueryPerformanceStatistics[] saveStats = null;

  /** 
   * Creates a new instance of AdvQueryPrintQueryStats
   *  For testing only
   */
     private AdvQueryPrintQueryStats(long intervalMillis,
            long runtimeMillis,
            String outputFilename, 
            boolean withMD) throws FileNotFoundException, SecurityException {
        this.interval = intervalMillis;
        this.withMD = withMD;
        this.filename = outputFilename;
        this.runtimeMillis = runtimeMillis + 1;
        output = new PrintWriter(filename);
        printHeader();        
    }
    
  /** 
   * Creates a new instance of AdvQueryPrintQueryStats
   *  
   */
    public AdvQueryPrintQueryStats(long intervalMillis,
            long runtimeMillis,
            String outputFilename, 
            ArrayList threadList,
            boolean withMD) throws FileNotFoundException, SecurityException {
        this.interval = intervalMillis;
        Object[] thrds = threadList.toArray();
        saveStats = 
                new AdvQueryPerformanceStatistics[thrds.length];
        threads = new AdvQueryThread[thrds.length];
        for (int i = 0; i < thrds.length; i++) {
            threads[i] = (AdvQueryThread) thrds[i];
            saveStats[i] = new AdvQueryPerformanceStatistics();
        }

        this.withMD = withMD;
        this.filename = outputFilename;
        this.runtimeMillis = runtimeMillis + 1;
        output = new PrintWriter(filename);
        printHeader();
        
    }
 
    public void terminate() {
        cancelThread = true;
        synchronized (this) {
            this.notify();
        }
    }
    private void printHeader() {       
        output.println("#Time        Results      Err     Res/sec/thrd       TotRes   AvgFQCL    FQCall     FQTime    FQMax  FQMin  AvgFetchLat    FLMax     FLMin    FCalls   FTime  Tot_Elapsed_Time");
        output.flush();
    }

    public void run() {
        startTime = System.currentTimeMillis();
        lastPrintTime = startTime;
        long t0, t1;

        long cnt =0;
        try {
            while ( cancelThread == false ) {
                t0 = System.currentTimeMillis();
                t1 = System.currentTimeMillis();
                try {
                    synchronized (this) {
                        this.wait(interval);
                    }
                } catch (InterruptedException ex) {
                    // Nothing to do
                }


                // print stats
                printStats();

            }   // end while
        } finally {
            if (output != null) {
                output.flush();
                output.close();
            }
            System.err.println("Exiting AdvQueryPrintQueryStats Thread");
       }
        // Check to see if last stats printed
        if (cancelThread && this.cancelStatsPrinted == false) {
            printStats();
        }
    }   // end run
    
    
    /**
     * Print out the statistics gathered for the last interval
     */
    private void printStats() {
        long currentItems = 0;
        long intervalErrors = 0;
        long currentTime = System.currentTimeMillis();
        // use this as approximation of  total elapsed time
        long currentElapsedTime = 0; //currentTime - startTime;
        long intervalElapsedTime = 0; // currentTime - lastPrintTime;
        lastPrintTime = currentTime;
        
         if (cancelThread) {
            cancelStatsPrinted = true;
        }
       // Current total statistics
        AdvQueryPerformanceStatistics[] currentStats = 
                new AdvQueryPerformanceStatistics[threads.length];
        // Used to calculate interval statistics
        AdvQueryPerformanceStatistics[] intervalStats = 
                new AdvQueryPerformanceStatistics[threads.length];
        
        long currentTotalOps = 0;
        double intervalAveOps = 0;
        double intervalAveFirsts = 0;
        double intervalAveFetches = 0;
        long intervalTotalFetches = 0;
        long intervalTotalFetchTime = 0;
        long intervalResults = 0;
        double intervalTotalFirsts = 0;
        double intervalTotalFirstTime = 0;
        long intervalMinFetch = Long.MAX_VALUE;
        long intervalMaxFetch = Long.MIN_VALUE;
        long intervalMinFirst = Long.MAX_VALUE;
        long intervalMaxFirst = Long.MIN_VALUE;

        long totalItemCount = 0;
        
        for (int i = 0; i < threads.length; i++) {
            currentStats[i] = threads[i].getQueryPerformanceStats();
            
            intervalStats[i] = new AdvQueryPerformanceStatistics();
            
            // Operations/Records
            intervalStats[i].totalops = currentStats[i].totalops -
                    saveStats[i].totalops;            
            currentTotalOps += currentStats[i].totalops;
                      
            // Elapsed time
            intervalStats[i].elapsed = currentStats[i].elapsed -
                    saveStats[i].elapsed;
            currentElapsedTime += currentStats[i].elapsed;
            intervalElapsedTime += intervalStats[i].elapsed;
                        
            if (intervalStats[i].elapsed > 0) {
                intervalStats[i].aveops = intervalStats[i].totalops/
                    (intervalStats[i].elapsed/1000.0);
            }

            intervalStats[i].itemCount = currentStats[i].itemCount -
                    saveStats[i].itemCount;
            totalItemCount += currentStats[i].itemCount;
            
            intervalStats[i].errorCount = currentStats[i].errorCount -
                    saveStats[i].errorCount;

            // First Fetches
            intervalStats[i].totalfirsttime = currentStats[i].totalfirsttime -
                    saveStats[i].totalfirsttime;            
            intervalTotalFirstTime += intervalStats[i].totalfirsttime;
            
            intervalStats[i].totalfirsts = currentStats[i].totalfirsts -
                    saveStats[i].totalfirsts;
            intervalTotalFirsts += intervalStats[i].totalfirsts;

            /* Calculate stats for first fetches */
            if (intervalStats[i].totalfirsts > 0) {
                intervalStats[i].avefirsts =
                        intervalStats[i].totalfirsttime /
                        intervalStats[i].totalfirsts;
                
                if (currentStats[i].minfirst < Long.MAX_VALUE) {
                    intervalStats[i].minfirst = currentStats[i].minfirst;
                    if (intervalMinFirst > intervalStats[i].minfirst) {
                        intervalMinFirst = intervalStats[i].minfirst;
                    }
                }

                if (currentStats[i].maxfirst > Long.MIN_VALUE) {
                    intervalStats[i].maxfirst = currentStats[i].maxfirst;
                    if (intervalMaxFirst < intervalStats[i].maxfirst) {
                        intervalMaxFirst = intervalStats[i].maxfirst;
                    }
                }
            
            }
            
            // Subsequent Fetches
            intervalStats[i].totalfetches = currentStats[i].totalfetches -
                    saveStats[i].totalfetches;
            intervalTotalFetches += intervalStats[i].totalfetches;
            intervalStats[i].totalfetchtime = currentStats[i].totalfetchtime -
                    saveStats[i].totalfetchtime;
            intervalTotalFetchTime += intervalStats[i].totalfetchtime;

            
            if (intervalStats[i].totalfetches > 0) {
                /* Calculate stats for subsequent fetches */
                intervalStats[i].avefetches = 
                        intervalStats[i].totalfetchtime / 
                        intervalStats[i].totalfetches; 
                
                if (currentStats[i].minfetch < Long.MAX_VALUE) {            
                    intervalStats[i].minfetch = currentStats[i].minfetch;
                    if (intervalMinFetch > intervalStats[i].minfetch) {
                        intervalMinFetch = intervalStats[i].minfetch;
                    }
                }

                if (currentStats[i].maxfetch > Long.MIN_VALUE) {
                    intervalStats[i].maxfetch = currentStats[i].maxfetch;
                    if (intervalMaxFetch < intervalStats[i].maxfetch) {
                        intervalMaxFetch = intervalStats[i].maxfetch;
                    }
                }
            }

	
            currentItems += intervalStats[i].itemCount;
            intervalErrors += intervalStats[i].errorCount;
            intervalAveOps += intervalStats[i].aveops;
            //intervalAveFetches += intervalStats[i].avefetches;
            intervalResults += intervalStats[i].totalops;
            // Save current state for next time
            saveStats[i] = currentStats[i];
        }   // end for
        
      
        itemCount += intervalResults; //currentItems;
        numErrors += intervalErrors;
        
        currentElapsedTime /= threads.length;
        intervalElapsedTime /= threads.length;
        intervalTotalFirstTime /= threads.length;
        intervalTotalFetches /= threads.length;
        intervalTotalFirsts /= threads.length;
        intervalTotalFetchTime /= threads.length;
        
        // TODO this or currentAveOps for results/sec
        double intervalAveOps2 = 0;
        if (intervalElapsedTime <= 0) {
            return;
        }
        intervalAveOps2 = 
                intervalResults/ (intervalElapsedTime/1000.0);

        
        /* Calculate stats for first op */
        if (intervalTotalFirsts > 0) {
            intervalAveFirsts = intervalTotalFirstTime /
                    (double) intervalTotalFirsts;
        }

        if (intervalTotalFetches > 0) {
            /* Calculate stats for subsequent fetches */
            intervalAveFetches = 
                    intervalTotalFetchTime/ intervalTotalFetches; 
        }
        
      
        if (intervalAveOps >= Long.MAX_VALUE) {
            System.err.println("Results/second was MAX (unset) so setting to zero");
            intervalAveOps = 0.0;
        }
        if (intervalAveOps2 >= Long.MAX_VALUE) {
            intervalAveOps2 = 0.0;
        }

        if (intervalMinFetch == Long.MAX_VALUE) {
            intervalMinFetch = 0;
        }
        if (intervalMaxFetch == Long.MIN_VALUE) {
            intervalMaxFetch = 0;
        }
        
        if (intervalMinFirst == Long.MAX_VALUE) {
            intervalMinFirst = 0;
        }
        if (intervalMaxFirst == Long.MIN_VALUE) {
            intervalMaxFirst = 0;
        }

        //intervalAveFirsts /= threads.length;
        intervalAveOps2 /= threads.length;
        //intervalAveFetches /= threads.length;

        double numThreads = threads.length;
        
        try {
          output.format("%1$10d %2$9d  %3$7d        %4$9.2f   %5$10d   %6$7.2f   %7$7.2f   %8$7.2f    %9$5d  %10$5d    %11$9.2f    %12$5d     %13$5d   %14$7d %15$7d        %16$10d\n",
                currentTime/1000, intervalResults, intervalErrors,
                intervalAveOps2, currentTotalOps, intervalAveFirsts,
                  intervalTotalFirsts, intervalTotalFirstTime, intervalMaxFirst,
                  intervalMinFirst, intervalAveFetches, intervalMaxFetch,
                  intervalMinFetch, intervalTotalFetches,
                  intervalTotalFetchTime, currentElapsedTime);
          output.flush();
      } catch (Exception ex) {
            System.err.println("Error printing statistics to file");
            try { 
                ex.printStackTrace(System.err);
                output.flush();
            } catch (Exception e) {}
            

        }
     }

}