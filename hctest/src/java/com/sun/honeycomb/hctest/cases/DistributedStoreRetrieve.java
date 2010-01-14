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



package com.sun.honeycomb.hctest.cases;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.hctest.task.*;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.client.*;

import java.util.ArrayList;

public class DistributedStoreRetrieve extends HoneycombRemoteSuite {

    // control
    private ArrayList sizes;
    private long filesize = 1024;
    private long timeout = 0;
    private int threadsPerClient = 1;
    private boolean rotateClients = false;
    private int iterations = 1;

    // analysis
    Statistic store_stats = new Statistic("all stores (ms)");
    Statistic retrieve_stats = new Statistic("all retrieves (ms)");
    BandwidthStatistic store_bw = new BandwidthStatistic("all stores", true);
    BandwidthStatistic retrieve_bw = new BandwidthStatistic("all retrieves", true);
    // for now aggregate times include sha1sum, so skip
    //Statistic store_stats_ag = new Statistic("all parallel stores");
    //Statistic store_stats_ag_sz = new Statistic("all parallel stores sz");
    //Statistic retrieve_stats_ag = new Statistic("all parallel retrieves");

    // output
    StringBuffer sb = new StringBuffer();

    StoreTask stores[] = null;

    public DistributedStoreRetrieve() {
        super();
    }

    public String help() {
        StringBuffer sb = new StringBuffer();
        sb.append("\tSimple distributed store/retrieve test demo.\n");
        sb.append("\tDefault filesize is ").append(filesize);
        sb.append(", multiple filesizes can be set.\n");
        if (timeout == 0) {
            sb.append("\tDefault timeout is 0 (==none).");
        } else {
            sb.append("\tDefault timeout is ").append(timeout).append("msec.");
        }
        sb.append("\n\tDefault ");
        sb.append(HoneycombTestConstants.PROPERTY_CLIENTS_PER_HOST);
        sb.append(" is ").append(threadsPerClient);
        sb.append(", default "); 
        sb.append(HoneycombTestConstants.PROPERTY_ITERATIONS);
        sb.append(" is ").append(iterations);
        sb.append(".\n\tDefault ");
        sb.append(HoneycombTestConstants.PROPERTY_ROTATE_CLIENTS);
        sb.append(" is ").append(rotateClients);
        sb.append(" (setting any value = 'true' and\n");
        sb.append("\tcauses retrieves from all clients that didn't store).\n");
        sb.append("\tAll settable by -ctx. Requires remote clients.\n");

        return sb.toString();
    }
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        TestCase self = createTestCase(
                             "DistributedSetup for DistributedStoreRetrieve");
        self.addTag(Tag.DISTRIBUTED);
        if (self.excludeCase()) 
            return;

        super.setUp();

        sizes = getFilesizeList();

        if (getFilesize() != HoneycombTestConstants.INVALID_FILESIZE)
            filesize = getFilesize();
        String s = getProperty(HoneycombTestConstants.PROPERTY_TIMEOUT);
        if (s != null) {
            timeout = HCUtil.parseTime(s);
            if (timeout < 0)
                throw new HoneycombTestException("timeout < 0");
        }
        s = getProperty(HoneycombTestConstants.PROPERTY_ITERATIONS);
        if (s != null) {
            iterations = Integer.parseInt(s);
            if (iterations <= 0)
                throw new HoneycombTestException(
                            HoneycombTestConstants.PROPERTY_ITERATIONS +
                            " < 1");
        }
        s = getProperty(HoneycombTestConstants.PROPERTY_CLIENTS_PER_HOST);
        if (s != null) {
            threadsPerClient = Integer.parseInt(s);
            if (threadsPerClient <= 0)
                throw new HoneycombTestException(
                            HoneycombTestConstants.PROPERTY_CLIENTS_PER_HOST +
                            " < 1");
        }
        s = getProperty(HoneycombTestConstants.PROPERTY_ROTATE_CLIENTS);
        if (s != null)
            rotateClients = true;

        if (sizes.size() == 0)
            sizes.add(new Long(filesize));

        Log.INFO("filesize " + sizes + "  timeout " + timeout +
                "  " + HoneycombTestConstants.PROPERTY_CLIENTS_PER_HOST + 
                " " + threadsPerClient);
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /**
     *  Store in parallel, then retrieve in parallel across all clients.
     */
    public boolean testMultipleStoreRetrieve() {
        addTag(Tag.DISTRIBUTED);
        if (excludeCase()) 
            return false;

        for (int iter=0; iter<iterations; iter++) {
            Log.INFO("======= iteration " + iter);

            if (!doit(iter))
                return false;
        }
        long avg_filesize = 0;
        for (int ii=0; ii<sizes.size(); ii++) {
            Long l = (Long) sizes.get(ii);
            avg_filesize += l.longValue();
        }
        avg_filesize /= sizes.size();

        String ss[] = new String[9];
        ss[0] = "===================== summary =========================";
        ss[1] = store_stats.toString();
        ss[2] = "Average per-file store bandwidth: " +
                HCUtil.megabytesPerSecond(store_stats.average(), avg_filesize);
        ss[3] = retrieve_stats.toString();
        ss[4] = "Average per-file retrieve bandwidth: " +
             HCUtil.megabytesPerSecond(retrieve_stats.average(), avg_filesize);
        ss[5] = store_bw.toString();
        ss[6] = retrieve_bw.toString();
        ss[7] = "  NOTE THAT ALL STATS INCLUDE OVERHEAD - SEE AUDIT DB FOR";
        ss[8] = "  CORRECT STATS";

        Log.SUM(ss);

        return true;
     }
     private boolean doit(int iter) {

        // group SUM msgs into 1 since each can generate an ssh
        // error in cluster log

        for (int ii=0; ii<sizes.size(); ii++) {

            Long l = (Long) sizes.get(ii);
            filesize = l.longValue();

            Log.INFO("==== storing size " + filesize + "  iteration " + iter);

            //
            //  store in parallel across all clients
            //  and wait for results
            //
            long t1 = System.currentTimeMillis();
            stores = startStores(filesize, threadsPerClient, false);
            try {
                waitForTasks(stores, timeout);
            } catch(Exception e) {
                Log.ERROR("waiting for stores: " + e);
                return false;
            }
            // includes sha1sum time so not directly meaningful
            long store_t = System.currentTimeMillis() - t1;

            //
            //  check results
            //
            long tot_time = 0;
            int errors = 0;
            for (int i=0; i<stores.length; i++) {
                StoreTask st = stores[i];
                if (st == null) {
                    Log.ERROR("store: missing task " + i);
                    return false;
                }
                if (!st.isDone()) {
                    Log.ERROR("store: task not done: " + i);
                    errors++;
                    continue;
                }

                String tag = null;
                if (st.result == null)
                    tag = "(result null)";
                else if (st.result.logTag == null)
                    tag = "(tag null)";
                else
                    tag = "(tag " + st.result.logTag + ")";

                //
                //  check for harness errors 1st
                //
                if (st.thrown.size() > 0) {
                    Log.ERROR("STORE ERROR " + tag);
                    for (int j=0; j<st.thrown.size(); j++) {
                        Throwable t = (Throwable) st.thrown.get(j);
                        Log.ERROR("\ttask exception [client " + i + "] " + 
                                                        Log.stackTrace(t));
                    }
                    errors++;
                    continue;
                }
                if (st.result == null) {
                    Log.ERROR("store: missing result " + i + " " + tag);
                    errors++;
                    continue;
                }

                //
                //  check API errors (retry may have succeeded)
                //
                if (st.result.thrown != null) {
                    String s;
                    if (st.result.pass)
                        s = " [pass] ";
                    else
                        s = " [fail] ";
                    for (int j=0; j<st.result.thrown.size(); j++) {
                        Throwable t = (Throwable) st.result.thrown.get(j);
                        if (st.result.pass)
                            Log.INFO("\t" + tag + s + t.toString());
                        else
                            Log.ERROR("\t" + tag + s + t.toString() +
                                       Log.stackTrace(t));
                    }
                }
                if (!st.result.pass) {
                    Log.ERROR("store failed: " + tag);
                    errors++;
                    continue;
                }

                if (st.result.mdoid == null) {
                    Log.ERROR("store: missing mdoid " + tag);
                    errors++;
                    continue;
                }
                if (TestBed.doHash  && st.result.datasha1 == null) {
                    Log.ERROR("store: missing sha " + tag);
                    errors++;
                    continue;
                }
                if (st.result.retries > 0)
                    Log.INFO("clnt " + i + " stored " + st.result.mdoid + 
                             " on " + st.result.dataVIP + 
                             " RETRIES: " + st.result.retries);
                else
                    Log.INFO("clnt " + i + " stored " + st.result.mdoid + 
                             " on " + st.result.dataVIP);
            }

            if (errors > 0)
                return false;

            //
            //  get metric for stores
            //
            Statistic store_time = timeStat("store times (ms)", stores);
            //BandwidthStatistic st_bw = timeStat("stores", stores);
            store_bw.add(bwStat("stores", stores));

            sb.setLength(0);
            sb.append(store_time.toString()).append("\n");

            // store_t includes sha1sum time so not so meaningful
            //sb.append("Aggregate store test time: ");
            //sb.append(store_t).append(" N ").append(stores).append("\n");

            sb.append("Bandwidth: avg ");
            sb.append(
                 HCUtil.megabytesPerSecond(store_time.average(), filesize));
            sb.append(" agg ").append(
                 HCUtil.megabytesPerSecond(store_time.average(), 
                                           stores.length * filesize));

            // store_t includes sha1sum time so not so meaningful
            //sb.append("\n");
            //sb.append("Aggregate store bandwidth: ");
            //sb.append(
            //    HCUtil.megabytesPerSecond(store_t, stores.length * filesize));

            Log.SUM(sb.toString());

            store_stats.addStatistic(store_time);
            //store_stats_ag.addValue(store_t);
            //store_stats_ag_sz.addValue(filesize * stores.length);

            if (rotateClients) {
                //
                //  rotate/retrieve n-1 times
                //
                for (int jj=1; jj<stores.length; jj++) {
                    // rotate
                    StoreTask st = stores[0];
                    for (int i=1; i<stores.length; i++)
                        stores[i-1] = stores[i];
                    stores[stores.length-1] = st;

                    if (!doRetrieves())
                        return false;
                }
            } else {
                if (!doRetrieves())
                    return false;
            }
        }
        //
        //  PASS
        //
        return true;
    }

    private boolean doRetrieves() {
        //
        //  launch retrieves and wait for results
        //
        long t1 = System.currentTimeMillis();
        RetrieveTask[] retrieves = startRetrieves(stores, false);
        try {
            waitForTasks(retrieves, timeout);
        } catch(Exception e) {
            Log.ERROR("waiting for retrieves: " + e);
            return false;
        }
        // includes sha1sum time, so not so meaningful
        long retrieve_t = System.currentTimeMillis() - t1;

        //
        //  check results against stores
        //
        int errors = 0;
        for (int i=0; i<retrieves.length; i++) {

            RetrieveTask rt = retrieves[i];

            if (!rt.isDone()) {
                Log.ERROR("retrieve: task not done: " + i);
                errors++;
                continue;
            }

            String tag = null;
            if (rt.result == null)
               tag = "(result null)";
            else if (rt.result.logTag == null)
               tag = "(tag null)";
            else
               tag = "(tag " + rt.result.logTag + ")";

            //
            //  check for harness errors 1st
            //
            if (rt.thrown.size() > 0) {
                Log.ERROR("RETRIEVE ERROR " + tag);
                for (int j=0; j<rt.thrown.size(); j++) {
                    Throwable t = (Throwable) rt.thrown.get(j);
                    Log.ERROR("\ttask exception [" + i + "] " + 
                                                        Log.stackTrace(t));
                }
                errors++;
                continue;
            }
            if (rt.result == null) {
                Log.ERROR("retrieve: missing result " + tag);
                errors++;
                continue;
            }

            //
            //  check API errors (retry may have succeeded)
            //
            if (rt.result.thrown != null) {
                String s;
                if (rt.result.pass)
                    s = " [pass] ";
                else
                    s = " [fail] ";
                for (int j=0; j<rt.result.thrown.size(); j++) {
                    Throwable t = (Throwable) rt.result.thrown.get(j);
                    if (rt.result.pass)
                        Log.INFO("\t" + tag + s + t.toString());
                    else
                        Log.ERROR("\t" + tag + s + t.toString());
                }
            }
            if (rt.result.pass == false) {
                Log.ERROR("retrieve: pass=false");
                errors++;
                continue;
            }
            if (TestBed.doHash  &&  rt.result.datasha1 == null) {
                Log.ERROR("retrieve: missing sha " + tag + " pass=" + 
                                     rt.result.pass + 
                                     " and no returned Exception (?)");
                errors++;
                continue;
            }
            StoreTask st = stores[i];
            if (TestBed.doHash  &&  !st.result.datasha1.equals(rt.result.datasha1)) {
                Log.ERROR("sha does not match " + tag);
                return false;
            }
            if (rt.result.retries > 0)
                Log.INFO("clnt " + i + " retrieved " + rt.result.mdoid +
                         " from " + rt.result.dataVIP + 
                         " RETRIES " + rt.result.retries);
            else
                Log.INFO("clnt " + i + " retrieved " + rt.result.mdoid +
                         " from " + rt.result.dataVIP);
        }

        if (errors > 0)
            return false;

        //
        //  get aggregate metric for retrieves
        //
        Statistic retrieve_stat = timeStat("retrieve times (ms)", retrieves);
        retrieve_bw.add(bwStat("retrieves", retrieves));

        sb.setLength(0);
        sb.append(retrieve_stat.toString()).append("\n");

        // retrieve_t includes sha1sum time, so not so meaningful
        //sb.append("Aggregate rtrv test time: ").append(retrieve_t);
        //sb.append(" N ").append(retrieves.length).append("\n");

        sb.append("Bandwidth: avg ").append(
            HCUtil.megabytesPerSecond(retrieve_stat.average(), filesize));
        sb.append(" agg ").append(
            HCUtil.megabytesPerSecond(retrieve_stat.average(), 
                                      retrieves.length * filesize));

        // retrieve_t includes sha1sum time, so not so meaningful
        //sb.append("\n");
        //sb.append("Aggregate retrieve bandwidth: ").append(
        //    HCUtil.megabytesPerSecond(retrieve_t, 
        //                              retrieves.length * filesize));

        Log.SUM(sb.toString());

        retrieve_stats.addStatistic(retrieve_stat);
        //retrieve_stats_ag.addValue(retrieve_t);

        return true;
    }

}
