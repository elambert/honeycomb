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



package com.sun.honeycomb.oa;

import com.sun.honeycomb.common.ConfigPropertyNames;
import com.sun.honeycomb.config.ClusterProperties;

import java.util.logging.Logger;
import java.util.LinkedList;
import java.util.logging.Level;

/**
    OAThreads holds the global freelist of OAThreadPools, as well as
    the instantiator's type-specific freelist of code pools (for
    creation, deoletion, etc.). The freelists are considered to be
    pools of pools, tho they are just ArrayLists. Methods are provided
    to get thread+code pools and return them to the freelists. 

    All pool sizes are the same, M+N.

    Keeping thread and code pools separate minimizes memory use. Having
    a global limit on thread pools risks deadlock if ever multiple pools
    were needed at once.
*/

public abstract class OAThreads {

    private static final int DEFAULT_MAXPOOLS = 10;
    
    protected static Logger LOG = Logger.getLogger(OAThreads.class.getName());

    protected interface ThreadDone {
        void done();
    }

    public interface RunnableCode {
        void run();
        void checkStatus() throws Exception;
    }

    
    private static int nbPoolsCreated = 0;
    private static final LinkedList availablePools = new LinkedList();
    private static int poolSize;
    private static int maxPools;

    private static boolean initialized = false;
    private static synchronized void init() {
        if (initialized)
            return;

        poolSize = OAClient.getInstance().getReliability().getTotalFragCount();

        ClusterProperties props = ClusterProperties.getInstance();
        maxPools =
            props.getPropertyAsInt(ConfigPropertyNames.PROP_OA_MAXPOOLS,
                                   DEFAULT_MAXPOOLS);

        LOG.info("Max OA thread pools: " + maxPools + "*" + poolSize);
        initialized = true;
    }

    private LinkedList availInstantiatedCode;
    private LinkedList allInstantiatedCode;
    private String tag;
    
    protected OAThreads(String nTag) {
        init();

        tag = nTag;
        availInstantiatedCode = new LinkedList();
        allInstantiatedCode = new LinkedList();
    }
    protected LinkedList getCodeList() {
        return allInstantiatedCode;
    }

    protected abstract RunnableCode buildCode();

    public OAThreadPool getPool() 
        throws OAException {
        OAThreadPool result = null;

        synchronized (availablePools) {

            //
            //  first get a pool - try what's available
            //
            if (availablePools.size() > 0) {
                result = (OAThreadPool)availablePools.remove(0);
            }

            //
            //  if no pool available, create or wait
            //
            if (result == null) {
                if (nbPoolsCreated < maxPools) {
                    String name = "OAPool-" + nbPoolsCreated;
                    result = createThreadPool(name, poolSize);
                    nbPoolsCreated++;
                } else {
                    String msg = "I've already created " + nbPoolsCreated +
                        " threads; waiting for a thread to become available...";
                    LOG.info(msg);

                    while (availablePools.size() == 0) {
                        try {
                            // wait for notification from checkInPool()
                            availablePools.wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                    // thanks to waiting, there is a pool now
                    result = (OAThreadPool)availablePools.remove(0);
                }
            }

            //
            //  now get code for the pool to run, reuse old or make new
            //
            RunnableCode[] code = null;
            if (availInstantiatedCode.size() > 0) {
                code = (RunnableCode[])availInstantiatedCode.remove(0);
            } else {
                code = new RunnableCode[poolSize];
                for (int i=0; i<code.length; i++) {
                    code[i] = buildCode();
                }
            }

            result.setCode(code);
        }
        
        return(result);
    }
    
    /**
     *  Put the pool in its (global) list, 
     *  the code in its (local) list, 
     *  and notify getPool()
     */
    public void checkInPool(OAThreadPool pool) {
        synchronized (availablePools) {
            availInstantiatedCode.add(pool.getCode());
            availablePools.add(pool);
            availablePools.notify();
        }
    }

    /**********************************************************************
     * For tests to override.
     */
    protected OAThreadPool createThreadPool(String tag, int size) {
        return new OAThreadPool(tag, size);
    }

    // Subclasses have to implement a routine to initialize the individual threads
}
