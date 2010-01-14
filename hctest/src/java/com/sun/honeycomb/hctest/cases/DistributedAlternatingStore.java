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
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.hctest.task.*;

import com.sun.honeycomb.client.*;

import java.util.HashMap;

public class DistributedAlternatingStore extends HoneycombRemoteSuite {
	private int numStores = 50;
	private boolean storeOnly = false;
	private TestCase self = null;
    public DistributedAlternatingStore() {
        super();
        
    }
    public DistributedAlternatingStore(int numstores, boolean storeonly) {
    	super();
    	numStores = numstores;
    	storeOnly = storeonly;
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
       	self = createTestCase("DistributedAlternatingStore",
       						  "DistributedSetup for DistributedAlternatingStore",
       						  false);	
       	self.addTag(Tag.DISTRIBUTED);
        if (self.excludeCase()) return;
        super.setUp();
    }
    





    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        if (excludeCase()) return;
        super.tearDown();
    }

    /**
     *  Store numStores * numclients in parallel.
     */
    public boolean testStressMultipleStoreRetrieve() {
    	if (excludeCase()) return false;
        int numFetches=2; // iterates over the entire store set
        int numClients = testBed.clientCount();
        long filesize = HoneycombTestConstants.DEFAULT_FILESIZE_SMALL;

        //System.out.println("startStores n=" + numClients*numStores + "  size=" + filesize);
        StoreTask[] stores = new StoreTask[numClients*numStores];
        for (int i=0; i<numClients*numStores; i++) {
            stores[i] = startStore(i%numClients, filesize,null);
        }
        
        try {
        	//System.out.println("Waiting for stores");
            waitForTasks(stores, 400*HoneycombTestConstants.SECOND);
        } catch(Exception e) {
            Log.ERROR("waiting for stores: " + e);
            return false;
        }
        
        if (storeOnly) {
        	return true;
        }

        //
        //  check results & collect oids
        //
        int errors = 0;
        String[] oids = new String[stores.length];
        for (int i=0; i<stores.length; i++) {
            StoreTask st = stores[i];
            if (st == null) {
                Log.ERROR("store: missing task " + i);
                return false;
            }
            if (st.thrown.size() > 0) {
                for (int j=0; j<st.thrown.size(); j++) {
                    Throwable t = (Throwable) st.thrown.get(j);
                    Log.ERROR("store: task exception [" + i + "] " + 
                                                        Log.stackTrace(t));
                }
                errors++;
                continue;
            }
            if (st.result == null) {
                Log.ERROR("store: missing result " + i);
                return false;
            }
            if (st.result == null  ||  st.result.mdoid == null  ||
                                    st.result.datasha1 == null) {
                Log.ERROR("store: missing result or mdoid or sha");
                return false;
            }
            oids[i] = st.result.mdoid;
        }
        if (errors > 0) {
            return false;
        }

        
        //
        //  launch retrieves and wait for results
        //

        for (int j=0;j<numFetches;j++) {
            RetrieveTask[] retrieves = new RetrieveTask[oids.length];
            int client = 0;
            for (int i=0; i<oids.length; i++) {
                retrieves[i] = startRetrieve(client%numClients, oids[i]);
                client++;
            }
            
            
            try {
                waitForTasks(retrieves, 40*HoneycombTestConstants.SECOND);
            } catch(Exception e) {
                Log.ERROR("waiting for retrieves: " + e);
                return false;
            }

            //
            //  check results against stores
            //
            for (int i=0; i<retrieves.length; i++) {
                RetrieveTask rt = retrieves[i];
                if (rt.result == null  ||  rt.result.datasha1 == null) {
                    Log.ERROR("retrieve: missing result or sha");
                    return false;
                }
                StoreTask st = stores[i];
                if (!st.result.datasha1.equals(rt.result.datasha1)) {
                    Log.ERROR("sha does not match");
                    return false;
                }
            }
        }

        //
        //  PASS
        //
        return true;
    }
}
