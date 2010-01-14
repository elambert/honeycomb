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
import com.sun.honeycomb.common.ArchiveException;

import java.util.*;
import java.io.*;
import java.net.*;

public class Cedars extends HoneycombTestCmd {

    boolean debuggingEnabled = false;
    boolean binary = false;
    int metadata = MD_ALL;

    final int SLEEP = 5000; // 5 sec
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
    String metadata_s = null;
    static int n_files = 1500;
    long sizes[] = null;
    long sizes2[] = null;
    long sizes3[] = null;
    String oids[] = null;
    String shas[] = null;
    String out_file = null;

    static boolean update = false;

    long times[] = new long[1];
    long store_bytes = 0;
    long time_store = 0;
    long time_retrieve = 0;

    String[] species = {
		"moose", "squirrel", "mongoose", "whippet", "human", "rat"
		};
    String[] collectors = {
                "george bush", "john kerry", "kermit frog"
		};
    String[] processors = {
		"dick cheney", "john edwards", "j lo"
		};
    String[] types = {
		"serum", "plasma", "saliva", "skin"
		};
    String[] algorithms = {
		"p2p", "www", "aba", "bab"
		};
    String[] content_types = {
		"Peaks", "Isotope distributions", "Peptide assignments"
		};
    String[] blood_types = {
		"A", "B", "AB", "O"
		};

    static Date start = new Date();
    static String subject_id;
    static String study_id;
    static String species_id;
    static String sample_id;
    static String sample_type;
    static String collection_date;
    static String processing_date;
    static String experiment_date;
    static String experiment_number;
    static String collected_by;
    static String processed_by;
    String prev_algorithm_id;
    String prev_content_type;
    static int retrieve_failures = 0;
    static int md_retrieve_failures = 0;
    static int bad_file_errors = 0;
    static int bad_md_errors = 0;
    static int store_retries = 0;
    static int retrieve_retries = 0;
    static int md_retrieve_retries = 0;
    static int query_retries = 0;
    static int update_retries = 0;
    static int md_retrieves = 0;
    static int total_files = 0;
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
    static boolean updating = false;
    static long update_time = 0;
    static int updates = 0;
    static LinkedList failed_rtrv = new LinkedList();
    static LinkedList failed_md_rtrv = new LinkedList();

    private void usage() {
        System.err.println("Usage: java Cedars -h host [-n n_files] [-o out] [-u]");
        System.err.println("");
        System.err.println("    -h:      host ip or name");
        System.err.println("    -n:      number of base files ~30M (default 1500)");
        System.err.println("    -o:      output file for CheckData");
        System.err.println("    -u:      do dumb metadata update");
        System.err.println();
        System.err.println("needed metadata:\n(client_$ ssh admin@hc1-admin \"schemacfg --schema\" < file)\nstring cedars_subject_id, string cedars_study_id, string cedars_species_id, string cedars_sample_id, string cedars_collection_date, string cedars_processing_date, string cedars_collected_by, string cedars_processed_by, string cedars_sample_type, string cedars_experiment_date, string cedars_experiment_number, string cedars_scan_number, string cedars_algorithm_id, string cedars_content_type, string cedars_blood_type");
        System.exit(1);
    }

    public static void main(String args[]) throws ArchiveException, IOException, HoneycombTestException {
        new Cedars(args);
    }
        
    public Cedars(String args[]) throws ArchiveException, IOException, HoneycombTestException {

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
            } else if (args[i].equals("-u")) {
                update = true;
            } else {
                System.err.println("Unexpected: " + args[i]);
                usage();
            }
        }
        if (host == null) {
            System.err.println("Need -h");
            usage();
        }
    }

    private String[] store(String names[], long size, int i, 
						NameValueRecord nvr) {
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
                sleep(SLEEP);
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
                            times[0] + '\n';
            flog(s);
        }
        return names;
    }

    private void doIt() throws ArchiveException, IOException {

        // create id for the run/sample series
        try {
            subject_id = new String(getRandomChars(32));
            study_id = new String(getRandomChars(32));
            species_id = species[randIndex(species.length)];
            sample_id = new String(getRandomChars(32));
            collection_date = new Date(System.currentTimeMillis() - 336000).toString();
            processing_date = new Date().toString();
            collected_by = collectors[randIndex(collectors.length)];
            processed_by = processors[randIndex(processors.length)];
            sample_type = types[randIndex(types.length)];
            experiment_date = new Date(System.currentTimeMillis() - 636000).toString();
            experiment_number = Integer.toString(randIndex(9999999));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        
        metadata_s = "#M\tN\t" + n_files + 
			"\tcedars_subject_id\t" + subject_id + 
			"\tcedars_study_id\t" + study_id + 
			"\tcedars_species_id\t" + species_id + 
			"\tcedars_sample_id\t" + sample_id + 
			"\tcedars_collection_date\t" + collection_date +
			"\tcedars_processing_date\t" + processing_date +
			"\tcedars_collected_by\t" + collected_by + 
			"\tcedars_processed_by\t" + processed_by + 
			"\tcedars_sample_type\t" + sample_type + 
			"\tcedars_experiment_date\t" + experiment_date + 
			"\tcedars_experiment_number\t" + experiment_number;
        flog(metadata_s + "\n");

        // pass 1: create/store n_files of ~30 MB each
        System.err.println("Storing 30M (N=" + n_files + "):");
        String[] names = null;
        for (int i=0; i<sizes.length; i++) {
            // store file
            NameValueRecord nvr = createRecord();

            nvr.put("cedars_subject_id", subject_id);
            nvr.put("cedars_study_id", study_id);
            nvr.put("cedars_species_id", species_id);

            nvr.put("cedars_sample_id", sample_id);
            nvr.put("cedars_collection_date", collection_date);
            nvr.put("cedars_processing_date", processing_date);
            nvr.put("cedars_collected_by", collected_by);
            nvr.put("cedars_processed_by", processed_by);
            nvr.put("cedars_sample_type", sample_type);

            nvr.put("cedars_experiment_date", experiment_date);
            nvr.put("cedars_experiment_number", experiment_number);

            nvr.put("cedars_pass", Long.toString(1));
            nvr.put("cedars_scan_number", Long.toString(i));

            names = store(names, sizes[i], i, nvr);
            dot("s");
        }
        deleteFile(names[0]);
        closeDots();
        bytes_30M = store_bytes;
        time_store_30M = time_store;

        // pass 2: read each ~30 MB file & write a 3 MB file
        System.err.println("Retrieving 30M / storing 3M (N=" + n_files + "):");
        readwrite(sizes, sizes2, 2);
        closeDots();
        time_retrv_30M = time_retrieve;
        bytes_3M = store_bytes;
        time_store_3M = time_store;

        // pass 3: read each ~3 MB file & write a 3K file
        System.err.println("Retrieving 3M / storing 300K (N=" + n_files + "):");
        readwrite(sizes2, sizes3, 3);
        closeDots();
        time_retrv_3M = time_retrieve;
        bytes_300K = store_bytes;
        time_store_300K = time_store;

        // pass 4: verify each 3K file
        System.err.println("Retrieving 300K (N=" + n_files + "):");
        readwrite(sizes3, null, 4);
        closeDots();
        time_retrv_300K = time_retrieve;

        if (!update)
            return;

        // pass 5: update all files with blood type
        String blood_type = blood_types[randIndex(blood_types.length)];
        System.err.println("Adding cedars_blood_type=" + blood_type +
				" to all files (3xN=" + (n_files*3) + "):");
        flog("#m\tcedars_blood_type\t" + blood_type + "\n");
        if (out_file != null) {
            out_file += ".final";
            flog("# updated oid file is " + out_file + "\n");
            System.err.println("    ( updated oid file is " + out_file + ")");
            try {
                String host = clnthost;
                fo = new FileWriter(out_file, true); // append=true
                flog("#S Cedars/final [" + host + "] " + new Date() + "\n");
                flog(metadata_s + "\tcedars_blood_type\t" + blood_type + "\n");
            } catch (Exception e) {
                System.err.println("Opening " + out_file);
                e.printStackTrace();
                System.exit(1);
            }
        }

        updating = true;
        ObjectIdentifier o = null;
        QueryResultSet qr = null;
        while (true) {
            boolean error = false;
            try {
                qr = query("\"cedars_sample_id\" = \"" + sample_id + "\"", 
							times, 0);
            } catch (Throwable t) {
                flex("query getting oid's", t);
                String s = "#e getting oid's: " + t.getMessage();
                log.log(s);
                //flog(s + "\n");
                //e.printStackTrace();
                error = true;
            }
            if (error) {
                query_retries++;
                sleep(SLEEP);
                continue;
            }
            break;
        }
        oid_query_time = times[0];
        NameValueRecord nvr = createRecord();
        nvr.put("cedars_blood_type", blood_type);

        // XXX PORT Bill, please review
        try {
            while (qr.next()) {
                o = qr.getObjectIdentifier();
                Object[] oo = null;
                while (true) {
                    boolean error = false;
                    try {
                        oo = addMetadata(o, nvr, times, 0);
                    } catch (Throwable t) {
                        flex("adding md to " + o.toString(), t);
                        String s = "#e adding md to " + o.toString() + 
                            ": " + t.getMessage();
                        log.log(s);
                        error = true;
                    }
                    if (error) {
                        update_retries++;
                        sleep(SLEEP);
                        continue;
                    }
                    break;
                }
                update_time += times[0];
                updates++;

                // write to new log - trust mdata for now
                SystemRecord sr = (SystemRecord) oo[0];
                NameValueRecord new_nvr = (NameValueRecord) oo[1];
                String oid = sr.getObjectIdentifier().toString();
                String s =  new_nvr.getString("filesize") + '\t' +
                    new_nvr.getString("filename") + '\t' + 
                    oid + '\t' +
                    new_nvr.getString("sha1") + '\t' +
                    new_nvr.getString("storedate") + '\t' +
                    binary + '\t' + metadata + '\t' +
                    0 + '\n';
                flog(s);
                dot("u");
            }
        } catch (Throwable t) {
            // XXX PORT Bill review
            flex("getting next result ", t);
            String s = "#e getting next result: " + t.getMessage();
            log.log(s);
            /*
            error = true;
        if (error) {
            update_retries++;
            sleep(SLEEP);
            continue;
        }
        */
        }
        closeDots();
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

    private void readwrite(long s1[], long s2[], int pass) {
        // oids[] should correspond to s1[] going in, s2[] going out
        String[] names = null;
        time_retrieve = 0;
        store_bytes = 0;
        time_store = 0;

        String algorithm_id = algorithms[randIndex(algorithms.length)];
        String content_type = content_types[randIndex(content_types.length)];
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
                    sleep(SLEEP);
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
                    sleep(SLEEP);
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
                String sha2 = nvr.getString("sha1");
                if (sha2 == null) {
                    sb.append("#e  no sha1 in metadata\n");
                } else if (!sha2.equals(shas[i])) {
                    sb.append("#e  original/md sha:  ").append(shas[i]);
                    sb.append(" / ").append(sha2).append("\n");
                }

                checkMD(sb, nvr, "cedars_subject_id", subject_id);
                checkMD(sb, nvr, "cedars_study_id", study_id);
                checkMD(sb, nvr, "cedars_species_id", species_id);
                checkMD(sb, nvr, "cedars_sample_id", sample_id);
                checkMD(sb, nvr, "cedars_collection_date", collection_date);
                checkMD(sb, nvr, "cedars_processing_date", processing_date);
                checkMD(sb, nvr, "cedars_collected_by", collected_by);
                checkMD(sb, nvr, "cedars_processed_by", processed_by);
                checkMD(sb, nvr, "cedars_sample_type", sample_type);
                checkMD(sb, nvr, "cedars_experiment_date", experiment_date);
                checkMD(sb, nvr, "cedars_experiment_number", experiment_number);

                checkMD(sb, nvr, "cedars_scan_number", Integer.toString(i));

                if (pass > 2) {
                    checkMD(sb, nvr, "cedars_algorithm_id", prev_algorithm_id);
                    checkMD(sb, nvr, "cedars_content_type", prev_content_type);
                }

                if (sb.length() > 0) {
                    bad_md_errors++;
                    flog("#e md error " + oids[i] + "\n");
                    flog(sb.toString());
                    sb.setLength(0);
                }
            }

            if (s2 != null) {
                // store next smaller file and save oid
                nvr = createRecord();
                nvr.put("cedars_subject_id", subject_id);
                nvr.put("cedars_study_id", study_id);
                nvr.put("cedars_species_id", species_id);

                nvr.put("cedars_sample_id", sample_id);
                nvr.put("cedars_collection_date", collection_date);
                nvr.put("cedars_processing_date", processing_date);
                nvr.put("cedars_collected_by", collected_by);
                nvr.put("cedars_processed_by", processed_by);
                nvr.put("cedars_sample_type", sample_type);

                nvr.put("cedars_experiment_date", experiment_date);
                nvr.put("cedars_experiment_number", experiment_number);

                nvr.put("cedars_pass", Long.toString(pass));
                nvr.put("cedars_scan_number", Long.toString(i));

                nvr.put("cedars_algorithm_id", algorithm_id);
                nvr.put("cedars_content_type", content_type);

                names = store(names, s2[i], i, nvr);
                oids[i] = names[1];
                dot("s");
            }
        }
        prev_algorithm_id = algorithm_id;
        prev_content_type = content_type;

        if (names != null  &&  names[0] != null)
            deleteFile(names[0]);
    }

    private static class Shutdown implements Runnable {
        public void run() {
            System.err.println();
            Date now = new Date();
            if (!done)
                flout("INTERRUPTED\n");
            flout("START:  " + start + "\n");
            flout("FINISH: " + now + "\n");

            flout("N: " + n_files + "\n");
            flout("Subject id: " + subject_id + "\n");
            flout("Sample id:  " + sample_id + "\n");
            flout("total files: " + total_files + "\n");
            flout("total bytes: " + total_bytes + "\n");
            flout("retrieve failures: " + retrieve_failures + "\n");
            flout("metadata retrieve failures: " + md_retrieve_failures + "\n");
            flout("bad files retrieved: " + bad_file_errors + "\n");
            flout("bad metadata retrieved: " + bad_md_errors + "\n");

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
            if (update) {

                flout("update oid query retries: " + query_retries + "\n");
                flout("update retries: " + update_retries + "\n");
                if (oid_query_time > 0)
                    flout("query-oid's time (ms): " + oid_query_time + "\n");
                if (update_time > 0)
                    flout("update time (ms/record): " + 
						(update_time / updates) + "\n");
                if (updating  &&  updates != n_files * 3)
                    flout("files saved: " + (n_files * 3) + " updates: " + 
							updates + "\n");
            }

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

            //long deltat = now.getTime() - start.getTime();
            //if (deltat > 0)
                //flout("total thruput (bytes/sec rdwr): " +
                        //(2000 * total_bytes / deltat) + "\n");
            try {
                new File(lastfile).delete();
            } catch (Exception e) {}
        }
    }
}
