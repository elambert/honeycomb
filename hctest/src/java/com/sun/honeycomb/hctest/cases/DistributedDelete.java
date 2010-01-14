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
import com.sun.honeycomb.common.*;

public class DistributedDelete extends HoneycombRemoteSuite {

    private long filesize = 1024;
    private long timeout = 10000;

    public DistributedDelete() {
        super();
    }

    public String help() {

        StringBuffer sb = new StringBuffer();

        sb.append("\tSimple distributed store/delete test demo.\n");
        sb.append("\tDefault filesize is ").append(Long.toString(filesize));
        sb.append(", default timeout is ").append(Long.toString(timeout));
        sb.append(" msec.\n\tBoth settable by -ctx. Requires remote clients.\n");

        return sb.toString();
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        TestCase self =createTestCase("DistributedSetup for DistributedDelete");
        self.addTag(Tag.DISTRIBUTED);
        if (self.excludeCase()) 
            return;

        super.setUp();

        if (getFilesize() != HoneycombTestConstants.INVALID_FILESIZE)
            filesize = getFilesize();
        String s = getProperty(HoneycombTestConstants.PROPERTY_TIMEOUT);
        if (s != null) {
            timeout = HCUtil.parseTime(s);
            if (timeout < 0)
                throw new HoneycombTestException("timeout < 0");
        }
        Log.INFO("filesize " + filesize + "  timeout " + timeout);
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /**
     *  Store/delete/retrieve in parallel passes across all clients.
     */
    public boolean testMultipleStoreDeleteRetrieve() {
        addTag(Tag.DISTRIBUTED);
        if (excludeCase()) 
            return false;

        //
        //  store in parallel across all clients
        //  and wait for results
        //
        StoreTask stores[] = startStores(filesize, false);
        try {
            waitForTasks(stores, timeout);
        } catch(Exception e) {
            Log.ERROR("waiting for stores: " + e);
            return false;
        }

        //
        //  check results & collect oids
        //
        long tot_time = 0;
        int errors = 0;
        int ok = 0;
        String[] oids = new String[stores.length];
        for (int i=0; i<stores.length; i++) {
            StoreTask st = stores[i];
            if (st == null) {
                Log.ERROR("store: missing task " + i);
                return false;
            }

            String tag = null;
            if (st.result == null)
                tag = "(result null)";
            else if (st.result.logTag == null)
                tag = "(tag null)";
            else
                tag = "(tag " + st.result.logTag + ")";

            if (st.thrown.size() > 0) {
                Log.ERROR("STORE ERROR " + tag);
                for (int j=0; j<st.thrown.size(); j++) {
                    Throwable t = (Throwable) st.thrown.get(j);
                    Log.ERROR("\ttask exception [" + i + "] " + 
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
            if (!st.result.pass) {
                Log.ERROR("store failed " + tag);
                errors++;
                continue;
            }
            if (st.result.mdoid == null  ||  st.result.datasha1 == null) {
                Log.ERROR("store: missing mdoid or sha " + tag);
                errors++;
                continue;
            }
            ok++;
            oids[i] = st.result.mdoid;
            Log.INFO("clnt " + i + " stored " + st.result.mdoid);
        }

        //
        //  get aggregate metric for stores
        //
        Statistic store_time = timeStat("store times", stores);
        Log.INFO(store_time.toString());

        if (ok == 0)
            return false;

        //
        //  launch deletes and wait for results
        //
        DeleteTask[] deletes = startDeletes(oids, false);
        try {
            waitForTasks(deletes, 10000);
        } catch(Exception e) {
            Log.ERROR("waiting for deletes: " + e);
            return false;
        }
        //
        //  check results
        //
        ok = 0;
        for (int i=0; i<deletes.length; i++) {
            DeleteTask dt = deletes[i];
            if (dt == null)
                continue;

            String tag = null;
            if (dt.result == null)
                tag = "(result null)";
            else if (dt.result.logTag == null)
                tag = "(tag null)";
            else
                tag = "(tag " + dt.result.logTag + ")";

            if (dt.thrown.size() > 0) {
                Log.ERROR("DELETE ERROR THROWN " + tag);
                for (int j=0; j<dt.thrown.size(); j++) {
                    Throwable t = (Throwable) dt.thrown.get(j);
                    Log.ERROR("\ttask exception [" + i + "] " + 
                                                        Log.stackTrace(t));
                }
                errors++;
                continue;
            }
            if (dt.result == null) {
                Log.ERROR("DELETE NO RESULT " + tag);
                errors++;
                continue;
            }
            if (dt.result.thrown != null) {
                for (int j=0; j<dt.result.thrown.size(); j++) {
                    Throwable t = (Throwable) dt.result.thrown.get(j);
                    Log.ERROR("\t" + tag + " exception [" + i + "] " + t.toString());
                }
            }
            if (!dt.result.pass) {
                Log.ERROR("DELETE FAILED " + tag + " oid " + dt.oid);
                errors++;
                continue;
            }
            ok++;
        }

        //
        //  get aggregate metric for deletes
        //
        Statistic delete_time = timeStat("delete times", deletes);
        Log.INFO(delete_time.toString());

        if (ok == 0)
            return false;

        //
        //  launch retrieves and wait for results
        //
        RetrieveTask[] retrieves = startRetrieves(oids, false);
        try {
            waitForTasks(retrieves, 10000);
        } catch(Exception e) {
            Log.ERROR("waiting for retrieves: " + e);
            return false;
        }

        //
        //  check results against deletes
        //
        for (int i=0; i<retrieves.length; i++) {

            DeleteTask dt = deletes[i];
            if (dt == null)
                continue;

            if (dt.result == null) {
                Log.INFO("skipping no-result delete");
                continue;
            }
            if (!dt.result.pass) {
                Log.INFO("skipping failed delete");
                continue;
            }

            RetrieveTask rt = retrieves[i];

            if (rt.result == null) {
                Log.INFO("skipping no-result retrieve");
                continue;
            }
            if (rt.result.pass) {
                Log.INFO("retrieve succeeded - error");
                errors++;
                continue;
            }

            //
            //  retrieve failed (good) - check retrieve exceptions
            //
            if (dt.result.thrown != null) {
                boolean exprob = false;
                for (int j=0; j<dt.result.thrown.size(); j++) {
                    Throwable t = (Throwable) dt.result.thrown.get(j);
                    String msg = t.toString();
                    if (msg.indexOf("no active host available") != -1)
                        continue;
                    if (! (t instanceof NoSuchObjectException)) {
                        Log.ERROR("ok delete but bad exception: " + msg);
                        exprob = true;
                    }
                }
                if (exprob)
                    errors++;
            }
        }

        //
        //  get aggregate metric for retrieves
        //
        Statistic retrieve_stat = timeStat("retrieve times", retrieves);
        Log.INFO(retrieve_stat.toString());

        if (errors > 0) {
            return false;
        }

        //
        //  PASS
        //
        return true;
    }
}
