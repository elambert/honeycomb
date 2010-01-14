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



package com.sun.honeycomb.datadoctor;

import java.util.Arrays;


/** 
 * Merge sorted lists to produce one sorted list, no duplicates.  Both
 * the input lists and result list are arrays of Strings, although this
 * algorithm could easily be generalized to work with other data types.  
 *
 * All methods are static; cannot instantiate an object of this class.
 */
class MergeSorted {

    static public class Result {
        String val;
        int count;
        
        public Result(String _val, int _count) {
            val = _val;
            count = _count;
        }
    }
    
    /** 
     * Combine pre-sorted lists into one sorted list, duplicates
     * removed.  This algorithm is O(n), we iterate the lists
     * multiple times.
     */
    static public Result[] mergeSortedLists (String[][] lists) {

        // compute maximum size of the resulting array
        int combinedLength = 0;
        for (int i=0; i < lists.length; i++) {
            combinedLength += lists[i].length;
        } 

        // indexes into the result list, and all input lists
        Result[] merged = new Result[combinedLength];
        int mergedIndex = 0;
        int[] listIndex = new int[lists.length];
        boolean[] listsDone = new boolean[lists.length];
        int doneCount = 0;

        // if list is empty, mark as done
        for (int i=0; i < lists.length; i++) {
            if (lists[i].length == 0) {
                listsDone[i] = true;
                doneCount++;
            }
        } 

        //  
        String lastVal = null;
        while (true) {

            // find the smallest value among current values in all lists
            String lowVal = null;
            int whichList = -1;
            for (int i=0; i < lists.length; i++) {

                // if done with this list, go to next one
                if (listsDone[i]) {
                    continue;
                }

                // get value at current index for this list
                String thisVal = lists[i][listIndex[i]];

                // if this val lower than current min, remember it 
                // Note: compateTo returns (neg val, 0, positive val)
                // and not (-1, 0, 1) like strcmp() and yes I discovered
                // that the hard way
                if (lowVal == null || thisVal.compareTo(lowVal) < 0) {

                    lowVal = thisVal; 
                    whichList = i;
                }
            } 

            if (DEBUG) {
                printStatus(whichList, lists, listIndex, PICK); 
            }

            // increment pointer beyond value we just picked, do it for
            // all lists in caes we have same value in multiple lists
            int valCount = 0;
            for (int i=0; i < lists.length; i++) {
                if (listsDone[i]) {
                    continue;
                }
                // get current value for this list
                String thisVal = lists[i][listIndex[i]];

                while (lowVal != null && thisVal.compareTo(lowVal) == 0) {
                    if (DEBUG) {
                        printStatus(i, lists, listIndex, SKIP); 
                    }
                    listIndex[i]++;
                    valCount++;

                    // if reached the end of a list, go to next one
                    if (listIndex[i] >= lists[i].length) {
                        if (!listsDone[i]) {
                            listsDone[i] = true;
                            doneCount++;
                            if (DEBUG) {
                                printStatus(i, lists, listIndex, END); 
                            }
                        }
                        break;
                    }
                    thisVal = lists[i][listIndex[i]];
                }
            }

            // update result list with lowest value
            merged[mergedIndex] = new Result(lowVal, valCount);
            mergedIndex++;
            
            // if all lists empty, we're done
            if (doneCount == lists.length) {
                break;
            }
        } 

        // create a new array that is the correct size, copy elements
        Result[] result = new Result[mergedIndex];
        System.arraycopy(merged, 0, result, 0, result.length);

        return result;
    }
    
    /** truncate everything after and including the given character */
    static public String[][] truncateAt(String c, String[][] lists) {

        String [][] result = new String[lists.length][];
        
        for (int i=0; i < lists.length; i++) {
            result[i] = new String[lists[i].length];
            for (int j=0; j < lists[i].length; j++) {
                String s = lists[i][j];
                int p = s.indexOf(c);
                if (p != -1) {
                    result[i][j] = s.substring(0, p);
                } else {
                    result[i][j] = s;
                }
            } 
        } 
        return result;
    }

    /** 
     * Print values at current index for each list, add symbols around
     * the value on which we just performed an operation. For debugging.
     */
    static private void printStatus(int whichList, String[][] lists,
                                    int[] listIndex, int op) {

        String s = "\n";
        for (int i=0; i < lists.length; i++) {

            int index = listIndex[i];
            String val = (index >= lists[i].length) ? " " : lists[i][index];

            char c = symbols[op];
            s += (i == whichList) ? c+val+c : " "+val+" ";
            s += "\t";
        } 
        System.out.print(s);
    }

    static private final int PICK = 0;
    static private final int SKIP = 1;
    static private final int END = 2;
    static private final char[] symbols = { '*', '|', '$' };

    static private final boolean DEBUG = false;

}


