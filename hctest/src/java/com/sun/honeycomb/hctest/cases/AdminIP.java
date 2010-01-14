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
import com.sun.honeycomb.hctest.cli.CLIState;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.client.*;
import com.sun.honeycomb.common.*;

import java.io.File;
import java.util.ArrayList;

/**
 * Validate that CLI change of datavip works.
 */
public class AdminIP extends HoneycombLocalSuite {

    private CmdResult storeResult = null;

    // If the store fails, we will fail the other tests due to dependencies
    private boolean storedOK = false;
    
    public AdminIP() {
        super();
    }

    public String help() {
        return("\tValidate that change of admin ip works\n");
    }

    /**
     * Store a file.
     */
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    public void testIt() {

        storedOK = false;

        TestCase self = createTestCase("AdminIP", "alternate IP's");
        
        // self.addTag(Tag.SMOKE);
        // self.addTag(Tag.REGRESSION);
        // self.addTag(Tag.POSITIVE);
        // self.addTag(HoneycombTag.PERF_BASIC);

        if (self.excludeCase()) return;

	int iterations = 1;
        CLIState cli = CLIState.getInstance();
        String initIP = testBed.adminVIPaddr;
        String alternateIP = null;
        try {
            String s = getProperty(HoneycombTestConstants.PROPERTY_ITERATIONS);
            if (s != null) {
                iterations = Integer.parseInt(s);
                if (iterations <= 0) {
                    self.testFailed(HoneycombTestConstants.PROPERTY_ITERATIONS +
                            " < 1");
                    return;
                }
            }
            alternateIP = getProperty(
                                HoneycombTestConstants.PROPERTY_ALTERNATE_IP);
            if (alternateIP == null) {
                self.testFailed(HoneycombTestConstants.PROPERTY_ALTERNATE_IP +
                            " not defined");
                return;
            }
        } catch (Exception e) {
            self.testFailed("getting params: " + e);
            return;
        }
        Log.INFO("iterations: " + iterations);
        Log.INFO("IPs are " + initIP + " and " + alternateIP);

        try {
            for (int i=0; i<iterations; i++) {
                Log.INFO("==================================== iteration " + i);
                cli.setStringValue(CLIState.ADMIN_VIP, alternateIP);
                cli.setStringValue(CLIState.ADMIN_VIP, initIP);
            }
        } catch (Exception e) {
            self.testFailed("failed: " + e);
            return;
        }
        self.testPassed("iterations: " + iterations);
    }
}
