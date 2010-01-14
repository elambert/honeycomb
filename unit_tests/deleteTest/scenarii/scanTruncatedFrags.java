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

/* "store a 500 byte file, truncate some frags to length 1, detect that
 * it is corrupted, then recover"
 */

ECHO("store 500 byte file");
STORE(1, 500);
DEREF(1, 0);
REFCNTCHECK(0, Constants.ALL_FRAGS, 1, 1);  // data
REFCNTCHECK(1, Constants.ALL_FRAGS, -1, -1); // metadata

ECHO("truncate and verify (for 1 data frag)");

ECHO("truncate data frag 0 to 1 byte file");
TRUNCATEFRAG(0, 0, 0, 1);

ECHO("scan the frag");
SCANFRAG(0, 0, 0);

ECHO("verify that frag is deleted");
FRAGABSENT(0, 0, 0);  // data

ECHO("retrieve, should work");
RETRIEVE(0, true);

ECHO("recover");
RECOVER(0, 0, 0);

ECHO("verify that frag is regenerated");
REFCNTCHECK(0, Constants.ALL_FRAGS, 1, 1);  // data

ECHO("retrieve");
RETRIEVE(0, true);

/**********************************************************************/
ECHO("truncate and verify (for 1 metadata frag)");

ECHO("truncate metadata frag 0 to 1 byte file");
TRUNCATEFRAG(1, 0, 0, 1);

ECHO("scan the frag");
SCANFRAG(1, 0, 0);

ECHO("verify that frag is deleted");
FRAGABSENT(1, 0, 0);

ECHO("retrieve, should work");
RETRIEVE(1, true);

ECHO("recover");
RECOVER(1, 0, 0);

ECHO("verify that frag is regenerated");
REFCNTCHECK(1, Constants.ALL_FRAGS, -1, -1);

ECHO("retrieve");
RETRIEVE(1, true);

/**********************************************************************/
ECHO("truncate and verify (for 2 data frag)");

ECHO("truncate data frag 0 and frag 1 to 1 byte file");
TRUNCATEFRAG(0, 0, 0, 1);
TRUNCATEFRAG(0, 1, 0, 1);

ECHO("scan the frag");
SCANFRAG(0, 0, 0);
SCANFRAG(0, 1, 0);

ECHO("verify that frag is deleted");
FRAGABSENT(0, 0, 0);
FRAGABSENT(0, 1, 0);

ECHO("retrieve, should work");
RETRIEVE(0, true);

ECHO("recover");
RECOVER(0, 0, 0);
RECOVER(0, 1, 0);

ECHO("verify that frag is regenerated");
REFCNTCHECK(0, Constants.ALL_FRAGS, 1, 1); 

ECHO("retrieve");
RETRIEVE(0, true);

/**********************************************************************/
ECHO("truncate and verify (for 2 metadata frag)");

ECHO("truncate metadata frag 0 and frag 1 to 1 byte file");
TRUNCATEFRAG(1, 0, 0, 1);
TRUNCATEFRAG(1, 1, 0, 1);

ECHO("scan the frag");
SCANFRAG(1, 0, 0);
SCANFRAG(1, 1, 0);

ECHO("verify that frag is deleted");
FRAGABSENT(1, 0, 0);  // metadata
FRAGABSENT(1, 1, 0);  // metadata

ECHO("retrieve, should work");
RETRIEVE(1, true);

ECHO("recover");
RECOVER(1, 0, 0);
RECOVER(1, 1, 0);

ECHO("verify that frag is regenerated");
REFCNTCHECK(1, Constants.ALL_FRAGS, -1, -1);

ECHO("retrieve");
RETRIEVE(1, true);

