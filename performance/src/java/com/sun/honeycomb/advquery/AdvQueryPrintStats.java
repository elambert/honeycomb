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
 *
 */
public class AdvQueryPrintStats extends Thread {
    
    private long runtimeMillis = 0;
    private long numErrors = 0;

    private boolean withMD;
    private long storeCount = 0;
    private long interval = 600; // # of milliseconds
    AdvQueryMDStoreThread[] threads = null;
    private boolean cancelThread = false;
    private boolean cancelStatsPrinted = false;
    private File outFile = null;
    private PrintWriter output = null;
    private String filename = "";
    private long lastPrintTime = 0;
    private long startTime = 0;
    private double serviceTime = 0;

  /** 
   * Creates a new instance of AdvQueryPrintStats
   *  
   */
    public AdvQueryPrintStats(long intervalMillis,
            long runtimeMillis,
            String outputFilename, 
            ArrayList threadList,
            boolean withMD) throws FileNotFoundException, SecurityException {
        this.interval = intervalMillis;
        Object[] thrds = threadList.toArray();
        threads = new AdvQueryMDStoreThread[thrds.length];
        for (int i = 0; i < thrds.length; i++) {
            threads[i] = (AdvQueryMDStoreThread) thrds[i];
        }

        this.withMD = withMD;
        this.filename = outputFilename;
        this.runtimeMillis = runtimeMillis;
        
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
        output.println("# Time       Objects      Err     Rate      Mean   Total_Objects");
        output.flush();

    }

    public void run() {
        startTime = System.currentTimeMillis();
        lastPrintTime = startTime;
        long t0, t1;

        long cnt =0;
        try {
            while (cancelThread == false && 
                    (runtimeMillis < 0 || 
                    runtimeMillis > (System.currentTimeMillis() - startTime))) {
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
        }
        
        // Check to see if last stats printed
        if (cancelThread && this.cancelStatsPrinted == false) {
            printStats();
        }
        
    }   // end run

    private void printStats() {
        long currentStores = 0;
        long currentErrors = 0;
        long currentTime = System.currentTimeMillis();
        double currentServiceTime = 0;

        if (cancelThread) {
            cancelStatsPrinted = true;
        }
        
        for (int i = 0; i < threads.length; i++) {
            currentStores += threads[i].getStoreCount();
            currentErrors += threads[i].getNumErrors();
            currentServiceTime += threads[i].getServiceTIme();
        }
        long intervalStores = currentStores - storeCount;
        long intervalErrors = currentErrors = numErrors;
        currentServiceTime /= 1000.0;
        double intervalServiceTime = currentServiceTime  - serviceTime;
        double elapsedTime = (currentTime - lastPrintTime) / 1000.0;
        double average = intervalServiceTime / intervalStores;
        double rate = intervalStores / elapsedTime;
        
        storeCount = currentStores;
        numErrors = currentErrors;
        serviceTime = currentServiceTime;
        lastPrintTime = currentTime;

        try {
            output.format("%1$10d %2$9d  %3$7d    %4$5.2f   %5$7.3f    %6$12d\n",
                currentTime/1000, intervalStores, intervalErrors,
                rate, average, currentStores);
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
