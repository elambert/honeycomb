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

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.SystemMetadata;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.logging.Logger;
import java.io.IOException;
import java.util.logging.Level;
import com.sun.honeycomb.layout.LayoutClient;

public abstract class OpCode {

    private static final Logger LOG = Logger.getLogger(OpCode.class.getName());
    private static final int CODE_STORE         = 0;
    private static final int CODE_ADDMD         = 1;
    private static final int CODE_DELETE        = 2;
    private static final int CODE_LS            = 3;
    private static final int CODE_RETRIEVE      = 4;
    private static final int CODE_DEREF         = 5;
    private static final int CODE_FRAGACTION    = 6;
    private static final int CODE_FRAGISDELETED = 7;
    private static final int CODE_REFCNTCHECK   = 8;
    private static final int CODE_ECHO          = 9;
    private static final int CODE_RECOVER       = 10;
    private static final int CODE_REMOVETMP     = 12;
    private static final int CODE_EXISTSTMP     = 13;
    private static final int CODE_NOTEXISTSTMP  = 14;
    private static final int CODE_EXISTSDATA    = 15;
    private static final int CODE_REMOVETMPTASK = 16;
    private static final int CODE_VERIFYTMPS    = 17;
    private static final int CODE_DISABLEDISK   = 18;
    private static final int CODE_ENABLEDISK    = 19;
    
    // compliance
    private static final int CODE_SETRETENTION  = 20;
    private static final int CODE_GETRETENTION  = 21;
    private static final int CODE_ADDHOLD       = 22;
    private static final int CODE_RMHOLD        = 23;
    private static final int CODE_GETHOLDS      = 24;
    private static final int CODE_GETHELD       = 25;
    private static final int CODE_FEFREMOVE     = 26;
    private static final int CODE_FEFRESTORE    = 27;
    private static final int CODE_FEFRECOVER    = 28;   

    // bulk oa
    private static final int CODE_DUMPCACHES    = 29;
    private static final int CODE_WIPECACHES    = 30;
    private static final int CODE_BACKUP        = 31;
    private static final int CODE_INCBACKUP     = 32;
    private static final int CODE_RESTORE       = 33;
    private static final int CODE_MOVEALL       = 34;
    private static final int CODE_RESTOREALL    = 35;
    private static final int CODE_SYSCACHESIZE  = 36;
    private static final int CODE_COMPARE       = 37;
    
    private static final int CODE_RETRIEVEMD    = 38;
    private static final int CODE_REMOVEM		= 39;
    private static final int CODE_GETCACHEDATA  = 40;
    private static final int CODE_BACKUPINTERVAL= 41;
    
    // Keep strings in the same order as numbers above!
    private static final String[] codeStrings = {
        "STORE", "ADDM", "DELETE", "LS", "RETRIEVE",
        "DEREF", "FRAGACTION", "FRAGISDELETED",
        "REFCNTCHECK", "ECHO", "RECOVER", "LS", "REMOVETEMP", 
        "EXISTSTMP", "NOTEXISTSTMP", "EXISTSDATA", "REMOVETMPTASK",
        "VERIFYTMPS", "DISABLEDISK", "ENABLEDISK",
        "SETRETENTION", "GETRETENTION", 
        "ADDHOLD", "RMHOLD", "GETHOLDS", "GETHELD", "FEFREMOVE",
        "FEFRESTORE", "FEFRECOVER", "DUMPCACHES", "WIPECACHES",
        "BACKUP","INCBACKUP","RESTORE","MOVEALL","RESTOREALL","SYSCACHESIZE",
        "COMPARE", "RETRIEVEM", "REMOVEM", "GETCACHEDATA", "BACKUPINTERVAL"
    };

    private static OpCodeFactory factory = null;
    private static OpCodeOutput output = null;
    private static int nbOps = 0;
    private static long startTime = 0;

    private int opCode;
    private Object[] args;

    public static void init(OpCodeFactory nFactory,
                            OpCodeOutput nOutput) {
        factory = nFactory;
        output = nOutput;
        nbOps = 0;
        startTime = 0;
    }

    public static OpCode createStore(String oidTag, int size, boolean shouldSucceed) {
        Object[] args = new Object[3];
        args[0] = oidTag;
        args[1] = new Integer(size);
	args[2] = new Boolean(shouldSucceed);
        return(factory.allocateOpCode(CODE_STORE, args));
    }

    public static OpCode createAddMD(String linkTag,
                                     String oidTag,
                                     boolean shouldSucceed) {
        Object[] args = new Object[3];
        args[0] = linkTag;
        args[1] = oidTag;
        args[2] = new Boolean(shouldSucceed);
        return(factory.allocateOpCode(CODE_ADDMD, args));
    }
    
    public static OpCode createDelete(String oid,
                                      boolean shouldSucceed) {
        Object[] args = new Object[2];
        args[0] = oid;
        args[1] = new Boolean(shouldSucceed);
        return(factory.allocateOpCode(CODE_DELETE, args));
    }
    
    public static OpCode createSystemCacheDelete(String oid) {
    	Object[] args = new Object[1];
    	args[0] = oid;
    	return(factory.allocateOpCode(CODE_REMOVEM, args));
    }

    public static OpCode createRecover(String oidTag,
				       int fragID,
				       int chunkID) {
        Object[] args = new Object[3];
        args[0] = oidTag;
	args[1] = new Integer(fragID);
	args[2] = new Integer(chunkID);
        return(factory.allocateOpCode(CODE_RECOVER, args));
    }

    public static OpCode createRetrieve(String oid,
					long offset,
					long length,
                                        boolean shouldSucceed) {
        Object[] args = new Object[4];
        args[0] = oid;
	args[1] = new Long(offset);
	args[2] = new Long(length);
        args[3] = new Boolean(shouldSucceed);
        return(factory.allocateOpCode(CODE_RETRIEVE, args));
    }

    public static OpCode createRetrieve(String oid,
                                        boolean shouldSucceed) {
        Object[] args = new Object[2];
        args[0] = oid;
        args[3] = new Boolean(shouldSucceed);
        return(factory.allocateOpCode(CODE_RETRIEVEMD, args));
    }
    
    public static OpCode createDeref(String mdOid,
                                     String dataOid) {
        Object[] args = new Object[2];
        args[0] = mdOid;
        args[1] = dataOid;
        return(factory.allocateOpCode(CODE_DEREF, args));
    }

    public static OpCode createFragAction(String action,
                                          String oid,
                                          int fragID,
                                          int chunkID,
                                          long expectedSize) {
        Object[] args = new Object[5];
        args[0] = action;
        args[1] = oid;
        args[2] = new Integer(fragID);
        args[3] = new Integer(chunkID);
        args[4] = new Long(expectedSize);
        return(factory.allocateOpCode(CODE_FRAGACTION, args));
    }

    public static OpCode createFragIsDeleted(String oid,
                                             int fragID,
                                             boolean shouldBeDeleted) {
        Object[] args = new Object[3];
        args[0] = oid;
        args[1] = new Integer(fragID);
        args[2] = new Boolean(shouldBeDeleted);
        return(factory.allocateOpCode(CODE_FRAGISDELETED, args));
    }

    public static OpCode createRefcntCheck(String oid,
                                           int fragID,
                                           int refcnt,
                                           int maxRefcnt) {
        Object[] args = new Object[4];
        args[0] = oid;
        args[1] = new Integer(fragID);
        args[2] = new Integer(refcnt);
        args[3] = new Integer(maxRefcnt);
        return(factory.allocateOpCode(CODE_REFCNTCHECK, args));
    }

    public static OpCode createRemoveTmp(String oid, int fragID) {
        Object[] args = new Object[2];
        args[0] = oid;
        args[1] = new Integer(fragID);
        return(factory.allocateOpCode(CODE_REMOVETMP, args));
    }
    
    public static OpCode createRemoveTmpTask(int removalTime) {
        Object[] args = new Object[1];
        args[0] = new Integer(removalTime);
        return(factory.allocateOpCode(CODE_REMOVETMPTASK, args));
    }
    
    public static OpCode createExistsTmp(String oid, int fragID) {
        Object[] args = new Object[2];
        args[0] = oid;
        args[1] = new Integer(fragID);
        return(factory.allocateOpCode(CODE_EXISTSTMP, args));
    }
    
    public static OpCode createVerifyTmps(int howMany) {
        Object[] args = new Object[1];
        args[0] = new Integer(howMany);
        return(factory.allocateOpCode(CODE_VERIFYTMPS, args));
    }
    
    public static OpCode createExistsData(String oid, int fragID) {
        Object[] args = new Object[2];
        args[0] = oid;
        args[1] = new Integer(fragID);
        return(factory.allocateOpCode(CODE_EXISTSDATA, args));
    }    
    
    public static OpCode createNotExistsTmp(String oid, int fragID) {
        Object[] args = new Object[2];
        args[0] = oid;
        args[1] = new Integer(fragID);
        return(factory.allocateOpCode(CODE_NOTEXISTSTMP, args));
    }
    
    public static OpCode createLs(String oid) {
        Object[] args = new Object[1];
        args[0] = oid;
        return(factory.allocateOpCode(CODE_LS, args));
    }

    public static OpCode createEcho(String s) {
        Object[] args = new Object[1];
        args[0] = s;
        return(factory.allocateOpCode(CODE_ECHO, args));
    }
    
    public static OpCode createDisableDisk(int disk) {
        Object[] args = new Object[1];
        args[0] = new Integer(disk);
        return(factory.allocateOpCode(CODE_DISABLEDISK, args));
    }
    
    public static OpCode createEnableDisk(int disk) {
        Object[] args = new Object[1];
        args[0] = new Integer(disk);
        return(factory.allocateOpCode(CODE_ENABLEDISK, args));
    }
    
    public static OpCode createDumpCaches() {
        Object [] args = new Object[0];
        return(factory.allocateOpCode(CODE_DUMPCACHES, args));
    }

    public static OpCode createWipeCaches() {
        Object [] args = new Object[0];
        return(factory.allocateOpCode(CODE_WIPECACHES, args));
    }
    
    public static OpCode createGetCacheData(String oid) {
    	Object[] args = new Object[1];
    	args[0] = oid;
        return(factory.allocateOpCode(CODE_GETCACHEDATA, args));
    }
    
    public static OpCode createBackup(String filename, int options) {
        Object [] args = new Object[2];
        args[0] = filename;
        args[1] = new Integer(options);
        return(factory.allocateOpCode(CODE_BACKUP, args));
    }
    
    public static OpCode createBackupInterval(
    	String filename, long t1, long t2, int options)
    {
        Object [] args = new Object[4];
        args[0] = filename;
        args[1] = new Long(t1);
        args[2] = new Long(t2);
        args[3] = new Integer(options);
        return(factory.allocateOpCode(CODE_BACKUPINTERVAL, args));
    }

    public static OpCode createIncBackup(String filename, int options) {
        Object [] args = new Object[2];
        args[0] = filename;
        args[1] = new Integer(options);
        return(factory.allocateOpCode(CODE_INCBACKUP, args));
    }
    
    public static OpCode createRestore(String filename, int options) {
        Object [] args = new Object[2];
        args[0] = filename;
        args[1] = new Integer(options);
        return(factory.allocateOpCode(CODE_RESTORE, args));
    }
    
    public static OpCode createMoveAll() {
        Object [] args = new Object[0];
        return(factory.allocateOpCode(CODE_MOVEALL, args));
    }

    public static OpCode createCompare() {
        Object [] args = new Object[0];
        return(factory.allocateOpCode(CODE_COMPARE, args));
    }

    public static OpCode createSysCacheSize() {
        Object [] args = new Object[0];
        return(factory.allocateOpCode(CODE_SYSCACHESIZE, args));
    }
   
    public static OpCode createRestoreAll() {
        Object [] args = new Object[0];
        return(factory.allocateOpCode(CODE_RESTOREALL, args));
    }
    
    public static OpCode createSetRetention (String oid, long timestamp) {
        Object[] args = new Object[2];
        args[0] = oid;
        args[1] = new Long(timestamp);
        return(factory.allocateOpCode(CODE_SETRETENTION, args));
    }

    public static OpCode createGetRetention (String oid) {
        Object[] args = new Object[1];
        args[0] = oid;
        return(factory.allocateOpCode(CODE_GETRETENTION, args));
    }

    public static OpCode createAddHold (String oid, String hold_tag) {
        Object[] args = new Object[2];
        args[0] = oid;
        args[1] = hold_tag;
        return(factory.allocateOpCode(CODE_ADDHOLD, args));
    }

    public static OpCode createRmHold (String oid, String hold_tag) {
        Object[] args = new Object[2];
        args[0] = oid;
        args[1] = hold_tag;
        return(factory.allocateOpCode(CODE_RMHOLD, args));
    }

    public static OpCode createGetHolds (String oid) {
        Object[] args = new Object[1];
        args[0] = oid;
        return(factory.allocateOpCode(CODE_GETHOLDS, args));
    }

    public static OpCode createGetHeld (String hold_tag) {
        Object[] args = new Object[1];
        args[0] = hold_tag;
        return(factory.allocateOpCode(CODE_GETHELD, args));
    }

    public static OpCode createFefRemove(String oid,
                                         int fefID,
                                         int chunkID) {
        Object[] args = new Object[3];
        args[0] = oid;
        args[1] = new Integer(fefID);
        args[2] = new Integer(chunkID);
        return(factory.allocateOpCode(CODE_FEFREMOVE, args));
    }

    public static OpCode createFefRestore(String oid,
                                          int fefID,
                                          int chunkID) {
        Object[] args = new Object[3];
        args[0] = oid;
        args[1] = new Integer(fefID);
        args[2] = new Integer(chunkID);
        return(factory.allocateOpCode(CODE_FEFRESTORE, args));
    }

    public static OpCode createFefRecover(String oid) {
        Object[] args = new Object[1];
        args[0] = oid;
        return(factory.allocateOpCode(CODE_FEFRECOVER, args));
    }

    protected OpCode(int nOpCode,
                     Object[] nArgs) {
        opCode = nOpCode;
        args = nArgs;
    }

    public int getArgumentAsInt(int index) {
        return(((Integer)args[index]).intValue());
    }

    public long getArgumentAsLong(int index) {
        return(((Long)args[index]).longValue());
    }
    
    public String getArgumentAsString(int index) {
        return((String)args[index]);
    }

    public boolean getArgumentAsBoolean(int index) {
        return(((Boolean)args[index]).booleanValue());
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(codeStrings[opCode]);
        sb.append(" (");
        if (args != null) {
            for (int i=0; i<args.length; i++) {
                if (i>0) {
                    sb.append(", ");
                }
                if (args[i] == null) {
                    sb.append("null");
                } else {
                    if (args[i] instanceof NewObjectIdentifier) {
                        sb.append(((NewObjectIdentifier)args[i]).toString());
                    } else if (args[i] instanceof String) {
                        sb.append("\"");
                        sb.append(args[i].toString());
                        sb.append("\"");
                    } else {
                        sb.append(args[i].toString());
                    }
                }
            }
        }
        sb.append(");");

        return(sb.toString());
    }

    public Object execute() 
        throws ArchiveException {
	LOG.setLevel(Level.FINE);

        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }

        try {
            output.print(this);
        } catch (IOException e) {
            ArchiveException newe = new ArchiveException("Failed to log the operation being executed ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        switch (opCode) {
        case CODE_STORE: {
            int size = getArgumentAsInt(1);
	    boolean shouldSucceed = getArgumentAsBoolean(2);
            NewObjectIdentifier oid = executeStore(size, shouldSucceed);
            OIDTagTable.getInstance().register(getArgumentAsString(0), oid);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("OPCODE STORE : ["+oid.toString()+"] has been stored with size " + size);
            }
            } break;

        case CODE_ADDMD: {
            NewObjectIdentifier link = OIDTagTable.getInstance().resolve(getArgumentAsString(0));
            boolean shouldSucceed = getArgumentAsBoolean(2);
            NewObjectIdentifier oid = executeAddMD(link,shouldSucceed);
            OIDTagTable.getInstance().register(getArgumentAsString(1), oid);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("OPCODE ADDM : Added ["+
                         oid.toString()+"] to ["+
                         link.toString()+"]");
            }
            } break;

        case CODE_DELETE: {
            NewObjectIdentifier oid = OIDTagTable.getInstance().resolve(getArgumentAsString(0));
            executeDelete(oid, getArgumentAsBoolean(1));
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("OPCODE DELETE :  ["+oid.toString()+
                         "] has been deleted");
            }
	    }break;
	    
	    
	case CODE_RECOVER: {
	    NewObjectIdentifier oid = new NewObjectIdentifier(OIDTagTable.getInstance().resolve(getArgumentAsString(0)).toString());
	    int fragID = getArgumentAsInt(1);
	    int chunkID = getArgumentAsInt(2);
	    int curchunk = 0;
	     while(chunkID > curchunk++) {
		oid.setChunkNumber(curchunk);
		oid.setLayoutMapId(LayoutClient.getInstance().
				   getConsecutiveLayoutMapId(oid.
							     getLayoutMapId()));
	    }
	    executeRecover(oid, fragID);
      if (LOG.isLoggable(Level.FINE)) {
	  LOG.fine("OPCODE RECOVER : ["+oid.toString()+"] fragID [" + 
		   fragID + "] has been recovered");
            }
	    } break;

        case CODE_REMOVETMP: {
            NewObjectIdentifier oid = new NewObjectIdentifier(OIDTagTable
                    .getInstance().resolve(getArgumentAsString(0)).toString());
            int fragID = getArgumentAsInt(1);
            executeRemoveTmp(oid, fragID);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("OPCODE REMOVETMP : [" + oid.toString() + "] fragID ["
                        + fragID + "] has been done.");
            }
        }
            break;
            
        case CODE_REMOVETMPTASK: {
            int removalTime = getArgumentAsInt(0);
            executeRemoveTmpTask(removalTime);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("OPCODE REMOVETMPTASK has been executed for tmp frags older than " + removalTime +" ms.");
            }
        }
            break;
            
        case CODE_REMOVEM: {
            NewObjectIdentifier oid = OIDTagTable.getInstance().resolve(getArgumentAsString(0));
            executeRemoveM(oid);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("OPCODE REMOVEM :  ["+oid.toString()+
                         "] has been deleted from the system cache");
            }
	    } break;
            
        case CODE_VERIFYTMPS: {
            int howMany = getArgumentAsInt(0);
            executeVerifyTmps(howMany);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("OPCODE CODE_VERIFYTMPS has been executed for all tmp directories");
            }
        }
            break;
            
        case CODE_EXISTSTMP: {
            NewObjectIdentifier oid = new NewObjectIdentifier(OIDTagTable
                    .getInstance().resolve(getArgumentAsString(0)).toString());
            int fragID = getArgumentAsInt(1);
            executeExistsTmp(oid, fragID);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("OPCODE EXISTSTMP : [" + oid.toString() + "] fragID ["
                        + fragID + "] has been done.");
            }
        }
            break;
            
        case CODE_EXISTSDATA: {
            NewObjectIdentifier oid = new NewObjectIdentifier(OIDTagTable
                    .getInstance().resolve(getArgumentAsString(0)).toString());
            int fragID = getArgumentAsInt(1);
            executeExistsTmp(oid, fragID);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("OPCODE EXISTSDATA : [" + oid.toString() + "] fragID ["
                        + fragID + "] has been done.");
            }
        }
            break;
            
        case CODE_NOTEXISTSTMP: {
            NewObjectIdentifier oid = new NewObjectIdentifier(OIDTagTable
                    .getInstance().resolve(getArgumentAsString(0)).toString());
            int fragID = getArgumentAsInt(1);
            executeNotExistsTmp(oid, fragID);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("OPCODE NOTEXISTSTMP : [" + oid.toString() + "] fragID ["
                        + fragID + "] has been done.");
            }
        }
            break;

        case CODE_RETRIEVE: {
            NewObjectIdentifier oid = OIDTagTable.getInstance().resolve(getArgumentAsString(0));
	    long offset = getArgumentAsLong(1);
	    long length = getArgumentAsLong(2);
            boolean shouldSucceed = getArgumentAsBoolean(3);
            executeRetrieve(oid, offset, length, shouldSucceed);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Retrieved object ["+
                         oid.toString()+"].");
            }
        } break;

        case CODE_RETRIEVEMD: {
            NewObjectIdentifier oid = OIDTagTable.getInstance().resolve(getArgumentAsString(0));
            boolean shouldSucceed = getArgumentAsBoolean(1);
            executeRetrieve(oid, 0, Long.MAX_VALUE, shouldSucceed);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Retrieved object ["+
                         oid.toString()+"].");
            }
        } break;
        
        case CODE_DEREF: {
            NewObjectIdentifier mdOid = OIDTagTable.getInstance().resolve(getArgumentAsString(0));
            NewObjectIdentifier dataOid = executeDeref(mdOid);
            OIDTagTable.getInstance().register(getArgumentAsString(1), dataOid);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("MD oid ["+
                         mdOid.toString()+"] has been derefenced. Data oid is ["+
                         dataOid.toString()+"]");
            }
        } break;

        case CODE_FRAGACTION: {
            String action = getArgumentAsString(0);
            NewObjectIdentifier oid = new NewObjectIdentifier(OIDTagTable.getInstance().resolve(getArgumentAsString(1)).toString());
            int fragID = getArgumentAsInt(2);
            int chunkID = getArgumentAsInt(3);
            int curchunk = 0;
            while(chunkID > curchunk++) {
                oid.setChunkNumber(curchunk);
                oid.setLayoutMapId(LayoutClient.getInstance().
                    getConsecutiveLayoutMapId(oid.
                    getLayoutMapId()));
            }
            long expectedSize = getArgumentAsLong(4);
            executeFragAction(action, oid, fragID, expectedSize);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Action " + action + " taken on oid " + oid +
                         ", frag " + fragID);
            }
        } break;

        case CODE_FRAGISDELETED: {
            NewObjectIdentifier oid = OIDTagTable.getInstance().resolve(getArgumentAsString(0));
            int fragID = getArgumentAsInt(1);
            boolean shouldBeDeleted = getArgumentAsBoolean(2);
            executeFragIsDeleted(oid, fragID, shouldBeDeleted);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Checking deleted status for oid " + oid +
                         ", frag " + fragID + "; shouldBeDeleted=" +
                         shouldBeDeleted);
            }
        } break;

        case CODE_REFCNTCHECK: {
            NewObjectIdentifier oid = OIDTagTable.getInstance().resolve(getArgumentAsString(0));
            int fragID = getArgumentAsInt(1);
            int refcnt = getArgumentAsInt(2);
            int maxRefcnt = getArgumentAsInt(3);
            executeRefcntCheck(oid, fragID, refcnt, maxRefcnt);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Checking refcnt status for oid " + oid +
                         ", frag " + fragID + "; refcnt=" +
                         refcnt + "; maxrefcnt=" + maxRefcnt);
            }
        } break;

        case CODE_LS: {
            NewObjectIdentifier oid = OIDTagTable.getInstance().resolve(getArgumentAsString(0));
            executeLs(oid);
        } break;

        case CODE_ECHO: {
            String s = getArgumentAsString(0);
            executeEcho(s);
        } break;
        
        case CODE_DISABLEDISK: {
            int disk = getArgumentAsInt(0);
            executeDisableDisk(disk);
        } break;
        
        case CODE_ENABLEDISK: {
            int disk = getArgumentAsInt(0);
            executeEnableDisk(disk);
        } break;

        case CODE_DUMPCACHES: {
            executeDumpCaches();
        } break;
        
        case CODE_WIPECACHES: {
            executeWipeCaches();
        } break;
        
        case CODE_GETCACHEDATA: {
            NewObjectIdentifier oid = OIDTagTable.getInstance().resolve(getArgumentAsString(0));
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("OPCODE GETCACHEDATA :  ["+oid.toString()+
                         "] has been retrieved");
            }
            return executeGetCacheData(oid);
	    }
        
        case CODE_BACKUP: {
            String filename = getArgumentAsString(0);
            int options = getArgumentAsInt(1);
            executeBackup(filename, options);
        } break;
       
        case CODE_BACKUPINTERVAL: {
            String filename = getArgumentAsString(0);
            long t1 = getArgumentAsLong(2);
            long t2 = getArgumentAsLong(2);
            int options = getArgumentAsInt(3);
            executeBackupInterval(filename, t1, t2, options);
        } break;
        
        case CODE_INCBACKUP: {
            String filename = getArgumentAsString(0);
            int options = getArgumentAsInt(1);
            executeIncBackup(filename, options);
        } break;
      
        case CODE_RESTORE: {
            String filename = getArgumentAsString(0);
            int options = getArgumentAsInt(1);
            executeRestore(filename, options);
        } break;
        
        case CODE_MOVEALL: {
            executeMoveAll();
        } break;
       
        case CODE_COMPARE: {
            executeCompare();
        } break;

        case CODE_SYSCACHESIZE: {
            return executeSysCacheSize();
        }
        
        case CODE_RESTOREALL: {
            executeRestoreAll();
        } break;
        
        case CODE_SETRETENTION: {
            NewObjectIdentifier oid 
                = OIDTagTable.getInstance().resolve(getArgumentAsString(0));
            long timestamp = getArgumentAsLong(1);
            executeSetRetention (oid, timestamp);
            break;
        }

        case CODE_GETRETENTION: {
            NewObjectIdentifier oid = OIDTagTable.getInstance().resolve(getArgumentAsString(0));
            return executeGetRetention (oid);
        }
        
        case CODE_ADDHOLD: {
            NewObjectIdentifier oid = OIDTagTable.getInstance().resolve(getArgumentAsString(0));
            String hold_tag = getArgumentAsString (1);
            executeAddHold (oid, hold_tag);
            break;
        }
        
        case CODE_RMHOLD: {
            NewObjectIdentifier oid = OIDTagTable.getInstance().resolve(getArgumentAsString(0));
            String hold_tag = getArgumentAsString (1);
            executeRmHold (oid, hold_tag);
            break;
        }
        
        case CODE_GETHOLDS: {
            NewObjectIdentifier oid = OIDTagTable.getInstance().resolve(getArgumentAsString(0));
            return executeGetHolds (oid);
        }
        
        case CODE_GETHELD: {
            String hold_tag = getArgumentAsString (0);
            return executeGetHeld (hold_tag);
        }

        case CODE_FEFREMOVE: {
            NewObjectIdentifier oid = new NewObjectIdentifier(OIDTagTable.getInstance().resolve(getArgumentAsString(0)).toString());
            int fefID = getArgumentAsInt(1);
            int chunkID = getArgumentAsInt(2);
            int curchunk = 0;
            while(chunkID > curchunk++) {
                oid.setChunkNumber(curchunk);
                oid.setLayoutMapId(LayoutClient.getInstance().
                    getConsecutiveLayoutMapId(oid.
                    getLayoutMapId()));
            }
            executeFefRemove(oid, fefID);
        } break;

        case CODE_FEFRESTORE: {
            NewObjectIdentifier oid = new NewObjectIdentifier(OIDTagTable.getInstance().resolve(getArgumentAsString(0)).toString());
            int fefID = getArgumentAsInt(1);
            int chunkID = getArgumentAsInt(2);
            int curchunk = 0;
            while(chunkID > curchunk++) {
                oid.setChunkNumber(curchunk);
                oid.setLayoutMapId(LayoutClient.getInstance().
                    getConsecutiveLayoutMapId(oid.
                    getLayoutMapId()));
            }
            executeFefRestore(oid, fefID);
        } break;

        case CODE_FEFRECOVER: {
            NewObjectIdentifier oid = new NewObjectIdentifier(OIDTagTable.getInstance().resolve(getArgumentAsString(0)).toString());
            executeFefRecover(oid);
        } break;

        default:
            throw new ArchiveException("Unknown opcode ["+opCode+"]");
        }

        nbOps++;
        if (nbOps % 60 == 0) {
            long time = System.currentTimeMillis();
            String msg = "STAT> "+nbOps+" operations have been executed in "+((time-startTime)/1000)+
                " s. ["+(nbOps*1000/(time-startTime))+" ops/s]";
            LOG.info(msg);
            System.out.println(msg);
        }

        return null;
    }

    /**********************************************************************
     *
     * Abtract methods
     *
     **********************************************************************/

    protected abstract NewObjectIdentifier executeStore(int size,
							boolean shouldSucceed)
        throws ArchiveException;

    protected abstract NewObjectIdentifier executeAddMD(NewObjectIdentifier link, boolean shouldSucceed) 
        throws ArchiveException;

    protected abstract void executeDelete(NewObjectIdentifier oid,
                                          boolean shouldSucceed)
        throws ArchiveException;

    protected abstract void executeRecover(NewObjectIdentifier oid,
					   int fragID)
        throws ArchiveException;
    
    protected abstract void executeRemoveTmp(NewObjectIdentifier oid,
                   int fragID)
        throws ArchiveException;
    
    protected abstract void executeRemoveTmpTask(int removalTime) 
        throws ArchiveException;
    
    protected abstract void executeRemoveM(NewObjectIdentifier oid)
    	throws ArchiveException;
    
    protected abstract void executeExistsTmp(NewObjectIdentifier oid,
                    int fragID)
    throws ArchiveException;
    
    protected abstract void executeNotExistsTmp(NewObjectIdentifier oid,
                    int fragID)
        throws ArchiveException;

    protected abstract void executeExistsData(NewObjectIdentifier oid,
                int fragID)
    throws ArchiveException;

    protected abstract void executeVerifyTmps(int howMany) throws ArchiveException;
    
    protected abstract void executeRetrieve(NewObjectIdentifier oid,
					    long offset,
					    long length,
                                            boolean shouldSucceed)
        throws ArchiveException;

    protected abstract void executeRetrieveM(NewObjectIdentifier oid,
                                             long offset,
                                             long length,
                                             boolean shouldSucceed)
        throws ArchiveException;

    protected abstract NewObjectIdentifier executeDeref(NewObjectIdentifier mdOid)
        throws ArchiveException;

    protected abstract void executeFragAction(String action,
                                              NewObjectIdentifier oid,
                                              int fragID,
                                              long expectedSize)
        throws ArchiveException;

    protected abstract void executeFragIsDeleted(NewObjectIdentifier oid,
                                                 int fragID,
                                                 boolean shouldBeDeleted)
        throws ArchiveException;

    protected abstract void executeRefcntCheck(NewObjectIdentifier oid,
                                               int fragID,
                                               int refcnt,
                                               int maxrefcnt)
        throws ArchiveException;

    protected abstract void executeLs(NewObjectIdentifier oid);

    protected abstract void executeEcho(String s);
    
    protected abstract void executeDisableDisk(int disk) throws ArchiveException;
    
    protected abstract void executeEnableDisk(int disk) throws ArchiveException;

    // bulk oa
    protected abstract void executeDumpCaches() throws ArchiveException;

    protected abstract void executeWipeCaches() throws ArchiveException;
   
    protected abstract SystemMetadata executeGetCacheData(NewObjectIdentifier oid)
    	throws ArchiveException;
    
    protected abstract void executeBackup(String filename, int options) 
                       throws ArchiveException;
    
    protected abstract void executeBackupInterval(
    		String filename, long t1, long t2, int options) throws ArchiveException;
    
    protected abstract void executeIncBackup(String filename, int options) 
                       throws ArchiveException;
   
    protected abstract void executeRestore(String filename, int options) 
                       throws ArchiveException;
 
    protected abstract void executeMoveAll() throws ArchiveException;

    protected abstract void executeCompare() throws ArchiveException;

    protected abstract Integer executeSysCacheSize() throws ArchiveException;
   
    protected abstract void executeRestoreAll() throws ArchiveException;

    // compliance
    protected abstract void executeSetRetention (NewObjectIdentifier oid, 
                                                 long timestamp)
                                                    throws ArchiveException;

    protected abstract Long executeGetRetention (NewObjectIdentifier oid)
                                                    throws ArchiveException;
    
    protected abstract void executeAddHold (NewObjectIdentifier oid, 
                                            String hold_tag)
                                                    throws ArchiveException;
    
    protected abstract void executeRmHold (NewObjectIdentifier oid,
                                           String hold_tag)
                                                    throws ArchiveException;
    
    protected abstract String[] executeGetHolds (NewObjectIdentifier oid)
                                                    throws ArchiveException;
    
    protected abstract NewObjectIdentifier[] executeGetHeld (String hold_tag)
                                                    throws ArchiveException;

    protected abstract void executeFefRemove(NewObjectIdentifier oid,
                                             int fragID)
        throws ArchiveException;

    protected abstract void executeFefRestore(NewObjectIdentifier oid,
                                              int fragID)
        throws ArchiveException;

    protected abstract void executeFefRecover(NewObjectIdentifier oid)
        throws ArchiveException;
}
