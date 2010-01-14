package com.sun.honeycomb.testcmd.cmd;

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



import com.sun.honeycomb.client.NameValueRecord;

import com.sun.honeycomb.testcmd.common.HoneycombTestCmd;
import com.sun.honeycomb.testcmd.common.HoneycombTestException;
import com.sun.honeycomb.testcmd.common.RunCommand;

import java.util.*;
import java.io.*;
import java.net.*;

public class FillData extends HoneycombTestCmd {

    String host = null;

    boolean binary = false;
    boolean retry = false;
    int     delay = 0;
    int metadata = 0;
    boolean pipeMode = false;
    long min = -1, max;
    long step;
    String filename = null;
    NameValueRecord nvr = createRecord();
    static String test_id = null;
    static long total_bytes;
    static long ttime = 0;
    static Date start = new Date();
    static int files = 0;

    private void usage() {
        System.err.println("Usage: java FillData -h host -o out-file [OPTIONS] range <min> <max> <step>");
        System.err.println ("\nValid options include:\n");
        System.err.println("    -b     binary");
        System.err.println("    -m     metadata");
        System.err.println("    -r     retry on failure");
        System.err.println("    -p     sleep for <sleep> seconds on retry");
        System.err.println("    --     stdout matches out-file");
        System.err.println();
        System.err.println("    step:  negative = random sizes, total < -step");
        System.err.println();
        System.err.println("needed metadata:\n" + baseMD() + ", string test_id");
		
        System.exit(1);
    }

    public static void main(String args[]) throws HoneycombTestException{
        new FillData(args);
    }
        
    public FillData(String args[]) throws HoneycombTestException{

        verbose = false;

        if (args.length == 0) {
            usage();
        }

        for (int i=0; i<args.length; i++) {

            if (args[i].equals("-h")) {
                if (i+1 >= args.length)
                    usage();
                // trim in case someone uses quotes :-)
                host = args[i+1].trim(); 
                i++;
            } else if (args[i].equals("-b")) {
                binary = true;
            } else if (args[i].equals("-r")) {
                retry = true;
            } else if (args[i].equals("-p")) {
                // implicit retry
                retry = true;
                // set the delay here
                try {
                    delay = (Integer.parseInt (args[i+1])) * 1000;
                    i++;
                } catch (Exception e) {
                    System.err.println (
                        "invalid value for -p: '" + args[i+1] + "'");
                    usage();
                }
            } else if (args[i].equals("-m")) {
                metadata = MD_ALL;
            } else if (args[i].equals("-o")) {
                i++;
                if (i >= args.length)
                    usage();
                filename = args[i];
            } else if (args[i].equals("--")) {
                pipeMode = true;
            } else if (args[i].equals("range")) {
                if (i+3 >= args.length) {
                    System.err.println("Expected range <min> <max> <step>");
                    usage();
                }
                min = parseSize(args[i+1]);
                max = parseSize(args[i+2]);
                step = parseSize(args[i+3]);
                i += 3;
            } else {
                System.err.println("Unexpected: " + args[i]);
                usage();
            }
        }

        if (host == null) {
            System.err.println("Need -h");
            usage();
        }
        if (min < 0) {
            System.err.println("Need range");
            usage();
        }
        if (min > max) {
            System.err.println("min must be >= max");
            usage();
        }
        if (filename != null) {
            try {
                fo = new FileWriter(filename, true); // append=true
                String clnt = InetAddress.getLocalHost().toString();
                flog("#S FillData [" + host + "] (" + clnt + ") " + 
						new Date() + "\n");

                flog("#S range " + min + " " + max + " " + step + "\n");
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        try {
            test_id = new String(getRandomChars(32));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        nvr.put("test_id", test_id);
        String s = "#M\ttest_id\t" + test_id + "\n";
        flog(s);
        if (pipeMode)
            System.out.print(s);

        initHCClient(host);

        Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown(),
                                                        "Shutdown"));
        if (!pipeMode)
            System.out.println("START: " + start);
        if (step < 0)
            doRandom();
        else
            doSeries();

        done = true;
    }

    private void doSeries() {
        int total = (int) ((max - min + 1) / step);
        long[] times = new long[total+1];

        long size = min;
        int failures = 0;
        String[] ss = null;
        for (int i=0; size<=max; size += step, i++) {
            while (true) {
                try {
                    ss = createFileAndStore(ss, size, binary, metadata, nvr,
							true, times, i);
                    total_bytes += size;
                    ttime += times[i];
                    files++;

                    String s = "" + size + '\t' +
					ss[0] + '\t' +  // filename
					ss[1] + '\t' +  // oid
					ss[2] + '\t' +  // sha1
					ss[3] + '\t' +  // store date
					binary + '\t' + metadata + '\t' +
					times[i] + '\n';
                    if (pipeMode)
                        System.out.print(s);
                    flog(s);
 
                    failures = 0;

                    if (!pipeMode)
                        dot(".");
                } catch (Exception e) {
                    flex("size=" + size, e);
                    if (retry) {
                        if (delay > 0) {
                            try {
                                Thread.sleep (delay);
                            } catch (InterruptedException ie) { }
                        }
                        if (!pipeMode) {
                            dot("x");
                            closeDots();
                            System.out.println(e.toString());
                        }

                        // retry same size
                        continue;
                    } 
                    if (!pipeMode) {
                        dot("x");
                        closeDots();
                    }
                    System.err.println("Time: " + new Date());
                    e.printStackTrace();
                    failures++;
                    if (failures > 3)
                        break;
                    closeDots();
                }
                break;
            }
            if (!pipeMode)
                closeDots();
        }
        if (!pipeMode)
            closeDots();
        if (ss != null) {
            try {
                File f = new File(ss[0]);
                f.delete();
            } catch (Exception e) {}
        }
    }

    private void doRandom() {
        try {
            initRandom();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        step *= -1;
        long range = max - min;

        if (step < max) {
            System.err.println("FillData: 'step' must be > max for random");
            usage();
        }
        System.err.println("  SIZE         TIME    MB/sec");
        int count = 0;
        long times[] = new long[1];
        while (step > 0) {
            long size = min + (long) (rand.nextDouble() * (double)range);
            while (true) {
                try {
                    String[] ss = createFileAndStore(size, binary, metadata, 
							nvr, true, times, 0);
                    if (times[0] == 0)
                        System.err.println("  " + size + "     " + times[0] +
                                    "    " + "infinite!");
                    else
                        System.err.println("  " + size + "     " + times[0] + 
				"    " + (((1000*size)/1048576)/times[0]));

                    String s = "" + size + '\t' +
					ss[0] + '\t' + 
					ss[1] + '\t' + 
					ss[2] + '\t' +
					binary + '\t' + metadata + '\t' +
					times[0] + '\n';
                    if (pipeMode)
                        System.out.print(s);
                    flog(s);
                    File f = new File(ss[0]);
                    f.delete();

                    count++;
                    step -= size;
                    total_bytes += size;
                    ttime += times[0];
                    files++;
                } catch (Exception e) {
                    flex("size=" + size, e);
                    if (!pipeMode) {
                        dot("x");
                        closeDots();
                    }
                    if (retry) {
                        if (delay > 0) {
                            try {
                                Thread.sleep (delay);
                            } catch (InterruptedException ie) {}
                        }
                        continue;
                    } 
                    e.printStackTrace();
                    System.exit(1);
                }
                break;
            }
        }
    }

    private static class Shutdown implements Runnable {
        public void run() {
            System.err.println();
            Date now = new Date();
            if (!done)
                flout("FillData INTERRUPTED\n");
            flout("FillData START:  " + start + "\n");
            flout("FillData FINISH: " + now + "\n");
            flout("FillData test_id " + test_id + "\n");
            flout("FillData total files: " + files + "\n");
            flout("FillData total bytes: " + total_bytes + "\n");

            reportExceptions("FillData");

            if (ttime > 0) {
                flout("FillData API thruput (bytes/sec): " + 
					(1000 * total_bytes / ttime) + "\n");
            }
            long deltat = now.getTime() - start.getTime();
            if (deltat > 0)
                flout("FillData total thruput (bytes/sec): " + 
					(1000 * total_bytes / deltat) + "\n");
            try {
                new File(lastfile).delete();
            } catch (Exception e) {}
        }
    }
}
