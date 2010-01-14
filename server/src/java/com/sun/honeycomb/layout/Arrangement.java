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

public class Arrangement 
    implements Permutation {
    private int dimension;
    private int nbElems;
    
    private boolean initialized;
    protected int[] cursors;
    private boolean[] free;
    
    public Arrangement(int _dimension,
                       int _nbElems) {
        dimension = _dimension;
        nbElems = _nbElems;
        cursors = new int[nbElems];
        free = new boolean[dimension];
        initialized = false;
    }

    private void reset() {
        for (int i=0; i<nbElems; i++) {
            cursors[i] = i;
            free[i] = false;
        }
        for (int i=nbElems; i<dimension; i++) {
            free[i] = true;
        }
    }

    private boolean increment(int position) {
        free[cursors[position]] = true;

        do {
            if (cursors[position] == dimension-1) {
                if ((position == 0) || (!increment(position-1))) {
                    return(false);
                }
                int i;
                for (i=0; i<dimension; i++) {
                    if (free[i]) {
                        break;
                    }
                }
                cursors[position] = i;
            } else {
                cursors[position]++;
            }
        } while (!free[cursors[position]]);

        free[cursors[position]] = false;
        return(true);
    }

    protected boolean valid() {
        return(true);
    }

    public int[] next() {
        do {
            if (!initialized) {
                reset();
                initialized = true;
            } else {
                if (!increment(nbElems-1)) {
                    reset();
                }
            }
        } while (!valid());
        return(cursors);
    }
    
    public int size() {
        return(comb(dimension, nbElems));
    }

    private static int comb(int n,
                            int p) {
        int result = 1;
        for (int i=n; i>=n-p+1; i--) {
            result *= i;
        }
        return(result);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer("[");
        for (int i=0; i<cursors.length; i++) {
            if (i>0)
                sb.append(" ");
            sb.append(cursors[i]);
        }
        sb.append("]");
        return(sb.toString());
    }

    public static void main(String[] arg) {
        if (arg.length != 2) {
            System.out.println("Args: n p");
            System.exit(1);
        }
        int n = Integer.parseInt(arg[0]);
        int p = Integer.parseInt(arg[1]);
        Arrangement c = new Arrangement(n, p);
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
