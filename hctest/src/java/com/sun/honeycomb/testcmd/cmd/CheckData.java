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
import com.sun.honeycomb.testcmd.common.NameValue;

import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.SystemRecord;

import com.sun.honeycomb.client.QueryResultSet;
import com.sun.honeycomb.client.ObjectIdentifier;

import java.util.*;
import java.io.*;
import java.net.*;

public class CheckData extends HoneycombTestCmd {

    final int SLEEP = 5000; // 5 sec
    final int GIVEUP = 90000; // 1.5 min

    String host = null;
    String clnthost = null;

    ArrayList app_md = null;

    boolean printNVR = false;
    boolean dots = true;
    String out_filename = null;
    static String in_filename = null;
    static long md_time = 0;
    static long md_qtime = 0;
    static long restore_time = 0;
    static long restore_bytes = 0;
    static int ok_files = 0;
    static int corruption_errors = 0;
    static int total = 0;
    static int md_retrieve_failures = 0;
    static int retrieve_failures = 0;
    static int unique_failures = 0;
    static Date start = new Date();
    static boolean checkUnique = false;
    static boolean checkData = true;
    static LinkedList failed_rtrv = new LinkedList();
    static LinkedList failed_md_rtrv = new LinkedList();

    private void usage() {
        System.err.println("Usage: java CheckData -h <host> -i <FillData-output> [-o <output-file>] [-mu] [-P]");
        System.err.println("    -i    if arg is '-', read stdin");
        System.err.println("    -m    metadata only (don't restore data & check hash)");
        System.err.println("    -u    md query to check uniqueness");
        System.err.println("    -P    print metadata for each file");
        System.err.println();
        System.exit(1);
    }

    public static void main(String args[]) {
        new CheckData(args);
    }
        
    public CheckData(String args[]) {

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
                dots = false;
            } else if (args[i].equals("-m")) {
                checkData = false;
            } else if (args[i].equals("-u")) {
                checkUnique = true;
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

        if (out_filename != null) {
            try {
                fo = new FileWriter(out_filename, true);
                flog("# " + new Date() + "\n");
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
       
	try {
	    initHCClient(host);
	} catch (HoneycombTestException e) {
	    e.printStackTrace();
	    System.exit(1);
	}

        try {
            clnthost = InetAddress.getLocalHost().toString();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown(), 
							"Shutdown"));
        doit();

        done = true;
    }

    private void setAppMetadata(String line) {
        app_md = new ArrayList();
        String ss[] = line.split("\t");
        for (int i=1; i<ss.length; i+=2) {
            if (ss[i].equals("N")) // add later
                continue;
            NameValue nv = new NameValue(ss[i], ss[i+1]);
            app_md.add(nv);
        }
    }

    private void checkMD(StringBuffer sb, NameValueRecord nvr,
                                        String name, String value) {
        String id = nvr.getString(name);
        if (id == null) {
            sb.append("#e  no ").append(name).append(" in metadata\n");
        } else if (!id.equals(value)) {
            sb.append("#e  original/md ").append(name).append(": ");
            sb.append(value).append(" / ").append(id).append("\n");
        }
    }

    private void doit() {
        LineReader lr = null;
        try {
            lr = new LineReader(in_filename);
        } catch (Exception e) {
            System.err.println("CheckData: opening/reading " + 
						in_filename + ": " + e);
            usage();
        }
        long times[] = new long[1];
        StringBuffer sb = new StringBuffer();
        String line;
        String size, fname, oid, sha, date;
        while ((line = lr.getline()) != null) {

            if (line.startsWith("#M"))
                setAppMetadata(line);
            if (line.startsWith("#"))
                continue;

            total++;

            // FillData format:
            //
            // flog("" + size + '\t' +
            //          ss[0] + '\t' +         FNAME
            //          ss[1] + '\t' +         OID
            //          ss[2] + '\t' +         SHA1
            //          ss[3] + '\t' +         DATE
            //          binary + '\t' + metadata + '\t' +
            //          times[0] + '\n');
            String[] ss = line.split("\t");

            size = ss[0];
            fname = ss[1];
            oid = ss[2];
            sha = ss[3];
            date = ss[4];

            long t1 = System.currentTimeMillis();
            NameValueRecord nvr = null;
            boolean error1 = false;
            while (true) {
                boolean error = false;
                try {
                    nvr = retrieveMetadata(oid, times, 0);
                } catch (Exception e) {
                    flex("retrieveMetadata " + oid + 
					" (stored " + date + ")", e);
                    if (dots)
                        closeDots();
                    error = true;
                } catch (Throwable t) {
                    flex("retrieveMetadata " + oid + 
					" (stored " + date + ")", t);
                    if (dots)
                        closeDots();
                    t.printStackTrace();
                    error = true;
                }
                if (error) {
                    error1 = true;
                    if (System.currentTimeMillis() - t1 > GIVEUP) {
                        md_retrieve_failures++;
                        nvr = null;
                        break;
                    }
                    sleep(SLEEP);
                    continue;
                }
                break;
            }
            if (nvr == null) {
                failed_md_rtrv.add(oid);
            } else {
                if (error1)
                    flog("#E ok\n");
                md_time += times[0];

                if (printNVR)
                    System.out.println(nvr.toString());

                sb.setLength(0);

                // compare system metadata
                SystemRecord sr = nvr.getSystemRecord();
                long input_size = Long.parseLong(size);
                long stored_size = sr.getSize();
                if (input_size != stored_size) {
                     sb.append("  original/stored size: ").append(input_size);
                     sb.append(" / ").append(stored_size).append("\n");
                }
                String stored_oid = sr.getObjectIdentifier().toString();
                if (!oid.equals(stored_oid)) {
                     // one oid looked up another
                     sb.append("  oid mismatch! ").append(oid);
                     sb.append(" / ").append(stored_oid).append("\n");
                }

                // compare extended metadata
                checkMD(sb, nvr, "filesize", size);
                checkMD(sb, nvr, "filename", fname);
                checkMD(sb, nvr, "sha1", sha);
                checkMD(sb, nvr, "storedate", date);
                if (app_md != null) {
                    for (int j=0; j<app_md.size(); j++) {
                        NameValue nv = (NameValue) app_md.get(j);
                        checkMD(sb, nvr, nv.name, nv.value);
                    }
                }
            }
            // check actual file data
            if (checkData) {
                t1 = System.currentTimeMillis();
                String ret_fname = null;
                error1 = false;
                while (true) {
                    boolean error = false;
                    try {
                        ret_fname = retrieve(oid, times, 0);
                    } catch (Exception e) {
                        flex("retrieve data " + oid + 
					" (stored " + date + ")", e);
                        if (dots)
                            closeDots();
                        error = true;
                    } catch (Throwable t) {
                        flex("retrieve data " + oid + 
					" (stored " + date + ")", t);
                        if (dots)
                            closeDots();
                        t.printStackTrace();
                        error = true;
                    }
                    if (error) {
                        error1 = true;
                        if (System.currentTimeMillis() - t1 > GIVEUP) {
                            retrieve_failures++;
                            ret_fname = null;
                            break;
                        }
                        sleep(SLEEP);
                        continue;
                    }
                    break;
                }
                if (ret_fname == null) {
                    failed_rtrv.add(oid);
                } else {
                    if (error1)
                        flog("#E ok\n");
                    restore_time += times[0];
                    String sha3 = null;
                    try {
                        sha3 = shell.sha1sum(ret_fname);
                    } catch (Throwable t) {
                        flex("sha1sum(" + ret_fname + ")", t);
                        t.printStackTrace();
                        System.exit(1);
                    }
                    try {
                        restore_bytes += (new File(ret_fname)).length();
                    } catch (Throwable t) {
                        flex("File.length(" + ret_fname + ")", t);
                        t.printStackTrace();
                        System.exit(1);
                    }
                    if (!sha.equals(sha3)) {
                        sb.append("  original/retrieve sha: ").append(sha);
                        sb.append(" / ").append(sha3).append("\n");
                    } else if (sb.length() > 0) {
                        sb.append("  (sha1 of retrieved data is ok)\n");
                    }
                    deleteFile(ret_fname);
                }
            }

            if (checkUnique) {
                // check md query
                // XXX for now we gotta run on store clnt
                t1 = System.currentTimeMillis();
                String q = "\"storedate\"=\"" + date + 
				"\" AND \"filename\"=\"" + fname + "\"";
                QueryResultSet qr = null;
                while (true) {
                    boolean error = false;
                    try {
                        qr = query(q, times, 0);
                    } catch (Exception e) {
                        flex("query uniqueness " + oid + 
					" (stored " + date + ")", e);
                        if (dots)
                            closeDots();
                        error = true;
                    } catch (Throwable t) {
                        flex("query uniqueness " + oid + 
					" (stored " + date + ")", t);
                        if (dots)
                            closeDots();
                        t.printStackTrace();
                        error = true;
                    }
                    if (error) {
                        if (System.currentTimeMillis() - t1 > GIVEUP) {
                            unique_failures++;
                            qr = null;
                            break;
                        }
                        sleep(SLEEP);
                        continue;
                    }
                    break;
                }
                if (qr != null) {
                    md_qtime += times[0];
                    // Check if there are multiple results
                    try {
                        if (qr.next()) {
                            ObjectIdentifier oid1 = qr.getObjectIdentifier();
                            if (qr.next()) {
                                ObjectIdentifier oid2 =
                                    qr.getObjectIdentifier();
                                sb.append("got multiple oid's, " +
                                    "expected one:\n");
                                sb.append("\t" + oid1 + "\n");
                                sb.append("\t" + oid2 + "\n");
                                while (qr.next()) {
                                    oid1 = qr.getObjectIdentifier();
                                    sb.append("\t" + oid1 + "\n");
                                }
                            }
                        }
                    } catch (Throwable t) {
                        sb.append("failed to examine query results: " + t);
                    }
                }
            }

            // handle errors
            if (sb.length() > 0) {

                if (dots)
                    closeDots();

                corruption_errors++;
                //sb.append("\n");
                String s = oid + ":\n" + sb.toString();
                flog(s);
                System.out.print("\nCheckData: " + s);
            } else {
                ok_files++;
                if (dots)
                    dot(".");
            }
        }
        if (dots)
            closeDots();
    }

    private static class Shutdown implements Runnable {
        public void run() {
            Date now = new Date();
            if (in_filename.equals("-")) {
                // let piping program print 1st
                try { Thread.sleep(1000); } catch (Exception e) {}
            }
            System.err.println();
            if (!done)
                flout("CheckData INTERRUPTED\n");
            flout("CheckData START:    " + start + "\n");
            flout("CheckData FINISH:   " + now + "\n");
            flout("CheckData Records:  " + total + "\n");
            flout("CheckData ok files: " + ok_files + "\n");
            flout("CheckData md retrieve failures: " + 
						md_retrieve_failures + "\n");
            if (checkData)
                flout("CheckData retrieve failures: " + 
						retrieve_failures + "\n");
            if (checkUnique)
                flout("CheckData uniqueness-check failures: " + 
						unique_failures + "\n");
            flout("CheckData Total corruption errors: " + 
						corruption_errors + "\n");
            reportExceptions("CheckData");

            long deltat = now.getTime() - start.getTime();
            deltat /= 1000;
            if (total > 0)
                flout("CheckData API thruput, ms/record:   " + 
						(md_time / total) + "\n");
            if (checkData) {
                flout("CheckData Total bytes retrieved:    " + 
							restore_bytes + "\n");
                if (restore_time > 0)
                    flout("CheckData Data rate:                " + 
						(restore_bytes * 1000 / 
						restore_time) + " bytes/sec\n");
            }
            if (checkUnique  && total > 0) {
                flout("CheckData API unique, ms/record:     " + 
						(md_qtime / total) + "\n");
            }
            deltat = now.getTime() - start.getTime();
            if (deltat > 0)
                flout("CheckData Total thruput, ms/record: " + 
						(deltat / total) + "\n");
            if (failed_rtrv.size() > 0) {
                ListIterator li = failed_rtrv.listIterator(0);
                while (li.hasNext())
                    flout("CheckData lost data: " + (String) li.next() + "\n");
            }
            if (failed_md_rtrv.size() > 0) {
                ListIterator li = failed_md_rtrv.listIterator(0);
                while (li.hasNext())
                    flout("CheckData lost mdata: " + (String) li.next() + "\n");
            }
            try {
                new File(lastfile).delete();
            } catch (Exception e) {}
        }
    }
}
