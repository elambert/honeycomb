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
import com.sun.honeycomb.common.InternalException;

import java.util.logging.Logger;
import java.util.ArrayList;

/** 
 * This class contains the static config info for Data Doctor tasks.
 * When the singleton is created, the ArrayList is filled with a
 * TaskListElem for each task.  Methods perform operations on all tasks
 * in the list, except for those related to sloshing, which is a special
 * task that does not appear in the list but is kept as a private member
 * of this class.
 */
public class TaskList extends ArrayList {

    /** Return the singleton, or create if doesn't exist. */
    static synchronized TaskList getInstance() {
        if (instance == null) {
            instance = new TaskList();
        }
        return instance;
    }

    /** total number of tasks, will be same as size of ArrayList */
    public static int numTasks() { return TASK_INFO.length; }

    /** get the task id (index into task array) given the task label */
    public static int taskId(String taskLabel) { 
        for (int i=0; i < TASK_INFO.length; i++) {
            if (TASK_INFO[i].label.equals(taskLabel)) {
                return i;
            }
        } 
        throw new InternalException("task label "+taskLabel+" invalid");
    }

    /** get the task label given the taskId */
    public static String taskLabel(int taskId) { 
        return TASK_INFO[taskId].label;
    }

    /** DataDocConfig needs to know the property string */
    static String cycleGoalProp(int taskId) { 
        return TASK_INFO[taskId].cycleGoalProp;
    }

    /** called when diskMask or a relavant config property changes. */
    void updateTasks(DiskMask mask) {
        for (int i=0; i < size(); i++) {
            long cycleGoal = DataDocConfig.getInstance().cycleGoal(i);
            ((TaskListElem) get(i)).updateTask(mask, cycleGoal);
        }
    }

    /** stop all DataDoctor tasks */
    void stopTasks() {
        for (int i=0; i < size(); i++) {
            ((TaskListElem) get(i)).stopTask();
        }
    }

    /** mask for each task, represents local disks done with that task */
    DiskMask[] computeTaskDoneMasks() {

        DiskMask[] masks = new DiskMask[size()];
        for (int i=0; i < size(); i++) {
            masks[i] = ((TaskListElem) get(i)).computeTaskDoneMask();
        }
        return masks;
    }

    /** CONSTRUCTOR creates the TaskListElem and adds them to the list */
    private TaskList() {

        // create task elements and insert into list
        for (int i=0; i < TASK_INFO.length; i++) {
            add(new TaskListElem(TASK_INFO[i]));
        } 
    }

    // singleton
    private static TaskList instance;

    /* CONSTANTS */

    // to increase readability of the TASK_INFO array
    private static final int ONE_CYCLE = 1;
    private static final boolean MASK_CHANGE_RESTARTS = true;
    static final int INFINITE_CYCLES = -1;

    // labels for the tasks, used to find particular task in the array
    public static final String RM_DUPS_TASK = "RemoveDupFrags";
    public static final String RM_TEMP_TASK = "RemoveTempFrags";
    public static final String POP_SYS_TASK = "PopulateSysCache";
    public static final String POP_EXT_TASK = "PopulateExtCache";
    public static final String RECOVER_TASK = "RecoverLostFrags";
    public static final String SCANNER_TASK = "ScanFrags";

    // DataDoctor will run any tasks listed in this array.
    public static final TaskInfo[] TASK_INFO =
    {
        // remove duplicate fragments (formerly in Crawl dlm)
        new TaskInfo( RM_DUPS_TASK,
                      INFINITE_CYCLES, MASK_CHANGE_RESTARTS,
                      RemoveDupFrags.class.getName(),
                      "honeycomb.datadoctor.remove_dup_frags_cycle"),

        // remove temp fragments (formerly in Purge dlm)
        new TaskInfo( RM_TEMP_TASK,
                      INFINITE_CYCLES, !MASK_CHANGE_RESTARTS,
                      RemoveTempFrags.class.getName(),
                      "honeycomb.datadoctor.remove_temp_frags_cycle"),

        // populate md system cache (formerly in Crawl dlm)
        new TaskInfo( POP_SYS_TASK,
                      INFINITE_CYCLES, MASK_CHANGE_RESTARTS,
                      PopulateSysCache.class.getName(),
                      "honeycomb.datadoctor.populate_sys_cache_cycle"),

        // populate md extended cache (formerly in Crawl dlm)
        new TaskInfo( POP_EXT_TASK,
                      INFINITE_CYCLES, MASK_CHANGE_RESTARTS,
                      PopulateExtCache.class.getName(),
                      "honeycomb.datadoctor.populate_ext_cache_cycle"),

        // recover missing fragments (formerly in Recover dlm)
        new TaskInfo( RECOVER_TASK, 
                      INFINITE_CYCLES, MASK_CHANGE_RESTARTS,
                      RecoverLostFrags.class.getName(),
                      "honeycomb.datadoctor.recover_lost_frags_cycle"),

        // verify integrity using checksums (formerly in Crawl dlm)
        new TaskInfo( SCANNER_TASK,
                      INFINITE_CYCLES, !MASK_CHANGE_RESTARTS,
                      ScanFrags.class.getName(),
                      "honeycomb.datadoctor.scan_frags_cycle")

        // IMPORTANT REMINDERS when adding a new task...
        // (1) the constructor (and the class) must have PUBLIC access
        //     or else DataDoctor will throw exception and die
        // (2) for property to appear on cli syscfg, you must add a call
        //     to PROPERTIES.put in admin.cli.commands.CommandSetConfig

    };
}
    
    
    
