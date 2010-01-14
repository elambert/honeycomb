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

import com.sun.honeycomb.cm.ServiceManager;

import java.util.logging.Logger;
import java.util.logging.Level;

public class OAThreadPool
    implements OAThreads.ThreadDone {

    private static Logger LOG = Logger.getLogger(OAThreads.class.getName());

    protected OAThread[] threads;
    private OAThreads.RunnableCode[] code;
    boolean[] initialized;
    private int nbRunning;
    private int size;
    private String tag;

    OAThreadPool(String nTag,
                 int nSize) {
        tag = mySvcName() + "." + nTag;
        size = nSize;
        threads = new OAThread[size];
        code = null;
        initialized = new boolean[size];
        for (int i=0; i<size; i++) {
            threads[i] = new OAThread(this, tag + "-" + i);
            threads[i].start();
        }
        nbRunning = 0;

        LOG.info("New OAThreadPool " + tag + "[" + size + "] created");
    }

    public void setCode(OAThreads.RunnableCode[] nCode)
        throws OAException {

        code = nCode;

        if (code.length != threads.length) {
            throw new OAException("There is a mismatch between the thread pool size ["+
                                  threads.length+"] and the code to be run ["+
                                  code.length+"]");
        }

        for (int i=0; i<threads.length; i++) {
            if (code.length <= i) {
                threads[i].code = null;
            } else {
                threads[i].code = code[i];
            }

        }
    }

    public OAThreads.RunnableCode[] getCode() {
        return(code);
    }

    public void initInitializedFlags() {
        for (int f=0; f<initialized.length; f++) {
            initialized[f] = false;
        }
    }

    public int countValidThreads() {
        int result = 0;

        for (int f=0; f<initialized.length; f++) {
            if (initialized[f]) {
                result++;
            }
        }

        return(result);
    }

    public synchronized void execute() {
        nbRunning = 0;

        for (int f=0; f<threads.length; f++) {
            if (initialized[f]) {
                synchronized (threads[f]) {
                    threads[f].setReady();
                    threads[f].notify();
                }
                nbRunning++;
            }
        }
    }

    public synchronized void done() {
        nbRunning--;
        if (nbRunning == 0) {
            notify();
        }
    }

    /*
     * Returns the number of threads that successfully completed
     */

    public synchronized int waitForCompletion() {
        int result = 0;

        while (nbRunning > 0) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }

        for (int f=0; f<threads.length; f++) {
            if (initialized[f]) {
                if(threads[f].exception != null) {
                    LOG.log(Level.WARNING,
                            "Exception while executing code on frag "
                            + f, threads[f].exception);
                    threads[f].exception = null;
                } else {
                    result++;
                }
            }
        }

        return(result);
    }

    private String mySvcName() {
        Class svcClass = ServiceManager.currentManagedService();
        if (svcClass == null)
            return "NULL";

        String name = svcClass.getName();

        // Return just the basename instead of the fully qualified name
        return name.replaceFirst("^.*\\.", "");
    }

}
