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



package com.sun.honeycomb.oa;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.BitSet;

import com.sun.honeycomb.common.NewObjectIdentifier;

/** Implementation of a Bloom Filter data structure, an elegant
 * alternative to the lookup hash table.</p> <p>
 *
 * Some ideas for this class came from reading Bruno Martins [
 * bmartins@xldb.di.fc.ul.pt ] BloomFilter class for the jaspell
 * project [http://jaspell.sourceforge.net ] which is released under
 * the BSD Licence [
 * http://www.opensource.org/licences/bsd-license.php ].
 */
public class BloomFilter implements Cloneable {
    
    /** The bit vector for the Bloom Filter. */
    private boolean keys[];
    
    /** The number of hash functions. */
    private int numFunctions;
    
	/**
	 * Constructs an empty BloomFilter with the default number of hash functions (10)
	 * and a given length for the bit vector;
	 *
	 *@param  numKeys           The length of the bit vector.
	 */
	public BloomFilter(int numKeys) {
		this(numKeys, 10);
	}
    
    /** Constructs a bloom filter initalized to the given bitmask and
     * default (10) num functions
     * 
     * @param initialKeys   the bit vector to initialize to.
     *                      NOTE: this does not copy the array, so it must not
     *                            be used after being passed into this function.  */
    
    public BloomFilter(BitSet bitSet) {
        this(bitSet.size());
        for (int i = 0; i < bitSet.size(); i++) {
            this.keys[i] = bitSet.get(i);
        }
        numFunctions = 10;
    }
    
	/**
	 * Constructs an empty BloomFilter with a given number of hash
	 * functions and a given length for the bit vector.
	 *
	 *@param numKeys The length of the bit vector.
	 *@param  numHashFunctions  The number of hash functions.
	 */
    
	public BloomFilter(int numKeys, int numHashFunctions) {
		this.keys = new boolean[numKeys];
		this.numFunctions = numHashFunctions;
		for (int i = 0; i < numKeys; i++)
        this.keys[i] = false;
	}
    
    /**
	 * Constructs a Bloom Filter from a string representation.
	 *
	 * @see #toString()
	 */
	public BloomFilter(String filter) {
		int index1 = filter.indexOf(":");
		int index2 = filter.lastIndexOf(":");
		numFunctions = new Integer(filter.substring(0, index1)).intValue();
		keys =
			new boolean[new Integer(filter.substring(index1, index2))
                       .intValue()];
		for (int i = index2 + 1; i < filter.length(); i++) {
			if (filter.charAt(i) == '1')
            keys[i] = true;
			else
            keys[i] = false;
		}
	}
    
    /** N different hash functions to hash oid. Result is in
     * [0-keys.length] so it can be an index into that array.  Select
     * which has function to use with fnum.  This hash is a variation
     * on the RS Hash function from [
     * http://www.partow.net/programming/hashfunctions/ ] which is in
     * turn a variation on a simple hash function from Robert
     * Sedgwicks Algorithms in C book.
     *
     * @param fnum which has function to use (0 - numFunctions-1)
     * @param oid the NewObjectIdentifier to hash into a integer
     */
    public int getHash(int fnum, NewObjectIdentifier oid)
    {
        // Only has the 1st 8 characters of the OID string representation
        // The other part remains fairly static due to the machine MAC address
        // etc.  NOTE: We may want to revisit this if we change our UUID generator.
        String str = oid.toString().substring(0,8);
        
        // Two
        int b     = 378551;
        int a     = 63689;
        
        for(int f = 0; f < fnum; f++) {
            a = a * b;
        }
        
        long hash = 0;
        
        for(int i = 0; i < str.length(); i++)
            {
                hash = hash * a + str.charAt(i);
                a    = a * b;
            }
        
        return ((int) (hash & 0x7FFFFFFF)) % keys.length;
        
    }
    
    /** See whether this filter contains oid.  This can return false
     * positives.
     * 
     * @param oid the oid to look for
     */
	public boolean hasKey(NewObjectIdentifier oid) {
		boolean result = true;
        String log = "CHECK OID " + oid + ":";
        
		for (int i = 0; i < numFunctions && result; i++) {
            int hash = getHash(i, oid);
			result &= keys[hash];
            if(result) {
                log = log + hash + "|";
            }
		}
        if(result) {
            log = log + "CRAP";
        }
        
        //System.out.println(log);
        
        return result;
    }
    
	/**
	 *  Adds the specified key in this Bloom Filter.
	 *
	 *@param  obj  The key to be added to this Bloom Filter.
	 */
	public void put(NewObjectIdentifier oid) {
        String log = "PUT OID " + oid + ":";
		for (int i = 0; i < numFunctions; i++) {
            int hash = getHash(i, oid);
            log = log + "." + hash;
			keys[hash] = true;
		}
        //System.out.println(log);
	}

	/**
	 *
	 * Returns a string representation of this Bloom Filter. The
	 * string representation consists of an integer specifying the
	 * number of hash Functions, an integer specifying the length
	 * of the bit vector, and a sequence of 0s and 1s specifying
	 * the bit vector. These 3 fields are separated by the
	 * character ":".
	 * 
	 * This implementation creates an empty string buffer, and
	 * iterates over the bit vector, appending the value of each
	 * bit in turn. A string is obtained from the stringbuffer,
	 * and returned.
	 * 
	 * @return A string representation of this Bloom Filter.
	 */
	public String toString() {
		StringBuffer aux =
			new StringBuffer(numFunctions + ":" + keys.length + ":");
		for (int i = 0; i < keys.length; i++) {
			if (keys[i])
            aux.append("1");
			else
            aux.append("0");
		}
		return aux.toString();
	}
    
    /** Returns a BitSet containing the current state of this
     * bloomfilter
     *  
     *  @return A BitSet reprentation of the current state of the
     *  Bloom Filter
     *
     */
    public BitSet getKeys() {
        BitSet bitSet = new BitSet(keys.length);
        bitSet.clear();
        for(int i=0; i < keys.length; i++) {
            if(keys[i]) {
                bitSet.set(i);
            }
        }
        return bitSet;
    }
    
	/**
	 * Returns a copy of this Bloom Filter instance.
	 *
	 * @see java.lang.Object#clone()
	 */
	public Object clone() {
		return new BloomFilter(this.toString());
	}
}
