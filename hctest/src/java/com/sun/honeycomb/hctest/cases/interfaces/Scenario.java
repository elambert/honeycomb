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
import com.sun.honeycomb.hctest.HoneycombTag;
import com.sun.honeycomb.test.Bug;
import com.sun.honeycomb.test.TestCase;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Random;

/**
 * A class that manages a scenario, which can include multiple actions such as
 * store, retrieve, delete, and query.
 */
public class Scenario {
    public String description;
    public ArrayList bugs;
    private ArrayList actions;
    private ListIterator iter;

    public static final String PASS_STRING = "PASS";
    public static final String FAIL_STRING = "FAIL";

    public static final int INVALID_ACTION = 0;
    public static final String INVALID_ACTION_STRING = "INVALID_ACTION";
    public static final int STORE_OBJECT = 1;
    public static final String STORE_OBJECT_STRING = "STORE_OBJ";
    public static final int STORE_METADATA = 2;
    public static final String STORE_METADATA_STRING = "STORE_MD";
    public static final int RETRIEVE_OBJECT = 3;
    public static final String RETRIEVE_OBJECT_STRING = "RETR_OBJ";
    public static final int RETRIEVE_METADATA = 4;
    public static final String RETRIEVE_METADATA_STRING = "RETR_MD";
    public static final int DELETE_OBJECT = 5;
    public static final String DELETE_OBJECT_STRING = "DEL_OBJ";
    public static final int DELETE_METADATA = 6;
    public static final String DELETE_METADATA_STRING = "DEL_MD";
    public static final int QUERY = 7;
    public static final String QUERY_STRING = "QUERY";
    public static final int MAX_ACTION = QUERY;

    public static String getActionString(int action) {
        switch (action) {
        case STORE_OBJECT:
            return (STORE_OBJECT_STRING);
        case STORE_METADATA:
            return (STORE_METADATA_STRING);
        case RETRIEVE_OBJECT:
            return (RETRIEVE_OBJECT_STRING);
        case RETRIEVE_METADATA:
            return (RETRIEVE_METADATA_STRING);
        case DELETE_OBJECT:
            return (DELETE_OBJECT_STRING);
        case DELETE_METADATA:
            return (DELETE_METADATA_STRING);
        case QUERY:
            return (QUERY_STRING);
        case INVALID_ACTION:
            return (INVALID_ACTION_STRING);
        default:
            return ("XXX_UNKNOWN_ACTION");
        }
    }
 
    public Scenario(String s) {
        actions = new ArrayList();
        bugs = new ArrayList();
        description = s;
    }

    public int getActionListLength() {
        return (actions.size());
    }

    public String getActionListString() {
        String actionListString = null;
        ListIterator li = actions.listIterator();

        while (li.hasNext()) {
            String action = (String)
                (getActionString((((Integer)li.next()).intValue())));

            if (actionListString != null) {
                actionListString += HoneycombInterface.DELIM + action;
            } else {
                actionListString = action;
            }
        }

        return (actionListString);
    }

    public void add(int i) {
        actions.add(new Integer(i));
    }

    public void associateBug(String bugid, String synopsis) {
        bugs.add(new Bug(bugid, synopsis));
    }

    public int getNextAction() {
        if (iter == null) {
            iter = actions.listIterator();
        }

        if (iter.hasNext()) {
            return (((Integer)iter.next()).intValue());
        } else {
            return (INVALID_ACTION);
        }
    }

    public void resetActionIterator() {
        iter = null;
    }

    public static String getPassFailString(boolean b) {
        if (b) {
            return (PASS_STRING);
        } else {
            return (FAIL_STRING);
        }
    }

    /**
     * For this scenario, add the relevant test tags. Hm, maybe we should
     * remember the tags at add time instead of after the fact...
     */
    public void addTags(TestCase tc) {
        String actionListString = getActionListString();
        tc.addTag(HoneycombTag.JAVA_API);
        if (actionListString.indexOf(STORE_OBJECT_STRING) != -1) {
            tc.addTag(HoneycombTag.STOREDATA);
        }
        if (actionListString.indexOf(STORE_METADATA_STRING) != -1) {
            tc.addTag(HoneycombTag.STOREMETADATA);
        }
        if (actionListString.indexOf(RETRIEVE_OBJECT_STRING) != -1) {
            tc.addTag(HoneycombTag.RETRIEVEDATA);
        }
        if (actionListString.indexOf(RETRIEVE_METADATA_STRING) != -1) {
            tc.addTag(HoneycombTag.RETRIEVEMETADATA);
        }
        if (actionListString.indexOf(DELETE_OBJECT_STRING) != -1) {
            tc.addTag(HoneycombTag.DELETE);
        }
        if (actionListString.indexOf(DELETE_METADATA_STRING) != -1) {
            tc.addTag(HoneycombTag.DELETE);
        }
        if (actionListString.indexOf(QUERY_STRING) != -1) {
            tc.addTag(HoneycombTag.QUERY);
        }
    }

    /**
     * Generate a random scenario of the given length.
     */
    public static Scenario getRandomScenario(int length, long seed) {
        Scenario s = new Scenario("Random scenario of length " + length +
            "; seed " + seed);

        // add length so different random length scenarios get different results
        Random r = new Random(seed + length); 

        s.add(STORE_OBJECT); // must start with this!

        for (int i = 1; i < length; i++) {
            // Skip new STORE_OBJECTS actions
            int newAction = r.nextInt(MAX_ACTION - 1) + 2;
            s.add(newAction);
        }

        return (s);
    }
}
