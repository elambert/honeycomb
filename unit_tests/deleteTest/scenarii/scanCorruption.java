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

ECHO("store a 500 byte file, corrupt the 1st 15 bytes of frag 0 it, detect it is corrupted, then try to recover it");

ECHO("---> store 500 byte file");
STORE(1, 500);
DEREF(1, 0);
REFCNTCHECK(0, Constants.ALL_FRAGS, 1, 1);
REFCNTCHECK(1, Constants.ALL_FRAGS, -1, -1);

ECHO("---> corrupt frag 0 (1st 15 bytes)");
CORRUPTFRAG(0, 0, 0, 15);

ECHO("--> retrieve (to detect corruption and show we can work around)");
RETRIEVE(0, true);

//ECHO("--> recover frag 0");
//RECOVER(0, 0, 0);

ECHO("--> scan and heal frag 0");
SCANFRAG(0, 0, 0);

ECHO("--> retrieve to show the recovery worked");
RETRIEVE(0, true);


ECHO("store a 500 byte file, corrupt the 1st 15 bytes of frag 1 it, detect it is corrupted, then try to recover it");

ECHO("---> store 500 byte file");
STORE(1, 500);
DEREF(1, 0);
REFCNTCHECK(0, Constants.ALL_FRAGS, 1, 1);
REFCNTCHECK(1, Constants.ALL_FRAGS, -1, -1);

ECHO("---> corrupt frag 1 (1st 15 bytes)");
CORRUPTFRAG(0, 1, 0, 15);

ECHO("--> retrieve (to detect corruption and show we can work around)");
RETRIEVE(0, true);

//ECHO("--> recover frag 1");
//RECOVER(0, 1, 0);

ECHO("--> scan and heal frag 1");
SCANFRAG(0, 1, 0);
FRAGABSENT(0, 1, 0);

ECHO("--> retrieve to show the recovery worked");
RETRIEVE(0, true);

ECHO("--> verify that frag is regenerated");
REFCNTCHECK(0, Constants.ALL_FRAGS, 1, 1);
REFCNTCHECK(1, Constants.ALL_FRAGS, -1, -1);

