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



//
// Tool to test if files have been stored reliably in OA. Ideally, this 
// tool would tell the user which fragment of a file is corrupted. Since
// fragments don't have checksums associated with them currently, this
// is not possible. Instead, for now, the tool does two things:
// 1. For a given file, see if the file can be reconstructed correctly
//    from only its data fragments. This is equivalent to retrieving
//    a file from the object archive when no data fragments are missing.
// 2. For the same file, see if it can be reconstructed correctly using
//    all the parity fragments.
//
// Finally, if both (1) and (2) succeed, we do a diff between the files 
// obtained in both cases to make sure they are the same.
//
// Note that we choose not to verify all possible combinations of data and
// parity fragments because that is an exponentially large set of combinations.
// 
// IMPORTANT: This test does not use Honeycomb's client protocol to test
// the reliability of files stored in the Object Archive. It instead uses
// the Object Archive directly.
//
// TODO: automatically run this for all objects in the system. Currently
// there is no reasonable way to list all objects in the system.
// XXX Note that this version of the program makes an assumption in
// oa/Transformer.java that the first "n" data fragments are ok. It is
// a bug to assume that one can reconstruct a file with "m" parity fragments
// and "n-m" data fragments. It's possible that some of the parity fragments
// are missing and that one needs to use more than "n-m" data fragments to
// reconstruct the file. This should be fixed in the next version of this
// program.
//
// USAGE:
// setenv LD_LIBRARY_PATH /opt/honeycomb/lib 
// Or one can use the format java -Djava.library.path=/opt/honeycomb/lib ...
//
// java -classpath /honeycomb/test/dist/honeycomb-test.jar:\
//  /opt/honeycomb/lib/honeycomb-client.jar:/opt/honeycomb/lib/honeycomb.jar\
//  com.sun.honeycomb.test.TestDataReliability object_id
//

package com.sun.honeycomb.hctest.sg;

import java.io.*;
import java.util.*;
import com.sun.honeycomb.oa.*;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.*;
import java.nio.channels.FileChannel;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.io.IOException;

/**
 * Test if a file stored in Honeycomb can be reconstructed using
 * (a) no parity fragments
 * (b) all parity fragments
 * If the file can be reconstructed in both cases, we do a diff to
 * make sure the contents are the same in both cases.
 */
public class TestDataReliability {

	private static long startTime;
	private static long endTime;
	private static String ObjectName=null; //ObjectName==object ID of a file
	
    public static void main(String args[]) throws IOException {

	if (args.length != 1) {
			System.err.println	("Usage:\n" +
			     "java TestDataReliability <oid>");
			System.exit(1);		
	}

	ObjectName = args[0];
	String[] fileName=new String[3];
	fileName[1] = "/tmp/" + ObjectName + ".1";
	fileName[2] = "/tmp/" + ObjectName + ".2";

	for (int i = 1; i <=2; i++) {
	TestLibrary.printDate("\n---> Starting phase " + i + " on  ");
	startTime = TestLibrary.msecsNow();

	boolean err = verifyFile(ObjectName, i, fileName[i]);

	endTime = TestLibrary.msecsNow();
	System.out.println("Time taken to verify " + ObjectName + " is " +
	     (endTime-startTime) + " milliseconds");

	if (err) {
		System.out.println("Object " + ObjectName +
		    " cannot be succesfully retrieved");
		System.exit(1);
	} else {
		System.out.println("Object " + ObjectName +
		     " was successfully retrieved");
	}
    	}

	//
	// We were able to successfully retrieve the files in both cases.
	// Do a diff to see if the files are the same.
	//	

	try {
		Process diff = 
			Runtime.getRuntime().exec("diff " + fileName[1] + " " +
			    fileName[2]);

		if (diff.waitFor() != 0) {
			System.out.println("diff of file retrieved from" +
			    " parity fragments" +
			    " is DIFFERENT from original file");
			System.exit(1);
		} else {
			System.out.println("Good - no difference between files"
			  + " obtained with and without parity fragments");
		}
	} catch (Throwable t) {
		t.printStackTrace();
		System.out.println("diff failed");
		System.exit(1);
	}

	try {
		Process rm =
			Runtime.getRuntime().exec("rm " + fileName[1] + " " +
			    fileName[2]);
	} catch (Throwable t) {
		t.printStackTrace();
		System.out.println("Unable to rm temp files");
		System.exit(1);
	}
	System.exit(0);
   }	

	public static boolean verifyFile(String ObjectID, int phase, String fn)
		throws IOException {

		WritableByteChannel channel = null;
		NewObjectIdentifier oid = null;
		long length = 0;

// BROKEN
//		oid = new NewObjectIdentifier(ObjectID);

		try {
			File file = new File(fn);
			FileOutputStream stream = new FileOutputStream(file);
			channel = Channels.newChannel(stream);
		} catch (IOException e) {
			System.out.println("Unable to open file " + e.getMessage());
			return (true);
		}

		try {
			if (phase == 2) {
// BROKEN
// 				ObjectArchive.getInstance().setTestMode();
			} else {
// BROKEN
// 				ObjectArchive.getInstance().unsetTestMode();
			}
// BROKEN
// 			length = ObjectArchive.getInstance().retrieve(channel, oid);
			System.out.println("Length retrieved is " + length);
// BROKEN
// 		} catch (NoSuchObjectException e) {
// 			System.out.println("Object " + ObjectID +
// 			    " does not exist" + e.getMessage());
// 			return (true);
// 		} catch (ObjectCorruptedException e) {
// 			System.out.println("Object " + ObjectID +
// 			    " is corrupted - cannot reconstruct" +
// 			     e.getMessage());
// 			return (true);
// 		} catch (ArchiveException e) {
// 			System.out.println("ArchiveException " +
// 			     e.getMessage());
// 			return (true);
		} catch (InternalException e) {
			System.out.println("InternalException " +
			    e.getMessage());
			return (true);
		}
		return (false);
	}
}
