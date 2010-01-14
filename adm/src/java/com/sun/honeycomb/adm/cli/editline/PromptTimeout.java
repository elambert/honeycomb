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



package com.sun.honeycomb.adm.cli.editline;

import com.sun.honeycomb.adm.client.AdminClient;
import com.sun.honeycomb.adm.client.AdminClientImpl;


public class PromptTimeout extends Thread {
    
    private long    _length;
    private Thread  _parent;
    private boolean _keepRunning;
    private boolean _doTimeout;

    public PromptTimeout (long millis, Thread promptThread) {
        super("CliTimeoutThread");
        setDaemon(true);
        _length = millis;
        _parent = promptThread;
        _keepRunning = true;
        _doTimeout = false;
    }

    public void arm() {
        _doTimeout = true;
    }

    public void reset () {
        _doTimeout = false;
        //synchronized (this) {
            // interrupt ourselves from sleep
            this.interrupt();
        //}
    }

    public void run () {
        while (_keepRunning) {
            try {
                sleep (_length);
            } catch (InterruptedException ie) {
                // if we get an interrupt, it means we recieved user
                // input, so loop back up and sleep
                continue;
            }

            if (_doTimeout) {
                try {
                    AdminClient api =new AdminClientImpl();
                    api.logout();
                } catch (Exception e) {                
                   // TODO: log something
                }

                System.out.println ("\nConnection timed out, exiting:"+(_length/1000)/60);
                System.exit(1); // this is dirty, and should interrupt the
                // parent to be cleaner, imo, but that doesn't
                // seem to work.
                
            }
        }
    }
}
