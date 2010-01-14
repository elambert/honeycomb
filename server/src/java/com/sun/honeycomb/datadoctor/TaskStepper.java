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
import java.util.logging.Logger;
import java.util.logging.Level;

/** 
 * Runs a task (that implements Steppable interface) in discrete steps.
 * Computes the time to sleep between steps based on the Cycle Goal,
 * which is the desired time to complete all steps in the cycle.
 */

class TaskStepper
    implements Runnable {

    private static final byte CONFIG_CHANGE_CYCLEGOAL   = 0x1;
    private static final byte CONFIG_CHANGE_DISKMASK    = 0x2;
    private static final byte CONFIG_CHANGE_TASKINTERRUPTED = 0x3;

    private static final int MS_PER_SEC = 1000;
    private static final int MS_PER_MIN = 1000 * 60;
    private static final int BEHIND_SCHEDULE_WARN_MS = MS_PER_MIN * 2;
    private static final int INFO_MGS_FREQ = MS_PER_MIN * 5;

    private TaskInfo taskInfo;
    private Steppable task;
    private TaskLogger log;

    private long[] durations;
    private long[] absTimes;
    private long clock;

    private int nbValidSteps;
    private int nbStepsWithoutChange;
    private long totalActiveTime;
    private int lastReset;
    private int nbCompletedCycles;
    private int cursor;

    private long startTime; //Start time of the current cycle number
    private long cycleGoal;
    private long stepGoal;
    private DiskMask diskMask;

    private boolean running;
    private boolean sleeping;
    private boolean taskScheduled;
    private byte pendingChanges;
    private long currentErrorCount;     // cum errors in current "cycle"

    private long currentCycleStart;     // the time 10000 steps ago
    private long currentRunStart;       // time of first error-free step

    private Thread t;


    public TaskStepper(TaskInfo _taskInfo,
                       Steppable _task) {
        taskInfo = _taskInfo;
        task = _task;
        log = new TaskLogger(TaskStepper.class.getName(),
                             task.getName());

        durations = new long[task.getNumSteps()];
        absTimes = new long[durations.length];
        for (int i=0; i<durations.length; i++) {
            durations[i] = 0;
            absTimes[i] = -1;
        }
        clock = -1;
        nbValidSteps = 0;
        nbStepsWithoutChange = 0;
        totalActiveTime = 0;
        lastReset = 0;
        nbCompletedCycles = 0;
        cursor = 0;
        
        cycleGoal = 0;
        stepGoal = 0;
        diskMask = null;

        running = false;
        sleeping = false;
        taskScheduled = false;
        pendingChanges = 0;
        startTime = -1;
        
        t = null;
    }

    /*
     * doInterrupt has to be called with this locked
     */
    private void doInterrupt() {
        if (sleeping) {
            t.interrupt();
        } else if (taskScheduled) {
            task.abortStep();
            pendingChanges |= CONFIG_CHANGE_TASKINTERRUPTED;
        }
    }
    
    public synchronized void cycleGoal(long goal) {
        log.info("cycleGoal set to "+goal);
        cycleGoal = goal;
        stepGoal = cycleGoal == 1 ? 1 
            : goal*1000 / durations.length;
        pendingChanges |= CONFIG_CHANGE_CYCLEGOAL;
        doInterrupt();
    }

    public synchronized void diskMask(DiskMask _diskMask) {
        diskMask = _diskMask;
        pendingChanges |= CONFIG_CHANGE_DISKMASK;
        doInterrupt();
    }
    
    public void startStepper(long _cycleGoal,
                             DiskMask _diskMask) {
        if (running || (t != null)) {
            log.warning("A TaskStepper is already running");
            return;
        }
        
        cycleGoal = _cycleGoal;
        stepGoal = cycleGoal == 1 ? 1
            : cycleGoal*1000 / durations.length;
        diskMask = _diskMask;
        t = new Thread(this);
        t.start();
    }

    public void stopStepper() {
        synchronized (this) {
            running = false;
            doInterrupt();
        }
        if (t != null) {
            boolean stopped = false;
            while (!stopped) {
                try {
                    t.join();
                    stopped = true;
                } catch (InterruptedException e) {
                }
            }
        }
    }

    public void run() {
        log.info("running, cycle goal: " + cycleGoal + " seconds");
        
        running = true;
        lastReset = cursor;
        nbCompletedCycles = 0;
        clock = System.currentTimeMillis();
        long lastBehindWarning = 0;
        long lastInfoLog = 0;
        task.newDiskMask(diskMask);
        startTime = System.currentTimeMillis();        
        
        while (running && 
               ((taskInfo.numCycles == TaskList.INFINITE_CYCLES) || (nbCompletedCycles<taskInfo.numCycles))) {
            boolean validStep = false;
            long taskDuration = 0;
            long stepStart = System.currentTimeMillis();
            long stepErrorCount = 1;

            // Run the step
            if (running) {
                // Run the task
                absTimes[cursor] = stepStart;
                taskScheduled = true;
                task.step(cursor);
                // Keep a running count of errors in this cycle
                stepErrorCount = task.getErrorCount();
                task.resetErrorCount();
                currentErrorCount += stepErrorCount;
                taskScheduled = false;
                taskDuration = System.currentTimeMillis() - absTimes[cursor];
            }

            if (running) {
                synchronized (this) {
                    if (pendingChanges == 0) {
                        // Nothing happened, the cycle is valid
                        validStep = true;
                    } else {
                        log.info("resetting cycle because of a config/disk mask change. Last known good cycle was "+
                                 completion()+"% complete with " + currentErrorCount + " faults");
                        nbStepsWithoutChange = 0;
                        totalActiveTime = 0;
                        currentErrorCount = 0;
                        clock = System.currentTimeMillis();
                        if ((pendingChanges & CONFIG_CHANGE_DISKMASK) != 0) {
                            task.newDiskMask(diskMask);
                            nbCompletedCycles = 0;
                            lastReset = cursor;
                        }
                    }
                    pendingChanges = 0;
                }
            }
            
            if (running && validStep) {
                if (nbValidSteps < durations.length) {
                    nbValidSteps++;
                }
                if (nbStepsWithoutChange < durations.length) {
                    nbStepsWithoutChange++;
                } else {
                    totalActiveTime -= durations[cursor];
                }
                totalActiveTime += taskDuration;
                durations[cursor] = taskDuration;
                cursor++;
                if (cursor == durations.length) {
                    cursor = 0;
                }

                //Set currentRunStart to the start time of the
                // current error-free run.
                if (stepErrorCount != 0) {
                    if (log.isLoggable(Level.FINE)) {
                        log.fine("reset  currentRunStart to zero"+
                                 " since stepErrorCount="+
                                 stepErrorCount);
                    }
                    currentRunStart = 0;
                } else if (currentRunStart ==0) {
                    currentRunStart = stepStart;
                    if (log.isLoggable(Level.FINE)) {
                        log.fine("set currentRunStart ="+currentRunStart+
                                 " (time of first error free step).");
                    }
                }

                // Set currentCycleStart to the start time of the
                // current cycle of 10000 steps.  This is just the
                // abstime of the cycle 10000 steps ago.
                if (cursor == lastReset) {
                    currentCycleStart = absTimes[lastReset];
                    if (nbCompletedCycles < Integer.MAX_VALUE) {
                        nbCompletedCycles++;
                    } else {
                        nbCompletedCycles = -1;
                    }
                    
                    log.info(" cycle complete, length: " + 
                             (lastCycleMillis()/1000) + " seconds, "+
                             "load: "+getLoad() + ", faults: " 
                             + currentErrorCount+
                             " currentCycleStart="+currentCycleStart);
                    currentErrorCount = 0;
                    startTime = System.currentTimeMillis();
                } else if (currentCycleStart != 0) {
                    // keep start time of the last 10000 steps up to
                    // date.
                    currentCycleStart = absTimes[cursor];
                    if (log.isLoggable(Level.FINE)) {
                        log.fine("updating currentCycleStart to "+
                                 currentCycleStart);
                    }
                }
            } else {
                // step was not valid, so reset currentRunStart and
                // currentCycleStart 
                log.info("resetting currentRunStart and currentCycleStart to 0");
                currentRunStart = 0;
                currentCycleStart = 0;
            }

            if (running && validStep) {
                // How much should we sleep ?
                boolean hasToSleep = (cycleGoal > 1);
                long now = System.currentTimeMillis();
                
                if (hasToSleep) {
                    
                    long sleepTime = 0;
                    clock += stepGoal;
                    sleepTime = clock - now;
                    if (sleepTime <= 0) {
                        if ((lastBehindWarning == 0) || ((now-lastBehindWarning)>BEHIND_SCHEDULE_WARN_MS)) {
                            log.warning("BEHIND SCHEDULE! ["+sleepTime+"]");
                            lastBehindWarning = now;
                        }
                    } else {
                        sleeping = true;
                        if (pendingChanges == 0) {
                            try {
                                Thread.sleep(sleepTime);
                            } catch (InterruptedException e) {
                                log.info("The TaskStepper sleep has been interrupted");
                            }
                        }
                        sleeping = false;
                    }
                }
                
                if ((lastInfoLog == 0) || (now-lastInfoLog>INFO_MGS_FREQ)) {
                    log.info("Info msg. Last known good cycle is "+ completion()
                             + "% complete with " + currentErrorCount + 
                             " faults. Load is "+(getLoad()));
                    lastInfoLog = now;
                }
            } // if (how much should we sleep)
        } // while (running &&...)
        
        running = false;
        t = null;
        
        log.info("EXITING");
    }
    
    public float getLoad() {
        if (!running) {
            return(0);
        }
        if (cycleGoal <= 1) {
            return(1);
        }
        return((float)(totalActiveTime*durations.length)/((float)(cycleGoal*1000*nbStepsWithoutChange)));
    }

    public boolean isRunning() { 
        return(running);
    }
    
    public long cycleGoal() {
        return cycleGoal;
    }

    public DiskMask diskMask() {
        return diskMask; 
    }

    public int cyclesDone() { 
        return(nbCompletedCycles);
    }

    public long lastCycleMillis() {
        if (nbValidSteps < durations.length) {
            return(-1);
        } else {
            return(System.currentTimeMillis()-absTimes[cursor]);
        }
    }
    
    public int completion() {
        int result = (cursor-lastReset)*100/durations.length;
        return ((result<0) ? result+100 : result);
    }

    public int getErrorCount () {
        return currentErrorCount < Integer.MAX_VALUE ?
            (int) currentErrorCount : Integer.MAX_VALUE;
    }
    
    public long getCycleStart() {
        return startTime;
    }

    public long getCurrentCycleStart() {
        return currentCycleStart;
    }
    public long getCurrentRunStart() {
        return currentRunStart;
    }
}
