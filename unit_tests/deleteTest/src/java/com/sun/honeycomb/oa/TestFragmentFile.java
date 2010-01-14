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



package com.sun.honeycomb.oa;

import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.oa.FragmentFaultEvent;
import com.sun.honeycomb.oa.FragmentFault;
import com.sun.honeycomb.oa.daal.nfs.FaultyNfsDAAL;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.oa.daal.DAAL;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ObjectCorruptedException;

import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.lang.reflect.Constructor;

public class TestFragmentFile extends FragmentFile {
    private static final Logger log
        = Logger.getLogger(TestFragmentFile.class.getName());

    private FaultEvent faultEvent = FragmentFaultEvent.NOOP;
    private Map eventFromOp = new HashMap();
    public boolean abortCreate = true;

    /**********************************************************************/
    public TestFragmentFile(NewObjectIdentifier oid, int fragNum, Disk disk) {
        super(oid, fragNum, disk);
    }

    /**********************************************************************/
    public TestFragmentFile() {
        super();
        eventFromOp.put(new Integer(OP_NOOP), FragmentFaultEvent.NOOP);
        eventFromOp.put(new Integer(OP_STORE), FragmentFaultEvent.STORE);
        eventFromOp.put(new Integer(OP_RETRIEVE), FragmentFaultEvent.RETRIEVE);
        eventFromOp.put(new Integer(OP_DELETE), FragmentFaultEvent.DELETE);
        eventFromOp.put(new Integer(OP_REFINC), FragmentFaultEvent.REFINC);
        eventFromOp.put(new Integer(OP_REFDEC), FragmentFaultEvent.REFDEC);
    }

    /**********************************************************************/
    protected DAAL instantiateDAAL(Disk disk, NewObjectIdentifier oid,
                                   int fragNum) {
        return new FaultyNfsDAAL(disk, oid, new Integer(fragNum), this);
    }

    /**********************************************************************/
    protected void setOp(int op) {
        super.setOp(op);
        faultEvent = (FragmentFaultEvent) eventFromOp.get(new Integer(op));
    }

    /**********************************************************************/
    public FaultEvent getFaultEvent() {
        return faultEvent;
    }

    /**********************************************************************/
    protected FragmentFile getFragmentFile(NewObjectIdentifier oid,
                                           int fragId, Disk disk) {
        return new TestFragmentFile(oid, fragId, disk);
    }

    /**********************************************************************/
    protected Constructor getDAALFactory() {
        return null;
    }

    /**********************************************************************/
    public long open(boolean checkFault)
        throws FragmentNotFoundException, DeletedFragmentException,
               ObjectCorruptedException, OAException {
        if (checkFault) {
            return open();
        } else {
            return super.open();
        }
    }

    /**********************************************************************/
    public void abortCreate() {
        //        if (abortCreate) {
        //    super.abortCreate();
        //} else {
            log.warning("When running unit tests, FragmentFiles do not cleanup " +
                        "themselves on abort in order to leave fragments behind, " +
                        "that would only be seen during real crash scenarios.");
            //}
    }
}
