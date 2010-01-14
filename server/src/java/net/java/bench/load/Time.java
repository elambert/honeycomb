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



/**
 * 
 */

package net.java.bench.load;

public final class Time {

    private static boolean inited = false;
    private static boolean loaded = false;

    private native static long _getProcessTime();

    public static synchronized long getProcessTime(Object test) {

	/* We would like to load the library in each instance of the
	 * classloader, but that fails with a
	 * "java.lang.UnsatisfiedLinkError: Native Library ... already
	 * loaded in another classloader" exception. So we fall-back
	 * to System.currentTimeMillis for multiple peers in the same
	 * JVM, while using the native call to get the actual user
	 * time used by the process for solo peers. This way, multiple
	 * peers can be used for load testing and solo peers for
	 * precise benchmark measurements.
	 * 
	 * Compare names of the classloaders because the two classes
	 * have been loaded via different classloaders, so instanceof
	 * will fail. 
	 */

	String classLoader = test.getClass().getClassLoader().toString();
	if (classLoader.startsWith("net.java.bench.load.PeerClassLoader")) {
	    return System.currentTimeMillis();
	}

	if (!inited) {
            try {
                System.loadLibrary("jxtabench");
                loaded = true;
                System.out.println("Reporting user time");
            } catch (Throwable ex) {
                loaded = false;
                System.err.println("Failed to load native library: " + ex);
                System.err.println("Reporting elapsed time");
            }
	    inited = true;
	}

	return loaded ? _getProcessTime() : System.currentTimeMillis();
    }
}
