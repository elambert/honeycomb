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



package com.sun.honeycomb.oa;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.text.MessageFormat;
import java.util.Locale;

import com.sun.honeycomb.coding.Codable;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.coding.Encoder;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.DeletedObjectException;
import com.sun.honeycomb.common.ObjectCorruptedException;
import com.sun.honeycomb.common.IncompleteObjectException;
import com.sun.honeycomb.common.ObjectReliability;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.coordinator.Disposable;
import com.sun.honeycomb.oa.checksum.ChecksumContext;
import com.sun.honeycomb.oa.hash.ContentHashContext;
import com.sun.honeycomb.layout.Layout;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.resources.ByteBufferList;
import com.sun.honeycomb.resources.ThreadPool;
import com.sun.honeycomb.util.ExtLevel;
import com.sun.honeycomb.util.BundleAccess;
import com.sun.honeycomb.cm.ManagedServiceException;


/**
 * The FragmentFileSet class encapsulates a set of files across the
 * cluster and and exposes methods which perform file operations
 * (create, read, write, delete, etc.) on them.  These files are
 * typically on different disks on different nodes.  FragmentFileSet
 * knows things like how many failures are too many during create, and
 * that reads of SMALL_FILE should start from frag1, and only on
 * failure come from 2-N.  FragmentFileSet manages multiple FragmentFile
 * over the DAAL interface.  In future releases this
 * class may instead communicate directly with OA Servers running on
 * the local and remote nodes.  FragmentFileSet objects are one class of
 * object that get saved in the context.
 * TODO: In recovery mode, how about we only make one fragFile, instead
 * of making the M+N array and only using one entry
 */
public class FragmentFileSet implements Codable, Disposable {
    
    FragmentFileSet(NewObjectIdentifier oid, Layout layout, 
                    ObjectReliability rel) throws OAException {
        init(oid, layout, rel, OAClient.NOT_RECOVERY);
    }

    FragmentFileSet(NewObjectIdentifier oid,
                    Layout layout, 
                    ObjectReliability rel,
                    int recoverFlag) throws OAException {
        init(oid, layout, rel, recoverFlag);
    }

    FragmentFileSet(NewObjectIdentifier oid,
                    Disk[] allDisks,
                    ObjectReliability rel) throws OAException {
        initByCrawling(oid, allDisks, rel);
    }

    /* protected */
    protected FragmentFileSet() {
    }
 
    /**
     * creates and opens tmp. files - returns how many failed
     */
    public void createSequentially(NewObjectIdentifier link,
                                   long size,
                                   long create, 
                                   long retention,
                                   long expiration,
                                   long autoClose, 
                                   long deletion,
                                   byte shred,
                                   short checksumAlg,
                                   int fragmentSize,
                                   int chunkSize,
				   int refCount,
				   int maxRefCount)
        throws OAException {
        int errors = 0;
        for(int f=0; f<fragFiles.length; f++) {
            try {
                if(fragFiles[f] != null && !fragFiles[f].bad()) {
                    fragFiles[f].create(link,
                                        size,
                                        create,
                                        retention, 
                                        expiration,
                                        autoClose,
                                        deletion,
                                        shred,
                                        checksumAlg,
                                        fragmentSize,
                                        chunkSize,
					refCount,
					maxRefCount);
                }
            } catch(OAException oe) {
                try {
                    incErrors("store");
                } catch(OAException ie) {
                    abortCreate();
                    throw ie;
                }
            } 
        }
    }

    /**
     * Parallel method to create temporary fragment files.
     */
    public void create(NewObjectIdentifier link,
                       long size,
                       long create, 
                       long retention,
                       long expiration,
                       long autoClose, 
                       long deletion,
                       byte shred,
                       short checksumAlg,
                       int fragmentSize,
                       int chunkSize,
		       int refCount,
		       int maxRefCount) throws OAException {
        if(link == null) {
            throw new IllegalArgumentException ("Link may not be null");
        }
  
        OAThreadPool pool = creatorThreads.getPool();
        
        try {
            creatorThreads.init(pool,
                                oid,
                                recovery,
                                recoverFrag,
                                fragFiles,
                                link,
                                size,
                                create,
                                retention, 
                                expiration,
                                autoClose,
                                deletion,
                                shred,
                                checksumAlg,
                                fragmentSize,
                                chunkSize,
				refCount,
				maxRefCount);
        
            if (pool.countValidThreads() < minGoodFragsRequired) {
                throw new OAException("Couldn't create enough fragments for oid ["+
                                      oid+"] - ["+
                                      pool.countValidThreads()+" fragments created]");
            }

            pool.execute();

            int nbCreated = pool.waitForCompletion();
            if ((nbCreated == 0) && (recovery)) {
                throw new OAException("OID [" + oid + "] Failed creating fragment");
            }

            if (nbCreated < minGoodFragsRequired) {
                LOG.warning("OID [" + oid + "] Too many errors - aborting");
                abortCreate();
                throw new OAException("OID [" + oid + "] Too many errors - aborting");
            }
            
        } finally {
            creatorThreads.checkInPool(pool);
        }

        // look at create times
        long max_create = -1;
        long avg_create = 0;
        int count = 0;
        for (int i=0; i<fragFiles.length; i++) {
            if(fragFiles[i] != null) {
                count++;
                long t = fragFiles[i].getCreateTime();
                avg_create += t;
                if (t > max_create)
                    max_create = t;
            }
        }
        avg_create /= count;
        LOG.info("OID [" + oid + "] avg/max frag create (ms): " + 
                 avg_create + "/" + max_create);
    }
    
    public void updateFooterWithMetadataField(byte[] metadataField) throws OAException {
        for(int f=0;f<fragFiles.length;f++) {
            // If recovering, skip non-recovery fragments
            if(recovery && f != recoverFrag) {
                threads[f] = null;
                continue;
            }
            if(fragFiles[f] != null && !fragFiles[f].bad()) {
                fragFiles[f].updateFooterWithMetadataField(metadataField);
            }
        }
    }
    
    public void updateFooterWithSystemMetadata(SystemMetadata sm, boolean moreChunks) {
        for(int f=0;f<fragFiles.length;f++) {
            // If recovering, skip non-recovery fragments
            if(recovery && f != recoverFrag) {
                threads[f] = null;
                continue;
            }
            if(fragFiles[f] != null && !fragFiles[f].bad()) {
                fragFiles[f].updateFooterWithSystemMetadata(sm, moreChunks);
             }
        }
    }

    public void updateFooterWithObjectHash(byte[] objectHash) throws OAException {
        for(int f=0;f<fragFiles.length;f++) {
            // If recovering, skip non-recovery fragments
            if(recovery && f != recoverFrag) {
                threads[f] = null;
                continue;
            }
            if(fragFiles[f] != null && !fragFiles[f].bad()) {
                fragFiles[f].updateFooterWithObjectHash(objectHash);
            }
        }
    }
    
    /** writes footer to and closes tmp files  */
    public void writeFooterAndCloseSequentially(long actualSize)
        throws OAException {
        errors = 0; // Reset errors for this call

        for(int f=0;f<fragFiles.length;f++) {
            // If recovering, skip non-recovery fragments
            if(recovery && f != recoverFrag) {
                threads[f] = null;
                continue;
            }
            try {
                if(fragFiles[f] != null && !fragFiles[f].bad()) {
                    fragFiles[f].writeFooterAndClose(actualSize);
                }
            } catch(OAException ie) {
                try {
                    incErrors("store");
                } catch(OAException ieie) {
                    abortCreate();
                }
                throw ie;
            }
        }
    }

    /**
     * Parallel method to write the footer to the fragment file and close it.
     *
     * @param actualSize the size of the object
     */
    public void writeFooterAndClose(long actualSize) throws OAException {

        OAThreadPool pool = closeThreads.getPool();
        
        try {
            closeThreads.init(pool,
                              oid,
                              recovery,
                              recoverFrag,
                              fragFiles,
                              actualSize);

            if (pool.countValidThreads() < minGoodFragsRequired) {
                throw new OAException("Couldn't close enough fragments for oid ["+
                                      oid+"] - ["+
                                      pool.countValidThreads()+" fragments closed]");
            }

            pool.execute();

            int nbClosed = pool.waitForCompletion();
            if ((nbClosed == 0) && (recovery)) {
                throw new OAException("OID [" + oid + "] Failed closing fragment");
            }
        
            if (nbClosed < minGoodFragsRequired) {
                LOG.warning("OID [" + oid + "] Too many errors - aborting");
                abortCreate();
                throw new OAException("OID [" + oid + "] Too many errors - aborting");
            }
        } finally {
            closeThreads.checkInPool(pool);
        }
    }
    
    /** Renames tmp files to perm. */
    public void completeCreateSequentially() throws OAException {
        errors = 0; // Reset errors for this call

        for(int f=0;f<fragFiles.length;f++) {
            // If recovering, skip non-recovery fragments
            if(recovery && f != recoverFrag) {
                continue;
            }
            try {
                if(fragFiles[f] != null && !fragFiles[f].bad()) {
                    fragFiles[f].completeCreate();
                }
            } catch(OAException ie) {
                try {
                    incErrors("store");
                } catch(OAException ieie) {
                    abortCreate();
                }
                throw ie;
            }
        }
    }

    /**
     * Parallel method to rename temporary fragment files.
     */
    public void completeCreate() throws OAException {
        
        OAThreadPool pool = renamerThreads.getPool();
        
        try {
            renamerThreads.init(pool,
                                oid,
                                recovery,
                                recoverFrag,
                                fragFiles);
            
            if (pool.countValidThreads() < minGoodFragsRequired) {
                throw new OAException("Couldn't rename enough fragments for oid ["+
                                      oid+"] - ["+
                                      pool.countValidThreads()+" fragments renamed]");
            }

            pool.execute();

            int nbRenamed = pool.waitForCompletion();
            if ((nbRenamed == 0) && (recovery)) {
                throw new OAException("OID [" + oid + "] Failed renaming fragment");
            }
        
            if (nbRenamed < minGoodFragsRequired) {
                LOG.warning("OID [" + oid + "] Too many errors - aborting");
                abortCreate();
                throw new OAException("OID [" + oid + 
				      "] Too many create errors - aborting");
            }
        } finally {
            renamerThreads.checkInPool(pool);
        }
    }
    
    /**
     * appends some data to the tmp files - this is done
     * one-by-one due to testing that showed that parallel
     * was slower, presumably because of network congestion
     * for nfs. the close is parallel however.
     */
    public void append(ByteBufferList[] buf) throws OAException {
        errors = 0; // Reset errors for this call
        
        if(buf.length != reliability.getTotalFragCount()) {
            throw new IllegalArgumentException("OID [" + oid + "] Expected " +  
                                               reliability.getTotalFragCount() +
                                               " fragments, got " + buf.length
                                               );
        }
        
        int maxTolerableFailures = (reliability.getTotalFragCount() - minGoodFragsRequired);
        
        // Write out the data
        for(int f=0; f<fragFiles.length;f++) {
            // If recovering, skip non-recovery fragments
            if(recovery && f != recoverFrag) {
                continue;
            }
            if(fragFiles[f] != null && !fragFiles[f].bad())
            { 
                try {
                    fragFiles[f].append(buf[f]);
                } catch (OAException oae) {
                    errors++;
                    LOG.warning("append failed to OID [" + oid +
                                "] frag " + f + ": " + oae);
                }
            }
	    
            if(errors > maxTolerableFailures) {
                throw new OAException("OID [" + oid + "] Too many append errors - aborting");
            }

            // Used to be an "else" clause here that printed a WARNING.
            // Removed it (bug #1786) because we expect a frag file to
            // be missing in window between when a disk fails and when
            // recovery finishes reconstructing it, so no need for a
            // scary message that fills up the log when the situation
            // will soon be automatically corrected by Recovery.
            // Might want to have a "crawl" flag, in addition to the 
            // recovery flag, so we could ignore this in the case of
            // crawl but print an error if it happends during non-DLM
            // operation.
        }
    }

    /**
     * commits the same data to all ctx files, maybe flushes frag files
     */
    public void commit(ByteBufferList ctxBuffers, boolean flush)
        throws OAException {
        errors = 0; // Reset errors for this call
        
        // TODO - do this in parallel, it will be much faster
        for(int f=0; f<fragFiles.length;f++) {
            // If recovering, skip non-recovery fragments
            if(recovery && f != recoverFrag) {
                threads[f] = null;
                continue;
            }
            if(fragFiles[f] != null && !fragFiles[f].bad()) {
                fragFiles[f].commit(ctxBuffers, flush);
            }
        }
    }

    /**
     * Rewrite a region of a fragment.
     */
    public void rewriteFragment(ByteBuffer buffer,
                                int fragmentNumber,
                                long offset) {
        if(fragFiles[fragmentNumber] != null &&
           !fragFiles[fragmentNumber].bad()) { 
            fragFiles[fragmentNumber].rewriteBlock(buffer, offset);
        }
    }

    public void deleteContextFiles() {

        OAThreadPool pool = null;

        try {
            try {
                pool = deleteThreads.getPool();
                deleteThreads.init(pool,
                                   oid,
                                   recovery,
                                   recoverFrag,
                                   fragFiles);
            } catch (OAException e) {
                LOG.log(Level.SEVERE,
                        "Failed to instanciate a thread pool to delete fragments ["+
                        e.getMessage()+"]",
                        e);
                return;
            }
        
            pool.execute();
        
            int nbDeleted = pool.waitForCompletion();
            if ((nbDeleted == 0) && (recovery)) {
                LOG.warning("OID [" + oid + "]. Failed deleting fragment");
            }
        
            if (nbDeleted < minGoodFragsRequired) {
                LOG.warning("OID [" + oid + "]. Too many errors in deleteContextFiles");
            }

        } finally {
            if (pool != null) {
                deleteThreads.checkInPool(pool);
            }
        }
    }
    
    public ByteBuffer restoreContextForStore(ByteBuffer ctxarchive,
                                             boolean explicitClose)
        throws OAException {
        // Try each context file until we successfully read from one
        for(int f=0; f<fragFiles.length;f++) {
            // If recovering, skip non-recovery fragments
            if(recovery && f != recoverFrag) {
                threads[f] = null;
                continue;
            }
            if(fragFiles[f] != null && !fragFiles[f].bad()) {
                try {
                    return fragFiles[f].restoreContextForStore(ctxarchive,
                                                               explicitClose);
                } catch (OAException oe) {
                    LOG.info("OID [" + oid + "] Missing ctx file " + f +
                             ": " + oe);
                }
            }
        }
        throw new OAException("OID [" + oid + "] Failed to find a single " +
                              "ctx file");
    }
    
    long open() throws NoSuchObjectException, OAException {
	return open(false, true);
    }
    
    /** 
     * Opens read-only file, reads in footer, and counts failures.
     * If enough fragmentFiles opened, return -1 to indicate success.
     * Otherwise, return creation time of the last fragment opened,
     * which may be 0 if no fragments were opened for this set.
     *
     * If called with !ignoreDeleted flag, will attempt to complete the delete;
     * otherwise will open deleted fragments like normal ones.
     *
     * TODO: This should be done in parallel!
     */
    long open(boolean ignoreDeleted, boolean ignoreIncomplete) 
        throws NoSuchObjectException, OAException {
        int attempts = 0;
        int deleted = 0;
        int incomplete = 0; // count of tmp fragments
        long creationTime = 0;  // obtained from frag footer
        boolean closeFrag = false; // close frag on failure
	
        errors = 0; // Reset errors for this call
	
        for(int f=0; f<fragFiles.length; f++) {
            // try to open
            try {
                // check for null disks and bad files errors
                if(fragFiles[f] == null || fragFiles[f].bad()) {
                    throw new FragmentNotFoundException("OID [" + oid +
                                                    "] Failed to init frag");
                }
                attempts++;
                creationTime = fragFiles[f].open();
            } catch(FragmentNotFoundException fnfe) {
                closeFrag = true;
                if (fragFiles[f] != null && fragFiles[f].checkTmp()) {
                    LOG.fine("OID [" + oid + "] frag " + f + 
                             " is not on data disk but in tmp");
                    errors++;
                    incomplete++;
                } else {
                    LOG.warning("OID[ " + oid + "] No File error opening frag " + f +
                                ": " + fnfe);
                    errors++;
                }
            } catch (DeletedFragmentException dfe) {
                if (!ignoreDeleted) {
                    // will attempt to complete the delete below...
                    closeFrag = true; 
                } // else allow caller to get system MD from deleted frags
                deleted++;
                // deleted frag does not count as an error
            } catch(ObjectCorruptedException oce) {
                closeFrag = true;
                LOG.warning("OID [" + oid + "] Corrupted Object error opening frag "
			    + f + ": " + oce);
                errors++;
            } catch(OAException oae) {
                closeFrag = true;
                LOG.warning("OID[ " + oid + "] OAException Error opening frag " + f +
                            ": " + oae);
                errors++;
            }
            // Not using finally{} because we only close on error.
            if (closeFrag) {
                if (fragFiles[f] != null) {
                    fragFiles[f].close();
                    fragFiles[f] = null;
                }
                closeFrag = false;
            }
        }

        if(!ignoreDeleted && deleted > 0) {
	    // someone at least _started_ trying to delete this object
	    // lets see how far they got

            if(deleted == attempts) {
                // Object was deleted, act like it was never here
                // TODO: Maybe the message could be better?
                throw new DeletedObjectException("OID [" + oid + "] Deleted.");
            } else if(deleted > 0) {	
		// Incomplete Delete
	
		if(deleted + errors == attempts) {
		    // All non-deleted frags are bad, so we can't try
		    // to complete delete not all frags are visible,
		    // see if enough are to know if deleted
		    if(deleted < (2*reliability.getRedundantFragCount())+1) {
			// missing too many frags to know if this object is deleted
			// can't access enough to complete the delete
			// but at least one is deleted
			// I think we lost their data
			// this case should NEVER happen
			throw new OAException("don't know if deleted " +
					      " deleted = " + deleted +
					      " errors = " + errors);
		    } else {
			// Some frags are missing or bad, but enough are deleted
			// we know this object is deleted but we can't complete
			// the delete right now
			throw new DeletedObjectException("OID [" + oid + 
							 "] Deleted, " +
							 errors + " frags bad");
		    }
		} else {
		    // at least some non-deleted frags are good, so try complete
		    // the delete
		    close(); // can't delete any frags we might have opened
		    int delRetries = delete(System.currentTimeMillis());
		    deleted += delRetries;
		    if(deleted < (2*reliability.getRedundantFragCount())+1) {
			throw new OAException("OID [" + oid + "] Failed " +
					      "completing old delete: " +
					      delRetries + " only, and " +
					      deleted + " total deleted.");
		    } else {

                        String str = BundleAccess.getInstance().getBundle().
                            getString("warn.oa.delete.completed");
			Object [] args = {oid};
			LOG.log(ExtLevel.EXT_WARNING, MessageFormat.format(str, args));
			
			throw new DeletedObjectException("OID [" + oid +
							 "] Delete Completed");
		    }
		}
	    }
	}

        // Special handling for incomplete objects: throw IOE and caller will act on it,
        // in recovery context we simply skip such objects.
        //
        if (!ignoreIncomplete && errors > reliability.getRedundantFragCount()) {

            if (incomplete == errors) {
                // All frags missing from data dirs are in tmp dirs, clearly incomplete object.
                // This happens on interruped store of a multichunk object: 
                // all frags of current chunk stay in tmp, all frags of earlier chunks are in data.
                close();
                throw new IncompleteObjectException("OID [" + oid + "] incomplete: " + incomplete + " tmp frags");
            } 
            else if (incomplete == 0 && errors == fragFiles.length) {
                // Not a single data or tmp frag found, must also be an incomplete object.
                // This happens on failed store of multichunk object when too many renames failed:
                // all frags of current chunk (data and tmp) are removed in abortCreate(),
                // all frags of earlier chunks are in data
                close();
                throw new IncompleteObjectException("OID [" + oid + "] incomplete: no tmp or data frags");
            }
            else if (incomplete > 0) {
                // There are tmp frags, but frags were lost to another cause too, may be a data loss case.
                LOG.warning("OID [" + oid + "] tmp count " + incomplete + " but frag errors " + errors);
            }
          
            /*
             * if there are more errors than there more errors than our redundancy
             * can account for then we have an incomplete object.
             */
            close();
            throw new IncompleteObjectException("OID [" + oid + "] is incomplete.");
        }

        // return cTime it too few frags found, else -1 for success
        if (errors > reliability.getRedundantFragCount()) {
            close();
            return creationTime;
        } else {
            return -1;
        }
    }
    
    /** 
     * closes fragment files (either tmp or perm, depending on mode)
     */
    void close() {
        errors = 0; // Reset errors for this call

        for(int f=0; f<fragFiles.length; f++) {
            if(fragFiles[f] != null && !fragFiles[f].bad()) { 
                // no exceptions as there's nothing we can do
                fragFiles[f].close();
            }
        }
    }
    
    /**
     * reads some data from regular files starting at offsets from file
     */
    public long readSequentially(ByteBuffer[] buf, long[] offsets)
        throws OAException {
        errors = 0; // Reset errors for this call
        
        // TODO - do this in parallel, it will be much faster
        long actuallyRead = 0;
        for(int f=0; f<fragFiles.length; f++) {
            try {
                if(fragFiles[f] != null && !fragFiles[f].bad()) {
                    // skip & abandon can make null frags
                    if(buf[f] != null) { 
                        // no exceptions as there's nothing we can do
                        actuallyRead += fragFiles[f].read(buf[f], offsets[f]);
                    } else {
                        LOG.warning("buf " + f + " is NULL");
                    }
                } else {
                    LOG.warning("OID [" + oid + "] fragment [" + f +
                                "] is no good");
                }
            } catch(OAException rie) {
                try {
                    LOG.warning("OID [" + oid + "] Error reading frag " + f +
                                ": " + rie);
                    incErrors("retrieve");
                } catch(OAException ie) {
                    LOG.warning("OID [" + oid + "] too many errors " +
                                "- aborting");
                    close();
                    throw ie;
                }
            } catch(ObjectCorruptedException oce) {
                try {
                    LOG.warning("OID [" + oid + "] Error reading frag " + f +
                                ": " + oce);
                    incErrors("retrieve");
                } catch(OAException ie) {
                    LOG.warning("OID [" + oid + "] too many errors " +
                                "- aborting");
                    close();
                    throw ie;
                }
            }
        }
        return actuallyRead;
    }

    private void waitForThreadExit (Thread[] threads, int cur, int shift) {
        // wait for all threads to exit before we call close() and
        // release the buffers back to the buffer pool
        for(int fe=shift; fe<(threads.length); fe++) {
            if (fe==cur) continue; // skip ourselves
            if (threads[fe] != null) {
                try {
                    threads[fe].join();
                } catch (InterruptedException intr) {
                    // we're probably shutting down anyway, so just
                    // keep trying
                    LOG.log(Level.FINEST,
                        "OID [" + oid + "] interrupted waiting for"
                        + " reader threads to exit");
                    continue;
                }
            }
        }
        LOG.log(Level.FINE,
            "OID [" + oid + "] reader threads exited, closing buffer");
    }

    /**
     * reads some data from regular files starting at offsets from file
     */
    public long read(ByteBuffer[] buf, long[] offsets, int shift)
        throws OAException {
        errors = 0; // Reset errors for this call
        
        // Do the read in parallel on all the fragments
        for(int f=shift; f<fragFiles.length; f++) {
            
            // Skip bad fragment files
            if((fragFiles[f] == null) || (fragFiles[f].bad())) {
                LOG.warning("OID [" + oid + "] fragment [" + f + 
                            "] is no good");
                threads[f] = null;
                try {
                    incErrors("retrieve");
                } catch(OAException ie) {
                    LOG.warning("OID [" + oid + "] too many bad fragments " +
                                "- aborting");
                    waitForThreadExit (threads, f, shift);
                    close();
                    throw ie;
                }
                continue;
            }
            
            // Skip null buffers
            if(buf[f-shift] == null) {
                LOG.warning("buf " + ((int)f-shift) + " is NULL");
                threads[f] = null;
                try {
                    incErrors("retrieve");
                } catch(OAException ie) {
                    LOG.warning("OID [" + oid + "] too many bad fragments " +
                                "- aborting");
                    waitForThreadExit (threads, f, shift);
                    close();
                    throw ie;
                }
                continue;
            }

            // Create a new fragment reader and start its thread
            fragmentReaders[f].initializeArgs(buf[f-shift],
                                              offsets[f-shift]);
            
            // TODO: We should fetch a thread from a pool and use it
            threads[f] = new Thread(fragmentReaders[f]);
            threads[f].start();
        }

        // Wait for all the readers and return the total number of bytes
        // read from all the fragments.
        // FIXME - we should rewrite OA thread implementation.
        // The OAThreadPool should be underneath DAAL.
        long actuallyRead = 0;
        for(int f=shift; f<(fragFiles.length); f++) {
            if(threads[f] == null) {
                // already counted this error
                continue;
            }
            try {
                boolean done = false;
                do {
                    try {
                        threads[f].join();
                        done = true;
                    } catch (InterruptedException ie) {
                        LOG.log(Level.WARNING, "Thread interrupted");
                    }
                } while (!done);
                
                if(fragmentReaders[f] != null) {
                    actuallyRead += fragmentReaders[f].getBytesRead();
                }
                threads[f] = null;
                
            } catch(Exception rie) {
                threads[f] = null;
                if(fragFiles[f] != null) {
                    fragFiles[f].setLastReadBad();
                }
                buf[f-shift].rewind();
                try {
                    LOG.log(Level.WARNING, 
                            "OID [" + oid + "] error in fragment [" +
                            f + "] Exception: " + rie,
                            rie);
                    incErrors("retrieve");
                } catch(OAException ie) {
                    LOG.warning("OID [" + oid + "] too many read errors " +
                                "- aborting");
                    waitForThreadExit (threads, f, shift);
                    close();
                    throw ie;
                }
            }
        }

        return actuallyRead;
    }

    public long readSingle(ByteBuffer buf, long offset, int frag)
        throws OAException {
        errors = 0; // Reset errors for this call
        
        if(fragFiles[frag] == null) {
            throw new OAException("OID [" + oid + "] Fragment file " + frag +
                                  " is null");
        }
        if(fragFiles[frag].bad) {
            throw new OAException("OID [" + oid + "] Fragment file " + frag +
                                  " is bad");
        }

        long bytesRead = 0;
        try {
            bytesRead = fragFiles[frag].read(buf, offset);
        } catch (ObjectCorruptedException oce) {
            throw new OAException("OID [" + oid + "] Fragment file " + frag +
                                  " is corrupted at offset " + offset + ": " +
                                  oce);
        }

        return bytesRead;
    }

    public int deleteRefFromReferee(long deletionTime) throws OAException {

        int fragsDecs = 0;
        OAException oae = null;

        for (int f=0; f<fragFiles.length; f++) {
            if (fragFiles[f] != null) {
                try {    long size = fragFiles[f].deleteRefFromReferee(deletionTime);
                    fragsDecs++;
                } catch(OAException e) {
                    LOG.info("Failed to delete ref from referee, oid = "
                        + oid + ", fragment = " + fragFiles[f].getFragNum());
                }
            }            
        }
        return fragsDecs;
    }

    /**
     * tries to delete regular object - returns how many fragments
     * were sucessfully deleted
     * TODO: this would be _much_ faster if it were parallel
     */
    public int delete(long deletionTime) {
        errors = 0; // Reset errors for this call
        int deleted = 0;
	long objectSize = -1;
	
        for(int f=0;f<fragFiles.length;f++) {
            try {
		if(fragFiles[f] != null && !fragFiles[f].bad()) {
		    objectSize = fragFiles[f].delete(deletionTime, objectSize);
                    deleted++;
                } 
            } catch(FragmentNotFoundException fnfe) {
                errors++;
                LOG.warning("OID [" + oid + "] No frag " + f +
                            " found to delete: "  + fnfe);
            } catch(OAException ie) {
                errors++; // Don't want usual too-many-errors count
                LOG.warning("OID [" + oid + "] Deletion of frag " + f +
                            " failed: "  + ie);
            }
        }

        return deleted; // Return sucess count
    }
    
    public void recoverDeletedStub(int recoverFragNum, Disk disk) 
	throws OAException {
	
	int errors = 0;
	
	for(int f=0;f<fragFiles.length;f++) {
	    if(f != recoverFragNum && 
	       fragFiles[f] != null && 
	       !fragFiles[f].bad()) {
		try {
		    fragFiles[f].open();
		} catch(DeletedFragmentException dfe) {
		    // Good, use info from this peer to create the recovered stub
		    try {
			fragFiles[recoverFragNum] = 
			    fragFiles[f].
			    recoverDeletedStubPeer(recoverFragNum, disk);
		    } catch (FragmentNotFoundException fnfe) {
			throw new OAException("No file: " + fnfe);
		    }
			return;
		} catch(OAException oae) {
		    errors++;
		} catch(FragmentNotFoundException fnfe) {
		    errors++;
		} catch(ObjectCorruptedException oce) {
		    errors++;
		}
	    }
	}
    }

    /**
     * Increment the ref count for all fragments, taking care of any
     * missed increments by comparing the number of increments on each
     * fragment.
     */
    public void incRefCount()
            throws OAException, FragmentFileSetLockFailedException {

        // Caveat: the variable "maxRefCount" in the fragment footer
        // is confusingly named: it should have been called
        // "numIncrements". It is not the "max" of the refcount; it is
        // the number of increments of the refcount. (It is thus a
        // monotonically increasing value, and we can never make more
        // than 2^31 addMetadata calls to an object.)
        //
        // We proceed in 2 phases.  In phase 1 we read maxRefCount
        // from as many frags as we can and choose the largest one. We
        // know that all the fragments must have had the same number
        // of increments, so if the numIncrements of any fragment is
        // less than the largest, that fragment must have missed that
        // many increments.  In phase 2 we increment the refCount and
        // maxRefCount of the fragments, using the value from phase 1
        // to correct refCount and maxRefCount for lost increments.
        //
        // Note that we CANNOT lock the fragments individually and
        // separately. It is this entire operation -- incremement a
        // chunk's ref count -- that is the critical section, and it
        // is this that needs to be protected with a lock. To see why,
        // re-define this operation as: find the largest maxRefCount,
        // and make sure each fragment's is set to one plus that number.
        // If two threads are doing this operation together: they both
        // get the same maxMaxRefCount, so they will both make sure
        // that all fragments are adjusted to have their ref counts
        // one plus that number. In other words, an increment was
        // lost.

        errors = 0; // Reset errors for this call

        long acquireTime = 0;
        int maxNumIncs = -1;
        try {
            // Get the lock on all fragment files
            openAndLock();

            if (LOG.isLoggable(Level.FINE))
                // We're going to measure how long the fragments were locked for
                acquireTime = System.currentTimeMillis();

            maxNumIncs = getMaxMaxRefCount();

            if (LOG.isLoggable(Level.FINE))
                LOG.fine("Got lock; no. of increments for " +
                         oid.toExternalHexString() + " = " + maxNumIncs);

            int incs = 0;
            for (int f = 0; f < fragFiles.length; f++) {
                try {
                    if(fragFiles[f] != null && !fragFiles[f].bad()) {
                        fragFiles[f].incRefCount(maxNumIncs);
                        incs++;
                    }
                } catch (FragmentNotFoundException fnfe) {
                    errors++;
                    LOG.warning("No fragment " + f + " for " +
                                oid.toExternalHexString() + ":" + fnfe);
                } catch (OAException ie) {
                    errors++;
                    LOG.log(Level.WARNING, "Failed to increment frag " + f +
                            " of " + oid.toExternalHexString(), ie);
                }
            }

            // If < 2P+1 were inc'd, we may lose them and end up with a
            // too-low reference count, which could result in a pre-mature
            // delete, so abort
            int minFrags = 2*reliability.getRedundantFragCount() + 1;
            if (incs < minFrags) {
                String msg = "Insufficient fragments: need " + minFrags +
                    " but only succeeded with " + incs + " - aborting.";
                throw new IncRefFailedException(msg);
            }
        }
        finally {
            // Make sure the locks are all released
            unlockAndClose();
            if (LOG.isLoggable(Level.FINE)) {
                long releaseTime = System.currentTimeMillis();
                LOG.fine("Lock " + oid.toExternalHexString() + " released; " +
                         "held for " + (releaseTime - acquireTime) + "ms " +
                         maxNumIncs + ":" + acquireTime + "-" + releaseTime);
            }
        }
    }
    
    public void setRetentionTime(long date) throws OAException {
        errors = 0; // Reset errors for this call

        int updates = 0;
        for(int f=0;f<fragFiles.length;f++) {
            try {
                if(fragFiles[f] != null && !fragFiles[f].bad()) {
                    fragFiles[f].setRetentionTime(date);
                    updates++;
                }
            } catch(FragmentNotFoundException fnfe) {
                errors++;
                LOG.warning("OID [" + oid + "] No frag " + f +
                            " found to set retention time on: "  + fnfe);
            } catch(OAException oae) {
                errors++;
                LOG.warning("OID [" + oid + "] setRetentionTime " +
                            "failed for " + f + " failed: " + oae);
            }
        }

	// If less than the number of data fragments were updated,
	// throw an exception.
	if (updates < reliability.getDataFragCount()) {
	    throw new OAException("Failed to update retention time " +
                                  "on at least " +
                                  reliability.getDataFragCount() +
                                  " fragments");
	}
    }

    //  Codable Interface
    public void encode(Encoder encoder) {
        encoder.encodeKnownClassCodable(oid);
        encoder.encodeKnownClassCodable(reliability);
        encoder.encodeInt(recoverFrag);
        encoder.encodeInt(fragFiles.length);
        for(int f=0; f<fragFiles.length;f++) {
            if(fragFiles[f] != null) {
                encoder.encodeKnownClassCodable(fragFiles[f]);
            } else {
                encoder.encodeKnownClassCodable(FragmentFile.NULL);
            }
        }
    }
    
    public void decode(Decoder decoder) {
        oid = new NewObjectIdentifier();
        decoder.decodeKnownClassCodable(oid);
        reliability = new ObjectReliability();
        decoder.decodeKnownClassCodable(reliability);
        recoverFrag = decoder.decodeInt();
        int length = decoder.decodeInt();
        
        recovery = (recoverFrag != OAClient.NOT_RECOVERY);
       
        makeArrays(length);
        
        calculateMinGoodFragsRequired();

        for(int f=0; f<fragFiles.length;f++) {
            try {
                fragFiles[f] = instantiateFragmentFile();
            } catch (OAException e) {
                LOG.severe(e.getMessage());
            }
	    
            decoder.decodeKnownClassCodable(fragFiles[f]);
            if(fragFiles[f].isNull()) {
                fragFiles[f] = null;
            }
        }
    }
    
    public ObjectReliability getReliability() {
        return reliability;
    }

    // Get the compliance retention time from the fragment with the
    // greatest time. This means reading all valid fragments.
    public long getRetentionTime()
        throws DeletedFragmentException,
               ObjectCorruptedException,
               OAException {
        long retentionTime = -2;
        int numFilesRead = 0;

        for(int f=0; f<fragFiles.length; f++) {
            try {
                if (fragFiles[f] != null && !fragFiles[f].bad()) {
                    long newRetentionTime = fragFiles[f].getRetentionTime();
                    numFilesRead++;

                    // Set the greatest retention time. Be careful to
                    // preserve infinite (-1) time since that is
                    // greater than unset (0) time.
                    if ((newRetentionTime == -1 && retentionTime == 0) ||
                        (!(newRetentionTime == 0 && retentionTime == -1 ) &&
                         (newRetentionTime > retentionTime))) {
                        retentionTime = newRetentionTime;
                    }
                }

            } catch(FragmentNotFoundException fnfe) {
                LOG.warning("OID [" + oid + "] No frag " + f +
                            " found to read retention time from: "  + fnfe);
            }
        }

        if (numFilesRead < reliability.getDataFragCount()) {
	    throw new OAException("Failed to retrieve the retention time " +
                                  "from at least " +
                                  reliability.getDataFragCount() +
                                  " fragments");
        }

        return retentionTime;
    }
    
    public SystemMetadata getSystemMetadata() throws OAException {
        errors = 0; // Reset errors for this call
        
        for(int f=0; f<fragFiles.length;f++) {
            if(fragFiles[f] != null && !fragFiles[f].bad()) {
                return fragFiles[f].getSystemMetadata();
            }
        }
        LOG.warning("OID [" + oid + "] Found no good fragment file");
        throw new OAException("OID [" + oid + "] Found no good fragment " +
                              "file for sysmd");
    }

    public NewObjectIdentifier getOID() {
        return oid;
    }

    /** The first fragment we can get an answer from will do */
    public long getObjectSize() throws OAException {
        errors = 0; // Reset errors for this call
        
        for(int f=0; f<fragFiles.length;f++) {
            if(fragFiles[f] != null && !fragFiles[f].bad()) {
                return fragFiles[f].getObjectSize();
            }
        }
        LOG.warning("OID [" + oid + "] No good frag file to read size from.");
        throw new OAException("OID [" + oid + "] No good frag file to read " +
                              "size from.");
    }

    /** The first fragment we can get an answer from will do */
    public byte[] getObjectMetadataField() throws OAException {

        errors = 0; // Reset errors for this call
        
        for(int f=0; f<fragFiles.length;f++) {
            if(fragFiles[f] != null && !fragFiles[f].bad()) {
                return fragFiles[f].getObjectMetadataField();
            }
        }
        LOG.warning("OID [" + oid + "] No good frag file to read md field " +
                    "from.");
        throw new OAException("OID [" + oid + "] No good frag file to " +
                              "read md field from.");
    }
    
    /** The first fragment we can get an answer from will do */
    public long getObjectCreateTime() throws OAException {
        errors = 0; // Reset errors for this call
        
        for(int f=0; f<fragFiles.length;f++) {
            if(fragFiles[f] != null && !fragFiles[f].bad()) {
                return fragFiles[f].getObjectCreateTime();
            }
        }
        LOG.warning("OID [" + oid + "] No good frag file to read create " +
                    "time from.");
        throw new OAException("OID [" + oid + "] No good frag file to " +
                              "read create time from.");
    }
    
    /** The first fragment we can get an answer from will do */
    public long  getObjectDeleteTime() throws OAException {
        errors = 0; // Reset errors for this call
        
        for(int f=0; f<fragFiles.length;f++) {
            if(fragFiles[f] != null && !fragFiles[f].bad()) {
                return fragFiles[f].getObjectDeleteTime();
            }
        }
        LOG.warning("OID [" + oid + "] No good frag file to read delete " +
                    "time from.");
        throw new OAException("OID [" + oid + "] No good frag file to " +
                              "read delete time from.");
    }
    
    /** The first fragment we can get an answer from will do */
    public byte[] getObjectContentHash() throws OAException {
        errors = 0; // Reset errors for this call
        
        for(int f=0; f<fragFiles.length;f++) {
            if(fragFiles[f] != null && !fragFiles[f].bad()) {
                return fragFiles[f].getObjectContentHash();
            }
        }
        LOG.warning("OID [" + oid + "] No good frag file to read chash from.");
        throw new OAException("OID [" + oid + "] No good frag file to " +
                              "read chash from.");
    }

    /** The first fragment we can get an answer from will do */
    public int getFragmentSize() throws OAException {
        errors = 0; // Reset errors for this call
        for(int f=0; f<fragFiles.length;f++) {
            if(fragFiles[f] != null && !fragFiles[f].bad()) {
                return fragFiles[f].getFragmentSize();
            }
        }
        String err = new String("OID [" + oid + "] No good frag file to " +
                                "read fragment size from.");
        LOG.warning(err);
        throw new OAException(err);
    }

    /** The first fragment we can get an answer from will do 
     * WARNING: chunkSize is in blocks, not bytes or kbytes */
    public int getChunkSize() throws OAException {
        errors = 0; // Reset errors for this call
        for(int f=0; f<fragFiles.length;f++) {
            if(fragFiles[f] != null && !fragFiles[f].bad()) {
                return fragFiles[f].getChunkSize();
            }
        }
        String err = new String("OID [" + oid + "] No good frag file to " +
                                "read chunk size from.");
        LOG.warning(err);
        throw new OAException(err);
    }

    /** The first fragment we can get an answer from will do */
    public int getSomeRefCount () throws OAException {
        errors = 0; // Reset errors for this call
        for(int f=0; f<fragFiles.length;f++) {
            if(fragFiles[f] != null && !fragFiles[f].bad()) {
                return fragFiles[f].getRefCount();
            }
        }
        String err = new String("OID [" + oid + "] No good frag file to " +
                                "read ref count from.");
        LOG.warning(err);
        throw new OAException(err);
    }

    public int getMaxMaxRefCount() throws OAException {
	int result = -2;
	
        errors = 0; // Reset errors for this call
	int found = 0;
        for(int f=0; f<fragFiles.length;f++) {
            if(fragFiles[f] != null && !fragFiles[f].bad()) {
                if(fragFiles[f].getMaxRefCount() > result) {
		    result = fragFiles[f].getMaxRefCount();
		}
		found++;
            }
        }
	
	// If >= 2P+1 were read, we can trust the result
	if(found >= ((2*reliability.getRedundantFragCount()) + 1)) {
	    return result;
	}
	   
        String err = new String("OID [" + oid + "] too few ( " + found + 
				" ) to read ref count from.");
        LOG.warning(err);
        throw new OAException(err);
    }

    // Builds a list expressing which fragments have erorrs, and which don't
    public boolean[] getGoodFragments(int shift) {
        errors = 0; // Reset errors for this call
        
        boolean[] res = new boolean[fragFiles.length];
        for(int f=shift; f<fragFiles.length;f++) {
            if(fragFiles[f] == null) {
                res[f-shift] = false;
            } else {
                int i = f-shift;
                res[f-shift] = !fragFiles[f].bad();
                if (res[f-shift]) {
                    res[f-shift] = !fragFiles[f].lastReadBad();
                }

            }
        }
        return res;
    }

    /**
     * Get the list of corrupted fragments from the previous read operation.
     *
     * @return boolean[] the array of booleans describing if the last
     *                   state of the read operation
     */
    public boolean[] getCorruptedFragments() {
        boolean[] res = new boolean[fragFiles.length];
        for(int f=0; f<fragFiles.length;f++) {
            if(fragFiles[f] != null) { 
                res[f] = fragFiles[f].lastReadBad();
            } else {
                res[f] = false;
            }
        }
        return res;
    }

  /**
   * Get ref fields strings for all fragments
   *
   * @return String refFields string for all fragments
   */
  public String getRefFieldsString() {
	String result = "[";
	for(int f=0; f<fragFiles.length;f++) {
	  if(fragFiles[f] != null) { 
		result = result + fragFiles[f].getRefFieldsString();
	  }
	}
	
	return result + "]";
  }
  
  /**
     * Method to get a fragment's checksum context.
     *
     * @param fragmentNumber the fragment to get the context from
     * @return ChecksumContext the checksum context of that fragment
     */
    public ChecksumContext getFragmentChecksumContext(int fragmentNumber) {
        if(fragFiles[fragmentNumber] != null &&
           !fragFiles[fragmentNumber].bad()) {
            return fragFiles[fragmentNumber].getChecksumContext();
        }
        return null;
    }
    
    public int getErrors() {return errors;}
    
    public void dispose() {
        for(int f=0; f<fragFiles.length;f++) {
            if(fragFiles[f] != null) {
                fragFiles[f].dispose();
                fragFiles[f] = null;
            } 
        }
    }

    // PUBLIC MEMBERS //
    
    public static final String CTXTAG = "OAFragmentFileSet";

    // PRIVATE METHODS //
    
    private void incErrors(String ApiOp) throws OAException {
        if(++errors > reliability.getRedundantFragCount()) {

            String str = BundleAccess.getInstance().getBundle().
                getString("err.oa.resource");

	    Object [] args = {ApiOp};
	    LOG.log(ExtLevel.EXT_SEVERE, MessageFormat.format(str, args));

	    

            throw new OAException("OID [" + oid + "] Too many errors: " +
                                  errors);
        }
    }

    // Try to clean up from the create
    
    private void abortCreate() {
        for(int f=0; f<fragFiles.length;f++) {
            // If recovering, skip non-recovery fragments
            if(recovery && f != recoverFrag) {
                threads[f] = null;
                continue;
            }
            if (fragFiles[f] != null) {
                fragFiles[f].abortCreate();
            }
        }
    }

    /*
     * PRIVATE CLASSES
     */

    /**
     * Class to read from a fragment file.
     */
    private class FragmentReader 
        implements Runnable {

        FragmentFile fragmentFile;
        int fragmentNumber;
        Exception exception = null;

        /** Arguments */
        ByteBuffer buffer;
        long offset;

        /** Return value */
        long bytesRead;

        public FragmentReader(FragmentFile fragmentFile,
                              int fragmentNumber) {
            this.fragmentFile = fragmentFile;
            this.fragmentNumber = fragmentNumber;
        }

        // Alert API ... not usable unless e.g. thread/code pools are used
        // since this object is disposed of when done
        int reads = 0;
        long read_time = 0;
        long read_bytes = 0;

        public void initializeArgs(ByteBuffer buffer,
                                   long offset) {

            // Temporary debugging double-check
            if(buffer == null) {
                LOG.severe("buffer for frag " + fragmentNumber + " is null");
                throw new 
                    IllegalArgumentException("buffer for frag " + 
                                             fragmentNumber + " is null");
            }
            
            // Arguments
            this.buffer = buffer;
            this.offset = offset;

            // Return value
            bytesRead = 0;

            // Internal variables
            exception = null;
        }

        /**
         * Get the result of the read.
         *
         * @return long the number of bytes read from the fragment
         * @throws Exception
         */
        public long getBytesRead() throws Exception {
            if (exception != null) {
                throw exception;
            }
            return bytesRead;
        }

        /**
         * The run method that reads the fragment
         */
        public void run() {
            exception = null;
            try {
                // Temmporary debugging triple-check
                if(fragmentFile == null) {
                    LOG.warning("FragmentFile " + fragmentNumber + " is null");
                    exception = new Exception("FragFile " + fragmentNumber + 
                                              " is null");
                    return;
                }
                if(buffer == null) {
                    LOG.warning("buffer for frag " + fragmentNumber + 
                                " is null");
                    exception = new Exception("Buf for frag " + 
                                              fragmentNumber + " is null");
                    return;
                }
                fragmentFile.resetReadMeasurements();
                bytesRead = fragmentFile.read(buffer, offset);
                if (bytesRead > 0) {
                    reads++;
                    read_time += fragmentFile.getReadTime();
                    read_bytes += fragmentFile.getReadBytes();
                }
            } catch (Exception e) {
                exception = e;
                fragmentFile.setLastReadBad();
            }
        }
    }

    /**
     * Class to write the fragment footer and close the fragment file.
     */
    public static class FragmentFooterWriterAndCloser
        implements OAThreads.RunnableCode {

        FragmentFile fragmentFile;
        int fragmentNumber;
        Exception exception = null;

        // OAStats/Alert
        private int closes = 0;
        private long close_time = 0;
        protected int getCloses() {
            return closes;
        }
        protected long getCloseTime() {
            return close_time;
        }

        /** Arguments */
        long actualSize;
        
        public FragmentFooterWriterAndCloser() {
        }
        
        public void initializeArgs(FragmentFile fragmentFile,
                                   int fragmentNumber,
                                   long actualSize) {
            // Arguments
            this.fragmentFile = fragmentFile;
            this.fragmentNumber = fragmentNumber;
            this.actualSize = actualSize;

            // Internal variables
            exception = null;
        }

        /**
         * Get the status of the operation.
         *
         * @return boolean true on success
         * @throws Exception
         */
        public void checkStatus() throws Exception {
            if (exception != null) {
                throw exception;
            }
        }

        /**
         * The run method that writes/closes the fragment
         */
        public void run() {
            exception = null;
            try {
                fragmentFile.writeFooterAndClose(actualSize);
                long t = fragmentFile.getCloseTime();
                if (t != -1) {
                    closes++;
                    close_time += t;
                }
            } catch(Exception e) {
                exception = e;
                LOG.warning("Error writing footer or closing frag [" + 
			    fragmentNumber + "]" + e);
            }
        }
    }

    /**
     * Class to rename a temporary fragment to its permanent name.
     */
    public static class FragmentRenamer
        implements OAThreads.RunnableCode {
        
        FragmentFile fragmentFile;
        int fragmentNumber;
        Exception exception = null;
        
        public FragmentRenamer() {
        }
        
        // OAStats/Alert 
        private int renames = 0;
        private long rename_time = 0;
        protected int getRenames() {
            return renames;
        }
        protected long getRenameTime() {
            return rename_time;
        }

        public void initializeArgs(FragmentFile fragmentFile,
                                   int fragmentNumber) {
            // Internal variables
            this.fragmentFile = fragmentFile;
            this.fragmentNumber = fragmentNumber;
            exception = null;
        }

        /**
         * Get the status of the operation.
         *
         * @return boolean true on success
         * @throws Exception
         */
        public void checkStatus() throws Exception {
            if (exception != null) {
                throw exception;
            }
        }
        
        /**
         * The run method that renames the fragment
         */
        public void run() {
            exception = null;
            try {
                fragmentFile.completeCreate();
                long t = fragmentFile.getRenameTime();
                if (t != -1) {
                    renames++;
                    rename_time += t;
                }
            } catch(Exception e) {
                exception = e;
                LOG.warning("Error renaming frag [" + fragmentNumber +
                            "]" + e);
            }
        }
    }

    /**
     * Class to create a temporary fragment.
     */
    public static class FragmentCreator
        implements OAThreads.RunnableCode {

        FragmentFile fragmentFile;
        int fragmentNumber;
        Exception exception = null;

        /** Arguments */
        NewObjectIdentifier link;
        long size;
        long create;
        long retention;
        long expiration;
        long autoClose;
        long deletion;
        byte shred;
        short checksumAlg;
        int fragmentSize;
        int chunkSize;
	int refCount;
	int maxRefCount;
        
        public FragmentCreator() {
        }
        
         // OAStats/Alert
        private int creates = 0;
        private long create_time = 0;
        protected int getCreates() {
            return creates;
        }
        protected long getCreateTime() {
            return create_time;
        }

        public void initializeArgs(FragmentFile fragmentFile,
                                   int fragmentNumber,
                                   NewObjectIdentifier link,
                                   long size,
                                   long create, 
                                   long retention,
                                   long expiration,
                                   long autoClose, 
                                   long deletion,
                                   byte shred,
                                   short checksumAlg,
                                   int fragmentSize,
                                   int chunkSize,
                                   int refCount,
                                   int maxRefCount) {

            this.fragmentFile = fragmentFile;
            this.fragmentNumber = fragmentNumber;
            this.link = link;
            this.size = size;
            this.create = create;
            this.retention = retention;
            this.expiration = expiration;
            this.autoClose = autoClose;
            this.deletion = deletion;
            this.shred = shred;
            this.checksumAlg = checksumAlg;
            this.fragmentSize = fragmentSize;
            this.chunkSize = chunkSize;
            this.refCount = refCount;
            this.maxRefCount = maxRefCount;

            // Internal variables
            exception = null;
        }

        /**
         * Get the status of the operation.
         *
         * @return boolean true on success
         * @throws Exception
         */
        public void checkStatus()
            throws Exception {
            if (exception != null) {
                Exception t = exception;
                exception = null;
                throw t;
            }
        }

        /**
         * The run method that creates the fragment
         */
        public void run() {
            exception = null;
            try {
                fragmentFile.create(link,
                                    size,
                                    create,
                                    retention, 
                                    expiration,
                                    autoClose,
                                    deletion,
                                    shred,
                                    checksumAlg,
                                    fragmentSize,
                                    chunkSize,
				    refCount,
				    maxRefCount);
                 long t = fragmentFile.getCreateTime();
                 if (t != -1) {
                     creates++;
                     create_time += t;
                 }
            } catch(Exception e) {
                exception = e;
                LOG.warning("Error reading frag [" + fragmentNumber + "]" + e);
            }
        }
    }
    
    // Today only used for logging
    public Layout getLayout() {
        return layout;
    }

    public boolean recovery() {
        return recovery;
    }

    /** Lock all fragments of a set */
    private void openAndLock() throws FragmentFileSetLockFailedException {
        // There is no one place that we can lock all fragments, since
        // there is no guarantee that each thread has the same idea of
        // what fragments are available. The way to resolve this is:
        // try to lock all fragments, and then see how many we
        // actually got. If we got a sufficient number we succeeded in
        // getting the lock and we proceed. If not, release all locks,
        // and caller will re-try. (Note: do not break out of the
        // while loop when we have enough locks -- we must try to lock
        // them all.)

        int nLocks = 0;
        int errs = 0;
        for(int f = 0; f < fragFiles.length; f++) {
            try {
                if(fragFiles[f] != null && !fragFiles[f].bad()) {
                    fragFiles[f].openAndLock();
                    nLocks++;
                }
            } catch(FragmentNotFoundException fnfe) {
                LOG.warning("Couldn't find frag " + f + ":" + fnfe);
            } catch(OAException ie) {
                errs++;
                LOG.warning("Couldn't lock fragment " + f +
                            " of " + oid.toExternalHexString() + ":" + ie);
            }
        }

        // We insist that we got locks on *all* available fragments,
        // not just a majority -- this makes sure that any other
        // threads that are doing something else (i.e. not trying to
        // lock *all* fragments) are also excluded.

        int needed = 2*reliability.getRedundantFragCount() + 1;

	if (errs > 0 || nLocks < needed) {
            // We didn't get enough locks to be guaranteed exclusive access

            String msg = "Couldn't lock " + oid.toExternalHexString();
            if (errs > 0)
                msg += " -- failed on " + errs + " fragments";
            else
                msg += " -- need at least " + needed + " frags, got " + nLocks;
            LOG.warning(msg);

            unlockAndClose();

            throw new FragmentFileSetLockFailedException(oid.toExternalHexString());
	}

        if (LOG.isLoggable(Level.FINER))
            LOG.finer("Got lock on all frags of " + oid.toExternalHexString());
    }

    /** Release the lock on all fragments of a set */
    private void unlockAndClose() {
        for(int f = 0; f < fragFiles.length; f++) {
            try {
                if(fragFiles[f] != null && !fragFiles[f].bad())
                    fragFiles[f].unlockAndClose();
            }
            catch (Exception e) {
                LOG.warning("Couldn't unlock fragment " + f + " of " +
                            oid.toExternalHexString());
            }
        }
    }

    private void init(NewObjectIdentifier oid,
                      Layout layout, 
                      ObjectReliability reliability,
                      int recoverFrag) throws OAException {
                          
       errors = 0;
       if(layout.size() < reliability.getTotalFragCount()) {
            throw new IllegalArgumentException("layout has " + layout.size() + 
                                               " disks, but reliability M+N is " + 
                                               reliability.getTotalFragCount()
                                               );
        }
        
        this.oid = oid;
        this.reliability = reliability;
        this.recoverFrag = recoverFrag;
        this.recovery = (recoverFrag != OAClient.NOT_RECOVERY);
        this.layout = layout;

        makeArrays(layout.size());
        
        calculateMinGoodFragsRequired();
        
        for(int f=0; f<fragFiles.length; f++) {
            // If recovering, skip non-recovery fragments
            if(recovery && f != recoverFrag) {
                threads[f] = null;
                continue;
            }
            Disk d = layout.getDisk(f);
            if(d != null && !d.isNullDisk()) {
                fragFiles[f] = instantiateFragmentFile();
                fragFiles[f].init(oid, f, d, reliability);
                fragmentReaders[f] = new FragmentReader(fragFiles[f], f);
            } else {
                fragFiles[f] = null;
                fragmentReaders[f] = null;
                LOG.warning("OID [" + oid + "] fragment [" + f + "] is null");
                try {
                    incErrors("retrieve");
                } catch(OAException ie) {
                    LOG.warning("Too many errors creating frag fileset for " +
                                "oid [" + oid + "] Exception: " + ie);
                    throw ie;
                }
            }
        }
    }

    // TODO: This would be much faster in parallel However, as this is
    // a rare case, and bad actors can use it to DOS us, Maybe we
    // should take the slower implementation which loads the system
    // slightly less?  Either way, optimizing this is low priority as
    // it should rarely if ever happen
    private void initByCrawling(NewObjectIdentifier oid,
                                Disk[] allDisks, 
                                ObjectReliability reliability) 
        throws OAException {
            
            errors = 0;
            if(allDisks.length < reliability.getTotalFragCount()) {
                throw new 
                IllegalArgumentException("layout full disk list has " + 
                                         allDisks.length + 
                                         " disks, but reliability M+N is " + 
                                         reliability.getTotalFragCount());
            }
            
            this.oid = oid;
            this.reliability = reliability;
            this.recoverFrag = recoverFrag;
            this.recovery = (recoverFrag != OAClient.NOT_RECOVERY);
            this.layout = null;     
            // TODO: can we make layout based on where we find the frags?
            
            makeArrays(reliability.getTotalFragCount());
            
            calculateMinGoodFragsRequired();
            
            // Start by making sure fragFiles are null
            for(int f=0; f< fragFiles.length; f++) {
                fragFiles[f] = null;
            }
            
            // Do a dir lookup on each disk, one at a time, and see what
            // frags we find
            
            int fragsFound = 0;
            
            for(int d=0; d<allDisks.length;d++) {
                
                if(allDisks[d] == null || 
                   !allDisks[d].isEnabled()) {
                    continue;
                }
                
                DiskId diskId = allDisks[d].getId();
                OAServerIntf api = OAServerIntf.Proxy.getAPI(diskId.nodeId());
                if (api == null) {
                    continue;
                }
                int[] fragNums = null;
                try {
                    fragNums = api.listFragNumbers(oid, diskId);
                } catch (ManagedServiceException me) {
                    LOG.warning("failed to get frags list for " + diskId + " " + me);
                }
                if (fragNums == null) {
                    continue;
                }
                for (int i = 0; i < fragNums.length; i++) {
                    int fragNum = fragNums[i];
                    if (fragNum < 0 || fragNum >= fragFiles.length) {
                        LOG.warning("found invalid fragNum crawling for " +
                                    oid + " on " + diskId);
                        continue;
                    }
                    if (fragFiles[fragNum] == null) {
                        fragFiles[fragNum] = instantiateFragmentFile();
                    }
                    fragFiles[fragNum].init(oid, fragNum, allDisks[d], reliability);
                    fragmentReaders[fragNum] = 
                        new FragmentReader(fragFiles[fragNum], fragNum);
                    fragsFound++;
                }
            }
            
            if(fragsFound < reliability.getDataFragCount()) {
                throw new OAException("Found too few frags - only " + fragsFound);
            }
        }
    
    void makeArrays(int len) {
        fragFiles = new FragmentFile[len];
        threads = new Thread[fragFiles.length];
        fragmentReaders = new FragmentReader[fragFiles.length];
    }
    
    
    void calculateMinGoodFragsRequired() {
        minGoodFragsRequired = 
            fragFiles.length-reliability.getRedundantFragCount();
        if(recovery) {
            minGoodFragsRequired = 1;
        }
    }
    
    public static FragmentFile instantiateFragmentFile() throws OAException {
        try {
            return (FragmentFile)Class.forName(frgClassName).newInstance();

        } catch (ClassNotFoundException cnfe) {
            LOG.severe(cnfe.getMessage());
            throw new OAException("Can't find FragmentFile class: " + cnfe);

        } catch (InstantiationException ie) {
            LOG.severe(ie.getMessage());
            throw new OAException("Cannot instantiate found FragmentFile class: " + ie);

        } catch (IllegalAccessException iae) {
            LOG.severe(iae.getMessage());
            throw new OAException("Illegal access instantiating FragmentFile class: " + iae);
        }
    }
    
    // PRIVATE MEMBERS //
    
    protected static final Logger LOG = 
        Logger.getLogger(FragmentFileSet.class.getName());

    protected static final String frgClassName = 
        System.getProperty(FragmentFile.FRAGFILE_PROP, "com.sun.honeycomb.oa.FragmentFile");

    private FragmentFile[] fragFiles = null;
    private Thread[] threads = null;
    private FragmentReader[] fragmentReaders = null;
    private NewObjectIdentifier oid = null;
    private ObjectReliability reliability = null;
    private int errors = -1;
    private int recoverFrag = -1;
    private boolean recovery = false;
    private int minGoodFragsRequired = -1;
    private Layout layout = null;    

    private static CreatorThreads creatorThreads = new CreatorThreads();
    private static WriteAndCloseThreads closeThreads = new WriteAndCloseThreads();
    private static final RenamerThreads renamerThreads = new RenamerThreads();
    private static final DeleteThreads deleteThreads = new DeleteThreads();
}
