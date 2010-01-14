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



/*
 * Utility to print the hash value for an OID for the bloom filter
 *
 * You can invoke it like this on a cluster node:
 *   

java -classpath <PATH_TO_UNIT_TEST_JAR>/honeycomb-utests.jar:/opt/honeycomb/lib/honeycomb.jar:/opt/honeycomb/lib/honeycomb-server.jar:/opt/honeycomb/lib/honeycomb-common.jar:/opt/honeycomb/lib/jug.jar -Djava.library.path=/opt/honeycomb/lib/ com.sun.honeycomb.util.BloomOidHash MD_OID

 */

package com.sun.honeycomb.util;

import java.util.BitSet;
import com.sun.honeycomb.oa.BloomFilter;
import com.sun.honeycomb.oa.FragmentFooter;
import com.sun.honeycomb.common.NewObjectIdentifier;

public class BloomOidHash {
    // The basis for this code is in oa/FragmentFile.java and
    // FragmentFooter.java
    public static void main(String args[]) {
        try {
            NewObjectIdentifier oid = new NewObjectIdentifier(args[0]);
            BitSet deletedRefs =
                new BitSet(FragmentFooter.DELETED_REFS_BLOOM_BITLENGTH);
            deletedRefs.clear();
            BloomFilter bloom = new BloomFilter(deletedRefs);
            bloom.put(oid);
            BitSet bs = bloom.getKeys();
            System.out.println("oid " + oid + " has bloom filter bit set " +
                bs);
        } catch (Throwable t) {
            // This is rather sloppy...
            t.printStackTrace();
            System.out.println("(exiting due to an exception)\n");
            System.out.println("Usage: BloomOidHash mdoid");
        }
    }
}
