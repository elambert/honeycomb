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



package com.sun.honeycomb.oa;

import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ExtendedMetadata;

/**
 * A JUnit-based unit test for Object Archive.
 */
public class ObjectArchiveTest extends TestCase {
    
    /**
     * Constructor for this unit test.
     * @param testName the name of the unit test
     */
    public ObjectArchiveTest(java.lang.String testName) {
        super(testName);
    }
    
    /**
     * Allows unit tests to be run together in a suite.
     * @return a test suite that contains a single test - this one
     */
    public static Test suite() {
        TestSuite suite = new TestSuite(ObjectArchiveTest.class);
        return suite;
    }
    
    /**
     * Allows test to be run stand-alone from the command-line.
     * @param args command-line args (ignored)
     */
    public static void main(String args[]) {
        junit.textui.TestRunner.run(suite());
    }
    
    public void setUp() {
        try {
// BROKEN
//             oa = ObjectArchive.getInstance();
        } catch (Throwable t) {
            t.printStackTrace();
            fail("OA getInstance failed");
        }
    }
    
    public void tearDown() {    
    }
    
    public void doStore(String src) {
        try {
            File df = new File(src);
            FileInputStream fis = new FileInputStream(df);
            FileChannel dfc = fis.getChannel();
            long size = dfc.size();
            ExtendedMetadata emd = new ExtendedMetadata((Map)null);
            NewObjectIdentifier oid = null;
// BROKEN
//             oid = oa.store(dfc, size, emd);
            df = File.createTempFile("oatest", null);
            FileOutputStream fos = new FileOutputStream(df);
            dfc = fos.getChannel();
// BROKEN
//             size = oa.retrieve(dfc, oid);
            Process diff = 
                Runtime.getRuntime().exec("diff " + src + " " + df);
            if (diff.waitFor() != 0) {
                fail("diff of retrieved file with original failed");
            } else {
                System.out.println("diff " + src + " " + df + " OK");
            }   
        } catch (Throwable t) {
            t.printStackTrace();
            fail("OA store/retrieve test failed");
        }
    }
    
    public void testStoreSmall() {
        doStore("dist" + File.separator + 
                "lib" + File.separator + "honeycomb.jar");
    }
    
}
