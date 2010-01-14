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


package com.sun.honeycomb.hctest.rmi.auditsrv.srv;

import com.sun.honeycomb.hctest.rmi.auditsrv.common.*;

import com.sun.honeycomb.hctest.util.*;

import com.sun.honeycomb.test.util.*;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import java.io.*;
import java.util.*;

import java.util.logging.Logger;

public class AuditSrvService extends UnicastRemoteObject implements AuditSrvIF {

    private final String DB_ROOT = "/cluster_db";

    private FileLister fl = null;

    private static final Logger LOG =
                            Logger.getLogger(AuditSrvService.class.getName());

    private long start_time = System.currentTimeMillis();
    private int log_reqs = 0;

    public AuditSrvService() throws RemoteException {

        super();
        LOG.info("AuditSrvService is initializing");
        try {
            File f = new File(DB_ROOT);
            if (!f.exists())
                f.mkdirs();
            fl = new FileLister(DB_ROOT);
        } catch (Exception e) {
            throw new RemoteException(DB_ROOT, e);
        }
        LOG.info("AuditSrvService initialized");
    }

    /**
     *  Log string to oid file. Parsed by util/AuditParser.java
     *  and task/ClientAuditThread.java.
     */
    public void logAudit(String oid, String action) throws RemoteException {

        log_reqs++;

        long t1 = System.currentTimeMillis();

        try {
            //
            //  get hash dirs as in HC itself
            //
            String dir =  DB_ROOT + HCUtil.getHashDirs(oid);
            String file = dir + oid;

            File f = new File(file);
            if (!f.exists()) {
                f = new File(dir);
                f.mkdirs();
            }
            FileWriter fo = new FileWriter(file, true);
            fo.write(Long.toString(t1) + "\t" + action + "\n");
            fo.close();

        } catch (Exception e) {
            throw new RemoteException("logAudit", e);
        }
        //LOG.info("audit log time: " + (System.currentTimeMillis()-t1) +
        //                                " " + action);
    }

    /**
     *  Log msg to syslog.
     */
    public void logMsg(String msg) throws RemoteException {
        // System.err.println("logMsg: " + msg);
        LOG.info(msg);
    }

    /////////////////////////////////////////////////////////////////
    //  methods for testing audit system
    //
    public String auditStatus() throws RemoteException {
        return "up since " + new Date(start_time) + 
               "\naudit stores: " + log_reqs;
    }

    public int countOIDs() throws RemoteException {
        try {
            fl.reset();
            return fl.countFiles();
        } catch (Exception e) {
            throw new RemoteException("countOIDs", e);
        }
    }

    char cbuf[] = null;

    public String getOID(String oid) throws RemoteException {
        try {
            String dir =  DB_ROOT + HCUtil.getHashDirs(oid);
            String file = dir + oid;

            File f = new File(file);
            if (!f.exists())
                return null;

            int length = (int)f.length();

            if (cbuf == null  ||  length > cbuf.length)
                cbuf = new char[length];

            FileReader fr = new FileReader(f);
            int l = fr.read(cbuf, 0, length);
            fr.close();
            if (l != length)
                throw new RemoteException("unexpected read (" + l +
                                             "/" + length + ") " + f.getPath());            return new String(cbuf, 0, (int)length);

        } catch (Exception e) {
            throw new RemoteException("getOID", e);
        }
    }
}

