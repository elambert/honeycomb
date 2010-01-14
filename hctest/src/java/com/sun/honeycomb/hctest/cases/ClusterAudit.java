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

public class ClusterAudit extends HoneycombRemoteSuite {

    private String auditdb = null;
    private String auditip = null;
    private long timeout = 0;
    private int threadsPerClient = 10;

    private AuditDBClient ac;

    public ClusterAudit() {
        super();
    }

    public String help() {

        StringBuffer sb = new StringBuffer();

        sb.append("\tDistributed audit of the cluster.\n");
        sb.append("\tRequires a database of store/addmd/delete");
        sb.append(" operations created\n\tby tests using HoneycombTestClient.");
        sb.append(" Requires remote clients and\n");
        sb.append("\t-ctx ").append(HoneycombTestConstants.PROPERTY_AUDIT_DB);
        sb.append(" pointing to the database for the cluster and\n");
        sb.append("\t").append(HoneycombTestConstants.PROPERTY_AUDIT_IP);
        sb.append(" pointing to the database host.\n");
        sb.append("\tDefault timeout is ");
        if (timeout == 0)
            sb.append("never");
        else
            sb.append(Long.toString(timeout)).append(" msec");
        sb.append(", and default ");
        sb.append(HoneycombTestConstants.PROPERTY_CLIENTS_PER_HOST);
        sb.append(" is ").append(Integer.toString(threadsPerClient)).append(",");
        sb.append("\n\tsettable by -ctx.\n");

        return sb.toString();
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        TestCase self = createTestCase("DistributedSetup for ClusterAudit");
        self.addTag(Tag.DISTRIBUTED);
        if (self.excludeCase()) 
            return;

        requiredProps.add(HoneycombTestConstants.PROPERTY_AUDIT_DB);
        requiredProps.add(HoneycombTestConstants.PROPERTY_AUDIT_IP);
        super.setUp();

        auditdb = getProperty(HoneycombTestConstants.PROPERTY_AUDIT_DB);
        auditip = getProperty(HoneycombTestConstants.PROPERTY_AUDIT_IP);

        ac = new AuditDBClient(auditip, auditdb);
        ac.queryObjects(false);

        String s = getProperty(HoneycombTestConstants.PROPERTY_TIMEOUT);
        if (s != null) {
            timeout = HCUtil.parseTime(s);
            if (timeout < 0)
                throw new HoneycombTestException("timeout < 0");
        }
        s = getProperty(HoneycombTestConstants.PROPERTY_CLIENTS_PER_HOST);
        if (s != null) {
            threadsPerClient = Integer.parseInt(s);
            if (threadsPerClient <=0)
                throw new HoneycombTestException(
                            HoneycombTestConstants.PROPERTY_CLIENTS_PER_HOST +
                            " < 1");
        }

        Log.INFO("setup done, auditdb=" + auditdb + "  timeout=" + timeout +
                 "  clientsperhost=" + threadsPerClient);
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /**
     *  Retrieve in parallel across all clients.
     */
    public boolean testClusterAudit() {
        addTag(Tag.DISTRIBUTED);
        if (excludeCase()) 
            return false;

        //
        //  start audit retrieves in parallel across all clients
        //  and wait for results
        //
        ClientAuditThread[] threads;
        try {
            threads = startAuditThreads(ac, threadsPerClient);
            waitForTasks(threads, timeout);
        } catch(Exception e) {
            Log.ERROR("waiting for threads: " + e);
            return false;
        }

        //
        //  check results
        //
        int entries = 0;
        int skipped = 0;
        int add_md = 0;
        long data_retrieved = 0;
        int retrieve_failures = 0;
        int bad_data = 0;
        int delete_failures = 0;
        int ok_retrieves = 0;
        int ok_deletes = 0;
        int suspect_deletes = 0;
        int no_host_errors = 0;
        int unexpected_deletes = 0;

        for (int i=0; i<threads.length; i++) {

            ClientAuditThread cat = threads[i];

            entries += cat.entries;
            skipped += cat.skipped;
            data_retrieved += cat.data_retrieved;
            retrieve_failures += cat.retrieve_failures;
            bad_data += cat.bad_data;
            delete_failures += cat.delete_failures;
            ok_retrieves += cat.ok_retrieves;
            ok_deletes += cat.ok_deletes;
            suspect_deletes += cat.suspect_deletes;
            no_host_errors += cat.no_host_errors;
            unexpected_deletes += cat.unexpected_deletes;
        }

        Log.SUM("Total entries: " + entries);
        Log.SUM("Entries skipped: " + skipped);
        Log.SUM("Addmd skipped: " + add_md);
        Log.SUM("Total data retrieved: " + data_retrieved);
        Log.SUM("Total retrieve_failures: " + retrieve_failures);
        Log.SUM("Total unexpected_deletes: " + unexpected_deletes);
        Log.SUM("Total bad_data: " + bad_data);
        Log.SUM("Total delete_failures: " + delete_failures);
        Log.SUM("Total ok_retrieves: " + ok_retrieves);
        Log.SUM("Total ok_deletes: " + ok_deletes);
        Log.SUM("Total suspect_deletes: " + suspect_deletes);
        Log.SUM("Total no-host errors: " + no_host_errors);

        if (retrieve_failures + unexpected_deletes + bad_data + 
                delete_failures + no_host_errors > 0)
            return false;

        //
        //  PASS
        //
        return true;
    }
}
