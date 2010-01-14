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

public class PermutedMap {
    
    private byte[][] rows;

    public PermutedMap(byte[][] map) { 
        int i = map.length;
        int j = map[0].length;
        rows = new byte[i][j];
        for (int a=0; a<i; a++) {
            for (int b=0; b<j; b++) {
                rows[a][b] = (byte)map[a][b];
            }
        }
    }

    public void permuteRows(int[] indexes) {
        byte[][] newMap = new byte[rows.length][];
        for (int i=0; i<newMap.length; i++) {
            newMap[i] = rows[indexes[i]];
        }
        rows = newMap;
    }
    
    public void permuteCols(int[] indexes) {
        byte[][] newMap = new byte[rows.length][rows[0].length];
        for (int i=0; i<newMap.length; i++) {
            for (int j=0; j<rows[0].length; j++) {
                newMap[i][j] = rows[i][indexes[j]];
            }
        }
        rows = newMap;
    }
    
    public void permuteAll(int[] indexes) {
        for (int i=0; i<rows.length; i++) {
            for (int j=0; j<rows[i].length; j++) {
                rows[i][j] = (byte)indexes[rows[i][j]];
            }
        }
    }

    public void merge(PermutedMap _other) {
        byte[][] other = _other.array();
        byte[][] newMap = new byte[rows.length+other.length][];
        for (int i=0; i<rows.length; i++) {
            newMap[i] = rows[i];
        }
        for (int i=0; i<other.length; i++) {
            newMap[rows.length+i] = other[i];
        }
        rows = newMap;
    }

    public void add(byte value) {
        for (int i=0; i<rows.length; i++) {
            for (int j=0; j<rows[i].length; j++) {
                rows[i][j] = (byte)(8+((rows[i][j] + value) % 8));
            }
        }
    }
    
    public byte[][] array() {
        return(rows);
    }

    private void toString(StringBuffer sb) {
        for (int i=0; i<rows.length; i++) {
            for (int j=0; j<rows[i].length; j++) {
                if (j>0)
                    sb.append(" ");
                sb.append(rows[i][j]);
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        toString(sb);
        return(sb.toString());
    }
    
    public void print() {
        System.out.println(this);
    }
}
