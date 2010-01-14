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

/**
 * Example and unit test for the TimedThread class.
 */
class TestTimedThread  {
    
    public static void usage() {
        System.err.println (
            "java TestTimedThread [time1] [time2]\n\n" +
            "\ttime1 = parent thread sleep time.\n" +
            "\ttime2 = timeout to wait for thread1 to return.\n\n" +
            "Setting time1 > time2 will result in thread 1 being " +
            "interrupted by the Timeout\n");
    }

    public static void main (String[] args) {
        if (args == null || args.length < 2) {
            usage();
            System.exit (1);
        }

        TestTimedThread test = null;

        try {
            test = new TestTimedThread (Long.parseLong (args[0]), 
                                        Long.parseLong (args[1]));
        } catch (NumberFormatException nfe) {
            System.err.println ("Invalid time: " + nfe.toString());
            System.exit (2);
        }

        test.test();
    }

    private long _t1Time  = 0;
    private long _timeout = 0;

    public TestTimedThread (long time1, long time2) {
        _t1Time = time1;
        _timeout = time2;
    }

    public void test () {
        Runner r = new Runner();
        TimedThread timeout = new TimedThread (r, _timeout);
        try {
            timeout.start();
            System.out.println ("success = " + r.succeeded());
        } catch (TimeoutException te) {
            System.err.println ("timed out: " + te.getMessage());
        }
        catch (InterruptedException ie) {
            System.err.println ("interrupted: " + ie.getMessage());
        }
    }

    /**
     * Private inner class that we'll pass to a TimedThread. This class 
     * represents a time-bounded task. Note the succeeded() method. It is
     * the responsibility of the 'worker' classes to track whether ot nor the
     * actions they took were successful (for example, was the disk formatted?).
     * The TimedThread *only* guarantees that the task completed in the
     * specified time, not whether the task succeeded or failed.
     */
    private class Runner implements Runnable {
        private volatile boolean _success;

        public void run () {
            for (int i = 0; i < _t1Time; i++) {
                try {
                    Thread.currentThread().sleep (10);
                } catch (InterruptedException e) {
                    System.err.println ("sleeping: " + e.getMessage());
                    return;
                }
                
                _success = true;
            }
        }

        public boolean succeeded() {
            return _success;
        }
    }
}
