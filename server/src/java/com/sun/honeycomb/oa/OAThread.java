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

import java.util.logging.Logger;
import java.util.logging.Level;

public class OAThread extends Thread {
    private static Logger LOG = Logger.getLogger(OAThreads.class.getName());

    OAThreads.RunnableCode code;
    private OAThreads.ThreadDone callback;
    private boolean ready;
    private boolean running;
    public Exception exception;
        
    OAThread(OAThreads.ThreadDone nCallback, String tag) {
        super(tag);
        code = null;
        callback = nCallback;
        running = true;
	exception = null;
        ready = false;
	
    }

    public void setReady() {
        ready = true;
    }

    public void setCode(OAThreads.RunnableCode nCode) {
        code = nCode;
    }
    
    public void run() {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Thread ["+Thread.currentThread().getName()+"] has been started");
        }

        while (running) {
            synchronized (this) {
                while ((running) && (!ready)) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
                    
                try {
                    if (code != null) {
                        code.run();
                    }
                } catch (Exception e) {
                    LOG.log(Level.SEVERE,
                            "Got an unexpected exception in an OA thread ["+
                            e.getMessage()+"]",
                            e);
                } finally {
		    try {
			code.checkStatus();
		    } catch (Exception e) {
			exception = e;
		    }
                    code = null;
                    ready = false;
                    callback.done();
                }
            }
        }
    }
}
