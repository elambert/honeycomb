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



import com.sun.honeycomb.delete.Constants;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.coordinator.Coordinator;

import java.util.logging.Logger;
import java.util.logging.Level;

private static final Logger classLogger = Logger.getLogger("com.sun.honeycomb.coordinator.BlockCache");
classLogger.setLevel(Level.FINEST);
private static final Logger classLogger1 = Logger.getLogger("com.sun.honeycomb.coordinator.Coordinator");
classLogger1.setLevel(Level.FINEST);

int iter = 0; // test iteration
boolean data = true; // interested in data cache

// Choose object sizes (in blocks):
// small objects with size < maxhits fit in cache,
// larger objects with size >= maxhits do not,
// objects with size > 10 are multichunk.

int bs = OAClient.getInstance().blockSize;
OAClient.getInstance().setMaxChunkSize(10*bs);
Coordinator co = Coordinator.getInstance();
int maxhits = co.getCacheMaxSize(data);
int storesize = 0;

int[] sizes = {1, 10, 20, 100, 
               maxhits-1, maxhits, maxhits+1, maxhits*2,
               1, 10, 100};

ECHO("Exercise coordinator's block cache (size = " + maxhits + ")");

void checkCacheHits(int expected) {
    int hits = co.getCacheHits(data);
    if (hits != expected) {
        throw new RuntimeException("Expected " + expected + 
                                   " cache hits but got " + hits);
    } else {
        //ECHO("Found expected number of cache hits: " + hits);
    }
    ECHO(co.getCacheStats(data));
}

void checkCacheDeletes(int expected) {
    int deletes = co.getCacheDeletes(data);
    if (deletes != expected) {
        throw new RuntimeException("Expected " + expected + 
                                   " cache deletes but got " + deletes);
    } else {
        //ECHO("Found expected number of cache deletes: " + hits);
    }
    ECHO(co.getCacheStats(data));
}

// *********** MAIN ************

for (int i = 0; i < sizes.length; i++) {
    storesize = sizes[i];
    ECHO("\nTest iteration " + (++iter) + " size = " + storesize + " blocks \n");
    
    co.resetCacheStats(data);

    ECHO("--> Store object of size = " + storesize*bs);
    STORE(1, storesize*bs);
    
    checkCacheHits(0);
    
    ECHO("--> Retrieve stored object");
    RETRIEVE(1, true);
   
    checkCacheHits(0);
 
    ECHO("--> Retrieve again from cache");
    RETRIEVE(1, true);

    // expect hits if object fits in the cache
    int hits = (storesize < maxhits) ? storesize : 0;
    checkCacheHits(hits);
    co.resetCacheStats(data); // to zero out hit counter

    ECHO("--> Retrieve again from cache");
    RETRIEVE(1, true);

    // expect hits if object fits in the cache
    int hits = (storesize < maxhits) ? storesize : 0;
    checkCacheHits(hits);

    ECHO("--> Delete cached object");
    DELETE(1, true);
    
    // all blocks for this oid should be deleted from cache
    int deletes = (storesize < maxhits) ? storesize : (maxhits-1);
    checkCacheDeletes(deletes);

    co.resetCacheStats(data); // to zero out hit counter

    ECHO("--> Attempt to retrieve deleted object - will fail");
    RETRIEVE(1, false);

    checkCacheHits(0); // no hits because it's deleted
}

// Special testcase where 2 objects occupy the cache half-and-half

co.resetCacheStats(data); // to zero out hit counter

storesize = (maxhits-1) / 2;
ECHO("\nTest iteration " + (++iter) + " size = " + storesize + " blocks \n");

ECHO("--> Store two objects, each takes up half of the cache");
STORE(1, storesize*bs);
STORE(2, storesize*bs);
checkCacheHits(0); // store doesn't populate the cache

ECHO("--> Retrieve both objects to populate the cache");
RETRIEVE(1, true);
ECHO("After first retrieve 1: " + co.getCacheStats(data, true));
RETRIEVE(2, true);
ECHO("After first retrieve 2: " + co.getCacheStats(data, true));
checkCacheHits(0); // no hits on first retrieve

for (int i=0; i<3; i++) {
    co.resetCacheStats(data); // to zero out hit counter
    ECHO("--> Retrieve both objects again from the cache, attempt " + i);
    RETRIEVE(1, true);
    //    ECHO("Attempt " + i + " after retrieve 1: " + co.getCacheStats(data, true));
    RETRIEVE(2, true);
    //    ECHO("Attempt " + i + " after retrieve 2: " + co.getCacheStats(data, true));
    checkCacheHits(storesize*2); // all blocks should have come from cache

    // note: there will be 1 miss per object, that's because OA will
    // look up 1 additional non-existent block past the end of the object.
}



