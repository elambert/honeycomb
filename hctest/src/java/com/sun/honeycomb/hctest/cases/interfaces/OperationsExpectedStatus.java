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

/**
 * A simple state machine that records what actions happen on an object to
 * determine if future actions should fail or succeed.
 */
public class OperationsExpectedStatus {
    public boolean useRefCountSemantics;
    public boolean storeObject;
    public boolean storeMetadata;
    public boolean retrieveObject;
    public boolean retrieveMetadata;
    public boolean deleteObject;
    public boolean deleteMetadata;
    public boolean query;
    public boolean queryAlwaysFails;

    public int numObjectsStored = 0;
    public int numMetadataRecords = 0;
    public int numQueriableMetadataRecords = 0;

    public OperationsExpectedStatus(boolean useRefCounts) {
        useRefCountSemantics = useRefCounts;
        storeObject = true; // always should succeed
        storeMetadata = false;
        retrieveObject = false;
        retrieveMetadata = false;
        deleteObject = false;
        deleteMetadata = false;
        query = false;
        queryAlwaysFails = false;
    }

    public String toString() {
        return(
            "useRefCountSemantics=" + useRefCountSemantics +
            " numObjectsStored=" + numObjectsStored +
            " numMetadataRecords=" + numMetadataRecords +
            " numQueriableMetadataRecords=" + numQueriableMetadataRecords +
            " storeObject=" + storeObject +
            " storeMetadata=" + storeMetadata +
            " retrieveObject=" + retrieveObject +
            " retrieveMetadata=" + retrieveMetadata +
            " deleteObject=" + deleteObject +
            " deleteMetadata=" + deleteMetadata +
            " query=" + query
            );
    }

    public void afterStoreWithQueriableMetadata() {
        numObjectsStored++;
        numMetadataRecords++;
        numQueriableMetadataRecords++;
        storeMetadata = true;
        retrieveObject = true;
        retrieveMetadata = true;
        deleteObject = true;
        deleteMetadata = true;
        query = queryStatus(); // XXX not in 1.0?
    }

    // If you use basic API but don't pass explicit MD
    public void afterStoreWithNonQueriableMetadata() {
        afterStoreWithQueriableMetadata();
        numQueriableMetadataRecords--;
        query = queryStatus();
    }

    // This is only used in the advanced API
    public void afterStoreWithoutMetadata() {
        afterStoreWithQueriableMetadata();
        numMetadataRecords--;
        numQueriableMetadataRecords--;
        // XXX these tests do not support query via the api
        // queryAlwaysFails = true;

        // We may have other MD records that allow MD ops to succeed
        if (numMetadataRecords == 0) {
            retrieveMetadata = false;
            deleteMetadata = false;
        }

        query = queryStatus();
    }

    public void afterAddQueriableMetadata() {
        numMetadataRecords++;
        numQueriableMetadataRecords++;
        retrieveMetadata = true;
        deleteMetadata = true;
        query = queryStatus();
    }

    public void afterAddMetadata() {
        afterAddQueriableMetadata();
        numQueriableMetadataRecords--;
        query = queryStatus();
    }

    // retrieve and query don't change the state so they are omitted

    public void afterDeleteObject() {
        if (useRefCountSemantics) {
            // XXX did we delete a queriable record?
            numMetadataRecords--;
            numQueriableMetadataRecords--;
        } else {
            numObjectsStored--;
        }
        
        if (numObjectsStored > 0) {
            // XXX ???
            return;
        }

        storeMetadata = false;
        retrieveObject = false;
        deleteObject = false;

        // XXX if the object is gone, is the MD gone? Currently no...
        // retrieveMetadata = false;
        
        // deleteMetadata = true;  // XXX unchanged????
        query = queryStatus();  // XXX does this change if obj deleted?
    }

    public void afterDeleteQueriableMetadata() {
        numMetadataRecords--;
        numQueriableMetadataRecords--;
        
        if (numMetadataRecords > 0) {
            return;
        }

        retrieveMetadata = false;
        deleteMetadata = false;

        if (useRefCountSemantics) {
            retrieveObject = false;
            storeMetadata = false;
            deleteObject = false;
        }

        query = queryStatus();
    }

    public void afterDeleteMetadata() {
        afterDeleteQueriableMetadata();
        // falsely decremented in routine above
        numQueriableMetadataRecords++;

        query = queryStatus();
    }

    private boolean queryStatus() {
        if (queryAlwaysFails) {
            return (false);
        }

        return (numQueriableMetadataRecords > 0);
    }
}
