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



package com.sun.honeycomb.hctest.cases.interfaces;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.Iterator;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.util.Log;

/**
 * This class builds up an array of the interesting file size test cases for
 * honeycomb.
 */
public class HCFileSizeCases {

    public static ArrayList uniqueSizeList = null;
    public static ArrayList newSizeList = null;
    public static TreeSet hcSizeSet = null;
    public static TreeSet hcBasicSizeSet = null;
    public static ArrayList finalSizeList = null;

    public static ArrayList getSizeList() {
        // If we've already constructed the list, don't do it again
        if (finalSizeList != null) {
            return (finalSizeList);
        }

        // See the API test plan for explanation of file sizes to use
        hcSizeSet = new TreeSet();
        hcBasicSizeSet = new TreeSet();

        // Small files
        for (long l = 0; l <= (3 * HoneycombTestConstants.OA_DATA_FRAGS); l++) {
            hcBasicSizeSet.add(new Long(l));
        }

        // Frag Size Boundaries
        for (long l = 1; l <= (3 * HoneycombTestConstants.OA_DATA_FRAGS); l++) {
            hcBasicSizeSet.add(
                new Long((l * HoneycombTestConstants.OA_FRAGMENT_SIZE) - 1));
            hcBasicSizeSet.add(
                new Long(l * HoneycombTestConstants.OA_FRAGMENT_SIZE));
            hcBasicSizeSet.add(
                new Long((l * HoneycombTestConstants.OA_FRAGMENT_SIZE) + 1));
            hcBasicSizeSet.add(
                new Long((l * HoneycombTestConstants.OA_FRAGMENT_SIZE) +
                (HoneycombTestConstants.OA_FRAGMENT_SIZE / 2)));
        }

        // Block Size Boundaries
        for (long l = 1; l <= (3 * HoneycombTestConstants.OA_DATA_FRAGS); l++) {
            hcBasicSizeSet.add(
                new Long((l * HoneycombTestConstants.OA_BLOCK_SIZE) - 1));
            hcBasicSizeSet.add(
                new Long(l * HoneycombTestConstants.OA_BLOCK_SIZE));
            hcBasicSizeSet.add(
                new Long((l * HoneycombTestConstants.OA_BLOCK_SIZE) + 1));
            hcBasicSizeSet.add(
                new Long((l * HoneycombTestConstants.OA_BLOCK_SIZE) +
                (HoneycombTestConstants.OA_BLOCK_SIZE / 2)));
        }

        // Chunk Size Boundaries
        for (long l = 1; l <= (3 * HoneycombTestConstants.OA_DATA_FRAGS); l++) {
            hcBasicSizeSet.add(
                new Long((l * HoneycombTestConstants.OA_MAX_CHUNK_SIZE) - 1));
            hcBasicSizeSet.add(
                new Long(l * HoneycombTestConstants.OA_MAX_CHUNK_SIZE));
            hcBasicSizeSet.add(
                new Long((l * HoneycombTestConstants.OA_MAX_CHUNK_SIZE) + 1));
            hcBasicSizeSet.add(
                new Long((l * HoneycombTestConstants.OA_MAX_CHUNK_SIZE) +
                (HoneycombTestConstants.OA_MAX_CHUNK_SIZE / 2)));
        }

        // repeat above cases using OA_MAX_CHUNK_SIZE + caseSize
        Iterator li = hcBasicSizeSet.iterator();
        hcSizeSet.addAll(hcBasicSizeSet);
        while (li.hasNext()) {
            Long l = (Long)li.next();
            hcSizeSet.add(
                new Long(l.longValue() +
                         HoneycombTestConstants.OA_MAX_CHUNK_SIZE));
        }

        // Large files
        hcSizeSet.add(new Long(HoneycombTestConstants.ONE_MEGABYTE));
        hcSizeSet.add(new Long(10 * HoneycombTestConstants.ONE_MEGABYTE));
        hcSizeSet.add(new Long(100 * HoneycombTestConstants.ONE_MEGABYTE));
        hcSizeSet.add(new Long(HoneycombTestConstants.ONE_GIGABYTE));
        hcSizeSet.add(new Long(10 * HoneycombTestConstants.ONE_GIGABYTE));
        hcSizeSet.add(new Long(100 * HoneycombTestConstants.ONE_GIGABYTE));

        // Maybe pick some random sizes?

        // Convert the sorted set to an ArrayList since that's what the lib uses
        finalSizeList = new ArrayList(hcSizeSet);
        return (finalSizeList);
    }

    private static ArrayList retrieveReconSizes = null;
    public static ArrayList getRetrieveReconSizes() {
        if (retrieveReconSizes == null) {
            retrieveReconSizes = new ArrayList();
            TreeSet sizes = new TreeSet();

            // single chunk sizes
            sizes.add(new Long(0));
            sizes.add(new Long(1));
            sizes.add(new Long(HoneycombTestConstants.OA_BLOCK_SIZE / 2)) ; 
            sizes.add(new Long(HoneycombTestConstants.OA_BLOCK_SIZE - 1)) ; 
            sizes.add(new Long(HoneycombTestConstants.OA_BLOCK_SIZE)) ; 
            sizes.add(new Long(HoneycombTestConstants.OA_BLOCK_SIZE + 1)) ; 
            sizes.add(new Long(3 * HoneycombTestConstants.OA_BLOCK_SIZE / 2)) ; 
            sizes.add(new Long(2 * HoneycombTestConstants.OA_BLOCK_SIZE)) ; 
            sizes.add(new Long(5 * HoneycombTestConstants.OA_BLOCK_SIZE / 2)) ; 
            sizes.add(new Long(3 * HoneycombTestConstants.OA_BLOCK_SIZE)) ; 
            sizes.add(new Long(3 * HoneycombTestConstants.OA_BLOCK_SIZE)) ; 
            sizes.add(new Long(HoneycombTestConstants.OA_MAX_CHUNK_SIZE / 2)) ; 
            sizes.add(new Long(HoneycombTestConstants.OA_MAX_CHUNK_SIZE - (HoneycombTestConstants.OA_BLOCK_SIZE - 1))) ; 
            sizes.add(new Long(HoneycombTestConstants.OA_MAX_CHUNK_SIZE - (HoneycombTestConstants.OA_BLOCK_SIZE / 2))) ; 
            sizes.add(new Long(HoneycombTestConstants.OA_MAX_CHUNK_SIZE - 1)) ; 
            sizes.add(new Long(HoneycombTestConstants.OA_MAX_CHUNK_SIZE)) ; 

            // multi-chunk sizes
            Object [] singleChunk = sizes.toArray();
            for (int i=0; i < singleChunk.length; i++) {
                Long size = (Long) singleChunk[i];
                sizes.add(new Long(HoneycombTestConstants.OA_MAX_CHUNK_SIZE - size.longValue())) ; 
                sizes.add(new Long(HoneycombTestConstants.OA_MAX_CHUNK_SIZE + size.longValue())) ; 
                sizes.add(new Long((2 * HoneycombTestConstants.OA_MAX_CHUNK_SIZE) - size.longValue())) ; 
                sizes.add(new Long((2 * HoneycombTestConstants.OA_MAX_CHUNK_SIZE) + size.longValue())) ; 
                sizes.add(new Long((3 * HoneycombTestConstants.OA_MAX_CHUNK_SIZE) - size.longValue())) ; 
                sizes.add(new Long((3 * HoneycombTestConstants.OA_MAX_CHUNK_SIZE) + size.longValue())) ; 
            }
            sizes.add(new Long(3 * HoneycombTestConstants.OA_MAX_CHUNK_SIZE / 2));
            sizes.add(new Long(5 * HoneycombTestConstants.OA_MAX_CHUNK_SIZE / 2));

            retrieveReconSizes.addAll(sizes);
        }
        return retrieveReconSizes;
    }

    public static ArrayList getUniqueSizeList() {
        // If we've already constructed the list, don't do it again
        if (uniqueSizeList != null) {
            return uniqueSizeList;
        } 

        // See the API test plan for explanation of file sizes to use

        TreeSet sizes = new TreeSet();

        // small files

        sizes.add(new Long(0));
        sizes.add(new Long(HoneycombTestConstants.DEFAULT_FILESIZE_XSMALL));
        sizes.add(new Long(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL));
        sizes.add(new Long(1000));

        ArrayList offsetList = new ArrayList();
        offsetList.add(new Long (1));
        offsetList.add(new Long (-1));





        // Frag Size Boundaries
        //
        // Each frag size is (-1,0,+1)
        // .5, 1, 1.5, 2, 2.5, 3.
        //
        for (long l = (long)(HoneycombTestConstants.OA_FRAGMENT_SIZE * 0.5); 
             l <= HoneycombTestConstants.OA_FRAGMENT_SIZE * 3;
             l += HoneycombTestConstants.OA_FRAGMENT_SIZE / 2 ) {
            sizes.add (new Long(l));
            if(l % HoneycombTestConstants.OA_FRAGMENT_SIZE == 0) {
                
                for (int i = 0; i < offsetList.size(); i++) {    
                    sizes.add(new Long(l + ((Long) offsetList.get(i)).longValue()));
                }
            }

        }


        // Block Size Boundaries
        //
        // Each bloxk size is (-1,0,+1)
        // .5, 1, 1.5, 2, 2.5, 3.
        //
        //            for (int i = 0; i < fileSizes.size(); i++) {    

        for (long l = (long)(HoneycombTestConstants.OA_BLOCK_SIZE * 0.5) ; 
             l <= HoneycombTestConstants.OA_BLOCK_SIZE * 3;
             l += HoneycombTestConstants.OA_BLOCK_SIZE / 2 ) {
            sizes.add (new Long(l));
            if(l % HoneycombTestConstants.OA_BLOCK_SIZE == 0) {                
                for (int i = 0; i < offsetList.size(); i++) {    
                    sizes.add(new Long(l + ((Long) offsetList.get(i)).longValue()));
                }
            }

        }




        // chunk Size Boundaries
        //
        // Each chunk size is (-1,0,+1) * fragment_Size
        // .5, 1, 1.5, 2, 2.5, 3.
        //
        // Also test chunk boundaries
        //
        for (long l = (long)(HoneycombTestConstants.OA_MAX_CHUNK_SIZE * 0.5) ; 
             l <= HoneycombTestConstants.OA_MAX_CHUNK_SIZE * 3 ;
             l += HoneycombTestConstants.OA_MAX_CHUNK_SIZE / 2 ) {
            sizes.add (new Long(l));
            if(l % HoneycombTestConstants.OA_MAX_CHUNK_SIZE == 0) {
                
                for (int i = 0; i < offsetList.size(); i++) {    
                    sizes.add(new Long(l + 
                                       ( ((Long) offsetList.get(i)).longValue() * 
                                         HoneycombTestConstants.OA_BLOCK_SIZE )));
                    // also test chunk boundaries
                    sizes.add(new Long(l + 
                                       ((Long) offsetList.get(i)).longValue()));
                }
            }

        }

        //
        // Max sizes
        //

        sizes.add(new Long((long)(HoneycombTestConstants.OA_MAX_SIZE * 0.5)));
        sizes.add(new Long(HoneycombTestConstants.OA_MAX_SIZE));
        sizes.add(new Long(HoneycombTestConstants.OA_MAX_SIZE - HoneycombTestConstants.OA_BLOCK_SIZE ));


        ArrayList uniqueSizeList = new ArrayList(sizes);


        return (uniqueSizeList);
    }

    public static void main(String [] args) throws Throwable {
        ArrayList s = getRetrieveReconSizes();
        for (int i = 0; i < s.size(); i++) {
            System.out.println(s.get(i));
            System.out.flush();
        }
    }
}
