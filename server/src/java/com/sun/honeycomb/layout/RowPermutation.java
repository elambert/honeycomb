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



package com.sun.honeycomb.layout;

public class RowPermutation 
    implements Permutation {

    private static final boolean DO_60_DISKS = true;
    private static final int EXTRA_RATIO = 12;

    private int[][] permutations = new int[][] {
        {0, 1, 2, 3},
        {1, 0, 3, 2},

        {1, 2, 3, 0},
        {2, 1, 0, 3},

        {2, 3, 0, 1},
        {3, 2, 1, 0},

        {3, 0, 1, 2},
        {0, 3, 2, 1}
    };

    private int[][]extra = new int[][] {
        {3, 1, 2, 0},
        {1, 3, 0, 2},
        {0, 2, 1, 3},
        {2, 0, 3, 1},
    };

    private int counter;
    private boolean initialized;
    private int extraIndex;
    private int extraSkipped;
    private int totalExtra;

    public RowPermutation() {
        initialized = false;
        totalExtra = 0;
    }
    
    public int[] next() {
        int[] result = null;

        if (!initialized) {
            counter = 0;
            extraIndex = 0;
            extraSkipped = 0;
            initialized = true;
        } 

        if ((result == null) && (DO_60_DISKS) && (extraSkipped == EXTRA_RATIO)) {
            result = extra[extraIndex];
            extraIndex++;
            if (extraIndex == extra.length)
                extraIndex = 0;
            extraSkipped = 0;
            totalExtra++;
        }

        if (result == null) {
            result = permutations[counter];
            extraSkipped++;
            counter++;
            if (counter == permutations.length) {
                counter = 0;
            }
        }
        
        return(result);
    }
    
    public int size() {
        return(permutations.length);
    }
    
    public static void main(String[] arg) {
        RowPermutation c = new RowPermutation();
        int max = c.size()+1;

        for (int i=0; i<max; i++) {
            int[] res = c.next();
            System.out.print((i+1)+":");
            for (int j=0; j<res.length; j++) {
                System.out.print(" "+res[j]);
            }
            System.out.println("");
        }
    }
}
