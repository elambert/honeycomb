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



package com.sun.honeycomb.util.sysdep;

import java.io.*;
import java.util.*;
import java.util.logging.*;

import com.sun.honeycomb.hwprofiles.HardwareProfile;
import com.sun.honeycomb.common.Getopt;
import com.sun.honeycomb.util.Exec;

/**
 * Test for DiskOps classes
 *
 * @author Shamim Mohamed
 * @version $Revision: 1.4 $ $Date: 2004-09-21 13:57:55 -0700 (Tue, 21 Sep 2004) $
 */
public class TestDiskOps {
    public static void main(String[] argv) {
	Date now = new Date(System.currentTimeMillis());
	int rc = 0;
	String inputScriptDir = ".";
	PrintStream simLog = System.out;
	int verbosity = 0;
	try {
	    Getopt opts = new Getopt(argv, "o:d:v");
	    while (opts.hasMore()) {
		Getopt.Option option = opts.next();
		switch (option.name()) {

		case 'o':
		    if (option.value().equals("-"))
			simLog = System.out;
		    else
			simLog = 
			    new PrintStream(new FileOutputStream(option.value()));
		    if (simLog == null) {
			System.err.println("Couldn't open \"" + option.value() +
					   "\" for writing -- abort");
			System.exit(2);
		    }
		    break;

		case 'd':
		    inputScriptDir = option.value();
		    break;

		case 'v':
		    verbosity++;

		}
	    }
	    System.out.println("Started at " + now.toString() +
			       ", trace is in \"" + argv[0] + "\"\n");
	    simLog.print(now.toString() + "\n\n");
	    Exec.simulatorMode(simLog, inputScriptDir);

	    DiskOps ops = DiskOps.getDiskOps();

	    List disks = ops.getDiskPaths(HardwareProfile.DISKS_IDE);
	    for (Iterator d = disks.iterator(); d.hasNext(); ) {
		String disk = (String) d.next();

		String[] partitions = ops.getPartitionTable(disk);
		for (int j = 0; j < partitions.length; j++)
		    simLog.println(partitions[j]);
	    }

	}
	catch (Exception e) {
	    e.fillInStackTrace();
	    System.err.println("Exception: " + e.getMessage());
	    e.printStackTrace(System.err);
	    rc = 1;
	}
	finally {
	    if (simLog != System.out)
		simLog.close();
	}

	System.out.println("done");
	System.exit(rc);
    }
}

        /*
    abstract List getDiskPaths() throws IOException;

    abstract String getSerialNo(String device);

    public String[] getPartitionTable(String device) throws IOException;
    public void writePartitionTable(String device, String[] partitions)
	throws IOException;

    public void mkfs(String device, int fsType) throws IOException;
    public boolean fsck(String device, int fsType, boolean fixit)
        throws IOException;

    public void mount(String device, int fsType, String mountPoint)
	throws IOException;
    public void unmount(String deviceOrPath, int fsType)
	throws IOException;

    public void export(String path) throws IOException;
    public void unexport(String path) throws IOException;

    public Map getCurrentMounts() throws IOException;
    public Set getCurrentExports() throws IOException;

    public long df(String path) throws IOException;

    public void makeBootable(String device, String deviceRoot)
	throws IOException;

    public void makeVirtualDisk(String device, String filename,
				long sizeMB) throws IOException;

    public void remove(String filename) throws IOException;
    public void link(String filename, String linkname) throws IOException;

    public void unpack(String archiveName, String root) throws IOException;
        */
