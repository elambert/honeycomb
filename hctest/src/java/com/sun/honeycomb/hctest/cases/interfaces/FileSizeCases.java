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
import java.util.ListIterator;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;

/**
 * This class declares logical mappings between named size classes ("small",
 * "large") and file sizes (10 bytes, 10 megs).
 */
public class FileSizeCases {

    public static final String FILESIZE_ZERO = "ZERO";
    public static final String FILESIZE_XXSMALL = "XXSMALL";
    public static final String FILESIZE_XSMALL = "XSMALL";
    public static final String FILESIZE_SMALL = "SMALL";
    public static final String FILESIZE_MEDIUM = "MEDIUM";
    public static final String FILESIZE_LARGE = "LARGE";
    public static final String FILESIZE_XLARGE = "XLARGE";
    public static final String FILESIZE_XXLARGE = "XXLARGE";
    public static final String FILESIZE_XXXLARGE = "XXXLARGE";
    public static final String FILESIZE_BOTROSARNOUD = "botros-arnoud";
    public static final String FILESIZE_SINGLECHUNK = "single-chunk";
    public static final String FILESIZE_MULTICHUNK = "multi-chunk";
    public static final String FILESIZE_INVALID = "INVALID";
    public static final String [] VALID_FILESIZES = new String [] { 
                                    FILESIZE_ZERO, FILESIZE_XXSMALL,
                                    FILESIZE_XSMALL, FILESIZE_SMALL,
                                    FILESIZE_MEDIUM, FILESIZE_LARGE,
                                    FILESIZE_XLARGE, FILESIZE_XXLARGE,
                                    FILESIZE_XXXLARGE, FILESIZE_BOTROSARNOUD,
                                    FILESIZE_SINGLECHUNK, FILESIZE_MULTICHUNK };

    public static ArrayList allCases = null;

    public static FileSizeCase INVALID_FILE_SIZE_CASE =
        new FileSizeCase("INVALID",
            HoneycombTestConstants.INVALID_FILESIZE,
            HoneycombTestConstants.INVALID_FILESIZE);
    
    // XXX this case currently hangs...
    public static FileSizeCase ZERO =
        new FileSizeCase(FILESIZE_ZERO, 0, 0);
    
    public static FileSizeCase XXSMALL =
        new FileSizeCase(FILESIZE_XXSMALL, 1, 10);
    
    public static FileSizeCase XSMALL =
        new FileSizeCase(FILESIZE_XSMALL, 11, 100);
    
    public static FileSizeCase SMALL =
        new FileSizeCase(FILESIZE_SMALL, 101, 1000);
    
    public static FileSizeCase MEDIUM =
        new FileSizeCase(FILESIZE_MEDIUM, 1001, 1000000);
    
    public static FileSizeCase LARGE =
        new FileSizeCase(FILESIZE_LARGE, 1000001, 10000000);
    
    public static FileSizeCase XLARGE =
        new FileSizeCase(FILESIZE_XLARGE, 10000001, 1000000000);
    
    public static FileSizeCase XXLARGE =
        new FileSizeCase(FILESIZE_XXLARGE, 1000000001L, 100000000000L);

    public static FileSizeCase XXXLARGE =
        new FileSizeCase(FILESIZE_XXXLARGE, 100000000001L, Long.MAX_VALUE);

    // XXX could pick better range for this
    public static FileSizeCase BOTROSARNOUD = 
        new FileSizeCase(FILESIZE_BOTROSARNOUD, 1, 200);

    public static FileSizeCase SINGLECHUNK =
        new FileSizeCase(FILESIZE_SINGLECHUNK, 0,
            HoneycombTestConstants.OA_MAX_CHUNK_SIZE);

    // XXX could pick better range for this
    public static FileSizeCase MULTICHUNK =
        new FileSizeCase(FILESIZE_MULTICHUNK,
            HoneycombTestConstants.OA_MAX_CHUNK_SIZE + 1, 
            2 * HoneycombTestConstants.OA_MAX_CHUNK_SIZE); 
            
    private static void initAllCases() {
        // Initialize this once...
	if (allCases == null) {
	    allCases = new ArrayList();
            allCases.add(ZERO);
            allCases.add(XXSMALL);
            allCases.add(XSMALL);
            allCases.add(SMALL);
            allCases.add(MEDIUM);
            allCases.add(LARGE);
            allCases.add(XLARGE);
            allCases.add(XXLARGE);
            allCases.add(XXXLARGE);
            allCases.add(BOTROSARNOUD);
            allCases.add(SINGLECHUNK);
            allCases.add(MULTICHUNK);
        }

    }

    /**
     * For a given size, return what named size class the size fits into.
     */
    public static String lookupCaseName(long size) {
	initAllCases();
        ListIterator li = allCases.listIterator();
        while (li.hasNext()) {
            FileSizeCase fsc = (FileSizeCase) li.next();
            if (size >= fsc.min && size <= fsc.max) {
                return (fsc.name);
            }
        }

        return (INVALID_FILE_SIZE_CASE.name);
    }

    /** 
     * For a given type Name, return a case of that type.
     */
    public static FileSizeCase getCase(String caseType) throws IllegalArgumentException {
	FileSizeCase result = null;
	if (allCases == null) {
	    initAllCases();
	}
	if  (caseType.equals(FILESIZE_INVALID)) {
	    result = INVALID_FILE_SIZE_CASE;
	}
	else {
	    ListIterator li = allCases.listIterator();
	    while (li.hasNext()) {
		FileSizeCase currentCase = (FileSizeCase) li.next();
		if (currentCase.name.equals(caseType)) {
		    result = currentCase;
		    break;
		}
	    }
	}
	
	if (result == null ) {
	    throw new IllegalArgumentException("Invalid filesize case type: " + caseType);
	}

	return result;
    }

}
