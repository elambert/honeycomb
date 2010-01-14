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
import java.util.HashSet;
import java.util.ListIterator;
import java.util.List;
import java.util.ArrayList;

/**
 * Validate delete scenarios when frags are missing
 */
public class DeleteMissingFrags extends DeleteSuite {

    boolean setupOK = false;

    int PASSED = 0;
    int FAILED = 1;
    int SKIPPED = 2;

    private String mdoid;
    private String dataoid;

    private int defaultHowManyObjs = 1;
    private int howManyObjs;

    // XXX allow to be random
    private int defaultHowManyFrags = 1;
    private int howManyFrags;

    // values for PROPERTY_PICK_FRAGS
    public static final String FIXED_FRAGS = "fixed";
    public static final String RANDOM_FRAGS = "random";
    private String defaultPickFrags = FIXED_FRAGS;
    private String pickFrags;

    private long defaultfilesize;
    private long filesize;

    private long defaultFilesystemSettleTime = 5000;
    private long filesystemSettleTime;

    private long defaultsleeptime = 0;
    private long sleeptime;

    // values for PROPERTY_WHICH_FRAGS
    public static final String BOTH_FRAGS = "bothfrags";
    public static final String MD_FRAGS = "mdfrags";
    public static final String DATA_FRAGS = "datafrags";
    private String defaultWhichFrags = BOTH_FRAGS;
    private String whichFrags;

    // XXX handle the no delete case better...expected results change...
    private boolean defaultnoretrieve = false;
    private boolean defaultnoretrievemd = false;
    private boolean defaultnodelete = false;
    private boolean defaultnoseconddelete = false;
    private boolean defaultnoaddmd = false;
    private boolean defaultnorestore = false;
    private boolean noretrieve;
    private boolean noretrievemd;
    private boolean nodelete;
    private boolean noseconddelete;
    private boolean noaddmd;
    private boolean norestore;

    // Use only the scenario the user asked for with specified params
    // plus
    public boolean defaultshortrun = false;
    public boolean shortrun;

    public boolean defaultlongrun = false;
    public boolean longrun = false;

    public boolean defaultdontskip = false;
    public boolean dontskip = false;

    private HashMap smallMD;
    private String md_value;
    private String md_value2;
    private String q;
    private String q2;

    public DeleteMissingFrags() {
        super();
        setDefaults();
    }

    public String help() {
        return(
            "\tDo a series of actions while deleting and restoring frags\n" +
            "\tOptional -ctx args:\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_FILESIZE +
                "=n (n is the size of the file in bytes)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS +
                "=n (n is how many objects to test per iteration)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_PICK_FRAGS +
                "=n (n can be " +
                    FIXED_FRAGS + ", " +
                    RANDOM_FRAGS + ", which declares how we pick frags)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_HOW_MANY_FRAGS +
                "=n (n is how many frags to affect per iteration)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_WHICH_FRAGS +
                "=n (n can be " +
                    BOTH_FRAGS + ", " +
                    MD_FRAGS + ", or " +
                    DATA_FRAGS + ", which declares what frags are affected)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_SLEEP_TIME +
                "=n (n is how much time to sleep after certain ops)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_FSSETTLE +
                "=n (n is how much time to wait to let fs attrs settle)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_NO_RETRIEVE + "\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_NO_RETRIEVEMD + "\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_NO_DELETE + "\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_NO_SECONDDELETE + "\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_NO_ADDMD + "\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_NO_RESTORE + "\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_SHORTRUN + " (do only a single test)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_LONGRUN + " (do the whole matrix of tests)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_DONTSKIP +
                " (do not avoid bugs by skipping tests)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_RMI +
                "=yes (REQUIRED FOR THIS TEST)\n"
            );
    }

    /**
     * Currently, setUp does the initialization of the shared
     * variables.  Actually stores, etc, are done in the cases.
     */ 
    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        super.setUp();
        setThrowAPIExceptions(false);

        // Test case to avoid running this in the regression suite
        TestCase self = createTestCase("DeleteMissingFrags setup");
        self.addTag(HoneycombTag.DELETE);
        self.addTag(HoneycombTag.JAVA_API);
        self.addTag(Tag.NORUN);  // since it uses rmi
        if (self.excludeCase()) 
            return;

        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);
        defaultfilesize = getFilesize();

        md_value = RandomUtil.getRandomString(16);
        q = "\"" + HoneycombTestConstants.MD_VALID_FIELD + "\"='" + md_value + "'";
        smallMD = new HashMap();
        smallMD.put(HoneycombTestConstants.MD_VALID_FIELD, md_value);

        String s = getProperty(HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS);
        if (s != null) {
            Log.INFO("Property " +
                HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS +
                " was specified. Using " + s + " " +
                HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS);
            defaultHowManyObjs = Integer.parseInt(s);
        } else {
            Log.INFO("using default value for property " +
                HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS +
                " of " + defaultHowManyObjs);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_HOW_MANY_FRAGS);
        if (s != null) {
            Log.INFO("Property " +
                HoneycombTestConstants.PROPERTY_HOW_MANY_FRAGS +
                " was specified. Using " + s + " " +
                HoneycombTestConstants.PROPERTY_HOW_MANY_FRAGS);
            defaultHowManyFrags = Integer.parseInt(s);
            if (defaultHowManyFrags > HoneycombTestConstants.OA_TOTAL_FRAGS) {
                throw new HoneycombTestException("total frags in oa per obj is "
                    + HoneycombTestConstants.OA_TOTAL_FRAGS + "; you can't " +
                    "specify " + defaultHowManyFrags + " for " +
                    HoneycombTestConstants.PROPERTY_HOW_MANY_FRAGS +
                    " because there aren't that many");
            }
        } else {
            Log.INFO("using default value for property " +
                HoneycombTestConstants.PROPERTY_HOW_MANY_FRAGS +
                " of " + defaultHowManyFrags);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_FSSETTLE);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_FSSETTLE +
                " was specified. Using " + s + " as " +
                HoneycombTestConstants.PROPERTY_FSSETTLE);
            defaultFilesystemSettleTime = Long.parseLong(s);
        } else {
            Log.INFO("using default value for property " +
                HoneycombTestConstants.PROPERTY_FSSETTLE + 
                " of " + filesystemSettleTime);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_SLEEP_TIME);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_SLEEP_TIME +
                " was specified. Using " + s + " as " +
                HoneycombTestConstants.PROPERTY_SLEEP_TIME);
            defaultsleeptime = Long.parseLong(s);
        } else {
            Log.INFO("using default value for property " +
                HoneycombTestConstants.PROPERTY_SLEEP_TIME + " of " +
                defaultsleeptime);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_WHICH_FRAGS);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_WHICH_FRAGS +
                " was specified. Using " + s + " " +
                HoneycombTestConstants.PROPERTY_WHICH_FRAGS);
            defaultWhichFrags = s;
        } else {
            Log.INFO("using default value for property " +
                HoneycombTestConstants.PROPERTY_WHICH_FRAGS + " of " +
                defaultWhichFrags);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_PICK_FRAGS);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_PICK_FRAGS +
                " was specified. Using " + s + " " +
                HoneycombTestConstants.PROPERTY_PICK_FRAGS);
            defaultPickFrags = s;
        } else {
            Log.INFO("using default value for property " +
                HoneycombTestConstants.PROPERTY_PICK_FRAGS +
                " of " + defaultPickFrags);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_NO_RETRIEVE);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_NO_RETRIEVE +
                " was specified.");
            defaultnoretrieve = true;
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_NO_RETRIEVEMD);
        if (s != null) {
            Log.INFO("Property " +
                HoneycombTestConstants.PROPERTY_NO_RETRIEVEMD +
                " was specified.");
            defaultnoretrievemd = true;
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_NO_DELETE);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_NO_DELETE +
                " was specified.");
            defaultnodelete = true;
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_NO_SECONDDELETE);
        if (s != null) {
            Log.INFO("Property " +
                HoneycombTestConstants.PROPERTY_NO_SECONDDELETE +
                " was specified.");
            defaultnoseconddelete = true;
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_NO_ADDMD);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_NO_ADDMD +
                " was specified.");
            defaultnoaddmd = true;
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_NO_RESTORE);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_NO_RESTORE +
                " was specified.");
            defaultnorestore = true;
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_LONGRUN);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_LONGRUN +
                " was specified.");
            defaultlongrun = true;
            longrun = defaultlongrun;
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_SHORTRUN);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_SHORTRUN +
                " was specified.");
            defaultshortrun = true;
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_DONTSKIP);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_DONTSKIP +
                " was specified.");
            defaultdontskip = true;
            dontskip = true;
        }
        
        if (!testBed.rmiEnabled()) {
            throw new HoneycombTestException(
                "Property " + HoneycombTestConstants.PROPERTY_RMI +
                " does not have value yes. This test case cannot run " +
                "without RMI.");
            
        }

        // setup passed
        self.testPassed();
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    public void setDefaults() {
        mdoid = null;
        dataoid = null;
        howManyObjs = defaultHowManyObjs;
        howManyFrags = defaultHowManyFrags;
        pickFrags = defaultPickFrags;
        filesize = defaultfilesize;
        filesystemSettleTime = defaultFilesystemSettleTime;
        sleeptime = defaultsleeptime;
        whichFrags = defaultWhichFrags;
        noretrieve = defaultnoretrieve;
        noretrievemd = defaultnoretrievemd;
        nodelete = defaultnodelete;
        noseconddelete = defaultnoseconddelete;
        noaddmd = defaultnoaddmd;
        norestore = defaultnorestore;
        shortrun = defaultshortrun;
        longrun = defaultlongrun;
        dontskip = defaultdontskip;
    }

    public String getTestName(String s) {
        return ("DeleteMissingFrags-" + s);
    }

    public String getTestParams() {
        return (
            HoneycombTestConstants.PROPERTY_HOW_MANY_OBJS + "=" + howManyObjs +
            "; " + HoneycombTestConstants.PROPERTY_HOW_MANY_FRAGS + "=" + howManyFrags +
            "; " + HoneycombTestConstants.PROPERTY_PICK_FRAGS + "=" + pickFrags +
            "; " + HoneycombTestConstants.PROPERTY_FILESIZE + "=" + filesize +
            "; " + HoneycombTestConstants.PROPERTY_SLEEP_TIME + "=" + sleeptime +
            "; " + HoneycombTestConstants.PROPERTY_FSSETTLE + "=" + filesystemSettleTime +
            "; " + HoneycombTestConstants.PROPERTY_WHICH_FRAGS + "=" + whichFrags +
            "; " + HoneycombTestConstants.PROPERTY_NO_RETRIEVE + "=" + noretrieve +
            "; " + HoneycombTestConstants.PROPERTY_NO_RETRIEVEMD + "=" + noretrievemd +
            "; " + HoneycombTestConstants.PROPERTY_NO_DELETE + "=" + nodelete +
            "; " + HoneycombTestConstants.PROPERTY_NO_SECONDDELETE + "=" + noseconddelete +
            "; " + HoneycombTestConstants.PROPERTY_NO_ADDMD + "=" + noaddmd +
            "; " + HoneycombTestConstants.PROPERTY_NO_RESTORE + "=" + norestore +
            "; " + HoneycombTestConstants.PROPERTY_SHORTRUN + "=" + shortrun +
            "; " + HoneycombTestConstants.PROPERTY_LONGRUN + "=" + longrun);
    }

    public void setTagsAndBugs(TestCase self) {
        //addTag(Tag.REGRESSION);
        // XXX
        self.addTag(HoneycombTag.DELETE);
        self.addTag(HoneycombTag.JAVA_API);
        self.addTag(Tag.NORUN);  // since it uses rmi
        //addTag(Tag.SMOKE);
        // XXX filesize
        //addTag(Tag.QUICK);

        if (howManyFrags == 2 && whichFrags != MD_FRAGS) {
            addBug("6292577",
                "delete of md object when two frags of data object are " +
                "missing fails unexpectedly");
        }

        if (filesize > HoneycombTestConstants.OA_MAX_CHUNK_SIZE &&
            !noaddmd) {
            addBug("6293141",
                "addMetadata doesn't increment all chunks in the referee " +
                "for multichunk files");
        }
    }
 
    public void processTestResult(TestCase self, int status) 
                                                 throws HoneycombTestException {
        // XXX maybe only do this if test fails?
        printFragmentInfo("After test run");
        if (status == PASSED) {
            self.testPassed();
        } else if (status == FAILED) {
            self.testFailed();
        } else if (status == SKIPPED) {
            self.testSkipped();
        } else {
            throw new HoneycombTestException("invalid return status " + status);
        }
    }

    public void printFragmentInfo(String context)
                                                 throws HoneycombTestException {
        HOidInfo oidInfo;

        // XXX It seems like there needs to be some settle time for the attrs.
        // This sleep seems to yield better results.  A bit hacky, but what to
        // do?  Maybe we could try to run sync via RMI?  Alas, tried the RMI
        // sync without consistent results, but have added some consistency
        // checking as part of delete/restore fragments so hopefully this
        // will be better.
        sleep(filesystemSettleTime);

        if (mdoid != null) {
            oidInfo = testBed.getOidInfo(mdoid, true);
            Log.INFO(context + ", md frags look like this: " +
                oidInfo);
        } else {
            Log.INFO(context + ", mdoid was null");
        }

        if (dataoid != null) {
            oidInfo = testBed.getOidInfo(dataoid, true);
            Log.INFO(context + ", data frags look like this: " +
                oidInfo);
        } else {
            Log.INFO(context + ", dataoid was null");
        }
    }

    /**
     * This collects a variety of interesting test cases and runs them.  User
     * input overrides these cases.
     */
    public void testStoreDeleteFrags() throws HoneycombTestException {
        TestCase self;

        // Do all permutations if we are doing a long run
        if (longrun) {
            doAllPermutations();
            return;
        }

        setDefaults();
        self = new TestCase(this, getTestName("defaults"), getTestParams());
        setTagsAndBugs(self);
        if (self.excludeCase()) return;
        processTestResult(self, oneStoreDeleteFragsScenario());

        // Check if we should do only one run with user's values + defaults
        if (shortrun) {
            Log.INFO("skipping remainder of the cases; " + HoneycombTestConstants.PROPERTY_SHORTRUN +
                "=" + shortrun);
            return;
        }

        // Do select permutations (basic set + multichunk)
        setDefaults();
        filesize = 0;
        self = new TestCase(this, getTestName("size0"), getTestParams());
        setTagsAndBugs(self);
        if (self.excludeCase()) return;
        processTestResult(self, oneStoreDeleteFragsScenario());

        setDefaults();
        self = new TestCase(this, getTestName("nodelete"), getTestParams());
        nodelete = true;
        setTagsAndBugs(self);
        if (self.excludeCase()) return;
        processTestResult(self, oneStoreDeleteFragsScenario());

        setDefaults();
        self = new TestCase(this, getTestName("noseconddelete"), getTestParams());
        noseconddelete = true;
        setTagsAndBugs(self);
        if (self.excludeCase()) return;
        processTestResult(self, oneStoreDeleteFragsScenario());

        setDefaults();
        self = new TestCase(this, getTestName("noaddmd"), getTestParams());
        noaddmd = true;
        setTagsAndBugs(self);
        if (self.excludeCase()) return;
        processTestResult(self, oneStoreDeleteFragsScenario());

        setDefaults();
        self = new TestCase(this, getTestName("noretrievemd"), getTestParams());
        noretrievemd = true;
        setTagsAndBugs(self);
        if (self.excludeCase()) return;
        processTestResult(self, oneStoreDeleteFragsScenario());

        setDefaults();
        self = new TestCase(this, getTestName("noretrieve"), getTestParams());
        noretrieve = true;
        setTagsAndBugs(self);
        if (self.excludeCase()) return;
        processTestResult(self, oneStoreDeleteFragsScenario());

        // num fragments varied, default filesize
        for (int i = 0; i <= HoneycombTestConstants.OA_TOTAL_FRAGS; i++) {
            setDefaults();
            self = new TestCase(this, getTestName("fragmentsmissing" + i),
                getTestParams());
            setTagsAndBugs(self);
            if (self.excludeCase()) return;
            processTestResult(self, oneStoreDeleteFragsScenario());
        }

        // multichunk, 1 chunk
        setDefaults();
        Log.WARN("XXX setting noaddmd = true to avoid bug 6293141");
        noaddmd = true;
        filesize = HoneycombTestConstants.OA_MAX_CHUNK_SIZE + 1;
        self = new TestCase(this, getTestName("multichunk1"), getTestParams());
        setTagsAndBugs(self);
        if (self.excludeCase()) return;
        processTestResult(self, oneStoreDeleteFragsScenario());

        // multichunk, 2 chunks
        setDefaults();
        Log.WARN("XXX setting noaddmd = true to avoid bug 6293141");
        noaddmd = true;
        filesize = (2 * HoneycombTestConstants.OA_MAX_CHUNK_SIZE) + 1;
        self = new TestCase(this, getTestName("multichunk2"), getTestParams());
        setTagsAndBugs(self);
        if (self.excludeCase()) return;
        processTestResult(self, oneStoreDeleteFragsScenario());

        // num fragments varied, multi-chunk
        // XXX do this also for 2xMultiChunk?
        for (int i = 0; i < HoneycombTestConstants.OA_TOTAL_FRAGS; i++) {
            setDefaults();
            Log.WARN("XXX setting noaddmd = true to avoid bug 6293141");
            noaddmd = true;
            filesize = HoneycombTestConstants.OA_MAX_CHUNK_SIZE + 1;
            self = new TestCase(this, getTestName("fragmentsmissing" + i +
                "multichunk"), getTestParams());
            setTagsAndBugs(self);
            if (self.excludeCase()) return;
            processTestResult(self, oneStoreDeleteFragsScenario());
        }
    }

    /**
     * Iterate through all combos...huge matrix.  This is good for a long
     * weekend test.
     */
    public void doAllPermutations() throws HoneycombTestException {
        long[] filesizeList = { defaultfilesize, 0,
            (HoneycombTestConstants.OA_MAX_CHUNK_SIZE + 1),
            ((2 * HoneycombTestConstants.OA_MAX_CHUNK_SIZE) + 1) };
        // we explicitly order the frags like this to hit the interesting cases
        // earlier, instead of later
        int[] fragsList = { 1, 2, 3, 4, 5, 6, 7, 0 };
        String[] whichFragsList = { BOTH_FRAGS, MD_FRAGS, DATA_FRAGS };
        boolean[] retrieveList = { false, true };
        boolean[] retrievemdList = { false, true };
        boolean[] deleteList = { false, true };
        boolean[] seconddeleteList = { false, true };
        boolean[] addmdList = { false, true };
        // for now, skip no restore

        TestCase self;
        int caseCount = 0;
        setDefaults();

        // XXX pickFrags: random vs fixed?  data vs parity?

        for (int a = 0; a < filesizeList.length; a++) {
         filesize = filesizeList[a];
         for (int b = 0; b < fragsList.length; b++) {
          howManyFrags = fragsList[b];
          for (int c = 0; c < whichFragsList.length; c++) {
           whichFrags = whichFragsList[c];
           for (int d = 0; d < retrieveList.length; d++) {
            noretrieve = retrieveList[d];
            for (int e = 0; e < retrievemdList.length; e++) {
             noretrievemd = retrievemdList[e];
             for (int f = 0; f < deleteList.length; f++) {
              nodelete = deleteList[f];
              for (int g = 0; g < seconddeleteList.length; g++) {
               noseconddelete = seconddeleteList[g];
               for (int h = 0; h < addmdList.length; h++) {
                noaddmd = addmdList[h];

                howManyObjs = 1; // Should we try doing more than 1?
                self = new TestCase(this, getTestName("matrix" + caseCount++),
                    getTestParams());
                setTagsAndBugs(self);
                if (self.excludeCase()) return;

                processTestResult(self, oneStoreDeleteFragsScenario());
               }
              }
             }
            }
           }
          }
         }
        }
    }

    /**
     * Execute one scenario of storing, deleting frags, deleting, etc, per
     * the configured variables.  Returns PASSED, FAILED, or SKIPPED.
     */
    public int oneStoreDeleteFragsScenario() throws HoneycombTestException {
        // XXX maybe iterate through all "whichFrag" modes?
        while (howManyObjs-- > 0) {
            CmdResult cr;
            boolean shouldsucceeddel;
            boolean shouldsucceedret;
            boolean shouldsucceedretmd;
            boolean shouldsucceedaddmd;
            boolean deletesucceeded = false;
            boolean randomFrags;
            String hash = null;
            List fragsToDelete;
            HashSet fragsHashSet = null;
            int count = 0;

            Log.INFO("Calling store for file of size " + filesize);
            cr = storeAsStream(filesize, smallMD);
            if (!cr.pass) {
                cr.logExceptions("store");
                return (FAILED);
            }
            mdoid = cr.mdoid;
            dataoid = cr.dataoid;
            hash = cr.datasha1;
            Log.INFO("[Object " + ++count +  "] store returned " + mdoid +
                " (dataoid " + dataoid + "; hash " + hash + ")");

            printFragmentInfo("After store and before deleteFragments");

            // pick how to delete frags
            randomFrags = false;
            if (pickFrags.equals(FIXED_FRAGS)) {
                randomFrags = false;
            } else if (pickFrags.equals(RANDOM_FRAGS)) {
                randomFrags = true;
            } else {
                // try to parse something that looks like this: 0,4,6
                // into a HashSet
                try {
                    String[] fragIds = pickFrags.split(",");

                    fragsHashSet = new HashSet();
                    for (int i = 0; i < fragIds.length; i++) {
                        int id = Integer.parseInt(fragIds[i]);
                        if (id >= HoneycombTestConstants.OA_TOTAL_FRAGS) {
                            throw new HoneycombTestException(
                                "invalid frag id " + id +
                                "; must be less than " +
                                HoneycombTestConstants.OA_TOTAL_FRAGS);
                        }
                        fragsHashSet.add(new Integer(id));
                    }

                    if (fragsHashSet.size() != howManyFrags) {
                        throw new HoneycombTestException("specified " +
                            howManyFrags + " frags but only provided " + 
                            fragsHashSet);
                    }
                } catch (Throwable t) {
                    throw new HoneycombTestException("invalid value for " +
                        HoneycombTestConstants.PROPERTY_PICK_FRAGS +
                        ": " + pickFrags + "(" + t + ")");
                }
            }

            // pick frags to delete
            if (whichFrags.equals(BOTH_FRAGS)) {
                fragsToDelete = pickFragsToDelete(howManyFrags,
                    testBed.getOidInfo(mdoid, true),
                    testBed.getOidInfo(dataoid, true),
                    randomFrags, fragsHashSet);
            } else if (whichFrags.equals(MD_FRAGS)) {
                fragsToDelete = pickFragsToDelete(howManyFrags,
                    testBed.getOidInfo(mdoid, true),
                    null, randomFrags, fragsHashSet);
            } else if (whichFrags.equals(DATA_FRAGS)) {
                fragsToDelete = pickFragsToDelete(howManyFrags,
                    null, testBed.getOidInfo(dataoid, true),
                    randomFrags, fragsHashSet);
            } else {
                throw new HoneycombTestException("invalid value for " +
                    HoneycombTestConstants.PROPERTY_WHICH_FRAGS +
                    ": " + whichFrags);
            }

            // have the rmi magic do its deleting action
            Log.INFO("Deleting frags from the list...this can take awhile " +
                "due to the filesystem consistency validation post-delete");

            try {
                testBed.deleteFragments(fragsToDelete);
            } catch (Throwable t) {
                // consider this a test failure--expected results will be
                // affected by this.  Sometimes DLM processes interfere
                // and cause this failure
                //
                // actually, for now, let's call this skipped, because
                // it's not really a failure, DLM is just making the result
                // invalid
                //printFragmentInfo("After failed deleteFragments attempt");
                Log.ERROR("Failed to delete fragments: " + t);
                Log.INFO("Considering this as a skipped result because " +
                    "most likely crawl is just doing its thing");
                return (SKIPPED);
            }

            printFragmentInfo("After deleteFragments");

            // Sometimes it is good to take a breather...let crawl/recover
            // do something interesting?
            Log.DEBUG("sleeping " + sleeptime);
            sleep(sleeptime);

            // figure out if actions should succeed or not based on
            // num frags.  Things like retrieve MD might succeed if only data
            // frags are affected, so this is a bit complicated.
            if (howManyFrags > HoneycombTestConstants.OA_PARITY_FRAGS) {
                Log.INFO(howManyFrags + " frags is more than the number " +
                    "of configured parity frags: " +
                    HoneycombTestConstants.OA_PARITY_FRAGS);
                shouldsucceedret = false;
                shouldsucceedaddmd = false;
                shouldsucceeddel = false;
                if (whichFrags == DATA_FRAGS) {
                    shouldsucceedretmd = true;
                } else {
                    shouldsucceedretmd = false;
                }
            } else {
                Log.INFO(howManyFrags + " frags is fewer than the number " +
                    "of configured parity frags: " +
                    HoneycombTestConstants.OA_PARITY_FRAGS);
                shouldsucceedret = true;
                shouldsucceeddel = true;
                shouldsucceedretmd = true;
                shouldsucceedaddmd = true;
            }

            if (!noaddmd) {
                Log.INFO("adding md to " + mdoid);
                cr = addMetadata(mdoid, smallMD);
                String newmd = cr.mdoid;
                Log.INFO("addMD returned " + newmd +
                    " with pass=" + cr.pass +
                    "; shouldsucceed=" + shouldsucceedaddmd);
                if (cr.pass != shouldsucceedaddmd) {
                    if (!cr.pass) {
                        cr.logExceptions("addMD");
                    } else {
                        Log.ERROR("unexpectedly passed");
                    }
                    return (FAILED);
                }

                // immediately remove this so we don't have an extra md
                // ref for our data obj.  If the addMD succeeded, the 
                // delete should always succeed.
                if (newmd != null) {
                    Log.INFO("delete newly added md obj " + newmd);
                    cr = delete(newmd);
                    if (!cr.pass) {
                        cr.logExceptions("delete");
                        return (FAILED);
                    }
                    Log.INFO("delete succeeded on new md " + newmd);
                }
            }

            if (!noretrievemd) {    
                Log.INFO("retrieve md to " + mdoid);
                cr = getMetadata(mdoid);
                Log.INFO("retrieve md returned " + cr.mdMap +
                    " with pass=" + cr.pass +
                    "; shouldsucceed=" + shouldsucceedretmd);
                if (cr.pass != shouldsucceedretmd) {
                    if (!cr.pass) {
                        cr.logExceptions("retrieveMD");
                    } else {
                        Log.ERROR("unexpectedly passed");
                    }
                    return (FAILED);
                }
            }

            if (!noretrieve) {
                Log.INFO("retrieve to " + mdoid);
                cr = retrieveAsStream(mdoid, filesize);
                Log.INFO("retrieve returned with pass=" + cr.pass +
                    "; shouldsucceed=" + shouldsucceedret);
                if (cr.pass != shouldsucceedret) {
                    if (!cr.pass) {
                        cr.logExceptions("retrieve");
                    } else {
                        Log.ERROR("unexpectedly passed");
                    }
                    return (FAILED);
                }
                if (cr.pass && !hash.equals(cr.datasha1)) {
                    Log.ERROR("got unexpected hash of " + cr.datasha1 +
                        "; expected hash " + hash);
                    return (FAILED);
                } else if (cr.pass) {
                    Log.INFO("retrieve succeeded; hashes matched (" +
                        cr.datasha1 + ")");
                }
            }

            if (!nodelete) {
                Log.INFO("delete md obj " + mdoid);
                cr = delete(mdoid);
                Log.INFO("delete returned with pass=" + cr.pass +
                    "; shouldsucceed=" + shouldsucceeddel);
                if (cr.pass != shouldsucceeddel) {
                    if (!cr.pass) {
                        cr.logExceptions("delete");
                    } else {
                        Log.ERROR("unexpectedly passed");
                    }
                    return (FAILED);
                }
                if (cr.pass) {
                    // If we succeeded at delete, then remember this so we
                    // can set expected status correctly for future actions
                    deletesucceeded = true;
                }
            }

            printFragmentInfo("Before restoreFragments");

            if (norestore) {
                Log.INFO("skipping restore test and validation; exiting early");
                return (PASSED);
            }

            Log.DEBUG("sleeping " + sleeptime);
            sleep(sleeptime);

            Log.INFO("Restoring frags from the list...this can take awhile " +
                "due to the filesystem consistency validation post-restore");

            try {
                testBed.restoreFragments(fragsToDelete);
            } catch (Throwable t) {
                // consider this a test failure--expected results will be
                // affected by this.  Sometimes DLM processes interfere
                // and cause this failure
                //
                // actually, for now, let's call this skipped, because
                // it's not really a failure, DLM is just making the result
                // invalid
                //printFragmentInfo("After failed restoreFragments attempt");
                Log.ERROR("Failed to restore fragments: " + t);
                Log.INFO("Considering this as a skipped result because " +
                    "most likely crawl is just doing its thing");
                return (SKIPPED);
            }

            printFragmentInfo("After restoreFragments");

            // figure out if actions should succeed or not based on
            // num frags and whether we deleted anything
            if (howManyFrags > HoneycombTestConstants.OA_PARITY_FRAGS) {
                Log.INFO(howManyFrags + " frags is more than the number " +
                    "of configured parity frags: " +
                    HoneycombTestConstants.OA_PARITY_FRAGS);
                // because we now have restored enough frags, these actions
                // should now succeed
                shouldsucceeddel = true;
                shouldsucceedret = true;
                shouldsucceedretmd = true;
                shouldsucceedaddmd = true;

                if (!nodelete &&
                    howManyFrags < HoneycombTestConstants.OA_DATA_FRAGS &&
                    whichFrags == DATA_FRAGS) {
                    // we did a partial delete of enough frags to make
                    // it a partial delete and subsequent deletes fail
                    shouldsucceeddel = false;
                    shouldsucceedret = false;
                }
                    
                if (!nodelete && whichFrags == DATA_FRAGS) {
                    // md obj would have been deleted successfully
                    shouldsucceedretmd = false;
                    shouldsucceedaddmd = false;
                    shouldsucceeddel = false;
                    shouldsucceedret = false;
                }
            } else {
                Log.INFO(howManyFrags + " frags is fewer than the number " +
                    "of configured parity frags: " +
                    HoneycombTestConstants.OA_PARITY_FRAGS);
                if (deletesucceeded) {
                    // delete already succeded, shouldn't succeed again.
                    shouldsucceeddel = false;
                    shouldsucceedret = false;
                    shouldsucceedretmd = false;
                    shouldsucceedaddmd = false;
                } else {
                    // delete hasn't been done, it will succeed now
                    shouldsucceeddel = true;
                    shouldsucceedret = true;
                    shouldsucceedretmd = true;
                    shouldsucceedaddmd = true;
                }
            }
 
            if (!noaddmd) {
                Log.INFO("adding md to " + mdoid);
                cr = addMetadata(mdoid, smallMD);
                String newmd = cr.mdoid;
                Log.INFO("addMD returned " + newmd +
                    " with pass=" + cr.pass +
                    "; shouldsucceed=" + shouldsucceedaddmd);
                if (cr.pass != shouldsucceedaddmd) {
                    if (!cr.pass) {
                        cr.logExceptions("addMD");
                    } else {
                        Log.ERROR("unexpectedly passed");
                    }
                    return (FAILED);
                }

                // immediately remove this so we don't have an extra md
                // ref for our data obj.  If the addMD succeeded, the 
                // delete should always succeed.
                if (newmd != null) {
                    Log.INFO("delete newly added md obj " + newmd);
                    cr = delete(newmd);
                    if (!cr.pass) {
                        cr.logExceptions("delete");
                        return (FAILED);
                    }
                    Log.INFO("delete succeeded on new md " + newmd);
                }
            }

            if (!noretrievemd) {
                Log.INFO("retrieve md to " + mdoid);
                cr = getMetadata(mdoid);
                Log.INFO("retrieve md returned " + cr.mdMap +
                    " with pass=" + cr.pass +
                    "; shouldsucceed=" + shouldsucceedretmd);
                if (cr.pass != shouldsucceedretmd) {
                    if (!cr.pass) {
                        cr.logExceptions("retrieveMD");
                    } else {
                        Log.ERROR("unexpectedly passed");
                    }
                    return (FAILED);
                }
            }

            if (!noretrieve) {
                Log.INFO("retrieve to " + mdoid);
                cr = retrieveAsStream(mdoid, filesize);
                Log.INFO("retrieve returned with pass=" + cr.pass +
                    "; shouldsucceed=" + shouldsucceedret);
                if (cr.pass != shouldsucceedret) {
                    if (!cr.pass) {
                        cr.logExceptions("retrieve");
                    } else {
                        Log.ERROR("unexpectedly passed");
                    }
                    return (FAILED);
                }
                if (cr.pass && !hash.equals(cr.datasha1)) {
                    Log.ERROR("got unexpected hash of " + cr.datasha1 +
                        "; expected hash " + hash);
                    return (FAILED);
                } else if (cr.pass) {
                    Log.INFO("retrieve succeeded; hashes matched (" +
                        cr.datasha1 + ")");
                }
            }

            if (!nodelete && !noseconddelete) {
                Log.INFO("delete md obj " + mdoid);
                cr = delete(mdoid);
                Log.INFO("delete returned with pass=" + cr.pass +
                    "; shouldsucceed=" + shouldsucceeddel);
                if (cr.pass != shouldsucceeddel) {
                    if (!cr.pass) {
                        cr.logExceptions("delete");
                    } else {
                        Log.ERROR("unexpectedly passed");
                    }
                    return (FAILED);
                }
                if (cr.pass) {
                    // If we succeeded at delete, then remember this so we
                    // can set expected status correctly for future actions
                    deletesucceeded = true;
                }
            }

            if (nodelete || noseconddelete) {
                Log.INFO("if we didn't delete or do a second delete, " +
                    "nothing left to check; exiting");
                return (PASSED);
            }

            // Verify all frags now look deleted.  However, we allow the frags
            // we've restored to be non-deleted because, currrently, re-delete
            // does not do an inline delete of missed frags.  Crawl does seem
            // to clean these frags up, though.  
            List fragsFound = new ArrayList();
            if (deletesucceeded &&
                (!onDiskFragsLookDeleted(mdoid, fragsToDelete, fragsFound) || 
                 !onDiskFragsLookDeleted(dataoid, fragsToDelete, fragsFound))) {
                Log.ERROR("Not all frags look deleted");
                return (FAILED);
            }

            for (int i = 0; i < fragsFound.size(); i++) {
                // XXX maybe verify these get crawled if we can force a crawl
                Log.WARN("Found and ignored non-deleted frag " +
                    fragsFound.get(i));
            }

            Log.INFO("All frags look deleted for both objects");
        }

        return (PASSED);
    }
}
