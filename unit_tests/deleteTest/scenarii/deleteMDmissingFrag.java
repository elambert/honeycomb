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

ECHO("Test delete with missing frags in MD object");

storesize = 10;
badfragno = 5; // doesn't matter which frag

ECHO("--> Store object of size " + storesize);
STORE(1, storesize);
DEREF(1,0);

ECHO("--> Remove one frag from MD obj");
FRAGREMOVE(1, badfragno, 0);

ECHO("--> Delete the object while frag is missing...");
DELETE(1, true);

ECHO("--> Try retrieve to complete the delete");
RETRIEVE(1, false);

ECHO("--> Check deleted status of MD object");
FRAGISDELETED(1, Constants.ALL_FRAGS, true);

// these ops should make data frag deleted, but don't
ECHO("--> Retrieve should complete delete but doesn't");
RETRIEVE(1, false);
RETRIEVE(0, false);

// Even after recovery of MD frag, retrieve doesn't complete delete
ECHO("--> Recover missing MD frag");
RECOVER(1, badfragno, 0); 

ECHO("--> Retrieve should complete delete but doesn't");
RETRIEVE(1, false);
RETRIEVE(0, false);

// recovery of data frag makes it deleted,
// and would make the test pass - skipping
//RECOVER(0, badfragno, 0); // makes deleted!!

ECHO("--> Check deleted status and ref cnt");
LS(0);
FRAGISDELETED(0, Constants.ALL_FRAGS, true);
REFCNTCHECK(0, Constants.ALL_FRAGS, 0, 1);
