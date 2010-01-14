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

public class Combination 
    implements Permutation {
    private int dimension;
    private int nbElems;
    
    private boolean initialized;
    private int[] cursors;
    
    public Combination(int _dimension,
                       int _nbElems) {
        dimension = _dimension;
        nbElems = _nbElems;
        cursors = new int[nbElems];
        initialized = false;
    }

    private void reset() {
        for (int i=0; i<nbElems; i++) {
            cursors[i] = i;
        }
    }

    private boolean increment(int position) {
        if (cursors[position] == dimension-nbElems+position) {
            if ((position == 0) || (!increment(position-1))) {
                return(false);
            }
            cursors[position] = cursors[position-1]+1;
        } else {
            cursors[position]++;
        }
        return(true);
    }

    public int[] next() {
        if (!initialized) {
            reset();
            initialized = true;
        } else {
            if (!increment(nbElems-1)) {
                reset();
            }
        }
        return(cursors);
    }

    public int size() {
        return(c(dimension, nbElems));
    }

    private static int c(int n, int p) {
        if (p == 0) {
            return(1);
        }
        if (n == p) {
            return(1);
        }
        return(c(n-1, p-1) + c(n-1,p));
    }

    public static void main(String[] arg) {
        if (arg.length != 2) {
            System.out.println("Args: n p");
            System.exit(1);
        }
        int n = Integer.parseInt(arg[0]);
        int p = Integer.parseInt(arg[1]);
        Combination c = new Combination(n, p);
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
