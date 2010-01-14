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
import java.util.Iterator;

/** 
 * A thread which execute a particular Task for a predetermined number of iterations of 
 * for a predetermined amount of time or number of iterations. Ideal for 
 * load testing.
 */

public class LoadTesterThread implements Runnable {

    public void setUp(int mode, long value, SimpleTask task) throws IllegalArgumentException {
	if (task == null) {
	    throw new IllegalArgumentException("Task can't be set to null");
	}
	m_task  = task;
	String modeDesc="";
	switch (mode) {
	    case (TIMER_MODE):
		    m_mode = TIMER_MODE;
		    modeDesc="time limit";
		    break;
	    case (FILESET_MODE):
		    m_mode = FILESET_MODE;
		    modeDesc="file set size";
		    break;
	    default:
		throw new IllegalArgumentException("Unkown mode");
	}

	if (value < 0) {
	    throw new IllegalArgumentException("Invalid " + modeDesc + " value: " +value);
	}
    
	m_value = value;
    }


    public void run () throws IllegalArgumentException {

	m_name = Thread.currentThread().getName();
	m_result = new CmdResult();
	CmdResult taskRes = null;
	boolean pass = false;

	//gotta have a task to do
	if (m_task == null) {
	    throw new IllegalStateException("No task has been specified");
	}

	//gotta have a mode
	if (m_mode == -1) {
	    throw new IllegalStateException("No mode has been specified");
	}

	pass = true;
	m_endTime = System.currentTimeMillis() + m_value; // only used if this is a timed thread 

	// do it already
	while (!isDone() && pass == true) {

	    try {
		taskRes = m_task.doIt();
	    }
	    catch (Exception e) {
		m_result.addException(e);
		pass = false;
	    }

	    m_iteration++;
	    taskRes.logExceptions(Thread.currentThread().getName());

	    // if one of my tasks failed, i failed
	    if (!taskRes.pass) {
		pass = false;
	    }

	    // accumulate filesize
	    if (m_result.filesize == -1)  {
		m_result.filesize = taskRes.filesize;
	    }
	    else{
		m_result.filesize += taskRes.filesize;
	    }

	    // roll up the exceptions
	    if (taskRes.thrown != null) {
		Iterator iter = taskRes.thrown.iterator();
		while (iter.hasNext()) {
		    m_result.addException((Throwable)iter.next());
		}
	    }

	    // roll up the log enteries
	    if (taskRes.logTag != null && (!taskRes.logTag.trim().equals(""))) {
		if (m_result.logTag == null) {
		    m_result.logTag = (Thread.currentThread().getName() + "::" + taskRes.logTag);
		}
		else
		    m_result.logTag += (Thread.currentThread().getName() + "::" + taskRes.logTag);
	    }
	}

	m_task.tearDown(); 
	m_result.count = m_iteration;
	m_result.pass = pass;

    }

    public CmdResult getResult () {
	return m_result;
    }

    public String getName () {
	return m_name;
    }

    private boolean isDone() {
	boolean isDone = true;
	switch (m_mode) {
	    case (TIMER_MODE):
				isDone = (System.currentTimeMillis() > m_endTime);
				break;
	    case (FILESET_MODE):
				isDone = (m_iteration >= m_value);
				break;
	}
	return isDone;
    }

    public static final int TIMER_MODE = 0;
    public static final int FILESET_MODE = 1;

    private int m_iteration = 0;
    private long m_endTime = 0;
    private long m_value = 0;
    private int m_mode = -1;
    private SimpleTask m_task = null;
    private CmdResult m_result;
    private String m_name;
}

