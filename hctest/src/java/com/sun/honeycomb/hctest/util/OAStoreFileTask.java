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

import com.sun.honeycomb.client.ObjectArchive;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.hctest.CmdResult;
import com.sun.honeycomb.hctest.cases.interfaces.FileSizeCase;
import com.sun.honeycomb.hctest.cases.interfaces.FileSizeCases;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.TestCase;

import java.util.Random;

/** 
 * A task that stores a random file to an ObjectArchive.
 */

public class OAStoreFileTask implements SimpleTask {

    /**
     * Create a new instance of the OAStoreFileTask.
     * @param oa An Honeycomb object archive instance. 
     * @param seed seed value used to random, yet repeatable values for filesize and data.
     * @param fileSizeType Size Class of file to be stored
     * @param tc TestCase to which this tasks belongs.
     */
    public OAStoreFileTask(ObjectArchive oa, long seed, String fileSizeType, TestCase tc) {
	m_oa = oa;
	m_fileSizeType = fileSizeType;
	m_tc = tc;
	m_random = new Random(seed);
	m_fileSizeSeed = m_random.nextLong();
    }

    /** Setup the task. 
     */
    public void setUp() {
	init();
    }
    

    /** 
     * Execute the task. 
     * @return true if the store succeeded, else false.
     */ 
    public CmdResult doIt() throws IllegalStateException {

	//initialize
	CmdResult result = new CmdResult();
	boolean pass = true;
	if (!m_inited) {
	    init();
	}

	//store files
	m_dataSeed = m_random.nextLong();
	FileSizeCase fsc = m_fscg.generateFileSizeCase();
	long fileSize =fsc.getSize();
	SystemRecord sr = null;
	HCTestReadableByteChannel bc = null;

	try {
	    bc = new HCTestReadableByteChannel(fileSize,m_dataSeed,m_tc);
	} catch (Exception e) {
	    result.addException(e);
	    result.logTag = "Unable to generate byte channel for writting ... ";
	    pass = false;
	    return result; // got nothing to do, so get out
	}
	try {
	    sr = HoneycombTestClient.storeObject(m_oa,bc,m_tc);
	}
	catch (Exception e) {
	    result.addException(e);
	    pass = false;
	}

	if (sr == null) { // make sure that I got a SystemRecord to look at
	    result.logTag += m_oaClassName + ".store(ReadableByteChannel) returned a null system record.";
	    pass = false;
	}

	if (pass && (fileSize != sr.getSize()) ) { //  make sure that size of file written is what I asked for
	    pass = false;
	    result.logTag += "Size retreived from SystemRecord.getSize() does not equal size of file requested.";
	    result.logTag += "SystemRecord.getSize() = " + sr.getSize();
	    result.logTag += "Filesize = " + fileSize;
	} else if (pass && bc.computeHash().equals(sr.getDataDigest())) { // make sure that the contents of the file is what I expect
	    pass = false;
	    result.logTag += "DataDigest retreived from SystemRecord.getDataDigest() does not equal hash of file as computed by HCTestByteChannel.computeHash()";
	    result.logTag += "SystemRecord.getDataDigest() = " + sr.getDataDigest();
	    result.logTag += "HCTestByteChannel.getHash() = " + bc.getHash();
	}
	if (result.filesize == -1)  {
	    if (sr != null)
		result.filesize = sr.getSize();
	}
	else {
	    if (sr != null)
		result.filesize += sr.getSize();
	}

	result.pass = pass;


	return result;
    }

    /**
     * Perform any cleanUp required by the task.
     */
    public void tearDown() {
    }

    private void init() throws IllegalStateException {
	//gotta have an oa
	if (m_oa == null) {
	    throw new IllegalStateException("ObjectArchive can not be null");
	}

	m_oaClassName = m_oa.getClass().getName();

	//gotta have a tc
	if (m_tc == null) {
	    throw new IllegalStateException("TestCase can not be null");
	}

	//set up my filesize type
	if (!m_fileSizeType.equals(FileSizeCaseGenerator.FILESIZETYPE_MIXED) ) {
	    m_fscg = new FileSizeCaseStaticGenerator(m_fileSizeSeed, m_fileSizeType);
	}
	else {
	    m_fscg = new FileSizeCaseRoundRobinGenerator(m_fileSizeSeed);
	}
	m_inited = true;
    }

    class FileSizeCaseStaticGenerator extends FileSizeCaseGenerator {
	public FileSizeCaseStaticGenerator(long seed,String type) {
	    super (seed);
	    _type = type;
	}

	public String getNextType() {
	    return _type;
	}


	String _type;
    }


    private String m_fileSizeType;
    private ObjectArchive m_oa;
    private boolean m_inited = false;
    private long m_dataSeed;
    private long m_fileSizeSeed;
    private Random m_random;
    private TestCase m_tc;
    private FileSizeCaseGenerator m_fscg;
    private String m_oaClassName;
}
