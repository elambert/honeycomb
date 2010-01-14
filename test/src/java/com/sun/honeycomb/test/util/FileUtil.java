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



package com.sun.honeycomb.test.util;

import java.util.*;
import java.io.*;
import java.security.SecureRandom;

import java.util.logging.Logger;

/**
 *  Convenience static routines for creating/extending/uniqu'ing files.
 *
 *  Important: because Log/LogArchive singleton objects call routines
 *  from FileUtil to createTempFile, createTempDir, in their constructors, 
 *  FileUtil must not log messages using Log object, else we have a loop.
 *  If you absolutely need to log something, use System.out/System.err.
 */

public class FileUtil {

    public static final String TMP_FILE_PREFIX = "hctest-";

    // Maximum file read/write size -- this must stay less than INT_MAX
    private static int MAX_ALLOCATE = 1048576; // 1Mb limit for buffers

    private static boolean verbose = false;
    private static final String defaultTmp = "/mnt/test";
    private static File tmpDir = null;

    //
    //  test clients have no space to speak of on /tmp
    //
    public static void tryLocalTempDir() {
        File dir = new File(defaultTmp);
        try {
            if (dir.isDirectory()) {
                setTempDir(dir);
            } else {
                dir = new File(System.getProperty("java.io.tmpdir"));
                if (dir.isDirectory()) 
                    setTempDir(dir);
            }
        } catch (SecurityException se) {;}
    }

    // instead of tryLocalTempDir, user can setTempDir directly
    //
    public static void setTempDir(File dir) {
        tmpDir = dir;
    }
    
    public static File getTempDir() {
        return tmpDir;
    }

    /**
     *  Rename file to a new temp filename.
     */
    private static File renameFile(File f) throws HoneycombTestException {
        File f2 = createTempFile();
        f2.delete();
        f.renameTo(f2);
        return f2;
    }

    /**
     *  Create a new empty file with a unique name in default tmp directory
     */
    public static File createTempFile() throws HoneycombTestException {
        return createTempFile(TMP_FILE_PREFIX, "-tmp");
    }

    public static File createTempFile(String prefix, String postfix) 
                                                throws HoneycombTestException {
        if (tmpDir == null) 
            tryLocalTempDir(); // init
        File f;
        try {
            if (tmpDir != null)
                f = File.createTempFile(prefix, postfix, tmpDir);
            else 
                f = File.createTempFile(prefix, postfix);
        } catch(IOException iox) {
            throw new HoneycombTestException(iox);
        }
        return f;
    }

    public static File createTempDir(String subpath) 
                                                throws HoneycombTestException {
        if (tmpDir == null) 
            tryLocalTempDir(); // init
        if (tmpDir == null)
            throw new HoneycombTestException("Base temp directory is not set, cannot create temp dir: " + subpath);
        
        File tmpDirF = new File(tmpDir.getAbsolutePath() + subpath);
        try {
            tmpDirF.mkdir();
        } catch (SecurityException se) {
            throw new HoneycombTestException("Failed to create temp dir: " + subpath + " : " + se);
        }
        return tmpDirF;
    }

    /**
     *  Filename version of createTempFile().
     */
    public static String getTmpFilename() throws HoneycombTestException {
        File f = createTempFile();
        return f.getPath();
    }

    /**
     *  Create a file of random bytes.
     */
    public static File createRandomByteFile(long size) 
                                                throws HoneycombTestException {

        long bytesRemaining = size;
        File f = createTempFile();

        if (size == 0)
            return f;

        FileOutputStream fout = null;

        try {
            fout = new FileOutputStream(f);
            byte randomBytes[];

            int alloc_size;
            if (size < MAX_ALLOCATE) {
                // The cast to int of size is safe because MAX_ALLOCATE
                // is less than MAX_INT
                alloc_size = (int)size;
            } else {
                alloc_size = MAX_ALLOCATE;
            }
            randomBytes = RandomUtil.getRandomBytes(alloc_size);

            while (bytesRemaining > 0) {

                if (bytesRemaining > randomBytes.length) {
                    fout.write(randomBytes);
                    bytesRemaining -= randomBytes.length;
                } else {
                    // The cast to int of bytesRemaining is safe because
                    // randomBytes.length is less than MAX_INT
                    fout.write(randomBytes, 0, (int)bytesRemaining);
                    bytesRemaining -= bytesRemaining;
                }
            }

        } catch(IOException iox) {
            throw new HoneycombTestException(iox);
        } finally {
            if (fout != null) {
                try {
                    fout.close();
                } catch (Exception ignore) {}
            }
        }

        return f;
    }

    /**
     *  Extend binary file with random data.
     */
    public static File extendBinaryFile(File f, long curr_size, long size) 
                                                throws HoneycombTestException {
                                                                                
        long bytesRemaining = size - curr_size;
        byte randomBytes[];
        int alloc_size;
        if (bytesRemaining < MAX_ALLOCATE) {
            // The cast to int of size is safe because MAX_ALLOCATE
            // is less than MAX_INT
            alloc_size = (int)bytesRemaining;
        } else {
            alloc_size = MAX_ALLOCATE;
        }
        randomBytes = RandomUtil.getRandomBytes(alloc_size);
        FileOutputStream fout = null;
        try {
            fout = new FileOutputStream(f, true);
            while (bytesRemaining > 0) {

                if (bytesRemaining > randomBytes.length) {
                    fout.write(randomBytes);
                    bytesRemaining -= randomBytes.length;
                } else {
                    // The cast to int of bytesRemaining is safe because
                    // randomBytes.length is less than MAX_INT
                    fout.write(randomBytes, 0, (int)bytesRemaining);
                    bytesRemaining -= bytesRemaining;
                }
            }
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        } finally {
            if (fout != null) {
                try {
                    fout.close();
                } catch (Exception e) {}
            }
        }
        return renameFile(f);
    }

    /**
     *  Create a file of a repeated random sequence of human-readable 
     *  characters. 
     */
    public static File createRandomCharFile(long size) 
                                                throws HoneycombTestException {
        return createRandomCharFile(size, MAX_ALLOCATE);
    }

    /**
     *  Create a file of a repeated random sequence (of a given size)
     *  of human-readable characters. 
     */
    public static File createRandomCharFile(long size, int repeat) 
                                                throws HoneycombTestException {
        long bytesRemaining = size;
        File f = createTempFile();

        if (size == 0)
            return f;

        FileWriter filewriter = null;
        try {
            filewriter = new FileWriter(f);
            char randomChars[];

            int alloc_size;
            if (size < repeat) {
                // The cast to int of size is safe because MAX_ALLOCATE
                // is less than MAX_INT
                alloc_size = (int)size;
            } else {
                alloc_size = repeat;
            }
            randomChars = RandomUtil.getRandomChars(alloc_size);

            while (bytesRemaining > 0) {
                if(bytesRemaining > randomChars.length) {
                    filewriter.write(randomChars, 0, randomChars.length);
                    bytesRemaining -= randomChars.length;
                } else {
                    // The cast to int of bytesRemaining is safe because
                    // randomChars.length is less than MAX_INT
                    filewriter.write(randomChars, 0, (int)bytesRemaining);
                    bytesRemaining -= bytesRemaining;
                }
            }

        } catch(IOException iox) {
            throw new HoneycombTestException(iox);
        } finally {
            if (filewriter != null) {
                try {
                    filewriter.close();
                } catch (Exception ignore) {}
            }
        }

        if (verbose)
            Log.INFO("createRandomCharFile [" + f + "] size=" +size);

        return f;
    }

    /**
     *  Extend a file with random human-readable characters.
     */
    public static File extendCharFile(File f, long cur_size, long size) 
                                                throws HoneycombTestException {
        long bytesRemaining = size - cur_size;
        int alloc = (bytesRemaining < MAX_ALLOCATE ? (int)bytesRemaining : 
                                                        MAX_ALLOCATE);
        char[] randomChars = RandomUtil.getRandomChars(alloc);
        FileWriter filewriter = null;
        try {
           filewriter = new FileWriter(f, true); //append
           while (bytesRemaining > 0) {
                if (bytesRemaining > randomChars.length) {
                    filewriter.write(randomChars, 0, randomChars.length);
                    bytesRemaining -= randomChars.length;
                } else {
                    // The cast to int of bytesRemaining is safe because
                    // randomChars.length is less than MAX_INT
                    filewriter.write(randomChars, 0, (int)bytesRemaining);
                    bytesRemaining -= bytesRemaining;
                }
            }

        } catch (Exception e) {
            throw new HoneycombTestException(e);
        } finally {
            if (filewriter != null) {
                try {
                    filewriter.close();
                } catch (Exception e) {}
            }
        }
        return renameFile(f);
    }

    /**
     *  Change bytes in a file to make it unique.
     */
    public static File changeBytes(File f, boolean binary) 
                                                throws HoneycombTestException {
        try {
            RandomAccessFile raf = new RandomAccessFile(f, "rw");
            int buf_size = 1024;
            if (f.length() < 1024)
                buf_size = (int)f.length();
            byte b[] = null;
            if (binary) {
                b = RandomUtil.getRandomBytes(buf_size);
            } else {
                b = new byte[buf_size];
                char randomChars[] = RandomUtil.getRandomChars(buf_size);
                for (int i=0; i<buf_size; i++)
                    b[i] = (byte) randomChars[i];
            }
            raf.write(b);
            raf.close();
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
        return renameFile(f);
    }
}
