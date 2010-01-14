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



/**
 * Questions/Answers:
 * 
 * Is there a reason not to put the RandomUtil.getRandomString(...) calls
 * in AuditSrvClnt?  Yes, the user of this class should manage its own
 * random strings.
 * 
 * 
 *
 */

package com.sun.honeycomb.hctest.rmi.auditsrv.clnt;

import com.sun.honeycomb.hctest.rmi.auditsrv.common.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.SystemRecord;

import java.rmi.*;
import java.util.*;
import java.io.*;

//import java.rmi.server.RMISocketFactory;
//import com.sun.honeycomb.harness.common.FixedPortRMISocketFactory;


public class AuditSrvClnt {

    private boolean connected;
    private String serviceName = null;
    private AuditSrvIF svc = null;

    public AuditSrvClnt() {
        boolean connected = false;
    }

    public AuditSrvClnt(String server_ip) throws HoneycombTestException {
        this();
        connect(server_ip);
    }

    public void connect(String server_ip) throws HoneycombTestException {
        if (System.getSecurityManager() == null) {
            System.setSecurityManager( new RMISecurityManager() ); 
        }
        serviceName = "rmi://" + server_ip + ":" + 
            AuditSrvConstants.RMI_REGISTRY_PORT + "/" +  
            AuditSrvConstants.SERVICE;
        getSvc();
        this.connected = true;
    }

    public boolean isConnected() {return this.connected;}

    private void getSvc() throws HoneycombTestException {
        try {
            svc = (AuditSrvIF) Naming.lookup(serviceName);
        } catch (Exception e) {
            throw new HoneycombTestException (
                                "Naming.lookup(" + serviceName + "): " + e);
        }
    }

    ////////////////////////////////////////////////////////////////////
    //  main functions
    //
    public void auditStoreObject(SystemRecord sr, 
                                 HCTestReadableByteChannel data,
                                 String logTag)
        throws HoneycombTestException
    {
        auditStoreObject(sr.getObjectIdentifier().toString(), 
                         data.getSize(), 
                         data.computeHash(), 
                         null, 
                         logTag);
    }

    public void auditStoreObject(SystemRecord sr, 
                                 HCTestReadableByteChannel data,
                                 NameValueRecord metadata,
                                 String logTag)
        throws HoneycombTestException
    {
        auditStoreObject(sr.getObjectIdentifier().toString(), 
                         data.getSize(), 
                         data.computeHash(), 
                         metadata, 
                         logTag);
    }

    public void auditStoreObject(String oid, long filesize, String sha1, 
                            NameValueRecord metadata, String logTag) 
        throws HoneycombTestException 
    {
        if (connected) {
            StringBuffer sb = new StringBuffer();
            sb.append(HoneycombTestConstants.AUDIT_STORE).append("\t");
            sb.append(Long.toString(filesize)).append("\t");
            sb.append(sha1).append("\t").append(logTag);
            if (metadata != null) {
                append(sb, metadata);
            }
            logAudit(oid, sb.toString());
        }
    }

    public void auditAddMetadata(String linkOID, NameValueRecord nvr, 
                              String newoid) 
        throws HoneycombTestException 
    {
        if (connected) {
            //
            //  note in the old oid that an md oid points to it
            //
            logAudit(linkOID, HoneycombTestConstants.AUDIT_ADD_MD + "\t" + newoid);
            
            //
            //  log/audit md under the new md oid
            //
            StringBuffer sb = new StringBuffer();
            sb.append(HoneycombTestConstants.AUDIT_ADD_MD).append("\t");
            sb.append(newoid);
            append(sb, nvr);
            logAudit(newoid, sb.toString());
        }
    }

    public void logAudit(String oid, String action) 
        throws HoneycombTestException 
    {
        if (connected) {
            try {
                svc.logAudit(oid, action);
            } catch (Exception e) {
                throw new HoneycombTestException(e);
            }
        }
    }

    public void logMsg(String msg) throws HoneycombTestException {
        if (connected) {
            try {
                svc.logMsg(msg);
            } catch (Exception e) {
                throw new HoneycombTestException(e);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////
    //  cmds for test of system
    //
    /**
     *  Get status from Audit server.
     */
    public String auditStatus() throws HoneycombTestException {
        try {
            return svc.auditStatus();
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public int countOIDs() throws HoneycombTestException {
        try {
            return svc.countOIDs();
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    public String getOID(String oid) throws HoneycombTestException {
        try {
            return svc.getOID(oid);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }


    public String toString() {
        return "AuditSrvClnt(SVC=" + serviceName + ")";
    }

    //
    //  main is for unit test of the audit server
    //
    public static void usage() {
        System.err.println("Usage: AuditSrvClnt <rmi_host_ip> <args>");
        System.err.println("         -s:   get runtime status");
        System.err.println("         -c:   get count of oid's");
        System.err.println("     -o oid:   get audit info on oid");
        System.err.println("-la oid msg:   log audit msg");
        System.err.println("     -l msg:   log msg (syslog)");
        System.exit(1);
    }

    public static void main(String args[]) {

        if (args.length < 2)
            usage();

        try {
            //RMISocketFactory.setSocketFactory(new
                                //FixedPortRMISocketFactory(3333));
            AuditSrvClnt clnt = new AuditSrvClnt(args[0]);
            if (args[1].equals("-s")) {
                System.out.println(clnt.auditStatus());
            } else if (args[1].equals("-c")) {
                System.out.println("oid count: " + clnt.countOIDs());
            } else if (args[1].equals("-la")) {
                if (args.length != 4)
                    usage();
                clnt.logAudit(args[2], args[3]);
                System.out.println("logged");
            } else if (args[1].equals("-o")) {
                System.out.println(clnt.getOID(args[2]));
            } else if (args[1].equals("-l")) {
                clnt.logMsg(args[2]);
                System.out.println("msg logged [" + args[2] + "]");
            } else {
                usage();
            }
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }


    //
    // Private Utilities
    //

    private void append(StringBuffer sb, NameValueRecord nvr)
    {
        sb.append("\t#MD#");
        String[] keys = nvr.getKeys();
        for (int i=0; i<keys.length; i++) {
            String name = keys[i];
            //-->
            Object value = nvr.getAsString(name);
            sb.append("\t").append(name).append("\t").append(value.toString());
        }
    }


    //
    // class members/methods
    //

    public static final AuditSrvClnt singleton = instantiate();

    /**
     * Records the successful storage of an Object.  If audit is not
     * enabled, calling this method has no effect.
     * @param data The stream that was used to generate that Object data.
     * @param record The SystemRecord returned by the archive.
     * @param logTag This string is to "tag" the logs created as part of the 
     *               audit.  In this way, it is easy to find (grep) all the log
     *               records that have the same tag.
     * @throws HoneycombTestException
     */
    public static void auditStore(HCTestReadableByteChannel data,
                                        SystemRecord record,
                                        String logTag)
        throws HoneycombTestException
    {
        if (enabled()) {
            singleton.auditStoreObject(record, data, logTag);
        }
    }

    public static boolean enabled() {
        return singleton.isConnected();
    }

    private static AuditSrvClnt instantiate() {
        AuditSrvClnt instance = null;
        try {
            instance = new AuditSrvClnt(TestRunner.getProperty(HoneycombTestConstants.PROPERTY_AUDIT_IP));
        }
        catch (Throwable t) {
            instance = new AuditSrvClnt();
        }
        return instance;
    }

}
