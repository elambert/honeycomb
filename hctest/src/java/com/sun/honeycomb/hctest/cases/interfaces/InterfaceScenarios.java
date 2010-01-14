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



package com.sun.honeycomb.hctest.cases.interfaces;

import java.util.ArrayList;
import java.util.ListIterator;
import java.util.HashMap;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.cases.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.client.*;

/**
 * Test cases that validate the basic actions (store, retrieve, delete, query)
 * work when combined in interesting ways.
 */
public class InterfaceScenarios extends HoneycombLocalSuite {
    private static final String PROPERTY_INCLUDE_RANDOM = "include_random";
    private static final String PROPERTY_RANDOM_ONLY = "random_only";
    private static final long USE_ALL_SIZES = -2;

    // property defaults
    public long startingFilesize = 0;
    public long endingFilesize = Long.MAX_VALUE;

    public InterfaceScenarios() {
        super();
    }

    public String help() {
        return(
            "\tQuery the system for all objects and call delete\n" +
            "\tOptional -ctx args:\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_FILESIZE +
                "=n (n is the size of the file in bytes; it can be a list\n" +
                 "\t\t\tor " + USE_ALL_SIZES + " to do all size cases)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_STARTINGFILESIZE +
                "=n (n is size to start with in list of all filesizes)\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_ENDINGFILESIZE +
                "=n (n is size to end with in list of all filesizes)\n" +
            "\t\t" + PROPERTY_INCLUDE_RANDOM + "\n" +
            "\t\t" + PROPERTY_RANDOM_ONLY + "\n" +
            "\t\t" + HoneycombTestConstants.PROPERTY_SEED +
                "=n (use n as the seed)\n"
            );
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");

        String s = getProperty(HoneycombTestConstants.PROPERTY_STARTINGFILESIZE);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_STARTINGFILESIZE +
                " was specified. Starting from " + s);
            startingFilesize = Long.parseLong(s);
        } else {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_STARTINGFILESIZE +
                " was not specified. Allowing sizes from " + startingFilesize);
        }

        s = getProperty(HoneycombTestConstants.PROPERTY_ENDINGFILESIZE);
        if (s != null) {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_ENDINGFILESIZE +
                " was specified. Ending at " + s);
            endingFilesize = Long.parseLong(s);
        } else {
            Log.INFO("Property " + HoneycombTestConstants.PROPERTY_ENDINGFILESIZE +
                " was not specified. Allowing sizes up to " + endingFilesize);
        }

        super.setUp();
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }

    /**
     * Iterate through the scenarios and different interfaces and validate
     * that the "right thing" happens.
     */
    public void testInterfaces() throws Throwable {
        HoneycombInterface hi;
        HoneycombInterface hiForFileReuse = null;
        ArrayList interfaceObjs = new ArrayList();
        ArrayList filesizeCases = new ArrayList();
        ArrayList filesizeDefaultCases = new ArrayList();
        ArrayList scenarios = new ArrayList();
        int action;
        String actionString;
        boolean actionSucceeded;
        boolean expectedResult;
        boolean includeCommonScenarios = true;
        boolean includeRandomScenarios = false;
        long defaultFileSize = 1000;

        TestCase self = createTestCase("Interfaces entry");
        self.addTag(Tag.POSITIVE);
        self.addTag(Tag.REGRESSION);
        self.addTag(HoneycombTag.DELETE);
        self.addTag(HoneycombTag.JAVA_API);
        if (self.excludeCase()) return;


        
        // XXX dimensions:
        // - Which interface to use (basic java API, NFS, etc).  Not all actions
        // apply to all interfaces.
        // - filesize -- iterate of list of size "classes" for each below
        // - metadata specified
        //      - metadata streamed vs map
        // - number of MD links (relevant for delete) (part of actions below?)
        // - order of actions  (array of operations)
        //      1. store
        //      2. store, retrieve
        //      3. store, range retrieve, delete, retrieve
        //      4. store, query, delete, retreive
        //      5. store, addMD, deleteMD, query
        //      6. etc.


        // Set the seed for this test to make the same test run repeatable
        if (seed != HoneycombTestConstants.INVALID_SEED) {
            FileSizeCase.setSeed(seed);
        } else {
            seed = System.currentTimeMillis();
            FileSizeCase.setSeed(seed);
        }

        self.postMetric(new Metric("Seed", "seed", seed));
        // the "Interfaces entry" test doesn't really do anything but allow us
        // to exit early if we are excluded, so mark it passed here so we
        // don't leave a dangling result
        self.testPassed("This is just a place holder result");

        // See if we only want random or common scenarios
        String s = getProperty(PROPERTY_RANDOM_ONLY);
        if (s != null) {
            Log.INFO("Property " + PROPERTY_RANDOM_ONLY + " was specified. " +
                "Using only random scenarios.");
            includeCommonScenarios = false;
            includeRandomScenarios = true;
        }

        s = getProperty(PROPERTY_INCLUDE_RANDOM);
        if (s != null) {
            Log.INFO("Property " + PROPERTY_INCLUDE_RANDOM + " was specified. " +
                "Using only common scenarios.");
            includeRandomScenarios = true;
        }

        filesizeDefaultCases.add(new Long(defaultFileSize));

        // If a user specified certain filesizes, use them.
        // Otherwise use our default cases.
        ListIterator sizeIter = null;
        ArrayList al = getFilesizeList();
        if (al != null &&  al.size() > 0) {
            long firstSize = ((Long) al.get(0)).longValue();
            if (firstSize == USE_ALL_SIZES) {
                Log.INFO("Using the API file size test cases");
                sizeIter = HCFileSizeCases.getSizeList().listIterator();
            } else {
                Log.INFO("Using the user specified cases");
                sizeIter = getFilesizeList().listIterator();
            }
        } else {
            Log.INFO("Using the default file size case");
            sizeIter = filesizeDefaultCases.listIterator();
        }

        // Create file size cases for each
        while (sizeIter.hasNext()) {
            long size = ((Long)sizeIter.next()).longValue();

            // check to see if we should skip this size due to the specified
            // startingFilesize
            if (size < startingFilesize) {
                Log.DEBUG("skipping size " + size + " because we are using " +
                    HoneycombTestConstants.PROPERTY_STARTINGFILESIZE + "=" +
                    startingFilesize);
                continue;
            }

            // check to see if we should skip this size due to the specified
            // endingFilesize
            if (size > endingFilesize) {
                Log.DEBUG("skipping size " + size + " because we are using " +
                    HoneycombTestConstants.PROPERTY_ENDINGFILESIZE + "=" +
                    endingFilesize);
                continue;
            }

            filesizeCases.add(new FileSizeCase(size));
        }

        if (includeCommonScenarios) {
            addCommonScenarios(scenarios);
        }

        if (includeRandomScenarios) {
            addRandomScenarios(scenarios);
        }

        ListIterator j = filesizeCases.listIterator();
        FileSizeCase fsc;
        while (j.hasNext()) {
            fsc = (FileSizeCase)j.next();
            
            // Need to clean up the creation of cases to be smarter
            // for large runs, but printing the size helps read the 
            // logs to see what tests this run is doing and gauge
            // progress
            Log.INFO("Processing file size case " + fsc.getSize());


            // Basic API, no MD
            /* XXX this test runs too slowly as it is.
             * for now, only test with some MD fields
             * set and skip the empty case to allow
             * test to run more quickly.
            hi = new HoneycombBasicJavaAPI(testBed.getDataVIP(),
                testBed.getDataPort());
            hi.filesize = fsc.getSize();
            hi.filesizetype = fsc.name;
            hi.seed = seed;
            hi.initialMetadataMap = HoneycombInterface.EMPTYMD;
            hi.initMetadata();
            interfaceObjs.add(hi);
            */

            // Basic API, with MD
            hi = new HoneycombBasicJavaAPI(testBed.getDataVIP(),
                testBed.getDataPort());
            hi.filesize = fsc.getSize();
            hi.filesizetype = fsc.name;
            hi.seed = seed;
            hi.initialMetadataMap = HoneycombInterface.DEFAULTMD;
            hi.initMetadata();
            interfaceObjs.add(hi);

/* XXX these all fail!
            // 6216452: a call to ObjectArchive.storeObject(dataChannel) fails
            // due to "cache [null] is unknown"

            Bug bug6216452 = new Bug("6216452",
                "a call to storeObject() with MD channel or no MD fails " +
                "due to \"cache [null] is unknown\"");

            // Advanced API, no MD
            hi = new HoneycombAdvancedJavaAPI(testBed.getDataVIP(),
            testBed.getDataPort());
            hi.filesize = fsc.getSize();
            hi.filesizetype = fsc.name;
            hi.seed = seed;
            hi.initialMetadataMap = HoneycombInterface.NOMD;
            hi.initMetadata();
            interfaceObjs.add(hi);
*/

            if (hiForFileReuse != null) {
                // XXX clean up file here
                // XXX only if test passed???

                hiForFileReuse = null;
            }
        }

        // Iterate through all interfaces to test
        ListIterator i = interfaceObjs.listIterator();
        while (i.hasNext()) {
            hi = (HoneycombInterface)i.next();

            if (reuseFiles) {
                if (hiForFileReuse != null) {
                    hi.reuseFiles(hiForFileReuse);
                } else {
                    hiForFileReuse = hi;
                }
            }

            // Iterate through all scenarios to test
            ListIterator k = scenarios.listIterator();
            Scenario scen;
            while (k.hasNext()) {
                scen = (Scenario)k.next();
                String errmsg = null;
                boolean passed = true;

                hi.startNewScenario(scen);
                hi.testcase = createTestCase(scen.description,
                    hi.getCurrentParamsTruncated(scen));
                scen.addTags(hi.testcase);

                // add bugs if there are any
                if (hi.bugs != null && hi.bugs.size() > 0) {
                    ListIterator bugListIterator = hi.bugs.listIterator();
                    while (bugListIterator.hasNext()) {
                        hi.testcase.addBug((Bug)bugListIterator.next());
                    }
                }

                int actionIndex = 0;
                int actionIndexMax = scen.getActionListLength() - 1;

                while ((action = scen.getNextAction()) !=
                    Scenario.INVALID_ACTION) {

                    // do pre-work to set up action
                    actionString = Scenario.getActionString(action);
                    hi.appendNewAction(actionString);

                    Log.INFO("-------------- performing action at index " +
                        actionIndex++ + " (out of " + actionIndexMax +
                        ") -------------------");

                    // XXX Don't create a new test case for each step,
                    // only one for the whole scenario.
                    //hi.testcase = createTestCase(hi.getCurrentProcedure(),
                    //                             hi.getCurrentParams());

                    // hi.testcase.addTag(Tag.EXPERIMENTAL);
                    // hi.testcase.addTag(Tag.NORUN,"Sarah's sandbox, stay out!");

                    //if (hi.testcase.excludeCase()) {
                    // The harness sets the skipped status based on
                    // making this call so we don't have to do anything.
                    //
                    // XXX but how do we skip things via regexp if we no
                    // longer have test cases for them???
                    //    break;
                    //}

                    // reset these variables....they are set again below
                    actionSucceeded = true;
                    expectedResult = true;

                    Log.INFO("Attempting action " + actionString +
                        " on object " + hi);
                    Log.INFO("Expected results: " +
                        hi.expectedResults.toString());

                    try {
                        switch (action) {
                        case Scenario.STORE_OBJECT:
                            expectedResult = hi.expectedResults.storeObject;
                            hi.storeObject();
                            Log.INFO("Store returned " + hi.IDString() +
                                " for filename " + hi.filename +
                                "; hashes are " + hi.hashString());

                            if (!reuseFiles) {
                                tmpFiles.add(hi.filename);
                            }

                            actionSucceeded = hi.verifyStoreObject();
                            break;
                        case Scenario.STORE_METADATA:
                            expectedResult =
                                hi.expectedResults.storeMetadata;
                            hi.storeMetadata();
                            Log.INFO("Post storeMetadata IDs are " +
                                hi.IDString() + "hashes are " +
                                hi.hashString());
                            actionSucceeded = hi.verifyStoreMetadata();
                            break;
                        case Scenario.RETRIEVE_OBJECT:
                            expectedResult =
                                hi.expectedResults.retrieveObject;
                            hi.retrieveObject();
                            tmpFiles.add(hi.filenameRetrieve);
                            actionSucceeded = hi.verifyRetrieveObject();
                            break;
                        case Scenario.RETRIEVE_METADATA:
                            expectedResult =
                                hi.expectedResults.retrieveMetadata;
                            hi.retrieveMetadata();
                            actionSucceeded = hi.verifyRetrieveMetadata();
                            break;

                            // XXX Dangling MD refs if delete object with adv
                            // api?
                        case Scenario.DELETE_OBJECT:
                            expectedResult =
                                hi.expectedResults.deleteObject;
                            hi.deleteObject();
                            Log.INFO("Post deleteObject IDs are " +
                                hi.IDString() + "hashes are " +
                                hi.hashString());
                            actionSucceeded = hi.verifyDeleteObject();
                            break;

                            // XXX Idempotence test for delete vs throwing
                            // XXX If there aren't non-deleted MD recs
                            // Does the data object get deleted implicitly?
                        case Scenario.DELETE_METADATA:
                            expectedResult =
                                hi.expectedResults.deleteMetadata;
                            hi.deleteMetadata();
                            Log.INFO("Post deleteMetadata IDs are " +
                                hi.IDString() + "hashes are " +
                                hi.hashString());
                            actionSucceeded = hi.verifyDeleteMetadata();
                            break;
                        case Scenario.QUERY:
                            expectedResult =
                                hi.expectedResults.query;
                            hi.query();
                            actionSucceeded = hi.verifyQuery();
                            break;
                        default:
                            throw new HoneycombTestException(
                                "Unknown action " + action);
                        }
                    } catch (HoneycombTestException hte) {
                        Log.ERROR("Failed to " + actionString +
                            " for " + hi +
                            " due to " + hte.getMessage());
                        actionSucceeded = false;
                    }

                    // Check if we should've succeeded or not
                    if (expectedResult == actionSucceeded) {
                        String msg = 
                            "Action " + actionString + " had result " +
                            Scenario.getPassFailString(actionSucceeded) +
                            ", which matched expected result of " +
                            Scenario.getPassFailString(expectedResult);
                        // XXX we no longer wish to post results for each
                        // step along the way.  Only for failures or at the
                        // end of a scenario.
                        //
                        // hi.testcase.testPassed(msg);
                        Log.INFO("SUCCESS: " + msg);
                    } else {
                        errmsg = 
                            "Action " + actionString + " had result " +
                            Scenario.getPassFailString(actionSucceeded) +
                            ", which DID NOT match expected result of " +
                            Scenario.getPassFailString(expectedResult);
                        Log.ERROR("\n~~~~~~~ Aborting scenario due to " +
                            "error ~~~~~~");
                        passed = false;
                        break;
                    }
                }

                // We already note failed results above.  Note a passed
                // result if everything looked good.
                if (passed) {
                    hi.testcase.testPassed("All actions were as expected");
                } else {
                    hi.testcase.testFailed(errmsg);
                }
            }

            hi.finalize();
        }
    }

    // Create a collection of scenarios to try for each object
    public void addCommonScenarios(ArrayList scenarios) {
        Scenario scenario = null;

        scenario = new Scenario("Basic Store and Retrieve Test");
        scenario.add(Scenario.STORE_OBJECT);
        scenario.add(Scenario.RETRIEVE_OBJECT);
        scenarios.add(scenario);

        scenario = new Scenario("Simplest Delete Object Scenario " +
            "(avoid caching bug)");
        scenario.add(Scenario.STORE_OBJECT);
        scenario.add(Scenario.DELETE_OBJECT);
        scenario.add(Scenario.RETRIEVE_OBJECT);
        scenarios.add(scenario);

        scenario = new Scenario("Validate Delete Object Scenario " +
            "(does not avoid caching bug)");
        // XXX try these with DELETE_OBJECT when it is supported
        // or for "negative" testing of NVOA
        scenario.add(Scenario.STORE_OBJECT);
        scenario.add(Scenario.RETRIEVE_OBJECT);
        scenario.add(Scenario.DELETE_OBJECT);
        scenario.add(Scenario.RETRIEVE_OBJECT);
        scenario.associateBug("6187592",
            "after deleting an object, a retrieve still works due to caching");
        scenarios.add(scenario);

        scenario = new Scenario("Validate Delete Object Scenario " +
            "(does not avoid caching bug)");
        scenario.add(Scenario.STORE_OBJECT);
        scenario.add(Scenario.RETRIEVE_METADATA);
        scenario.add(Scenario.DELETE_METADATA);
        scenario.add(Scenario.RETRIEVE_METADATA);
        scenarios.add(scenario);

        scenario = new Scenario("Simple Query");
        scenario.add(Scenario.STORE_OBJECT);
        scenario.add(Scenario.QUERY);
        scenarios.add(scenario);

        scenario = new Scenario("Deleted Query");
        scenario.add(Scenario.STORE_OBJECT);
        scenario.add(Scenario.DELETE_OBJECT);
        scenario.add(Scenario.QUERY);
        scenarios.add(scenario);

        scenario = new Scenario("Delete reference counting (MD version)");
        scenario.add(Scenario.STORE_OBJECT);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.DELETE_METADATA);
        scenario.add(Scenario.DELETE_METADATA);
        scenario.add(Scenario.RETRIEVE_OBJECT);
        scenario.associateBug("6275612", "delete reference counting with " +
            "more than two objects doesn't work as expected");
        // XXX bug not fixed
        // scenarios.add(scenario);

        scenario = new Scenario("Delete reference counting (Object version)");
        scenario.add(Scenario.STORE_OBJECT);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.DELETE_OBJECT);
        scenario.add(Scenario.DELETE_OBJECT);
        scenario.add(Scenario.RETRIEVE_OBJECT);
        scenario.associateBug("6275612", "delete reference counting with " +
            "more than two objects doesn't work as expected");
        // XXX bug not fixed
        // scenarios.add(scenario);

        scenario = new Scenario("StoreMetadata for Deleted Object");
        scenario.add(Scenario.STORE_OBJECT);
        scenario.add(Scenario.DELETE_OBJECT);
        scenario.add(Scenario.STORE_METADATA);
        scenario.associateBug("6229497",
            "storeMetadata hangs if you pass a deleted object id");
        scenarios.add(scenario);

        scenario = new Scenario("StoreMetadata for Deleted Metadata");
        scenario.add(Scenario.STORE_OBJECT);
        scenario.add(Scenario.DELETE_METADATA);
        scenario.add(Scenario.STORE_METADATA);
        scenarios.add(scenario);

        scenario = new Scenario("Simple Delete Metadata w/ Retrieve Validation");
        scenario.add(Scenario.STORE_OBJECT);
        scenario.add(Scenario.DELETE_METADATA);
        scenario.add(Scenario.RETRIEVE_METADATA);
        scenario.add(Scenario.RETRIEVE_OBJECT);
        scenarios.add(scenario);

        scenario = new Scenario("Extra Delete Metadata");
        scenario.add(Scenario.STORE_OBJECT);
        scenario.add(Scenario.DELETE_METADATA);
        scenario.add(Scenario.DELETE_METADATA);
        scenario.add(Scenario.STORE_METADATA);
        scenarios.add(scenario);

        scenario = new Scenario("Complicated Delete Scenario1");
        scenario.add(Scenario.STORE_OBJECT);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.RETRIEVE_METADATA);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.RETRIEVE_METADATA);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.RETRIEVE_METADATA);
        scenario.add(Scenario.DELETE_METADATA);
        scenario.add(Scenario.RETRIEVE_OBJECT);
        scenario.add(Scenario.DELETE_METADATA);
        scenario.add(Scenario.RETRIEVE_OBJECT);
        scenario.add(Scenario.DELETE_METADATA);
        scenario.add(Scenario.RETRIEVE_OBJECT);
        scenario.add(Scenario.DELETE_METADATA);
        scenario.add(Scenario.RETRIEVE_OBJECT);
        scenario.add(Scenario.DELETE_OBJECT);
        scenario.add(Scenario.RETRIEVE_OBJECT);
        scenario.add(Scenario.DELETE_OBJECT);
        scenario.add(Scenario.RETRIEVE_OBJECT);
        scenario.associateBug("6275612", "delete reference counting with " +
            "more than two objects doesn't work as expected");
        // XXX bug not fixed
        // scenarios.add(scenario);

        scenario = new Scenario("Complicated Delete Scenario2");
        scenario.add(Scenario.STORE_OBJECT);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.DELETE_METADATA);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.DELETE_OBJECT);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.DELETE_OBJECT);
        scenario.add(Scenario.DELETE_OBJECT);
        scenario.add(Scenario.DELETE_OBJECT);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.DELETE_METADATA);
        scenario.add(Scenario.STORE_METADATA);
        scenario.add(Scenario.DELETE_METADATA);
        scenario.add(Scenario.DELETE_OBJECT);
        scenario.add(Scenario.DELETE_OBJECT);
        scenario.add(Scenario.RETRIEVE_OBJECT);
        scenario.add(Scenario.DELETE_OBJECT);
        scenario.add(Scenario.RETRIEVE_OBJECT);
        scenario.associateBug("6275612", "delete reference counting with " +
            "more than two objects doesn't work as expected");
        // XXX bug not fixed
        // scenarios.add(scenario);
    }

    public void addRandomScenarios(ArrayList scenarios) {
        // Some random scenarios
        scenarios.add(Scenario.getRandomScenario(3, seed));
        scenarios.add(Scenario.getRandomScenario(6, seed));
        scenarios.add(Scenario.getRandomScenario(9, seed));
        scenarios.add(Scenario.getRandomScenario(12, seed));
        scenarios.add(Scenario.getRandomScenario(15, seed));
        scenarios.add(Scenario.getRandomScenario(18, seed));
        scenarios.add(Scenario.getRandomScenario(21, seed));
    }
}
