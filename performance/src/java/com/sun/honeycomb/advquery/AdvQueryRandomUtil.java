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




import java.io.FileInputStream;
import java.security.SecureRandom;
import java.util.StringTokenizer;
import com.sun.honeycomb.test.util.HoneycombTestException;


/**
 *  A convenience class for random functions.
 *
 *  This was borrowed from RandomUtil class. The difference is the AdvQuery
 *  tests need to be able to reproduce the same random # sequence on
 *  subsequent runs. Each thread will have used a different seed value and
 *  have it's own AdvQueryRandomUtil instance.
 */
public class AdvQueryRandomUtil {
    
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
    
    private SecureRandom rand = null;
    private String seedString = null;
    
    
    public AdvQueryRandomUtil(String knownSeed)throws HoneycombTestException {
        initRandom(knownSeed);
    }
    
    public AdvQueryRandomUtil(byte[] knownSeed)throws HoneycombTestException {
        initRandom(knownSeed);
    }
    
    /**
     *  Get last seed used.
     */
    public  String getSeed() throws HoneycombTestException {
        return seedString;
    }
    
    /**
     * Initialize the generator with known random seed to reproduce a run.
     */
    public void initRandom(byte[] seed) throws HoneycombTestException {
        if (null == seed) {
            System.err.println("Null random seed value passed in, " +
                    "will generate a different seed value.");
            initRandom();
            return;
        }
        
        if (seed.length != SEED_SIZE) {
            System.err.println("Invalid seed length passed in. Seed must be " +
                    SEED_SIZE + " in length. Generating different one");
            initRandom();
            return;
        }
        
        seedWithBytes(seed);
    }
    
    /**
     * Initialize the generator with known random seed to reproduce a run.
     */
    public void initRandom(String knownSeed)
    throws HoneycombTestException {
        if (null == knownSeed) {
            System.err.println("Null random seed value passed in, " +
                    "will generate a different seed value");
            initRandom();
            return;
        }
        
        byte[] seed = new byte[SEED_SIZE];
        StringTokenizer st = new StringTokenizer(knownSeed, "_");
        for (int i=0; i<SEED_SIZE; i++) {
            if (st.hasMoreTokens())
                seed[i] = Byte.valueOf(st.nextToken()).byteValue();
            else
                throw new HoneycombTestException(
                        "Specified seed value is too short: " +
                        "seed size = " + SEED_SIZE +
                        ", seed value = " + knownSeed);
        }
        if (st.hasMoreTokens())
            throw new HoneycombTestException(
                    "Specified seed value is too long: " +
                    "seed size = " + SEED_SIZE +
                    ", seed value = " + knownSeed);
        
        seedWithBytes(seed);
    }
    
    /**
     *  Initialize the generator. The random seed comes from /dev/urandom.
     */
    public void initRandom() throws HoneycombTestException {
        if (rand == null) {
            FileInputStream fi = null;
            try {
                fi = new FileInputStream(DEFAULT_SEED_SOURCE);
                byte[] seed = new byte[SEED_SIZE];
                fi.read(seed);
                seedWithBytes(seed);
            } catch (Exception e) {
                throw new HoneycombTestException(e);
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
    private String seedWithBytes(byte[] knownSeed)
    throws HoneycombTestException {
        try {
            rand = SecureRandom.getInstance(GENERATOR);
            rand.setSeed(knownSeed);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
        seedString = formatSeed(knownSeed);
        return seedString;
    }
    
    /** Format the byte array to a human-readable string, which can
     *  serve as input to initRandom(String)
     */
    private String formatSeed(byte[] bytes) {
        StringBuffer sb = new StringBuffer();
        for (int i=0; i<SEED_SIZE; i++) {
            sb.append(Byte.toString(bytes[i])).append("_");
        }
        sb.deleteCharAt(sb.length()-1);
        return sb.toString();
    }
    private String formatSeed(long seed) {
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
    public byte[] getRandomBytes(int size)
    throws HoneycombTestException {
        
        byte randomBytes[] = new byte[size];
        rand.nextBytes(randomBytes);
        return randomBytes;
    }
    
    /**
     *  Get a new seed of a certain size. Used to seed other random
     *  number generators.
     */
    public byte[] getNewSeed()  {
        byte seedValue[] = new byte[SEED_SIZE];
        rand.nextBytes(seedValue);
        return seedValue;
    }
    
    /**
     *  Set seed (e.g. to replicate an old sequence).
     */
    public void setSeed(byte[] b) throws HoneycombTestException {
        if (b.length != SEED_SIZE) {
            throw new HoneycombTestException("seed length must be " +
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
            throw new HoneycombTestException(e);
        }
        seedString = formatSeed(b);
    }
    
    public void setSeed(long seed) throws HoneycombTestException {
        try {
            //
            //  need a new generator since SecureRandom.setSeed()
            //  is cumulative
            //
            rand = SecureRandom.getInstance(GENERATOR);
            rand.setSeed(seed);
        } catch (Exception e) {
            throw new HoneycombTestException(e);
        }
        seedString = formatSeed(seed);
    }
    
    /**
     *  Get a random string of letters and numbers of the specified
     *  length (no punctuation so it can be a directory name).
     */
    public char[] getRandomChars(int size)
    throws HoneycombTestException {
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
    public String getRandomString(int len)
    throws HoneycombTestException {
        char[] c = getRandomChars(len);
        return new String(c);
    }
    
    /**
     *  Get a random string of letters and numbers of the specified
     *  length (no punctuation so it can be a directory name).
     */
    public char[] getRandomChars2(int size)
        throws HoneycombTestException {
        char randomChars[] = new char[size];
        
        for (int i = 0; i < randomChars.length; i++) {
            char c = 0;
            // numbers or lower case chars
            // real range for values is is 48-57, 97-122
            int randNum = rand.nextInt(36);
            if (randNum < 10) {
                randNum += 48;
            } else {
                randNum += 87;
            }
            c = (char) randNum;
            randomChars[i] = c;
        }
        return randomChars;
    }

    /**
     *  Get String of random chars (per getRandomChars) of given length.
     */
    public String getRandomString2(int len)
    throws HoneycombTestException {
        char[] c = getRandomChars2(len);
        return new String(c);
    }

    /**
     *  Generate a random index in range 0 to mod.
     */
    public int randIndex(int mod) throws HoneycombTestException {
        return rand.nextInt(mod);
    }
    
    /**
     *  Generate a random integer in range 0 to mod.
     */
    public int getInteger(int max) {
        return rand.nextInt(max);
    }
    
    /**
     *  Get a double.
     */
    public double getDouble() throws HoneycombTestException {
        return rand.nextDouble();
    }
    
    
    /**
     *  Get a double.
     */
    public long getLong() throws HoneycombTestException {
        return rand.nextLong();
    }
    
    /**
     *  Get a double chopped to total decimal string length 10.
     */
    public double getLessPreciseRandomDouble()
    throws HoneycombTestException {
        return getLessPreciseRandomDouble(10);
    }
    
    /**
     *  Get a double chopped to specified total decimal string length.
     */
    public double getLessPreciseRandomDouble(int len)
    throws HoneycombTestException {
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
        String s = null;
        AdvQueryRandomUtil testRand;
        try {
            if (args.length > 0) {
                s = args[0];
            }
            testRand = new AdvQueryRandomUtil(s);
            byte[] b1 = testRand.getRandomBytes(2);
            byte[] b2 = testRand.getRandomBytes(2);
            System.out.println("1:  " + b1[0] + " "+ b1[1]);
            System.out.println("2:  " + b2[0] + " "+ b2[1]);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
