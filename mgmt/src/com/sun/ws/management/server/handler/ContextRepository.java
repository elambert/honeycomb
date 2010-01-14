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



package com.sun.ws.management.server.handler;

import java.math.BigInteger;
import com.sun.honeycomb.mgmt.common.MgmtException;

public class ContextRepository {
    
    private static final int REPOSITORY_SIZE = 7;

    private static ContextRepository instance = null;

    public synchronized  static ContextRepository getInstance() 
        throws MgmtException {
        if (instance == null) {
            instance = new ContextRepository();
        }
        return(instance);
    }

    private Thread threads[];
    private InteractiveContext contextes[];

    private ContextRepository() 
        throws MgmtException {
        contextes = new InteractiveContext[REPOSITORY_SIZE];
        threads = new Thread[REPOSITORY_SIZE];

        for (int i=0; i<REPOSITORY_SIZE; i++) {
            contextes[i] = new InteractiveContext(i);
            threads[i] = new Thread(contextes[i]);
            threads[i].start();
        }
    }

    public synchronized InteractiveContext getFreeContext() {
        for (int i=0; i<REPOSITORY_SIZE; i++) {
            if (!contextes[i].isBusy()) {
                contextes[i].setBusy();
                return(contextes[i]);
            }
        }
        return(null);
    }

    public InteractiveContext matchContext(BigInteger cookie) 
        throws MgmtException {
        int slot = cookie.shiftRight(32).intValue();
        if ((slot < 0) || (slot >= REPOSITORY_SIZE)) {
            throw new MgmtException("Cookie ["+cookie+"] is invalid. Bad slot ["+slot+"]");
        }
       InteractiveContext candidate = contextes[slot];
        if (candidate.matches(cookie)) {
            return(candidate);
        }
        throw new MgmtException("Cookie ["+cookie+"] is invalid");
    }

}
        
            
            
        