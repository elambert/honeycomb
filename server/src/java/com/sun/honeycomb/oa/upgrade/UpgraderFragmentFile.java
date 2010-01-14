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



package com.sun.honeycomb.oa.upgrade;

import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.oa.FragmentNotFoundException;
import com.sun.honeycomb.oa.daal.DAALException;
import com.sun.honeycomb.oa.daal.DAAL;
import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.coding.Decoder;
import com.sun.honeycomb.resources.ByteBufferPool;
import com.sun.honeycomb.oa.upgrade.DiskUpgrader.Profile;
import com.sun.honeycomb.oa.upgrade.UpgraderException.TmpUpgraderException;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Map;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;

public class UpgraderFragmentFile extends FragmentFile {

    private static Logger log
        = Logger.getLogger(UpgraderFragmentFile.class.getName());
    private final ByteBuffer readBuffer;
    private final ByteBuffer writeBuffer;
    private long footerOffset;
    private Profile profile;
    protected final String workDirectory;

    /**********************************************************************/
    public UpgraderFragmentFile(NewObjectIdentifier oid, int fragmentId,
                                Disk disk, ByteBuffer readBuffer,
                                ByteBuffer writeBuffer, String workDirectory,
                                Profile profile) {
        super(oid, fragmentId, disk);
        this.readBuffer = readBuffer;
        this.writeBuffer = writeBuffer;
        this.profile = profile;
        this.workDirectory = workDirectory;
    }

    /**********************************************************************
     * Read footer, upgrade OID and write it back.
     */
    public void upgrade() throws UpgraderException {
        File newFile = moveFile();
	updateDAAL(newFile);
        UpgraderFragmentFooter uff = readFragmentFooter();
        if (uff != null) {
            uff.upgrade();
            writeFragmentFooter(uff);
            restoreFile();
        }
    }

    /**********************************************************************/
    protected String fragmentFileName(UpgraderNewObjectIdentifier oid) {
	return Common.makeFragmentName(oid, getFragNum());
    }

    /**********************************************************************/
    protected File fragmentFile(UpgraderNewObjectIdentifier oid,
				String fragmentName) {
        String dataDir = Common.makeDir(oid, getDisk());
        String name = dataDir + File.separator + fragmentName;
        return new File(name);
    }

    /**********************************************************************/
    protected void updateDAAL(File file) throws UpgraderException {
        if (daal == null) {
            throw new UpgraderException("DAAL object is not initialized");
        }
        ((UpgraderDAAL) daal).setFile(file);
    }	

    /**********************************************************************/
    protected File moveFile() throws UpgraderException {
        UpgraderNewObjectIdentifier oid
            = (UpgraderNewObjectIdentifier) getOID();
        oid.setOldVersion();
	String fragmentName = fragmentFileName(oid);
	File file = fragmentFile(oid, fragmentName);
        
	String newName = workDirectory + File.separator + fragmentName;
        File newFile = new File(newName);
        renameFile(file, newFile);
	return newFile;
    }

    /**********************************************************************/
    protected void restoreFile() throws UpgraderException {
        UpgraderNewObjectIdentifier oid
            = (UpgraderNewObjectIdentifier) getOID();
        oid.setOldVersion();
        String fragmentName = fragmentFileName(oid);
	String name = workDirectory + File.separator + fragmentName;
        File file = new File(name);

        oid.setNewVersion();
	File newFile = fragmentFile(oid, fragmentFileName(oid));

	renameFile(file, newFile);
    }

    /**********************************************************************/
    protected void renameFile(File file, File newFile) 
	throws UpgraderException {
        try {
            if (!file.renameTo(newFile)) {
                throw new UpgraderException("Rename failed for "
                                            + file.getName());
            }
        } catch (Exception e) {
            throw new UpgraderException(e);
        }
    }

    /**********************************************************************/
    protected UpgraderFragmentFooter readFragmentFooter() 
	throws UpgraderException {
        try {
            long start = System.currentTimeMillis();
            daal.rwopen();
            profile.put(Profile.DaalRWOpen, System.currentTimeMillis() - start);
        } catch (DAALException de) {
            log.severe("Cannot open " + daal + ", " + de.getMessage());
            throw new UpgraderException(de);
        } catch (FragmentNotFoundException fnfe) {
            log.severe("Cannot find " + daal + ", " + fnfe.getMessage());
            throw new UpgraderException(fnfe);
        }

        // Read footer
        try {
            long fileLength = daal.length();
            if (fileLength < UpgraderFragmentFooter.oldSize()) {
                String msg = "File too small - no footer: " + daal + ": "
                    + fileLength;
                log.severe(msg);
                throw new UpgraderException(msg);
            }
            footerOffset = fileLength - UpgraderFragmentFooter.oldSize();
            long start = System.currentTimeMillis();
            long nRead = daal.read(readBuffer, footerOffset);
            profile.put(Profile.DaalRead, System.currentTimeMillis() - start);
            if (nRead != readBuffer.capacity()) {
                String msg = "Failed to read footer " + daal + ", read "
                    + nRead + ", expected " + readBuffer.capacity();
                log.severe(msg);
                throw new UpgraderException(msg);
            }
        } catch (DAALException de) {
            log.severe("Failed to read footer " + daal + ", " + de.getMessage());
            throw new UpgraderException(de);
        }
        readBuffer.flip();
        ByteBufferCoder decoder = new ByteBufferCoder(readBuffer);
        UpgraderFragmentFooter upgraderFragmentFooter
            = new UpgraderFragmentFooter();

        try {
            decoder.decodeKnownClassCodable(upgraderFragmentFooter);
        } catch (Exception unexpected) {
            String msg = unexpected.getClass().getName() + ": "
                + unexpected.getMessage();
            log.log(Level.SEVERE, msg, unexpected);
            throw new UpgraderException(msg);
        }

        if (!upgraderFragmentFooter.isConsistent()) {
            String msg = "Fragment footer is corrupted";
            log.severe(msg);
            throw new UpgraderException(msg);
        }
        UpgraderNewObjectIdentifier oid
            = (UpgraderNewObjectIdentifier) upgraderFragmentFooter.oid;
        oid.setNewVersion();
        UpgraderNewObjectIdentifier linkoid
            = (UpgraderNewObjectIdentifier) upgraderFragmentFooter.linkoid;
        linkoid.setNewVersion();
        return upgraderFragmentFooter;
    }

    /**********************************************************************/
    protected void writeFragmentFooter(UpgraderFragmentFooter uff)
        throws UpgraderException {
        ByteBufferCoder encoder = new ByteBufferCoder(writeBuffer);
        encoder.encodeKnownClassCodable(uff);
        writeBuffer.flip();
        assert(footerOffset != 0);
        try {
            long start = System.currentTimeMillis();
            long written = daal.write(writeBuffer, footerOffset);
            profile.put(Profile.DaalWrite, System.currentTimeMillis() - start);
            if (written != writeBuffer.capacity()) {
                String msg = "Error writing file " + daal + ", written "
                    + written + ", expected " + writeBuffer.capacity();
                log.severe(msg);
                throw new UpgraderException(msg);
            }
        } catch (DAALException de) {
            String msg = "Failed to write footer " + daal;
            log.severe(msg);
            throw new UpgraderException(msg);
        }
        try {
            long start = System.currentTimeMillis();
            daal.close();
            profile.put(Profile.DaalClose, System.currentTimeMillis() - start);
        } catch (DAALException de) {
            log.severe("Cannot close " + daal + ", " + de.getMessage());
            throw new UpgraderException(de);
        }
    }

    /**********************************************************************/
    protected DAAL instantiateDAAL(Disk disk, NewObjectIdentifier oid,
                                   int fragNum) {
        return new UpgraderDAAL(disk, oid, new Integer(fragNum));
    }

    /**********************************************************************/
    public static class UpgraderFragmentFooter extends FragmentFooter {
        private static final byte oldVersion = (byte) 0x1;

        public UpgraderFragmentFooter() {
            super();
        }
        protected NewObjectIdentifier
            decodeNewObjectIdentifier(Decoder decoder) {
            UpgraderNewObjectIdentifier noi 
		= new UpgraderNewObjectIdentifier();
	    decoder.decodeKnownClassCodable(noi);
            return noi;
        }
        public void upgrade() {
            footerChecksum = 0;
            footerChecksum = calculateChecksum();
        }
        public static int oldSize() {
            return 372;
        }
        public static int newSize() {
            return FragmentFooter.SIZE;
        }
    }

    /**********************************************************************/
    public static class UpgraderTmpFragmentFile extends UpgraderFragmentFile {
	public UpgraderTmpFragmentFile(NewObjectIdentifier oid, int fragmentId,
				       Disk disk, ByteBuffer readBuffer,
				       ByteBuffer writeBuffer, 
				       String workDirectory, Profile profile) {
	    super(oid, fragmentId, disk, readBuffer, writeBuffer, 
		  workDirectory, profile);
	}

	/**********************************************************************
	 * Read footer, upgrade OID and write it back.
	 */
	public void upgrade() throws TmpUpgraderException, UpgraderException {
	    UpgraderNewObjectIdentifier oid
		= (UpgraderNewObjectIdentifier) getOID();
	    oid.setOldVersion();
	    File file = fragmentFile(oid, fragmentFileName(oid));

	    try {
		updateDAAL(file);
		UpgraderFragmentFooter uff = readFragmentFooter();
		if (uff != null) {
		    uff.upgrade();
		    writeFragmentFooter(uff);
		}
	    } catch (UpgraderException ignore) {
		throw new TmpUpgraderException(ignore.getMessage());
	    } finally {
		// rename the file
		oid.setNewVersion();
		File newFile = fragmentFile(oid, fragmentFileName(oid));
		renameFile(file, newFile);
	    }
	}

	/********************************************************************/
	protected File fragmentFile(UpgraderNewObjectIdentifier oid,
				    String fragmentName) {
	    String tmpDir = Common.makeTmpDirName(getDisk());
	    String name = tmpDir + File.separator + fragmentName;
	    return new File(name);
	}
    }

    /**********************************************************************/
    public static class UpgraderRepoFragmentFile extends UpgraderFragmentFile {
	public UpgraderRepoFragmentFile(NewObjectIdentifier oid, int fragmentId,
				       Disk disk, ByteBuffer readBuffer,
				       ByteBuffer writeBuffer, 
				       String workDirectory, Profile profile) {
	    super(oid, fragmentId, disk, readBuffer, writeBuffer, 
		  workDirectory, profile);
	}

	/**********************************************************************
	 * Read footer, upgrade OID and write it back.
	 */
	public void upgrade() throws UpgraderException {
	    UpgraderNewObjectIdentifier oid
		= (UpgraderNewObjectIdentifier) getOID();
	    oid.setOldVersion();
	    String name = workDirectory + File.separator + fragmentFileName(oid);
	    File file = new File(name);

	    updateDAAL(file);
	    UpgraderFragmentFooter uff;
	    try {
		uff = readFragmentFooter();
	    } catch (UpgraderException ue) {
		log.warning("Failed to read 1.0 footer, attempting to read "
			    + "as 1.1: " + name);
		repoOpen();
		restoreFile();
		return;
	    }
	    
	    if (uff != null) {
		uff.upgrade();
		writeFragmentFooter(uff);
		restoreFile();
	    }

	}

	public long repoOpen() throws UpgraderException {

	    assert(daal != null);
	    
	    ByteBufferPool pool = ByteBufferPool.getInstance();
	    ByteBuffer readBuffer = pool.checkOutBuffer(CACHE_READ_SIZE);
	    try {
		readFooter(readBuffer);
	    } catch (Exception e) {
		throw new UpgraderException(e);
	    } finally {
		pool.checkInBuffer(readBuffer);
	    }

	    return fragmentFooter.creationTime;
	}

    }
}
