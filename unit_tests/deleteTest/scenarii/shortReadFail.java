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



/**
 * Verify CR6684653
 * Store a small file, cause a short read, fail reading of first 
 * fragment and two more fragments, verify if read fails. 
 */

import com.sun.honeycomb.delete.Constants;
import com.sun.honeycomb.delete.OIDTagTable;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.coordinator.MetadataCoordinator;
import com.sun.honeycomb.coordinator.Coordinator;

Coordinator coordinator = Coordinator.getInstance();
OIDTagTable oidTable = OIDTagTable.getInstance();

void verifyShortRead(int storesize, int mdOid, int oid, int chunk) {

    ECHO("--> Store object of " + storesize + " bytes");
    STORE(mdOid, storesize);
    DEREF(mdOid, oid);
    REFCNTCHECK(oid, Constants.ALL_FRAGS, 1, 1);
    REFCNTCHECK(mdOid, Constants.ALL_FRAGS, -1, -1);

    ECHO("---> Corrupt frag 0 (1st 15 bytes)");
    CORRUPTFRAG(oid, 0, chunk, 15);

    coordinator.deleteFromLocalCache(oidTable.resolve(Integer.toString(mdOid)), 
                                     oidTable.resolve(Integer.toString(oid)));    
    ECHO("--> Retrieve (should succeed)");
    RETRIEVE(oid, true);
    
    ECHO("---> Corrupt frag 0 (1st 15 bytes)");
    CORRUPTFRAG(oid, 0, chunk, 15);
    ECHO("---> Corrupt frag 1 (1st 15 bytes)");
    CORRUPTFRAG(oid, 1, chunk, 15);

    coordinator.deleteFromLocalCache(oidTable.resolve(Integer.toString(mdOid)), 
                                     oidTable.resolve(Integer.toString(oid)));    
    ECHO("--> Retrieve (should succeed)");
    RETRIEVE(oid, true);

    ECHO("---> Corrupt frag 0 (1st 15 bytes)");
    CORRUPTFRAG(oid, 0, chunk, 15);
    ECHO("---> Corrupt frag 1 (1st 15 bytes)");
    CORRUPTFRAG(oid, 1, chunk, 15);
    ECHO("---> Corrupt frag 2 (1st 15 bytes)");
    CORRUPTFRAG(oid, 2, chunk, 15);
        
    coordinator.deleteFromLocalCache(oidTable.resolve(Integer.toString(mdOid)), 
                                     oidTable.resolve(Integer.toString(oid)));    
    ECHO("--> Retrieve (should fail)");
    RETRIEVE(oid, false);
}

OAClient oac = OAClient.getInstance();
chunksize = Constants.MAX_CHUNK_SIZE = oac.blockSize;
oac.setMaxChunkSize(Constants.MAX_CHUNK_SIZE);

storesize = 10000;
verifyShortRead(storesize, 1, 0, 0);

storesize = chunksize * 2 + 1000;
verifyShortRead(storesize, 3, 2, 2);

/* Verify DD behavior */
ECHO("--> Store object of 1000 bytes");
STORE(5, 1000);
DEREF(5, 4);
REFCNTCHECK(4, Constants.ALL_FRAGS, 1, 1);
REFCNTCHECK(5, Constants.ALL_FRAGS, -1, -1);

ECHO("--> Corrupt frag 0 (1st 15 bytes)");
CORRUPTFRAG(4, 0, 0, 15);

ECHO("--> Remove frag 1 and 2");
TRUNCATEFRAG(4, 1, 0, 1); // truncate to 1 byte file
SCANFRAG(4, 1, 0);
ECHO("--> Verify that frag 1 is deleted");
FRAGABSENT(4, 1, 0);
TRUNCATEFRAG(4, 2, 0, 1);
SCANFRAG(4, 2, 0);
ECHO("--> Verify that frag 2 is deleted");
FRAGABSENT(4, 2, 0);

coordinator.deleteFromLocalCache(oidTable.resolve(Integer.toString(5)), 
                                 oidTable.resolve(Integer.toString(4)));    
ECHO("--> Retrieve data obj (should fail)");
RETRIEVE(4, false);

ECHO("--> Recover (should fail)");
try {
    RECOVER(4, 1, 0);
    RECOVER(4, 2, 0);
} catch (ArchiveException expected) {
}
FRAGABSENT(4, 1, 0);
FRAGABSENT(4, 2, 0);

ECHO("Success.");
