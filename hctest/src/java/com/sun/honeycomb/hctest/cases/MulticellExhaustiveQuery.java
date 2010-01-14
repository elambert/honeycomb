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
 * Validate that queries can be done across multicell with exhaustive
 * cases of oids-per-cell. First cousin of MetadataCookies and 
 * MetadataQuery tests.
 */
public class MulticellExhaustiveQuery extends HoneycombSuite {

    private static final int DEFAULT_MAX_OIDS_PER_CELL = 5;
    private int max_oids_per_cell = DEFAULT_MAX_OIDS_PER_CELL;

    private boolean setupOK = false;
    private Hiveadm hiveadm = null;
    private NVOAX nvoax = null;
    private int endLevel;
    private int counts[] = null;
    private boolean pass = true;

    private void addTags(TestCase tc) {
        if (tc == null) {
            addTag(Tag.POSITIVE);
            addTag(HoneycombTag.QUERY);
            addTag(HoneycombTag.JAVA_API);
            addTag(HoneycombTag.EMULATOR);
        } else {
            tc.addTag(Tag.POSITIVE);
            tc.addTag(HoneycombTag.QUERY);
            tc.addTag(HoneycombTag.JAVA_API);
            tc.addTag(HoneycombTag.EMULATOR);
        }
    }

    public MulticellExhaustiveQuery() {
        super();
    }

    public String help() {
        return(
            "\tTest queries on mcell with complete distrib of oids.\n" +
            "\t\tFiles are 4 bytes. Set " + 
            HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS + 
            "to set max objs/cell\n" +
            "\t\tDefault is " + DEFAULT_MAX_OIDS_PER_CELL + "\n"
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
            counts = new int[hiveadm.cells.size()];
            endLevel = hiveadm.cells.size() - 1;
        } else
            Log.INFO("Not Multicell, skipping test");
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /**
     * Do query across all cells varying results/fetch.
     */
    public void testQuery() throws Throwable {

        TestCase self = createTestCase("MulticellQuery", 
                                       "Exhaustive");
        addTags(self);
        if (!setupOK)
            self.addTag(Tag.MISSINGDEP, "Dependency failed");
        if (self.excludeCase())
            return;

        String s = getProperty(HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS);
        if (s != null) {
            max_oids_per_cell = Integer.parseInt(s);
        }
        Log.INFO("Storing all permutations of 0.." + max_oids_per_cell +
                 " on each cell..");

        recurse(0);

        if (pass)
            self.testPassed("all passed");
        else
            self.testFailed("something went wrong");
    }

    private void recurse(int level) {

        Hiveadm.Cell c = (Hiveadm.Cell) hiveadm.cells.get(level);

        for (int i=0; i<max_oids_per_cell+1; i++) {

            counts[level] = i;

            if (level == endLevel)
                doIt();
            else
                recurse(level+1);
        }
    }

    private void doIt() {

        StringBuffer sb = new StringBuffer();
        for (int i=0; i<hiveadm.cells.size(); i++) {
            if (i>0)
                sb.append(",");
            sb.append(counts[i]);
        }
        String permu = sb.toString();

        // set up metadata, query and oid tracking

        long key = System.currentTimeMillis();
        NameValueRecord nvr = nvoax.createRecord();
        nvr.put("system.test.type_long", new Long(key));
        nvr.put("system.test.type_string", "MulticellExhaustiveQuery.java");

        String query = "system.test.type_long=" + key;

        HashSet oids = new HashSet();

        Log.INFO("Storing [" + permu + "]..");

        int maxPerCell = 0;

        try {
            byte bytes[] = { 0, 1, 2, 3 };
            for (int i=0; i<hiveadm.cells.size(); i++) {
                if (counts[i] > maxPerCell)
                    maxPerCell = counts[i];
                Hiveadm.Cell c = (Hiveadm.Cell) hiveadm.cells.get(i);
                for (int j=0; j<counts[i]; j++) {
                    HCTestReadableByteChannel bc = 
                                 new HCTestReadableByteChannel(bytes, 1);
                    SystemRecord sr = nvoax.storeObject(c.cellid, bc, nvr);
                    String oid = sr.getObjectIdentifier().toString();
                    oids.add(oid);
                }
            }
            if (oids.size() == 0)
                return;

            TestCase self = createTestCase("MulticellExhaustiveQuery", permu);
            Log.INFO("Running query " + query);
            int maxFetch = maxPerCell + 1;
            for (int nPerFetch=1; nPerFetch<maxFetch; nPerFetch++) {
                int nTotalResults = 0;
                HashSet gotten = new HashSet();

                //Log.INFO("resultsPerFetch = " + nPerFetch);
                QueryResultSet qrs = nvoax.query(query, nPerFetch);
                while (qrs.next()) {
                    String result = qrs.getObjectIdentifier().toString();
                    Log.DEBUG("Found result " + result);
                    if (!oids.contains(result)) {
                        self.testFailed("oid " + result + 
                                      ", should not be " +
                                      "returned by this query");
                        pass = false;
                        return;
                    }
                    gotten.add(result);
                    nTotalResults++;
                    if (nTotalResults > oids.size()) {
                        self.testFailed("Too many results returned");
                        pass = false;
                        return;
                    }
                }
                // Did we get the right number of results
                if (nTotalResults != oids.size()) {
                    self.testFailed("Query did not get exactly " + oids.size() +
                            " objects that we stored.  Found " + nTotalResults);
                    pass = false;
                    return;
                }
                if (gotten.size() != oids.size()) {
                    self.testFailed("Query got duplicate results, total unique " +
                                  gotten.size() + " expected " + oids.size());
                    pass = false;
                    return;
                }
            } 
            self.testPassed("ok");

        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }
}
