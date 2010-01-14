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



package com.sun.honeycomb.hctest.task;

import com.sun.honeycomb.hctest.CmdResult;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import java.util.Date;
import java.util.Arrays;
import java.util.HashMap;

/**
 *  Task for emulating Cedars workflow. Call interrupt() freely
 *  on this one.
 */
public class CedarsLoadTask extends SequenceTask {

    int client;
    int n_files;
    int iterations;

    public boolean aborted = false;
    public int cycles = 0;
    public int store_failed = 0;
    public int retrieve_failed = 0;
    public int retrieve_bad_data = 0;
    public BandwidthStatistic store1_bw = new BandwidthStatistic("store1_bw");
    public BandwidthStatistic store2_bw = new BandwidthStatistic("store2_bw");
    public BandwidthStatistic store3_bw = new BandwidthStatistic("store3_bw");
    public BandwidthStatistic retrieve1_bw = 
                                         new BandwidthStatistic("retrieve1_bw");
    public BandwidthStatistic retrieve2_bw = 
                                         new BandwidthStatistic("retrieve2_bw");
    public BandwidthStatistic retrieve3_bw = 
                                         new BandwidthStatistic("retrieve3_bw");

    long sizes1[] = null;
    long sizes2[] = null;
    long sizes3[] = null;
    CmdResult results[] = null;

    // range of file sizes
    final long MIN_SIZE = 30618419; // 29.2 MB
    final long RANGE = 1677722; // 1.6 MB
    final long MIN_SIZE2 = 2936013; // 2.8 MB
    final long RANGE2 = 419430; // 0.4 MB
    final long MIN_SIZE3 = 276480; // .9 x 300K
    final long RANGE3 = 61440; // .2 x 300K

    // md/nfs
    final int RAN_LEN = 4;
    final String VIEW1 = "study_group_sample";
    final String VIEW2 = "study_group_processing";
    HashMap md = new HashMap();
    int scan_number = 0;
    String study_id, sample_type, sample_id, subject_id, species_id;
    String collection_date, processing_date, collected_by, processed_by;
    String experiment_date, experiment_number;

    final static String[] species = {
                "moose", "squirrel", "mongoose", "whippet", "human", "rat"
                };
    final static String[] collectors = {
                "george bush", "john kerry", "kermit frog"
                };
    final static String[] processors = {
                "dick cheney", "john edwards", "j lo"
                };
    final static String[] types = {
                "serum", "plasma", "saliva", "skin"
                };
    final static String[] algorithms = {
                "p2p", "www", "aba", "bab"
                };
    final static String[] content_types = {
                "Peaks", "Isotope distributions", "Peptide assignments"
                };
    final static String[] blood_types = {
                "A", "B", "AB", "O"
                };

    String mount_point, nfs1, nfs2;

    public CedarsLoadTask(int client, int n_files, int iterations) 
                                                 throws HoneycombTestException {
        super();

        this.client = client;
        this.n_files = n_files;
        this .iterations = iterations;

        sizes1 = new long[n_files];
        for (int i=0; i<sizes1.length; i++) {
            sizes1[i] =  MIN_SIZE + (long)
                                   (RandomUtil.getDouble() * (double)RANGE);
        }
        Arrays.sort(sizes1);

        sizes2 = new long[n_files];
        for (int i=0; i<sizes2.length; i++) {
            sizes2[i] =  MIN_SIZE2 + (long) 
                                     (RandomUtil.getDouble() * (double)RANGE2);
        }
        Arrays.sort(sizes2);

        sizes3 = new long[n_files];
        for (int i=0; i<sizes3.length; i++) {
            sizes3[i] =  MIN_SIZE3 + (long) 
                                     (RandomUtil.getDouble() * (double)RANGE3);
        }
        Arrays.sort(sizes3);

        results = new CmdResult[n_files];

        study_id = "study_" + RandomUtil.getRandomString(RAN_LEN);
        sample_id = "sample_id_" + RandomUtil.getRandomString(RAN_LEN);
        sample_type = types[RandomUtil.randIndex(types.length)];
        //processing_param = "proc_" + RandomUtil.getRandomString(RAN_LEN);
        subject_id = "subject_" + RandomUtil.getRandomString(RAN_LEN);
        species_id = "species_" + species[RandomUtil.randIndex(species.length)];
        collection_date = 
                       new Date(System.currentTimeMillis() - 336000).toString();
        processing_date = new Date().toString();
        collected_by = collectors[RandomUtil.randIndex(collectors.length)];
        processed_by = processors[RandomUtil.randIndex(processors.length)];
        experiment_date = new Date(System.currentTimeMillis() - 636000).toString();
        experiment_number = Integer.toString(RandomUtil.randIndex(9999999));

        md.put("cedars_study_id", study_id);
        md.put("cedars_sample_id", sample_id);
        md.put("cedars_sample_type", sample_type);
        md.put("cedars_subject_id", subject_id);
        md.put("cedars_species_id", species_id);
        md.put("cedars_collection_date", collection_date);
        md.put("cedars_collected_by", collected_by);
        md.put("cedars_processed_by", processed_by);
        md.put("cedars_processing_date", processing_date);
        md.put("cedars_experiment_date", experiment_date);
        md.put("cedars_experiment_number", experiment_number);
        //md.put("cedars_processing_param", processing_param);

        mount_point = "/tmp/nfs";
        nfs1 = mount_point + '/' + VIEW1 + '/' + study_id + '/' +
                sample_id + '/';
        nfs2 = mount_point + '/' + VIEW2 + '/' + study_id + '/' + 
                sample_id + '/';

    }

    public void run() {
        while (cycles < iterations  &&  !stop) {
            Log.INFO("Starting pass " + cycles + " client " + client);
            if (store(sizes1, store1_bw))
                break;
            Log.INFO("store1 done, pass " + cycles + " client " + client);
            if (readwrite(sizes2, retrieve1_bw, store2_bw))
                break;
            Log.INFO("retrieve/store2 done, pass " + cycles + " client " + 
                       client);
            if (readwrite(sizes3, retrieve2_bw, store3_bw))
                break;
            Log.INFO("retrieve/store3 done, pass " + cycles + " client " + 
                       client);
            if (retrieve(retrieve3_bw))
                break;
            cycles++;
        }
    }

    private boolean store(long[] sizes, BandwidthStatistic bw) {

        md.put("cedars_scan_number", Integer.toString(scan_number));
        scan_number++;

        for (int i=0; i<n_files; i++) {
            results[i] = null;
            try {
                CmdResult cr = testBed.store(client, sizes[i], md, null);
                if (cr.pass) {
                    if (cr.datasha1 == null) {
                        Log.ERROR("No sha1 for store from harness");
                        stop = true;
                        aborted = true;
                        return true;
                    }
                    results[i] = cr;
                    bw.add(cr.time, cr.filesize);
                } else {
                    store_failed++;
                }
            } catch (Throwable t) {
                Log.ERROR(Log.stackTrace(t));
                stop = true;
                aborted = true;
                return true;
            }
        }
        return false;
    }

    private boolean retrieve(BandwidthStatistic bw) {
        for (int i=0; i<results.length; i++) {

            CmdResult cr = results[i];
            if (cr == null)
                continue;
            results[i] = null;

            //
            //  retrieve
            //
            try {
                CmdResult cr2 = testBed.retrieve(client, cr.mdoid);
                if (!cr2.pass) {
                    retrieve_failed++;
                    continue;
                }
                if (!cr2.datasha1.equals(cr.datasha1)) {
                    retrieve_bad_data++;
                    Log.ERROR("Bad data: " + cr.datasha1 + "/" + cr2.datasha1 + 
                              ": " + cr.mdoid);
                    continue;
                }
                bw.add(cr2.time, cr2.filesize);
            } catch (Throwable t) {
                Log.ERROR(Log.stackTrace(t));
                stop = true;
                aborted = true;
                return true;
            }
        }
        return false;
    }

    private boolean readwrite(long[] sizes, BandwidthStatistic retrieve_bw, 
                                            BandwidthStatistic store_bw) {
        for (int i=0; i<results.length; i++) {

            CmdResult cr = results[i];
            if (cr == null)
                continue;
            if (cr.datasha1 == null) {
                Log.ERROR("No sha1 for store from harness [2]");
                stop = true;
                aborted = true;
                return true;
            }
            results[i] = null;

            //
            //  retrieve
            //
            try {
                CmdResult cr2 = testBed.retrieve(client, cr.mdoid);
                if (!cr2.pass) {
                    retrieve_failed++;
                    continue;
                }
                if (cr2.datasha1 == null) {
                    Log.ERROR("No sha1 from harness on retrieve");
                    stop = true;
                    aborted = true;
                    return true;
                }
                if (!cr2.datasha1.equals(cr.datasha1)) {
                    retrieve_bad_data++;
                    Log.ERROR("Bad data: " + cr.datasha1 + "/" + cr2.datasha1 + 
                              ": " + cr.mdoid);
                    continue;
                }

                retrieve_bw.add(cr2.time, cr2.filesize);

            } catch (Throwable t) {
                Log.ERROR(Log.stackTrace(t));
                stop = true;
                aborted = true;
                return true;
            }

            //
            //  store
            //
            try {
                cr = testBed.store(client, sizes[i], md, null);
                if (cr.pass) {
                    results[i] = cr;
                    store_bw.add(cr.time, cr.filesize);
                } else {
                    store_failed++;
                }
            } catch (Throwable t) {
                Log.ERROR(Log.stackTrace(t));
                stop = true;
                aborted = true;
                return true;
            }
        }
        return false;
    }
}
