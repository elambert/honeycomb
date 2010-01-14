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



package com.sun.honeycomb.delete.util;

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.layout.Layout;
import com.sun.honeycomb.layout.LayoutClient;
import com.sun.honeycomb.disks.Disk;
import java.util.logging.Logger;
import java.io.File;
import java.io.FileNotFoundException;
import com.sun.honeycomb.common.DeletedObjectException;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.TestFragmentFile;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.delete.Constants;

/**
 * This class tries to find out as much info as possible about the given
 * fragment of an oid.
 */
public class FragInfo {
    private static final Logger LOG = Logger.getLogger(FragInfo.class.getName());
    public static final int UNSET = -1;

    // fields that are always valid, assuming valid input of oid and fragId
    public NewObjectIdentifier oid = null;
    public int fragnum = UNSET;
    public String fragFileName = null;
    public boolean isMD = false;
    public int chunkId = UNSET;
    public boolean fragIsMissing = false;
    public boolean fieldsAreValid = false;
    public String errstr = null;

    // fields that are not always valid,
    // can test if they are valid if fieldsAreValid is true
    public long fragFileSize = UNSET;
    public NewObjectIdentifier linkOid = null;
    public long objectSize = UNSET;
    public long objectSizeInFrag = UNSET;
    public int numChunks = UNSET;
    public int chunkSize = UNSET;
    public int refcnt = UNSET;
    public int maxRefcnt = UNSET;
    public boolean isDeleted = false;
    public SystemMetadata sm = null; // needs to look at last frag

    public FragInfo(NewObjectIdentifier fragoid, int fragId) {
        oid = fragoid;
        fragnum = fragId;

        if (oid == null) {
            errstr = "oid was null";
            return;
        }

        isMD = (oid.getObjectType() == NewObjectIdentifier.METADATA_TYPE);
        chunkId = oid.getChunkNumber();

        int layoutMapId = oid.getLayoutMapId();
        Layout layout = LayoutClient.getInstance().getLayoutForRetrieve(
            layoutMapId);

        Disk d = layout.getDisk(fragnum);
        fragFileName = Common.makeFilename(oid, d, fragnum);
        File fragFile = new File(fragFileName);

        if (fragFile.exists()) {
            fragIsMissing = false;
            fragFileSize = fragFile.length();
        } else {
            fragIsMissing = true;
            errstr = "frag is missing";
            return;
        }

        TestFragmentFile ff = null;
        try {
            ff = new TestFragmentFile(oid, fragnum, d);
            ff.open(false); // open in read mode
        } catch (com.sun.honeycomb.oa.DeletedFragmentException dfe) {
            isDeleted = true;
        } catch (Throwable e) {
            errstr = "couldn't open fragfile " +
                "for " + fragFileName + ": " + e + "; ";
            LOG.severe(errstr);
            if (ff != null) {
                ff.close();
            }
            return;
        }

        // XXX The following is a bit hacky:
        // Even if we got a deleted exception above, we can still read from
        // the footer...this might be a bit fragile because if the OA code
        // changes, then this code might break.
        if (ff == null) {
            errstr = "fragment file was null; ";
            return;
        }

        FragmentFooter footer = ff.getFooter();
        if (footer == null) {
            errstr = "footer was null; ";
            ff.close();
            return;
        }

        linkOid = footer.linkoid;
        objectSizeInFrag = footer.size;
        // this call return system MD from the last chunk of the object,
        // which contains correct size and hash value. Important to call
        // with ignoreDeleted, ! knownMoreChunks, ignoreIncomplete flags.
        try {
            sm = OAClient.getInstance().getLastSystemMetadata(oid, true, false, true);
            if (sm == null) {
                throw new IllegalStateException("SystemMetadata not found for " + oid);
            }
            LOG.warning("sm not null");
            objectSize = sm.getSize();
        } catch (NoSuchObjectException e) {
            errstr = "Failed to getLastSystemMD: " + e.getMessage();
            ff.close();
            return;
        } catch (OAException e) {
            LOG.warning("Failed to getLastSystemMD: " + e.getMessage());
            // ignore - should not happen
        }
        refcnt = footer.refCount;
        maxRefcnt = footer.maxRefCount;
        chunkSize = footer.chunkSize * OAClient.getInstance().blockSize;
        numChunks = (int)(objectSize / (long)chunkSize) + 1;
        if (objectSize > 0 && objectSize % chunkSize == 0) {
            // XXX the OA code does something different...I don't
            // understand how it gets the right value.
            numChunks--;
        }

        ff.close();
        fieldsAreValid = true;
    }

    /**
     * Given an oid, print all info about it including following its link and
     * printing info about that obj and all of its chunks.
     */
    public static String getObjInfo(NewObjectIdentifier fragoid) {
        return (getFragInfo(fragoid, Constants.ALL_FRAGS));
    }

    public static String getFragInfo(NewObjectIdentifier fragoid, int fragID) {
        String s = "";
        int start = fragID;
        int end = fragID;

        if (fragID == Constants.ALL_FRAGS) {
            start = 0;
            end = Constants.MAX_FRAG_ID;
        }

        for (int i = start; i <= end; i++) {
            FragInfo fi = new FragInfo(fragoid, i);
            if (fi.isMD) {
                s += "*MD[" + i + "." + fi.chunkId + "]* ";
            } else {
                s += "*DATA[" + i + "." + fi.chunkId + "]* ";
            }
            s += fi.toString();

            NewObjectIdentifier prevoid = fragoid;

            while (fi.fieldsAreValid &&
                fi.objectSizeInFrag == OAClient.MORE_CHUNKS) {
                // Get next layout id
                int lid = LayoutClient.getInstance().
                    getConsecutiveLayoutMapId (prevoid.getLayoutMapId());
                // Construct next OID
                NewObjectIdentifier nextoid =
                    new NewObjectIdentifier(prevoid.getUID(),
                        lid,
                        prevoid.getObjectType(),
                        prevoid.getChunkNumber()+1,
                        prevoid.getRuleId(),
                        prevoid.getSilolocation());
                // XXX missing frags
                fi = new FragInfo(nextoid, i);
                s += "\n*CHUNK[" + i + "." + fi.chunkId + "]* " + fi.toString();
                prevoid = nextoid;
            }

            if (fi.isMD) {
                s += "\n" + getFragInfo(fi.linkOid, i);
            }
            s += "\n";
        }

        return (s);
    }

    public String toString() {
        return (
            "FragInfo:" +
            " oid=" + oid +
            " linkOid=" + linkOid +
            " fragnum=" + fragnum +
            " fragFileName=" + fragFileName +
            " fragIsMissing=" + fragIsMissing +
            " fragFileSize=" + fragFileSize +
            " objectSize=" + objectSize +
            " objectSizeInFrag=" + objectSizeInFrag +
            " chunkSize=" + chunkSize +
            " numChunks=" + numChunks +
            " chunkId=" + chunkId +
            " refcnt=" + refcnt +
            " maxRefcnt=" + maxRefcnt +
            " isMD=" + isMD +
            " isDeleted=" + isDeleted +
            " fieldsAreValid=" + fieldsAreValid +
            " errstr=" + errstr
            );
    }
}

