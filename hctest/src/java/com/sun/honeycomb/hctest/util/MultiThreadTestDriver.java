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

import com.sun.honeycomb.hctest.CmdResult;
import com.sun.honeycomb.test.util.Log;

import java.util.ArrayList;
import java.util.Iterator;

public class MultiThreadTestDriver {

    /**
     * Create an instance of MultiThreadTestDriver. 
     * A MulitTestThreadDriver is used to manage the execution of serveral threads,
     * each executing a task.
     *
     * @param task The task to be executed within the threads managed by this instance of 
     * MultiTestThreadDriver.
     *
     * @param mode The duration mode of the threads executed by this class. FILESET_MODE indicates that 
     * that the threads will execute the task for a set number of iterations. While TIMER_MODE indicates 
     * that the threads will execute the task for set period of time.
     *
     * @param value The value associated with the duration mode. If duration mode is set to FILESET_MODE, then
     * this value represents the number of times (interations) that the task will be executed. If the duration
     * mode is set to TIMER_MODE, the value represents how long the thread will execute.
     *
     * @param threads The number of threads started by this class.
     *
     * @param name A string description of the task.
     */
    public MultiThreadTestDriver (SimpleTask task, int mode, long value, int threads, String name) {
	m_task = task;
	m_mode = mode;
	m_value = value;
	m_numThreads = threads;
	m_testName = name;
    }

    /**
     *
     */
    public CmdResult run() {

	CmdResult result = new CmdResult();
	boolean pass = true;

	for (int i = 0; i < m_numThreads; i++) {
	    LoadTesterThread ltt = new LoadTesterThread();
	    ltt.setUp(m_mode,m_value, m_task);
	    Thread aThread = new Thread(ltt,"Thread"+i);
	    m_threads.add(aThread);
	    m_tests.add(ltt);
	}

	//start all the threads
	Iterator startIter = m_threads.iterator();
	while (startIter.hasNext()) {
	    Thread ct = (Thread) startIter.next();
	    ct.start();
	}

	//Wait for all threads to finish
	Iterator stopIter = m_threads.iterator();
	while (stopIter.hasNext()) {
	    Thread ct = (Thread) stopIter.next();
	    try {
		ct.join();
	    }
	    catch (Exception e) {
	    }
	}

	//find out what happened
	Iterator statIter = m_tests.iterator();
	int x = 0;
	while (statIter.hasNext()) {
	    LoadTesterThread ct = (LoadTesterThread) statIter.next();
	    CmdResult thisRes = ct.getResult();
	    if (!thisRes.pass) {
		pass = false;
	    }
	    if (result.filesize == -1)  {
		result.filesize = thisRes.filesize;
	    }
	    else { 
		result.filesize += thisRes.filesize;
	    }

	    if (result.count == -1)  {
		result.count = thisRes.count;
	    }
	    else {
		result.count += thisRes.count;
	    }
	    Log.INFO("iter " + x++ + ": " + (thisRes.pass ? "passed. " : "failed. ") + " files: " + thisRes.count +  " bytes: " + thisRes.filesize); 
	    thisRes.logExceptions(m_testName + ":" +ct.getName(),true);
	    if (thisRes.logTag != null) {
		Log.INFO(thisRes.logTag);
	    }
	}
	result.pass = pass;
	return result;
    }

    int m_numThreads = 0;
    boolean m_result = true;
    SimpleTask m_task = null;
    int m_mode;
    long m_value; 
    ArrayList m_threads = new ArrayList();
    ArrayList m_tests = new ArrayList();
    String m_testName;

}

