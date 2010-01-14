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



package com.sun.honeycomb.emd.remote;

import java.util.logging.Logger;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.emd.common.EMDException;
import java.util.LinkedList;

public class MergeEngine {
    private static final Logger LOG = Logger.getLogger("MergeEngine");

    private LinkedList list;
    
    public MergeEngine(StreamHead[] inputs) 
        throws EMDException {
        list = new LinkedList();
        for (int i=0; i<inputs.length; i++) {
            if ((inputs[i] != null) 
                && (inputs[i].current() != null)) {
                insert(inputs[i]);
            }
        }
    }

    private void insert(StreamHead elem) {
        int index;

        for (index=0; index<list.size(); index++) {
            if (elem.compareTo((StreamHead)list.get(index)) <= 0) {
                break;
            }
        }
        
        list.add(index, elem);
    }

    public Object getFirst() 
        throws EMDException {
        if (list.size() == 0) {
            return(null);
        }

        StreamHead firstHead = (StreamHead)list.removeFirst();
        Object result = firstHead.current();
        StreamHead head;

        while ( (list.size() != 0)
                && (((StreamHead)list.getFirst()).compareTo(firstHead) == 0) ) {
            head = (StreamHead)list.removeFirst();
            head.moveToNext();
            if (head.current() != null) {
                insert(head);
            }
        }

        // Reinsert the first head
        firstHead.moveToNext();
        if (firstHead.current() != null) {
            insert(firstHead);
        }

        return(result);
    }
}   
