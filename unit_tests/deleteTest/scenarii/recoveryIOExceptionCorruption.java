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
import com.sun.honeycomb.oa.FragmentFaultEvent;
import com.sun.honeycomb.oa.FragmentFault;
import com.sun.honeycomb.oa.daal.nfs.FaultyNfsDAAL;

import java.util.Set;
import java.util.HashSet;

new bsh.Interpreter().source(System.getProperty("deletefmk.script.dir") +
                             File.separatorChar + "utils" + File.separatorChar +
                             "faultUtils.java", this.namespace);

storesize = 684;
recoverFrag = 0;
chunkId = 0;
interruptFrag = 1;
data = 0;
md = 1;

ECHO("Store a " + storeszie + " data object...");
STORE(md, storesize, true);
DEREF(md,data);

ECHO("Remove fragment " + recoverFrag+ " of it...");
FRAGREMOVE(data, recoverFrag, chunkId);

ECHO("Corrupt the 1st " + corruptFirstBytes + " of fragment " + corruptFrag +
     " of it...");
// XXX how is the corruption accomplished here?

ECHO("Configure an IO failure such that read of fragment " + interruptFrag +
     " during recovery take too long and get interrupted...");

// XXX why is this a delay fault?

Set faults = new HashSet();
faults.add(addFragmentFault("read fault",
                            FragmentFaultEvent.RETRIEVE,
                            FaultyNfsDAAL.READ,
                            FragmentFault.DELAY_ERROR,
                            interruptFrag,
                            0,
                            OAClient.OBJECT_TYPE_DATA,
                            0,
                            1,
                            Integer.MAX_VALUE));
faults.add(addFragmentFault("read fault",
                            FragmentFaultEvent.RETRIEVE,
                            FaultyNfsDAAL.READ,
                            FragmentFault.DELAY_ERROR,
                            interruptFrag,
                            0,
                            OAClient.OBJECT_TYPE_METADATA,
                            0,
                            1,
                            Integer.MAX_VALUE));

ECHO("Now recover fragment " + recoverFrag + " in the presence of that failure...");
RECOVER(data, recoverFrag, chunkId);

ECHO("Compare the recovered fragment 0 to the one we moved, looking for corruption");
// TODO - compare old and new fragments here

ECHO("Retrieve the object following the corruption");
RETRIEVE(0, true);
assertMinTriggered(faults, 1);
