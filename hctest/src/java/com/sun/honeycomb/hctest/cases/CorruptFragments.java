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

/**
 *  Validate that fragment recovery/repair work.
 */

public class CorruptFragments extends HoneycombRemoteSuite {

    private ArrayList sizes;
    private StoreTask[] stores;
    private CmdResult[] storeResults;
    //private CmdResult[] retrieveResults;
    private HOidInfo[]  oidInfo;

    ArrayList delFrags = new ArrayList();
    ArrayList delFrags2 = new ArrayList();
    ArrayList delFrags3 = new ArrayList();

    private long timeout = 0;

    public CorruptFragments() {
        super();
    }

    public String help() {

        StringBuffer sb = new StringBuffer();
        sb.append("\tTest deletion of M and M+1 fragments on");
        sb.append(" metadata and data chunks\n");
        sb.append("\tof files of different sizes. Requires these -ctx args:\n");
        sb.append("\t\t").append(HoneycombTestConstants.PROPERTY_FILESIZE).append("\n");
        sb.append("\t\t").append(HoneycombTestConstants.PROPERTY_SP_IP).append("\n");
        sb.append("\tand for the SP and Node RMI servers to be running. Since\n");
        sb.append("\tcacheing cannot be disabled, new files are stored for\n");
        sb.append("\teach deletion variant.\n");

        return sb.toString();
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        TestCase self = createTestCase("CorruptFragments");
        self.addTag(Tag.WHITEBOX);
        self.addTag(Tag.DISTRIBUTED);
        if (self.excludeCase()) 
            return;

        requiredProps.add(HoneycombTestConstants.PROPERTY_FILESIZE);
        requiredProps.add(HoneycombTestConstants.PROPERTY_SP_IP);

        super.setUp();

        sizes = getFilesizeList();
        if (sizes == null  ||  sizes.size() == 0) {
            throw new HoneycombTestException("no " + 
                        HoneycombTestConstants.PROPERTY_FILESIZE + " defined");
        }

        storeResults = new CmdResult[sizes.size()];
        //retrieveResults = new CmdResult[sizes.size()];
        oidInfo = new HOidInfo[sizes.size()];
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    private void store() throws HoneycombTestException {

        //
        //  store files and get fragment info on them
        //
        Log.INFO("storing");
        stores = startStores(sizes);
        try {
            waitForTasks(stores, timeout);
        } catch(HoneycombTestException e) {
            Log.ERROR("waiting for stores: " + e);
            throw e;
        }

        for (int i=0; i<stores.length; i++) {
            CmdResult cr = stores[i].result;
            if (cr == null) {
                throw new HoneycombTestException("store failed " + 
                                                       stores[i].thrown);
            }
            storeResults[i] = cr;
            if (!cr.pass) {
                throw new HoneycombTestException("store failed " + cr.thrown);
            }
        }

        Log.INFO("getting fragment info");
        for (int i=0; i<stores.length; i++) {
            oidInfo[i] = getOidInfo(storeResults[i].mdoid, false);
        }

        //
        //  collect 1st & last md & data frags for each stored file
        //  into a master list of frags to delete 
        //  collect a 3rd data & md frag for each file for another
        //  round of deletion 
        //  XXX correctness of test depends on M=2
        //
        Log.INFO("decide frags to delete");

        delFrags.clear();
        delFrags2.clear();
        delFrags3.clear();

        for (int i=0; i<oidInfo.length; i++) {

            //
            //  get md frags
            //
            HOidInfo md_hoi = oidInfo[i];
            //System.out.println(md_hoi);

            if (md_hoi.missingFrags) {
                Log.ERROR("frags already missing from " + md_hoi.oid);
                throw new HoneycombTestException("frags already missing from " +
                                                 md_hoi.oid);
            }
            if (md_hoi.other == null) {
                Log.ERROR("md oid lacks data oid: " + md_hoi.oid);
                throw new HoneycombTestException("md oid lacks data oid: " + 
                                                 md_hoi.oid);
            }           
            if (md_hoi.chunks == null) {
                Log.ERROR("test impl error: no chunks in " + md_hoi.oid);
                throw new HoneycombTestException(
                               "test impl error: no chunks in " + md_hoi.oid);
            }
            HChunkInfo ci = (HChunkInfo) md_hoi.chunks.get(0);
            String hashPath = ci.getHashPath();
            ArrayList frags = ci.getFragments();
            //
            //  for all rounds
            //
            HFragmentInfo hf = (HFragmentInfo) frags.get(0);
            delFrags.add(hf.getPath(hashPath));
            delFrags2.add(hf.getPath(hashPath));
            delFrags3.add(hf.getPath(hashPath));
            hf = (HFragmentInfo) frags.get(frags.size()-1);
            delFrags.add(hf.getPath(hashPath));
            delFrags2.add(hf.getPath(hashPath));
            delFrags3.add(hf.getPath(hashPath));

            //
            //  for 3rd round
            //
            hf = (HFragmentInfo) frags.get(1);
            delFrags3.add(hf.getPath(hashPath));

            //
            //  get data frags
            //
            HOidInfo data_hoi = md_hoi.other;
            if (data_hoi.chunks == null) {
                Log.ERROR("test impl error: no data chunks in " + md_hoi.oid);
                throw new HoneycombTestException(
                            "test impl error: no data chunks in " + md_hoi.oid);
            }
            for (int j=0; j<data_hoi.chunks.size(); j++) {
                ci = (HChunkInfo) data_hoi.chunks.get(j);
                hashPath = ci.getHashPath();
                frags = ci.getFragments();
                //
                //  for all rounds
                //
                hf = (HFragmentInfo) frags.get(0);
                delFrags.add(hf.getPath(hashPath));
                delFrags2.add(hf.getPath(hashPath));
                //delFrags3.add(hf.getPath(hashPath));
                hf = (HFragmentInfo) frags.get(frags.size()-1);
                delFrags.add(hf.getPath(hashPath));
                delFrags2.add(hf.getPath(hashPath));
                //delFrags3.add(hf.getPath(hashPath));
                //
                //  for 2nd & 3rd round
                //
                hf = (HFragmentInfo) frags.get(1);
                delFrags2.add(hf.getPath(hashPath));
                //delFrags3.add(hf.getPath(hashPath));
            }
        }
        Log.INFO("store/get info done");
        //System.out.println(delFrags);
    }

    /**
     *  Verify that all retrieves failed.
     */
    private void checkRetrievesFail(RetrieveTask[] retrieves) 
                                                throws HoneycombTestException {
        for (int i=0; i<retrieves.length; i++) {
            CmdResult cr = retrieves[i].result;
            if (cr == null) {
                Log.INFO("2nd retrieve failed as expected: " + 
                                                     retrieves[i].thrown);
            } else {
                if (cr.pass) {
                    throw new HoneycombTestException("retrieve succeeded");
                }
                Log.INFO("2nd retrieve failed as expected: " + cr.thrown);
            }
        }
    }

    /**
     *  Verify that retrieve works after deleting 1st & last md fragment
     *  and 1st/last frag of each data chunk, then test that it fails
     *  when deleting another fragment. Separate groups of files are
     *  stored for each test since cacheing prevents reuse of files
     *  once they have been retrieved (file will be returned from
     *  cache without regard to frags on disk).
     */
    public boolean testDeleteFragments() throws HoneycombTestException {

        addTag(Tag.WHITEBOX);
        addTag(Tag.DISTRIBUTED);
        if (excludeCase()) 
            return false;

        // prepare
        store();

        //
        //
        //  delete the 1st-round frags
        //
        Log.INFO("deleting fragments");
        deleteFragments(delFrags);

        //
        //  retrieve the files
        //
        Log.INFO("retrieving");
        RetrieveTask[] retrieves = startRetrieves(stores, false);
        try {
            waitForTasks(retrieves, timeout);
        } catch(Exception e) {
            Log.ERROR("waiting for retrieves: " + e);
            return false;
        }
        for (int i=0; i<retrieves.length; i++) {
            CmdResult cr = retrieves[i].result;
            if (!cr.pass) {
                throw new HoneycombTestException("retrieve failed " +cr.thrown);
            }
        }
        Log.INFO("1st round ok");

        //
        //  delete a 3rd frag from 1st data chunk and verify that
        //  retrieve fails
        //
        store();
        Log.INFO("deleting 2nd round of fragments");
        deleteFragments(delFrags2);

        Log.INFO("retrieving");
        retrieves = startRetrieves(stores, false);
        try {
            waitForTasks(retrieves, timeout);
        } catch(Exception e) {
            Log.ERROR("waiting for retrieves: " + e);
            return false;
        }

        checkRetrievesFail(retrieves);

        Log.INFO("2nd round ok");

        //
        //  delete a 3rd frag from md and verify that
        //  retrieve fails (just to see if the error
        //  is different from the missing data frag)
        //
        store();
        Log.INFO("deleting 3rd round of fragments");
        deleteFragments(delFrags3);

        Log.INFO("retrieving");
        retrieves = startRetrieves(stores, false);
        try {
            waitForTasks(retrieves, timeout);
        } catch(Exception e) {
            Log.ERROR("waiting for retrieves: " + e);
            return false;
        }

        checkRetrievesFail(retrieves);

        Log.INFO("3rd round ok");

        return true;
    }
}
