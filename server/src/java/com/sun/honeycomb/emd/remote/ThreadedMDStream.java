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

import java.util.LinkedList;

public abstract class ThreadedMDStream
    implements MDInputStream, MDOutputStream, Runnable {

    /**********************************************************************
     *
     * EndOfStream API
     *
     **********************************************************************/

    public static class ThreadedEndOfStream
        implements EndOfStream {
        private ThreadedEndOfStream() {
        }
    }
        
    /**********************************************************************
     * 
     * Main class
     *
     **********************************************************************/

    private LinkedList buffer;
    private EndOfStream eos;
    private Object lastObject;

    public ThreadedMDStream() {
        buffer = new LinkedList();
        eos = null;
        lastObject = null;
    }
    
    public void sendObject(Object obj) {
        synchronized (buffer) {
            buffer.add(obj);
            lastObject = obj;
            buffer.notify();
        }
    }
    
    public void run() {
        executionBody();
        synchronized (buffer) {
            eos = new ThreadedEndOfStream();
            buffer.notify();
        }
    }

    public Object getObject() {
        synchronized (buffer) {
            while ((buffer.size() == 0)
                   && (eos == null)) {
                try {
                    buffer.wait();
                } catch (InterruptedException ignored) {
                }
            }

            if (buffer.size() > 0) {
                return(buffer.remove(0));
            }

            return(eos);
        }
    }

    public void clearLastObject() {
        lastObject = null;
    }

    public Object getLastObject() {
        return(lastObject);
    }

    /**********************************************************************
     *
     * Methods to be implemented by subclasses
     *
     **********************************************************************/

    protected abstract void executionBody();
}
