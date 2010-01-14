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

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.hctest.cli.Hiveadm;

import com.sun.honeycomb.client.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Validate that queries can be done across multicell with interesting
 * cases of oids-per-cell. First cousin of MetadataCookies test.
 */
public class MulticellQuery extends HoneycombSuite {

    private static final int MAX_OIDS_PER_CELL = 15;

    private boolean setupOK = false;
    private Hiveadm hiveadm = null;
    private NVOAX nvoax = null;

    private void addTags(TestCase tc) {
        if (tc == null) {
            addTag(Tag.REGRESSION);
            addTag(Tag.POSITIVE);
            addTag(Tag.QUICK);
            addTag(Tag.SMOKE);
            addTag(HoneycombTag.QUERY);
            addTag(HoneycombTag.JAVA_API);
            addTag(HoneycombTag.EMULATOR);
        } else {
            tc.addTag(Tag.REGRESSION);
            tc.addTag(Tag.POSITIVE);
            tc.addTag(Tag.QUICK);
            tc.addTag(Tag.SMOKE);
            tc.addTag(HoneycombTag.QUERY);
            tc.addTag(HoneycombTag.JAVA_API);
            tc.addTag(HoneycombTag.EMULATOR);
        }
    }

    public MulticellQuery() {
        super();
    }

    public String help() {
        return(
            "\tTest queries on mcell with interesting distrib of oids.\n" +
            "\t\tFiles are 4 bytes.\n"
            );
    }

    /**
     * See if it's a multicell & skip if not.
     */
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();

        hiveadm = new Hiveadm(testBed.adminVIPaddr);
        if (hiveadm.cells.size() > 1) {
            Log.INFO("Cell ids = " + hiveadm.listIDs());
            nvoax = new NVOAX(testBed.dataVIPaddr);
            setupOK = true;
        } else
            Log.INFO("Not Multicell, skipping tests");
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /**
     * Do query across all cells having MAX_OIDS_PER_CELL varying results/fetch.
     */
    public void testQueryMaxOidsOnAllCells() throws Throwable {
        TestCase self = createTestCase("MulticellQuery", 
                                       "QueryMaxOidsOnAllCells");
        addTags(self);
        if (!setupOK)
            self.addTag(Tag.MISSINGDEP, "Dependency failed");
        if (self.excludeCase())
            return;

        long key = System.currentTimeMillis();

        String query = "system.test.type_long=" + key;

        Log.INFO("Storing " + MAX_OIDS_PER_CELL + " on each cell..");

        // set up data and metadata
        byte bytes[] = { 0, 1, 2, 3 };
        NameValueRecord nvr = nvoax.createRecord();
        nvr.put("system.test.type_long", new Long(key));
        nvr.put("system.test.type_string", "MulticellQuery.java all");

        HashSet oids = new HashSet();
        String firstOids[] = new String[hiveadm.cells.size()];

        for (int i=0; i<hiveadm.cells.size(); i++) {
            Hiveadm.Cell c = (Hiveadm.Cell) hiveadm.cells.get(i);
            for (int j=0; j<MAX_OIDS_PER_CELL; j++) {
                HCTestReadableByteChannel bc = new HCTestReadableByteChannel(
                                                   bytes, 1);
                SystemRecord sr = nvoax.storeObject(c.cellid, bc, nvr);
                String oid = sr.getObjectIdentifier().toString();
                oids.add(oid);
                if (j == 0)
                    firstOids[i] = oid;
            }
        }
        for (int i=0; i<hiveadm.cells.size(); i++) {
            Hiveadm.Cell c = (Hiveadm.Cell) hiveadm.cells.get(i);
            Log.INFO("Cell " + c.cellid + 
                             " (hex " + Integer.toHexString(c.cellid) + ") " +
                             "first oid: " + firstOids[i]);
        }

        Log.INFO("Running query " + query);

        for (int nPerFetch=1; nPerFetch<MAX_OIDS_PER_CELL+2; nPerFetch++) {
            int nTotalResults = 0;
            HashSet gotten = new HashSet();

            Log.INFO("resultsPerFetch = " + nPerFetch);
            QueryResultSet qrs = nvoax.query(query, nPerFetch);
            while (qrs.next()) {
                String result = qrs.getObjectIdentifier().toString();
                Log.DEBUG("Found result " + result);
                if (!oids.contains(result)) {
                    self.testFailed("oid " + result + 
                                  ", should not be " +
                                  "returned by this query");
                    return;
                }
                gotten.add(result);
                nTotalResults++;
                if (nTotalResults > MAX_OIDS_PER_CELL * hiveadm.cells.size()) {
                    self.testFailed("Too many results returned");
                    return;
                }
            }
            // Did we get the right number of results
            if (nTotalResults != MAX_OIDS_PER_CELL * hiveadm.cells.size()) {
                self.testFailed("Query did not get exactly " + 
                        (MAX_OIDS_PER_CELL * hiveadm.cells.size()) +
                        " objects that we stored.  Found " + nTotalResults);
                return;
            }
            if (gotten.size() != MAX_OIDS_PER_CELL * hiveadm.cells.size()) {
                self.testFailed("Query got duplicate results, total unique " +
                              gotten.size() + " expected " + 
                              MAX_OIDS_PER_CELL * hiveadm.cells.size());
                return;
            }
        } 
        self.testPassed("ok");
    }

    /**
     * Do query across odd cells having MAX_OIDS_PER_CELL varying results/fetch.
     */
    public void testQueryMaxOidsOnOddCells() throws Throwable {
        TestCase self = createTestCase("MulticellQuery", 
                                       "QueryMaxOidsOnOddCells");
        addTags(self);
        if (!setupOK)
            self.addTag(Tag.MISSINGDEP, "Dependency failed");
        if (self.excludeCase())
            return;

        long key = System.currentTimeMillis();
        String query = "system.test.type_long=" + key;

        Log.INFO("Storing " + MAX_OIDS_PER_CELL + " on odd cells..");

        // set up data and metadata
        byte bytes[] = { 0, 1, 2, 3 };
        NameValueRecord nvr = nvoax.createRecord();
        nvr.put("system.test.type_long", new Long(key));
        nvr.put("system.test.type_string", "MulticellQuery.java odd");

        HashSet oids = new HashSet();
        int nStores = 0;

        for (int i=0; i<hiveadm.cells.size(); i++) {
            if (i % 2 == 0)
                continue;

            Hiveadm.Cell c = (Hiveadm.Cell) hiveadm.cells.get(i);
            for (int j=0; j<MAX_OIDS_PER_CELL; j++) {
                HCTestReadableByteChannel bc = new HCTestReadableByteChannel(
                                                   bytes, 1);
                SystemRecord sr = nvoax.storeObject(c.cellid, bc, nvr);
                String oid = sr.getObjectIdentifier().toString();
                oids.add(oid);
                nStores++;
            }
        }

        Log.INFO("Running query " + query);

        for (int nPerFetch=1; nPerFetch<MAX_OIDS_PER_CELL+2; nPerFetch++) {
            int nTotalResults = 0;
            HashSet gotten = new HashSet();

            Log.INFO("resultsPerFetch = " + nPerFetch);
            QueryResultSet qrs = nvoax.query(query, nPerFetch);
            while (qrs.next()) {
                String result = qrs.getObjectIdentifier().toString();
                Log.DEBUG("Found result " + result);
                if (!oids.contains(result)) {
                    self.testFailed("oid " + result + 
                                  ", should not be " +
                                  "returned by this query");
                    return;
                }
                gotten.add(result);
                nTotalResults++;
                if (nTotalResults > nStores) {
                    self.testFailed("Too many results returned");
                    return;
                }
            }
            // Did we get the right number of results
            if (nTotalResults != nStores) {
                self.testFailed("Query did not get exactly " + nStores +
                        " objects that we stored.  Found " + nTotalResults);
                return;
            }
            if (gotten.size() != nStores) {
                self.testFailed("Query got duplicate results, total unique " +
                              gotten.size() + " expected " + nStores);
                return;
            }

        } 
        self.testPassed("ok");
    }

    /**
     * Do query across even cells having MAX_OIDS_PER_CELL, vary results/fetch.
     */
    public void testQueryMaxOidsOnEvenCells() throws Throwable {
        TestCase self = createTestCase("MulticellQuery", 
                                       "QueryMaxOidsOnEvenCells");
        addTags(self);
        if (!setupOK)
            self.addTag(Tag.MISSINGDEP, "Dependency failed");
        if (self.excludeCase())
            return;

        long key = System.currentTimeMillis();
        String query = "system.test.type_long=" + key;

        Log.INFO("Storing " + MAX_OIDS_PER_CELL + " on even cells..");

        // set up data and metadata
        byte bytes[] = { 0, 1, 2, 3 };
        NameValueRecord nvr = nvoax.createRecord();
        nvr.put("system.test.type_long", new Long(key));
        nvr.put("system.test.type_string", "MulticellQuery.java even");

        HashSet oids = new HashSet();
        int nStores = 0;

        for (int i=0; i<hiveadm.cells.size(); i++) {
            if (i % 2 != 0)
                continue;

            Hiveadm.Cell c = (Hiveadm.Cell) hiveadm.cells.get(i);
            for (int j=0; j<MAX_OIDS_PER_CELL; j++) {
                HCTestReadableByteChannel bc = new HCTestReadableByteChannel(
                                                   bytes, 1);
                SystemRecord sr = nvoax.storeObject(c.cellid, bc, nvr);
                String oid = sr.getObjectIdentifier().toString();
                oids.add(oid);
                nStores++;
            }
        }

        Log.INFO("Running query " + query);

        for (int nPerFetch=1; nPerFetch<MAX_OIDS_PER_CELL+2; nPerFetch++) {
            int nTotalResults = 0;
            HashSet gotten = new HashSet();

            Log.INFO("resultsPerFetch = " + nPerFetch);
            QueryResultSet qrs = nvoax.query(query, nPerFetch);
            while (qrs.next()) {
                String result = qrs.getObjectIdentifier().toString();
                Log.DEBUG("Found result " + result);
                if (!oids.contains(result)) {
                    self.testFailed("oid " + result + 
                                  ", should not be " +
                                  "returned by this query");
                    return;
                }
                gotten.add(result);
                nTotalResults++;
                if (nTotalResults > nStores) {
                    self.testFailed("Too many results returned");
                    return;
                }
            }
            // Did we get the right number of results
            if (nTotalResults != nStores) {
                self.testFailed("Query did not get exactly " + nStores +
                        " objects that we stored.  Found " + nTotalResults);
                return;
            }
            if (gotten.size() != nStores) {
                self.testFailed("Query got duplicate results, total unique " +
                              gotten.size() + " expected " + nStores);
                return;
            }

        } 
        self.testPassed("ok");
    }
}
