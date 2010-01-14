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



package com.sun.honeycomb.hctest;

import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.hctest.task.*;

import com.sun.honeycomb.hctest.rmi.*;
import com.sun.honeycomb.hctest.rmi.spsrv.common.SPSrvConstants;

import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.client.*;

import java.io.File;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Random;

/**
 *  This class has helper functions for test cases that address 
 *  Honeycomb clusters via remote clients. The interfaces to the 
 *  remote (RMI) servers and synchronization facilities are defined 
 *  in test.rmi.HoneycombRMISuite.
 *
 *  HoneycombRMISuite requires that cluster and client host information
 *  be provided, either in a property file or as --ctx command-line 
 *  arguments (see TestRunner.java).
 *
 *  Methods useful for both local and remote access should be in
 *  HoneycombSuite.
 *
 *  For all inheritors of this class, be sure to call super.setUp()
 *  in your setUp() method and super.tearDown() likewise.
 */
public class HoneycombRemoteSuite extends HoneycombRMISuite {

    public HoneycombRemoteSuite() {
        super();
    }

    public void setUp() throws Throwable {
        Log.DEBUG("com.sun.honeycomb.test.HoneycombRemoteSuite::setUp() called");
        super.setUp();

        //
        //  if there's a SP, see if there's a SP server
        //
        if (testBed.spIP != null) {
            try {
                testBed.startSP();
            } catch (Exception e) {}
        }
    }

    /////////////////////////////////////////////////////////////////////////
    //  synchronous infrastructure metrics
    //
    public CmdResult timeRMI() throws HoneycombTestException {
        return testBed.timeRMI(false);
    }

    /////////////////////////////////////////////////////////////////////////
    //  asynchronous infrastructure metrics
    //
    public TimeTask startTimeRMI(int client) {
        TimeTask t = new TimeTask(client);
        t.start();
        return t;
    }
    public TimeTask[] startTimeRMIs() throws HoneycombTestException {

        int nclients = testBed.clientCount();
        TimeTask[] tasks = new TimeTask[nclients];
        for (int i=0; i<nclients; i++) {
            tasks[i] = startTimeRMI(i);
        }
        return tasks;
    }

    /////////////////////////////////////////////////////////////////////////
    //  synchronous compound and non-API capabilities
    //

    /**
     *  Return true if oid is in results of query q, else return false.
     */
    public boolean queryAndCheckOid(int client, String oid, String q)
                                                throws HoneycombTestException {

        CmdResult cr = query(client, q);
        return oidInQueryResults(oid, cr.rs);
    }

    /////////////////////////////////////////////////////////////////////////
    //  asynchronous API tasks
    //
    /**
     *  Start a store on a client.
     */
    public StoreTask startStore(int client, 
                                byte[] bytes ,
                                int repeats,
                                HashMap mdMap,
                                String dataVIP) {
        StoreTask t = new StoreTask(client, bytes, repeats, mdMap, dataVIP);
        t.setNoisy(false);
        t.start();
        return t;
    }

    public StoreTask startStore(int client, 
                                long filesize, 
                                HashMap mdMap) {
        return startStore(client, filesize, mdMap, null);

    }

    public StoreTask startStore(int client, 
                                long filesize, 
                                HashMap mdMap,
                                String dataVIP) {
        StoreTask t = new StoreTask(client, filesize, mdMap, dataVIP);
        t.setNoisy(false);
        t.start();
        return t;
    }
    
    public QueryTask startQuery(int client, 
            String query,
            String dataVIP) {
    	QueryTask t = new QueryTask(client, query, dataVIP);
		t.setNoisy(false);
		t.start();
		return t;
	}
    
    public AssocMetaDataTask startAssocMetaData(int client, 
							            String oid,
							            HashMap mdMap,
							            String dataVIP) {
    	AssocMetaDataTask t = new AssocMetaDataTask(client, oid, mdMap, dataVIP);
    	t.setNoisy(false);
    	t.start();
    	return t;
	}

    /**
     *  Start stores of a given size on all clients.
     */
    public StoreTask[] startStores(long filesize) {
        int nclients = testBed.clientCount();
        StoreTask[] tasks = new StoreTask[nclients];
        for (int i=0; i<nclients; i++) {
            tasks[i] = startStore(i, filesize, null);
        }
        return tasks;
    }
    /**
     *  Start stores of a given size on all clients w/ control of logging noise.
     */
    public StoreTask[] startStores(long filesize, boolean noisy) {
        int nclients = testBed.clientCount();
        StoreTask[] tasks = new StoreTask[nclients];
        for (int i=0; i<nclients; i++) {
            tasks[i] = startStore(i, filesize, null);
            tasks[i].noisy = noisy;
        }
        return tasks;
    }

    /**
     *  Start stores of a given size on all clients, 
     *  w/ multiple threads/clnt.
     */
    public StoreTask[] startStores(long filesize, int nthreads) {
        int nclients = testBed.clientCount();
        StoreTask[] tasks = new StoreTask[nclients * nthreads];
        int index = 0;
        for (int i=0; i<nclients; i++) {
            for (int j=0; j<nthreads; j++) {
                tasks[index++] = startStore(i, filesize, null);
            }
        }
        return tasks;
    }
    public StoreTask[] startStores(long filesize, int nthreads, boolean noisy) {
        int nclients = testBed.clientCount();
        StoreTask[] tasks = new StoreTask[nclients * nthreads];
        int index = 0;
        for (int i=0; i<nclients; i++) {
            for (int j=0; j<nthreads; j++) {
                tasks[index] = startStore(i, filesize, null);
                tasks[index].noisy = noisy;
                index++;
            }
        }
        return tasks;
    }

    /**
     *  Start stores of given sizes distributed across clients.
     */
    public StoreTask[] startStores(ArrayList filesizes) {
        int nclients = testBed.clientCount();
        int nfiles = filesizes.size();
        StoreTask[] tasks = new StoreTask[nfiles];
        int client = 0;
        for (int i=0; i<nfiles; i++) {

            long filesize = ((Long)filesizes.get(i)).longValue();
            tasks[i] = startStore(client, filesize, null);

            client++;
            if (client == nclients)
                client = 0;
        }
        return tasks;
    }

    /**
     *  Start a retrieve on a client.
     */
    public RetrieveTask startRetrieve(int client, String oid) {
        return startRetrieve(client, oid, false, null);
    }

    public RetrieveTask startRetrieve(int client, String oid, String dataVIP) {
        return startRetrieve(client, oid, false, dataVIP);
    }

    public RetrieveTask startRetrieve(int client, String oid, boolean noisy,
                                                             String dataVIP) {
        RetrieveTask t = new RetrieveTask(client, oid, noisy, dataVIP);
        t.setNoisy(false);
        t.start();
        return t;
    }
    
    /**
     *  Start a retrieve of MD on a client.
     */
    public RetrieveMDTask startRetrieveMD(int client, String oid) {
        return startRetrieveMD(client, oid, false, null);
    }

    public RetrieveMDTask startRetrieveMD(int client, String oid, String dataVIP) {
        return startRetrieveMD(client, oid, false, dataVIP);
    }

    public RetrieveMDTask startRetrieveMD(int client, String oid, boolean noisy,
                                                             String dataVIP) {
        RetrieveMDTask t = new RetrieveMDTask(client, oid, noisy, dataVIP);
        t.setNoisy(false);
        t.start();
        return t;
    }

    /**
     *  Start retrieves of oids, spread across clients.
     */
    public RetrieveTask[] startRetrieves(String[] oids) {
        return startRetrieves(oids, true);
    }
    public RetrieveTask[] startRetrieves(String[] oids, boolean noisy) {
        RetrieveTask[] tasks = new RetrieveTask[oids.length];
        int nclients = testBed.clientCount();
        int client = 0;
        for (int i=0; i<oids.length; i++) {
            // wrap client if needed
            if (client == nclients)
                client = 0;
            tasks[i] = startRetrieve(client, oids[i], noisy, null);
            client++;
        }
        return tasks;
    }

    /**
     *  Start retrieves of oids resulting from stores, 
     *  spread across clients.
     */
    public RetrieveTask[] startRetrieves(StoreTask[] stores) {
        return startRetrieves(stores, true);
    }
    public RetrieveTask[] startRetrieves(StoreTask[] stores, boolean noisy) {
        return startRetrieves(stores, noisy, null);
    }

    public RetrieveTask[] startRetrieves(StoreTask[] stores, boolean noisy, 
                                                             String dataVIP) {
        RetrieveTask[] tasks = new RetrieveTask[stores.length];
        int nclients = testBed.clientCount();
        int client = 0;
        for (int i=0; i<stores.length; i++) {
            // wrap client if needed
            if (client == nclients)
                client = 0;
            tasks[i] = startRetrieve(client, stores[i].result.mdoid, noisy,
                                                                      dataVIP);
            client++;
        }
        return tasks;
    }

    /**
     *  Start a delete on a client.
     */
    public DeleteTask startDelete(int client, String oid) {
        return startDelete(client, oid, false);
    }
    public DeleteTask startDelete(int client, String oid, boolean noisy) {
        DeleteTask t = new DeleteTask(client, oid);
        t.setNoisy(noisy);
        t.start();
        return t;
    }

    public AuditTask startAudit(int num, String oid) {
    	AuditTask t = new AuditTask(num, oid);
    	t.start();
    	return t;
	}
    /**
     *  Start deletes of oids, spread across clients.
     */
    public DeleteTask[] startDeletes(String[] oids) {
        return startDeletes(oids, true);
    }
    public DeleteTask[] startDeletes(String[] oids, boolean noisy) {
        DeleteTask[] tasks = new DeleteTask[oids.length];
        int nclients = testBed.clientCount();
        int client = 0;
        for (int i=0; i<oids.length; i++) {
            if (oids[i] == null)
                continue;

            // wrap client if needed
            if (client == nclients)
                client = 0;
            tasks[i] = startDelete(client, oids[i]);
            tasks[i].noisy = noisy;
            client++;
        }
        return tasks;
    }

    /**
     *  Start deletes of oids resulting from stores, 
     *  spread across clients.
     */
    public DeleteTask[] startDeletes(StoreTask[] stores) {
        return startDeletes(stores, true);
    }
    public DeleteTask[] startDeletes(StoreTask[] stores, boolean noisy) {
        DeleteTask[] tasks = new DeleteTask[stores.length];
        int nclients = testBed.clientCount();
        int client = 0;
        for (int i=0; i<stores.length; i++) {
            // wrap client if needed
            if (client == nclients)
                client = 0;
            tasks[i] = startDelete(client, stores[i].result.mdoid, noisy);
            client++;
        }
        return tasks;
    }

    /**
     *  Start audit retrieves of oids from cluster audit db/tree,
     *  spread across clients. 
     *  XXX add thread limit if files<clients*perClient
     */
    public ClientAuditThread[] startAuditThreads(AuditDBClient ac, int perClnt) 
                                                 throws HoneycombTestException {

        int nclients = testBed.clientCount();
        int nthreads = nclients * perClnt;
        ClientAuditThread[] tasks = new ClientAuditThread[nthreads];
        int index = 0;
        int usedclnts = 0;
        for (int i=0; i<nclients; i++) {
            usedclnts++;
            for (int j=0; j<perClnt; j++) {
                tasks[index] = new ClientAuditThread(i, ac);
                tasks[index].start();
                index++;
                if (index >= nthreads)
                    break;
            }
            if (index >= nthreads)
                break;
        }
        Log.INFO("Started " + index + " threads on " + usedclnts + " clients");
        return tasks;
    }
    
    /**
     *  Start Cedars workload threads spread across clients. 
     */
    public CedarsLoadTask[] startCedarsTasks(int perClnt, int n_files, 
                                             int iterations) 
                                                 throws HoneycombTestException {

        int nclients = testBed.clientCount();
        int nthreads = nclients * perClnt;
        if (nthreads == 0)
            return new CedarsLoadTask[0];
        CedarsLoadTask[] tasks = new CedarsLoadTask[nthreads];
        int index = 0;
        int usedclnts = 0;
        for (int i=0; i<nclients; i++) {
            usedclnts++;
            for (int j=0; j<perClnt; j++) {
                tasks[index] = new CedarsLoadTask(i, n_files, iterations);
                tasks[index].start();
                index++;
                if (index >= nthreads)
                    break;
            }
            if (index >= nthreads)
                break;
        }
        Log.INFO("Started " + index + " threads on " + usedclnts + " clients");
        return tasks;
    }

    /////////////////////////////////////////////////////////////////////////
    //  sync of asynchronous tasks
    //

    /**
     *  Wait for task to finish - forever if timeout is 0.
     */
    public void waitForTask(Waitable task, long timeout)
                                                throws HoneycombTestException {
        Waitable taskArray[] = new Waitable[1];
        taskArray[0] = task;
        waitForTasks(taskArray, timeout);
    }

    /**
     *  Wait for all tasks to finish - forever if timeout is 0.
     */
    public void waitForTasks(Waitable[] tasks, long timeout)
                                                throws HoneycombTestException {
        Log.DEBUG("waitForTasks n=" + tasks.length + "  wait=" + timeout);
        long startWait = System.currentTimeMillis();
        long wait = 0;
        for (int i=0; i<tasks.length; i++) {

            if (tasks[i] == null) {
                Log.DEBUG("waitForTasks null task " + i);
                continue;
            }
            if (tasks[i].isDone()) {
                Log.DEBUG("waitForTasks isDone: " + i + "  n=" + tasks.length);
                continue;
            }

            if (timeout != 0) {
                //
                //  calc remaining wait time
                //
                long waited = System.currentTimeMillis() - startWait;
                wait = timeout - waited;
                if (wait <= 0) {
                    throw new HoneycombTestException(
                                            "waitForTasks: timeout exceeded");
                }
            }

            //
            //  do the wait
            //
            try {
                Log.DEBUG("waitForTasks joining " + i + 
                                    "  n=" + tasks.length + "  wait=" + wait);
                tasks[i].join(wait);
                if (!tasks[i].isDone()) {
                    throw new HoneycombTestException(
                                            "waitForTasks: timeout exceeded");
                }
                Log.DEBUG("waitForTasks joined " + i + "  n=" + tasks.length);
            } catch (Exception e) {
                tasks[i].thrown.add(e);
            }
        }
    }

    /**
     *  Tell tasks to finish - then wait politely until all are done.
     */
    public void endWaitForTasks(SequenceTask[] tasks) 
                                                throws HoneycombTestException {
        Log.DEBUG("endWaitForTasks n=" + tasks.length);
        for (int i=0; i<tasks.length; i++) {
            tasks[i].stopWhenReady();
        }
        waitForTasks(tasks, 0);
    }

    /////////////////////////////////////////////////////////////////////////
    //  stats gathering
    //
    public Statistic timeStat(String name, Task[] tasks) {
        Statistic s = new Statistic(name);
        for (int i=0; i<tasks.length; i++) {

            Task t = tasks[i];
            if (t == null)
                continue;
            if (t.result == null)
                continue;
            if (!t.result.pass)
                continue;

            s.addValue(t.result.time);
        }
        return s;
    }
    public BandwidthStatistic bwStat(String name, Task[] tasks) {
        BandwidthStatistic s = new BandwidthStatistic(name);
        for (int i=0; i<tasks.length; i++) {

            Task t = tasks[i];
            if (t == null)
                continue;
            if (t.result == null)
                continue;
            if (!t.result.pass)
                continue;

            s.add(t.result.time, t.result.filesize);
        }
        return s;
    }
}
