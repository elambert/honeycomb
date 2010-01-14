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



// Overview
// --------
//
// This a set of library routines that might be useful for test code
// to call.
//
// A lot of stuff still needs to be re-worked and re-organized.  This
// is just a quick (and sloppy) first pass attempt to divide the functionality
// in the existing store/retrieve test to a common library that other test
// code can use.

package com.sun.honeycomb.hctest.sg;

import java.io.*;
import java.lang.*;
import java.util.*;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.hctest.util.HCUtil;

/**
 * Utility Routines class for some of the old tests.
 */
public class TestLibrary {

    // For logging verbosity
    public static boolean logInfoMsgs = true;
    public static boolean logDebugMsgs = false;
    public static boolean useLogClass = false;

    // Note this value must be smaller than MAXINT
    public static int maxAllocate = 1048576; // 1Mb limit for buffers
    public static boolean addNewLines = true; // add newlines to created files?
    public static int newLineFrequency = 1000; // how often to add newlines
    public static boolean useRandomBytes = true; // generate random/predictible
                                                 // bytes for file seeded with
                                                 // passed in char and size
    public static int newRandomByteFrequency = 3; // Change chars every N bytes
                                                  // This might help with
                                                  // diagnosing bit rot or
                                                  // other errors, but maybe
                                                  // not...

    /**
     * Tries to validate a path and create directories if needed.
     */
    public static void validatePath(String p) throws IOException {
        int index = p.lastIndexOf(File.separatorChar);
        if (index == -1) {
            printError("ERROR: Couldn't extract directory from " + p);
            printError("Make sure you are using an absolute path");
            die();
        }

        String dirname = p.substring(0, index);
        File d = new File(dirname);
        printDebug("Check: Path " + p + " directory " + d + " index " + index);
        if (!d.isDirectory() && !d.mkdirs()) {
            printError("ERROR: Couldn't create directory " + dirname);
            printError("for path " + p);
            die();
        }
    }

    /**
     * Create a random file for test
     */
    public static String createRandomFileForTest(long maxBytes) 
        throws IOException {

        // XXX size 0 doesn't work, so add 1 to make sure we don't get 0
        long filesize = (long)(Math.random() * maxBytes) + 1;
        int randint = (int)(Math.random() * Character.MAX_RADIX);
        char seedChar = Character.forDigit(randint, Character.MAX_RADIX);

        printInfo("Using size " + filesize + " and char seed of " + seedChar +
            " for random test file");
        return(createFileForTest(filesize, seedChar));
    }

    /**
     * Create a file with the given size and seed, but pick a name
     */
    public static String createFileForTest(long size, char seed)
        throws IOException {

        // Create a filename for this file
        String filename = "/tmp/hctest-" + size + "-" + seed + "-" +
            TestLibrary.msecsNow();

        printInfo("Creating file " + filename);

        boolean overwrite = false;
        createFile(filename, size, seed, overwrite);
        return (filename);
    }

    /**
     * Create a file with the given size and seed and name
     */
    public static void createFileForTest(long size, char seed, String name)
        throws IOException {

        printInfo("Creating file " + name);

        boolean overwrite = false;
        createFile(name, size, seed, overwrite);
    }

    /**
     * Creates (or overwrites) a file with the given name.
     * The file will be of size 'size' and contain the character 'c'.
     * If 'owrite' is true, then we'll overwrite an existing file.
     */
    public static void createFile(String name, long size, char c,
        boolean owrite) throws IOException {

        char[] buf;
        long bytesRemaining = size;
        Random r = null; // Used for generating random bytes in a file
        int randomByteCounter = 0; // Times we've repeated current byte
        char randomChar = 'a';  // current random number being used

        validatePath(name);
        File newfile = new File(name);

        // Test to see if file already exists
        printDebug("Checking to see if file " + newfile + " exists");
        if (!owrite && newfile.isFile()) {
            printError("ERROR: File " + newfile + " already exists");
            printError("Try setting the overwrite option to proceed");
            die();
        }

        // If we'll be using random bytes in the file contents, initialize
        // the random number generator with the character c * size as the seed
        if (useRandomBytes) {
            printDebug("Using random bytes for file contents.  Seed is " +
                c + "*" + size + "=" + ((long)c * size) + " (initchar*size)");
            r = new Random((long)c * size);
        }

        FileWriter fw = new FileWriter(newfile);
        if (size < maxAllocate) {
            buf = new char[(int)size];
        } else {
            // We'll loop to create the file
            buf = new char[maxAllocate];
        }

        for (int i = 0; i < buf.length; i++) {
            if (useRandomBytes) {
                // Check if we should repeat the same byte or generate a new
                // byte to be used
                if (++randomByteCounter >= newRandomByteFrequency) {
                    randomChar = Character.forDigit(((int)(r.nextDouble() *
                        Character.MAX_RADIX)), Character.MAX_RADIX);
                    randomByteCounter = 0;
                }

                buf[i] = randomChar;
            } else {
                // use just a constant, repeating character
                buf[i] = c;
            }
        }

        // Sometimes having a line that is really long makes it difficult
        // to use a file viewer/editor.  Optionally, we add newlines to the
        // file at occasional intervals to make editting it easier.
        if (addNewLines) {
            for (int i = 0; i < buf.length; i += newLineFrequency) {
                buf[i] = '\n';
            }
        }

        while (bytesRemaining > 0) {
            // Note that this causes an existing file to be truncated
            if (bytesRemaining > buf.length) {
                fw.write(buf, 0, buf.length);
                bytesRemaining -= buf.length;
            } else {
                fw.write(buf, 0, (int)bytesRemaining);
                bytesRemaining -= (int)bytesRemaining; // 0!
            }
        }
        fw.close();
    }

    /**
     * Rename the source file to the target name.
     */
    public static void renameFile(String source, String target) 
        throws IOException {
        File orig = new File(source); // XXX check exceptions
        File renamed = new File(target);
        printDebug("Renaming file " + source + " to " + target);
        if (!orig.renameTo(renamed)) {
            printError("ERROR: failed to rename file " +
                source + " to " + target);
            die();
        }
    }

    /**
     * Takes a given file and replicates that file to the new name.
     */
    public static void copyFile(String source, String target, boolean owrite) 
        throws FileNotFoundException, IOException {
        File inputFile = new File(source);
        File outputFile = new File(target);

        // Test to see if file already exists
        printDebug("Checking to see if file " + outputFile + " exists");
        if (!owrite && outputFile.isFile()) {
            printError("ERROR: File " + outputFile + " already exists");
            printError("Try setting the overwrite option to proceed");
            die();
        }

        FileInputStream in = new FileInputStream(inputFile);
        FileOutputStream out = new FileOutputStream(outputFile);
        int bytesRead;
        byte[] buf = new byte[maxAllocate];

        printDebug("Copying file " + source + " to " + target);
        while ((bytesRead = in.read(buf)) != -1) {
            out.write(buf, 0, bytesRead);
        }

        in.close();
        out.close();
    }

    /**
     * Adds the given string to the end of the given file.
     */
    public static void appendFile(String append, String filename) 
        throws IOException {
        printDebug("Appending " + append + " to " + filename);
        File appendFile = new File(filename);
        FileWriter out = new FileWriter(appendFile, true);
        out.write(append + "\n");
        out.close();
    }

    /**
     * Replaces the bytes starting from offset in the file with the
     * given string.
     */
    public static void replaceBytesInFile(String s, long offset,
        String filename) throws IOException {
        int len = s.length();
        File f = new File(filename);
        // XXX can this be optimized using memory mapped files?
        RandomAccessFile raf = new RandomAccessFile(f, "rw");

        printDebug("Replacing " + len + " bytes at position " + offset +
            " in file " + filename + " (filesize " + f.length() + " bytes)");

        raf.seek(offset);
        raf.writeBytes(s);
        raf.close();
    }

    /**
     * Get the file size.
     */
    public static long fileSize(String filename) throws IOException {
        File f = new File(filename);
        return (f.length());
    }

    /**
     * Get the current time in milliseconds.
     */
    public static long msecsNow() {
        return (System.currentTimeMillis());
    }

    /**
     * Compares two files for equivalence.
     */
    public static boolean isEquivalent(String f1, String f2) {
        boolean equiv;

        String f1Hash = getHash(f1);
        String f2Hash = getHash(f2);

        if (f1Hash.equals(f2Hash)) {
            printDebug("Files " + f1 + " and " + f2 + " are equivalent");
            equiv = true;
        } else {
            printError("Files " + f1 + " and " + f2 + " are NOT equivalent");
            printError("Hashes are");
            printError(f1 + ": " + f1Hash);
            printError(f2 + ": " + f2Hash);
            equiv = false;
        }

        return (equiv);
    }

    /**
     * Calculate the Hash id of a given file.
     */
    public static String getHash(String filename) {
        String hash = null;

        try {
            hash = HCUtil.computeHash(filename);
        } catch (Throwable t) {
            printError("Failed to compute hash " + t.getMessage());
            die();
        }

        return (hash);
    }

    /**
     * Prints the current date plus the given string.
     */
    public static void printDate(String s) {
        Date d = new Date();
        printInfo(s + d.toString());
    }

    /**
     * Pick a random item from an array.
     */
    public static String pickRandom(String items[]) {
        Random r = new Random();
        int i = items.length;
        String item = null;

        // printInfo("nextInt(" + i + ")=" + r.nextInt(i) + " " + r.nextInt(i));
        if (i > 0) {
            item = items[(r.nextInt(i))]; // selects values n, where 0 <= n < i
        }

        return (item);
    }

    //
    // Routines that handle error, info, and debugging messages
    //
    public static void printError(String s) {
        if (useLogClass) {
            Log.ERROR(s);
        } else {
            System.out.println(s);
        }
    }

    public static void printInfo(String s) {
        if (useLogClass) {
            Log.INFO(s);
        } else {
            if (logInfoMsgs) {
                System.out.println(s);
            }
        }
    }

    public static void printDebug(String s) {
        if (useLogClass) {
            Log.DEBUG(s);
        } else {
            if (logDebugMsgs) {
                System.out.println(s);
            }
        }
    }

    // XXX libraries shouldn't exit!  fix this...by returning error codes,
    // etc., but this is good enough for now, sigh.
    public static void die() {
        printDate("<--- Exiting test with ERROR at ");
        System.exit(1);
    }
}
