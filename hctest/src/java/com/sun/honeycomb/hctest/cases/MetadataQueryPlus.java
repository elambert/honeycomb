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

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;

import com.sun.honeycomb.client.*;
import com.sun.honeycomb.common.ArchiveException;

import com.sun.honeycomb.client.NameValueSchema.Attribute;
import com.sun.honeycomb.client.NameValueSchema.ValueType;

import java.util.ArrayList;
import java.util.HashMap;
import java.io.IOException;
import java.util.Iterator;


public class MetadataQueryPlus extends HoneycombLocalSuite {

    private boolean emulator;
    private CmdResult storeResult;
    private MdGenerator mdGenerator;
    private static final int MAX_ATTRS = 5;

    private void addTags() {
        addTag(Tag.REGRESSION);
        addTag(Tag.POSITIVE);
        addTag(Tag.QUICK);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.STOREMETADATA);
        addTag(HoneycombTag.QUERY);
        addTag(HoneycombTag.JAVA_API);
    }


    public void setUp() throws Throwable {

        super.setUp();

        boolean emulator = false;
        
        String emulatorStr =
            TestRunner.getProperty(HoneycombTestConstants.PROPERTY_NO_CLUSTER);
        if (emulatorStr != null) {
            emulator = true;
            Log.INFO("RUN AGAINST THE EMULATOR (nocluster defined)");
        } else {
            String s = TestRunner.getProperty(TestConstants.PROPERTY_INCLUDE);
            if (s != null  &&  s.indexOf(HoneycombTag.EMULATOR) != -1) {
                emulator = true;
                Log.INFO("RUN AGAINST THE EMULATOR (include=emulator)");
            }
        }

        createTestCase("MetadataQueryPlus", 
                        emulator ? "emulator" : "no-emulator");
        addTags();
        if (excludeCase())
            return;

        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);

        NameValueObjectArchive nvoa = new
            NameValueObjectArchive(TestBed.getInstance().getDataVIP());
        
        try {
            mdGenerator = new MdGenerator(nvoa.getSchema(), emulator);
        } catch (HoneycombTestException e) {
            Log.ERROR("Failed to create MDGenerator, exit");
            throw (e);
        }
        doStoreTestNow();
    }

    public void tearDown() throws Throwable {
        super.tearDown();
    }


    private void doStoreTestNow() throws HoneycombTestException {

        // This must happen first so it is done in setup().
        // If the store fails, we abort the test case.
        storeResult = store(getFilesize());

        Log.INFO("Stored file of size " + getFilesize() +
            " as oid " + storeResult.mdoid);
    }

    public boolean testSimpleQueryPlus()
        throws HoneycombTestException {

        if (excludeCase())
            return false;

        boolean res = false;

        for (int i = 1; i < MAX_ATTRS; i++) {
            mdGenerator.createRandomStringFromSchema(i);
            HashMap hm = mdGenerator.getMdMap();
            CmdResult addMetadataResult = addMetadata(storeResult.mdoid,
                                                      hm); 
            if (!addMetadataResult.pass) {
                Log.ERROR("failed to add metadata for " + storeResult.mdoid);
                return false;                
            }
            res = queryPlusCheckResults(addMetadataResult.mdoid, i);
            if (res == false) {
                Log.ERROR("Test failed for i = " + i);
                return (false);
            }
        }
        return (true);
    }


    private boolean queryPlusCheckResults(String oid, int size)
        throws HoneycombTestException {

        String q = mdGenerator.generateQueryFromMap(size);
        String [] selectClause = new String[size];
        HashMap hm = mdGenerator.getMdMap();
        Iterator it = hm.keySet().iterator();
        int i =0;
        while (it.hasNext()) {
            String attrName = (String) it.next();
            selectClause[i] = attrName;
            i++;
        }
        QueryResultSet qrs = null;
        try {
            CmdResult cr = query(q, selectClause);
            qrs = (QueryResultSet) cr.rs;
            while (qrs.next()) {
                ObjectIdentifier curOid = qrs.getObjectIdentifier();
                if (oid.equals(curOid.toString())) {
                    it = hm.keySet().iterator();
                    while (it.hasNext()) {
                        String attrName = (String) it.next();
                        ValueType type = mdGenerator.getType(attrName);
                        if (type.equals(NameValueSchema.BINARY_TYPE)) {
                            Log.INFO("Unsupported BINARY type");
                            return (false);
                        } else if (type.equals(NameValueSchema.DOUBLE_TYPE)) {
                            Log.WARN("unsupported type DOUBLE");
                            return (false);                            
                        } else if (type.equals(NameValueSchema.LONG_TYPE)) {
                            if (((Long) hm.get(attrName)).compareTo(
                                     new Long(qrs.getLong(attrName))) != 0) {
                                Log.ERROR("Attribute " + attrName + 
                                         " was stored with val = " +
                                         hm.get(attrName).toString() + 
                                         ", and received val = " +
                                         qrs.getLong(attrName));
                                return (false);
                            } else {
                                Log.INFO("QueryPlus OK: attribute " + attrName + " type Long");
                            }
                        } else if (type.equals(NameValueSchema.STRING_TYPE)) {
                            //
                            // Strings can be truncated in HADB!
                            // Result of the query should be a substring of original.
                            //
                            if (((String)hm.get(attrName)).indexOf(
                                        (qrs.getString(attrName))) == -1) {
                                Log.ERROR("Attribute " + attrName + 
                                         " was stored with val = " +
                                         hm.get(attrName).toString() + 
                                         ", and received val = " +
                                         qrs.getString(attrName));
                                return (false);
                            } else {
                                Log.INFO("QueryPlus OK: attribute " + attrName + " type String");
                            }
                        } else {
                            Log.WARN("unexpected type");
                            return (false);                            
                        }
                    }
                    return (true);
                }
            }
            Log.ERROR("oid not found for query "+q);
            return (false);
        } catch (Exception e) {
            Log.ERROR("Failed to queryPlus " + e);
            e.printStackTrace();
            return (false);
        }
    }
}
