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
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.ErrorInjectionState;
import com.sun.honeycomb.oa.FaultPoint;

ECHO("Test delete after hiding the data object."); 

ECHO("Storing object 0");
STORE(0, 1024);
DEREF(0,1);

ECHO("Hiding 3 fragments.");
FRAGREMOVE(1, 0);
FRAGREMOVE(1, 1);
FRAGREMOVE(1, 2);

ECHO("Trying to delete object 0 knowing it will fail.");
DELETE(0,false);

ECHO("Storing object 2");
STORE(2, 1024);
DEREF(2,3);

ECHO("Hiding 5 fragments.");
FRAGREMOVE(3, 0);
FRAGREMOVE(3, 1);
FRAGREMOVE(3, 2);
FRAGREMOVE(3, 3);
FRAGREMOVE(3, 4);

ECHO("Trying to delete object 2 knowing it will fail.");
DELETE(2,false);

ECHO("Success.");
