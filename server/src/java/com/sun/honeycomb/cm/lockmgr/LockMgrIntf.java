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



package com.sun.honeycomb.cm.lockmgr;

import com.sun.honeycomb.cm.ManagedServiceException;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.lockmgr.LockMgr.LockOwner;

public interface LockMgrIntf 
    extends ManagedService.RemoteInvocation, ManagedService {

    //
    // API to lock/unlock the fragments
    //
    public boolean tryLock(LockOwner newOwner, String lockName)
      throws ManagedServiceException;

    public void unlock(LockOwner expectedOwner, String fragName, boolean force) 
      throws ManagedServiceException;
    

    public class Proxy extends ManagedService.ProxyObject {
        
        public Proxy() {
        }

        static public LockMgrIntf getLockMgrAPI(int nodeid) {
            Proxy proxy = getProxy(nodeid);
            if (proxy == null) {
                return null;
            }
            Object api = proxy.getAPI();
            if (! (api instanceof LockMgrIntf)) {
                return null;
            }
            return (LockMgrIntf) api;
        }

        static public Proxy getProxy(int nodeid) {
            ManagedService.ProxyObject proxy =
              ServiceManager.proxyFor(nodeid, "LockMgr");
            if (! (proxy instanceof LockMgrIntf.Proxy)) {
                return null;
            }
            return (LockMgrIntf.Proxy) proxy;
        }
    }
}