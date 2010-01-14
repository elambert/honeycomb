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



package com.sun.honeycomb.cm.jvm_agent;

import com.sun.honeycomb.cm.jvm_agent.CMAgent;
import com.sun.honeycomb.cm.jvm_agent.CMAException;
import com.sun.honeycomb.cm.jvm_agent.CMSAP;
import com.sun.honeycomb.cm.jvm_agent.Service;
import com.sun.honeycomb.cm.jvm_agent.ProxyService;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.honeycomb.cm.ManagedService;

public class Test 
    implements TestApi {

    public static final int START_PORT = 2035;
    private static final int NB_THREADS = 10;

    private static final long WARNING_TIMEOUT = 5000;
    private static final long ABORT_TIMEOUT = 15000;
    private static final long MAIN_SLEEP = 1000;
    private static final long MAIN_LOG = 10000;
    
    /* Set the next boolean to true, to buid a jar that can be run as a server */
    /* To run on a single machine, set :
     * - serverCompileMode = false
     * - SERVER_ADDR = "localhost"
     */
    private static final boolean serverCompileMode = false;
    public static final String SERVER_ADDR = "localhost";

    private static final Logger LOG = Logger.getLogger(Test.class.getName());

    public Test() {
        LOG.info("Test instanticated");
    }

    public void testCall(byte[] input) {
        if (input.length != 1024) {
            throw new RuntimeException("Invalid length ["+input.length+"]");
        }
        LOG.info("Test API called [Thread "+Thread.currentThread().getName()+"]");
    }

    public void run() {
        LOG.info("Run");
    }

    public void syncRun() {
        LOG.info("Sync Run");
    }

    public void shutdown() {
        LOG.info("Shutdown");
    }
 
    public ManagedService.ProxyObject getProxy() {
        return(null);
    }

    private static long heartbeat[];
    private static long warnings[];
    private static int nbCalls = 0;
    private static TestApi proxy;

    private static void initProxy() 
        throws CMAException {
        ProxyService service = new ProxyService(0, "tag");
        proxy = (TestApi)service.getProxy().api;
    }

    public static void main(String[] arg) {
        try {
            if (serverCompileMode) {
                CMAgent.createAgent(SERVER_ADDR, Test.class, "TestJVM");
            } else {
                CMAgent.createAgent("localhost", Test.class, "TestJVM");
            }
            CMAgent.addService(Test.class, "TestService");

            if (!serverCompileMode) {
                initProxy();
                heartbeat = new long[NB_THREADS];
                warnings = new long[NB_THREADS];

                for (int i=0; i<NB_THREADS; i++) {
                    heartbeat[i] = System.currentTimeMillis();
                    warnings[i] = heartbeat[i];
                    new Thread(new TestThread(i), buildThreadName(i)).start();
                }
            }

            long startTime = System.currentTimeMillis();
            long lastLog = startTime;

            while (true) {
                try {
                    Thread.sleep(MAIN_SLEEP);
                } catch (InterruptedException e) {}

                long current = System.currentTimeMillis();

                if (!serverCompileMode) {
                    for (int i=0; i<NB_THREADS; i++) {
                        if (current - heartbeat[i] > ABORT_TIMEOUT) {
                            LOG.severe("Thread ["+buildThreadName(i)+"] has been inactive for "+
                                       ((current-heartbeat[i])/1000)+" s. Aborting the test. Ran for "
                                       +((current-startTime)/1000)+" s.");
                            System.exit(1);
                        }
                        if (current - warnings[i] > WARNING_TIMEOUT) {
                            LOG.warning("Thread ["+buildThreadName(i)+"] has been inactive for "+
                                        ((current-heartbeat[i])/1000)+" s. Test has been running for "+
                                        ((current-startTime)/1000)+" s.");
                            warnings[i] = current;
                        }
                    }
                }

                if (current-lastLog > MAIN_LOG) {
                    LOG.info("The test has been running for "+
                             ((current-startTime)/1000)+" s. Performance is "+
                             (nbCalls*1000/(current-startTime))+" calls a s.");
                    lastLog += MAIN_LOG;
                    LOG.info("Free memory ["+Runtime.getRuntime().freeMemory()+"]");
                }
            }

        } catch (CMAException e) {
            LOG.log(Level.SEVERE,
                    "Exception ["+e.getMessage()+"]",
                    e);
        }
    }

    private static void heartbeat(int index) {
        long current = System.currentTimeMillis();
        warnings[index] = current;
        heartbeat[index] = current;
        nbCalls++;
    }

    public static String buildThreadName(int index) {
        return("TestThread-"+index);
    }

    public static class TestThread 
        implements Runnable {

        private int index;
        
        private TestThread(int nIndex) {
            index = nIndex;
        }

        public void run() {
            LOG.info("Thread ["+Thread.currentThread().getName()+"] started");
            while (true) {
                LOG.info("Thread ["+Thread.currentThread().getName()+"] makes call");
                proxy.testCall(new byte[1024]);
                heartbeat(index);
                Thread.currentThread().yield();
            }
        }
    }
}
