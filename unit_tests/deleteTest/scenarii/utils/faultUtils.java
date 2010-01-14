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



import com.sun.honeycomb.oa.Fault;
import com.sun.honeycomb.oa.FaultEvent;
import com.sun.honeycomb.oa.FragmentFault;
import com.sun.honeycomb.oa.FragmentFaultEvent;
import com.sun.honeycomb.oa.daal.nfs.FaultyNfsDAAL;
import com.sun.honeycomb.oa.FaultManager;
import com.sun.honeycomb.delete.Constants;

import java.util.Collection;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Set;

/**********************************************************************/
Fault addFragmentFault(String name,
                       FaultEvent event,
                       FaultyNfsDAAL.Operation operation,
                       Fault.FaultType faultType,
                       int fragment,
                       int chunk,
                       byte objType) {
    return addFragmentFault(name, event, operation, faultType, fragment, chunk,
                            objType, Integer.MAX_VALUE);
}

/**********************************************************************/
Fault addFragmentFault(String name,
                       FaultEvent event,
                       FaultyNfsDAAL.Operation operation,
                       Fault.FaultType faultType,
                       int fragment,
                       int chunk,
                       byte objType,
                       int triggerCount) {
    return addFragmentFault(name, event, operation, faultType, fragment,
                            chunk, objType, -1, -1, triggerCount);
}

/**********************************************************************/
Fault addFragmentFault(String name,
                       FaultEvent event,
                       FaultyNfsDAAL.Operation operation,
                       Fault.FaultType faultType,
                       int fragment,
                       int chunk,
                       byte objType,
                       long start,
                       long offset,
                       int triggerCount) {
    FragmentFault.FragmentInfo fragInfo
        = new FragmentFault.FragmentInfo(fragment, chunk, objType);
    fragInfo.start = start;
    fragInfo.offset = offset;
    FragmentFault fault = new FragmentFault(name, event, faultType, fragInfo,
                                            triggerCount);
    FaultManager.insertFault(operation.toString(), fault);
    return fault;
}

/**********************************************************************/
Collection addFragmentFaults(String name,
                             FaultEvent event,
                             FaultyNfsDAAL.Operation operation,
                             Fault.FaultType faultType,
                             Collection fragments,
                             int chunk,
                             byte objType) {
    Set faults = new HashSet();
    for (Iterator it = fragments.iterator(); it.hasNext(); ) {
        faults.add(addFragmentFault(name, event, operation, faultType,
                                    ((Integer) it.next()).intValue(),
                                    chunk, objType));
    }
    return faults;
}

/**********************************************************************
 * Number of chunks is determined by size of object stored. Inserting
 * fault on first 'chunkCount' chunks.
 */
Collection addFragmentFaultAllChunks(String name,
                                     FaultEvent event,
                                     FaultyNfsDAAL.Operation operation,
                                     Fault.FaultType faultType,
                                     int fragment,
                                     int chunkCount,
                                     byte objType) {
    Set faults = new HashSet();
    for (int chunk = 0; chunk < chunkCount; chunk++) {
        faults.add(addFragmentFault(name, event, operation, faultType,
                                    fragment, chunk, objType));
    }
    return faults;
}

/**********************************************************************
 * Number of chunks is determined by size of object stored. Inserting
 * fault on first 'chunkCount' chunks.
 */
Collection addFragmentFaultAllChunks(String name,
                                     FaultEvent event,
                                     FaultyNfsDAAL.Operation operation,
                                     Fault.FaultType faultType,
                                     Collection fragments,
                                     int chunkCount,
                                     byte objType) {
    return addFragmentFaultAllChunks(name, event, operation, faultType, fragments,
                                     chunkCount, objType, Integer.MAX_VALUE);
}
/**********************************************************************
 * Number of chunks is determined by size of object stored. Inserting
 * fault on first 'chunkCount' chunks.
 */
Collection addFragmentFaultAllChunks(String name,
                                     FaultEvent event,
                                     FaultyNfsDAAL.Operation operation,
                                     Fault.FaultType faultType,
                                     Collection fragments,
                                     int chunkCount,
                                     byte objType,
                                     int triggerCount) {
    Set faults = new HashSet();
    for (int chunk = 0; chunk < chunkCount; chunk++) {
        for (Iterator it = fragments.iterator(); it.hasNext(); ) {
            faults.add(addFragmentFault(name, event, operation, faultType,
                                        ((Integer) it.next()).intValue(),
                                        chunk, objType, triggerCount));
        }
    }
    return faults;
}

/**********************************************************************/
Collection addFragmentFaultAllChunksAllFragments(String name,
                                                 FaultEvent event,
                                                 FaultyNfsDAAL.Operation operation,
                                                 Fault.FaultType faultType,
                                                 int chunkCount,
                                                 byte objType) {
    Set faults = new HashSet();
    for (int frag = 0; frag < Constants.reliability.getTotalFragCount();
         frag++) {
        faults.addAll(addFragmentFaultAllChunks(name, event, operation, faultType,
                                                frag, chunkCount, objType));
    }
    return faults;
}

/**********************************************************************/
Fault addInterruptFault(String name,
                        FaultEvent event,
                        FaultyNfsDAAL.Operation operation,
                        Fault.FaultType faultType,
                        int fragment,
                        int triggerCount) {
    ThreadInterruptFault fault
        = new ThreadInterruptFault(name, event, faultType);
    fault.setFaultyFragment(fragment);
    fault.setTriggerCount(triggerCount);
    FaultManager.insertFault(operation.toString(), fault);
    return fault;
}

/**********************************************************************/
Collection addInterruptFaults(String name,
                              FaultEvent event,
                              FaultyNfsDAAL.Operation operation,
                              Fault.FaultType faultType,
                              Collection fragments,
                              int triggerCount) {
    Set faults = new HashSet();
    for (Iterator it = fragments.iterator(); it.hasNext(); ) {
        faults.add(addInterruptFault(name, event, operation, faultType,
                                     ((Integer) it.next()).intValue(),
                                     triggerCount));
    }
    return faults;
}

/**********************************************************************/
void assertTriggered(Fault fault) {
    fault.assertTriggered();
}

/**********************************************************************/
void assertTriggered(Collection faults) {
    for (Iterator it = faults.iterator(); it.hasNext(); ) {
        assertTriggered((Fault) it.next());
    }
}

/**********************************************************************/
void assertNotTriggered(Fault fault) {
    if (fault.triggered()) {
        throw new IllegalStateException("Fault " + fault + " triggered");
    }
}

/**********************************************************************/
void assertNotTriggered(Collection faults) {
    for (Iterator it = faults.iterator(); it.hasNext(); ) {
        assertNotTriggered((Fault) it.next());
    }
}

/**********************************************************************/
void assertMinTriggered(Collection faults, int count) {
    int triggered = 0;
    for (Iterator it = faults.iterator(); it.hasNext(); ) {
        if (((Fault) it.next()).triggered()) {
            triggered++;
        }
    }
    if (triggered < count) {
        throw new IllegalStateException("Less than expected faults triggered "
                                        + "(expected: " + count + " got: "
                                        + triggered);
    }
}

/**********************************************************************/
void resetFaults(String clazz) {
    FaultManager.resetFaults(clazz);
}

/**********************************************************************/
void removeFaults(String clazz, FaultEvent event) {
    FaultManager.removeFaults(clazz, event);
}

/**********************************************************************/
void removeAllFaults(String clazz) {
    FaultManager.removeFaults(clazz);
}

/**********************************************************************/
void removeAllFaults() {
    FaultManager.removeAllFaults();
}
