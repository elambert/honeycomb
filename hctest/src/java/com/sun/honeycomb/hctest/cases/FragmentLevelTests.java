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


import com.sun.honeycomb.hctest.Audit;
import com.sun.honeycomb.hctest.cases.interfaces.HCFileSizeCases;
import com.sun.honeycomb.hctest.cases.interfaces.FileSizeCases;
import com.sun.honeycomb.hctest.cli.CLI;
import com.sun.honeycomb.hctest.cli.HwStat;
import com.sun.honeycomb.hctest.cli.DataDoctorState;
import com.sun.honeycomb.hctest.cli.CLIState;
import com.sun.honeycomb.hctest.util.HCTestReadableByteChannel;
import com.sun.honeycomb.hctest.util.HCUtil;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.HoneycombLocalSuite;
import com.sun.honeycomb.hctest.CmdResult;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.ArrayList;
import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.hctest.HoneycombTag;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.Run;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.client.TestNVOA;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.SystemRecord;

import com.sun.honeycomb.common.NewObjectIdentifier;

import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.layout.Layout;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.layout.LayoutConfig;
import java.lang.Runtime;
import java.nio.channels.ReadableByteChannel;

/**
   This are the zap fragment tests. 
   
   *. Disable datadoctor.
   *. Store interesting piece of data/metadata (where intersting is variety of filesizes, document here).
   *. Identify layout that data is on.
   *. Identify exact path to that data
   *. Ssh to cluster and remove/corrupt the fragment (do both).
   *. Attempt to fetch said piece of data
   *. throw error if fetchable/unfetchable (depending on what we want to see).
   *. Heal said piece of data
   *. Check that fragment now exists/is fixed (in the case of corruption) 
   *  Attempt to fetch
   *. Enable datadoctor

   Parameters:
   "deletedouble"  - double fragment delete tests(no parameter does both)
   "deletesingle" - single fragment delete tests (no parameter does both)
   "corruptfragment" - corrupts the beginning of a fragment
   We assume that all three of the above are "true" if not otherwise specified.

   "healback" - copy the hidden fragment to other disks; healing will copy it back into place instead of RS recovery
   "fullheal" - don't use the suitcase to run healing on a single dir; instead, trigger a full heal/scan of the entire system
   "recoverycommutes" - doesn't do the full combinatorial delete on i,j for double fragment delete. Faster, less complete.
   "reusefiles" - re-use stored files if possible to save execution time.  
                  re-using files is ok because we do close validation 
                  at each phase and throw exceptions if anything is amiss.  
                  If an exception is thrown
   "startingfilesize" - staring filesize in the series
   "endingfilesize" - ending filesizes in the series
   "verbose" - more verbose, man!
   "allfilesizes" - uses "getSizeList" in HCFileSizeCases instead
                    of getUniqueSizeList. There are more filesizes 
                    when this flag is on.

   "skippedresults" - enters "skipped" for filesizes whch don't meed startingfilesize or endingfilesize and are in filesizes.


    NO_FRAGMENT_VALIDATE = "noValidate";


  533  ./bin/runtest com.sun.honeycomb.hctest.cases.FragmentLevelTests -ctx cluster=dev327:nodes=16:deletedouble:fullheal=true:startingfilesize=1073741824 > results 2>&1 &
  670  ./bin/runtest com.sun.honeycomb.hctest.cases.FragmentLevelTests -ctx cluster=dev327:nodes=16:recoverycommutes:corruptfragment:fullheal=true:startingfilesize=67000 > results 2>&1 &
  605  ./bin/runtest com.sun.honeycomb.hctest.cases.FragmentLevelTests -ctx cluster=dev327:nodes=16:deletesingle:fullheal=true:startingfilesize=1024

 */

//
// Todo: implement locking of some sort (at the directory level, I guess) so
// can run multiple instances simultaneously without data doctor stepping 
// on itself.
//



public class FragmentLevelTests extends HoneycombLocalSuite
{

    private static final int numtries=2;
    private static final long retrySleep = 15 * 1000;
    private static final int DELETE_SINGLE=0x1;
    private static final int DELETE_DOUBLE=0x2;
    private static final int CORRUPT_FRAGMENT=0x4;
    private static final int DO_HEALBACK=0x8;
    private static final int ALL_TESTS=0xf; // DELETE_SINGLE | DELETE_DOUBLE | CORRUPT_FRAGMENT | DO_HEALBACK
    private long startingFilesize = 0;  // to skip early file size cases
    private long endingFilesize = Long.MAX_VALUE;  // skip late file size cases
    private boolean useAllFilesizes = false;  // use more exhaustive size list
    private boolean verbose = false;  // to print the Data Doctor output
    private boolean fullHeal = false;  // run the full data doctor cycle for each heal.
                                       // default uses the suitcase to run on only one directory
    private boolean recoveryCommutes = false;  // assume recovery commutes
    private boolean reuseFiles = false;  // re-use files when possible?
    private boolean enterSkippedResults = false;  // skipped res for all cases?
    private static int corruptType = 0; 

    
    public FragmentLevelTests() {
        super();
    }

    
    public void setUp()
    {

    }

    /**
     * This class contains information about fragment to be acted upon.
     */
    class FragAction {
        String oid;
        int fragId;
        int chunkId;
        long filesize;
        
        public FragAction(String oid, int fragId, int chunkId, long filesize) {
            this.oid = oid;
            this.fragId = fragId;
            this.chunkId = chunkId;
            this.filesize = filesize;
        }

        public String toString() {
            return 
                "fragment=" + fragId + " in chunk=" + chunkId + 
                " of oid=" + oid + " of size=" + filesize; 
        }
    }
    
    //
    // Might be nice but probably breaks our world right now.
    //
    public void logRotate() {
        //  logadm /var/adm/messages -s 10b
    }

    //
    // Currently only supports "full heal" model. 
    // Targeted heal is an option.
    //
    private boolean corruptOneFrag(int chunk,CmdResult cmd) 
            throws HoneycombTestException {
        boolean result=true;
        DataDoctorState state=DataDoctorState.getInstance();

        for (int j=0; (j< (HoneycombTestConstants.OA_DATA_FRAGS + 
                           HoneycombTestConstants.OA_PARITY_FRAGS) && result==true); j++) {
            
            //
            // Joe: Add for loop for corruption type
            //

            FragAction frag = new FragAction(cmd.dataoid, j, chunk, cmd.filesize);
            Log.INFO("-------------> corrupting " + frag);

            boolean didCorrupt = false; // detect if we punt on file too small
            //
            // XXX
            // corruption is currently round robin of different parts of
            // frags.  eventually, have option to do combinatorial per
            // frag size since it might be handled differently per-size
            // (or at least botros-arnoud vs not and in data vs parity
            // frags).
            //
            didCorrupt = state.corrupt(cmd.dataoid,j,chunk,corruptType);


            // didCorrupt will be true for recovery case and for corrupt
            // case, and is only false when we skip corruption due to 
            // filesize.
            if ( !didCorrupt == true) {
                Log.INFO("-------------> Skipping corruption of type "
                         + DataDoctorState.getCorruptionTypeString(corruptType)
                         +"--fragment too small for this type-- " + frag);
            } else {
                Log.INFO("-------------> Recover " + frag);
                
                //
                // Not currently supporting targeted healing
                //

                state.startCycle(CLIState.SCAN_FRAGS);
                //
                // do verify cycle until it's gone
                //
                long increments =  120;
                Log.INFO("looking for scan frags to remove the frag every 30 seconds for...  "+
                         increments/2 + " minutes.");
                
                boolean keepChecking = true;
                for(int i=0;(i<increments) && keepChecking;i++) {                    
                    if(increments==60) {
                        //
                        // Nuttin's happened for a half hour. Re-issue the dd cycle command.
                        //
                        state.stopCycle(CLIState.SCAN_FRAGS);
                        Log.WARN("Re-issuing cycle command. This bad - could be due to dd cycle respond bug");
                        try {
                            Thread.sleep(30000);
                        } catch (InterruptedException ie) {
                            Log.ERROR("Interrupted exception: "+ie.toString());
                            Log.ERROR("Failing test.");
                            return false;
                        }
                 
                        state.startCycle(CLIState.SCAN_FRAGS);                       
                    }
                    Log.INFO("sleeping 30 secs");
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException ie) {
                                Log.ERROR("Interrupted exception: "+ie.toString());
                                Log.ERROR("Failing test.");
                                return false;
                    }
                    
                    Log.INFO("  Checking...");                
                    keepChecking = state.checkPresent(cmd.dataoid,j,chunk);
                    if(keepChecking) {
                        Log.INFO(" file still present, continuing scan frags cycle");
                    }
                }
                if(keepChecking) {
                    Log.ERROR("Damaged fragment never removed, failing..");
                    return false;
                }
                //
                // end verify
                //
                
                state.stopCycle(CLIState.SCAN_FRAGS);
            } 
            result=state.dataDoctorFullHeal(cmd.dataoid, j, chunk, cmd.filesize, verbose);
            
            corruptType++;
            if (corruptType > DataDoctorState.MAX_CORRUPT)
                corruptType = 0;
        
        }

        return result;
    }

    /** Delete (hide) a fragment, copy it to failover disks, then force healback to copy it back into place.
     */
    private boolean healbackOneFrag(int chunk, CmdResult cmd)  
            throws HoneycombTestException {
        
        boolean result=true;
        DataDoctorState state=DataDoctorState.getInstance();
        for (int j=0;
             (j< (HoneycombTestConstants.OA_DATA_FRAGS + 
                 HoneycombTestConstants.OA_PARITY_FRAGS) && result==true);
             j++) {

            FragAction frag = new FragAction(cmd.dataoid, j, chunk, cmd.filesize);
            Log.SPACE();
            Log.INFO("-------------> Delete and copy to other disks: " + frag);

            state.makeRemoteFragBackups(cmd.dataoid, j, chunk);
            state.delete(cmd.dataoid,j,chunk);

            Log.INFO("-------------> Healback " + frag);
                
            if(!fullHeal) {                   
                result=state.dataDoctorTargetedHeal(cmd.dataoid,j,chunk,CLIState.LOST_FRAGS,verbose);
            } else {
                result=state.dataDoctorFullHeal(cmd.dataoid, j, chunk, cmd.filesize, verbose);
            }

            // cleanup extra frags
            state.deleteRemoteFragBackups(cmd.dataoid, j, chunk);
        }
        return result;

    }

    /** Delete (hide) a fragment, then force healing on it
     */
    private boolean deleteOneFrag(int chunk,CmdResult cmd) 
            throws HoneycombTestException {

        boolean result=true;
        DataDoctorState state=DataDoctorState.getInstance();
        for (int j=0;
             (j< (HoneycombTestConstants.OA_DATA_FRAGS + 
                 HoneycombTestConstants.OA_PARITY_FRAGS) && result==true);
             j++) {

            FragAction frag = new FragAction(cmd.dataoid, j, chunk, cmd.filesize);
            Log.SPACE();
            Log.INFO("-------------> Delete " + frag);

            state.delete(cmd.dataoid,j,chunk);

            Log.INFO("-------------> Recover " + frag);
                
            if(!fullHeal) {                   
                result=state.dataDoctorTargetedHeal(cmd.dataoid,j,chunk,CLIState.SCAN_FRAGS,verbose);
            } else {
                result=state.dataDoctorFullHeal(cmd.dataoid, j, chunk, cmd.filesize, verbose);
            }
        }
        return result;
    }

    /** Delete (hide) 2 fragments of an OID, then force healing
     */
    private boolean deleteTwoFrags(int chunk,CmdResult cmd) 
                throws HoneycombTestException {
        DataDoctorState state=DataDoctorState.getInstance();
        boolean result=true;
        int loopend;

        for (int j=0;
             (j< HoneycombTestConstants.OA_DATA_FRAGS + 
                 HoneycombTestConstants.OA_PARITY_FRAGS) && result==true;
             j++) {

            // Nice to skip the duplicate cases (ie, (0,1) and (1,0)), but this
            // isn't truly a duplicate because the order we recover frags can 
            // make a difference.  That is, delete f0, delete f1.  The choice of
            // running recover f0 or recover f1 first might make a difference,
            // at least at the RS level....
            if (recoveryCommutes) {
                loopend = j;
            } else {
                loopend = HoneycombTestConstants.OA_DATA_FRAGS +
                    HoneycombTestConstants.OA_PARITY_FRAGS;
            }

            for (int i=0;
                 i < loopend && result==true;
                 i++) {

                if (i == j)
                    continue;

                FragAction frag1 = new FragAction(cmd.dataoid, j, chunk, cmd.filesize);
                FragAction frag2 = new FragAction(cmd.dataoid, i, chunk, cmd.filesize);

                Log.SPACE();
                Log.INFO("-------------> Delete 2 fragments: " + j + " and " + i);
                Log.INFO("Fragment 1: " + frag1);
                Log.INFO("Fragment 2: " + frag2);

                state.delete(cmd.dataoid,j,chunk);
                state.delete(cmd.dataoid,i,chunk);
                
                Log.INFO("-------------> Heal 2 fragments: " + j + " and " + i);

                //Joe diag
                //                Log.INFO("Aborting - check state of the chunk, ensure 5 frags.");
                //                System.exit(1);
                // end Joe diag

                if(!fullHeal) {
                    result=state.dataDoctorTargetedHeal(cmd.dataoid, j, chunk, CLIState.LOST_FRAGS, verbose);
                    if(result==true){
                        result=state.dataDoctorTargetedHeal(cmd.dataoid, i, chunk, CLIState.LOST_FRAGS, verbose);
                    }
                } else {
                    result=state.dataDoctorFullHeal(cmd.dataoid,i,chunk,cmd.filesize,verbose);
                    if(result==true){
                        Log.INFO("Found one chunk - checking for the second");
                        boolean keepChecking = true;
                        long increments =  120;
                        Log.INFO("second fragment: checking every 30 seconds for...  "+
                                 increments/2 + " minutes.");
                        
                        for(int k=0;(k<increments) && keepChecking;k++) {
                            
                            if(k==60) {
                                //
                                // Nuttin's happened for a half hour. Re-issue the dd cycle command.
                                //
                                state.stopCycle(CLIState.LOST_FRAGS);
                                Log.WARN("Re-issuing cycle command. This bad - could be due to dd cycle respond bug");
                                Log.INFO("sleeping 30 secs");
                                try {
                                    Thread.sleep(30000);
                                } catch (InterruptedException ie) {
                                    Log.ERROR("Interrupted exception: "+ie.toString());
                                }
         
                                state.startCycle(CLIState.LOST_FRAGS);                       
                            }

                            Log.INFO("sleeping 30 secs");
                            try {
                                Thread.sleep(30000);
                            } catch (InterruptedException ie) {
                                Log.ERROR("Interrupted exception: "+ie.toString());
                            }
         
                            Log.INFO("Checking..");
                            keepChecking = !state.checkPresent(cmd.dataoid,j,chunk);
                        }

                        result=state.verify(cmd.dataoid,j,chunk);
                    }
                }
            }
        }
        return result;
    }

    private boolean zapHealFragments(long fileSize, CmdResult cmd,int mode) 
                          throws HoneycombTestException {
        boolean result=true;

        DataDoctorState state=DataDoctorState.getInstance();
        int numChunks=(int)(fileSize/HoneycombTestConstants.OA_MAX_CHUNK_SIZE);
        if(fileSize % HoneycombTestConstants.OA_MAX_CHUNK_SIZE != 0 || fileSize == 0)
            numChunks++;

        Log.INFO("Deleting and healing fragments in all " + numChunks + " chunks " +
                 " of oid " + cmd.dataoid + " of filesize " + fileSize);

        int factor=1;
        if(numChunks > 5) {
            factor=2;
        }
        if(numChunks > 10) {
            factor=3;
        }
        if(numChunks > 15) {
            factor=5;
        }
        if(numChunks > 25) {
            factor=20;
        }
        int k;        

        if(DELETE_SINGLE == mode) { // Single fragment, all chunks

            for( k=0;k<numChunks && result==true;k=k+factor) {
                result=deleteOneFrag(k,cmd);
            }
            if(numChunks!=k&& result == true) {
                result=deleteOneFrag(k,cmd);
            }

        } 
        if (DELETE_DOUBLE == mode) { // DualFragment, all chunks
            
            for( k=0;k<numChunks;k=k+factor) {
                result=deleteTwoFrags(k,cmd);
            }
            if(numChunks!=k && true==result) {
                result=deleteTwoFrags(numChunks-1,cmd);
            }
        } 
        if (CORRUPT_FRAGMENT == mode) { // Single fragment, all chukns

            for( k=0;k<numChunks && result == true;k=k+factor) {
                result=corruptOneFrag(k,cmd);
            }
             
            if(numChunks!=k && result == true) {
                result=corruptOneFrag(k,cmd);
            }
        }
        if (DO_HEALBACK == mode) {

            for( k=0;k<numChunks && result == true;k=k+factor) {
                result=healbackOneFrag(k,cmd);
            }
             
            if(numChunks!=k && result == true) {
                result=healbackOneFrag(k,cmd);
            }
            
        }
        return result;
    }

    private CmdResult doStore(long fileSize) {
        boolean actionSucceeded=false;
        CmdResult curCommandResult=null;
        for (int l = 1; l <= numtries && !actionSucceeded; l++) {
            curCommandResult=null;
            try {
                Log.DEBUG("Storing size "+fileSize);
                // store as a stream without any metadata (null)
                curCommandResult=super.storeAsStream(fileSize, null);
                Log.DEBUG("Stored size "+fileSize);
                actionSucceeded=true;
            } catch (HoneycombTestException e) {

                Log.WARN("Caught Exception during store " + fileSize + ": " + e);
                Log.WARN(Log.stackTrace(e));
                Log.WARN("sleeping " + retrySleep + " and will retry");
                // avoid the extra wait if we are going to
                // exit the loop nexttime
                if ((l + 1) > numtries) {
                    curCommandResult = null;
                    break;
                }
                try {
                    Thread.sleep(retrySleep);
                } catch (InterruptedException ie) {
                    Log.ERROR("Interrupted exception: "+ie.toString());
                }
                curCommandResult=null;
                actionSucceeded=false;
            }
        }
        if(!actionSucceeded) {
            return null;
        } else {
            return curCommandResult;                                
        }


    }

    private boolean doFetch(CmdResult lastResult) {
        boolean actionSucceeded=false;

        for (int l = 1; l <= numtries && !actionSucceeded; l++) {
                                    
            try {
                Log.INFO("Fetching:"+lastResult.dataoid + " of size " + lastResult.filesize);
                CmdResult cmd = super.retrieveAsStream(lastResult.mdoid, lastResult.filesize);
                Log.DEBUG("Fetched:"+lastResult.dataoid);
                // Do we have to manually do sha1sum verification
                // or does audit DB do this for us?  
                if ((cmd.datasha1).equals(lastResult.datasha1)) {
                    Log.INFO("After retrieve, got expect hash of " + cmd.datasha1 +
                        " for object " + lastResult.dataoid);
                } else {
                    throw new RuntimeException("Hashes don't match for " +
                        lastResult.dataoid +
                        ": Stored as " + lastResult.datasha1 + ", " +
                        "Retrieved as " + cmd.datasha1);
                }
                actionSucceeded=true;
            } catch (HoneycombTestException e) {

                actionSucceeded=false;
                Log.WARN("Caught Exception: " + e);
                Log.WARN(Log.stackTrace(e));
                Log.WARN("sleeping " + retrySleep +
                         " and will retry");
                // avoid the extra wait if we are going to
                // exit the loop nexttime
                if ((l + 1) > numtries) {
                    break;
                }
                try {
                    Thread.sleep(retrySleep);
                } catch (InterruptedException ie) {
                    Log.ERROR("Interrupted exception: "+ie.toString());
                }


            }
        }
        return actionSucceeded;
    }

    private void doTest (int mode) {
        ArrayList fileSizes;
        
        if (useAllFilesizes) {
            fileSizes = HCFileSizeCases.getSizeList();
        } else {
            fileSizes = HCFileSizeCases.getUniqueSizeList();
        }

        // hack to silently exclude all frag tests from regression runs
        ArrayList tagSet = new ArrayList();
        tagSet.add(Tag.POSITIVE);
        tagSet.add(Tag.FEWHOURSLONG);
        Run run = Run.getInstance();
        if (!run.isTagSetActive(tagSet)) {
            Log.INFO("SKIPPING ALL FRAGMENT LEVEL TESTS");
            return;
        }

        setupDD();

        Log.INFO("Test mode is " + mode);
        Log.INFO("Master size list: " + fileSizes);

        for (int i = 0; i < fileSizes.size(); i++) {    
            long fileSize = ((Long) fileSizes.get(i)).longValue();
                                        
            // Check if we are skipping some of the early filesizes
            if (fileSize < startingFilesize) {
                // Could add skipped results for these...
                Log.DEBUG("Skipping filesize " + fileSize +
                    " because it is less than our starting size of " + startingFilesize);
                continue;
            }

            // Check if we are skipping some of the later filesizes
            if (fileSize > endingFilesize) {
                // Could add skipped results for these...
                Log.DEBUG("Skipping filesize " + fileSize +
                    " because it is greater than our ending size of " + endingFilesize);
                continue;
            }

            String caseName = FileSizeCases.lookupCaseName(fileSize);
            Log.INFO("---------------------- Starting case for filesize: " +fileSize + " " + caseName);

            TestCase testCase = null;
            CmdResult storeResult = null;

            // iterate through all modes.  re-use stored files
            // if possible to save execution time.  re-using files is ok
            // because we do close validation at each phase and throw
            // exceptions if anything is amiss.  If an exception is thrown
            // we do not re-use the file. it is unclear if caching will affect
            // retrieve.  The checks we do of diffing the frags should ensure
            // retrieve would work, but if we are paranoid, we shouldn't reuse
            // files...  Otherwise, a nice optimization because storing 100G is
            // a bit time consuming...

            for (int currmode = 0x1; currmode < ALL_TESTS; currmode <<= 1) {
                if((currmode & mode & DELETE_SINGLE) != 0x0) {
                    testCase = new TestCase(this, 
                        "singleFragmentDelete" + caseName,
                        "filesize=" + fileSizes.get(i));
                } else if ((currmode & mode & DELETE_DOUBLE) != 0x0) {
                    testCase = new TestCase(this, 
                        "doubleFragmentDelete" + caseName,
                        "filesize=" + fileSizes.get(i));
                } else if ((currmode & mode & CORRUPT_FRAGMENT) != 0x0) {
                    testCase = new TestCase(this, 
                        "corruptFragment" + caseName,
                        "filesize=" + fileSizes.get(i));
                } else if ((currmode & mode & DO_HEALBACK) != 0x0) {
                    testCase = new TestCase(this,
                                            "doHealback" + caseName,
                                            "filesize=" + fileSizes.get(i));
                    
                } else {
                    Log.DEBUG("Skip mode " + currmode + " size " + fileSize);
                    continue;
                }

                testCase.addTag(new String [] {Tag.POSITIVE, 
                                           Tag.FEWHOURSLONG,
                                           HoneycombTag.DATA_DOCTOR,
                                           HoneycombTag.DD_FRAGMENT_HEAL});

                if (!reuseFiles) {
                    storeResult = null;
                }

                if (!testCase.excludeCase() && !enterSkippedResults) {

                    try {
                        // track if we stored a new file
                        boolean didStore = false;

                        // storeResult is null if we don't have file to re-use
                        if (storeResult == null) {
                            setFilesize(fileSize);
                            Log.INFO("Storing a file of size: " + fileSize);
                            storeResult = doStore(fileSize);
                            if (storeResult != null) {
                                didStore = true;
                                Log.INFO("Stored: " + storeResult.dataoid +
                                    " (size=" + storeResult.filesize +
                                    "; mdoid " + storeResult.mdoid + ")");
                            } else {
                                throw new RuntimeException("Store size " +
                                    fileSize + " did not succeed");
                            }
                        }

                        if (!didStore) {
                            Log.INFO("Re-using previous file " +
                                storeResult.dataoid + " (size=" +
                                storeResult.filesize + "; mdoid " +
                                storeResult.mdoid + ")");
                        }

                        boolean result=zapHealFragments(fileSize,storeResult,currmode);

                        // Test passes or fails depending on whether
                        // file retrieve succeeds or not.
                        if(false==result) {
                            testCase.postResult(false);
                        } else {
                            testCase.postResult(doFetch(storeResult));
                        }
                    } catch (Throwable t) {
                        // if the test hit an error, don't re-use file
                        storeResult = null;

                        Log.WARN("Test failed due to exception: " + Log.stackTrace(t));
                        testCase.testFailed("Hit exception: " + t);

                        /*
                        if (fileSize == 0) {
                            testCase.addBug("6379431", "recovery of files of size 0 fails"); 
                        }
                        
                        if (currmode == CORRUPT_FRAGMENT) {
                            testCase.addBug("6187542", "Fragment scanner should not delete fragments with corrupted footers without recovering them");
                            testCase.addBug("6380408", "FragmentScanner detects corruption and says it is going to fix it but doesn't for small files");
                            testCase.addBug("6382558", "we fail to correct corruption of small files (NPE in checksum code, diff from NPE for 0 size files)");
                        }
                        */
                        
                    }
                } else {
                    if (enterSkippedResults) {
                        testCase.testSkipped("skipped result at user request");
                    } else {
                        testCase.testSkipped("excluded.");
                    }
                }
            }  // for modes
        } // for sizes

        ddDone();
    }

    /*
    public static final String DELETE_DOUBLE = "deletedouble";
    public static final String DELETE_SINGLE = "deletesingle";
     */

    private void setupDD() {
        DataDoctorState state;

        state=DataDoctorState.getInstance();
        //        Log.WARN("XXX we were hanging turning off DD...skip it now, do it manually!");
        state.setValue(CLIState.LOST_FRAGS,0);
        state.setValue(CLIState.SCAN_FRAGS,0);
        Log.INFO("Turned off data doctor fragment recovery.");
        Log.INFO("Turned off fragment scanning recovery.");
    }

    /** Cleanup at the end of the test: reset DD cycles back to normal
     */
    private void ddDone() {

        DataDoctorState state=DataDoctorState.getInstance();
        //        Log.WARN("XXX we were hanging turning on DD...skip it now, do it manually!");
        
        try {
            state.setDefault(CLIState.LOST_FRAGS);
            state.setDefault(CLIState.SCAN_FRAGS);        
            Log.INFO("Restored data doctor fragment recovery.");
            Log.INFO("Restored fragment scanning recovery.");
        } catch (HoneycombTestException he) {
            Log.ERROR(he.getMessage());
        }
    }

    public void runTests() {    

        try {
            String singleProperty = getProperty(HoneycombTestConstants.DELETE_SINGLE);
            String doubleProperty = getProperty(HoneycombTestConstants.DELETE_DOUBLE);
            String corruptProperty = getProperty(HoneycombTestConstants.CORRUPT_FRAGMENT);
            String healbackProperty = getProperty(HoneycombTestConstants.DO_HEALBACK);
            String startingFilesizeProperty = getProperty(HoneycombTestConstants.PROPERTY_STARTINGFILESIZE);
            String endingFilesizeProperty = getProperty(HoneycombTestConstants.PROPERTY_ENDINGFILESIZE);
            String allFilesizesProperty = getProperty(HoneycombTestConstants.PROPERTY_ALLFILESIZES);
            String verboseProperty = getProperty(HoneycombTestConstants.PROPERTY_VERBOSE);
            String fullHealProperty = getProperty(HoneycombTestConstants.PROPERTY_FULL_HEAL);
            String recoveryCommutesProperty = getProperty(HoneycombTestConstants.PROPERTY_RECOVERYCOMMUTES);
            String reuseFilesProperty = getProperty(HoneycombTestConstants.PROPERTY_REUSE_FILES);
            String enterSkippedResultsProperty = getProperty(HoneycombTestConstants.PROPERTY_SKIPPEDRESULTS);

            if (null != startingFilesizeProperty) {
                Log.INFO("Will skip all files below size " + startingFilesizeProperty);
                startingFilesize = Long.parseLong(startingFilesizeProperty);
            }

            if (null != endingFilesizeProperty) {
                Log.INFO("Will skip all files above size " + endingFilesizeProperty);
                endingFilesize = Long.parseLong(endingFilesizeProperty);
            }

            if (null != allFilesizesProperty) {
                Log.INFO("Will use the more complete list of filesizes");
                useAllFilesizes = true;
            }

            if (null != verboseProperty) {
                verbose = true;
            }
            if (null != fullHealProperty) {                
                Log.INFO("Full heal is true 1.0!");
                fullHeal = true;
            }
            
            if (null != recoveryCommutesProperty) {
                Log.INFO("Will assume recovery commutes");
                recoveryCommutes = true;
            }

            if (null != reuseFilesProperty) {
                Log.INFO("Will re-use files when possible");
                reuseFiles = true;
            }

            if (null != enterSkippedResultsProperty) {
                Log.INFO("Will enter skipped results for cases");
                enterSkippedResults = true;
            }
             
            int testsToRun = 0x0;

            if (null != singleProperty) {
                Log.INFO("Will do Single fragment delete tests");
                testsToRun |= DELETE_SINGLE;
            }
            if  (null!=doubleProperty) {
                Log.INFO("Will do Double fragment delete tests");
                testsToRun |= DELETE_DOUBLE;
            }
            if (null!=corruptProperty) {
                Log.INFO("Will do Corrupt fragment tests");
                testsToRun |= CORRUPT_FRAGMENT;
            }
            if (null != healbackProperty) {
                Log.INFO("Will force Healback to copy fragments during recovery");
                testsToRun |= DO_HEALBACK;
            }
            
            // if no option specified, do all tests
            if (testsToRun == 0x0) {
                Log.INFO("Will do All fragment delete tests");
                testsToRun = ALL_TESTS;
            }

            doTest(testsToRun);

        } catch (Throwable t) {

            throw new RuntimeException(t);
        }
        //        state.setDefault(CLIState.LOST_FRAGS);
        //        Log.INFO("Turned on data doctor fragment recovery.");

    }
}


