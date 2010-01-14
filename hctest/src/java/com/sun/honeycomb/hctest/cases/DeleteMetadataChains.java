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
import com.sun.honeycomb.common.*;

import java.util.HashMap;

/**
 * Validate that a basic delete works if there are long chains of MD.
 */
public class DeleteMetadataChains extends DeleteSuite {

    boolean setupOK = false;

    private HashMap mdMap;
    private HashMap mdMap2;
    private String md_value;
    private String md_value2;
    private String q;
    private String q2;
    private boolean emulator = false;


    public DeleteMetadataChains() {
        super();
    }

    public String help() {
        return(
            "\tMiscellaneous scenarios that add MD for an object\n" +
            "\tand do deletes to test the object disappears when expected.\n" +
            "\tOptional -ctx args:\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_FILESIZE +
                "=n (n is the size of the file in bytes)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_STRICT +
                " (do strict checking; enabled by default now)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_RMI +
                "=yes (will do additional validation)\n"
            );
    }

    /**
     * Currently, setUp does the initialization of common variables.
     * The stores, etc, are done in the cases themselves.
     */ 
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        super.setUp();
        setThrowAPIExceptions(false);

        String emulatorStr =
            TestRunner.getProperty(HoneycombTestConstants.PROPERTY_NO_CLUSTER);
        if (emulatorStr != null) {
            Log.INFO("RUN AGAINST THE EMULATOR");
            emulator = true;
        }


        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);
        md_value = RandomUtil.getRandomString(16);
        q = "\"" + HoneycombTestConstants.MD_VALID_FIELD + "\"='" + md_value + "'";
        mdMap = new HashMap();
        mdMap.put(HoneycombTestConstants.MD_VALID_FIELD, md_value);

        mdMap2 = new HashMap();

        if (testBed.rmiEnabled()) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_RMI +
                " was specified. Will validate deleted fragments on cluster." +
                " RMI servers must be started for this to work");
        } else {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_RMI +
                " does not have value yes. " +
                "Will not validate deleted fragments on cluster");
        }
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /**
     * Repro for bug 6275612.
      oid1=`store $VIP $FILE -m "filename"="fromtest"`
      oid2=`storemetadata $VIP $oid1` 
      oid3=`storemetadata $VIP $oid1` 
      delete $VIP $oid1
      delete $VIP $oid2
      retrieve_std_out $VIP $oid3 |head -2
     */
    public boolean test1_shortMetadataChain() throws HoneycombTestException {
        addTag(Tag.REGRESSION);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.STOREMETADATA);
        addTag(HoneycombTag.JAVA_API);
        if (excludeCase())
            return false;

        addBug("6275612",
            "delete reference counting with more than two objects " +
            "doesn't work as expected");

        CmdResult cr;

        cr = store(getFilesize(), mdMap);
        String oid1 = cr.mdoid;
        String dataoid = cr.dataoid;
        if (!cr.pass) {
            cr.logExceptions("store");
            return false;
        }
        Log.INFO("store1 returned " + oid1 + " (dataoid " + dataoid + ")");

        cr = addMetadata(oid1, mdMap2);
        if (!cr.pass) {
            cr.logExceptions("addMetadata to " + oid1);
            return false;
        }
        String oid2 = cr.mdoid;
        Log.INFO("addMetadata2 returned " + oid2);

        cr = addMetadata(oid1, mdMap2);
        if (!cr.pass) {
            cr.logExceptions("addMetadata to " + oid1);
            return false;
        }
        String oid3 = cr.mdoid;
        Log.INFO("addMetadata3 returned " + oid3);

        cr = delete(oid1);
        if (!cr.pass) {
            cr.logExceptions("delete of " + oid1);
            return false;
        } else {
            Log.INFO("delete1 succeeded on oid " + oid1);
        }

        cr = delete(oid2);
        if (!cr.pass) {
            cr.logExceptions("delete of " + oid2);
            return false;
        } else {
            Log.INFO("delete2 succeeded on oid " + oid2);
        }

        // verify the object is retrievable with last reference
        cr = retrieve(oid3);
        if (cr.pass) {
            Log.INFO("retrieve3 succeeded as expected for " + oid3);
        } else {
            cr.logExceptions("retrieve of " + oid3);
            Log.ERROR("retrieve3 failed when it shouldn't have" + oid3);
            return false;
        }


        // now verify the final delete makes the object not retreivable
        cr = delete(oid3);
        if (!cr.pass) {
            cr.logExceptions("delete of " + oid3);
            return false;
        } else {
            Log.INFO("delete3 succeeded on oid " + oid3);
        }

        cr = retrieve(oid3);
        if (!cr.pass) {
            Log.INFO("retrieve3 failed as expected for " + oid3);
        } else {
            Log.ERROR("retrieve3 succeeded when it shouldn't have" + oid3);
            return (false);
        }

        if (testBed.rmiEnabled()) {
            // Verify files on-disk are deleted
            if (!onDiskFragsLookDeleted(oid1) ||
                !onDiskFragsLookDeleted(oid2) ||
                !onDiskFragsLookDeleted(oid3) ||
                !onDiskFragsLookDeleted(dataoid)) {
                Log.ERROR("frags don't look deleted as expected on disk");
                return (false);
            } else {
                Log.INFO("files look deleted on disk as expected");
            }
        }

        //
        // There is no garbage collection on emulator so skip next test.
        //
        if (emulator) {
            return (true);
        }

        // Verify that the data object isn't retrievable
        cr = retrieveDataObject(dataoid);
        if (!cr.pass) {
            Log.INFO("retrieve data oid failed as expected for " + dataoid);
        } else {
            Log.ERROR("retrieve data oid succeeded when it shouldn't have for "
                + dataoid);
            return (false);
        }

        return (true);
    }

    /**
     * Repro for bug 6276950.
      oid=`store $VIP $FILE -m "filename"="fromtest"`
      oid2=`storemetadata $VIP $oid` 
      oid3=`storemetadata $VIP $oid ` 
      oid4=`storemetadata $VIP $oid ` 
      delete $VIP $oid
      oid5=`storemetadata $VIP $oid2` 
      delete $VIP $oid2
      oid6=`storemetadata $VIP $oid3` 
      oid7=`storemetadata $VIP $oid3` 
      oid8=`storemetadata $VIP $oid3` 
      delete $VIP $oid3
      delete $VIP $oid4
      delete $VIP $oid5
      oid9=`storemetadata $VIP $oid6` 
      delete $VIP $oid6
      oid10=`storemetadata $VIP $oid7` 
      delete $VIP $oid7
      delete $VIP $oid8
      delete $VIP $oid9
      retrieve_std_out $VIP $oid10 |head -2
     */
    public boolean test2_longMetadataChain() throws HoneycombTestException {
        addTag(Tag.REGRESSION);
        addTag(Tag.QUICK);
        addTag(Tag.POSITIVE);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.DELETE);
        addTag(HoneycombTag.STOREMETADATA);
        addTag(HoneycombTag.JAVA_API);
        if (excludeCase())
            return false;

        addBug("6276950",
            "complicated metadata chains (10 links) with deletes " +
            "aren't always handled correctly");

        CmdResult cr;

        cr = store(getFilesize(), mdMap);
        String oid = cr.mdoid;
        String dataoid = cr.dataoid;
        if (!cr.pass) {
            cr.logExceptions("store");
            return false;
        }
        Log.INFO("store1 returned " + oid + " (dataoid " + dataoid + ")");

        cr = addMetadata(oid, mdMap2);
        if (!cr.pass) {
            cr.logExceptions("addMetadata to " + oid);
            return false;
        }
        String oid2 = cr.mdoid;
        Log.INFO("addMetadata2 returned " + oid2);

        cr = addMetadata(oid, mdMap2);
        if (!cr.pass) {
            cr.logExceptions("addMetadata to " + oid);
            return false;
        }
        String oid3 = cr.mdoid;
        Log.INFO("addMetadata3 returned " + oid3);

        cr = addMetadata(oid, mdMap2);
        if (!cr.pass) {
            cr.logExceptions("addMetadata to " + oid);
            return false;
        }
        String oid4 = cr.mdoid;
        Log.INFO("addMetadata4 returned " + oid4);

        cr = delete(oid);
        if (!cr.pass) {
            cr.logExceptions("delete of " + oid);
            return false;
        } else {
            Log.INFO("delete1 succeeded on oid " + oid);
        }

        cr = addMetadata(oid2, mdMap2);
        if (!cr.pass) {
            cr.logExceptions("addMetadata to " + oid2);
            return false;
        }
        String oid5 = cr.mdoid;
        Log.INFO("addMetadata5 returned " + oid5);


        cr = delete(oid2);
        if (!cr.pass) {
            cr.logExceptions("delete of " + oid2);
            return false;
        } else {
            Log.INFO("delete2 succeeded on oid " + oid2);
        }

        cr = addMetadata(oid3, mdMap2);
        if (!cr.pass) {
            cr.logExceptions("addMetadata to " + oid3);
            return false;
        }
        String oid6 = cr.mdoid;
        Log.INFO("addMetadata6 returned " + oid6);

        cr = addMetadata(oid3, mdMap2);
        if (!cr.pass) {
            cr.logExceptions("addMetadata to " + oid3);
            return false;
        }
        String oid7 = cr.mdoid;
        Log.INFO("addMetadata7 returned " + oid7);

        cr = addMetadata(oid3, mdMap2);
        if (!cr.pass) {
            cr.logExceptions("addMetadata to " + oid3);
            return false;
        }
        String oid8 = cr.mdoid;
        Log.INFO("addMetadata8 returned " + oid8);

        cr = delete(oid3);
        if (!cr.pass) {
            cr.logExceptions("delete of " + oid3);
            return false;
        } else {
            Log.INFO("delete3 succeeded on oid " + oid3);
        }

        cr = delete(oid4);
        if (!cr.pass) {
            cr.logExceptions("delete of " + oid4);
            return false;
        } else {
            Log.INFO("delete4 succeeded on oid " + oid4);
        }

        cr = delete(oid5);
        if (!cr.pass) {
            cr.logExceptions("delete of " + oid5);
            return false;
        } else {
            Log.INFO("delete5 succeeded on oid " + oid5);
        }

        cr = addMetadata(oid6, mdMap2);
        if (!cr.pass) {
            cr.logExceptions("addMetadata to " + oid6);
            return false;
        }
        String oid9 = cr.mdoid;
        Log.INFO("addMetadata9 returned " + oid9);

        cr = delete(oid6);
        if (!cr.pass) {
            cr.logExceptions("delete of " + oid6);
            return false;
        } else {
            Log.INFO("delete6 succeeded on oid " + oid6);
        }

        cr = addMetadata(oid7, mdMap2);
        if (!cr.pass) {
            cr.logExceptions("addMetadata to " + oid7);
            return false;
        }
        String oid10 = cr.mdoid;
        Log.INFO("addMetadata10 returned " + oid10);

        cr = delete(oid7);
        if (!cr.pass) {
            cr.logExceptions("delete of " + oid7);
            return false;
        } else {
            Log.INFO("delete7 succeeded on oid " + oid7);
        }

        cr = delete(oid8);
        if (!cr.pass) {
            cr.logExceptions("delete of " + oid8);
            return false;
        } else {
            Log.INFO("delete8 succeeded on oid " + oid8);
        }

        cr = delete(oid9);
        if (!cr.pass) {
            cr.logExceptions("delete of " + oid9);
            return false;
        } else {
            Log.INFO("delete9 succeeded on oid " + oid9);
        }

        // verify the object is retrievable with last reference
        cr = retrieve(oid10);
        if (cr.pass) {
            Log.INFO("retrieve10 succeeded as expected for " + oid10);
        } else {
            Log.ERROR("retrieve10 failed when it shouldn't have for " + oid10);
            return (false);
        }

        // now verify the final delete makes it non-retrievable
        cr = delete(oid10);
        if (!cr.pass) {
            cr.logExceptions("delete of " + oid10);
            return false;
        } else {
            Log.INFO("delete10 succeeded on oid " + oid10);
        }

        cr = retrieve(oid10);
        if (!cr.pass) {
            Log.INFO("retrieve10 failed as expected for " + oid10);
        } else {
            Log.ERROR("retrieve10 succeeded when it shouldn't have for " + oid10);
            return (false);
        }

        if (testBed.rmiEnabled()) {
            // Verify files on-disk are deleted
            if (!onDiskFragsLookDeleted(oid) ||
                !onDiskFragsLookDeleted(oid2) ||
                !onDiskFragsLookDeleted(oid3) ||
                !onDiskFragsLookDeleted(oid4) ||
                !onDiskFragsLookDeleted(oid5) ||
                !onDiskFragsLookDeleted(oid6) ||
                !onDiskFragsLookDeleted(oid7) ||
                !onDiskFragsLookDeleted(oid8) ||
                !onDiskFragsLookDeleted(oid9) ||
                !onDiskFragsLookDeleted(oid10) ||
                !onDiskFragsLookDeleted(dataoid)) {
                Log.ERROR("frags don't look deleted as expected on disk");
                return (false);
            } else {
                Log.INFO("files look deleted on disk as expected");
            }
        }

        //
        // There is no garbage collection on emulator so skip next test.
        //
        if (emulator) {
            Log.INFO("strict checking not enabled; skipping validation that " +
                "data object isn't retrieveable");
            return (true);
        }

        // Verify that the data object isn't retrievable
        cr = retrieveDataObject(dataoid);
        if (!cr.pass) {
            Log.INFO("retrieve data oid failed as expected for " + dataoid);
        } else {
            Log.ERROR("retrieve data oid succeeded when it shouldn't have for "
                + dataoid);
            return (false);
        }

        return (true);
     }
}
