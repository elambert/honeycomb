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



package com.sun.honeycomb.hctest.cases.interfaces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;
import java.io.File;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.client.*;

/**
 * HoneycombInterface encapsulates actions that can be done using the different
 * honeycomb interfaces (such as the java API, nfs, etc.).  Currently only the
 * java API is supported.
 */
public class HoneycombInterface {
    public String type;  // interface type being used (ie, api, nfs)
    public boolean useRefCountSemantics = true; // Deleting MD obj dels the obj.
                                                // Data OIDs are never used.
    public String baseProcedureName;
    public String baseParametersName;
    public String actionListString; // actions we've done so far on this object
    public TestCase testcase;
    public boolean res; // pass/fail
    public ArrayList bugs; // results aren't always available when setting bugs
    public String metadataIncarnation; // unique string for this object
    public ArrayList metadataObjects;
    public MetadataObject activeMetadataObject;
    public MetadataObject retrievedMetadataObject;
    public QueryResultSet queryResults;
    public boolean streamData;
    public int initialMetadataMap;
    public String storePath; // for NFS and the like where there aren't oids
    public long seed;
    public long filesize;
    public String filesizetype;
    public String filename;  // file that is to be stored
    public String filenameRetrieve; // tmp file for retrieve
    public String dataOID;
    public String returnedDataHash; // returned from API
    public String computedDataHash; // computed locally
    public String computedRetrieveStreamDataHash; // computed on retrieve stream
    public OperationsExpectedStatus expectedResults;
    public String dataVIP;
    public int dataPort;
    public HoneycombTestClient htc;

    public static final String DELIM = "-";

    public static final int NOMD = 1;
    public static final String NOMD_STRING = "NO_METADATA";
    public static final int EMPTYMD = 2;
    public static final String EMPTYMD_STRING = "EMPTY_METADATA";
    public static final int DEFAULTMD = 3;
    public static final String DEFAULTMD_STRING = "DEFAULT_METADATA";

    public static String getMetadataTypeString(int i) {
        if (i == NOMD) {
            return (NOMD_STRING);
        } else if (i == EMPTYMD) {
            return (EMPTYMD_STRING);
        } else if (i == DEFAULTMD) {
            return (DEFAULTMD_STRING);
        } else {
            return ("XXX_UNKNOWN_MD_STRING");
        }
    }

    public static final int maxQueryResults = 10000;

    public static final String MD_INCARNATION_FIELD =
        HoneycombTestConstants.MD_VALID_FIELD2;

    public HoneycombInterface(String dVIP, int dPort)
                                                 throws HoneycombTestException {
        type = null;
        metadataIncarnation = RandomUtil.getRandomString(20);
        streamData = true;
        initialMetadataMap = NOMD;
        seed = HoneycombTestConstants.INVALID_SEED;
        filesize = HoneycombTestConstants.INVALID_FILESIZE;
        filesizetype = null;
        dataVIP = dVIP;
        if (dataVIP == null) {
            throw new HoneycombTestException("DataVIP must not be null");
        } else {
            Log.DEBUG("Using dataVIP " + dataVIP);
        }
	dataPort = dPort;
        htc = new HoneycombTestClient(dataVIP, dataPort);

        initialize();
    }

    // initialize common fields that are reset at the beginning of scenarios
    // and at initial creation time
    public void initialize() {
        testcase = null;
        res = false; // "fail"
        bugs = new ArrayList();
        baseProcedureName = null;
        baseParametersName = null;
        actionListString = null;
        metadataObjects = new ArrayList();
        activeMetadataObject = null;
        retrievedMetadataObject = null;
        queryResults = null;
        storePath = null;
        filename = null;
        dataOID = null;
        returnedDataHash = null; // returned from server
        computedRetrieveStreamDataHash = null;
        computedDataHash = null; // computed locally
        expectedResults = new OperationsExpectedStatus(useRefCountSemantics);
     }

    // Cleanup, try to encourage freeing of sockets by dropping the reference
    // to the test client
    public void finalize() {
        htc = null;
    }

    // Reset some of the state to start a new scneario
    public void startNewScenario(Scenario scen) {
        Log.INFO("\n============ Initializing for new scenario =========");
        Log.INFO("============ " + scen.description + " =========");
        Log.INFO("============ " + scen.getActionListString() + " =========\n");
        initialize();
        initMetadata();
        baseProcedureName = scen.getActionListString();
        baseParametersName = "actionsequence=" + scen.getActionListString() +
            " " + HoneycombTestConstants.PROPERTY_FILESIZE +
            "=" + filesize + " filesizetype=" + filesizetype + " interfacetype="
            + type + " metadatatype=" +
            getMetadataTypeString(initialMetadataMap);
        scen.resetActionIterator();
        bugs = scen.bugs;
    }

    public String getCurrentProcedure() {
        return (baseProcedureName);
    }

    public String getCurrentParamsTruncated(Scenario scen) {
        // XXX alas, the parameters can only be ~256 in length.
        // Truncation is the best we can do until bug 6273449
        // "limit of 256 chars for params is a bit short" is fixed.
        if (baseParametersName.length() > 250) {
            return (baseParametersName.substring(0, 230) + " (TRUNCATED!)");
        }

        return (baseParametersName);
    }

    public String getCurrentParams(Scenario scen) {
        // XXX We no longer store a result per step...
        return (baseParametersName);
        // return (baseParametersName + actionListString);
    }

    public String getFullTestName() {
        return (baseParametersName);
        // return (baseProcedureName + "; " + baseParametersName);
        // return (getCurrentProcedure() + "; " + getCurrentParams());
    }

    public String appendNewAction(String a) {
        if (actionListString != null) {
            actionListString += DELIM + a;
        } else {
            actionListString = a;
        }

        return (actionListString);
    }

    public String toString() {
        return (type + DELIM + filesizetype + DELIM + filesize + DELIM +
            getMetadataTypeString(initialMetadataMap));
    }

    public String toStringVerbose() {
        return (toString());
    }

    public String hashString() {
        String s = "DataHash returned" + DELIM + returnedDataHash +
            DELIM + "DataHash computed" + DELIM + computedDataHash + "; ";
        /* XXX currently MD hash isn't a meaningful concept
        ListIterator li = metadataObjects.listIterator();
        int i = 0;
        while (li.hasNext()) {
            MetadataObject mo = (MetadataObject) li.next();
            s += "[" + i++ + "] " + mo.hashString() + "; ";
        }
        */

        return (s);
    }

    public String IDString() {
        return (null);
    }

    public void initMetadata() {
        Log.DEBUG("Initializing Metadata of type " + initialMetadataMap);
        if (initialMetadataMap != NOMD) {
            // create the empty MD for EMPTYMD case
            activeMetadataObject = new MetadataObject();

            // if we are using default md, augment the empty md
            if (initialMetadataMap == DEFAULTMD) {
                activeMetadataObject.put(HoneycombTestConstants.MD_VALID_FIELD1,
                    type);
                activeMetadataObject.put(HoneycombTestConstants.MD_ATTR_SIZE1,
                    new Long(filesize));
                activeMetadataObject.put(MD_INCARNATION_FIELD,
                    metadataIncarnation);
                activeMetadataObject.queriable = true;
                Log.DEBUG("Map is " + activeMetadataObject.hm);
            }

            metadataObjects.add(activeMetadataObject);
        }
    }

    public void initMetadata(HashMap hm) {
        activeMetadataObject = new MetadataObject();
        activeMetadataObject.hm = hm;
        activeMetadataObject.put(MD_INCARNATION_FIELD, metadataIncarnation);
        activeMetadataObject.queriable = true;
        metadataObjects.add(activeMetadataObject);
    }

    // If there are objects that have null OIDs, remove them from our list
    public void cleanupMetadataObjects() throws HoneycombTestException {
        ListIterator li = metadataObjects.listIterator();
        int i = 0;
        int indexToDelete = -1;
        while (li.hasNext()) {
            MetadataObject mo = (MetadataObject) li.next();
            if (mo.metadataOID == null) {
                if (indexToDelete != -1) {
                    throw new HoneycombTestException(
                        "unexpectedly found two MD objects needing removal!");
                }

                Log.INFO("Will remove md object " + mo + ", index " + i +
                    ", from list.  List current size is " +
                    metadataObjects.size());
                indexToDelete = i;
            }
            i++;
        }

        if (indexToDelete != -1) {
            Log.DEBUG("Removing MD object at index " + indexToDelete);
            metadataObjects.remove(indexToDelete);
            Log.DEBUG("MD list now has " + metadataObjects.size() + " items:" + this.IDString());
        }
    }

    // If we are using things like NVOA that operate on MD OIDs,
    // then return those.  Otherwise, return the data OID.
    public ObjectIdentifier findObjectIdentifierToUse()
        throws HoneycombTestException {
        // remember the active MD obj
        MetadataObject md = activeMetadataObject;
        ObjectIdentifier oid;

        if (useRefCountSemantics) {
            if (findNonDeletedMetadata()) {
                oid = new ObjectIdentifier(activeMetadataObject.metadataOID);
            } else if (findAnyMetadata()) {
                oid = new ObjectIdentifier(activeMetadataObject.metadataOID);
            } else {
                throw new HoneycombTestException("No MD object found!");
            }
        } else {
            oid = new ObjectIdentifier(dataOID);
            
        }

        // restore the original active MD obj
        activeMetadataObject = md;
        Log.INFO("Using oid " + oid + "; useRefCountSemantics=" +
            useRefCountSemantics);
        return (oid);
    }

    // set activeMetadataObject to any MD object (deleted or not)
    // XXX make these more random
    public boolean findAnyMetadata() {
        ListIterator li = metadataObjects.listIterator();

        while (li.hasNext()) {
            MetadataObject mo = (MetadataObject)li.next();
            activeMetadataObject = mo;
            return (true);
        }

        return (false);
    }

    // set activeMetadataObject to a non-deleted MD object
    // XXX make these more random
    public boolean findNonDeletedMetadata() {
        ListIterator li = metadataObjects.listIterator();

        while (li.hasNext()) {
            MetadataObject mo = (MetadataObject)li.next();
            if (mo.deleted == false) {
                activeMetadataObject = mo;
                return (true);
            }
        }

        return (false);
    }


    // set activeMetadataObject to a queriable, non-deleted MD object
    public boolean findQueriableNonDeletedMetadata() {
        ListIterator li = metadataObjects.listIterator();

        while (li.hasNext()) {
            MetadataObject mo = (MetadataObject)li.next();

            // Make a best effort to at least get a MD object that is
            // queriable...
            if (mo.queriable) {
                activeMetadataObject = mo;
            }

            // If it also not deleted, yay!  Return it, we are done looking.
            if (mo.queriable && mo.deleted == false) {
                return (true);
            }
        }

        return (false);
    }

    public void reuseFiles(HoneycombInterface hi) {
        filename = hi.filename;
        filesize = hi.filesize;
        computedDataHash = hi.computedDataHash;
    }

    public void preStoreObject() throws HoneycombTestException {
        // XXX
        // defaultFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);

        if (dataVIP == null) {
            throw new HoneycombTestException("Data VIP was not set");
        } else {
            Log.INFO("Using data VIP " + dataVIP);
        }
    
        if (streamData) {
            filename = HoneycombTestConstants.DATA_FROM_STREAM;
            return;
        }

        // Create a file, if needed, if we are doing a non-stream store.
        // If we are not given a file to store, create one
        if (filename == null) {
            // XXX char vs byte
            Log.DEBUG("Creating a file of size " + filesize);
            File file = FileUtil.createRandomCharFile(filesize);
            filename = file.getAbsolutePath();
            Log.INFO("Created file " + filename + " of size " + filesize);
        } else {
            Log.INFO("Re-using file " + filename + " of size " + filesize);
        }

        // XXX fix when consolidate libs to not try/catch...
        try {
            if (computedDataHash == null) {
                computedDataHash = com.sun.honeycomb.hctest.util.HCUtil.computeHash(filename);
            } else {
                Log.INFO("Re-using computed hash " + computedDataHash);
            }
        } catch (Throwable t) {
            throw new HoneycombTestException(t);
        }

        Log.INFO("File " + filename + " has Hash " + computedDataHash);

        // XXX MD SHA?
    }

    public void storeObject() throws HoneycombTestException {
        throw new HoneycombTestException("generic storeObject not supported");
    }

    public boolean verifyStoreObject() {
        // It is expected that process store result is called before this
        // to fill in the data Hash.

        if (computedDataHash != null) {
            if (returnedDataHash == null ||
                !returnedDataHash.equals(computedDataHash)) {
                Log.ERROR("Data hashes don't match: " + hashString());
                return (false);
            }
        }

        if (activeMetadataObject != null &&
            activeMetadataObject.computedMetadataHash != null) {
            if (activeMetadataObject.returnedMetadataHash == null ||
                !activeMetadataObject.returnedMetadataHash.equals(
                activeMetadataObject.computedMetadataHash)) {
                Log.ERROR("Metadata hashes don't match: " + hashString());
                return (false);
            }
        }

        Log.INFO("Verified store was okay");
        return (true);
    }

    public void preStoreMetadata() throws HoneycombTestException {
        HashMap hm = new HashMap();
        // XXX fix this...use oid?
        hm.put(HoneycombTestConstants.MD_VALID_FIELD1, "a string");
        initMetadata(hm);
    }

    public void storeMetadata() throws HoneycombTestException {
        throw new HoneycombTestException("generic storeMetadata not supported");
    }

    public boolean verifyStoreMetadata() {
        // XXX compare hash
        Log.INFO("Verified store metadata was okay");
        return (true);
    }

    public void preRetrieveObject() {
        if (!streamData) {
            filenameRetrieve = filename + DELIM +
                Scenario.RETRIEVE_OBJECT_STRING;
        } else {
            filenameRetrieve = HoneycombTestConstants.DATA_FROM_STREAM;
        }
    }

    public void retrieveObject() throws HoneycombTestException {
        throw new HoneycombTestException("generic retrieveObject not " +
            "supported");
    }

    public boolean verifyRetrieveObject() {
        String computedRetrieveDataHash = null;

        if (streamData) {
            computedRetrieveDataHash = computedRetrieveStreamDataHash;
        } else {
            try {
                computedRetrieveDataHash = com.sun.honeycomb.testcmd.common.HoneycombTestCmd.computeHash(filenameRetrieve);
            } catch (Throwable t) {
                Log.ERROR("Failed to compute hash: " + t);
            }
        }

        Log.INFO("File " + filenameRetrieve + " has Hash " +
            computedRetrieveDataHash);

        if (computedRetrieveDataHash == null ||
            !computedDataHash.equals(computedRetrieveDataHash)) {
            return (false);
        }

        Log.INFO("Verified retrieve was okay");
        return (true);
    }

    public void preRangeRetrieveObject() {
        //filenameRangeRetrieve = filename + "-rangeretrieve";
    }

    public void rangeRetrieveObject() throws HoneycombTestException {
        throw new HoneycombTestException("generic rangeRetrieveObject not " +
            "supported");
    }

    public void verifyRangeRetrieveObject() {
        // is retrieved content correct?
    }

    public void preRetrieveMetadata() throws HoneycombTestException {
        if (activeMetadataObject == null || metadataObjects == null) {
            throw new HoneycombTestException("Metadata hasn't been stored; " +
                "can't retrieve it");
        }

        Log.DEBUG("MD objs: " + this.IDString());

        if (findNonDeletedMetadata()) {
            Log.INFO("Found active MD to retrieve: " + activeMetadataObject);
            return;
        } else if (findAnyMetadata()) {
            Log.INFO("Will retrieve delete MD: " + activeMetadataObject);
        } else {
            throw new HoneycombTestException("MD hasn't been stored");
        }
    }

    public void retrieveMetadata() throws HoneycombTestException {
        throw new HoneycombTestException("generic retrieveMetadata not " +
            "supported");
    }

    public boolean verifyRetrieveMetadata() {
        if (activeMetadataObject == null && retrievedMetadataObject == null) {
            Log.INFO("Both metadata objects are null");
            return (true);
        }

        if (activeMetadataObject == null || retrievedMetadataObject == null) {
            Log.ERROR("Only one metadata object is null");
            return (false);
        }

        if (activeMetadataObject.hm == null &&
            retrievedMetadataObject.hm == null) {
            Log.INFO("Both metadata maps are null");
            return (true);
        }

        if (activeMetadataObject.hm == null || 
            retrievedMetadataObject.hm == null) {
            Log.INFO("Only one metadata map is null");
            return (false);
        }

        String keys[] = activeMetadataObject.getKeys();
        String keysRetrieved[] = retrievedMetadataObject.getKeys();

        if (keys == null && keysRetrieved == null) {
            Log.INFO("Both key lists are null");
            return (true);
        }

        if ((keys == null && keysRetrieved != null) ||
            (keys != null && keysRetrieved == null) ||
            keys.length != keysRetrieved.length) {
            Log.ERROR("Mismatch in MD; Passed in MD: " +
                activeMetadataObject.hm + "; Returned MD: " +
                retrievedMetadataObject.hm);
        }

        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            boolean foundkey = false;

            for (int j = 0; j < keysRetrieved.length; j++) {
                // Make sure this key shows up in the server map
                // Since maps are same length, if all keys in one are
                // in the other, that's enough for equivalence
                // Note that we can't use nvr.equals because the system
                // metadata won't match
                if (keysRetrieved[j].equals(key)) {
                    foundkey = true;
                    // also check the values are the same
                    if ((activeMetadataObject.hm.get(key)).equals(retrievedMetadataObject.hm.get(key))) {
                        Log.DEBUG("Found " + key + "=" +
                            activeMetadataObject.hm.get(key));
                    } else {
                        Log.ERROR("key " + key + " mismatch in MD; "
                            + "Passed in MD: " + activeMetadataObject.hm +
                            "; Returned MD: " + retrievedMetadataObject.hm);
                    }
                }
            }

            if (!foundkey) {
                Log.ERROR("Did not find key " + key + " in map");
                return (false);
            }
        }

        Log.INFO("Verified retrieve metadata was okay");
        return (true);
    }

    public void preDeleteObject() throws HoneycombTestException {
        // Check that we have an object stored
        // XXX should things like NFS handle this differently or will
        // they set the dataOID to be compliant?
        if (dataOID == null) {
            throw new HoneycombTestException("Store hasn't succeeded; " +
                "can't delete");
        }

        // if we need to use MD OIDs, let's find a non-deleted one
        if (useRefCountSemantics) {
            preDeleteMetadata();
        }
    }

    public void deleteObject() throws HoneycombTestException {
        throw new HoneycombTestException("generic deleteObject not supported");
    }

    public boolean verifyDeleteObject() {
        // XXX nothing to do here?  rely on separate calls to retrieve/query
        // to validate?
        return (true);
    }

    public void preDeleteMetadata() throws HoneycombTestException {
        // Check that we have a metadata object stored
        if (activeMetadataObject == null || metadataObjects == null) {
            throw new HoneycombTestException("Metadata hasn't been stored; " +
                "can't delete");
        }

        Log.DEBUG("MD objs: " + this.IDString());

        if (findNonDeletedMetadata()) {
            Log.INFO("Found active MD to delete: " + activeMetadataObject);
            return;
        } else if (findAnyMetadata()) {
            Log.INFO("Will re-delete MD: " + activeMetadataObject);
        } else {
            throw new HoneycombTestException("MD hasn't been stored");
        }
    }

    public void deleteMetadata() throws HoneycombTestException {
        throw new HoneycombTestException("generic deleteMetadata not " + 
            "supported");
    }

    public boolean verifyDeleteMetadata() {
        // XXX nothing to do here?  rely on separate calls to retrieve/query
        // to validate?
        return (true);
    }

    public void preQuery() throws HoneycombTestException {
        // Check that we have a metadata object stored
        if (activeMetadataObject == null || metadataObjects == null) {
            throw new HoneycombTestException("Metadata hasn't been stored; " +
                "can't query");
        }

        if (!findQueriableNonDeletedMetadata()) {
            if (activeMetadataObject.queriable == false) {
                throw new HoneycombTestException(
                    "Couldn't find a queriable metadata entry...");
            }

            Log.WARN("Could only find deleted queriable metadata entries");
        }
    }

    public void query() throws HoneycombTestException {
        throw new HoneycombTestException("generic query not " +
            "supported");
    }

    //XXX PORT
    public boolean verifyQuery() throws Throwable {
        if (queryResults == null) {
            Log.ERROR("Query results were null");
            return (false);
        }

        // Verify that the active MD object is in the results
        int i = 0;
        while (queryResults.next()) {
            String resultOid = queryResults.getObjectIdentifier().toString();
            if (resultOid.equals(activeMetadataObject.metadataOID)) {
                Log.INFO("Found oid " + resultOid +
                    " in the results after looking at " + i + " results");
                return (true);
            }
            i++;
        }

        Log.ERROR("Query results did not contain " + activeMetadataObject);
        return (false);
    }
}
