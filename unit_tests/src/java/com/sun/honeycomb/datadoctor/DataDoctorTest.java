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



package com.sun.honeycomb.datadoctor;

import com.sun.honeycomb.util.Exec;

import java.util.Properties;
import java.io.IOException;
import java.io.File;

public class DataDoctorTest {

    // read config to get location of share directory
    public void setUp() {

        Properties props = System.getProperties();
        sharePath = (String)props.get("share.path");
        if (sharePath == null) {
            throw new RuntimeException(
                    "share.path system property undefined");
        }
    }

    public boolean testMergeSorted() {

        testId = TEST_MERGE_SORTED;
        String tn = TEST_NAMES[testId];

        String expectedFilePath = sharePath+"/"+PROG_NAME+"_"+tn+".out";
        File tempFile;
        try {
            tempFile = getTempFile(PROG_NAME+"_"+tn,".out");
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        String[] INPUT_FILES = new String[] { 
                        sharePath+"/"+PROG_NAME+"_"+tn+"1.in",
                        sharePath+"/"+PROG_NAME+"_"+tn+"2.in",
                        sharePath+"/"+PROG_NAME+"_"+tn+"3.in",
                        sharePath+"/"+PROG_NAME+"_"+tn+"4.in" };

        MergeSortedTest.setOutputFile(tempFile.getPath());
        MergeSortedTest.sortFiles(INPUT_FILES);

        boolean result;
        try {
            result =  diffFiles(expectedFilePath, tempFile);  
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        return result;
    }
    
    // create temp file to hold output
    public static File getTempFile(String prefix, String suffix) 
            throws IOException {

        File tempFile;
        tempFile = File.createTempFile(prefix, suffix);
        return tempFile;
    }
    
    // compare tempFile to the expected results, return "true" if same
    public static boolean diffFiles(String expectedFilePath, 
                                    File tempFile) throws IOException {
       
        // get the file containing the expected output
        File expectedFile = new File(expectedFilePath); 
        if (!expectedFile.exists()) {
            throw new IOException(expectedFile.getName() + " not found");
        }

        // diff the result with the expected output
        int retcode = 0;
        System.out.println(
                "checking results: diff "+tempFile+" "+expectedFile);
        retcode = Exec.exec(DIFF_CMD+" "+tempFile+" "+expectedFile);

        // log whether the test passed or failed
        if (retcode == 0) {
            System.out.println("Test PASSED!");
            tempFile.delete();
            return true;
        } else if (retcode == 1) {
            System.out.println(
                "Test FAILED, "+tempFile+" and "+expectedFile+" differ");
            return false;
        } else {
            throw new IOException(
                    "ERROR diffing "+tempFile+" and "+expectedFile);
        }
    }


    /************ this test takes lots of time, omit for now

     public boolean testFramework() {
 
         testId = TEST_FRAMEWORK;
 
         long[] cycleGoals = { 25, 0, 30, 1, 35 };
         return RunDummyTask.runTask(cycleGoals);
     }

     ***********************************/

    // class members
    private String sharePath = null;
    private int testId = -1;

    // constants
    private static final int NODES = 16;
    private static final int FRAGS = 7;
    private static final int NUM_MAP_IDS = 10;
    private static final String ESTIMATED_TIME = "1 minute";
    private static final String PROG_NAME =
        DataDoctorTest.class.getName().replaceAll("[A-Za-z_0-9]*[.]","");
    private static final String DIFF_CMD = "/bin/diff";

    // ids and names of the test runs
    private static final int TEST_MERGE_SORTED = 0;
    private static final int TEST_FRAMEWORK = 1;
    private static final String[] TEST_NAMES = { "mergeSorted", "framework"};

}


