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

import java.util.Enumeration;
import java.util.Hashtable;

import com.sun.honeycomb.test.util.Log;

class ConnectionReaper extends Thread {

	private Hashtable pools;

	// Check connections every 5m
	private final long delay = 300000;

	private boolean timeToRun = true;

	ConnectionReaper(Hashtable pools) {
		this.pools = pools;
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				closeConnections();
			}
		});
	}

	public void run() {
		while (timeToRun) {
			try {
				sleep(delay);
			} catch (InterruptedException e) {
			}
			cleanUpConnections();
		}
		closeConnections();
	}

	private void cleanUpConnections() {
		synchronized (pools) {
			Enumeration elems = pools.keys();
			while (elems.hasMoreElements() && timeToRun) {
				Object key = elems.nextElement();
				ConnectionPool pool = (ConnectionPool) pools.get(key);
				printInfo(pool,key);
				pool.reapConnections();
			}
		}
	}

	private void closeConnections() {
		synchronized (pools) {
			Enumeration elems = pools.keys();
			while (elems.hasMoreElements() && timeToRun) {
				Object key = elems.nextElement();
				ConnectionPool pool = (ConnectionPool) pools.get(key);
				printInfo(pool,key);
				pool.closeConnections();
			}
		}
	}

	private void printInfo(ConnectionPool pool, Object key) {
		Log.DEBUG("*** Stats for ConnectionPool: " + key + " ***");
		Log.DEBUG("Open Connections: " + pool.countOpenConnections());
		Log.DEBUG("Existing Connections: " + pool.countExistingConnections());
	}

	public void timeToStop() {
		timeToRun = false;
		wakeUp();
		Log.DEBUG("Shutting down ConnectionReaper thread.");
	}

	public void wakeUp() {
		interrupt();
	}
}
