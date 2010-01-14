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



package com.sun.honeycomb.test.stress;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Date;

public class AnalyzeStress
{
    private static void printUsage()
    {
        String msg = "";
        msg = "       cat store-stress.out | ";
        msg += "java com.sun.honeycomb.test.stress.AnalyzeStress";
        System.err.println("NAME");
        System.err.println("    AnalyzeStress - Analyze stress output files");
        System.err.println();
        System.err.println("SYNOPSIS");
        System.err.println("    java AnalyzeStress");
        System.err.println();
        System.err.println("DESCRIPTION");
        System.err.println("    Reads a stress output file from stdin.");
        System.err.println("    Prints analysis to stdout.");
        System.err.println();
        System.err.println("EXAMPLES");
        msg = "    cat store-stress.out | ";
        msg += "java com.sun.honeycomb.test.stress.AnalyzeStress";
        msg += "(TEST_SIZE, VERBOSE, INTERVAL)";
        System.err.println(msg);
    }
    
    public static void main(String [] args) throws Throwable
    {
        AnalyzeStress analyzer = new AnalyzeStress();
        analyzer.analyze(System.in, System.out, args);
    }

    private long startTime;
    private long endTime;
    private long intervalEndTime;
    private long okOps; // successful ops
    private long errorOps;
    private long totalBytes;
    private long opMinBytes;
    private long opMinBytesDuration;
    private long opMaxBytes;
    private long opMaxBytesDuration;
    private long totalDuration; // uses actual duration of successful ops
    private long opMinDuration;
    private long opMinDurationBytes;
    private long opMaxDuration;
    private long opMaxDurationBytes;
    private String op = "no-op";
    private String size;
    private boolean verbose;
    private boolean splitIntoIntervals = false;
    private long interval= Long.MAX_VALUE;

    public AnalyzeStress()
    {
        this.startTime = Long.MAX_VALUE;
        this.endTime = Long.MIN_VALUE;
        this.okOps = 0;
        this.errorOps = 0;
        this.totalBytes = 0;
        this.opMinBytes = Long.MAX_VALUE;
        this.opMinBytesDuration = -1;
        this.opMaxBytes = Long.MIN_VALUE;
        this.opMaxBytesDuration = -1;
        this.totalDuration = 0;
        this.opMinDuration = Long.MAX_VALUE;
        this.opMinDurationBytes = -1;
        this.opMaxDuration = Long.MIN_VALUE;
        this.opMaxDurationBytes = -1;
        this.intervalEndTime = Long.MAX_VALUE;
    }

    public void analyze(InputStream input, OutputStream output, String [] args)
        throws Throwable
    {
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(input));
        PrintWriter writer =
            new PrintWriter(output);

        // Get Input Args
        size = args[0];
        verbose = (new Boolean(args[1])).booleanValue();
        interval = new Integer(args[2]).longValue(); // In Minutes
        
        if (interval != 0) {
        	splitIntoIntervals = true;
        	interval = interval * 60 * 1000; // Convert to ms
        }
        
        String s = null;
        while ((s = reader.readLine()) != null) {
            if (s.startsWith("#"))
                continue;
            InputOutput line = InputOutput.readLine(s);
            
            if ( splitIntoIntervals && (line.t0 > intervalEndTime)) {
            	printOutput(writer);
            	resetValues();
            }
            
            op = line.op;
            if (line.t0 < startTime) {
                startTime = line.t0;
                intervalEndTime = line.t0 + interval;
            }
            if (line.t1 > endTime) {
                endTime = line.t1;
            }

            // don't record stats about errors beyond the total
            // duration and number of errors
            if (!line.ok) {
                errorOps++;
                continue;
            }

            long duration = line.t1 - line.t0;
            if (duration < opMinDuration) {
                opMinDuration = duration;
                opMinDurationBytes = line.sizeBytes;
            }
            if (duration > opMaxDuration) {
                opMaxDuration = duration;
                opMaxDurationBytes = line.sizeBytes;
            }

            if (line.sizeBytes < opMinBytes) {
                opMinBytes = line.sizeBytes;
                opMinBytesDuration = duration;
            }
            if (line.sizeBytes > opMaxBytes) {
                opMaxBytes = line.sizeBytes;
                opMaxBytesDuration = duration;
            }

            okOps++;
            totalDuration += duration;
            totalBytes += line.sizeBytes;
            
        } 
        printOutput(writer);
    }
    
    public void printOutput(PrintWriter writer)
    {
        long avgFilesize = (okOps != 0 ? (totalBytes / okOps) : 0);
        long avgOpTime = (okOps != 0 ? (totalDuration / okOps) : 0);

        // The difference between elapsedTime and totalDuration is that
        // totalDuration attempts to sum just the time it took to do the
        // operation, whereas elapsedTime estimates that based on
        // earliest entry and latest entry timewise.  totalDuration
        // should be sufficient, but there is a chance the log times
        // might be wonky so we end up printing out both just for 
        // reference.  They should be very close in value.
        // Note that failures will also be counted in this value but
        // not in the totalDuration value.
        long elapsedTime = endTime - startTime;

        // for store and retrieve, it makes sense to talk about bytes.
        // for query, not so much...
        boolean sizeMatters = 
            (op.equals(InputOutput.STORE) || op.equals(InputOutput.RETRIEVE));

       
        if (splitIntoIntervals) {
        	Date date = new Date(startTime);
        	writer.println(startTime + " (" + date + ")");
        }
        if (verbose) {
            writer.println(op + " " + size + ": ");
        	writer.println("  " + errorOps + " total failed ops");
        	writer.println("  " + okOps + " total successful ops");
        	writer.println("  " + totalDuration +
        		" ms total successful op duration time");
        	writer.println("  " + elapsedTime + " ms total elapsed test time" +
            	" (should be similar to total op " +
            	"duration unless many failures)");
        	writer.println("  " + avgOpTime + " ms avg per file");
        	writer.println("  " + getOpsPerSecString(okOps, totalDuration) +
            	" aggregate");
        	if (sizeMatters) {
        		writer.println("  " + totalBytes + " total bytes");
        		writer.println("  " + getBandwidthString(totalBytes,
        			totalDuration) + " aggregate bandwidth");
        		writer.println("  " + avgFilesize + " bytes avg per file");
        		writer.println("  " + formatBytes(opMinBytes) +
        		                " smallest file");
        		writer.println("  " + formatBytes(opMaxBytes) +
        		                " largest file");
        		writer.println("  " + opMinBytesDuration +
        		                " ms for smallest file");
        		writer.println("  " + opMaxBytesDuration +
        		                " ms for largest file");
        		writer.println("  " + getBandwidthString(opMinBytes,
        			opMinBytesDuration) + " smallest file");
        		writer.println("  " + getBandwidthString(opMaxBytes,
        			opMaxBytesDuration) + " largest file");
        	}

        	writer.println("  " + opMinDuration + " ms quickest op");
        	writer.println("  " + opMaxDuration + " ms slowest op");
        	if (sizeMatters) {
        		writer.println("  " + opMinDurationBytes +
        		                " bytes quickest op");
        		writer.println("  " + opMaxDurationBytes +
        		                " bytes slowest op");
        		writer.println("  " + getBandwidthString(opMinDurationBytes,
        			opMinDuration) + " quickest file");
        		writer.println("  " + getBandwidthString(opMaxDurationBytes,
        			opMaxDuration) + " slowest file");
        	}
        	writer.println();
        	writer.flush();
        	
        } else { // Not Verbose
            // OUTPUT
            writer.print("  " + op + " " + size + ": ");
            writer.print(formatBytes(opMinBytes) + " - ");
            writer.print(formatBytes(opMaxBytes) + ", ");
            writer.print("avg " + formatBytes(avgFilesize));
            writer.print("\n");
            
            writer.print("  Throughput: ");
            writer.print(getOpsPerSecString(okOps, totalDuration) + ", ");
            writer.print(getBandwidthString(totalBytes, totalDuration));
            writer.print("\n");
           
            writer.print("  Latency: ");
            writer.print(opMinDuration + " min, ");
            writer.print(opMaxDuration + " max, ");
            writer.print(avgOpTime + " avg ms/op");
            writer.print("\n");
            
            writer.print("  Ops count: ");
            writer.print(okOps + " OK, ");
            writer.print(errorOps + " ERR");
            writer.print("\n");
            
            writer.println("");
            writer.flush();
        }
    }

    
    public void resetValues() {
        this.startTime = Long.MAX_VALUE;
        this.endTime = Long.MIN_VALUE;
        this.okOps = 0;
        this.errorOps = 0;
        this.totalBytes = 0;
        this.opMinBytes = Long.MAX_VALUE;
        this.opMinBytesDuration = -1;
        this.opMaxBytes = Long.MIN_VALUE;
        this.opMaxBytesDuration = -1;
        this.totalDuration = 0;
        this.opMinDuration = Long.MAX_VALUE;
        this.opMinDurationBytes = -1;
        this.opMaxDuration = Long.MIN_VALUE;
        this.opMaxDurationBytes = -1;
        this.intervalEndTime = Long.MAX_VALUE;
    }
    
    
    private static final Object [][] OPS_PER_SEC_METRICS = new Object[][] {
        {new Integer(1), "ops/sec"},
        {new Integer(60), "ops/min"},
        {new Integer(60), "ops/hour"}
    };
    public static String formatOpsPerSec(double ops_s)
    {   
    	DecimalFormat df = new DecimalFormat( "##.##" );
    	int metric = 0;
    	String answer = " " + df.format(ops_s) + " " + 
    	                OPS_PER_SEC_METRICS[metric][1];
        while (ops_s < 1 && metric < 2) {
            metric++;
            Integer multiplier = (Integer) OPS_PER_SEC_METRICS[metric][0];
            ops_s *= (int) (multiplier.intValue());
            answer += " (" + df.format(ops_s) + " " +
                        OPS_PER_SEC_METRICS[metric][1] + ")";
        }
        return answer;
    }

    private static final Object [][] BW_METRICS = new Object[][] {
        {new Integer(1), "bytes/s"},
        {new Integer(1024), "KB/s"},
        {new Integer(1024), "MB/s"},
        {new Integer(1024), "GB/s"}
    };
    public static String formatBandwidth(double bytes_per_sec)
    {
    	DecimalFormat df = new DecimalFormat( "##.##" );
        int metric = 0;
        while (bytes_per_sec > 1024 && metric < 3) {
            metric++;
            Integer divisor = (Integer) BW_METRICS[metric][0];
            bytes_per_sec /= (double) (divisor.intValue());
        }
        return "" + df.format(bytes_per_sec) + " " + BW_METRICS[metric][1];
    }

    private static final Object [][] BYTE_METRICS = new Object[][] {
        {new Integer(1), "bytes"},
        {new Integer(1024), "KB"},
        {new Integer(1024), "MB"},
        {new Integer(1024), "GB"}
    };
    
    public static String formatBytes(long bytes) {
    	DecimalFormat df = new DecimalFormat( "##.##" );
        int metric = 0;
        while (bytes > 1024 && metric < 3) {
            metric++;
            Integer divisor = (Integer) BYTE_METRICS[metric][0];
            bytes /= (double) (divisor.intValue());
        }
        return df.format(bytes) + " " + BYTE_METRICS[metric][1];
    }
    
    
    public static String getBandwidthString(long bytes, long msecs) {
        double bandwidth = 0;
        String answer = "n/a";
        if (msecs != 0) {
                bandwidth = ((double)bytes * (double)1000) / (double)(msecs);
                answer = formatBandwidth(bandwidth);
        }

        return (answer);
    }

    public static String getOpsPerSecString(long ops, long msecs) {
        double opsPerSec = 0;
        String answer = "n/a";
        if (msecs != 0) {
                opsPerSec = ((double)ops * (double)1000) / (double)(msecs);
                answer = formatOpsPerSec(opsPerSec);
        }

        return (answer);
    }
}
