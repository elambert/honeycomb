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
import com.sun.honeycomb.layout.LayoutConfig;
import com.sun.honeycomb.common.InternalException;

import java.lang.reflect.Constructor;
import java.util.logging.Logger;
import java.util.ArrayList;

/** 
 * Element in the task list. This class manages all the local steppers
 * for the given task.  It encapsulate the fact that there is one
 * stepper per disk per task, simplifying the TaskList methods.
 */
class TaskListElem {

    TaskStepper[] steppers;             // one per disk
    TaskInfo taskInfo;              // arg to constructor 

    // all tasks use same set of local disks
    static DiskId[] localDisks;

    private static final int TASK_STOP_WAIT = 2000;

    private static final Logger LOG =
        Logger.getLogger(TaskListElem.class.getName());

    /**********************************************************************/
    TaskListElem(TaskInfo taskInfo) {

        this.taskInfo = taskInfo;

        // get our local disks
        int myNodeId = DataDocConfig.getInstance().localNodeId();
        if (localDisks == null) {
            localDisks = new DiskId[LayoutConfig.DISKS_PER_NODE];
            for (int d=0; d < localDisks.length; d++) {
                localDisks[d] = new DiskId(myNodeId, d);
            }
        }

        int numDisks = localDisks.length;
        steppers = new TaskStepper[numDisks];
        for (int d=0; d < numDisks; d++) {
            Steppable task = createTask(d);
            steppers[d] = new TaskStepper(taskInfo, task);
        } 
    }

    /**********************************************************************
     * include the diskId in the task name for logging
     **/
    private String taskName(DiskId diskId) {
        return taskInfo.label+"("+diskId.toStringShort()+")";
    }

    /**********************************************************************/
    public String taskName(int diskIndex) {
        return taskName(localDisks[diskIndex]);
    }

    /**********************************************************************
     * start this task on the given diskIndex
     **/
    private void startTask(int diskIndex, DiskMask mask, 
                           long cycleGoal) {

        int d = diskIndex;
        steppers[d].startStepper(cycleGoal, mask);

        LOG.info("started task "+taskName(d));
    }

    /**********************************************************************
     * stop this task on all disks
     **/
    void stopTask() {
        for (int d=0; d < localDisks.length; d++) {
            if (steppers[d].isRunning()) {
                stopTask(d);
            }
        }
    }

    /**********************************************************************
     * stop this task on the specified disk
     **/
    private void stopTask(int diskIndex) {

        int d = diskIndex;
        steppers[d].stopStepper();
        LOG.info("stopped task "+taskName(d));
    }

    /**********************************************************************
     * Instantiate a task to perform the desired task.
     **/
    private Steppable createTask(int diskIndex) {

        Object cArgs[] = null;
        Class cArgTypes[] = null;
    
        // find the constructor for task with this taskIndex
        int d = diskIndex;
        Steppable task;
        try {
            Class theClass = Class.forName(taskInfo.className);
            Constructor c = theClass.getConstructor(cArgTypes);
            task = (Steppable) c.newInstance(cArgs);

        } catch (Exception ex) {
            throw new InternalException(taskInfo.className+
                                        "not found, be sure its constructor is PUBLIC", ex);
        }

        // We use a separate init method so the Steppable interface
        // provides type-checking at compile time. If the task class
        // constructor takes arguments (cArgs) we won't discover
        // until runtime that the arg types (cArgTypes) are wrong.
        task.init(taskName(d), localDisks[d]);

        return task;
    }

    /**********************************************************************
     * update all the instances of this task on this node
     **/
    void updateTask(DiskMask mask, long cycleGoal) {

        // step through all local disks
        for (int d=0; d < localDisks.length; d++) {
            updateTask(d, mask, cycleGoal);
        }
    }

    /**********************************************************************
     * update the task on the given disk
     **/
    private void updateTask(int diskIndex, DiskMask mask,
                            long cycleGoal) {

        int d = diskIndex;
        DiskId diskId = localDisks[d];

        boolean taskRunning = steppers[d].isRunning();
        boolean diskOnline = mask.isOnline(diskId);
        boolean dontRunTask = (cycleGoal == DataDocConfig.CG_DONT_RUN);

        // start the task, if it should be running
        if (!taskRunning && diskOnline && !dontRunTask) {

            startTask(d, mask, cycleGoal);
            taskRunning = true;
        } 

        // stop the task, if it should not be running
        if (taskRunning && (!diskOnline || dontRunTask)) {

            stopTask(d);
            taskRunning = false;
        }

        // if task is running, check if we need to update it
        if (taskRunning) {

            // check for new config prop
            long oldCycleGoal = steppers[d].cycleGoal();
            if (oldCycleGoal != cycleGoal) {
                steppers[d].cycleGoal(cycleGoal);
            }

            // check for new disk mask
            DiskMask oldMask = steppers[d].diskMask();
            if (!mask.equals(oldMask)) {
                steppers[d].diskMask(mask);
            }
        }
    }

    /**********************************************************************
     * check if any tasks have just completed a cycle
     **/
    DiskMask computeTaskDoneMask() {

        // step through all local disks
        DiskMask doneMask = new DiskMask();
        for (int d=0; d < localDisks.length; d++) {
            if (isTaskDone(d)) {
                doneMask.setOnline(localDisks[d]);
            } else {
                doneMask.setOffline(localDisks[d]);
            }
        }
        return doneMask;
    }

    /**********************************************************************
     * update the doneMask for task on the given disk
     **/
    private boolean isTaskDone(int diskIndex) {

        int d = diskIndex;

        // we if we've completed desired number of cycles
        return steppers[d].isRunning() && 
            steppers[d].cyclesDone() == taskInfo.numCycles;
    }

    public int taskCompletion(int disk) {
        return(steppers[disk].completion());
    }

    public int cyclesDone (int disk) {
        return(steppers[disk].cyclesDone());
    }

    public int getErrorCount (int disk) {
        return (steppers[disk].getErrorCount());
    }
    
    public long getCycleStart (int disk) {
        return (steppers[disk].getCycleStart());
    }

    public long getCurrentRunStart (int disk) {
        return (steppers[disk].getCurrentRunStart());
    }

    public long getCurrentCycleStart (int disk) {
        return (steppers[disk].getCurrentCycleStart());
    }

}
