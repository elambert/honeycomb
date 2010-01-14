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



package com.sun.honeycomb.cm.err_inj;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;

/**
 * Public definition of the ErrInj managed service.
 * It is an interface that extends ManagedService and
 * exports its own public API and proxy object.
 */
public interface ErrInjManagedService 
    extends ManagedService.RemoteInvocation, ManagedService {
    /*
     * the errinj managed service exports the following public API.
     */
    void voidCall() throws ManagedServiceException;

    boolean checkedCall(Proxy proxy) throws ManagedServiceException;

    void throwException(String msg) throws ManagedServiceException;

    void killJVM(boolean server, String msg) throws ManagedServiceException;

    void eatMemory(boolean server, String msg) throws ManagedServiceException;

    public void eatFDs(boolean server, String msg) throws ManagedServiceException;

    public void eatThreads(boolean server, String msg) throws ManagedServiceException;

    /*
     * the example managed service exports the following proxy object.
     */
    public class Proxy extends ManagedService.ProxyObject {

        private int value;

        Proxy(int val) {
            value = val;
        }

        public int getValue() {
            return value;
        }

        public String toString() {
            return super.toString() + " val: " + value;
        }
    }
}
