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



package com.sun.honeycomb.hctest.cases;

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.hctest.task.*;

import com.sun.honeycomb.hctest.rmi.spsrv.common.SPSrvConstants;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.client.*;

public class RebootNodes extends HoneycombRemoteSuite {

    private final long DEFAULT_INTERVAL = 300000; // 5 minutes
    private long interval = DEFAULT_INTERVAL;
    private long run_time = 3 * interval; // default 15 minutes

    public RebootNodes() {
        super();
    }

    public String help() {

        StringBuffer sb = new StringBuffer();

        sb.append("\tTests periodic rebooting of random cluster nodes.\n");
        sb.append("\tDoes not check whether nodes never came back, so not a\n");
        sb.append("\tcomplete test. Default reboot interval is ");
        sb.append(Long.toString(DEFAULT_INTERVAL)).append(" msec, default run\n");
        sb.append("\ttime is 3x that. Override with -ctx:\n");
        sb.append("\t\t").append(HoneycombTestConstants.PROPERTY_INTERVAL);
        sb.append("\n\t\t").append(HoneycombTestConstants.PROPERTY_RUN_DURATION);
        sb.append("\n\tRequires -ctx:\n\t\t");
        sb.append(HoneycombTestConstants.PROPERTY_SP_IP).append("\n");
        sb.append("\tand for SP and Node RMI servers to be running.\n");

        return sb.toString();
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        TestCase self = createTestCase("DistributedSetup for InfrastructureTests");
        self.addTag(Tag.WHITEBOX);
        if (self.excludeCase()) 
            return;

        //
        //  need Service Processor rmi server to access node servers
        //
        requiredProps.add(HoneycombTestConstants.PROPERTY_SP_IP);

        super.setUp();

        String s = getProperty(HoneycombTestConstants.PROPERTY_INTERVAL);
        if (s != null) {
            interval = HCUtil.parseTime(s);
            if (interval < 0)
                throw new HoneycombTestException("interval < 0");
        }
        s = getProperty(HoneycombTestConstants.PROPERTY_RUN_DURATION);
        if (s != null) {
            run_time = HCUtil.parseTime(s);
            if (run_time < 0)
                throw new HoneycombTestException("run_time < 0");
        }

        Log.INFO("interval " + interval + "  " + 
                 HoneycombTestConstants.PROPERTY_RUN_DURATION + " " + run_time);

        //
        //  XXX could check if SP has any node servers
        //
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /**
     *  Reboot random nodes at given interval. XXX needs to compare
     *  system state at end (after waiting a few minutes) to beginning
     *  to verify that no nodes were lost.
     */
    public boolean testReboot() {
        addTag(Tag.WHITEBOX);
        addTag(Tag.EXPERIMENTAL, 
               "Not part of any larger suite; don't want this run by mistake because it requires special setup");
        if (excludeCase()) 
            return false;
        RebootNodeTask t = new RebootNodeTask(SPSrvConstants.RANDOM_NODE, 
                                                               interval);
        t.start();

        sleep(run_time);

        t.interrupt();

        if (t.thrown.size() > 0) {
            return false;
        }

        //
        //  PASS
        //
        return true;
    }
}
