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



import com.sun.honeycomb.testcmd.common.HoneycombTestCmd;
import com.sun.honeycomb.testcmd.common.HoneycombTestException;
import com.sun.honeycomb.testcmd.common.RunCommand;

import com.sun.honeycomb.common.*;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.SystemRecord;

import java.util.*;
import java.io.*;

public class DumpMetadata extends HoneycombTestCmd {

    String host = null;

    boolean retrieve = false;
    boolean printNVR = false;
    String in_filename = null;
    String out_filename = null;
    FileWriter fo = null;
    long api_time = 0;

    private void usage() {
        System.err.println("Usage: java DumpMetadata -h <host> -i <FillData-output> [-o <output-file>] [-P]");
        System.err.println("    -P    print metadata for each file");
        System.err.println();
        System.exit(1);
    }

    public static void main(String args[]) {
        new DumpMetadata(args);
    }
        
    public DumpMetadata(String args[]) {

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
            } else if (args[i].equals("-o")) {
                i++;
                if (i >= args.length)
                    usage();
                out_filename = args[i];
            } else if (args[i].equals("-i")) {
                i++;
                if (i >= args.length)
                    usage();
                in_filename = args[i];
            } else if (args[i].equals("-P")) {
                printNVR = true;
            } else {
                System.err.println("Unexpected: " + args[i]);
                usage();
            }
        }

        if (host == null) {
            System.err.println("Need -h <host>");
            usage();
        }
        if (in_filename == null) {
            System.err.println("Need -i <in_file>");
            usage();
        }
        if (out_filename == null  &&  !printNVR) {
            System.err.println("Need -P or -o <filename>");
            usage();
        }
        if (out_filename != null) {
            try {
                fo = new FileWriter(out_filename);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        try {
            initHCClient(host);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

	Date start = new Date();

        int total = doit();

        Date now = new Date();
	System.out.println("Start:  " + start);
        System.out.println("Finish: " + now);
        long deltat = now.getTime() - start.getTime();
        deltat /= 1000;
        System.out.println("Records: " + total);
        System.out.println("API thruput, ms/record:     " + (api_time / total));
        System.out.println("Total thruput, records/sec: " + (total / deltat));
    }

    private int doit() {
        LineReader lr = null;
        try {
            lr = new LineReader(in_filename);
        } catch (Exception e) {
            System.err.println("Opening/reading " + in_filename + ": " + e);
            usage();
        }
        long times[] = new long[1];
        int dots = 0;
        int total = 0;
        int errors = 0;
        StringBuffer sb = new StringBuffer();
        try {
            String line;
            while ((line = lr.getline()) != null) {
                total++;

                // need hex & dot versions of oid 
                NewObjectIdentifier noid;
                if (line.indexOf(".") == -1)
                    noid = NewObjectIdentifier.fromHexString(line);
                else
                    noid = new NewObjectIdentifier(line);

                // skip non-metadata
                if (noid.getObjectType() != 
				NewObjectIdentifier.METADATA_TYPE) {
                    System.out.print(".");
                    dots++;
                    if (dots > 70) {
                        System.out.println();
                        dots = 0;
                    }
                    continue;
                }

                try {
                    NameValueRecord nvr = retrieveMetadata(noid.toExternalHexString(), 
							times, 0);
                    api_time += times[0];

                    String s = nvr.toString();

                    if (printNVR) {
                        if (dots > 0  &&  printNVR) {
                            System.out.println();
                            dots = 0;
                        }
                        System.out.println(s);
                    } else {
                        System.out.print("*");
                        dots++;
                        if (dots > 70) {
                            System.out.println();
                            dots = 0;
                        }
                    }
                    if (fo != null) {
                        fo.write(s);
                        fo.write("\n");
                        fo.flush();
                    }
                } catch (HoneycombTestException hte) {
                    String s = "getting " + noid.toExternalHexString() + ": " + hte;
                    if (dots > 0) {
                        System.out.println();
                        dots = 0;
                    }
                    System.err.println(s);
                    if (fo != null) {
                        fo.write(s);
                        fo.write("\n");
                        fo.flush();
                    }
                }
            }
            if (dots > 0)
                System.out.println();
            fo.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.out.println("Total errors: " + errors);
        return total;
    }
}
