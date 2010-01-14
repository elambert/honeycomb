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



package com.sun.honeycomb.hctest;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;

import com.sun.honeycomb.client.*;
import com.sun.honeycomb.common.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Iterator;
import java.util.ArrayList;

/**
 * Methods useful for delete testing.
 */
public class DeleteSuite extends HoneycombLocalSuite {

    public DeleteSuite() {
        super();
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        super.setUp();
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }


    public boolean deleteAndRetrieve(String oid, String prefix) 
                                                 throws HoneycombTestException {

        CmdResult cr = delete(oid);
        if (!cr.pass) {
            cr.logExceptions(prefix + "/delete");
            return false;
        }
        Log.INFO(prefix + ": delete ok");

        cr = retrieve(oid);
        if (cr.pass) {
            Log.ERROR(prefix + ": Retrieve succeeded after delete");
            return (false);
        } else {
            Log.INFO(prefix + ": retrieve failed as expected, checking exception");
        }
        if (cr.thrown == null) {
            Log.ERROR(prefix + ": No retrieve exception [?]");
            return (false);
        }

        if (cr.checkExceptions(NoSuchObjectException.class, 
                                         prefix + " delete/retrieve")) {
            addBug("6187583", "if object has been deleted, we shouldn't " +
                    "mention its deleted status");
            Log.ERROR(prefix + ": Retrieve failed w/ unexpected exception");
            return (false);
        }

        if (!cr.checkExceptionStringContent()) {
            /*XXX
            addBug("6273390", "OA should return user appropriate " +
                "msgs/excpns (and no internal oids, refCount, deleted " +
                "status)");
            return (false);
             */
            Log.WARN("Bad error message...ignoring until bug 6273390 is fixed");
            return (true);
        }

        Log.INFO(prefix + ": retrieve failure mode correct");
        return true;
    }

    public boolean onDiskFragsLookDeleted(String oid)
                                                throws HoneycombTestException {
        return (onDiskFragsLookDeleted(oid, null, null));
    }

    /**
     * Check if frags for given oid look deleted.  If fragsToIgnore is non-null,
     * we'll allow those frags to be non-deleted and will add them to the
     * fragsFoundAndIgnored list (which must be non-null if fragsToIgnore is
     * non-null) if they are found to be non-deleted.
     */
    public boolean onDiskFragsLookDeleted(String oid, List fragsToIgnore,
        List fragsFoundAndIgnored)
                                                throws HoneycombTestException {
        boolean success = false;
        long retries = 5;
        long sleeptime = 4000; // in msecs
        boolean retried = false;

        // We use retries because sometimes there is a delay in getting the NFS
        // info to be correct.
        while (retries-- > 0 && success == false) {
            long numchunks = 0;
            success = true;

            // true below means don't dereference the oid
            HOidInfo oidInfo = testBed.getOidInfo(oid, true);
            Log.DEBUG("oidInfo for " + oid + " is " + oidInfo);

            ListIterator chunkIter = oidInfo.chunks.listIterator();
            while (chunkIter.hasNext()) {
                Log.DEBUG("--- chunk " + numchunks++ + " for oid " + oid);
                HChunkInfo chunk = (HChunkInfo)chunkIter.next();
                ListIterator fragIter = chunk.getFragments().listIterator();
                while (fragIter.hasNext()) {
                    HFragmentInfo frag = (HFragmentInfo)fragIter.next();
                    ArrayList disks = frag.getDisks();
                    if (disks == null) {
                        Log.INFO("disks is null for frag " + frag.getFragNum() +
                            " of oid " + oid + "; skipping");
                        continue;
                    }
                    ListIterator diskIter = frag.getDisks().listIterator();
                    while (diskIter.hasNext()) {
                        boolean shouldIgnore = false;
                        HFragmentInstance fragInst =
                            (HFragmentInstance) diskIter.next();
                        String sizePath = fragInst +
                            chunk.getHashPath() + frag.getFragNum();
                        if (fragInst.filesize !=
                            HoneycombTestConstants.OA_DELETED_SIZE) {
                            // only log error if we are on last retry
                            String msg = "expected deleted frag size " +
                                HoneycombTestConstants.OA_DELETED_SIZE +
                                ", but size is " + sizePath;
                            if (retries == 0) {
                                Log.ERROR(msg);
                           
                                // Check if frag in question is in our ignore
                                // list.
                                for (int i = 0; i < fragsToIgnore.size(); i++) {
                                    String fragToIgnore =
                                        (String)fragsToIgnore.get(i);
                                    if (sizePath.indexOf(fragToIgnore) != -1) {
                                        Log.WARN("Ignoring fragment of size " +
                                            sizePath + "; possibly crawl " +
                                            "will clean it up.");
                                        fragsFoundAndIgnored.add(fragToIgnore);
                                        shouldIgnore = true;
                                    }
                                }
                            } else {
                                Log.DEBUG(msg);
                            }
                            if (!shouldIgnore) {
                                success = false;
                            }
                        } else {
                            Log.DEBUG("size is " + sizePath);
                        }
                    }
                }
            }

            if (!success && retries > 0) {
                Log.INFO("Found some non-delete frags for oid " + oid +
                    "; will retry to avoid race in propogation of fs attrs");
                sleep(sleeptime);
                retried = true;
            }
        }

        if (retried && success) {
            Log.INFO("Retrying confirmed object " + oid +
                " is actually deleted on disk");
        }

        return (success);
    }

    /**
     * Hack to ask RMI servers if they can figure out the data oid for
     * an MD object.  This is used by DeleteAllObjects.  This shouldn't
     * be needed once bug 6287089 is fixed.
     */
    public String findDataOid(String oid) throws HoneycombTestException {
        String dataoid = null;

        try {
            // false below means to dereference the oid
            HOidInfo oidInfo = testBed.getOidInfo(oid, false);
            HOidInfo dataOidInfo = oidInfo.other;
            dataoid = dataOidInfo.oid;
        } catch (Throwable t) {
            // do nothing, we'll just return null
        }

        return (dataoid);
    }

    /**
     * Return the list of fragments from each object specified to be deleted.
     * Either oid can be null to not include frags from that object.  If random
     * is true, then we will try to pick a more interesting set of fragments.
     */
    public static List pickFragsToDelete(int numFrags, HOidInfo mdoidInfo,
        HOidInfo dataoidInfo, boolean random, HashSet fragsToPick) 
                                                 throws HoneycombTestException {
        ArrayList frags = new ArrayList();
        ArrayList chunks = new ArrayList();
        ArrayList oidInfoList = new ArrayList();
        String mdoid = null;
        String dataoid = null;
        HOidInfo oidInfo;

        if (mdoidInfo != null) {
            mdoid = mdoidInfo.oid;
            oidInfoList.add(mdoidInfo);
        }

        if (dataoidInfo != null) {
            dataoid = dataoidInfo.oid;
            oidInfoList.add(dataoidInfo);
        }

        if (numFrags > HoneycombTestConstants.OA_TOTAL_FRAGS) {
            throw new HoneycombTestException("You wanted to pick " + numFrags +
                " but there are only " + HoneycombTestConstants.OA_TOTAL_FRAGS);
        }

        Log.INFO("Finding " + numFrags + " frags to delete from mdoid " +
            mdoid + " and dataoid " + dataoid);

        // for each object we are examining, find the chunks
        for (int i = 0; i < oidInfoList.size(); i++) {
            oidInfo = (HOidInfo)oidInfoList.get(i);
            // might have multi-chunk file
            for (int j = 0; j < oidInfo.chunks.size(); j++) {
                chunks.add(oidInfo.chunks.get(j));
            }
        }

        // For every chunk, delete numFrag frags
        HashSet allFragsChoosen = new HashSet();
        for (int i = 0; i < chunks.size(); i++) {
            ArrayList chunkFrags = new ArrayList();
            HChunkInfo chunk = (HChunkInfo)chunks.get(i);
            if (chunk.missingFrags || chunk.extraFrags) {
                Log.WARN("chunk is missing or has extra frags: " + chunk);
            }

            if (random || fragsToPick != null) {
                // Decide which frags to delete -- get a random distribution
                // XXX need repeatability/seed
                HashSet fragNums;
                
                if (fragsToPick != null) {
                    fragNums = fragsToPick;
                    Log.INFO("Using passed-in frags ids " + fragsToPick +
                        " for this chunk");
                } else {
                    fragNums = new HashSet();
                    Integer fragId;
                    while (fragNums.size() < numFrags) {
                        // try to force different frags to be chosen when
                        // possible.  Useful for multi-chunk testing.
                        if (allFragsChoosen.size() <
                            HoneycombTestConstants.OA_TOTAL_FRAGS) {
                            while (allFragsChoosen.contains(
                                fragId = new Integer(RandomUtil.randIndex(
                                HoneycombTestConstants.OA_TOTAL_FRAGS))))
                                // do nothing, just find a frag not in hash
                                ;
                            
                        } else {
                            allFragsChoosen.clear();
                            fragId = new Integer(RandomUtil.randIndex(
                                HoneycombTestConstants.OA_TOTAL_FRAGS));
                        }

                        // if we accidentally add a dup, it is okay because
                        // the hash length won't increase and we'll loop again.
                        fragNums.add(fragId);
                        allFragsChoosen.add(fragId);
                    }
                    Log.INFO("Using random frags " + fragNums +
                        " for this chunk");
                }

                // use the choices from hash.  If that doesn't work,
                // we'll just use the first N frags.  There are corner cases
                // that make the hash set case not always work.
                Iterator iter = fragNums.iterator();
                int count = 0;
                while (iter.hasNext()) {
                    // find out which frag we should look at next
                    int whichFrag = ((Integer)iter.next()).intValue();

                    try {
                        // XXX check for null and revert to other method?
                        HFragmentInfo fi =
                            (HFragmentInfo) chunk.getFragments().get(whichFrag);
                        String path =
                            ((HFragmentInstance)fi.getDisks().get(0)).disk
                            + chunk.getHashPath() +
                            Integer.toString(fi.getFragNum());
                        Log.INFO("[" + count++ + "] adding path " + path);
                        chunkFrags.add(path);
                    } catch (Throwable t) {
                        // ignore and revert to other method
                        break;
                    }
                }
            }

            // if we've found our frags for this chunk randomly,
            // move on to next chunk
            if (chunkFrags.size() == numFrags) {
                Log.INFO("adding " + chunkFrags.size() + " frags " +
                    "to list of all frags");
                frags.addAll(chunkFrags);
                continue;
            }

            if (random || fragsToPick != null) {
                Log.INFO("Prefered method of picking frags didn't work. " +
                    "Using first fragments");
                chunkFrags.clear();
            }
                
            // random method didn't work, try the first N frags
            // XXX check for null?
            for (int h = 0;
                (h < chunk.getFragments().size() && h < numFrags); h++) {
                HFragmentInfo fi = (HFragmentInfo) chunk.getFragments().get(h);
                if (fi.getDisks() == null) {
                    throw new HoneycombTestException("missing fragments " +
                        "already; aborting");
                }
                String path = ((HFragmentInstance)fi.getDisks().get(0)).disk +
                    chunk.getHashPath() + Integer.toString(fi.getFragNum());
                Log.INFO("[" + h + "] adding path " + path);
                chunkFrags.add(path);
            }

            if (chunkFrags.size() == numFrags) {
                Log.INFO("adding " + chunkFrags.size() + " frags from chunk " +
                    "to list of all frags");
                frags.addAll(chunkFrags);
            } else {
                throw new HoneycombTestException("could only find " +
                    chunkFrags.size() + " frags but we wanted " + numFrags);
            }
        }
    
        Log.INFO("Returning frag list of size " + frags.size());
        return (frags);
    }
}
