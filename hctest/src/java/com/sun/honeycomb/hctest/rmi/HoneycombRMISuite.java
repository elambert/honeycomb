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



package com.sun.honeycomb.hctest.rmi;

import com.sun.honeycomb.hctest.rmi.spsrv.common.SPSrvConstants;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.client.*;

import java.io.File;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Random;

/**
 * This class has helper functions for test cases that address 
 * Honeycomb clusters via remote clients. It is restricted
 * to cluster interface and synchronization - please add
 * other helper functions to test.HoneycombSuite for all types
 * of tests, or test.HoneycombRemoteSuite for distributed tests.
 */
public class HoneycombRMISuite extends HoneycombSuite {

    public HoneycombRMISuite() {
        super();
    }

    public void setUp() throws Throwable {
        Log.DEBUG("com.sun.honeycomb.test.HoneycombRMISuite::setUp() called");
        super.setUp();
        //
        //  the basic requirement of a HoneycombRMISuite is that
        //      the TestBed has clients
        //
        if (testBed.clientCount() == 0) {
            throw new HoneycombTestException(
                                    "Clients and cluster must be defined.");
        }
    }

    //////////////////////////////////////////////////////////////////////////
    //  synchronous non-API client host capabilities
    //
    /**
     *  Check if file exists on remote host, retrying for given waitSeconds.
     */
    public CmdResult doesFileExist(int client, String path, int waitSeconds)
                                                throws HoneycombTestException {
        return testBed.doesFileExist(client, path, waitSeconds);
    }   

    /**
     *  Ssh a command from remote host to another.
     */
    public CmdResult sshCmd(int client, String login_host, String sshCmd)
                                                throws HoneycombTestException {
        return testBed.sshCmd(client, login_host, sshCmd);
    }

    //////////////////////////////////////////////////////////////////////////
    //  synchronous basic Honeycomb API capabilities
    //

    public CmdResult store(int client, long filesize, HashMap mdMap) 
                                                throws HoneycombTestException {
        return testBed.store(client, filesize, mdMap);
    }

    public CmdResult addMetadata(int client, String oid, HashMap mdMap) 
                                                throws HoneycombTestException {

        Log.DEBUG("Attempting to add metadata " + mdMap + " to " + oid);
        CmdResult cr = testBed.addMetadata(client, oid, mdMap);
        Log.INFO("OID returned from addMetadata: " + cr.mdoid);

        return cr;
    }

    public CmdResult retrieve(int client, String oid) 
                                                throws HoneycombTestException {
        Log.INFO("Retrieving " + oid);
        return testBed.retrieve(client, oid);
    }
                
    public CmdResult query(int client, String q) throws HoneycombTestException {
        Log.DEBUG("Attempting a query of " + q);
        return testBed.query(client, q);
    }
    public CmdResult query(int client, String q, int n) 
                                                throws HoneycombTestException {
        Log.DEBUG("Attempting a query of " + q + ", with max results " + n);
        return testBed.query(client, q, n);
    }

    public CmdResult delete(int client, String oid) 
                                                throws HoneycombTestException {
        Log.DEBUG("Attempting a delete of oid " + oid);
        return testBed.delete(client, oid);
    }

    public CmdResult umountNFS(int client) throws HoneycombTestException {
        throw new HoneycombTestException("umountNFS not impl");
    }

    public CmdResult mountNFS(int client) throws HoneycombTestException {
        throw new HoneycombTestException("mountNFS not impl");
    }

    //////////////////////////////////////////////////////////////////////////
    //  synchronous basic Honeycomb whitebox capabilities
    //

    public HOidInfo getOidInfo(String oid, boolean thisOnly)
                                                throws HoneycombTestException {
        return testBed.getOidInfo(oid, thisOnly);
    }
    public void deleteFragments(ArrayList l) throws HoneycombTestException {
        testBed.deleteFragments(l);
    }
    /**
     *  Reboot a random node.
     */
    public void rebootRandomNode() throws HoneycombTestException {
         testBed.rebootNode(SPSrvConstants.RANDOM_NODE);
    }
}
