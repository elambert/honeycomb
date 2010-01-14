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

import java.util.*;
import java.io.*;

public class StoreRetrieve extends HoneycombTestCmd {

    boolean debuggingEnabled = false;

    // filesizes
    final long small_sizes[] = {
                        1,
                        getBlockSize(), // 64k * n
                        getBlockSize()+1,
                        getBlockSize()-1,
                        getBlockSize() * 64
                };

    final long big_sizes[] = {
                        1024 * 1024 * 1024, // 1G
                        1024 * 1024 * 1024 + 1
                };

    String host = null;
    long extra_sizes[] = null;

    boolean small = false;
    boolean big = false;
    boolean extra = false;
    int metadata = 0;
    boolean delete = false;
    boolean delete2 = false;

    private void usage() {
        System.err.println("Usage: java StoreRetrieve -h host [-S] [-B] [-A] [-bytes <size1> <size2> ..] [-P] [-M]");
        System.err.println("");
        System.err.println("    -h:      host ip or name");
        System.err.println("");
        System.err.println("    -S:      1..blocksize*64  (" + small_sizes.length +
							" cases, ~5 min)");
        System.err.println("    -B:      ~1G  (" + big_sizes.length + " cases, ~60 min)");
        System.err.println("    -A:      {S+B}");
        System.err.println("    -bytes:  1 2k 3M 100g 2f 30b 5E ..");
        System.err.println("             [k, m, g = kbytes, mbytes, gbytes]");
        System.err.println("             [f, b, e = fragsz, blocksz, extentsz]");
        System.err.println("    -P:      print all sizes and exit");
        System.err.println("");
        System.err.println("    -M:      add token metadata");
        System.err.println("    -d:      store, delete, retrieve");
        System.err.println("    -D:      store, retrieve, delete, retrieve");
        System.err.println("");
        System.exit(1);
    }

    public static void main(String args[]) throws HoneycombTestException{
        new StoreRetrieve(args);
    }
        
    public StoreRetrieve(String args[]) throws HoneycombTestException{

        parseArgs(args);

        initHCClient(host);

        if (small)
            doSizes(small_sizes);
        if (big)
            doSizes(big_sizes);
        if (extra)
            doSizes(extra_sizes);
    }

    private void parseArgs(String args[]) {

        if (args.length == 0) {
            usage();
        }
        ArrayList extras = new ArrayList();

        boolean print = false;

        for (int i=0; i<args.length; i++) {

            if (args[i].equals("-h")) {
                if (i+1 >= args.length)
                    usage();
                // trim in case someone uses quotes :-)
                host = args[i+1].trim(); 
                i++;
            } else if (args[i].equals("-S")) {
                small = true;
            } else if (args[i].equals("-B")) {
                big = true;
            } else if (args[i].equals("-A")) {
                small = true;
                big = true;
            } else if (args[i].equals("-P")) {
                print = true;
            } else if (args[i].equals("-M")) {
                metadata = MD_ALL;
            } else if (args[i].equals("-d")) {
                delete = true;
            } else if (args[i].equals("-D")) {
                delete2 = true;
            } else if (args[i].equals("-bytes")) {
                extra = true;
                int j;
                for (j=i+1; j<args.length; j++) {
                    try {
                        long lsize = parseSize(args[j]);
                        if (lsize < 1) {
                            System.err.println(args[j] + ": sizes must be > 0");
                            usage();
                        }
                        extras.add(new Long(lsize));
                    } catch (NumberFormatException nfe) {
                        break;
                    }
                }
                i = j - 1;
            } else {
                System.err.println("Unexpected: " + args[i]);
                usage();
            }
        }

        if (extra) {
            if (extras.size() == 0) {
                System.err.println("-bytes: must give number");
                usage();
            }
            extra_sizes = new long[extras.size()];
            for (int i=0; i<extras.size(); i++) {
                Long l = (Long) extras.get(i);
                extra_sizes[i] = l.longValue();
            }
        }
        if (print) {
            StringBuffer sb = new StringBuffer();
            if (small) {
                sb.append("small:  ");
                for (int i=0; i<small_sizes.length; i++) {
                    if (i > 0)
                        sb.append(", ");
                    sb.append(small_sizes[i]);
                }
                sb.append("\n");
            }
            if (big) {
                sb.append("big:    ");
                for (int i=0; i<big_sizes.length; i++) {
                    if (i > 0)
                        sb.append(", ");
                    sb.append(big_sizes[i]);
                }
                sb.append("\n");
            }
            if (extra) {
                sb.append("custom: ");
                for (int i=0; i<extra_sizes.length; i++) {
                    if (i > 0)
                        sb.append(", ");
                    sb.append(extra_sizes[i]);
                }
            }
            System.out.println(sb);
            System.exit(0);
        }
        if (host == null) {
            System.err.println("Need -h");
            usage();
        }
    }

    private void doSizes(long[] sizes) {

        long times[] = new long[2 * sizes.length];
        Arrays.fill(times, -1L);

        String oids[] = new String[sizes.length];
        Arrays.fill(oids, null);

        for (int i=0; i<sizes.length; i++) {

            String[] names = null;
            String retrieved = null;

            String action = null;

            try {
                action = "create/store";
                names = createFileAndStore(sizes[i], true, metadata, null,
								times, i*2);
                if (delete) {
                    action = "delete";
                    delete(names[1]);
                    log.log("deleted object: retrieve should fail");
                }
                action = "retrieve";
                retrieved = retrieve(names[1], times, i*2+1);
                action = "compare";
                String cmp = shell.cmp(names[0], retrieved);
                if (cmp != null) {
                    log.log("Comparing files: " + cmp);
                }
                if (delete2) {
                    action = "delete";
                    delete(names[1]);
                    log.log("deleted object: retrieve should fail");
                    action = "retrieve";
                    retrieved = retrieve(names[1], times, i*2+1);
                    log.log("Error: retrieve succeeded on deleted object");
                    cmp = shell.cmp(names[0], retrieved);
                    if (cmp == null) {
                        log.log("(Same data was retrieved)");
                    } else {
                        log.log("Different data retrieved: " + cmp);
                    }
                }
            } catch (HoneycombTestException e) {
                log.log(action + " problem: " + e.getMessage());
                //e.printStackTrace();
            } catch (Throwable t) {
                log.log(action + " got throwable");
                t.printStackTrace();
            }
            if (names != null) {
                deleteFile(names[0]);
                oids[i] = names[1];
            }
            deleteFile(retrieved);
        }
        log.log("size          store    retrv    oid");
        //       1234567890123456789012345678901234567890
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<sizes.length; i++) {
            sb.setLength(0);
            sb.append(sizes[i]);
            while (sb.length() < 14)
                sb.append(' ');
            if (times[i*2] == -1) {
                sb.append("N/A");
            } else {
                sb.append(times[i*2]);

                while (sb.length() < 23)
                    sb.append(' ');
                if (times[i*2+1] == -1) {
                    sb.append("N/A");
                } else {
                    sb.append(times[i*2+1]);
                    if (oids[i] != null) {
                        while (sb.length() < 32)
                            sb.append(' ');
                        sb.append(oids[i]);
                    }
                }
            }
            log.log(sb.toString());
        }
    }
/*
NameValueRecord nvr = new NameValueRecord();

                // Create the extended metadata hashmap
                hm = new HashMap();
                                                                                
                hm.put("filename", filename1);
                hm.put("fileorigsize", Integer.toString(filesize));
                hm.put("filecurrsize", Integer.toString(filesize));
                hm.put("iteration", Long.toString(0));
                hm.put("prevSHA1", "null");
                hm.put("timestart", date);
                hm.put("timenow", date);
                hm.put("word", "honeycomb");
                hm.put("wordlength", Long.toString(9));
                hm.put("first", "h");
                hm.put("second", "o");
                hm.put("third", "n");
                hm.put("fourth", "e");
                hm.put("fifth", "y");
                hm.put("sixth", "c");
                hm.put("initchar", "a");
                hm.put("stringorigargs", "-a foo -b bar");
*/
}
