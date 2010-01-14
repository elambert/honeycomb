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
import java.util.Random;

/** 
 * Implements the layout map generation algorithm.  See Layout Detailed
 * Design for description of algorithm and examples.  This class uses
 * the template map to generate all other layout maps by shuffling the
 * nodes and disks.  Shuffling is done using a random number generator,
 * seeded with the MapId, which works because the Java Random class
 * always returns the same sequence of numbers for the same seed.
 */
class MapGenerator implements MapGenInterface {

    static int[][][] templateMap = new int[][][] {

        { {0,0},  {4,1},  {8,2}, {12,3}, {15,0}, {11,1},  {7,2} },
        { {1,0},  {5,1},  {9,2}, {13,3}, {14,0}, {10,1},  {6,2} },
        { {2,0},  {6,1}, {10,2}, {14,3}, {13,0},  {9,1},  {5,2} },
        { {3,0},  {7,1}, {11,2}, {15,3}, {12,0},  {8,1},  {4,2} },

        { {0,2},  {4,3},  {8,0}, {12,1}, {15,2}, {11,3},  {7,0} },
        { {1,2},  {5,3},  {9,0}, {13,1}, {14,2}, {10,3},  {6,0} },
        { {2,2},  {6,3}, {10,0}, {14,1}, {13,2},  {9,3},  {5,0} },
        { {3,2},  {7,3}, {11,0}, {15,1}, {12,2},  {8,3},  {4,0} },

        { {3,1},  {3,1},  {3,1},  {3,1},  {3,1},  {3,1},  {3,1} },
        { {2,1},  {2,1},  {2,1},  {2,1},  {2,1},  {2,1},  {2,1} },
        { {1,1},  {1,1},  {1,1},  {1,1},  {1,1},  {1,1},  {1,1} },
        { {0,1},  {0,1},  {0,1},  {0,1},  {0,1},  {0,1},  {0,1} },

        { {3,3},  {3,3},  {3,3},  {3,3},  {3,3},  {3,3},  {3,3} },
        { {2,3},  {2,3},  {2,3},  {2,3},  {2,3},  {2,3},  {2,3} },
        { {1,3},  {1,3},  {1,3},  {1,3},  {1,3},  {1,3},  {1,3} },
        { {0,3},  {0,3},  {0,3},  {0,3},  {0,3},  {0,3},  {0,3} }, 
    };

    static int GROUPS = 4;
    static int SHUFFLES = 24;
    static int PER_GROUP = 4;

    static int nodes[][][] = new int[][][] {
    
    {{0, 1, 2, 3}, {0, 1, 3, 2}, {0, 2, 1, 3}, {0, 2, 3, 1},
    {0, 3, 1, 2}, {0, 3, 2, 1}, {1, 0, 2, 3}, {1, 0, 3, 2},
    {1, 2, 0, 3}, {1, 2, 3, 0}, {1, 3, 0, 2}, {1, 3, 2, 0},
    {2, 0, 1, 3}, {2, 0, 3, 1}, {2, 1, 0, 3}, {2, 1, 3, 0},
    {2, 3, 0, 1}, {2, 3, 1, 0}, {3, 0, 1, 2}, {3, 0, 2, 1},
    {3, 1, 0, 2}, {3, 1, 2, 0}, {3, 2, 0, 1}, {3, 2, 1, 0}},
    
    {{4, 5, 6, 7}, {4, 5, 7, 6}, {4, 6, 5, 7}, {4, 6, 7, 5},
    {4, 7, 5, 6}, {4, 7, 6, 5}, {5, 4, 6, 7}, {5, 4, 7, 6},
    {5, 6, 4, 7}, {5, 6, 7, 4}, {5, 7, 4, 6}, {5, 7, 6, 4},
    {6, 4, 5, 7}, {6, 4, 7, 5}, {6, 5, 4, 7}, {6, 5, 7, 4},
    {6, 7, 4, 5}, {6, 7, 5, 4}, {7, 4, 5, 6}, {7, 4, 6, 5},
    {7, 5, 4, 6}, {7, 5, 6, 4}, {7, 6, 4, 5}, {7, 6, 5, 4}},
    
    {{10, 11, 8, 9}, {10, 11, 9, 8}, {10, 8, 11, 9}, {10, 8, 9, 11},
    {10, 9, 11, 8}, {10, 9, 8, 11}, {11, 10, 8, 9}, {11, 10, 9, 8},
    {11, 8, 10, 9}, {11, 8, 9, 10}, {11, 9, 10, 8}, {11, 9, 8, 10},
    {8, 10, 11, 9}, {8, 10, 9, 11}, {8, 11, 10, 9}, {8, 11, 9, 10},
    {8, 9, 10, 11}, {8, 9, 11, 10}, {9, 10, 11, 8}, {9, 10, 8, 11},
    {9, 11, 10, 8}, {9, 11, 8, 10}, {9, 8, 10, 11}, {9, 8, 11, 10}},
    
    {{12, 13, 14, 15}, {12, 13, 15, 14}, {12, 14, 13, 15}, {12, 14, 15, 13},
    {12, 15, 13, 14}, {12, 15, 14, 13}, {13, 12, 14, 15}, {13, 12, 15, 14},
    {13, 14, 12, 15}, {13, 14, 15, 12}, {13, 15, 12, 14}, {13, 15, 14, 12},
    {14, 12, 13, 15}, {14, 12, 15, 13}, {14, 13, 12, 15}, {14, 13, 15, 12},
    {14, 15, 12, 13}, {14, 15, 13, 12}, {15, 12, 13, 14}, {15, 12, 14, 13},
    {15, 13, 12, 14}, {15, 13, 14, 12}, {15, 14, 12, 13}, {15, 14, 13, 12}}
    
    };
    
    static int shuffle4[][] = new int[][] {

    {0, 1, 2, 3}, {0, 1, 3, 2}, {0, 2, 1, 3}, {0, 2, 3, 1},
    {0, 3, 1, 2}, {0, 3, 2, 1}, {1, 0, 2, 3}, {1, 0, 3, 2},
    {1, 2, 0, 3}, {1, 2, 3, 0}, {1, 3, 0, 2}, {1, 3, 2, 0},
    {2, 0, 1, 3}, {2, 0, 3, 1}, {2, 1, 0, 3}, {2, 1, 3, 0},
    {2, 3, 0, 1}, {2, 3, 1, 0}, {3, 0, 1, 2}, {3, 0, 2, 1},
    {3, 1, 0, 2}, {3, 1, 2, 0}, {3, 2, 0, 1}, {3, 2, 1, 0}

    };
    
    public MapGenerator() {

        int n = LayoutConfig.NODES_PER_CELL;
        int d = LayoutConfig.DISKS_PER_NODE;
        int f = LayoutConfig.FRAGS_PER_OBJ;

        if (n != 16 || d != 4 || f != 7) {
            throw new IllegalArgumentException(
            "This map generator designed for NODES=16 DISKS=4 FRAGS=7, "+
            " but LayoutConfig shows NODES="+n+" DISKS="+d+" FRAGS="+f);
        }
    }

    /*
     * Implementation of MapGenInterface (see comments there)
     */
    public int[] getMapEntry(int mapId, int row, int col) {

        int[] nodeList = new int[GROUPS * PER_GROUP];
        int[] diskList = new int[PER_GROUP];

	Random rnd = new Random(mapId);

        // choose the ordering of disks
        diskList = shuffle4[ rnd.nextInt(SHUFFLES) ];

        // choose the ordering of node groups
        int groupOrder[] = shuffle4[ rnd.nextInt(SHUFFLES) ];

        // choose ordering of elements within each group, multiple
        // groups can have same ordering (i.e. same shuffle index)
        int[] shuffleIndexes = new int[GROUPS];
        for (int i=0; i < GROUPS; i++) {
            shuffleIndexes[i] = rnd.nextInt(SHUFFLES); 
        } 

        // fill in the nodeList by choosing a node from each group, and
        // then another node from each group, and repeating
        for (int elem=0; elem < PER_GROUP; elem++) {
            for (int g=0; g < GROUPS; g++) {

                int groupIndex = groupOrder[g];
                int shuffleIndex = shuffleIndexes[groupIndex];
                int i = (elem * GROUPS) + g;
                nodeList[i] = nodes[groupIndex][shuffleIndex][elem];
            } 
        } 

        return calcMapEntry(nodeList, diskList, row, col);
    }

    /** Given a shuffled list of nodes and disks, derive the entry. */
    private int[] calcMapEntry(int[] nodeList, int[] diskList,
                               int row, int col) {

        int nodeIndex = templateMap[row][col][LayoutClient.ENTRY_NODE];
        int diskIndex = templateMap[row][col][LayoutClient.ENTRY_DISK];

        int node = nodeList[nodeIndex];
        int disk = diskList[diskIndex];

        return new int[] {node, disk};
    }

}



