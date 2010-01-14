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



package com.sun.honeycomb.multicell.lib;

import com.sun.honeycomb.test.Testcase;
import com.sun.honeycomb.common.UID;

import java.util.logging.Logger;
import java.util.Set;
import java.util.HashSet;

public class Rule_t1 extends Testcase {
    private static Logger log
        = Logger.getLogger(Rule_t1.class.getName());

   /**********************************************************************/
    public Rule_t1(String name) {
        super(name);
    }

    /**********************************************************************/
    public void testHashBasic() throws Exception {
        Rule.Interval interval = new Rule.Interval((short) 0, Short.MAX_VALUE,
                                                   (long) 0) {
                public long computeHash(String str) {
                    return super.computeHash(str);
                }
            };
        String uuid1 = "f3621078-9d31-11db-981d-00e0815983ae";
        String uuid2 = "0e2876ab-9d32-11db-9d00-00e081598343";
        long hash1 = interval.computeHash(uuid1);
        long hash2 = interval.computeHash(uuid2);
        assertFalse("hash value", hash1 == hash2);

        // replace one char at a time and verify that hash values are different
        Set set = new HashSet();
        for (int i = 0; i < uuid1.length(); i++) {
            StringBuffer uuid = new StringBuffer(uuid1);
            uuid.setCharAt(i, '*');
            Long hashValue = new Long(interval.computeHash(uuid.toString()));
            set.add(hashValue);
        }
        assertEquals("hash values", uuid1.length(), set.size());
    }

    /**********************************************************************
     * Verify that silolocation values are spread uniformly over the interval.
     * XXX: This is a long running test (25 sec), commented out for now. Run
     * this whenever hash function changes.
     */
    public void xtestSiloLocationSpread() throws Exception {
        Rule.Interval interval = new Rule.Interval((short) 0, Short.MAX_VALUE,
                                                   (long) 0);
        // UID's computed here have the same mac address
        // encoded. However, silolocation should still be uniformly
        // spread. Verify that the values are spread uniformly across
        // the universe (NB_PARTS intervals in {0, Short.MaX_VALUE}).
        int[] spread = new int[Rule.Interval.NB_PARTS];
        int count = 10000000;
        short width = Short.MAX_VALUE / Rule.Interval.NB_PARTS;
        for (int i = 0; i < count; i++) {
            int index = interval.getNextSiloLocation(new UID()) / width;
            spread[index - 1]++;
        }
        // verify
        int expected = count / Rule.Interval.NB_PARTS;
        int delta = expected / 200; // 0.5 %
        for (int i = 0; i < Rule.Interval.NB_PARTS; i++) {
            assertTrue("deviation from ideal spread",
                       Math.abs(expected - spread[i]) < delta);
        }
    }
}
