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

import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.layout.DiskMask;

// Creates TaskStepper that runs a DummyTask.   
class RunDummyTask {

    // main method, for testing
    public static void main(String[] args) {

        if (args.length < 1) {
            usage();
            return;
        }

        // parse args before starting any threads
        int numTasks = args.length;
        long[] cycleGoals = new long[numTasks];
        for (int i=0; i < numTasks; i++) {
            cycleGoals[i] = (long) Integer.parseInt(args[i]);
        } 

        runTask(cycleGoals);
    }


    // create a stepper for each cycle goal, run these dummy tasks, and
    // then check to see if the tasks completed on schedule
    static boolean runTask(long[] cycleGoals) {

        // get the largest cycle length, so we know how long to wait
        long maxCycle = 0;
        for (int i=0; i < cycleGoals.length; i++) {
            maxCycle = Math.max(cycleGoals[i], maxCycle);
        } 
        if (maxCycle == DataDocConfig.CG_FULL_SPEED) {
            maxCycle = DummyTask.FULL_SPEED_GOAL;
        }

        // start up the tasks, then wait until expected completion time
        startDummyTask(cycleGoals);
        long sleep_sec = maxCycle + (long)(maxCycle * BUFFER_PERCENT);
        log("waiting "+sleep_sec+" seconds"+ " for tasks to finish");
        try {
            Thread.sleep(sleep_sec * MS_PER_SEC);
        } catch (InterruptedException e) {
            log("wait interrupted");
        }

        // check if we completed on schedule
        boolean metCycleGoals = stopDummyTask(cycleGoals, sleep_sec);
        if (metCycleGoals) {
            log("Test PASSED!");
        } else  {
            log("Test FAILED!");
        }

        return metCycleGoals;
    }

    // start a dummy task with each of the cycle goals
    private static void startDummyTask(long[] cycleGoals) {

        DiskMask dummyMask = new DiskMask();
        int numTasks = cycleGoals.length;
        steppers = new TaskStepper[numTasks];
        for (int i=0; i < numTasks; i++) {

            Steppable dummyTask = new DummyTask();
            String taskName = "dummy"+i;
            int nodeId = 101 + (i/4);
            int diskIndex = i % 4;
            DiskId diskId = new DiskId(nodeId, diskIndex);
            dummyTask.init(taskName, diskId);
            steppers[i] = new TaskStepper(TASK_INFO, dummyTask);

            // if DONT_RUN is set, create task but do not start it
            if (cycleGoals[i] != DataDocConfig.CG_DONT_RUN) {
                steppers[i].startStepper(cycleGoals[i], dummyMask);
            }
        } 
    }

    // stop all tasks, and check if we hit our goals 
    private static boolean stopDummyTask(long[] cycleGoals,
                                         long sleep_sec) {

        boolean metCycleGoals = true;
        for (int i=0; i < steppers.length; i++) {

            long cycle =  steppers[i].lastCycleMillis() / MS_PER_SEC;
            long goal = cycleGoals[i];
            long buffer = (long)(goal * BUFFER_PERCENT);

            // verify "don't run" really didn't do anything
            if (goal == DataDocConfig.CG_DONT_RUN) {
                
                if (cycle != 0) {
                    metCycleGoals = false;
                    log("task "+i+" took "+cycle+" seconds but wasn't "+
                        "supposed to run");
                }

            // verify "don't run" really didn't do anything
            } else if (goal == DataDocConfig.CG_FULL_SPEED) {

                goal = DummyTask.FULL_SPEED_GOAL;
                buffer = (long)(goal * BUFFER_PERCENT);

                if (cycle > goal + buffer) {
                    metCycleGoals = false;
                    log("task "+i+" completed in "+cycle+" seconds, "+
                        "but expected less than "+(goal+buffer));
                } else if (cycle == 0) {
                    metCycleGoals = false;
                    log("task "+i+" not finished after "+sleep_sec+
                        " seconds (goal was "+goal+")");
                }
                
            // verify task completed within expected limits
            } else if (cycle < goal - buffer || cycle > goal + buffer) {
                   
                metCycleGoals = false;
                String s = "task "+i+" ";
                if (cycle == 0) {
                    s += "not finished after "+sleep_sec+" seconds "+
                         "(goal was "+goal+")";
                } else {
                    s += "completed in "+cycle+" seconds, but expected"+
                         " in range "+(goal-buffer)+"-"+(goal+buffer);
                }
                log(s);
            }

            steppers[i].stopStepper();
        }

        return metCycleGoals;
    }

    static void usage() {

    System.out.print(
    "Expected one arg: cycleGoal (in seconds) recommened goal is 30-90 \n"+
    "List multiple cycleGoals to run additional tasks (e.g 30 60 90) \n"+
    "Use "+DataDocConfig.CG_FULL_SPEED+" to run as fast as possible, "+
    "and "+DataDocConfig.CG_DONT_RUN+" means don't run the task.\n");
    }

    static void log(String msg) {
        System.out.println(PROG_NAME+"> "+msg);
    }

    // each stepper runs a task
    static private TaskStepper[] steppers;

    static final private long MS_PER_SEC = 1000;
    static final private double BUFFER_PERCENT = 0.15;
    static final private int ONE_CYCLE = 1;
    static final private boolean MASK_CHANGE_RESTARTS = true;

    static final private String PROG_NAME =
        DataDoctorTest.class.getName().replaceAll("[A-Za-z_0-9]*[.]","");

    static final private TaskInfo TASK_INFO = new TaskInfo(
        "DummyTask", ONE_CYCLE, !MASK_CHANGE_RESTARTS, 
        DummyTask.class.getName(), "dummyProperty");
    
} 

