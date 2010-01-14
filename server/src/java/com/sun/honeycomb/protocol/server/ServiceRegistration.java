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



package com.sun.honeycomb.protocol.server;

import java.util.Set;

import org.mortbay.http.HttpContext;

import com.sun.honeycomb.common.NewObjectIdentifier;


public interface ServiceRegistration
{
    public void registerContext(ContextRegistration context);

    public interface EventRegistrant {

        static final public int API_DELETE = 1;
        static final public int API_STORE = 2;
        static final public int API_RETRIEVE = 3;

        public boolean apiCallback(int evt, NewObjectIdentifier oid, NewObjectIdentifier doid);
    }

    public class ContextRegistration {

        static final public int DEFAULT_PORT = -1;

        private String namespace;
        private EventRegistrant reg;
        private Set evts;
        private HttpContext context;
        private int port;
        private boolean newListener;

        public ContextRegistration(String namespace,
                                   EventRegistrant reg,
                                   Set evts,
                                   HttpContext ctx,
                                   int port,
                                   boolean newListener) {
            this.namespace = namespace;
            this.reg = reg;
            this.evts = evts;
            this.context = ctx;
            this.port = port;
            this.newListener = newListener;
        }

        public ContextRegistration(String namespace,
                                   EventRegistrant reg,
                                   Set evts,
                                   HttpContext ctx,
                                   boolean newListener) {
            this(namespace, reg, evts, ctx, DEFAULT_PORT, newListener);
        }

        public ContextRegistration(String namespace,
                                   HttpContext ctx,
                                   boolean newListener) {
            this(namespace, null, null, ctx, DEFAULT_PORT, newListener);
        }
        
        public String getNamespace() {
            return namespace;
        }
        
        public EventRegistrant getRegistrant() {
            return reg;
        }

        public Set getEvents() {
            return evts;
        }

        public HttpContext getContext() {
            return context;
        }

        public int getPort() {
            return port;
        }

        public boolean isNewListener() {
            return newListener;
        }
    }
}
