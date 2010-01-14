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

import com.sun.honeycomb.client.*;
import com.sun.honeycomb.common.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * Validate that we can add metadata as complex chains.
 */
public class MetadataComplexChains extends HoneycombLocalSuite {

    private static  int TOTAL_NB_OBJS = 10;
    private static  int PERCENT_MD = 95;

    private long sleeptime = 0;
    private boolean refcheck = true;
    private boolean verbose = false; // log ops to cluster log
    private String oidArg = null; // use this oid for all addMD ops
    private boolean noretrieve = false; // retrieve after each delete op

    private ArrayList completedObjs;
    private ArrayList storedObjs;

    public MetadataComplexChains() {
        super();
        completedObjs = new ArrayList();
        storedObjs = new ArrayList();
    }

    private void addTags(TestCase tc) {
        if (tc == null) {
            addTag(Tag.REGRESSION);
            addTag(Tag.POSITIVE);
            addTag(HoneycombTag.STOREMETADATA);
            addTag(HoneycombTag.QUERY);
            addTag(HoneycombTag.JAVA_API);
        } else {
            tc.addTag(Tag.REGRESSION);
            tc.addTag(Tag.POSITIVE);
            tc.addTag(HoneycombTag.STOREMETADATA);
            tc.addTag(HoneycombTag.QUERY);
            tc.addTag(HoneycombTag.JAVA_API);
        }
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();

        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);

        // Set the seed for this test to make the same test run repeatable
        if (seed == HoneycombTestConstants.INVALID_SEED) {
            seed = System.currentTimeMillis();
        }

        String s = getProperty(HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS);
        if (s != null) {
            Log.INFO("Property " +
                     HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS +
                     " was specified. Using " + s + " " +
                     HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS);
            TOTAL_NB_OBJS = Integer.parseInt(s);
        } else {
            Log.INFO("using default value for property " +
                     HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS +
                     " of " + TOTAL_NB_OBJS);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_PERCENT_MD);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_PERCENT_MD +
                     " was specified. Using " + s + " as " +
                     HoneycombTestConstants.PROPERTY_PERCENT_MD);
            PERCENT_MD = Integer.parseInt(s);
            if (PERCENT_MD > 99 || PERCENT_MD < 0) {
                throw new HoneycombTestException("invalid value of " +
                                                 PERCENT_MD + " for " +
                               HoneycombTestConstants.PROPERTY_PERCENT_MD);
            }
        } else {
            Log.INFO("using default value for property " +
                     HoneycombTestConstants.PROPERTY_PERCENT_MD +
                     " of " + PERCENT_MD);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_SLEEP_TIME);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_SLEEP_TIME +
                     " was specified. Using " + s + " as " +
                     HoneycombTestConstants.PROPERTY_SLEEP_TIME);
            sleeptime = Long.parseLong(s);
        } else {
            Log.INFO("using default value for property " +
                     HoneycombTestConstants.PROPERTY_SLEEP_TIME +
                     " of " + sleeptime);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_VERBOSE);
        if (s != null) {
            verbose = Boolean.parseBoolean(s);
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_VERBOSE +
                     " was specified. Using " + verbose + " as " +
                     HoneycombTestConstants.PROPERTY_VERBOSE);
        } else {
            Log.INFO("using default value for property " +
                     HoneycombTestConstants.PROPERTY_VERBOSE + " of " +
                     verbose);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_REFCHECK);
        if (s != null) {
            refcheck = Boolean.parseBoolean(s);
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_REFCHECK +
                     " was specified. Using " + refcheck + " as " +
                     HoneycombTestConstants.PROPERTY_REFCHECK);
        } else {
            Log.INFO("using default value for property " +
                     HoneycombTestConstants.PROPERTY_REFCHECK + " of " +
                     refcheck);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_OID);
        if (s != null) {
            oidArg = s;
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_OID +
                     " was specified. Using " + oidArg + " as " +
                     HoneycombTestConstants.PROPERTY_OID);
        } else {
            Log.INFO("using default value for property " +
                     HoneycombTestConstants.PROPERTY_OID + " of " +
                     oidArg);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_NO_RETRIEVE);
        if (s != null) {
            noretrieve = Boolean.parseBoolean(s);
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_NO_RETRIEVE +
                     " was specified. Using " + noretrieve + " as " +
                     HoneycombTestConstants.PROPERTY_NO_RETRIEVE);
        } else {
            Log.INFO("using default value for property " +
                     HoneycombTestConstants.PROPERTY_NO_RETRIEVE + " of " +
                     noretrieve);
        }
        
        if (testBed.rmiEnabled() && refcheck) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_RMI +
                     " was specified and property " +
                     HoneycombTestConstants.PROPERTY_REFCHECK + " is " +
                     refcheck + ". Will validate ref counts on cluster." +
                     " RMI servers must be started for this to work");
        } else {
            Log.INFO("To do refcount validation, property " +
                     HoneycombTestConstants.PROPERTY_RMI +
                     " must have value yes " +
                     "and property " +
                     HoneycombTestConstants.PROPERTY_REFCHECK + 
                     " must have value " +
                     true);
        }
        setThrowAPIExceptions(false);
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /**
     * At the highest level, we either store or add MD to an exisiting object.
     * If we add MD, we arbitrarily pick which MD oid we should add to within
     * the given object.  The exception to this is if an oid has been given
     * to us.  In that case, we only add to that oid.
     */
    private void storeComplexChains(TestCase self)
        throws HoneycombTestException {

        Random r = new Random(seed);
        int nextRandom;
        int objsSoFar = 0;

        // If we were passed the oid to use, add it as our 'stored' object.
        // It will be the only stored object, and all MD we add will point
        // to this object.
        if (oidArg != null) {
            Log.INFO("Using oid " + oidArg + " as the only stored object");
            storedObjs.add(new StoredObject(oidArg, (String)null, objsSoFar++));
        }

        while (objsSoFar < TOTAL_NB_OBJS) {
            nextRandom = r.nextInt(100);

            if (((nextRandom % 100) + 1) > PERCENT_MD && oidArg == null) {

                Log.DEBUG("Attempting to store a file of size " +
                          getFilesize());

                CmdResult cr = storeAsStream(getFilesize(), null);
                if (!cr.pass) {
                    cr.logExceptions("store");
                    self.testFailed("store failed unexpectedly");
                    return;
                }

                StoredObject storedObj = new StoredObject(cr.mdoid, cr.dataoid,
                                                          objsSoFar++);
                storedObjs.add(storedObj);

                Log.INFO("[" + storedObj.origObjectNumber +
                         "] Stored file of size " + getFilesize() +
                         " as " + storedObj + "; #StoredObjs=" + 
                         storedObjs.size());

            } else {
                // addMD to an existing object if we have one
                if (storedObjs.isEmpty()) {
                    Log.INFO("No stored objects currently...continuing");
                    continue;
                }

                int index = nextRandom % storedObjs.size();
                StoredObject storedObj = (StoredObject)storedObjs.get(index);

                try {
                    boolean success = storedObj.addMD(objsSoFar, nextRandom);
                    if (!success) {
                        storedObjs.remove(index);
                        self.testFailed("store MD failed unexpectedly");
                        return;
                    } else {
                        objsSoFar++;
                        //
                        // We reached maximum number of metadata per objects,
                        // remove it from the list unless we are only working
                        // on an oid we were given on the cmdline.
                        //
                        if (storedObj.activeOids.size() == 
                            MdGenerator.MAX_MD_PER_OBJECTS && oidArg == null) {
                            storedObjs.remove(index);
                            completedObjs.add(storedObj);
                        }
                    }
                } catch (HoneycombTestException hte) {
                    self.addBug("6293141", 
                            "old bug fixed in balboa: addMetadata doesn't " +
                            "increment all chunks in the referee for multichunk files");
                    self.testFailed("addMD failed: " + hte);
                    return;
                }

            }
            Log.DEBUG("sleeping " + sleeptime);
            sleep(sleeptime);
        }
        self.testPassed();
    }

    /**
     *
     * Now we check for each object that we have the right number
     * of query results.  The boolean indicates if the delete of the
     * objects has been done, so we know how many results to expect (0 or all).
     *
     */
    private void queryComplexChains(TestCase self, boolean objsDeleted) 
        throws HoneycombTestException {

        Log.INFO("queryComplexChains: storedObjs.size()=" +
            storedObjs.size() + ", completedObjs.size()=" + 
            completedObjs.size());

        for (int i = 0; i < storedObjs.size() + completedObjs.size(); i++) {

            StoredObject obj = null;
            if (i < storedObjs.size()) {
                obj = (StoredObject) storedObjs.get(i);
            } else {
                obj = (StoredObject) completedObjs.get(i - storedObjs.size());
            }

            // Try to make queries only find things from this test run.
            // Not guaranteed, but likely given the seed.
            String query = HoneycombTestConstants.MD_VALID_FIELD + "='" +
                obj.origoid + "' AND " + HoneycombTestConstants.MD_LONG_FIELD2 +
                "=" + seed;

            int nbResults = 0;
            // The first store does not add any metadata, hence the (-1).
            int numExpectedResults = obj.activeOids.size() - 1;
            // If objects have been deleted, then we expect 0 results
            if (objsDeleted) {
                Log.INFO("Objects have been deleted, changing expected " +
                    "results from " + numExpectedResults + " to 0");
                numExpectedResults = 0;
            }

            try {
                CmdResult cr = query(query);
                QueryResultSet qrs = (QueryResultSet) cr.rs;
                while (qrs.next()) {
                    nbResults++;
                }
                Log.INFO("Query [" + i + "] for " + query + " returned " +
                    nbResults + ", expected " + numExpectedResults);
            } catch (Exception e) {
                self.testFailed("query failed with exception " + e);
                return;
            }

            if (nbResults != numExpectedResults) {
                self.testFailed("query failed, obj=" + obj + ", i=" +
                    i + ", expecting  " + numExpectedResults +
                    ", got " + nbResults);
                return;
            }
        }
        self.testPassed();
    }

    /**
     * Now we check for each OID that we can:
     * - Retrieve the object,
     * - Delete the object and test we can't retrieve it
     */
    private void deleteComplexChains(TestCase self) 
        throws HoneycombTestException {

        Log.INFO("deleteComplexChains: storedObjs.size()=" +
            storedObjs.size() + ", completedObjs.size()=" + 
            completedObjs.size());

        for (int i = 0; i < storedObjs.size() + completedObjs.size(); i++) {
            StoredObject obj = null;
            if (i < storedObjs.size()) {
                obj = (StoredObject) storedObjs.get(i);
            } else {
                obj = (StoredObject) completedObjs.get(i - storedObjs.size());
            }

            for (int j = 0; j < obj.activeOids.size(); j++) {
                String oidToUse = (String) obj.activeOids.get(j);

                // skip delete/retrieve for the oid that we passed in -- we'll
                // check it is still retrievable at the end.
                if (oidToUse.equals(oidArg)) {
                    Log.INFO("Skipping retrive/delete of passed in OID " +
                        oidArg);
                    continue;
                }
 
                if (!noretrieve) {
                    Log.INFO("retrieving before delete [" + i + "][" + j + "] " + 
                        oidToUse);
                    CmdResult cr = retrieve(oidToUse);
                    if (!cr.pass) {
                        self.addBug("6507453",  	
	                        "old bug fixed in 1.1-65: simultaneous add " +
                                "metadata doesn't always increment " + 	
	                        "data object's refcnt (possible data loss)");
                        self.testFailed("failed to retrieve before delete, obj=" +
                            oidToUse);
                        return;
                    }
                }
                
                Log.INFO("deleting [" + i + "][" + j + "] " + oidToUse);
                verbose("BEGIN delete [" + i + "][" + j + "] seed=" + seed +
                     " oid=" + oidToUse);
                CmdResult cr = delete(oidToUse);
                verbose("END delete [" + i + "][" + j + "] seed=" + seed +
                     " oid=" + oidToUse);
                if (!cr.pass) {
                    self.testFailed("failed to delete object " +
                        oidToUse);
                    return;
                }

                if (!noretrieve) {
                    Log.INFO("retrieving after delete [" + i + "][" + j + "] " +
                        oidToUse);
                    cr = retrieve(oidToUse);                
                    if (cr.pass) {
                        self.testFailed("succeeded to retrieve deleted object " +
                            oidToUse);
                        return;
                    }
                }
            }

            // We assume we didn't delete the passed-in oid above and that it
            // should still be retrievable.  This assumes no other test did
            // the delete.  If we have problems with retrieve, this means
            // we are in trouble with refcnts.
            if (oidArg != null) {
                Log.INFO("retrieving passed-in oid " + oidArg);
                CmdResult cr = retrieve(oidArg);
                if (!cr.pass) {
                    self.addBug("6507453",  	
	                        "old bug fixed in 1.1-65: simultaneous add " +
                                "metadata doesn't always increment " + 	
	                        "data object's refcnt (possible data loss)");
                    self.testFailed("failed to retrieve passed-in object " +
                        oidArg);
                    return;
                }
            }
        }
        self.testPassed();
    }

    // Log to syslog -- useful for detecting where an error
    // occurs relative to a test action
    public void verbose(String s) {
        if (verbose) {
            Log.STEP(s);
        }
    }

    public void testMetadataComplexChains()
        throws HoneycombTestException {
        TestCase self = null;

        self = createTestCase("storeComplexChains");
        addTags(self);
        if (self.excludeCase())
            return;
        storeComplexChains(self);


        self = createTestCase("queryComplexChains");
        addTags(self);
        if (self.excludeCase())
            return;
        // false means objects have not been deleted yet
        queryComplexChains(self, false);


        self = createTestCase("deleteComplexChains");
        addTags(self);
        if (self.excludeCase())
            return;
        deleteComplexChains(self);

        self = createTestCase("queryComplexChainsAfterDelete");
        addTags(self);
        if (self.excludeCase())
            return;
        // true means objects have been deleted
        queryComplexChains(self, true);


        StringBuffer strBuf = new StringBuffer();
        strBuf.append("stored objs = " + storedObjs.size() +
                      ", completed objs = " + completedObjs.size() + "\n");
        Log.INFO(strBuf.toString());
        /*
        for (int k = 0; k < storedObjs.size(); k++) {
            StoredObject obj = (StoredObject) storedObjs.get(k);            
            strBuf.append("stored obj size = " + obj.activeOids.size() + "\n");
        }
        */
        
    }

    public class StoredObject {

        public String origoid;
        public String dataoid;
        public int numRefs;
        public int origObjectNumber;
        public int mostRecentMDNumber;
        public ArrayList activeOids;
        public HOidInfo oidInfo;

        StoredObject(String oid, String doid, int num) {
            origoid = oid;
            dataoid = doid;
            numRefs = 0;
            origObjectNumber = num;
            activeOids = new ArrayList();
            activeOids.add(origoid);
            if (testBed.rmiEnabled() && refcheck) {
                try {
                    oidInfo = testBed.getOidInfo(doid, true);
                } catch (HoneycombTestException hte) {
                    Log.INFO("getOidInfo failed: " + hte);
                    oidInfo = null;
                }
            } else {
                oidInfo = null;
            }
        }

        public void addObject(String newoid, int num) {
            numRefs++;
            mostRecentMDNumber = num;
            activeOids.add(newoid);
        }

        public boolean addMD(int numObjs, int random)
            throws HoneycombTestException {
            HOidInfo newOidInfo = null;
            boolean success = false;
            while (success == false && activeOids.size() != 0) {
               int index = random % activeOids.size();
                String oidToUse = origoid;
                HashMap hm = new HashMap();
                hm.put(HoneycombTestConstants.MD_VALID_FIELD, oidToUse);
                hm.put(HoneycombTestConstants.MD_LONG_FIELD1,
                       new Long(numRefs));
                // we use the seed to allow query to find only results
                // from this test run, since now this test can be run
                // simultaneously on the same base oid
                hm.put(HoneycombTestConstants.MD_LONG_FIELD2,
                       new Long(seed));

                Log.DEBUG("Adding md " +  hm + " to oid " + oidToUse +
                          " at index " + index);
                verbose("BEGIN addMD [" + numObjs + "] seed=" + seed);
                CmdResult cr = addMetadata(oidToUse, hm);
                verbose("END addMD [" + numObjs + "] seed=" + seed +
                    ", got oid=" + cr.mdoid);

                if (cr.pass) {
                    addObject(cr.mdoid, numObjs);
                    Log.INFO("[" + mostRecentMDNumber +
                             "] Added metadata object " + cr.mdoid + 
                             " to mdoid " +
                             oidToUse + "(index " + index +
                             ") to storedObject " + toString());

                    // check on references after add
                    if (testBed.rmiEnabled() && refcheck) {
                        // XXX todo if data oid is null, we can get it using RMI
                        if (dataoid != null) {
                            newOidInfo = testBed.getOidInfo(dataoid, true);
                            if (!testBed.verifyRefCounts(oidInfo,
                                    newOidInfo, 1)) {
                                Log.INFO("Bad ref counts for obj: " +
                                    newOidInfo);
                                throw new HoneycombTestException(
                                    "Found incorrect ref counts");
                            }
                            oidInfo = newOidInfo;
                        }
                    }
                    success = true;
                    break;
                } else {
                    // we failed...maybe our object is deleted by another test?
                    if (!cr.checkExceptions(NoSuchObjectException.class,
                                            "addMD")) {
                        // We got the right exception...may as well check
                        // that the content is okay...
                        //
                        // XXX
                        // BUG 6273390: OA should return user appropriate 
                        // msgs/excpns (and no internal oids, refCount, 
                        // deleted status)
                        if (!cr.checkExceptionStringContent()) {
                            Log.WARN("Got incorrect Deleted expception--" +
                                     "ignoring until bug 6273390 is fixed");
                        }
                    } else {
                        Log.WARN("ignoring unexpected exception until " +
                                 "bug 6273390 is fixed.");
                        // XXX 6273390
                        //throw new HoneycombTestException(
                        //XXX print exceptions better
                        //"Unexpected exception during test: " + cr.thrown);
                    }

                    // in any case, consider this obj deleted
                    activeOids.remove(index);

                    if (oidArg != null) {
                        throw new HoneycombTestException("can't specify a " +
                            "deleted oid for the oid to addMD to");
                    }
                }
            }
            return (success);
        }

        public String toString() {
            return ("oid=" + origoid +
                    "; dataoid=" + dataoid +
                    "; numRefs=" + numRefs +
                    "; origObjNum=" + origObjectNumber +
                    "; mostRecentMDNum=" + mostRecentMDNumber +
                    "; numActive=" + activeOids.size());
        }
    }
}
