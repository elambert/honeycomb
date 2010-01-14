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


package com.sun.honeycomb.hctest.util;

import com.sun.honeycomb.hctest.cases.interfaces.FileSizeCase;
import com.sun.honeycomb.hctest.cases.interfaces.FileSizeCases;

import java.util.Hashtable;
import java.util.Random;

/**
 * An abstract class containing common code to generate FileSizeCases.
 */
public abstract class FileSizeCaseGenerator {

    /** 
     * Create a FileSizeCase Generator. All sub-classes constructors should call this
     * constructor.
     *
     * @param seed A seed which the FileSizeCases returned by this class use to determine
     * the actual size of the file.
     */
    public FileSizeCaseGenerator (long seed) {
	Random r = new Random(seed);
	for (int i = 0; i < m_fileSizeCaseTypes.length; i++) {
	    String currentSizeType = m_fileSizeCaseTypes[i];
	    FileSizeCase fsc = FileSizeCases.getCase(currentSizeType);
	    fsc.setSeed(r.nextLong());
	    m_fileSizeCases.put(currentSizeType,fsc);
	}
    }

    /**
     * Generate a new FileSizeCase which can be used to create a file whose size is one of the interesing 
     * Honeycomb file sizes. 
     *
     */
    public FileSizeCase generateFileSizeCase() {
	return (FileSizeCase) m_fileSizeCases.get(getNextType());
    }

    /** 
     * Determine the "type" of FileSizeCase returned when generateFileSizeCase is invoked.
     * Extending classes need to implement this method so that it does the right thing for the type generator being created.
     *
     * @return Name of FileSizeCase type to be returned. Must be one of the following String values listed below:
     *  "ZERO" -> A zero byte file
     *  "XXSMALL" -> A file size between 1  and 10 bytes
     *  "XSMALL" -> A file size between 11 and 100 bytes
     *  "SMALL" -> A file size between 101  and 1,000 (1K) bytes
     *  "MEDIUM"  -> A file size between 1,001 and 1,000,000 (1 MB) bytes
     *  "LARGE" -> A file size between 1,000,001 (~1MB) and  10,000,000 (10 MB) bytes
     *  "XLARGE" -> A file size between 10,000,000 (~10MB) and  1,000,000,000 (1 GB) bytes
     *  "XXLARGE" -> A file size between 1,000,000,001 (~1GB) and 100,000,000,000 (100 GB) bytes
     *  "XXXLARGE" -> A file size between 100,000,000,001 (~100GB) and  Long.MAX_VALUE
     *  "INVALID" -> A file size set to  HoneycombTestConstants.INVALID_FILESIZE
     */
    protected abstract String getNextType();

    public static final String FILESIZETYPE_MIXED = "MIXED";
    protected String [] m_fileSizeCaseTypes = FileSizeCases.VALID_FILESIZES;
    protected Hashtable m_fileSizeCases = new Hashtable();


}

