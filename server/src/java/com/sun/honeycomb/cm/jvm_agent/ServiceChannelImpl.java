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



package com.sun.honeycomb.cm.jvm_agent;

import java.util.logging.Logger;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
import java.io.IOException;

import com.sun.honeycomb.cm.ManagedService;

final class ServiceChannelImpl implements ManagedService.ServiceChannel {

    static final transient Logger LOG = Logger.getLogger(ServiceChannelImpl.class.getName());
    
    private final CMSAP sap;
    private final boolean isServer;
    
    ServiceChannelImpl(CMSAP sap, boolean isServer) {
        this.sap = sap;
        this.isServer = isServer;
    }
    
    public int read(ByteBuffer dst) throws IOException {
        SocketChannel sc = sap.channel();
        if (sc == null) {
            throw new IOException("socket channel invalid");
        }
        return sc.read(dst);
    }
    
    public int write(ByteBuffer src) throws IOException {
        SocketChannel sc = sap.channel();
        if (sc == null) {
            throw new IOException("socket channel invalid");
        }
        return sc.write(src);
    }
    
    public boolean isOpen() {
        SocketChannel sc = sap.channel();
        if (sc == null) {
            return false;
        }
        return sc.isOpen();
    }
    
    public void close() throws IOException {
        if (isOpen()) {
            SocketChannel sc = getChannel();
            sc.socket().setTcpNoDelay(true);
            if (isServer) {
                CMAgent.addToDispatcher(sap);
            } else {
                sc.configureBlocking(true);
                sap.disconnect();
            }
        } else {
            LOG.warning("channel " + this + " is not open");
        }
    }
    
    public SocketChannel getChannel() throws IOException {
        SocketChannel sc = sap.channel();
        if (sc == null) {
            throw new IOException("socket channel invalid");
        }
        return sc;
    }
    
    public void flush() throws IOException {
    }        
}
