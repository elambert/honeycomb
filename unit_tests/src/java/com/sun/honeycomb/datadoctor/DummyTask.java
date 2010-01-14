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



package com.sun.honeycomb.datadoctor;

import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.disks.DiskId;
import java.util.Random;

// Sample task that sleeps for a random number of seconds every step.
class DummyTask implements Steppable {

    DummyTask() {
        // all work done in init method
    }

    public void init(String taskName, DiskId myDiskId) {
        this.taskName = taskName;
        this.myDiskId = myDiskId;
        rand = new Random();
    }

    // used to report stats
    public String getName() {
        return taskName;
    }

    // how many steps for this task
    public int getNumSteps() {
        return NUM_STEPS;
    }

    // perform ith step
    public void step(int stepNum) {
        int sleep_sec = rand.nextInt(SLEEP_SEC_MAX) + SLEEP_SEC_MIN; 
        //log("stepping for "+sleep_sec+" seconds");
        try {
            Thread.sleep(sleep_sec * MILLIS_PER_SEC);
        } catch (InterruptedException ie) {
            log("sleep interrupted");
        }
    }

    public void resetErrorCount() {
        return;
    }

    public int getErrorCount () {
        return 0;
    }

    public void abortStep() {
        // do nothing
    }
    public void newDiskMask(DiskMask newMask) {
        // do nothing
    }

    // emulate the HC logger
    private void log(String msg) {
        System.out.println(taskName+"> "+msg);
    }

    private Random rand;
    private String taskName;
    private DiskId myDiskId;

    static private int NUM_STEPS = 10;
    static private int SLEEP_SEC_MIN = 1;
    static private int SLEEP_SEC_MAX = 3;
    static private int MILLIS_PER_SEC = 1000;

    static int FULL_SPEED_GOAL = (NUM_STEPS+2) * 
                            ((SLEEP_SEC_MAX + SLEEP_SEC_MIN) / 2 );
}


