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

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;

public class BshOpCode {
    
    public static void STORE(int i)
        throws ArchiveException {
        STORE(Integer.toString(i), Constants.DEFAULT_STORE_SIZE, true);
    }

    public static void STORE(String oid) 
        throws ArchiveException {
        OpCode.createStore(oid, Constants.DEFAULT_STORE_SIZE, true).execute();
    }

    public static void STORE(int i, int size)
        throws ArchiveException {
        STORE(Integer.toString(i), size);
    }

    public static void STORE(String oid, int size) 
        throws ArchiveException {
        OpCode.createStore(oid, size, true).execute();
    }
    
     public static void STORE(int oid, int size, boolean shouldSucceed) 
	 throws ArchiveException {
	 OpCode.createStore(Integer.toString(oid), size, shouldSucceed).execute();
    }

    public static void STORE(String oid, int size, boolean shouldSucceed) 
        throws ArchiveException {
        OpCode.createStore(oid, size, shouldSucceed).execute();
    }


    public static void ADDM(int link,
                            int oid)
        throws ArchiveException {
        ADDM(Integer.toString(link), Integer.toString(oid));
    }

    public static void ADDM(int link,
                            int oid, 
                            boolean shouldSucceed)
        throws ArchiveException {
        ADDM(Integer.toString(link), Integer.toString(oid), shouldSucceed);
    }
    
    public static void ADDM(String link,
                            String oid)
        throws ArchiveException {
        ADDM(link,oid,true);
    }
    
    public static void ADDM(String link,
                            String oid, 
                            boolean shouldSucceed)
                    throws ArchiveException {
        OpCode.createAddMD(link, oid, shouldSucceed).execute();
    }

    public static void DELETE(int i,
                              boolean shouldSucceed)
        throws ArchiveException {
        DELETE(Integer.toString(i), shouldSucceed);
    }

    public static void DELETE(String oid,
                              boolean shouldSucceed)
        throws ArchiveException {
        OpCode.createDelete(oid, shouldSucceed).execute();
    }
    
    public static void RECOVER(int oid,
			       int fragID,
			       int chunkID)
	throws ArchiveException {
	RECOVER(Integer.toString(oid), fragID, chunkID);
    }
    
    public static void RECOVER(String oid,
			       int fragID,
			       int chunkID)
	throws ArchiveException {
	OpCode.createRecover(oid, fragID, chunkID).execute();
    }
    
    public static void RECOVER(int oid,
			       int fragID)
	throws ArchiveException {
	RECOVER(Integer.toString(oid), fragID, 0);
    }
    
    public static void RECOVER(String oid,
			       int fragID)
	throws ArchiveException {
	OpCode.createRecover(oid, fragID, 0).execute();
    }

    public static void REMOVETMP(int oid, int fragID)
        throws ArchiveException {
        OpCode.createRemoveTmp(Integer.toString(oid), fragID).execute();
    }
    
    public static void REMOVEM(int i) throws ArchiveException {
    	REMOVEM(Integer.toString(i));
    }

    public static void REMOVEM(String oid) throws ArchiveException {
    	OpCode.createSystemCacheDelete(oid).execute();
    }
    
    public static void EXECREMOVETMP(int removalTime)
        throws ArchiveException {
        OpCode.createRemoveTmpTask(removalTime).execute();
    }
    
    public static void EXISTSTMP(int oid, int fragID)
        throws ArchiveException {
        OpCode.createExistsTmp(Integer.toString(oid), fragID).execute();
    }
    
    public static void VERIFYTMPS(int howMany)
        throws ArchiveException {
        OpCode.createVerifyTmps(howMany).execute();
    }
    
    public static void EXISTSDATA(int oid, int fragID)
        throws ArchiveException {
        OpCode.createExistsTmp(Integer.toString(oid), fragID).execute();
    }
    
    public static void NOTEXISTSTMP(int oid, int fragID)
        throws ArchiveException {
        OpCode.createNotExistsTmp(Integer.toString(oid), fragID).execute();
    }
    
    public static void RETRIEVE(int oid,
                                boolean shouldSucceed) 
        throws ArchiveException {
        RETRIEVE(Integer.toString(oid), shouldSucceed);
    }

    public static void RETRIEVE(String oid,
                                boolean shouldSucceed)
        throws ArchiveException {
        RETRIEVE(oid, 0, Long.MAX_VALUE, shouldSucceed);
    }
    
    public static void RETRIEVEM(int oid,
                                boolean shouldSucceed)
        throws ArchiveException {
        RETRIEVEM(Integer.toString(oid), shouldSucceed);
    }
    
    public static void RETRIEVEM(String oid,
                                boolean shouldSucceed)
        throws ArchiveException {
        OpCode.createRetrieve(oid, 0, Long.MAX_VALUE, shouldSucceed).execute();
    }
    
    public static void RETRIEVE(String oid,
				long offset,
				long length,
				boolean shouldSucceed)
        throws ArchiveException {
        OpCode.createRetrieve(oid, offset, length, shouldSucceed).execute();
    }

    public static void RETRIEVE(int oid,
				long offset,
				long length,
				boolean shouldSucceed)
        throws ArchiveException {
        RETRIEVE(Integer.toString(oid), offset, length, shouldSucceed);
    }
    
    public static void DEREF(int mdOid,
                             int dataOid)
        throws ArchiveException {
        DEREF(Integer.toString(mdOid), Integer.toString(dataOid));
    }

    public static void DEREF(String mdOid,
                             String dataOid)
        throws ArchiveException {
        OpCode.createDeref(mdOid, dataOid).execute();
    }

    public static void FRAGREMOVE(String oid,
                                  int fragId,
				  int chunkId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_REMOVE, 
                                oid, 
                                fragId,
                                chunkId,
                                Constants.SIZE_UNKNOWN).execute();
    }

    /* FEF operations are for compliance legal hold footer extensions */

    public static void FEFRECOVER(int oid)
	throws ArchiveException {
	FEFRECOVER(Integer.toString(oid));
    }
    
    public static void FEFRECOVER(String oid)
	throws ArchiveException {
	OpCode.createFefRecover(oid).execute();
    }

    public static void FEFREMOVE(String oid,
                                 int fefId,
                                 int chunkId)
        throws ArchiveException {
        OpCode.createFefRemove(oid, 
                               fefId,
                               chunkId).execute();
    }

    public static void FRAGREMOVE(int oid,
                                  int fragId,
				  int chunkId)
        throws ArchiveException {
	FRAGREMOVE(Integer.toString(oid),
		   fragId,
		   chunkId);
    }
    
    public static void FEFREMOVE(int oid,
                                 int fefId,
                                 int chunkId)
        throws ArchiveException {
	       FEFREMOVE(Integer.toString(oid),
		   fefId,
		   chunkId);
    }
    
    public static void FRAGREMOVE(String oid,
                                  int fragId)
	throws ArchiveException{
	FRAGREMOVE(oid,
		   fragId,
		   0);
    }

    public static void FEFREMOVE(String oid,
                                 int fefId)
	throws ArchiveException{
	       FEFREMOVE(oid,
		   fefId,
		   0);
    }

    public static void FRAGREMOVE(int oid,
                                  int fragId)
	throws ArchiveException{
	FRAGREMOVE(Integer.toString(oid),
		   fragId);
    }
   
    public static void FRAGMOVE(int oid,int fragID) throws ArchiveException { 
        FRAGMOVE(""+oid,fragID,0);
    }
    
    public static void FRAGMOVE(int oid,  
                int fragId,
                int chunkId) 
    throws ArchiveException {
        FRAGMOVE(""+oid, fragId,chunkId);
    }
    public static void FRAGMOVE(String oid,  
                                int fragId,
                                int chunkId) 
                  throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_MOVE, 
                oid, 
                fragId,
                chunkId,
                Constants.SIZE_UNKNOWN).execute();
    }

    public static void FEFREMOVE(int oid,
                                 int fefId)
	throws ArchiveException{
	       FEFREMOVE(Integer.toString(oid),
		   fefId);
    }

    public static void FRAGRESTORE(String oid,
                                   int fragId,
				   int chunkId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_RESTORE,
                                oid, 
                                fragId,
                                chunkId,
                                Constants.SIZE_UNKNOWN).execute();
    }

    public static void FEFRESTORE(String oid,
                                  int fefId,
                                  int chunkId)
        throws ArchiveException {
        OpCode.createFefRestore(oid, 
                                fefId,
                                chunkId).execute();
    }

    public static void FRAGRESTORE(int oid,
                                   int fragId,
				   int chunkId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_RESTORE,
                                Integer.toString(oid),
                                fragId,
                                chunkId,
                                Constants.SIZE_UNKNOWN).execute();
    }

    public static void FEFRESTORE(int oid,
                                  int fefId,
                                  int chunkId)
        throws ArchiveException {
        OpCode.createFefRestore(Integer.toString(oid),
                                fefId,
                                chunkId).execute();
    }

    public static void FEFRESTORE(int oid,
                                  int fefId)
        throws ArchiveException {
        FEFRESTORE(oid, fefId, 0);
    }

    public static void FRAGINTERNALSIZE(String oid,
                                   long expectedSize,
                                   int fragId,
                                   int chunkId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_INTERNALSIZE,
                                oid, 
                                fragId,
                                chunkId,
                                expectedSize).execute();
    }

    public static void FRAGINTERNALSIZE(int oid,
                                   long expectedSize,
                                   int fragId,
                                   int chunkId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_INTERNALSIZE,
                                Integer.toString(oid),
                                fragId,
                                chunkId,
                                expectedSize).execute();
    }

    public static void FRAGFILESIZE(String oid,
                                   long expectedSize,
                                   int fragId,
                                   int chunkId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_FILESIZE,
                                oid, 
                                fragId,
                                chunkId,
                                expectedSize).execute();
    }

    public static void FRAGFILESIZE(int oid,
                                   long expectedSize,
                                   int fragId,
                                   int chunkId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_FILESIZE,
                                Integer.toString(oid),
                                fragId,
                                chunkId,
                                expectedSize).execute();
    }

    public static void FRAGNOTZERO(String oid,
                                   int fragId,
                                   int chunkId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_NOTZERO,
                                oid, 
                                fragId,
                                chunkId,
                                Constants.SIZE_UNKNOWN).execute();
    }

    public static void FRAGNOTZERO(int oid,
                                   int fragId,
                                   int chunkId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_NOTZERO,
                                Integer.toString(oid),
                                fragId,
                                chunkId,
                                Constants.SIZE_UNKNOWN).execute();
    }

    public static void FRAGABSENT(String oid,
                                  int fragId,
                                  int chunkId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_ABSENT,
                                oid, 
                                fragId,
                                chunkId,
                                Constants.SIZE_UNKNOWN).execute();
    }

    public static void FRAGABSENT(int oid,
                                  int fragId,
                                  int chunkId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_ABSENT,
                                Integer.toString(oid),
                                fragId,
                                chunkId,
                                Constants.SIZE_UNKNOWN).execute();
    }

    public static void FRAGRESTORE(String oid,
                                   int fragId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_RESTORE,
                                oid, 
                                fragId,
                                0,
                                Constants.SIZE_UNKNOWN).execute();
    }

    public static void FRAGRESTORE(int oid,
                                   int fragId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_RESTORE,
                                Integer.toString(oid),
                                fragId,
                                0,
                                Constants.SIZE_UNKNOWN).execute();
    }

    public static void FRAGINTERNALSIZE(String oid, long expectedSize,
                                   int fragId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_INTERNALSIZE,
                                oid, 
                                fragId,
                                0,
                                expectedSize).execute();
    }

    public static void FRAGINTERNALSIZE(int oid, long expectedSize,
                                   int fragId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_INTERNALSIZE,
                                Integer.toString(oid),
                                fragId,
                                0,
                                expectedSize).execute();
    }

    public static void FRAGFILESIZE(String oid, long expectedSize,
                                   int fragId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_FILESIZE,
                                oid, 
                                fragId,
                                0,
                                expectedSize).execute();
    }

    public static void FRAGFILESIZE(int oid, long expectedSize,
                                   int fragId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_FILESIZE,
                                Integer.toString(oid),
                                fragId,
                                0,
                                expectedSize).execute();
    }

    public static void FRAGNOTZERO(String oid,
                                   int fragId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_NOTZERO,
                                oid, 
                                fragId,
                                0,
                                Constants.SIZE_UNKNOWN).execute();
    }

    public static void FRAGNOTZERO(int oid,
                                   int fragId)
        throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_NOTZERO,
                                Integer.toString(oid),
                                fragId,
                                0,
                                Constants.SIZE_UNKNOWN).execute();
    }

    public static void FRAGISDELETED(int oid,
                                     int fragId,
                                     boolean shouldBeDeleted)
        throws ArchiveException {
        FRAGISDELETED(Integer.toString(oid), fragId, shouldBeDeleted);
    }

    public static void FRAGISDELETED(String oid,
                                     int fragId,
                                     boolean shouldBeDeleted)
        throws ArchiveException {
        OpCode.createFragIsDeleted(oid, fragId, shouldBeDeleted).execute();
    }

    public static void REFCNTCHECK(int oid,
                                   int fragId,
                                   int refcnt, 
                                   int maxRefcnt)
        throws ArchiveException {
        REFCNTCHECK(Integer.toString(oid), fragId, refcnt, maxRefcnt);
    }

    public static void REFCNTCHECK(String oid,
                                   int fragId,
                                   int refcnt,
                                   int maxRefcnt)
        throws ArchiveException {
        OpCode.createRefcntCheck(oid, fragId, refcnt, maxRefcnt).execute();
    }

    public static void CORRUPTFRAG(int oid,
				   int fragId,
				   int chunkId,
				   long numBadBytes) throws ArchiveException {
	CORRUPTFRAG(Integer.toString(oid), fragId, chunkId, numBadBytes);
    }

    public static void CORRUPTFRAG(String oid,
				   int fragId,
				   int chunkId,
				   long numBadBytes) throws ArchiveException {
	OpCode.createFragAction(Constants.FRAGACTION_CORRUPT,
                                oid, 
                                fragId,
                                chunkId,
                                numBadBytes).execute();
    }
    
    /**********************************************************************
     * Truncate to final length equal to numBytes.
     **/
    public static void TRUNCATEFRAG(int oid,
                                    int fragId,
                                    int chunkId,
                                    long numBytes) throws ArchiveException {
        TRUNCATEFRAG(Integer.toString(oid), fragId, chunkId, numBytes);
    }

    public static void TRUNCATEFRAG(String oid,
                                    int fragId,
                                    int chunkId,
                                    long numBytes) throws ArchiveException {
        OpCode.createFragAction(Constants.FRAGACTION_TRUNCATE,
                                oid, 
                                fragId,
                                chunkId,
                                numBytes).execute();
    }
    
    /**********************************************************************
     **/
    public static void SCANFRAG(int oid,
				int fragId,
				int chunkId) throws ArchiveException {
	SCANFRAG(Integer.toString(oid), fragId, chunkId);
    }
    
    public static void SCANFRAG(String oid,
				   int fragId,
				   int chunkId) throws ArchiveException {
	OpCode.createFragAction(Constants.FRAGACTION_SCAN,
                                oid, 
                                fragId,
                                chunkId,
				0).execute();
    }

    public static void LS(int oid) throws ArchiveException {
        LS(Integer.toString(oid));
    }

    public static void LS(String oid) throws ArchiveException {
        OpCode.createLs(oid).execute();
    }
    
    public static void ECHO(String s)
        throws ArchiveException {
        OpCode.createEcho(s).execute();
    }
    
    public static void DISABLEDISK(int disk)
        throws ArchiveException {
        OpCode.createDisableDisk(disk).execute();
    }
    
    public static void ENABLEDISK(int disk)
        throws ArchiveException {
        OpCode.createEnableDisk(disk).execute();
    }
    
    /**
     *  System Metadata Cache functions
     */
    public static void DUMPCACHES() throws ArchiveException {
        OpCode.createDumpCaches().execute();
    }
    
    public static void WIPECACHES() throws ArchiveException {
        OpCode.createWipeCaches().execute();
    }
    
    public static SystemMetadata GETCACHEDATA(int i)
    	throws ArchiveException {
    	return GETCACHEDATA(Integer.toString(i));
    }
    
    public static SystemMetadata GETCACHEDATA(String oid)
    	throws ArchiveException {
    	return (SystemMetadata) OpCode.createGetCacheData(oid).execute();
    }
    
    /**
     * full backup of the current UT environment to the filename specified.
     * 
     * @param filename location of the file where we should place the backup stream.
     * @param options flags defined in the BackupRestore class that define what parts of 
     *                the Honeycomb environment to backup.
     * @throws ArchiveException 
     */
    public static void BACKUP(String filename, int options) throws ArchiveException {
       OpCode.createBackup(filename,options).execute();
    }
    
    /**
     * backup from t1 to t2 to the filename specified
     * 
     * @param filename location of the file where we should place the backup stream.
     * @param t1 time to begin the backup
     * @param t2 time to end the backup
     * @param options flags defined in the BackupRestore class that define what parts of 
     *                the Honeycomb environment to backup.
     * @throws ArchiveException 
     */
    public static void BACKUPINTERVAL(
    		String filename, long t1, long t2, int options) throws ArchiveException {
       OpCode.createBackupInterval(filename, t1, t2, options).execute();
    }
   
    /**
     * incremental backup of the current UT environment to the filename specified, done 
     * from the last BACKUP or INCBACKUP was called.
     * 
     * @param filename location of the file where we should place the backup stream.
     * @param options flags defined in the BackupRestore class that define what parts of 
     *                the Honeycomb environment to backup.
     * @throws ArchiveException 
     */
    public static void INCBACKUP(String filename, int options) throws ArchiveException {
       OpCode.createIncBackup(filename,options).execute();
    }
    
    /**
     * restore the current UT environment from the filename specified.
     *  
     * @param filename location of the file that contains the backup stream previously 
     *                 created with the BACKUP command.
     * @param options flags defined in the BackupRestore class that define what parts of 
     *                the Honeycomb environment to restore.
     * @throws ArchiveException 
     */
    public static void  RESTORE (String filename, int options) throws ArchiveException {
        OpCode.createRestore(filename, options).execute();
    }
  
    /**
     *  move all of the current UT environment into a safe keeping area
     * @throws ArchiveException 
     *
     */
    public static void MOVEALL() throws ArchiveException {
       OpCode.createMoveAll().execute(); 
    }
 
    /**
     * compare each fragment in the current UT environment to the fragments in
     * the safe keeping area.
     *
     * @throws ArchiveException
     */
    public static void COMPARE() throws ArchiveException {
        OpCode.createCompare().execute();
    }

    /** 
     * retrieve the size of the current system cache
     * 
     * @return
     * @throws ArchiveException
     */
    public static Integer SYSCACHESIZE() throws ArchiveException {
       return (Integer)OpCode.createSysCacheSize().execute(); 
    }
   
    /**
     *  restore from previous COPYALL, MOVEALL command, in this we delete whatever is the 
     *  current UT environment and replace with the safe image.
     * @throws ArchiveException 
     *
     */
    public static void RESTOREALL() throws ArchiveException {
       OpCode.createRestoreAll().execute(); 
    }

    /* **** Compliance ********************************************* */

    public static void SETRETENTION (int oid,
                                     long timestamp) throws ArchiveException {
        SETRETENTION (Integer.toString(oid), timestamp);
    }

    public static void SETRETENTION (String oid,
                                     long timestamp) throws ArchiveException {
        OpCode.createSetRetention (oid, timestamp).execute();
    }

    public static Long GETRETENTION (int oid) throws ArchiveException {
        return ((Long) GETRETENTION (Integer.toString (oid)));
    }

    public static Long GETRETENTION (String oid) throws ArchiveException {
        return ((Long) OpCode.createGetRetention (oid).execute());
    }

    public static void ADDHOLD (int oid,
                                String hold_tag) throws ArchiveException {
        ADDHOLD (Integer.toString (oid), hold_tag);
    }

    public static void ADDHOLD (String oid,
                                String hold_tag) throws ArchiveException {
        OpCode.createAddHold (oid, hold_tag).execute();
    }

    public static void RMHOLD (int oid,
                               String hold_tag) throws ArchiveException {
        RMHOLD (Integer.toString (oid), hold_tag);
    }

    public static void RMHOLD (String oid,
                               String hold_tag) throws ArchiveException {
        OpCode.createRmHold (oid, hold_tag).execute();
    }

    public static String[] GETHOLDS (int oid) throws ArchiveException {
        return GETHOLDS (Integer.toString(oid));
    }

    public static String[] GETHOLDS (String oid) throws ArchiveException {
        return ((String[])OpCode.createGetHolds (oid).execute());
    }

    public static NewObjectIdentifier[] GETHELD (String hold_tag) 
                                            throws ArchiveException {
        return ((NewObjectIdentifier[])
            OpCode.createGetHeld (hold_tag).execute());
    }
}
