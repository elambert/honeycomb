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

import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.client.QueryResultSet;
import com.sun.honeycomb.client.ObjectIdentifier;

import java.util.*;
import java.io.*;
import java.net.*;

public class Cedars2 extends HoneycombTestCmd {

    boolean debuggingEnabled = false;
    boolean binary = false;

    final int metadata = (MD_FNAME|MD_FSIZE|MD_STOREDATE);
    final int RAN_LEN = 4;

    final String VIEW1 = "study_group_sample";
    final String VIEW2 = "study_group_processing";

    final int NFS_SLEEP = 900000; // 15 min
    final int RETRY_SLEEP = 5000; // 5 sec
    final int GIVEUP = 90000; // 1.5 min

    // range of file sizes
    final long MIN_SIZE = 30618419; // 29.2 MB
    final long RANGE = 1677722; // 1.6 MB
    final long MIN_SIZE2 = 2936013; // 2.8 MB
    final long RANGE2 = 419430; // 0.4 MB
    final long MIN_SIZE3 = 276480; // .9 x 300K
    final long RANGE3 = 61440; // .2 x 300K

    final int MAX_RETRIES = 200;

    String host = null;
    String mount_point = null;
    static String nfs1 = null;
    static String nfs2 = null;
    String metadata_s = null;
    static int n_files = 1500;
    long sizes[] = null;
    long sizes2[] = null;
    long sizes3[] = null;
    String oids[] = null;
    String shas[] = null;
    String out_file = null;

    long times[] = new long[1];
    long store_bytes = 0;
    long time_store = 0;
    long time_retrieve = 0;

    static Date start = new Date();

    static String study_id;
    static String sample_group;
    static String sample_id;
    static String processing_param;

    static int retrieve_failures = 0;
    static int md_retrieve_failures = 0;
    static int bad_file_errors = 0;
    static int bad_md_errors = 0;
    static int store_retries = 0;
    static int retrieve_retries = 0;
    static int md_retrieve_retries = 0;
    static int query_retries = 0;
    static int md_retrieves = 0;
    static int total_files = 0;
    static int nfs_missing = 0;
    static int nfs_mixed_view = 0;
    static int nfs_bad_data = 0;
    static int nfs_bad_size = 0;
    static int nfs_zero_size = 0;
    static int nfs_io_errors = 0;
    static int nfs_access_errors = 0;
    static int flawed_test = 0;
    static long total_bytes = 0;
    static long total_store_time = 0;
    static long total_retrieve_time = 0;
    static long md_retrieve_time = 0;
    static long bytes_30M = 0;
    static long bytes_3M = 0;
    static long bytes_300K = 0;
    static long time_store_30M = 0;
    static long time_retrv_30M = 0;
    static long time_store_3M = 0;
    static long time_retrv_3M = 0;
    static long time_store_300K = 0;
    static long time_retrv_300K = 0;
    static long oid_query_time = 0;
    static long nfs_read_time = 0;
    static long nfs_read_bytes = 0;
    static boolean updating = false;
    static LinkedList failed_rtrv = new LinkedList();
    static LinkedList failed_md_rtrv = new LinkedList();
    static LinkedList missing_nfs = new LinkedList();
    static LinkedList nfs_bad = new LinkedList();

    private void usage() {
        System.err.println("Usage: java Cedars -h host -m nfs_mnt [-n n_files] [-o out]");
        System.err.println("");
        System.err.println("    -h:      host ip or name");
        System.err.println("    -m:      mount point for nfs view");
        System.err.println("    -n:      number of base files ~30M (default 1500)");
        System.err.println("    -o:      output file for CheckData");
        System.err.println();
        System.err.println("needed metadata:\n(client_$ ssh admin@hc1-admin \"schemacfg --schema\" < file)\nstring study_id, string sample_group, string sample_id, string processing_param, string scan_number");
        System.exit(1);
    }

    public static void main(String args[]) throws HoneycombTestException {
        new Cedars2(args);
    }
        
    public Cedars2(String args[]) throws HoneycombTestException{

        verbose = false;

        parseArgs(args);
        initHCClient(host);

        // generate lists of random sizes around 30M and 3M
        // sort ascending to allow continuous expansion
        try {
            initRandom();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        sizes = new long[n_files];
        for (int i=0; i<sizes.length; i++) {
            sizes[i] =  MIN_SIZE + (long) (rand.nextDouble() * (double)RANGE);
        }
        Arrays.sort(sizes);

        sizes2 = new long[n_files];
        for (int i=0; i<sizes2.length; i++) {
            sizes2[i] =  MIN_SIZE2 + (long) (rand.nextDouble() *(double)RANGE2);
        }
        Arrays.sort(sizes2);

        sizes3 = new long[n_files];
        for (int i=0; i<sizes3.length; i++) {
            sizes3[i] =  MIN_SIZE3 + (long) (rand.nextDouble() *(double)RANGE3);
        }
        Arrays.sort(sizes3);

        oids = new String[n_files];
        Arrays.fill(oids, null);
        shas = new String[n_files];
        Arrays.fill(shas, null);

        if (out_file != null) {
            try {
                String host = clnthost;
                fo = new FileWriter(out_file, true); // append=true
                flog("#S Cedars [" + host + "] " + new Date() + "\n");
            } catch (Exception e) {
                System.err.println("Opening " + out_file);
                e.printStackTrace();
                System.exit(1);
            }
        }
        Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown(),
                                                        "Shutdown"));
        doIt();

        done = true;
    }

    private void parseArgs(String args[]) {

        if (args.length == 0) {
            usage();
        }
        boolean print = false;

        for (int i=0; i<args.length; i++) {

            if (args[i].equals("-h")) {
                if (i+1 >= args.length)
                    usage();
                // trim in case someone uses quotes :-)
                host = args[i+1].trim(); 
                i++;
            } else if (args[i].equals("-m")) {
                if (i+1 >= args.length)
                    usage();
                mount_point = args[i+1].trim();
                i++;
            } else if (args[i].equals("-n")) {
                if (i+1 >= args.length)
                    usage();
                i++;
                try {
                    n_files = Integer.parseInt(args[i]);
                } catch (NumberFormatException nfe) {
                    System.err.println("Parsing [" + args[i] + "]: " + nfe);
                    usage();
                }
                if (n_files < 1) {
                    System.err.println(args[i] + ": n_files must be > 0");
                    usage();
                }
            } else if (args[i].equals("-o")) {
                if (i+1 >= args.length)
                    usage();
                i++;
                out_file = args[i];
            } else {
                System.err.println("Unexpected: " + args[i]);
                usage();
            }
        }
        if (host == null) {
            System.err.println("Need -h");
            usage();
        }
        if (mount_point == null) {
            System.err.println("Need -m");
            usage();
        }
    }

    private int scan_number = 0;

    private String[] store(String names[], long size, int i, 
						NameValueRecord nvr) {

        nvr.put("scan_number", Long.toString(scan_number));

        // store file
        boolean error1 = false;
        while (true) {
            boolean error = false;
            try {
                names = createFileAndStore(names, size, binary, metadata, nvr,
							true, times, 0);
                //nvr = retrieveMetadata(names[1], times, 0);
            } catch (HoneycombTestException e) {
                flex("store size=" + size + " name=" + 
				(names == null ? "null" : names[0]), e);
                log.log("store problem: " + e.getMessage());
                //e.printStackTrace();
                error = true;
            } catch (Throwable t) {
                flex("store size=" + size + " name=" + 
				(names == null ? "null" : names[0]), t);
                log.log("store: got throwable");
                t.printStackTrace();
                error = true;
            }
            if (error) {
                error1 = true;
                store_retries++;
                sleep(RETRY_SLEEP);
                continue;
            }
            break;
        }
        if (error1)
            flog("#E ok\n");

        // save info
        total_files++;
        total_bytes += size;
        store_bytes += size;
        time_store += times[0];
        total_store_time += times[0];

        oids[i] = names[1];
        shas[i] = names[2];
        if (fo != null) {
            String s = "" + size + '\t' +
                            names[0] + '\t' +  // filename
                            names[1] + '\t' +  // oid
                            names[2] + '\t' +  // sha1
                            names[3] + '\t' +  // store date
                            binary + '\t' + metadata + '\t' +
                            times[0] + '\t' +
                            scan_number + '\n';
            flog(s);
        }
        scan_number++;
        return names;
    }

    private void doIt() {

        // create id for the run/sample series
        try {
            study_id = "study_" + new String(getRandomChars(RAN_LEN));
            sample_group = "sample_grp_" + new String(getRandomChars(RAN_LEN));
            sample_id = "sample_id_" + new String(getRandomChars(RAN_LEN));
            processing_param = "proc_" + new String(getRandomChars(RAN_LEN));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        nfs1 = mount_point + '/' + VIEW1 + '/' + study_id + '/' +
		sample_group + '/' + sample_id + '/';
        nfs2 = mount_point + '/' + VIEW2 + '/' + study_id + '/' +
                sample_group + '/' + processing_param + '/' + sample_id + '/';
        
        metadata_s = "#M\tN\t" + n_files + 
			"\tstudy_id\t" + study_id + 
			"\tsample_group\t" + sample_group + 
			"\tsample_id\t" + sample_id +
			"\tprocessing_param\t" + processing_param;
        flog(metadata_s + "\n");

        // pass 1: create/store n_files of ~30 MB each
        System.err.println("Storing 30M (N=" + n_files + "):");
        String[] names = null;
        for (int i=0; i<sizes.length; i++) {
            // store file
            NameValueRecord nvr = createRecord();

            nvr.put("study_id", study_id);
            nvr.put("sample_group", sample_group);
            nvr.put("sample_id", sample_id);
            nvr.put("processing_param", processing_param);

            names = store(names, sizes[i], i, nvr);
            dot("s");
        }
        deleteFile(names[0]);
        closeDots();
        bytes_30M = store_bytes;
        time_store_30M = time_store;

        // pass 2: read each ~30 MB file & write a 3 MB file
        sleep(NFS_SLEEP);
        System.err.println("Retrieving 30M / storing 3M (N=" + n_files + "):");
        readwrite(sizes, sizes2, 2);
        closeDots();
        time_retrv_30M = time_retrieve;
        bytes_3M = store_bytes;
        time_store_3M = time_store;

        // pass 3: read each ~3 MB file & write a 3K file
        sleep(NFS_SLEEP);
        System.err.println("Retrieving 3M / storing 300K (N=" + n_files + "):");
        readwrite(sizes2, sizes3, 3);
        closeDots();
        time_retrv_3M = time_retrieve;
        bytes_300K = store_bytes;
        time_store_300K = time_store;

        // pass 4: verify each 3K file
        sleep(NFS_SLEEP);
        System.err.println("Retrieving 300K (N=" + n_files + "):");
        readwrite(sizes3, null, 4);
        closeDots();
        time_retrv_300K = time_retrieve;
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

    private boolean check_nfs(String fname, String oid, String sha, long size) {

	StringBuffer sb = new StringBuffer();
        int problems = 0;

        // test java's view of things
        try {
            File f = new File(fname);
            if (f.isFile()) {
	        if (f.length() != size) {
		    problems++;
		    sb.append("[size stored/seen: " + size + " / " +
					f.length() + "]");
		    if (f.length() == 0  &&  size != 0) {
		        nfs_zero_size++;
		    } else {
                        nfs_bad_size++;
		    }
		}
	    } else {
	        sb.append("[isFile() false (size not checked)]");
		problems++;
            }
        } catch (Exception e) {
	    sb.append("[java access: " + e.getMessage() + "]");
	    problems++;
            flex("File: " + fname + " oid: " + oid, e);
        }
/*
        long t = -1;
        try {
            t = timeCat(fname);
System.out.println("nfs read: " + t + " size " + size);
        } catch (Exception e) {
            flex("timecat: " + fname, e);
            return false;
        }
        if (t > -1) {
            nfs_read_time += t;
            nfs_read_bytes += size;
        } else {
            ok = false;
        }
*/
        String sha1sum = null;
        try {
            sha1sum = shell.sha1sum(fname);
            if (sha1sum == null) {
	        sb.append("[sha1sum is null but no Exception - error in test??]");
	        problems++;
	        flawed_test++;
	    }
        } catch (Exception e) {
	    String s = e.getMessage();
	    if (s.indexOf("No such file or directory") != -1) {
	        nfs_missing++;
		problems++;
		sb.append("[sha1sum: No such file or directory]");
	    } else if (s.indexOf("Input/output error") != -1) {
	        nfs_io_errors++;
		problems++;
		sb.append("[sha1sum: i/o error]");
	    } else {
	        nfs_access_errors++;
	        problems++;
		sb.append("[sha1sum: " + s + "]");
                e.printStackTrace();
	    }
        }
        if (sha1sum != null  &&  !sha1sum.equals(sha)) {
            nfs_bad_data++;
	    problems++;
	    sb.append("[sha1sum wrong: " + sha1sum + "]");
        }
	if (problems > 0) {
	    String s = "nfs problems: " + problems + " " + oid + " " + 
	    		fname + " " + sha + ": " + sb.toString() + "\n";
            flog("#e " + s);
	    nfs_bad.add(s);
	    return false; // not ok
	}
	return true;  // ok
    }

    private void readwrite(long s1[], long s2[], int pass) {
        // oids[] should correspond to s1[] going in, s2[] going out
        String[] names = null;
        time_retrieve = 0;
        store_bytes = 0;
        time_store = 0;

        int id1 = (pass - 2) * s1.length;

        StringBuffer sb = new StringBuffer();

        for (int i=0; i<s1.length; i++) {
            String retrieved = null;
            boolean error1 = false;
            long t1 = System.currentTimeMillis();
            while (true) {
                boolean error = false;
                try {
                    retrieved = retrieve(oids[i], times, 0);
                    time_retrieve += times[0];
                    total_retrieve_time += times[0];
                } catch (HoneycombTestException e) {
                    flex("retrv " +  oids[i], e);
                    String s = "#e retrv " + oids[i] + ": " + e.getMessage();
                    log.log(s);
                    //flog(s + "\n");
                    //e.printStackTrace();
                    error = true;
                } catch (Throwable t) {
                    flex("retrv " +  oids[i], t);
                    log.log("retrieve got throwable");
                    t.printStackTrace();
                    error = true;
                }
                if (error) {
                    error1 = true;
                    retrieve_retries++;
                    if (System.currentTimeMillis() - t1 > GIVEUP) {
                        retrieve_failures++;
                        retrieved = null;
                        break;
                    }
                    sleep(RETRY_SLEEP);
                    continue;
                }
                break;
            }
            if (retrieved == null) {
                failed_rtrv.add(oids[i]);
            } else {
                if (error1)
                    flog("#E ok\n");
                dot("r");
                // compare retrieved w/ original
                File f = new File(retrieved);
                if (f.length() != s1[i]) {
                    bad_file_errors++;
                    String s = "#e size mismatch [" + oids[i] + 
				"] store/retrieve: " +
				s1[i] + " / " + f.length() + "\n";
                    flog(s);
                } else {
                    String sha1sum = null;
                    try {
                        sha1sum = shell.sha1sum(retrieved);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (sha1sum != null &&  !sha1sum.equals(shas[i])) {
                        bad_file_errors++;
                        String s = "#e sha1 mismatch [" + oids[i] + 
						"] store/retrieve: " +
						shas[i] + " / " +
						sha1sum + "\n";
                        flog(s);
                    }
                }
                deleteFile(retrieved);

            }
            // compare mdata
            t1 = System.currentTimeMillis();
            NameValueRecord nvr = null;
            error1 = false;
            while (true) {
                boolean error = false;
                try {
                    nvr = retrieveMetadata(oids[i], times, 0);
                    md_retrieve_time += times[0];
                    md_retrieves++;
                } catch (HoneycombTestException e) {
                    flex("md retrv " +  oids[i], e);
                    String s = "#e md retrv " + oids[i] + ": " + e.getMessage();
                    log.log(s);
                    //flog(s + "\n");
                    //e.printStackTrace();
                    error = true;
                } catch (Throwable t) {
                    flex("md retrv " +  oids[i], t);
                    log.log("md retrieve got throwable");
                    t.printStackTrace();
                    error = true;
                }
                if (error) {
                    error1 = true;
                    md_retrieve_retries++;
                    if (System.currentTimeMillis() - t1 > GIVEUP) {
                        md_retrieve_failures++;
                        nvr = null;
                        flog("#E " + oids[i] + ": giving up\n");
                        break;
                    }
                    sleep(RETRY_SLEEP);
                    continue;
                }
                break;
            }
            if (nvr == null) {
                failed_md_rtrv.add(oids[i]);
            } else {
                if (error1)
                    flog("#E ok\n");
                SystemRecord sr = nvr.getSystemRecord();
                String stored_oid = sr.getObjectIdentifier().toString();
                if (!oids[i].equals(stored_oid)) {
                    // one oid looked up another
                    sb.append("#e  oid mismatch! ").append(oids[i]);
                    sb.append(" / ").append(stored_oid).append("\n");
                }
                // [no sha in 'actual' cedars md]
                // String sha2 = nvr.get("sha1");
                // if (sha2 == null) {
                //     sb.append("#e  no sha1 in metadata\n");
                // } else if (!sha2.equals(shas[i])) {
                //     sb.append("#e  original/md sha:  ").append(shas[i]);
                //     sb.append(" / ").append(sha2).append("\n");
                // }

                checkMD(sb, nvr, "study_id", study_id);
                checkMD(sb, nvr, "sample_group", sample_group);
                checkMD(sb, nvr, "sample_id", sample_id);
                checkMD(sb, nvr, "processing_param", processing_param);

                checkMD(sb, nvr, "scan_number", Integer.toString(id1));

                if (sb.length() > 0) {
                    bad_md_errors++;
                    flog("#e md error " + oids[i] + "\n");
                    flog(sb.toString());
                    sb.setLength(0);
                }
            }

            // check nfs version(s)
            int nfs_ok = 0;
            if (check_nfs(nfs1 + id1, oids[i], shas[i], s1[i])) {
                dot("n");
                nfs_ok++;
            } else {
                dot("e");
            }
            if (check_nfs(nfs2 + id1, oids[i], shas[i], s1[i])) {
                dot("n");
                nfs_ok++;
            } else {
                dot("e");
            }
            if (nfs_ok == 1)
                nfs_mixed_view++;

            if (s2 != null) {
                // store next smaller file and save oid
                nvr = createRecord();
                nvr.put("study_id", study_id);
                nvr.put("sample_group", sample_group);
                nvr.put("sample_id", sample_id);
                nvr.put("processing_param", processing_param);

                names = store(names, s2[i], i, nvr);
                oids[i] = names[1];
                dot("s");
            }
            id1++;
        }
        if (names != null  &&  names[0] != null)
            deleteFile(names[0]);
    }

    private static class Shutdown implements Runnable {
        public void run() {

            Date now = new Date();

            System.err.println();
            if (!done)
                flout("INTERRUPTED\n");

            flout("START:  " + start + "\n");
            flout("FINISH: " + now + "\n");

            flout("N: " + n_files + "\n");
            flout("Sample id:  " + sample_id + "\n");
            flout("Sample group:  " + sample_group + "\n");
            flout("processing_param:  " + processing_param + "\n");
            flout("nfs1:  " + nfs1 + "\n");
            flout("nfs2:  " + nfs2 + "\n");
            flout("total files: " + total_files + "\n");
            flout("total bytes: " + total_bytes + "\n");
            flout("retrieve failures: " + retrieve_failures + "\n");
            flout("metadata retrieve failures: " + md_retrieve_failures + "\n");
            flout("bad files retrieved: " + bad_file_errors + "\n");
            flout("bad metadata retrieved: " + bad_md_errors + "\n");
            flout("nfs missing: " + nfs_missing + "\n");
            flout("nfs zero size: " + nfs_zero_size + "\n");
            flout("nfs bad size: " + nfs_bad_size + "\n");
	    flout("nfs i/o errors: " + nfs_io_errors + "\n");
	    flout("nfs access errors: " + nfs_access_errors + "\n");
	    flout("nfs bad data: " + nfs_bad_data + "\n");
            flout("nfs 'mixed view': " + nfs_mixed_view + "\n");
	    flout("flawed_test: " + flawed_test + "\n");

            reportExceptions();

            flout("store retries:    " + store_retries + "\n");
            flout("retrieve retries: " + retrieve_retries + "\n");
            flout("md retrieve retries: " + md_retrieve_retries + "\n");
            if (md_retrieve_time > 0)
                flout("md retrieve rate (ms/record): " + 
			(md_retrieve_time / md_retrieves) + "\n");
            if (total_store_time > 0)
                flout("total store rate (bytes/sec): " +
			((total_bytes * 1000) / total_store_time) + "\n");
            if (total_retrieve_time > 0)
                flout("total retrieve rate (bytes/sec): " +
			((total_bytes * 1000) / total_retrieve_time) + "\n");
            if (time_store_30M > 0)
                flout("30 MB store (bytes/sec): " +
                        (1000 * bytes_30M / time_store_30M) + "\n");
            if (time_retrv_30M > 0)
                flout("30 MB retrieve (bytes/sec): " +
			(1000 * bytes_30M / time_retrv_30M) + "\n");
            if (time_store_3M > 0)
                flout("3 MB store (bytes/sec): " +
                        (1000 * bytes_30M / time_store_3M) + "\n");
            if (time_retrv_3M > 0)
                flout("3 MB retrieve (bytes/sec): " +
			(1000 * bytes_3M / time_retrv_3M) + "\n");
            if (time_store_300K > 0)
                flout("300 KB store (bytes/sec): " +
			(1000 * bytes_300K / time_store_300K) + "\n");
            if (time_retrv_300K > 0)
                flout("300 KB retrieve (bytes/sec): " +
			(1000 * bytes_300K / time_retrv_300K) + "\n");
            if (nfs_read_bytes > 0)
                flout("aggregate nfs read rate (bytes/sec): " +
			(1000 * nfs_read_bytes / nfs_read_time) + "\n");

            if (failed_rtrv.size() > 0) {
                ListIterator li = failed_rtrv.listIterator(0);
                while (li.hasNext())
                    flout("lost data: " + (String) li.next() + "\n");
            }
            if (failed_md_rtrv.size() > 0) {
                ListIterator li = failed_md_rtrv.listIterator(0);
                while (li.hasNext())
                    flout("lost mdata: " + (String) li.next() + "\n");
            }
            if (missing_nfs.size() > 0) {
                ListIterator li = missing_nfs.listIterator(0);
                while (li.hasNext())
                    flout("nfs missing: " + (String) li.next() + "\n");
            }
	    if (nfs_bad.size() > 0) {
	        ListIterator li = nfs_bad.listIterator(0);
		while (li.hasNext())
		    flout((String) li.next() + "\n");
	    }

            boolean pass = true;
            if (total_files == 0)
                pass = false;
            if (retrieve_failures + md_retrieve_failures + bad_file_errors +
                  nfs_missing + nfs_bad_data + nfs_bad_size + nfs_zero_size +
		  nfs_io_errors + nfs_access_errors + flawed_test > 0)
                pass = false;

            if (pass) 
                flout("RESULT PASS\n");
            else
                flout("RESULT FAIL\n");

            try {
                new File(lastfile).delete();
            } catch (Exception e) {}
        }
    }
}
