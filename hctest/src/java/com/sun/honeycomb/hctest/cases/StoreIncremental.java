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

import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.test.Tag;

import com.sun.honeycomb.client.*;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Stores incrementally larger files, fetches them, does a sha1sum diff on 
 * the fetch. Does 10 operations total, incrementing size 5gb each time. 
 * so 5-50 gb test. Make sure your client has disk space!
 */
public class StoreIncremental extends HoneycombLocalSuite {
    long gigabyte = 1024 * 1024 * 1024;
    private ArrayList _fileIds;
    private static final int NUMSTORES = 10;
    public StoreIncremental() {
        super();
    }

    public void setUp() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::setUp() called");
        _fileIds = new ArrayList(20);
        super.setUp();
    }

    public void tearDown() throws Throwable {
        Log.DEBUG(this.getClass().getName() + "::tearDown() called");
        super.tearDown();
    }


    public void verifyFilesMatch(String f1, String f2) 
        throws HoneycombTestException {
        String f1sha,f2sha;
        try {
            f1sha = com.sun.honeycomb.hctest.util.HCUtil.computeHash(f1);
            f2sha = com.sun.honeycomb.hctest.util.HCUtil.computeHash(f2);
        } catch (Throwable t) {
            throw new HoneycombTestException(t);
        }

        // logger.info(f1 + " has sha1sum of " + f1sha);
        // logger.info(f2 + " has sha1sum of " + f2sha)
        if (!f1sha.equals(f2sha)) {
            throw new HoneycombTestException(f1 + " has sha1sum " + f1sha +
                                             "; " + f2 + " has sha1sum " + f2sha);
        }
    }


    private boolean makeIncrementalFiles() {
        int multFactor=5;
        for (int i=1;i<=NUMSTORES;i=i+1) {
            System.out.println("Storing this many gigs: " + i*multFactor);
            long filesize = gigabyte*(i*multFactor);
            // This must happen first so it is done in setup().
            // If the store fails, we abort the test case.
            CmdResult storeResult;
            try {
                storeResult = store(filesize);
            } catch (HoneycombTestException hte) {
                Log.ERROR("Store failed: " + 
                          hte.getMessage());
                Log.DEBUG(Log.stackTrace(hte));
                return (false);
            }
            Log.INFO("Created file with sum:" + storeResult + "\n");
            _fileIds.add(storeResult);
        }            
        return true;
    }
    private boolean fetchIncrementalFiles() {

        
        Iterator i = _fileIds.iterator();
        while (i.hasNext())
        {
            CmdResult cr;
            CmdResult curFile = ((CmdResult) i.next());
            try {
                cr = retrieve(curFile.mdoid);
            } catch (HoneycombTestException hte) {
                Log.ERROR("Retrieve failed [oid " + curFile.mdoid + "]: " + 
                          hte.getMessage());
                Log.DEBUG(Log.stackTrace(hte));
                return (false);
            }
            try {
                verifyFilesMatch(curFile, cr);
            } catch (HoneycombTestException hte) {
                Log.ERROR("verifyFilesMatch failed: " + hte.getMessage());
                return (false);
            }
        }
        return true;
    }
    public boolean testIncremental() {
        addTag(Tag.POSITIVE);
        addTag(Tag.HOURLONG);
        addTag(Tag.EXPERIMENTAL,"Haven't run this in a while, should pass");
        if (excludeCase()) return false;
        if(!makeIncrementalFiles()) 
            return false;
        if(!fetchIncrementalFiles())
            return false;
        return true;


            
    }


}
