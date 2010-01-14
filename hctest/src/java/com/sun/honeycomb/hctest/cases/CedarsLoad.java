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

public class CedarsLoad extends HoneycombRemoteSuite {

    //private long timeout = 0;
    private int threadsPerClient = 1;
    private int nfiles = 1500;
    private int iterations = 1;

    public CedarsLoad() {
        super();
    }

    public String help() {

        StringBuffer sb = new StringBuffer();

        sb.append("\tDistributed Cedars Sinai workload simulation.\n");
        sb.append("\tStores N files, retrieves each and stores a smaller, and again.\n");
        sb.append("\tFile sizes are 30M, 2.8M, and 300K. Metadata included - needs\n");
        sb.append("\tappropriate schema.\n");
        sb.append("\tDefaults:\n\t\t");
        sb.append(HoneycombTestConstants.PROPERTY_CLIENTS_PER_HOST);
        sb.append("=").append(threadsPerClient);
        sb.append("\n\t\t");
        sb.append(HoneycombTestConstants.PROPERTY_NFILES);
        sb.append("=").append(nfiles);
        sb.append("\n\t\t");
        sb.append(HoneycombTestConstants.PROPERTY_ITERATIONS);
        sb.append("=").append(iterations);
        sb.append("\n\tsettable by -ctx.\n");

        return sb.toString();
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        TestCase self = createTestCase("DistributedSetup for CedarsLoad.java");
        self.addTag(Tag.DISTRIBUTED);
        if (self.excludeCase()) 
            return;

        super.setUp();

        String s = getProperty(HoneycombTestConstants.PROPERTY_CLIENTS_PER_HOST);
        if (s != null) {
            threadsPerClient = Integer.parseInt(s);
            if (threadsPerClient < 1)
                throw new HoneycombTestException(
                            HoneycombTestConstants.PROPERTY_CLIENTS_PER_HOST +
                            " < 1");
        }
        s = getProperty(HoneycombTestConstants.PROPERTY_NFILES);
        if (s != null) {
            nfiles = Integer.parseInt(s);
            if (nfiles < 1)
                throw new HoneycombTestException(
                           HoneycombTestConstants.PROPERTY_NFILES + " < 1");
        }
        s = getProperty(HoneycombTestConstants.PROPERTY_ITERATIONS);
        if (s != null) {
            iterations = Integer.parseInt(s);
            if (iterations < 1)
                throw new HoneycombTestException(
                           HoneycombTestConstants.PROPERTY_ITERATIONS + " < 1");
        }
        Log.INFO("setup done");
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /**
     *  Launch parallel Cedars threads.
     */
    public boolean testCedarsLoad() {
        addTag(Tag.DISTRIBUTED);
        if (excludeCase()) 
            return false;

        //
        //  start Cedars in parallel across all clients
        //  and wait for results
        //
        CedarsLoadTask[] tasks;
        try {
            tasks = startCedarsTasks(threadsPerClient, nfiles, iterations);
            waitForTasks(tasks, 0);
        } catch(Exception e) {
            Log.ERROR("waiting for tasks: " + e);
            return false;
        }

        //
        //  collate results
        //
        int aborted_tasks = 0;
        int cycles = 0;
        int store_failed = 0;
        int retrieve_failed = 0;
        int retrieve_bad_data = 0;
        BandwidthStatistic store1_bw = new BandwidthStatistic("store1_bw");
        BandwidthStatistic store2_bw = new BandwidthStatistic("store2_bw");
        BandwidthStatistic store3_bw = new BandwidthStatistic("store3_bw");
        BandwidthStatistic store_net_bw = new BandwidthStatistic("store_net_bw");
        BandwidthStatistic retrieve1_bw = new BandwidthStatistic("retrieve1_bw");
        BandwidthStatistic retrieve2_bw = new BandwidthStatistic("retrieve2_bw");
        BandwidthStatistic retrieve3_bw = new BandwidthStatistic("retrieve3_bw");
        BandwidthStatistic retrieve_net_bw = new BandwidthStatistic("retrieve_net_bw");

        for (int i=0; i<tasks.length; i++) {

            CedarsLoadTask clt = tasks[i];

            if (clt.aborted)
                aborted_tasks++;

            cycles += clt.cycles;
            store_failed += clt.store_failed;
            retrieve_failed += clt.retrieve_failed;
            retrieve_bad_data += clt.retrieve_bad_data;

            store1_bw.add(clt.store1_bw);
            store2_bw.add(clt.store2_bw);
            store3_bw.add(clt.store3_bw);

            retrieve1_bw.add(clt.retrieve1_bw);
            retrieve2_bw.add(clt.retrieve2_bw);
            retrieve3_bw.add(clt.retrieve3_bw);
        }
        store_net_bw.add(store1_bw);
        store_net_bw.add(store2_bw);
        store_net_bw.add(store3_bw);
        retrieve_net_bw.add(retrieve1_bw);
        retrieve_net_bw.add(retrieve2_bw);
        retrieve_net_bw.add(retrieve2_bw);

        if (aborted_tasks > 0)
            Log.ERROR("ABORTED TASKS:   " + aborted_tasks);
        StringBuffer sb = new StringBuffer();
        sb.append("Total cycles:      " + cycles).append("\n");
        if (store_failed > 0)
            sb.append("Store failed:      " + store_failed).append("\n");
        if (retrieve_failed > 0)
            sb.append("Retrieve failed:   " + retrieve_failed).append("\n");
        if (retrieve_bad_data > 0)
            sb.append("Retrieve bad data: " + retrieve_bad_data).append("\n");
        sb.append(store1_bw.toString()).append("\n");
        sb.append(store2_bw.toString()).append("\n");
        sb.append(store3_bw.toString()).append("\n");
        sb.append(store_net_bw.toString()).append("\n");
        sb.append(retrieve1_bw.toString()).append("\n");
        sb.append(retrieve2_bw.toString()).append("\n");
        sb.append(retrieve3_bw.toString()).append("\n");
        sb.append(retrieve_net_bw.toString());
        Log.SUM(sb.toString());

        if (aborted_tasks + store_failed + retrieve_failed + retrieve_bad_data
                   > 0)
            return false;

        //
        //  PASS
        //
        return true;
    }
}
