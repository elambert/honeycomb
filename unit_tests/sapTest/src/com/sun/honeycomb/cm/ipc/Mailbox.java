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



package com.sun.honeycomb.cm.ipc;

import java.io.IOException;
import java.util.logging.Logger;
import java.io.OutputStream;

public class Mailbox 
    extends OutputStream {

    private static final Logger LOG = Logger.getLogger(Mailbox.class.getName());

    private static final int STATE_DISABLED     = 0;
    private static final int STATE_INIT         = 1;
    private static final int STATE_READY        = 2;
    private static final int STATE_RUNNING      = 3;

    private int state;

    public Mailbox(String tag, boolean manager)
        throws IOException {
        state = STATE_DISABLED;
    }
    
    public boolean isRunning() {
        return(state == STATE_RUNNING);
    }

    public boolean isReady() {
        return(state == STATE_READY);
    }

    public boolean isDisabled() {
        return(state == STATE_DISABLED);
    }

    public boolean isInit() {
        return(state == STATE_INIT);
    }

    public void setReady() {
        state = STATE_READY;
    }

    public void setRunning() {
        state = STATE_RUNNING;
    }

    public void sync() {
    }

    public boolean stateCheck(Mailbox.Listener service) {
        if (state < STATE_READY) {
            service.doInit();
        } else if (state < STATE_RUNNING) {
            service.doStart();
        }
        
        return(true);
    }

    public void write(int b)
        throws IOException {
    }

    public int size() {
        return(0);
    }

    static public interface Listener {
        void doInit();
        void doStart();
    }
}

