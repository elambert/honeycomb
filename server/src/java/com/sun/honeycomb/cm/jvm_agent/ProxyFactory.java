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

import com.sun.honeycomb.cm.ManagedService;
import java.util.Hashtable;
import java.util.logging.Logger;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.Enumeration;

final class ProxyFactory {

    public static final Logger logger =
        Logger.getLogger(ProxyFactory.class.getName());

    private static Hashtable cache = new Hashtable();

    private ProxyFactory() {
    }
        
    /**
     * return the proxy object for the given ManagedService class
     * running on the given node.
     */
    static ManagedService.ProxyObject proxyFor(int nodeid, String tag) {
                
        ProxyService service = null;
        try {
            synchronized (cache) {
                service = (ProxyService) cache.get(tag);
                if (service == null) {
                    service = new ProxyService(nodeid, tag);
                    cache.put(tag, service);
                }
            }
            return service.getProxy();
            
        } catch (CMAException e) {
            logger.fine("ClusterMgmt - Distributed IPC failure for " + 
                        e.getMessage() + " : " + e.getCause());
            if (service != null) {
                synchronized (cache) {
                    cache.remove(tag);
                }
                service.destroy();
            }
        }
        return null;
    }
}
