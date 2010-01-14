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

import java.util.ArrayList;
import java.util.Iterator;

/**
 * A round robin FileCaseSize generator. 
 *
 * This generator will iterate, in a circular fashion, its way through the array of FileCasesTypes.VALID_FILESIZES.  
 * Calling generateFileSizeCase will return a FileSizeCase who is type is the next type in the VALID_FILESIZES array, once
 * the end of the array has been reached, it will begin again from the start of the array.
 *
 */

public class FileSizeCaseRoundRobinGenerator extends FileSizeCaseGenerator {

    public FileSizeCaseRoundRobinGenerator (long seed) {
	this(seed, null);
    }

    public FileSizeCaseRoundRobinGenerator(long seed, String [] skippedTypes) {
	super(seed);
	if (skippedTypes != null ) {
	    ArrayList tempList = new ArrayList();
	    for (int i = 0; i < m_fileSizeCaseTypes.length; i++) {
		for (int j = 0; j < skippedTypes.length; j++) {
		    if (skippedTypes[j].equals(m_fileSizeCaseTypes[i])) {
			break;
		    }
		    tempList.add(m_fileSizeCaseTypes[i]);
		}
	    }
	    m_types = new String [tempList.size()];
	    Iterator iter = tempList.iterator();
	    int k = 0;
	    while (iter.hasNext()) {
		m_types[k++] = (String)iter.next();
	    }
	}
	else {
	    m_types = m_fileSizeCaseTypes;
	}
    }

    protected String getNextType() {
	String value = m_types[pointer];
	if (++pointer == m_types.length )  {
	    pointer = 0;
	}
	return value;
    }

    private int pointer = 0;
    private String [] m_types;
    
} 
