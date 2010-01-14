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


package com.sun.honeycomb.hctest.rmi.clntsrv.common;

import com.sun.honeycomb.hctest.CmdResult;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.util.*;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;

public interface ClntSrvIF extends Remote {

    public CmdResult timeRMI(ClusterTestContext cluster, boolean trySP)
                                                        throws RemoteException;

    public boolean ping(String host) throws RemoteException;

    public void logMsg(ClusterTestContext cluster, String msg)
                                                        throws RemoteException;

    public CmdResult doesFileExist(String path, int waitSeconds) 
                                                        throws RemoteException;

    public CmdResult sshCmd(String login_host, String sshCmd)
                                                        throws RemoteException;

    public CmdResult ls(String path) throws RemoteException;

    public CmdResult store(ClusterTestContext cluster, 
                           long size, 
                           boolean binary, HashMap mdMap, boolean calcHash)
                                                        throws RemoteException;

    public CmdResult store(ClusterTestContext cluster, 
                           byte[] bytes,
                           int repeats, 
                           boolean binary, 
                           HashMap mdMap, boolean calcHash)
                                                        throws RemoteException;

    public CmdResult addMetadata(ClusterTestContext cluster, String oid, 
                                                            HashMap mdMap)
                                                        throws RemoteException;

    public CmdResult retrieve(ClusterTestContext cluster, String oid, 
                              boolean calcHash)
                                                        throws RemoteException;

    public CmdResult getMetadata(ClusterTestContext cluster, String oid)
                                                        throws RemoteException;

    public CmdResult query(ClusterTestContext cluster, String query) 
                                                        throws RemoteException;

    public CmdResult query_without_resultset(ClusterTestContext cluster, String query) 
    													throws RemoteException;

    public CmdResult query(ClusterTestContext cluster, String query, 
                                                                int maxResults) 
                                                        throws RemoteException;

    public CmdResult delete(ClusterTestContext cluster, String oid) 
                                                        throws RemoteException;

    public void shutdown() throws RemoteException;

	public CmdResult audit(ClusterTestContext cluster, String oid) throws RemoteException;
}
