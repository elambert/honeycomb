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

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileFilter;

import com.sun.honeycomb.common.IncompleteObjectException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.ObjectCorruptedException;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.coordinator.Coordinator;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.emd.MetadataClient;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.oa.DeletedFragmentException;
import com.sun.honeycomb.oa.FragmentNotFoundException;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.emd.SysCacheUtils;
import com.sun.honeycomb.cm.ManagedServiceException;


/** 
 * Contains methods used by multiple tasks, mostly related to iterating
 * through directories and dealing with fragment files.
 */
public class TaskFragUtils {
    
    private static Logger LOG = Logger.getLogger(TaskFragUtils.class.getName());
        
    /*
     * This method returns the list of temporary fragments on the given *LOCAL*
     * disk as File objects
     * The disk must be local.
     */
    static File[] listTmpFrags(DiskId diskId) {
        
        File tmpDir = new File(Common.makeTmpDirName(DiskProxy.getDisk(diskId)));
        return tmpDir.listFiles(new FragmentFilter());
    }
    
    /*
     * This method returns the list of filenames contained on the given map id 
     * for the given disk.
     * return null if the map is not accessible.
     */
    static String[] readMap(DiskId diskId, int mapId) {
        throw new RuntimeException("not implemented in OA unit test framework");
    }
    
    /*
     * This method returns the list of fragments committed on persistent 
     * storage for the given OID on the given disk.
     */
    static String[] getDataFrags(NewObjectIdentifier oid, DiskId diskId) {

        // Disk necessary on this node.
        Disk disk = DiskProxy.getDisk(diskId);
        if (disk == null) {
            throw new IllegalArgumentException("invalid disk " + diskId);
        }
        // The unit test framework does not have the have the maps id
        // directories structure (and it does not need to).
        // So always return an empty map.
        String[] res = new String[0];

        String path = disk.getPath() + Common.getStoreDir(oid);
        if (!verifyDirPath(path)) {
            LOG.warning("Directory " + path + " not found");
        } else {
            res = new File(path).list(new OidFilter(oid));
        }
        return res;
    }        
    
    /*
     * This method returns the list of transient fragments for the given OID
     * on the given disk.
     */
    static String[] getTmpFrags(NewObjectIdentifier oid, DiskId diskId) {
        
        // Disk is on this node
        Disk disk = DiskProxy.getDisk(diskId);
        if (disk == null) {
            throw new IllegalArgumentException("invalid disk " + diskId);
        }
        String[] res = null;
        String path = disk.getPath() + Common.dirSep + Common.closeDir;
        if (!verifyDirPath(path)) {
            LOG.warning("Directory " + path + " not found");
        } else {
            res = new File(path).list(new OidFilter(oid));
        }
        return res;
    }
    
    /*
     * convert filename to oid, throws if filename is bad
     */
    static NewObjectIdentifier fileNameToOid(String f) 
        throws NotFragmentFileException 
    {
        NewObjectIdentifier oid = Common.extractOIDFromFilename(f);
        if (oid == NewObjectIdentifier.NULL) {
            throw new NotFragmentFileException("filename does not resolve to OID", f);
        }
        return oid;
    }
            
    /*
     * extract fragment id from filename, throws on bad filename
     */
    static int extractFragId(String f) 
        throws NotFragmentFileException 
    {
        int fragNum = Common.extractFragNumFromFilename(f);
        if (fragNum == -1) {
            throw new NotFragmentFileException("cannot extract fragId from file", f);
        }
        return fragNum;
    }
            
    /*
     * This method will remove the FragmentFile and also will make sure to 
     * remove the entry from the system cache on this disk if it is the case 
     * to be remove
     */
    static boolean removeFragment(FragmentFile frag) {
        SystemMetadata sm = null;
        
        try {
            sm = frag.readSystemMetadata();
        } catch (FragmentNotFoundException e1) {
            return false;
        } catch (DeletedFragmentException e1) {
            sm = frag.getSystemMetadata();
        } catch (ObjectCorruptedException e1) {
            // go ahead an delete it
        } catch (OAException e1) {
            sm = null; // no metadata to remove from sys cache
        }
        
        boolean result = frag.remove();
        return result;
    }
   
    /* 
     * This method will move the FragmentFile from to the FragmentFile to and 
     * will do this in a correct manner by moving to a tmp directory and only 
     * then committing it to disk with the correct FragmentFile commmit method.
     * 
     */
    static boolean moveFragment(FragmentFile from, Disk disk) {
        
        FragmentFile tmpFrag = null; 
        try {
            SystemMetadata sm;
            try {
                sm = from.readSystemMetadata();
            } catch (DeletedFragmentException e) {
                // It's ok to be deleted we will still want to move this 
                // fragment
                sm = from.getSystemMetadata();

            } catch (FragmentNotFoundException fnfe) {
                // fragment from does not exist
                return false;
            }

            // copy contents to tmp frag
            tmpFrag = new FragmentFile(from, from.getFragNum(), disk);
            tmpFrag.createFrom(from);
            
            // commit tmp frag
            tmpFrag.completeCreate();
            
            // remove old fragment
            from.remove();
            moveSystemMetadata(sm, from.getDisk(), disk);
            
        } catch (ObjectCorruptedException e) {
            return false;

        } catch (OAException e) {
            return false;

        } finally {
            if (tmpFrag != null) 
                tmpFrag.close();
        }
        return true;
    }
    
    static void recoverFragment(NewObjectIdentifier oid, 
                                int fragId,
                                Disk disk) throws 
        IncompleteObjectException, 
        OAException 
    {
        OAClient oaClient = OAClient.getInstance();
        oaClient.recover(oid, fragId, disk);
        SystemMetadata sm;
        try {
            sm = oaClient.getLastSystemMetadata(oid,true,true,false);
        } catch (NoSuchObjectException e) {
            throw new OAException(e);
        }
        moveSystemMetadata(sm, null, disk);
    }
    
    private static void moveSystemMetadata(SystemMetadata sm, 
                                           Disk from, 
                                           Disk to) {
        /*
         * TODO: be able to insert into the right caceh on the to Disk and not
         *       insert into all 3 caches.
         */
        SysCacheUtils.insertRecord(sm);
        try {
            if (from != null)
                SysCacheUtils.removeRecord(sm.getOID(),from);
        } catch (EMDException e) {
            // We don't want to break if we were unable to move the system
            // metadata just do the best we can and log the failure
            LOG.log(Level.INFO,"Failure to insert metadata for object : " + sm.getOID(),e);
        } 
    }
    
    /*
     * check if the given string points to a valid directory
     */
    private static boolean verifyDirPath(String path) {        
        // TODO: should we generate a DataDoctor exception here, and
        // force the caller to catch it?
        File dir = new File(path);
        return dir.exists() && dir.isDirectory();
    }
    
    
    public static class OidFilter implements FilenameFilter {
        private String oidStr;
        OidFilter(NewObjectIdentifier oid) {
            oidStr = oid.toString();
        }
        public boolean accept(File dir, String name) {
            if (name.startsWith(oidStr)) {
                return true;
            }
            return false;
        }
    }
    
    private static class FragmentFilter implements FileFilter {
        public boolean accept(File f) {
            return(f.isFile() && !f.isHidden());
        }
    }    
    
    
    /**
     * Crawl all available disks and find all of the tmp fragments that can be
     * found for the specified oid.
     * 
     * @param oid when a null oid is specified this function will return all of 
     *        the available tmp fragments in the cluster.
     *        
     * @param log if a null logger is specified no logging will be done.
     * 
     * @return ArrayList of all available data fragments for this oid.
     */
    public static ArrayList crawlAndGetAllTmpFrags(NewObjectIdentifier oid, 
                                                   TaskLogger log) {

        ArrayList res = new ArrayList();
        Disk[] allDisks = DiskProxy.getClusterDisks();

        for (int d = 0; d < allDisks.length; d++) {
            Disk disk = allDisks[d];
            if (disk == null) {
                // all disk must be online
                return null;
            }
            if (!disk.isEnabled()) {
                // disk must be enabled
                return null;
            }
            String[] frags = TaskFragUtils.getTmpFrags(oid, disk.getId());
            if (frags == null) {
                return null;
            }
            for (int i = 0; i < frags.length; i++) {
                int fragid = -1;
                try {
                    fragid = TaskFragUtils.extractFragId(frags[i]);
                } catch (NotFragmentFileException e) {
                    if (log != null)
                        log.warning("Skipping file: " + frags[i] + 
                                    ": " + e.getMessage());
                    continue;
                }
                if (oid != null) 
                    res.add(new FragmentFile(oid, fragid, disk));
                else {
                    try {
                        NewObjectIdentifier oid2 = TaskFragUtils.fileNameToOid(frags[i]);
                        res.add(new FragmentFile(oid2, fragid, disk));
                    } catch (NotFragmentFileException e) {
                        log.log(Level.WARNING, "Skipping file: " + frags[i], e);
                        return null;
                    }
                }
            }
        }
        return res;
    }

    /**
     * Crawl all available disks and find all of the data fragments that can be
     * found for the specified oid.
     * 
     * @param oid when a null oid is specified this function will return all of 
     *        the available tmp fragments in the cluster.
     *
     * @param log if a null logger is specified no logging will be done.
     * 
     * @return ArrayList of all available data fragments for this oid.
     */
    public static ArrayList crawlAndGetAllDataFrags(NewObjectIdentifier oid, 
                                                    TaskLogger log) {

        ArrayList res = new ArrayList();
        Disk[] allDisks = DiskProxy.getClusterDisks();

        for (int d = 0; d < allDisks.length; d++) {
            Disk disk = allDisks[d];
            if (disk == null) {
                // all disk must be online
                return null;
            }
            if (!disk.isEnabled()) {
                // disk must be enabled
                return null;
            }
            String[] frags = TaskFragUtils.getDataFrags(oid, disk.getId());
            if (frags == null) {
                return null;
            }
            for (int i = 0; i < frags.length; i++) {
                int fragid = -1;
                try {
                    fragid = TaskFragUtils.extractFragId(frags[i]);
                } catch (NotFragmentFileException e) {
                    if (log != null)
                        log.warning("Skipping file: " + frags[i] + 
                                    ": " + e.getMessage());
                    continue;
                }
                res.add(new FragmentFile(oid, fragid, disk));
            }
        }
        return res;
    }
}
