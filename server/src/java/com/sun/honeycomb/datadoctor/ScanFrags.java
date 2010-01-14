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

import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.oa.OAClient;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.oa.OAException;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.FragmentNotFoundException;
import com.sun.honeycomb.oa.DeletedFragmentException;
import com.sun.honeycomb.coordinator.Context;
import com.sun.honeycomb.common.ObjectCorruptedException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.SystemMetadata;
import com.sun.honeycomb.resources.ByteBufferPool;

import java.util.ArrayList;
import java.nio.ByteBuffer;

import java.util.logging.Level;

/** 
 * Checks if fragments on this disk have correct data, according to the
 * checksums.  This is where we should detect and correct any bitrot.
 */
public class ScanFrags implements Steppable {

  private String taskName;
  private DiskMask diskMask = null;
  private DiskId myDiskId;
  private boolean abortStep = false;
  private TaskLogger log;

  /**********************************************************************
   * constructor is no-args, real setup work done in init method
   **/
  public ScanFrags() {
  }

  /**********************************************************************
   * @inherit javadoc
   **/
  public void init(String taskName, DiskId diskId) {

    this.taskName = taskName;
    this.myDiskId = diskId;
    this.log = new TaskLogger(ScanFrags.class.getName(), taskName);
  }

  /**********************************************************************
   * @inherit javadoc
   **/
  public String getName() { return taskName; }

  /**********************************************************************
   * @inherit javadoc
   **/
  public int getNumSteps() { return Steppable.NUM_STEPS; }

  /**********************************************************************
   * @inherit javadoc
   **/
  public void abortStep() { abortStep = true; }

  /**********************************************************************
   * @inherit javadoc
   **/
  public int getErrorCount() { return 0; }

  /**********************************************************************
   * @inherit javadoc
   **/
  public void resetErrorCount() { return; }

  /**********************************************************************
   * @inherit javadoc
   *
   * synchronized to prevent mask change in middle of step
   **/
  synchronized public void newDiskMask(DiskMask newMask) {
    diskMask = newMask;
  }

  /**********************************************************************
   * @inherit javadoc
   **/
  synchronized public void step(int stepNum) {

    if (diskMask == null) {
      log.severe("DiskMask is null, aborting step "+stepNum);
      return;
    }

    int mapId = stepNum;
    String[] fragList = TaskFragUtils.readMap(myDiskId, mapId);
    if (fragList == null) {
        log.severe("failed to read map " + mapId + " on disk " + myDiskId);
        return;
    }

    // walk frags in this directory, checking frag number
    for (int i=0; i < fragList.length; i++) {

      // check if we're supposed to stop
      if (abortStep) {
        log.info("aborting step "+stepNum);
        abortStep = false;
        return;
      }

      String f = fragList[i];
      NewObjectIdentifier oid = null;
      int fragId = -1;
      try {
          fragId = TaskFragUtils.extractFragId(f); 
          oid = TaskFragUtils.fileNameToOid(f);
      } catch (NotFragmentFileException e) {
          log.warning("Skipping file: " + f + ": " + e.getMessage());
          continue;
      }

      FragmentFile frag = new FragmentFile(oid, fragId, DiskProxy.getDisk(myDiskId));

      // open the fragment file and check its integrity
      try {
          scanFragment(frag);
      } catch (ObjectCorruptedException oce) {

        // footer is corrupted, should handle this case by
        // reconstructing the footer from redundant fragments in
        // FragmentScanner.scanFragment()
        log.severe("cannot repair corrupted fragment " + f +
                   ": "+oce.getMessage());
                
      }
    } 
  }
  
  /**
   * Method to scan a fragment file for errors. This opens the fragment and
   * ensures that its footer is correct. After that it scans the fragment
   * from beginning till end and ensures that all the data blocks are
   * consistent by verifying them with their embedded checksums. Any bad
   * region is recovered from the redundant fragments and rewritten with
   * the correct checksums.
   *
   * NOTE:
   * In the current implementation, if the fragment's footer is corrupted,
   * the fragment is deleted (see below for issues).
   *
   * @param fragmentFile the fragment file to check and repair
   * @throws IllegalArgumentException if the fragment file is invalid
   * @throws ObjectCorruptedException if the fragment cannot be repaired
   */
  private void scanFragment(FragmentFile fragment)
      throws ObjectCorruptedException 
  {
      try {
            fragment.open();

      } catch (FragmentNotFoundException fnfe) {
          log.warning("Fragment file not found: " + fnfe);
          fragment.dispose();
          // For now delete the fragment file. This will be reconstructed
          // by the recovery DLM.
          fragment.remove();
          return;

      } catch (DeletedFragmentException dfe) {
          if (log.isLoggable(Level.FINE))
              log.fine("Fragment file " + fragment + " has been deleted: " + dfe);
          fragment.dispose();
          return;

      } catch (ObjectCorruptedException oce) {
          log.warning("Footer of fragment " + fragment +
                         " is corrupted. Deleting fragment...");
          fragment.dispose();
          // For now delete the fragment file. This will be reconstructed
          // by the recovery DLM.
          fragment.remove();
          return;
              
          // TODO:
          // Repair the fragment footer from other fragments
          //
          // ISSUE: SystemMetadata for an object has most of the
          //        information to construct the fragment footer except:
          //        - Metadata field
          //        - Number of preceeding checksums before the footer
          //        The metadata field can be obtained but the number of
          //        preceeding checksums might not be obvious as it is
          //        different for fragment0 and other fragments.
          //
          // UNCOMMENT THIS ONCE OA::getFooter() is implemented
          // repairFragmentFooter(fragment, fragmentFile);
          
      } catch (OAException oae) {
          log.warning("Error opening fragment file " + fragment + oae);
          fragment.dispose();
          return;
      }
      
      // Find out the actual data bytes in the fragment. This is the region
      // that will be scanned.
      long dataSize = fragment.getDataSize();
      //log.info("Fragment contains " + dataSize + " bytes of data");
      
      int fragmentSize = fragment.getFragmentSize();
      
      ByteBufferPool pool = ByteBufferPool.getInstance();
      ByteBuffer readBuffer = pool.checkOutBuffer(fragmentSize);
      readBuffer.clear();
      
      // Scan the fragment
      long totalBytesRead = 0;
      long bytesRead = 0;
      long toRead = fragmentSize;
      long timeStart = System.currentTimeMillis();
      while (totalBytesRead < dataSize) {
          if ((dataSize - totalBytesRead) < fragmentSize) {
              toRead = dataSize - totalBytesRead;
              readBuffer.limit((int)toRead);
          }
          
          try {
              bytesRead = fragment.read(readBuffer, totalBytesRead);
          } catch (ObjectCorruptedException oce) {
              log.warning("Object region is corrupted at [" +
                             totalBytesRead + "]. Exception = " + oce);
              
              // NEW: just delete any fragment corrupted anywhere
              // the same thing we do when the footer is corrupted
              // We do this because in place fix is broken for Botros-Arnoud
              // see 6380408
              log.warning("Currently, inline repair is disabled." +
                             "Deleteing fragment file. Recovery will repair it shortly.");              
              fragment.dispose();
              pool.checkInBuffer(readBuffer);
              fragment.remove();
              return;
              
              // OLD: Try to replair corrupted block in place
              // Nice, but broken for Botros-Arnoud (see bug 6380408).
              //repairFragment(fragment, totalBytesRead, toRead);
              //bytesRead = toRead;
          } catch (OAException ie) {
              log.warning("Fragment file cannot be read. Aborting scan");
              break;
          }
          
          totalBytesRead += bytesRead;
          readBuffer.clear();
      }
      long timeEnd = System.currentTimeMillis();
      long timeInterval = timeEnd - timeStart;
      if (log.isLoggable(Level.FINE))
          log.fine("Scanned Fragment file " + fragment + " in " + timeInterval +
               " msec " +  totalBytesRead + " bytes of data");
      
      fragment.dispose();
      pool.checkInBuffer(readBuffer);
  }
  
  /**
   * Method to repair the fragment footer.
   * TODO - this method is not fully implemented and never called.
   *
   * @param fragment the fragment object
   */
  private void repairFragmentFooter(FragmentFile fragment) {

      NewObjectIdentifier oid = fragment.getOID();
      int fragmentNumber = fragment.getFragNum();
      
      OAClient oaClient = OAClient.getInstance();
      
      //
      // log.info("Repairing footer for oid [" + oid + "] fragment [" +
      //            fragmentNumber + "]");
      //
      
      Context readContext = new Context();
      try {
          oaClient.open(oid, readContext);
      } catch (ArchiveException e) {
          log.warning("Fragment cannot be opened: " + e);
          readContext.dispose();
          return;
      }
      
      // Get the fragment footer, set the correct fragment number and
      // rewrite it in the fragment.
      FragmentFooter fragmentFooter = null; //oaClient.getFooter(readContext)
      fragmentFooter.fragNum = fragmentNumber;
      /* repairFooter not implemented - API wrong
          fragment.repairFooter(fragmentFile, fragmentFooter);
      */
      readContext.dispose();
  }
  
  /**
   * Method to repair a section of a fragment. This method remaps the bad
   * section to a block offset in the complete object (defragmented) and
   * reads it. It then gets the slice for the particular fragment to repair
   * and overwrites the corrupted region.
   *
   * @param fragment the fragment to repair
   * @param offset the start offset of the section to repair
   * @param length the length of the section to repair
   */
  private void repairFragment(FragmentFile fragment,
                              long fragmentOffset,
                              long length) 
  {

      OAClient oaClient = OAClient.getInstance();
      
      // Open the fragment and read the required data from the object
      SystemMetadata sm = fragment.getSystemMetadata();
      
      Context readContext = new Context();
      try {
          oaClient.open(sm.getOID(), readContext);
      } catch (ArchiveException e) {
          log.warning("Fragment cannot be opened: " + e);
          readContext.dispose();
          return;
      }
      
      //
      // Read region from the object
      //
      long objectBlockOffset =
          fragment.mapFragmentOffsetToObjectBlockOffset(fragmentOffset);
      
      // Get the read size
      int readSize = oaClient.getReadBufferSize(readContext);
      
      // If this is near the end of the last chunk, the remaining bytes
      // might be less.
      if (sm.getSize() != OAClient.MORE_CHUNKS) {
          int bytesRemaining = (int) (sm.getSize() - objectBlockOffset);
          if (bytesRemaining < readSize) {
              readSize = oaClient.getLastReadBufferSize(bytesRemaining);
          }
      }
      
      int fragNum = fragment.getFragNum();
      log.info("Repairing oid [" + sm.getOID() + "] fragment [" +
                  fragNum + "] fragmentOffset [" +
                  fragmentOffset + "] object offset [" + objectBlockOffset +
                  "] length [" + length + "] read size [" + readSize + "]");
      
      
      ByteBuffer buffer =
          ByteBufferPool.getInstance().checkOutBuffer(readSize);
      
      long bytesRead = 0;
      try {
          oaClient.read(buffer, objectBlockOffset, readSize, readContext);
      } catch (Exception e) {
          log.warning("Failed to read [" + readSize + "] bytes from " +
                         "offset [" + objectBlockOffset + "] in ["
                         + sm.getOID() + "]. Cannot repair fragment. " +
                         "Exception: " + e);
          ByteBufferPool.getInstance().checkInBuffer(buffer);
          readContext.dispose();
          return;
      }
      
      // Clean up
      ByteBufferPool.getInstance().checkInBuffer(buffer);
      readContext.dispose();
  }
  
}
