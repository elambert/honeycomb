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



package com.sun.honeycomb.delete;

import com.sun.honeycomb.common.ObjectReliability;

public class Constants {

    // needs to be multiple of 4 otherwise NodeTable cannot calculate correctly
    // the number of nodes in the ring...
    public static final int NB_DISKS = 32;
    public static final int ALL_FRAGS = -1;  // look at all frags, not just one
    public static final int REFCNT_UNKNOWN = -2; // skip the refcnt check
    public static final int REFCNT_MD = -1; // skip the refcnt check
    public static final long SIZE_UNKNOWN = -1; // don't check size
    public static final String CURRENT_HASH_ALG = "SHA1";

    public static final int MAX_ALLOCATE = 65536;
    public static final int DEFAULT_STORE_SIZE = 10; // default store obj size
    public static final long OA_DELETED_SIZE = 376; // deleted stub size
    public static final int OA_READ_RETRIES = 3; // FragmentFile.java
    public static final int OA_WRITE_RETRIES = 3; // FragmentFile.java

    public static int MAX_CHUNK_SIZE = -1; // initialized at startup
    public static ObjectReliability reliability = null; //initialized at startup
    public static int MAX_FRAG_ID = -1; // initialized at startup
    public static final int MAX_TEST_CHUNKS = 10;

    public static final String FRAGACTION_MOVE         = "move";
    public static final String FRAGACTION_REMOVE       = "remove";
    public static final String FRAGACTION_RESTORE      = "restore";
    public static final String FRAGACTION_NOTZERO      = "notzero";
    public static final String FRAGACTION_FILESIZE     = "filesize";
    public static final String FRAGACTION_INTERNALSIZE = "internalsize";
    public static final String FRAGACTION_CORRUPT      = "corrupt";
    public static final String FRAGACTION_SCAN         = "scan";
    public static final String FRAGACTION_TRUNCATE     = "truncate";
    public static final String FRAGACTION_ABSENT       = "absent";
    public static final String FRAGACTION_REMOVEFEF    = "removefef";
    public static final String FRAGACTION_RESTOREFEF   = "restorefef";

    public static final long SECONDS   = 1000;
    public static final long MINUTES   = SECONDS * 60;
    public static final long HOURS     = MINUTES * 60;
    public static final long DAYS      = HOURS * 24;
    public static final long WEEKS     = DAYS * 7;
    public static final long MONTHS    = WEEKS * 4;
    public static final long YEARS     = WEEKS * 52;
    public static final long DECADES   = YEARS * 10;
    public static final long CENTURIES = YEARS * 100;

    private static String rootDir = null;

    public static String getRootDir() {
        if (rootDir == null) {
            rootDir = System.getProperty("delete.root");
        }
        return(rootDir);
    }

    public static String getDiskRoot() {
        return(getRootDir()+"/disks");
    }

}

