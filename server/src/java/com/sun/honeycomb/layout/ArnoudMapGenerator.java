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

public class ArnoudMapGenerator
    implements MapGenInterface {

    public static final byte DISK_MASK = 0x03;
    
    public static final byte NODE_MASK = 0x3C;
    public static final byte NODE_SHIFT = 2;

    /*
     * What are the golden seeds ?
     * The nodes in a column define the sequential list nodes that will
     * take over the data when there are failures.
     * So if node 0 fails, its data will be recoved by the node below 0 in
     * each of the map.
     * One understands that to have a good repartition of the recovery work
     * across nodes and across maps when there is a failure, one node
     * should have as many "partners" as possible.
     * If we have 8 nodes, the number of possible couples is 8*7/2 = 28
     * A map contains 4 couples
     *
     * Is there a combination a 28/4=7 maps that contain all the possible
     * couples once ? Yes, and this is the set of golden seeds.
     */

    private static final byte[][][] goldenSeeds = new byte[][][] {
        { {0, 2, 5, 7},
          {1, 3, 4, 6} },
        
        { {7, 6, 5, 1},
          {4, 0, 2, 3} },

        { {3, 5, 2, 0},
          {4, 6, 1, 7} },

        { {3, 2, 1, 4},
          {5, 0, 7, 6} },

        { {6, 7, 0, 4},
          {1, 5, 3, 2} },

        { {6, 3, 4, 1},
          {2, 7, 0, 5} },
        
        { {7, 4, 6, 0},
          {2, 1, 3, 5} }
    };

    private static final byte[][][] goldenDisks = new byte[][][] {
        { {0, 0},
          {0, 0} },

        { {0, 2},
          {1, 3} },

        { {3, 2},
          {1, 0} },

        { {0, 2},
          {3, 1} }
    };

    private byte[][][] maps = null;

    public ArnoudMapGenerator() {
        generateMaps();
    }

    public int[] getMapEntry(int mapId, int row, int column) {
        byte entry = 0;

        if (row < 8) {
            entry = maps[mapId][row][column];
        } else {
            // We have to take the spare column
            entry = maps[mapId][row-8][7];
        }

        int[] result = new int[2];
        
        // Compute the node
        result[0] = (entry & NODE_MASK) >> NODE_SHIFT;
        
        // Compute the disk
        result[1] = (entry & DISK_MASK);

        return(result);
    }

    private void generateMaps() {
        //        Permutation columnShuffle = new Arrangement(4, 4);
        Permutation columnShuffle = new Circular(4);
        int[] currentColumnShuffle = null;
        
        //Permutation rowShuffle = new BiasedArrangement();
         Permutation rowShuffle = new RowPermutation();
        int[] currentRowShuffle = null;

        PermutedMap disks = null;
        Permutation diskShuffle = new Circular(4);
        int[] currentDiskShuffle = diskShuffle.next();

        int goldenIndex = 0;
        int diskIndex = 0;

        maps = new byte[LayoutClient.NUM_MAP_IDS][][];
        for (int i=0; i<maps.length; i++) {
            if ((i%7) == 0) {
                disks = new PermutedMap(goldenDisks[diskIndex]);
                diskIndex++;
                if (diskIndex == goldenDisks.length) {
                    diskIndex = 0;
                    currentDiskShuffle = diskShuffle.next();
                }
                disks.permuteAll(currentDiskShuffle);
            }

            PermutedMap map = new PermutedMap(goldenSeeds[goldenIndex]);
            PermutedMap bottom = new PermutedMap(goldenSeeds[goldenIndex]);

            bottom.add((byte)0);
            
            map.merge(bottom);
            
            if (i%112 == 0) {
                currentRowShuffle = rowShuffle.next();
            }
            if (i%896 == 0) {
                currentColumnShuffle = columnShuffle.next();
            }
            map.permuteRows(currentRowShuffle);
            map.permuteCols(currentColumnShuffle);

            maps[i] = expandMap(map.array(), disks.array());

            goldenIndex++;
            if (goldenIndex == goldenSeeds.length) {
                goldenIndex = 0;
            }
        }
    }

    private byte encode(byte node,
                        int disk) {
        return((byte)((node << NODE_SHIFT) + disk));
    }

    private byte[][] expandMap(byte[][] input,
                               byte[][] disks) {
        byte[][] result = new byte[8][8];
        
        for (int i=0; i<8; i++) {
            for (int j=0; j<8; j++) {
                int x = (j<4) ? i%4 : 3-(i%4);
                int y = (j<4) ? j : 7-j;
                byte node = input[x][y];

                int origDisk = (i<4)
                    ? (j%4)
                    : ((j+2)%4);

                result[i][j] = encode(node, (disks[x%2][y%2]+origDisk)%4);
            }            
        }
        
        return(result);
    }

    public void print(int index) {
        byte[][] input = maps[index];
        for (int i=0; i<input.length; i++) {
            for (int j=0; j<input[i].length; j++) {
                if (j>0)
                    System.out.print(" ");
                System.out.print((input[i][j] >> NODE_SHIFT)+":"+(input[i][j] & DISK_MASK));
            }
            System.out.println("");
        }
        System.out.println("");
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        ArnoudMapGenerator mapGen = new ArnoudMapGenerator();
        System.out.println("It took "+(System.currentTimeMillis()-start)+" ms. to create the maps");

        if (args.length == 0) {
            for (int i=0; i<LayoutClient.NUM_MAP_IDS; i++) {
                mapGen.print(i);
            }
        } else {
            int map = Integer.parseInt(args[0]);
            System.out.println("MAP "+map+"\n");
            mapGen.print(map);
        }
    }
}
