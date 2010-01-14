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
 * XXX This is a placeholder for legacy code to interrupt OAThreads. This
 * should all go away after DAAL threads are implemented.
 **/

package com.sun.honeycomb.oa;

import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.delete.Constants;

import java.lang.reflect.Field;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ThreadInterruptFault extends Fault {
    private static final Logger log
        = Logger.getLogger(ThreadInterruptFault.class.getName());

    public static final FaultType INTERRUPT_CREATOR_ERROR
        = new FaultType("InterruptCreatorThreads");
    public static final FaultType INTERRUPT_CLOSE_ERROR
        = new FaultType("InterruptCloseThreads");
    public static final FaultType INTERRUPT_CREATOR_AND_CLOSE_ERROR
        = new FaultType("InterruptCreatorAndCloseThreads");

    private static TestCreatorThreads testCreatorThreads
        = new TestCreatorThreads();
    private static TestWriteAndCloseThreads testWriteAndCloseThreads
        = new TestWriteAndCloseThreads();
    private static boolean creatorThreadsReplaced;
    private static boolean writeThreadsReplaced;

    /**********************************************************************/
    public ThreadInterruptFault(String name, FaultEvent event, FaultType type) {
        super(name, event, type);
        if (type == INTERRUPT_CREATOR_ERROR) {
            prepareForCreatorInterrupt();
        } else if (type == INTERRUPT_CLOSE_ERROR) {
            prepareForCreatorInterrupt(false);
            prepareForCloseInterrupt();
        } else {
            prepareForCreatorInterrupt();
            prepareForCloseInterrupt();
        }
    }

    /**********************************************************************/
    public void setFaultyFragment(int index) {
        if (index >= Constants.reliability.getTotalFragCount()) {
            throw new IllegalArgumentException("index out of bounds");
        }
        testCreatorThreads.setFaultyFragment(index);
        testWriteAndCloseThreads.setFaultyFragment(index);
    }

    /**********************************************************************/
    public void setTriggerCount(int count) {
        testWriteAndCloseThreads.setFaultCount(count);
        testCreatorThreads.setFaultCount(count);
    }

    /**********************************************************************/
    private synchronized void prepareForCreatorInterrupt() {
        prepareForCreatorInterrupt(true);
    }

    /**********************************************************************/
    private synchronized void prepareForCreatorInterrupt(boolean active) {
        if (!creatorThreadsReplaced) {
            creatorThreadsReplaced = true;
            testCreatorThreads.activateInterrupt = active;
            try {
                Field creatorThreadsField
                    = ReflectedAccess.getField(FragmentFileSet.class,
                                               "creatorThreads");
                creatorThreadsField.set(null, testCreatorThreads);
            } catch(IllegalAccessException iae) {
                throw new RuntimeException(iae);
            }
        }
    }

    /**********************************************************************/
    private synchronized void prepareForCloseInterrupt() {
        if (!writeThreadsReplaced) {
            writeThreadsReplaced = true;
            try {
                Field writeAndCloseThreadsField
                    = ReflectedAccess.getField(FragmentFileSet.class,
                                               "closeThreads");
                writeAndCloseThreadsField.set(null, testWriteAndCloseThreads);
            } catch(IllegalAccessException iae) {
                throw new RuntimeException(iae);
            }
        }
    }

    /**********************************************************************/
    public synchronized FaultType triggerFault(FaultEvent event, Object ctx) {
        return null;
    }

    /**********************************************************************/
    private static class TestCreatorThreads extends CreatorThreads {
        private transient static final Logger log
            = Logger.getLogger(TestCreatorThreads.class.getName());
        private boolean[] faultyFrags
            = new boolean[Constants.reliability.getTotalFragCount()];
        private int faultCount;
        public boolean activateInterrupt;

        protected OAThreadPool createThreadPool(String tag, int size) {
            TestOAThreadPool pool = new TestOAThreadPool(tag, size);
            /**
             * XXX Following doesn't quite work if activateInterrupt ==
             * false. createThreadPool() will not get called again
             * (from TestWriteAndCloseThreads) and so we end up with
             * no InterrupterThread. This bug has been there
             * before. Since this will be revamped for DAAL, I am
             * leaving the behaviour as is.
             **/
            if (activateInterrupt) {
                InterrupterThread interrupter
                    = new InterrupterThread(faultCount, faultyFrags);
                interrupter.setThreadPool(pool);
                pool.setInterrupter(interrupter);
                interrupter.start();
             }
            return pool;
       }

        public synchronized void setFaultyFragment(int index) {
            faultyFrags[index] = true;
        }

        public void setFaultCount(int n) {
            faultCount = n;
        }
    }

    /**********************************************************************/
    private static class TestWriteAndCloseThreads extends WriteAndCloseThreads {
        private transient static final Logger log
            = Logger.getLogger(TestWriteAndCloseThreads.class.getName());
        private boolean[] faultyFrags
            = new boolean[Constants.reliability.getTotalFragCount()];
        private int faultCount;

        protected OAThreadPool createThreadPool(String tag, int size) {
            log.warning("createThreadPool");
            InterrupterThread thread
                = new InterrupterThread(faultCount, faultyFrags);
            TestOAThreadPool pool = new TestOAThreadPool(tag, size);
            thread.setThreadPool(pool);
            pool.setInterrupter(thread);
            thread.start();
            return pool;
        }

        public synchronized void setFaultyFragment(int index) {
            faultyFrags[index] = true;
        }

        public void setFaultCount(int n) {
            faultCount = n;
        }
    }

    /**********************************************************************/
    public static class TestOAThreadPool extends OAThreadPool {
        private transient static final Logger log
            = Logger.getLogger(TestOAThreadPool.class.getName());
        public InterrupterThread interrupter;

        public TestOAThreadPool(String tag, int size) {
            super(tag, size);
            log.info("A new TestOAThreadPool has been created [" + tag + "]");
        }

        public synchronized void execute() {
            if (interrupter != null) {
                synchronized (interrupter) {
                    interrupter.notifyAll();
                }
            }
            super.execute();
        }

        public OAThread[] getThreads() {
            return threads;
        }

        public void setInterrupter(InterrupterThread thread) {
            interrupter = thread;
        }
    }

    /**********************************************************************/
    public static class InterrupterThread extends Thread {
        private transient static final Logger log
            = Logger.getLogger(InterrupterThread.class.getName());

        private TestOAThreadPool pool;
        private boolean[] faultyFrags;
        private int faultCount = 1;

        public InterrupterThread(int faultCount, boolean[] faultyFrags) {
            this.faultCount = faultCount;
            this.faultyFrags = faultyFrags;
        }

        public void setThreadPool(TestOAThreadPool pool) {
            this.pool = pool;
        }

        public void run() {
            /**
             * Interrupt one or many OAThreads which could be blocked
             * on I/O operation of file channel. If interrupted
             * correctly, the channel will be closed, the OAThread's
             * interrupt status will be set, and the thread will
             * receive a ClosedByInterruptException (an IOException).
             */
            while (true) {
                synchronized (this) {
                    try {
                        wait();
                    } catch (InterruptedException ignore) {
                    }
                }
                faultCount--;
                log.info("Ready to interrupt, " + faultCount
                         + " interruptions left");
                OAThread[] threads = pool.getThreads();
                if (threads.length != faultyFrags.length) {
                    throw new RuntimeException("Found " + threads.length
                                               + " threads for "
                                               + faultyFrags.length + " frags");
                }
                for (int i = 0; i < threads.length; i++) {
                    if (faultyFrags[i]) {
                        log.info("Interrupting OA thread " + threads[i].getName());
                        threads[i].interrupt();
                    }
                }
                if (faultCount <= 0) {
                    log.info("Finished interrupting threads");
                    return;
                }
            }
        }
    }
}
