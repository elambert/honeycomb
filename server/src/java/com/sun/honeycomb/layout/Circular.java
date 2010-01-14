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

public class Circular 
    implements Permutation {

    private int dimension;
    
    private boolean initialized;
    private int current;
    private int[] cursors;
    
    public Circular(int _dimension) {
        dimension = _dimension;
        cursors = new int[dimension];
        current = 0;
        initialized = false;
    }

    public int[] next() {
        for (int i=0; i<dimension; i++) {
            cursors[(i+current) % dimension] = i;
        }
        current++;
        if (current == dimension) {
            current = 0;
        }
        return(cursors);
    }

    public int size() {
        return(dimension);
    }

    public static void main(String[] arg) {
        if (arg.length != 1) {
            System.out.println("Args: n");
            System.exit(1);
        }
        int n = Integer.parseInt(arg[0]);
        Circular c = new Circular(n);
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
