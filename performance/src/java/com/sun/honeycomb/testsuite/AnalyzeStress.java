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



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

public class AnalyzeStress
{
    private static void printUsage()
    {
        System.err.println("NAME");
        System.err.println("       AnalyzeStress - Analyze stress output files");
        System.err.println();
        System.err.println("SYNOPSIS");
        System.err.println("       java AnalyzeStress");
        System.err.println();
        System.err.println("DESCRIPTION");
        System.err.println("       Reads a stress output file from stdin.");
        System.err.println("       Prints analysis to stdout.");
        System.err.println();
        System.err.println("EXAMPLES");
        System.err.println("       cat store-stress.out | java com.sun.honeycomb.test.stress.AnalyzeStress");
    }

    public static void main(String [] args) throws Throwable
    {
        AnalyzeStress analyzer = new AnalyzeStress();
        analyzer.analyze(System.in, System.out);
    }

  /*Aggregate stats*/
  /* Try to find earliest and latest time stamps to establish total time */
  private long startTime;
  private long endTime;

  /* Total number of ops and total number of bytes, if applicable */
  private long totalOps;
  private long totalBytes;

  /* Individual op stats */

  /* Individual operation durations to calculate average/max/min/std dev */
  private long opMinDuration;
  private long opMaxDuration;
  private long opTotalDuration;
  private long opDurationSquares;

  /* Individual operation bytes/sec to calculate average/max/min/stddev */
  private double opMinBytesPerSec;
  private double opMaxBytesPerSec;
  private double opTotalBytesPerSec;
  private double opBytesPerSecSquares;

  public AnalyzeStress()
  {
    this.startTime = Long.MAX_VALUE;
    this.endTime = Long.MIN_VALUE;
    this.totalOps = 0;
    this.totalBytes = 0;
    this.opMinDuration = Long.MAX_VALUE;
    this.opMaxDuration = Long.MIN_VALUE;
    this.opTotalDuration = 0;
    this.opDurationSquares = 0;
    this.opMinBytesPerSec = Long.MAX_VALUE;
    this.opMaxBytesPerSec = Long.MIN_VALUE;
    this.opTotalBytesPerSec = 0;
    this.opBytesPerSecSquares = 0;
  }

  public void analyze(InputStream input, OutputStream output)
      throws Throwable
    {
	BufferedReader reader =
	    new BufferedReader(new InputStreamReader(input));
	PrintWriter writer =
	    new PrintWriter(output);
	
	String s = null;
	String op = null;
	long elapsed = 0;

	boolean notempty = false;
	
	while ((s = reader.readLine()) != null) {
	    notempty = true;

	    InputOutput line = InputOutput.readLine(s);
	    
	    op = line.op;
	    if (line.t0 < startTime) {
		startTime = line.t0;
	    }
	    if (line.t1 > endTime) {
		endTime = line.t1;
	    }
	    
	    totalOps++;
	    
	    elapsed = line.t1 - line.t0;
	    if (elapsed < opMinDuration) {
		opMinDuration = elapsed;
	    }
	    if (elapsed > opMaxDuration) {
		opMaxDuration = elapsed;
	    }
	    
	    opTotalDuration += elapsed;
	    opDurationSquares += elapsed * elapsed;
	    
	    if (line.sizeBytes > 0) {
		totalBytes += line.sizeBytes;
		
		double bw = line.sizeBytes/(elapsed/1000.0);
		opTotalBytesPerSec += bw;
		opBytesPerSecSquares += bw * bw;
		
		if (bw < opMinBytesPerSec) {
		    opMinBytesPerSec = bw;
		}
		if (bw > opMaxBytesPerSec) {
		    opMaxBytesPerSec = bw;
		}
		
	    }
	}

	if (notempty) {
	    long elapsedTime = endTime - startTime;
	    
	    /* Overall aggregate opsrate and throughput */
	    double aggOpsPerSec =
		((double) totalOps * (double) 1000) / (double) (elapsedTime);
	    
	    double aggBytesPerSec =
		((double) totalBytes * (double) 1000) / (double) (elapsedTime);
	    
	    /* Calculate average & standard deviation for individual op durations */
	    double opAveDuration = (totalOps != 0) ? opTotalDuration/totalOps : 0;
	    double opStdDevDuration = (totalOps > 1)
		? java.lang.Math.sqrt((opDurationSquares - ((opTotalDuration
							     * opTotalDuration)/totalOps))/(totalOps - 1)) : 0;
	
	    /* Calculate average & standard deviation for individual bytes/sec */
	    double opAveBytesPerSec = (totalOps != 0) ? opTotalBytesPerSec/totalOps : 0;
	    double opStdDevBytesPerSec = (totalOps > 1)
		? java.lang.Math.sqrt((opBytesPerSecSquares - ((opTotalBytesPerSec
								* opTotalBytesPerSec)/totalOps))/(totalOps - 1)) : 0;
	    
	    writer.println(op);
	    writer.println("Total:  " + totalOps + " ops");
	    if ((totalBytes > 0) && !(op.equals("DEL") || op.equals("AMD")))
		writer.println("Total:  " + totalBytes + " bytes");
	    writer.println("Total:  " + elapsedTime + " ms");
	    writer.println("Aggregate:  " + aggOpsPerSec + " ops/sec");
	    if ((totalBytes > 0) && !(op.equals("DEL") || op.equals("AMD")))
		writer.println("Aggregate:  " + formatBandwidth(aggBytesPerSec));
	    writer.println("Individual Total: " + opTotalDuration + " ms");
	    writer.println("Individual Ave:  " + opAveDuration + " ms");
	    writer.println("Individual Min: " + opMinDuration + " ms");
	    writer.println("Individual Max: " + opMaxDuration + " ms");
	    writer.println("Individual StdDev: " + opStdDevDuration + " ms");
	    writer.println("Individual SquaresSum: " + opDurationSquares + " ms");
	    if ((totalBytes > 0) && !(op.equals("DEL") || op.equals("AMD"))) {
		writer.println("Individual Total: " + formatBandwidth(opTotalBytesPerSec));
		writer.println("Individual Ave: " + formatBandwidth(opAveBytesPerSec));
		writer.println("Individual Min: " + formatBandwidth(opMinBytesPerSec));
		writer.println("Individual Max: " + formatBandwidth(opMaxBytesPerSec));
		writer.println("Individual StdDev: " + formatBandwidth(opStdDevBytesPerSec));
		writer.println("Individual SquaresSum: " + formatBandwidth(opBytesPerSecSquares));
	    }
	}
	else {
	    writer.println("No results");
	}
	writer.flush();
    }
    
    private static final Object [][] OPS_PER_SEC_METRICS = new Object[][] {
        {new Integer(1), "ops/sec"},
        {new Integer(60), "ops/min"},
        {new Integer(60), "ops/hour"}
    };
    public static String formatOpsPerSec(double ops_s)
    {
        int metric = 0;
        while (ops_s < 1 && metric < 2) {
            metric++;
            Integer multiplier = (Integer) OPS_PER_SEC_METRICS[metric][0];
            ops_s *= (double) (multiplier.intValue());
        }
        return "" + ops_s + " " + OPS_PER_SEC_METRICS[metric][1];
    }

    private static final Object [][] BW_METRICS = new Object[][] {
        {new Integer(1), "bytes/s"},
        {new Integer(1024), "KB/s"},
        {new Integer(1024), "MB/s"},
        {new Integer(1024), "GB/s"}
    };
    public static String formatBandwidth(double bytes_per_sec)
    {
        int metric = 0;
        while (bytes_per_sec > 1024 && metric < 3) {
            metric++;
            Integer divisor = (Integer) BW_METRICS[metric][0];
            bytes_per_sec /= (double) (divisor.intValue());
        }
        return "" + bytes_per_sec + " " + BW_METRICS[metric][1];
    }
}
