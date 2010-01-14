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

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.datadoctor.TaskFragUtils;
import com.sun.honeycomb.datadoctor.NotFragmentFileException;
import com.sun.honeycomb.oa.Common;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.oa.upgrade.DiskUpgrader.ByteBufferPool;
import com.sun.honeycomb.oa.upgrade.DiskUpgrader.Profile;
import com.sun.honeycomb.oa.upgrade.DiskUpgrader.ByteBufferRecord;
import com.sun.honeycomb.oa.FragmentFile;
import com.sun.honeycomb.oa.daal.DAALException;
import com.sun.honeycomb.oa.daal.DAAL;
import com.sun.honeycomb.oa.upgrade.UpgraderFragmentFile.UpgraderFragmentFooter;

import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class UpgraderFragmentFile_t1 extends UpgradeTestcase {
    private static Logger log
        = Logger.getLogger(UpgraderFragmentFile_t1.class.getName());
    private ByteBufferPool readBufferPool;
    private ByteBufferPool writeBufferPool;
    private ByteBufferRecord rbr;
    private ByteBufferRecord wbr;

    /**********************************************************************/
    public UpgraderFragmentFile_t1(String name) {
        super(name);
    }

    /**********************************************************************/
    protected void setUp() throws Exception {
        super.setUp();
        readBufferPool
            = new ByteBufferPool(1, UpgraderFragmentFooter.oldSize());
        writeBufferPool
            = new ByteBufferPool(1, UpgraderFragmentFooter.newSize());
        rbr = readBufferPool.checkoutBuffer();
        wbr = writeBufferPool.checkoutBuffer();
        File workDir = new File(workDirectory);
        if (!workDir.isDirectory()) {
            assertTrue("work dir", workDir.mkdir());
        }
    }

    /**********************************************************************/
    protected void tearDown() throws Exception {
        readBufferPool.checkinBuffer(rbr);
        writeBufferPool.checkinBuffer(wbr);
        super.tearDown();
    }

    /**********************************************************************/
    public void testUpgrade1() throws Exception {
        File[] files = new File(FRAG_DIR).listFiles();
        upgrade(files[0]);
    }

    /**********************************************************************/
    public void testUpgrade2() throws Exception {
        File[] files = new File(FRAG_DIR).listFiles();
        upgrade(files[1]);
    }

    /**********************************************************************/
    private void upgrade(File file) throws Exception {
        int size = (int) file.length();
        int fragmentId = TaskFragUtils.extractFragId(file.getName());
        NewObjectIdentifier oid
            = UpgraderNewObjectIdentifier.fromFilename(file.getName());
        ByteBuffer buffer = ByteBuffer.allocate(size - UpgraderFragmentFooter.oldSize());
        readFile(file, buffer);

        // try to open old version fragment
        try {
            new FragmentFile(oid, fragmentId, disk).open();
            fail("opened fragment file");
        } catch (Exception expected) {
        }

        // cache the footer
        UpgraderFragmentFile uff = new UpgraderFragmentFile(oid, fragmentId,
                                                            disk,
                                                            rbr.getBuffer(),
                                                            wbr.getBuffer(),
                                                            workDirectory,
                                                            new Profile()) {
                public UpgraderFragmentFooter readFragmentFooter()
                    throws UpgraderException {
                    String filename
                        = Common.makeFilename(getOID(), getDisk(), getFragNum());
                    ((UpgraderDAAL) daal).setFile(new File(filename));
                    UpgraderFragmentFooter uff = super.readFragmentFooter();
                    uff.upgrade();
                    try {
                        daal.close();
                    } catch (DAALException e) {
                        throw new UpgraderException(e);
                    }
                    rbr.getBuffer().clear();
                    return uff;
                }
            };
        UpgraderFragmentFooter footer = uff.readFragmentFooter();

        // do the full upgrade
        uff = new UpgraderFragmentFile(oid, fragmentId, disk, rbr.getBuffer(),
                                       wbr.getBuffer(), workDirectory,
                                       new Profile());
        uff.upgrade();

        // locate the upgraded file and read footer using non-specialized
        //   fragment file.
        File[] newFiles = new File(FRAG_DIR).listFiles(new FileFilter() {
                public boolean accept(File file) {
                    return Pattern.matches("(\\S{36})(\\.(\\d+)){7}_(\\d+)",
                                           file.getName());
                }
            });
        File newFile = null;
        for (int i = 0; i < newFiles.length; i++) {
            String oidStr = newFiles[i].getName().substring(0, 36);
            if (oidStr.equals(file.getName().substring(0, 36))) {
                newFile = newFiles[i];
            }
        }
        assertNotNull("upgraded file not found", newFile);
        NewObjectIdentifier newOid
            = Common.extractOIDFromFilename(newFile.getName());

        // try to open new version fragment
        log.info("upgraded fragment file: " + newFile.getPath());
        FragmentFile ff = new FragmentFile(newOid, fragmentId, disk);
        ff.open();

        // verify footer
        assertTrue("footers", ff.getFooter().equals(footer));
        ff.close();
        // verify size
        int newSize = (int) newFile.length();
        assertEquals("file size", size + 4, newSize);
        // verify data
        ByteBuffer newBuffer = ByteBuffer.allocate(newSize - FragmentFooter.SIZE);
        readFile(newFile, newBuffer);
        assertTrue("data", buffer.equals(newBuffer)); // yay

        // try to upgrade upgraded file
        try {
            uff.upgrade();
            fail("upgraded second time");
        } catch (Exception expected) {
        }
    }

    /**********************************************************************/
    public void testUpgradeFail() throws Exception {
        File file = (new File(FRAG_DIR).listFiles())[0];
        int fragmentId = TaskFragUtils.extractFragId(file.getName());
        NewObjectIdentifier oid
            = UpgraderNewObjectIdentifier.fromFilename(file.getName());

        // try to upgrade a corrupt file
        UpgraderFragmentFile uff
            = new UpgraderFragmentFile(oid, fragmentId, disk, rbr.getBuffer(),
                                       wbr.getBuffer(), workDirectory,
                                       new Profile()) {
                    // skip renaming file
                    protected void renameFile() throws UpgraderException { }
                };
        uff.upgrade();
        try {
            new UpgraderFragmentFile(oid, fragmentId, disk, rbr.getBuffer(),
                                     wbr.getBuffer(), workDirectory,
                                     new Profile()).upgrade();
            fail("upgraded corrupt file");
        } catch (UpgraderException expected) {
        }

        // try to upgrade a 0 length file
        File empty = new File("foo");
        try {
            upgrade(empty);
            fail("upgraded empty file");
        } catch (NotFragmentFileException expected) {
        }
    }

    /**********************************************************************/
    private int readFile(File file, ByteBuffer buffer) throws Exception {
        FileChannel channel = new FileInputStream(file).getChannel();
        int nread = channel.read(buffer);
        buffer.flip();
        assertFalse("bytes read", 0 == nread);
        assertFalse("bytes read", -1 == nread);
        return nread;
    }
}
