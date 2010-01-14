package com.sun.honeycomb.testcmd.common;

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



import java.util.*;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.QueryResultSet;
import com.sun.honeycomb.client.ObjectIdentifier;

public class HoneycombTestCmd {

    // someday replace these hard-coded values w/ 
    // numbers from interface
    public final int FRAG_SIZE = 64 * 1024; // 64k
    public final int N = 5; // Data fragments
    public final int M = 3; // Parity fragments
    public final int BLOCK_SIZE = N * FRAG_SIZE;
    public final int EXTENT_SIZE = 2731 * BLOCK_SIZE;

    public final int MD_FNAME = 0x01;
    public final int MD_FSIZE = 0x02;
    public final int MD_STOREDATE = 0x04;
    public final int MD_CLIENT = 0x08;
    public final int MD_SHA = 0x10;
    public final int MD_ALL = (MD_FNAME|MD_FSIZE|MD_STOREDATE|MD_CLIENT|MD_SHA);

    public HCTestLogIF log = new HCLogStdout();  // default log = stdout
    public RunCommand shell = new RunCommand();

    public boolean verbose = true;
    boolean debuggingEnabled = false;
    public String clnthost = null;

    public static boolean done = false;

    HoneycombTestClient htc = null;

    String clusterName = null;
    String nodeHost = null;
    String nodeHostIP = null;
    String server = null;

    File tmpDir = null;

    public static String lastfile = null;
    public static FileWriter fo = null;
    public static int active_host_ex = 0;
    public static int general_ex = 0;
    public static int opening_frags_ex = 0;
    public static int fragging_data_ex = 0;
    public static int io_ex = 0;
    public static int sha_ex = 0;
    public static int too_many_errors_ex = 0;

    // Maximum file read/write size -- this must stay less than INT_MAX
    private static int MAX_ALLOCATE = 1048576; // 1Mb limit for buffers

    // Random number class instance
    //    SecureRandom is used to be more sure that
    //    different test nodes have different
    //    random sequences. Only initted if needed, since
    //    creation can be slow.
    public SecureRandom rand = null;

    public void initHCClient(String server) throws HoneycombTestException {
        tryLocalTempDir();
        try {
            clnthost = InetAddress.getLocalHost().toString();
        } catch (Exception e) {
            clnthost = "local host unknown";
            e.printStackTrace();
        }
        this.server = server;
        htc = new HoneycombTestClient(server);
        htc.setDebug(debuggingEnabled);
    }

    public int getBlockSize() {
        return BLOCK_SIZE;
    }
    public int getExtentSize() {
        return EXTENT_SIZE;
    }

    // for cmd-line args
    public long parseSize(String s) throws NumberFormatException {

        String size = s.trim();
        long mult = 1;
        if (size.endsWith("k")  ||  size.endsWith("K")) {
            mult = 1024;
        } else if (size.endsWith("m")  ||  size.endsWith("M")) {
            mult = 1024 * 1024;
        } else if (size.endsWith("g")  ||  size.endsWith("G")) {
            mult = 1024 * 1024 * 1024;
        } else if (size.endsWith("f")  ||  size.endsWith("F")) {
            mult = FRAG_SIZE;
        } else if (size.endsWith("b")  ||  size.endsWith("B")) {
            mult = getBlockSize();
        } else if (size.endsWith("e")  ||  size.endsWith("E")) {
            mult = getExtentSize();
        }
        String size2 = size;
        if (mult != 1)
            size2 = size.substring(0, size.length()-1);
        long lsize = Long.parseLong(size2);
        lsize *= mult;
        return lsize;
    }

    // for status to terminal
    public static int dot_range = 70;
    private int dots = 0;
    public void dot(String s) {
        System.err.print(s);
        dots++;
        if (dots == dot_range) {
            System.err.println();
            dots = 0;
        }
    }
    public void closeDots() {
        if (dots > 0) {
            System.err.println();
            dots = 0;
        }
        System.err.flush();
    }
    public void finishDotRange(String s) {
        while (dots < dot_range) {
            System.err.print(s);
            dots++;
        }
        System.err.println();
        System.err.flush();
    }

    // for logging to file
    static public void flog(String s) {
        if (fo == null)
            return;
                                                                                
        try {
            fo.write(s);
            fo.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // for logging to file & stderr
    static public void flout(String s) {
        flog("#S " + s);
        System.err.print(s);
    }
    static public void flex(String s, Throwable t) {
        if (fo == null)
            return;
        String msg = t.getMessage();
        try {
            if (msg == null) {
                fo.write("#E " + new Date() + " " + s + ": no msg\n");
                fo.flush();
                stackTracePlus(t);
                return;
            }
            if (msg.indexOf("no active host available") != -1) {
                active_host_ex++;
                fo.write("#E " + new Date() + " " + s + ": no active host available\n");
                fo.flush();
                return;
            }
            if (msg.indexOf("Error opening fragments") != -1) {
                opening_frags_ex++;
                fo.write("#E " + new Date() + " " + s + ": " + msg + "\n");
                fo.flush();
                return;
            }
            if (msg.indexOf("Error fragmenting data") != -1) {
                fragging_data_ex++;
                fo.write("#E " + new Date() + " " + s + ": Error fragmenting data\n");
                fo.flush();
                return;
            }
            if (msg.indexOf("IOException") != -1) {
                io_ex++;
                if ((t=t.getCause()) != null)
                    fo.write("#E " + new Date() + " " + s + ": IOException (" +
					t.getMessage() + "\n");
                else
                    fo.write("#E " + new Date() + " " + s + ": IOException\n");
                fo.flush();
                return;
            }
            if (msg.indexOf("Failed to get sha1") != -1) {
                sha_ex++;
                fo.write("#E " + new Date() + " " + msg + "\n");
                fo.flush();
                return;
            }
            if (msg.indexOf("Too many errors:") != -1) {
                too_many_errors_ex++;
                fo.write("#E " + new Date() + " " + s + ": " + msg + "\n");
                fo.flush();
                return;
            }
            fo.write("#E " + new Date() + "\n");
            fo.flush();
            general_ex++;
            fo.write("#E " + s + ":\n");
            fo.write("#E " + msg + "\n");
            fo.flush();
            stackTracePlus(t);
        } catch (Exception e) {
            System.err.println("Exception logging exception:");
            e.printStackTrace();
        }
    }
    static private void stackTracePlus(Throwable t) {
        try {
            StackTraceElement[] stack = t.getStackTrace();
            for (int i=0; i<stack.length; i++) {
                String s2 = stack[i].toString();
                fo.write("#E     " + s2 + "\n");
                fo.flush();
                if (s2.indexOf("HoneycombTestCmd") != -1)
                    break;
            }
            while ((t = t.getCause()) != null) {
                String ss = t.getMessage();
                if (ss == null)
                    ss = t.toString();
                fo.write("#E   cause: " + ss + "\n");
                fo.flush();
            }
        } catch (Exception e) {
            System.err.println("Exception logging stack:");
            e.printStackTrace();
        }
    }
    public static void reportExceptions() {
        reportExceptions(null);
    }
    public static void reportExceptions(String s) {
        if (s == null)
            s = "";
        else
            s += " ";
        flout(s + "Exceptions (no host/other): " +
                    active_host_ex + " / " + general_ex + "\n");
        if (fragging_data_ex > 0)
            flout(s + "'Error fragmenting data' Exceptions: " +
                    fragging_data_ex + "\n");
        if (opening_frags_ex > 0)
            flout(s + "'Error opening fragments' Exceptions: " +
                    opening_frags_ex + "\n");
        if (io_ex > 0)
            flout(s + "IOExceptions: " + io_ex + "\n");
        if (sha_ex > 0)
            flout(s + "Sha exceptions: " + sha_ex + "\n");
        if (too_many_errors_ex > 0)
            flout(s + "'Too many errors' Exceptions: " +
                    opening_frags_ex + "\n");
    }

    // Store a file using the Honeycomb API, returns OID
    public String store(String filename) throws HoneycombTestException {
        return htc.store(filename);
    }

    // Create and store a file of a given size and returns the filename and oid
    public HoneycombCmdResult store(long size, NameValueRecord nvr)
        throws HoneycombTestException {
        HoneycombCmdResult hcr = new HoneycombCmdResult();
        hcr.filename = createRandomByteFile(size);
        if (nvr == null) {
            hcr.mdoid = store(hcr.filename);
        } else {
            hcr.mdoid = store(hcr.filename, nvr);
        }
        return (hcr);
    }

    // Create and store a file of a given size and returns the filename and oid
    public HoneycombCmdResult store(long size) throws HoneycombTestException {
        return (store(size, null));
    }

    public String store(String filename, NameValueRecord nvr) 
					throws HoneycombTestException {
        if (filename == null)
            throw new HoneycombTestException("store(): filename is null");
        if (nvr == null)
            throw new HoneycombTestException("store(): nvr is null");
        return htc.store(filename, nvr);
    }

    // delete an object using the Honeycomb API
    public void delete(String oid) throws HoneycombTestException {
        htc.delete(oid);
    }

    // Create the random number generator. SecureRandom is used
    // to minimize the chance of JVM's on separate hosts coming
    // up with the same number, since Random() uses system time
    // as seed. 
    public void initRandom() throws HoneycombTestException {
        if (rand == null) {
            FileInputStream fi = null;
            try {
                // get a seed from /dev/urandom - the default
                // is /dev/random, but it can block if the
                // system hasn't seen much activity yet:
                //
                //  "The /dev/random code presents two interfaces, 
                //   both character devices:
                //     /dev/random    high-quality, blocks when there is not
                //                    enough entropy available
                //     /dev/urandom    never blocks, may be lower quality"
                //
                // rather than change all of java to use /dev/urandom by
                // updating /usr/lib/java/lib/security/java.security,
                // we just get a seed from /dev/urandom explicitly 

                rand = SecureRandom.getInstance("SHA1PRNG");
                fi = new FileInputStream("/dev/urandom");
                byte[] seed = new byte[8];
                fi.read(seed);
                rand.setSeed(seed);
            } catch (Exception e) {
                throw new HoneycombTestException(e);
            } finally {
                if (fi != null) {
                    try {
                        fi.close();
                    } catch (Exception ignore) {}
                }
            }

            // log the 1st bytes so that collisions across
            // nodes can be detected in case there's a problem
            byte[] initBytes = new byte[8];
            rand.nextBytes(initBytes);

            if (debuggingEnabled) {
                StringBuffer sb = new StringBuffer();
                for (int i=0; i<8; i++)
                    sb.append(Integer.toString((initBytes[i] & 0xff) + 0x100, 16).substring(1)).append(" ");
                if (verbose)
                    log.log("INITIAL RANDOM = " + sb);
             }
        }
    }
    public int randIndex(int mod) {
        try {
            initRandom();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        int i = rand.nextInt();
        if (i < 0)
            i *= -1;
        return i % mod;
    }


    //
    //  test clients have no space to speak of on /tmp
    //
    final String defaultTmp = "/mnt/test";
    private void tryLocalTempDir() {
        File dir = new File(defaultTmp);
        if (dir.isDirectory()) {
            if (verbose)
                log.log("setting tmp dir to " + defaultTmp);
            setTempDir(dir);
        }
    }
    public void setTempDir(File dir) {
        tmpDir = dir;
    }
    public String createTempFile() throws HoneycombTestException {
        File file;
                                                                                
        try {
            if (tmpDir == null)
                file = File.createTempFile("hctest-", "-tmp");
            else
                file = File.createTempFile("hctest-", "-tmp", tmpDir);
        } catch(IOException iox) {
            throw new HoneycombTestException(iox);
        }
        lastfile = file.toString();
        return lastfile;
    }

    public byte[] getRandomBytes(int size) throws HoneycombTestException {
        initRandom();
        
        byte randomBytes[] = new byte[size];
        rand.nextBytes(randomBytes);
        return randomBytes;
    }

    // Create a file of random bytes.
    public String createRandomByteFile(long size) 
					throws HoneycombTestException {
        long bytesRemaining = size;
        String filename;

        filename = createTempFile();

        FileOutputStream fout = null;

        try {
            fout = new FileOutputStream(filename);
            byte randomBytes[];

            if (size < MAX_ALLOCATE) {
                // The cast to int of size is safe because MAX_ALLOCATE
                // is less than MAX_INT
                randomBytes = getRandomBytes((int)size);
            } else {
                randomBytes = getRandomBytes(MAX_ALLOCATE);
            }

            while (bytesRemaining > 0) {

                if(bytesRemaining > randomBytes.length) {
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

        return filename;
    }

    private void extendBinaryFile(String filename, long len, long size) 
						throws HoneycombTestException {
                                                                                
        long bytesRemaining = size - len;
        byte randomBytes[];
        if (bytesRemaining < MAX_ALLOCATE) {
            // The cast to int of size is safe because MAX_ALLOCATE
            // is less than MAX_INT
            randomBytes = getRandomBytes((int)bytesRemaining);
        } else {
            randomBytes = getRandomBytes(MAX_ALLOCATE);
        }
        FileOutputStream fout = null;
        try {
            fout = new FileOutputStream(filename, true);
            while (bytesRemaining > 0) {
                                                                                
                if(bytesRemaining > randomBytes.length) {
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
    }

    // random string of letters and numbers (no punctuation
    // so it can be a directory name in an hc view)
    public char[] getRandomChars(int size) throws HoneycombTestException {

        initRandom();

        char randomChars[] = new char[size];

        for (int i = 0; i < randomChars.length; i++) {
            char c = 0;
            while (c == 0) {
                int randNum = rand.nextInt();
                if (randNum < 0)
                    randNum *= -1;
                randNum %= Character.MAX_RADIX;
                c = Character.forDigit(randNum, Character.MAX_RADIX);
                if (Character.isLetterOrDigit(c)) {
                    randomChars[i] = c;
                    break;
                }
                c = 0;
            }
        }

        return randomChars;
    }

    // Create a file of a repeated random sequence of human-readable 
    // characters to store. 
    public String createRandomCharFile(long size) 
					throws HoneycombTestException {
        return createRandomCharFile(size, MAX_ALLOCATE);
    }
    public String createRandomCharFile(long size, int repeat) 
					throws HoneycombTestException {
        long bytesRemaining = size;
        String filename;

        filename = createTempFile();

        FileWriter filewriter = null;
        try {
            filewriter = new FileWriter(filename);
            char randomChars[];

            if (size < repeat) {
                // The cast to int of size is safe because MAX_ALLOCATE
                // is less than MAX_INT
                randomChars = getRandomChars((int)size);
            } else {
                randomChars = getRandomChars(repeat);
            }

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
            log.log("createRandomCharFile [" + filename + "] size=" +size);
        return filename;
    }

    public void extendCharFile(String filename, long current, long size) 
							throws Exception {
        long bytesRemaining = size - current;
        int alloc = (bytesRemaining < MAX_ALLOCATE ? (int)bytesRemaining : 
							MAX_ALLOCATE);
        char[] randomChars = getRandomChars(alloc);
        FileWriter filewriter = null;
        try {
           filewriter = new FileWriter(filename, true); //append
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
    }

    private void changeBytes(File f) throws HoneycombTestException {
        try {
            RandomAccessFile raf = new RandomAccessFile(f, "rw");
            int buf_size = 1024;
            if (f.length() < 1024)
                buf_size = (int)f.length();
            char randomChars[] = getRandomChars(buf_size);
            byte b[] = new byte[buf_size];
            for (int i=0; i<buf_size; i++)
                b[i] = (byte) randomChars[i];
            raf.write(b);
            raf.close();
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
    }

    // create file and store to cluster, returning name and OID 
    // and optional sha1sum in String array, and putting time 
    // to store in 'time' array if not null
    public String[] createFileAndStore(long filesize, boolean binary,
                                int base_metadata, NameValueRecord nvr,
				long[] time, int time_i)
                                        throws HoneycombTestException {
        return createFileAndStore(filesize, binary, base_metadata, nvr, false,
				time, time_i);
    }

    // append to old file if present
    public String[] createFileAndStore(String ss[], long filesize, 
				boolean binary, int base_metadata, 
				NameValueRecord nvr, boolean sha,
                                long[] time, int time_i)
                                        throws HoneycombTestException {
        if (ss == null  ||  ss[0] == null)
            return createFileAndStore(filesize, binary, base_metadata, nvr, sha,
				time, time_i);
        String name = ss[0];
        String name2 = null;
        try {
            // make sure file is ok
            File f = new File(name);
            if (!f.exists()) {
                // in case someone removed it
                return createFileAndStore(filesize, binary, base_metadata, 
						nvr, sha, time, time_i);
            }
            if (!f.isFile()) {
                throw new HoneycombTestException(name + ": not a file");
            }
            long len = f.length();
            if (len > filesize) {
                throw new HoneycombTestException(name + 
						": length > requested");
            }
            // rename for uniqueness
            name2 = createTempFile();
            File f2 = new File(name2);
            f2.delete();
            f.renameTo(f2);
            ss[0] = name2; // update the array so that if we fail we don't
                           // try and use the old name
            if (len == filesize) {
                changeBytes(f2);
            } else {
                if (binary)
                    extendBinaryFile(name2, len, filesize);
                else
                    extendCharFile(name2, len, filesize);
            }
        } catch (Exception e) {
            throw new HoneycombTestException("Extending file: " + e);
        }
        return storeFile(name2, filesize, base_metadata, nvr, sha, 
							time, time_i);
    }
    public String[] createFileAndStore(long filesize, boolean binary,
                                int base_metadata, NameValueRecord nvr,
				boolean sha, long[] time, int time_i)
                                        throws HoneycombTestException {
        String filename;
        if (binary)
                filename = createRandomByteFile(filesize);
        else
                filename = createRandomCharFile(filesize);

        if (verbose)
                log.log("created size=" + filesize + " [" + filename + 
						"]  storing..");
        try {
            return storeFile(filename, filesize, base_metadata, nvr, sha, 
								time, time_i);
        } catch (HoneycombTestException e) {
            deleteFile(filename);
            throw e;
        }
    }

    public String baseMD() {
        return "string filename, string filesize, string storedate, string client, string sha1";
    }

    String[] storeFile(String filename, long filesize,
						int base_metadata, 
						NameValueRecord nvr,
						boolean sha,
						long time[], int time_i)
						throws HoneycombTestException {
        String oid = null;
        String sha1sum = null;
        String date = (new Date()).toString();

        if (sha  ||  ((base_metadata & MD_SHA) != 0))
            sha1sum = shell.sha1sum(filename);
        long t1;
        if (base_metadata != 0  ||  nvr != null) {

            NameValueRecord nvr2;
            if (nvr == null)
                nvr2 = htc.createRecord();
            else
                nvr2 = copyNameValueRecord(nvr);

            if ((base_metadata & MD_FNAME) != 0)
                nvr2.put("filename", filename);
            if ((base_metadata & MD_FSIZE) != 0)
                nvr2.put("filesize", Long.toString(filesize));
            if ((base_metadata & MD_STOREDATE) != 0)
                nvr2.put("storedate", date);
            if ((base_metadata & MD_CLIENT) != 0)
                nvr2.put("client", clnthost);

            if (sha  &&  (base_metadata & MD_SHA) != 0)
                nvr2.put("sha1", sha1sum);

            t1 = System.currentTimeMillis();
            oid = store(filename, nvr2);
        } else {
            // no metadata
            t1 = System.currentTimeMillis();
            oid = store(filename);
        }
        t1 = System.currentTimeMillis() - t1;
        if (verbose)
            log.log("stored as oid " + oid + " time=" + t1);
        if (time != null)
            time[time_i] = t1;

        String[] s = new String[4];
        s[0] = filename;
        s[1] = oid;
        s[2] = sha1sum;
        s[3] = date;
        return s;
    }

    // Create and store a file of a given size and returns the filename and oid
    public HoneycombCmdResult addSimpleMetadata(String oid, NameValueRecord nvr)
        throws HoneycombTestException {
        HoneycombCmdResult hcr = new HoneycombCmdResult();
        hcr.mdoid = htc.addSimpleMetadata(oid, nvr).getObjectIdentifier().toString();
        return (hcr);
    }

    public Object[] addMetadata(String oid, NameValueRecord nvr,
						long[] time, int time_i) 
					throws HoneycombTestException {
        long t1 = System.currentTimeMillis();
        Object[] oo = htc.addMetadata(oid, nvr);
        t1 = System.currentTimeMillis() - t1;
        if (verbose)
            log.log("addmetadata done, time=" + t1);
        if (time != null)
            time[time_i] = t1;
        return oo;
    }

    protected NameValueRecord createRecord(){
	return htc.createRecord();
    }

    public Object[] addMetadata(ObjectIdentifier oid, NameValueRecord nvr,
                                                long[] time, int time_i)
                                        throws HoneycombTestException {
        long t1 = System.currentTimeMillis();
        Object[] oo = htc.addMetadata(oid, nvr);
        t1 = System.currentTimeMillis() - t1;
        if (verbose)
            log.log("addmetadata done, time=" + t1);
        if (time != null)
            time[time_i] = t1;
        return oo;
    }

    // retrieve OID obj to new file, return filename, put time in optional
    // array
    public String retrieve(String oid, long[] time, int time_i) 
					throws HoneycombTestException {

        String retrieveFile = createTempFile();

        long t1 = System.currentTimeMillis();
        try {
            htc.retrieve(oid, retrieveFile);
        } catch (HoneycombTestException e) {
            deleteFile(retrieveFile);
            throw e;
        }
        t1 = System.currentTimeMillis() - t1;
        if (verbose)
            log.log("retrieve done, time=" + t1);
        if (time != null)
            time[time_i] = t1;
        return retrieveFile;
    }

    // retrieve OID to the named file
    public void retrieve(String oid, String filename) 
    throws HoneycombTestException {
            htc.retrieve(oid, filename);
    }

    public NameValueRecord retrieveMetadata(String oid, 
						long[] time, int time_i)
                                        throws HoneycombTestException {
        long t1 = System.currentTimeMillis();
        NameValueRecord nvr = htc.retrieveMetadata(oid);
        t1 = System.currentTimeMillis() - t1;
        if (verbose)
            log.log("retrievemetadata done, time=" + t1);
        if (time != null)
            time[time_i] = t1;

        return nvr;
    }

    // some systems don't have sha1sum installed so compute the sha1sum
    // via the java library
    public static String computeHash(String filename) 
        throws HoneycombTestException {
        String hashAlg = "SHA";
        String computedHash = null;
        try {
            MessageDigest md = MessageDigest.getInstance(hashAlg);
            File f = new File(filename);
            long len = f.length();
            FileInputStream in = new FileInputStream(f);
            int bytesRead;
            int bufsize = (int) (len > MAX_ALLOCATE ? MAX_ALLOCATE : len);
            byte[] buf = new byte[bufsize];

            while ((bytesRead = in.read(buf)) != -1) {
                md.update(buf, 0, bytesRead);
            }
            in.close();

            // convert the bytes to a string.
            // XXX This code was copied from OA.
            byte[] rawHash = md.digest();
            StringBuffer sbHash = new StringBuffer();
            for(int i = 0; i < rawHash.length; i++) {
                sbHash.append(Character.forDigit((rawHash[i] & 0xF0) >> 4, 16));
                sbHash.append(Character.forDigit(rawHash[i] & 0x0F, 16));
            }
            computedHash = sbHash.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new HoneycombTestException("Couldn't compute " + hashAlg +
                ":  No such algorithm");
        } catch (IOException e) {
            throw new HoneycombTestException("Couldn't compute " + hashAlg +
                ": " + e.getMessage());
        }

        return (computedHash);
    }

    public void verifyFilesMatch(String f1, String f2)
    throws HoneycombTestException {
        String f1sha = computeHash(f1);
        String f2sha = computeHash(f2);
        // logger.info(f1 + " has sha1sum of " + f1sha);
        // logger.info(f2 + " has sha1sum of " + f2sha);
        if (!f1sha.equals(f2sha)) {
            throw new HoneycombTestException(f1 + " has sha1sum " + f1sha +
                "; " + f2 + " has sha1sum " + f2sha);
        }
    }

    public QueryResultSet query(String query, int maxResults)
        throws HoneycombTestException {
        return (htc.query(query, maxResults));
    }

    public QueryResultSet query(String query) throws HoneycombTestException {
        return (htc.query(query, HoneycombTestClient.USE_DEFAULT_MAX_RESULTS));
    }

    public QueryResultSet query(String query, long[] time, int time_i)
					throws HoneycombTestException {
        QueryResultSet qr;

        long t1 = System.currentTimeMillis();
        qr = htc.query(query, HoneycombTestClient.USE_DEFAULT_MAX_RESULTS);
        t1 = System.currentTimeMillis() - t1;
        if (verbose)
            log.log("query done, time=" + t1);
        if (time != null)
            time[time_i] = t1;

        return qr;
    }

    // Delete a file
    public void deleteFile(String filename) {
        if (filename != null) {
            File f = new File(filename);
            if (!f.exists()) {
                log.log("Failed to delete file " + filename +
                            ": does not exist");
            } else if ( f.delete() == false) {
                log.log("Failed to delete file " + filename);
            }
        }
    }

    // Delete files that start with the same base filename of the filename
    public void deleteFilesStartingWithPattern(String filename) {
        File f = new File(filename);
        File d = f.getParentFile();
        File list[] = d.listFiles();

        //tetInfoline("Deleting files with pattern " + filename);
        for (int i = 0; i < list.length; i++) {
            if (list[i].toString().startsWith(filename)) {
                if (!list[i].delete()) {
                    log.log("Failed to delete file " + list[i]);
                }
            }
        }
    }

    // Sleep the given number of milliseconds
    public void sleep(long msecs) {
        try {
            if (msecs > 0) {
                log.log("Sleeping for " + msecs + " msecs");
            }
            Thread.sleep(msecs);
        } catch (InterruptedException e) {
            log.log("Sleep " + msecs + " Interrupted: " + e.getMessage());
        }
    }

    // copy.. note that SystemRecord isn't copied
    public NameValueRecord copyNameValueRecord(NameValueRecord nvr) {
        NameValueRecord new_nvr = htc.createRecord();
        String[] keys = nvr.getKeys();
        for (int i=0; i<keys.length; i++)
            new_nvr.put(keys[i], nvr.getAsString(keys[i]));
        return new_nvr;
    }

    public long timeLs(String fname) throws HoneycombTestException {
        return timeLs(fname, -1);
    }
    public long timeLs(String fname, int n_expected) throws HoneycombTestException {
        long t1 = System.currentTimeMillis();
        List l = shell.ls(fname);
        if (n_expected > -1  &&  l.size() != n_expected) {
            throw new HoneycombTestException("Expected " + n_expected +
				" entries, got " + l.size());
        }
        return System.currentTimeMillis() - t1;
    }

    public long timeCat(String fname) throws HoneycombTestException {
        long t1 = System.currentTimeMillis();
        shell.catnull(fname);
        return System.currentTimeMillis() - t1;
    }

    public class LineReader {
        private BufferedReader in = null;
        public LineReader(String fname) throws HoneycombTestException {
            try {
                if (fname.equals("-"))
                    in = new BufferedReader(new InputStreamReader(
						System.in));
                else
                    in = new BufferedReader(new FileReader(fname));
            } catch (FileNotFoundException e) {
	        throw new HoneycombTestException("File not found: " + fname);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        public void close() {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignore) {}
            }
        }
        public String getline() {
            String s = null;
            try {
                s = in.readLine();
                if (s != null) {
                    while (s.startsWith("# ")) {
                        s = in.readLine();
                        if (s == null)
                            break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
            if (s == null)
                return null;
            return s.trim();
        }
    }

    public class ObjectCounter {
        HashMap map = new HashMap();

        private class Count implements Comparable {
            String name;
            int value = 1;
            public Count(String name) {
                this.name = name;
            }
            public void increment() {
                value++;
            }
            public int compareTo(Object o) {
                if (! (o instanceof Count))
                    return -1;
                Count c = (Count) o;
                if (c.value < this.value)
                    return -1;
                if (c.value > this.value)
                    return 1;
                return 0;
            }
        }

        public void count(Object o) {
            Count c = (Count) map.get(o);
            if (c == null) {
                map.put(o, new Count(o.toString()));
                return;
            }
            c.increment();
        }

        public StringBuffer sort() {
            StringBuffer sb = new StringBuffer();
            Object oo[] = map.values().toArray();
            Arrays.sort(oo);   
            for (int i=0; i<oo.length; i++) {
                Count c = (Count) oo[i];
                sb.append(c.value).append('\t').append(c.name).append('\n');
            }
            return sb;
        }
    }
}
