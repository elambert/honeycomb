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

import com.sun.honeycomb.emd.common.EMDException;
import java.util.LinkedList;

public class InMemoryMDStream
    implements MDInputStream, MDOutputStream {
    
    private static final MDInputStream.EndOfStream END_OF_STREAM
        = new InMemoryEndOfStream();
    
    private LinkedList objects;
    private Object lastObject;

    private static class InMemoryEndOfStream
        implements MDInputStream.EndOfStream {
        private InMemoryEndOfStream() {
        }
    }
    
    public InMemoryMDStream() {
        objects = new LinkedList();
        lastObject = null;
    }
    
    public void sendObject(Object o) {
        objects.add(o);
        lastObject = o;
    }
    
    public Object getObject() 
        throws EMDException {
        if (objects.size() == 0) {
            return(END_OF_STREAM);
        }
        
        Object result = objects.removeFirst();
        return(result);
    }

    public int size() {
        return(objects.size());
    }

    public Object getLastObject() {
        return(lastObject);
    }
    public void clearLastObject() {
        lastObject = null;
    }
}
