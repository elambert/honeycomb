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



/*
 * This is the regression test for bug
 * 6426567: QueryConvert generates invalid queries. Can be triggered by
 * Webdav browsing.
 */

package com.sun.honeycomb.hctest.cases;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.hctest.HoneycombLocalSuite;
import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.hctest.CmdResult;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

public class Query6426567
    extends HoneycombLocalSuite {

    public Query6426567() {
        super();
    }

    public void testMain() {

        TestCase testCase = createTestCase("SimplePerf");
        testCase.addTag(Tag.REGRESSION);
        
        if (testCase.excludeCase()) return;

        String[] keys = new String[] {
            "system.object_id"
        };

        CmdResult result = null;

        try {
            result = query("system.object_ctime=0", keys, 1);
        } catch (HoneycombTestException hte) {
            testCase.testFailed("Query failed: " + hte);
            Log.DEBUG(Log.stackTrace(hte));
            testCase.testFailed("Query6426567: " + hte);
        }

        if (result != null) {
            testCase.testPassed();
        }
    }
}
