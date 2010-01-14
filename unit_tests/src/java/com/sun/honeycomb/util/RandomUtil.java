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



package com.sun.honeycomb.util;

import java.io.FileInputStream;
import java.security.SecureRandom;
import java.util.StringTokenizer;

/**
 *  A convenience class for random functions, adapted from honeycomb.test.util.
 *
 *  SecureRandom is used for the generator to minimize the 
 *  chance of JVM's on separate hosts coming up with the same 
 *  number, which is more likely than one would think for
 *  parallel hosts, since Random() uses system time as seed. 
 */
public class RandomUtil {

    static final String GENERATOR = "SHA1PRNG";

    //
    //  get a seed from /dev/urandom - the default
    //  is /dev/random, but it can block if the
    //  system hasn't seen much activity yet:
    //
    //  "The /dev/random code presents two interfaces, 
    //   both character devices:
    //     /dev/random    high-quality, blocks when there is not
    //                    enough entropy available
    //     /dev/urandom   never blocks, may be lower quality"
    //
    //  rather than change all of java to use /dev/urandom by
    //  updating /usr/lib/java/lib/security/java.security,
    //  we just get a seed from /dev/urandom explicitly 
    //
    static final String DEFAULT_SEED_SOURCE = "/dev/urandom";
    static final int SEED_SIZE = 8; // sizeof(long) in bytes

    private static SecureRandom rand = null;
    private static String seedString = null;

    /**
     *  Get last seed used.
     */
    public static String getSeed() throws Exception {
        initRandom();
        return seedString;
    }

    /**
     * Initialize the generator with known random seed to reproduce a run.
     */
    public static void initRandom(String knownSeed) throws Exception {
        if (null == knownSeed) {
            initRandom();
            return;
        }

        byte[] seed = new byte[SEED_SIZE];
        StringTokenizer st = new StringTokenizer(knownSeed, "_");
        for (int i=0; i<SEED_SIZE; i++) {
            if (st.hasMoreTokens())
                seed[i] = Byte.valueOf(st.nextToken()).byteValue();
            else
                throw new Exception(
                               "Specified seed value is too short: " +
                               "seed size = " + SEED_SIZE + 
                               ", seed value = " + knownSeed);
        }
        if (st.hasMoreTokens())
            throw new Exception(
                                       "Specified seed value is too long: " +
                                       "seed size = " + SEED_SIZE + 
                                       ", seed value = " + knownSeed);
        
        seedWithBytes(seed);
    }

    /**
     *  Initialize the generator. The random seed comes from /dev/urandom.
     */
    public static void initRandom() throws Exception {
        if (rand == null) {
            FileInputStream fi = null;
            try {
                fi = new FileInputStream(DEFAULT_SEED_SOURCE);
                byte[] seed = new byte[SEED_SIZE];
                fi.read(seed);
                seedWithBytes(seed);
            } catch (Exception e) {
                throw new Exception(e);
            } finally {
                if (fi != null) {
                    try {
                        fi.close();
                    } catch (Exception ignore) {}
                }
            }
        }
    }

    /** Utility function to seed the generator once we have the byte array.
     */
    private static void seedWithBytes(byte[] knownSeed) throws Exception {
        try {
            rand = SecureRandom.getInstance(GENERATOR);
            rand.setSeed(knownSeed);
        } catch (Exception e) {
            throw new Exception(e);
        }
        seedString = formatSeed(knownSeed);
    }

    /** Format the byte array to a human-readable string, which can 
     *  serve as input to initRandom(String)
     */
    private static String formatSeed(byte[] bytes) {
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<SEED_SIZE; i++) {
            sb.append(Byte.toString(bytes[i])).append("_");
        }
        sb.deleteCharAt(sb.length()-1);
        return sb.toString();
    }
    private static String formatSeed(long seed) {
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<SEED_SIZE; i++) {
            long shift = seed >> (i*8);
            byte b = (byte) shift;
            sb.append(Byte.toString(b)).append("_");
        }
        sb.deleteCharAt(sb.length()-1);
        return sb.toString();
    }

    /**
     *  Get the specified number of random bytes in an array.
     */
    public static byte[] getRandomBytes(int size) throws Exception {
        initRandom();
        
        byte randomBytes[] = new byte[size];
        rand.nextBytes(randomBytes);
        return randomBytes;
    }

    /**
     *  Get the number of random bytes used for seeding.
     */
    public static byte[] getNewSeed() throws Exception {
        return getRandomBytes(SEED_SIZE);
    }

    /**
     *  Set seed (e.g. to replicate an old sequence).
     */
    public static void setSeed(byte[] b) throws Exception {
        if (b.length != SEED_SIZE) {
            throw new Exception("seed length must be " + 
                                             SEED_SIZE);
        }
        try {
            //
            //  need a new generator since SecureRandom.setSeed() 
            //  is cumulative
            //
            rand = SecureRandom.getInstance(GENERATOR);
            rand.setSeed(b);
        } catch (Exception e) {
            throw new Exception(e);
        }
        seedString = formatSeed(b);
    }
    
    public static void setSeed(long seed) throws Exception {
        try {
            //
            //  need a new generator since SecureRandom.setSeed() 
            //  is cumulative
            //
            rand = SecureRandom.getInstance(GENERATOR);
            rand.setSeed(seed);
        } catch (Exception e) {
            throw new Exception(e);
        }
        seedString = formatSeed(seed);
    }

    /**
     *  Get a random string of letters and numbers of the specified
     *  length (no punctuation so it can be a directory name).
     */
    public static char[] getRandomChars(int size) throws Exception {
        initRandom();

        char randomChars[] = new char[size];

        for (int i = 0; i < randomChars.length; i++) {
            char c = 0;
            while (c == 0) {
                int randNum = rand.nextInt(Character.MAX_RADIX);
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

    /**
     *  Get String of random chars (per getRandomChars) of given length.
     */
    static public String getRandomString(int len) throws Exception {
        char[] c = getRandomChars(len);
        return new String(c);
    }

    /**
     *  Generate a random index in range 0 to mod.
     */
    static public int randIndex(int mod) throws Exception {
        initRandom();
        return rand.nextInt(mod);
    }

    /**
     *  Get a double.
     */
    static public double getDouble() throws Exception {
        initRandom();
        return rand.nextDouble();
    }

    /**
     *  Get a double chopped to total decimal string length 10.
     */
    static public double getLessPreciseRandomDouble() throws Exception {
        return getLessPreciseRandomDouble(10);
    }

    /**
     *  Get a double chopped to specified total decimal string length.
     */
    static public double getLessPreciseRandomDouble(int len) throws Exception {
        initRandom();
        double d = rand.nextDouble();
        String dstring = d + "";
        if (dstring.length() > len) {
            // Truncate string.  Don't worry about rounding correctly
            dstring = dstring.substring(0, len+1);
            d = Double.parseDouble(dstring);
        }

        return (d);
    }

    /** main is for test */
    public static void main(String[] args) {
        long seed = 0;
        String s;
        try {
            if (args.length > 0) {
                s = args[0];
                RandomUtil.initRandom(s);
            }
            byte[] b1 = RandomUtil.getRandomBytes(2);
            byte[] b2 = RandomUtil.getRandomBytes(2);
            System.out.println("1:  " + b1[0] + " "+ b1[1]);
            System.out.println("2:  " + b2[0] + " "+ b2[1]);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
