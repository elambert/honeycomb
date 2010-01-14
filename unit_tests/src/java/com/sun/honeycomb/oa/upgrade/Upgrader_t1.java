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

import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.disks.DiskId;

import java.util.logging.Logger;
import java.util.LinkedList;
import java.util.List;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;

public class Upgrader_t1 extends UpgradeTestcase {
    private static Logger log
        = Logger.getLogger(Upgrader_t1.class.getName());
    /**********************************************************************/
    public Upgrader_t1(String name) {
        super(name);
    }

    /**********************************************************************/
    public void testUpgrade() throws Exception {
        doUpgrade();
    }

    /**********************************************************************/
    public void testVersion() throws Exception {
        File file = new File(disk.getPath() + "/VERSION");
        assertFalse("version file", file.exists());
        doUpgrade();
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line = br.readLine();
        br.close();
        assertTrue("version",
                   line.substring(0, 3).equals(Upgrader.CURRENT_VERSION));
    }

    /**********************************************************************/
    public void testVersionFileOverwrite() throws Exception {
        doVersionFileTest("1.1-20"); // should update to 1.1-27
    }

    /**********************************************************************/
    public void testCorruptedVersionFile() throws Exception {
        doVersionFileTest("foo");
    }

    /**********************************************************************/
    public void testCorruptedVersionFileEmpty() throws Exception {
        doVersionFileTest("");
    }

    /**********************************************************************/
    public void doVersionFileTest(String content) throws Exception {
        File file = new File(disk.getPath() + "/VERSION");
        assertFalse("version file", file.exists());
        FileWriter out = new FileWriter(file);
        out.write(content);
        out.close();
        assertTrue("version file", file.exists());
        doUpgrade();
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line = br.readLine();
        br.close();
        assertTrue("version", line.equals("1.1-27")); // as in config file
    }

    /**********************************************************************/
    private void doUpgrade() throws Exception {
	assertEquals("Temp disk files", 0, getTempFilesCount());
        List disks = new LinkedList();
        disks.add(disk);
        new Upgrader().upgrade(disks);
	assertEquals("Temp disk files", 0, getTempFilesCount());
    }


}
