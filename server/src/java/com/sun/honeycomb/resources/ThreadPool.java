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



package com.sun.honeycomb.resources;

import java.util.logging.Logger;
import EDU.oswego.cs.dl.util.concurrent.*;
import com.sun.honeycomb.common.InternalException;

/**
 * Super basic thread pool to limit the number of, and not require
 * users to block on the creation of threads.
 * This class is a singleton */
public class ThreadPool {
    
    // SINGLETON METHODS //
    
    /**
     * ThreadPool is a singleton.  The first time this method is called,
     * an instance is created and returned.  Subsequent calls
     * return a reference to the same object.
     */
    public static ThreadPool getInstance() {
        synchronized(LOG) {
            if (threadpool == null) {
                // This is the first time getInstance has been called
                threadpool = new ThreadPool();
            }
        }
        return threadpool;
    }

    private ThreadPool() {
        try {
            getConfig();
            
            pool = new PooledExecutor(new LinkedQueue());
            pool.setKeepAliveTime(-1); // live forever
            pool.createThreads(numThreads);

        } catch (InternalException ie) {
            LOG.severe("ThreadPool failed to get configuration");
        }
    }
    
    /** Read in any config values like min/max pool size */
    private void getConfig() throws InternalException {
        // TODO - use ClusterProperties
        numThreads = 32;
    }
    
    /** 
     * This static method can be used to see whether the ThreadPool
     * singleton has been instantiated yet or not (in other words,
     * whether getInstance has ever been called before or not).
     */
    public static boolean isInstantiated() {
        return !(threadpool == null);
    }

    // ThreadPool functionality //
    
    /** Arrange for the given cmd to be executed by a thread in this pool.*/
    public void execute(java.lang.Runnable cmd) throws InterruptedException {
        pool.execute(cmd);
    }

    protected static final Logger LOG = 
        Logger.getLogger(ThreadPool.class.getName());
    private static ThreadPool threadpool = null;
    
    private int numThreads = -1; // configurable

    private PooledExecutor pool = null;
}


