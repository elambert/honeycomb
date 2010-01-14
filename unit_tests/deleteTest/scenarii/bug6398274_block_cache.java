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

import java.util.logging.Logger;
import java.util.logging.Level;

private static final Logger classLogger = Logger.getLogger("com.sun.honeycomb.coordinator.BlockCache");
classLogger.setLevel(Level.FINEST);
private static final Logger classLogger1 = Logger.getLogger("com.sun.honeycomb.coordinator.Coordinator");
classLogger1.setLevel(Level.FINEST);

chunksize = com.sun.honeycomb.delete.Constants.MAX_CHUNK_SIZE;
storesize = chunksize * 60;

ECHO("Test for bug 6398274: attempt to check in a buffer that has already been checked in");

// Essence of the bug: when we deal with multichunk objects, OA stack can
// modify the OID object to have different chunk number and layout id.
// If this OID was used to insert into block cache, the modification would
// cause the key of the cache entries to change too, invalidating them.
// The fix was to clone the OID for insertion into block cache.

ECHO("----> Store data and md obj for size " + storesize);
STORE(1, storesize);
DEREF(1, 0);

ECHO("--> Retrieve data obj");
RETRIEVE(0, true);
ECHO("--> Retrieve md obj");
RETRIEVE(1, true);

ECHO("--> Add md obj");
ADDM(1, 2);

ECHO("--> Delete orig md obj");
DELETE(1, true);

ECHO("--> Retrieve data obj");
RETRIEVE(0, true);
ECHO("--> Retrieve new md obj");
RETRIEVE(2, true);

