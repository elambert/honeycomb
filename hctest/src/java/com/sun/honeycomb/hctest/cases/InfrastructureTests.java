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
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.hctest.task.*;

public class InfrastructureTests extends HoneycombRemoteSuite {

    public InfrastructureTests() {
        super();
    }

    public String help() {

        StringBuffer sb = new StringBuffer();

        sb.append("\tTests RMI roundtrip time to remote client 0 and all clients.\n");
        sb.append("\tRequires remote clients.\n");

        return sb.toString();
    }

    public void setUp() throws Throwable {

        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        //requiredProps.add(HoneycombTestConstants.PROPERTY_SP_IP);
        TestCase self = createTestCase("DistributedSetup for InfrastructureTests");
        self.addTag(Tag.DISTRIBUTED);
        if (self.excludeCase()) 
            return;

        super.setUp();
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /**
     *  Simple test of RMI roundtrip time.
     */
    public boolean testRMITime() throws HoneycombTestException {
        addTag(Tag.DISTRIBUTED);
        addTag(Tag.EXPERIMENTAL, "Not part of any larger suite, distributed");
        if (excludeCase()) 
            return false;
        CmdResult cr = timeRMI();
        Log.INFO("testRMITime: " + cr.time);
        return true;
    }

    public boolean testRMITimes() throws HoneycombTestException {

        addTag(Tag.DISTRIBUTED);
        addTag(Tag.EXPERIMENTAL, "Not part of any larger suite, distributed");
        if (excludeCase()) 
            return false;

        long t1 = System.currentTimeMillis();
        TimeTask[] tasks = startTimeRMIs();
        try {
            waitForTasks(tasks, 10000);
        } catch(Exception e) {
            Log.ERROR("waiting for tasks: " + e);
            return false;
        }
        t1 = System.currentTimeMillis() - t1;

        int errors = 0;
        long min = 99999999;
        long max = -1;
        long total = 0;
        int n = 0;

        for (int i=0; i<tasks.length; i++) {

            TimeTask tt = tasks[i];

            String tag = "";
            if (tt.result == null)
                tag = "(result null)";

            if (tt.thrown.size() > 0) {
                Log.ERROR("ERROR " + tag);
                for (int j=0; j<tt.thrown.size(); j++) {
                    Throwable t = (Throwable) tt.thrown.get(j);
                    Log.ERROR("\ttask exception [" + i + "] " +
                                                        Log.stackTrace(t));
                }
                errors++;
                continue;
            }
            if (tt.result == null) {
                Log.ERROR("missing result " + i + " " + tag);
                errors++;
                continue;
            }
            Log.INFO("time " + tt.client + ": " + tt.result.time);
            total += tt.result.time;
            n++;
            if (tt.result.time < min)
                min = tt.result.time;
            if (tt.result.time > max)
                max = tt.result.time;
        }
        Log.INFO("min " + min + "  max " + max + "  avg " + (total / n));
        Log.INFO("total parallel time " + t1);
        if (errors > 0)
            return false;
        return true;
    }
}
