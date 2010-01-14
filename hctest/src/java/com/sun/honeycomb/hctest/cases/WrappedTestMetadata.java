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
import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.hctest.sg.*;

import com.sun.honeycomb.client.*;

import java.util.ArrayList;

/**
 * Uses an old test to validate some different MD scnearios.
 */
public class WrappedTestMetadata extends HoneycombLocalSuite {

    public WrappedTestMetadata() {
        super();
    }

    public String help() {
        return(
            "\tUses the old TestMetadata test to validate that the basic\n" +
            "\tcases tested by that test still pass.\n"
            );
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        super.setUp();
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /**
     * Run the metadata test to see if it discovers any errors.
     */
    public boolean testMetadata() throws Throwable {
        addTag(Tag.POSITIVE);
        addTag(HoneycombTag.QUERY);
        addTag(HoneycombTag.JAVA_API);
        // addTag(Tag.QUICK);
        // addTag(Tag.REGRESSION);
        // addTag(Tag.SMOKE);
        if (excludeCase()) return false;

        TestMetadata Metadata = new TestMetadata();
        String args[] = {
            "-s", testBed.getDataVIP(),
            "-p", "" + testBed.getDataPort(),
            "-T" };
        try {
            Metadata.main(args);
        } catch (HoneycombTestException hte) {
            // Look at the exception message to see if it passed or failed
            String rc = hte.getMessage();
            Log.DEBUG("Result is " + rc);
            if (!rc.equals(TestRun.PASS)) {
                Log.ERROR("Test failed...see stdout");
                return (false);
            }
        }

        return (true);
    }
}
