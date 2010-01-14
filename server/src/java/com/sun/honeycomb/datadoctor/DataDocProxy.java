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

import java.util.logging.Logger;
import java.util.ArrayList;
import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ServiceManager;
import com.sun.honeycomb.cm.node_mgr.NodeMgrService;
import com.sun.honeycomb.common.InternalException;
import com.sun.honeycomb.layout.DiskMask;
import com.sun.honeycomb.layout.DiskIdList;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.layout.LayoutConfig;


/** 
 * Published by the DataDoctor.  No remote API (RMI) for now.
 */ 
public class DataDocProxy extends ManagedService.ProxyObject {

    /* PULIC METHODS */
    private static transient final Logger logger = 
      Logger.getLogger(DataDocProxy.class.getName());

    /** Get current DataDoctor proxy for given node, null if failed. */
    public static DataDocProxy getDataDocProxy(int nodeId) {

        ManagedService.ProxyObject proxy = null;
        proxy = ServiceManager.proxyFor(nodeId, DataDoctor.class);

        if (! (proxy instanceof DataDocProxy)) {
            return null;
        }
        return (DataDocProxy) proxy;
    }
    
    public static DataDocIntf getServiceAPI(int nodeId) {
        
        ManagedService.ProxyObject proxy;
        proxy = ServiceManager.proxyFor(nodeId, DataDoctor.class);
        if (!(proxy instanceof DataDocProxy)) {
            return null;
        }
        ManagedService.RemoteInvocation api = proxy.getAPI();
        if (!(api instanceof DataDocIntf)) {
            return null;
        }
        return (DataDocIntf) api;
    }
    
    
    /** accessors for data doctor healing cycle state **/

    public int  completion(int taskId,int disk) { 
        if(disk > cycleStates.length || disk < 0) 
            throw new IllegalArgumentException("Disk " + 
                                               disk + " is out of range.");

        if(taskId > TaskList.numTasks() || taskId < 0) 
            throw new IllegalArgumentException ("Task id " + 
                                                taskId + " is out of range.");


        return cycleStates[disk].getCycleState(taskId).completion;

    }
    public int  cyclesDone(int taskId,int disk) { 
        if(disk > cycleStates.length || disk < 0) 
            throw new IllegalArgumentException("Disk " + 
                                               disk + " is out of range.");

        if(taskId > TaskList.numTasks() || taskId < 0) 
            throw new IllegalArgumentException ("Task id " + 
                                                taskId + " is out of range.");


        return cycleStates[disk].getCycleState(taskId).cyclesDone;
    }

    public int  numFaults(int taskId, int disk) {
        if(disk > cycleStates.length || disk < 0) 
            throw new IllegalArgumentException("Disk " + 
                                               disk + " is out of range.");

        if(taskId > TaskList.numTasks() || taskId < 0) 
            throw new IllegalArgumentException ("Task id " + 
                                                taskId + " is out of range.");


        return cycleStates[disk].getCycleState(taskId).numFaults;
    }

    public long cycleStart(int taskId, int disk) { 
        if(disk > cycleStates.length || disk < 0) 
            throw new IllegalArgumentException("Disk " + 
                                               disk + " is out of range.");

        if(taskId > TaskList.numTasks() || taskId < 0) 
            throw new IllegalArgumentException ("Task id " + 
                                                taskId + " is out of range.");


        return cycleStates[disk].getCycleState(taskId).cycleStart;

    }

    public long currentRunStart(int taskId, int disk) { 
        if(disk > cycleStates.length || disk < 0) 
            throw new IllegalArgumentException("Disk " + 
                                               disk + " is out of range.");

        if(taskId > TaskList.numTasks() || taskId < 0) 
            throw new IllegalArgumentException ("Task id " + 
                                                taskId + " is out of range.");


        return cycleStates[disk].getCycleState(taskId).currentRunStart;
    }

    public long currentCycleStart(int taskId, int disk) { 
        if(disk > cycleStates.length || disk < 0) 
            throw new IllegalArgumentException("Disk " + 
                                               disk + " is out of range.");

        if(taskId > TaskList.numTasks() || taskId < 0) 
            throw new IllegalArgumentException ("Task id " + 
                                                taskId + " is out of range.");


        return cycleStates[disk].getCycleState(taskId).currentCycleStart;
    }
    

    /** accessors for the unhealed failure counts */

    public int unhealedFailuresTotal() { return unhealedFailuresTotal;}
    public void unhealedFailuresTotal(int f) {unhealedFailuresTotal = f;}
    public int unhealedFailuresUnique() { return unhealedFailuresUnique;}
    public void unhealedFailuresUnique(int f) {unhealedFailuresUnique = f;}
    public boolean failureTolerance() { return failureTolerance;}
    public void failureTolerance(boolean t) {failureTolerance = t;}

    /** print the cell-wide info, not local done masks */
    public String toString() {

        StringBuffer sb = new StringBuffer();
        sb.append("unhealedFailuresTotal="+unhealedFailuresTotal()+
        " unhealedFailuresUnique="+unhealedFailuresUnique()+"   ");

        sb.append("failureTolerance="+failureTolerance()+"    ");

        sb.append("cellDoneMasks - ");
        for (int i=0; i < TaskList.numTasks(); i++) {
            String label = TaskList.taskLabel(i);
            sb.append(label+": "+cellDoneMasks[i].toString()+" ");
        } 
        sb.append("  ");
        sb.append("cellSloshMask - "+cellSloshMask.toString());

        for (int i=0; i < TaskList.numTasks(); i++) {
            sb.append("  ");
            sb.append("Last "+TaskList.taskLabel(i)+" Done at ");
            if (!maskUsed[i].isEmpty()) {
                sb.append(DataDoctor.timeToString(timeFinished[i]));
            } else {
                sb.append("<none yet>");
            }
        }

        return sb.toString();
    }


    /* PACKAGE METHODS */

    /** Constructor */
    DataDocProxy() {
        sloshDoneMask = new DiskMask();
        cellSloshMask = new DiskMask();

        doneMasks = new DiskMask[TaskList.numTasks()];
        cellDoneMasks = new DiskMask[TaskList.numTasks()];
        timeFinished = new long[TaskList.numTasks()];
        maskUsed = new DiskMask[TaskList.numTasks()];
        for (int i=0; i < TaskList.numTasks(); i++) {
            doneMasks[i] = new DiskMask();
            cellDoneMasks[i] = new DiskMask();
            timeFinished[i] = -1;
            maskUsed[i] = new DiskMask();
        }

        cycleStates = new CycleStates[LayoutConfig.DISKS_PER_NODE];
        for (int i = 0; i < LayoutConfig.DISKS_PER_NODE; i++) {
            cycleStates[i] = new CycleStates (i);
        }
    }
    
    public synchronized void refresh() {
        for (int i = 0; i < cycleStates.length; i++) {
            ((CycleStates)cycleStates[i]).refresh();
        }
    }

    /*********************  local done masks  *************************
     * Each mask (one per task) represents which local disks are done
     * with that task.  The done masks from all the nodes will be
     * unioned together to detect cell-wide completion of each task.
     */
    void clearDoneMasks() { 
        for (int i=0; i < doneMasks.length; i++) {
            doneMasks[i].clear();
        } 
    }
    DiskMask doneMask(int taskId) {
        return (DiskMask)doneMasks[taskId].clone();
    }
    DiskMask[] doneMasks() {
        DiskMask[] doneMasksCopy = new DiskMask[doneMasks.length];
        for (int i=0; i < doneMasksCopy.length; i++) {
            doneMasksCopy[i] = (DiskMask)doneMasks[i].clone();
        } 
        return doneMasksCopy;
    }
    void doneMask(int taskId, DiskMask newMask) {
        doneMasks[taskId] = newMask;
    }

    /*********************  local slosh mask  *************************
     * Done mask for the sloshing task.  Since this task only runs
     * during cluster expansion, it's not part of TaskList array.
     */
    void clearSloshMask() { sloshDoneMask.clear(); }
    DiskMask sloshDoneMask() { return (DiskMask)sloshDoneMask.clone(); }
    void sloshDoneMask(DiskMask newMask) { sloshDoneMask = newMask; };
    boolean isSloshDone(DiskId diskId) { 
        return sloshDoneMask.isOnline(diskId); }


    /*********************  cell done masks  *************************
     * The cellDoneMasks are the union of the doneMasks from each node,
     * allows us to to determine when a task has completed cell-wide.
     * TODO: do we need to also record stats for each task?
     */
    void clearCellDoneMasks() { 
        for (int i=0; i < cellDoneMasks.length; i++) {
            cellDoneMasks[i].clear();
        } 
    }
    DiskMask cellDoneMask(int taskId) {
        return (DiskMask)cellDoneMasks[taskId].clone();
    }
    DiskMask[] cellDoneMasks() {
        DiskMask[] doneMasksCopy = new DiskMask[cellDoneMasks.length];
        for (int i=0; i < doneMasksCopy.length; i++) {
            doneMasksCopy[i] = (DiskMask)cellDoneMasks[i].clone();
        } 
        return doneMasksCopy;
    }

    /** check if all disks have completed the given task */
    boolean isTaskDone(int taskId, DiskMask diskMask) { 

        // get disks that are online, but not done the task
        DiskMask notDone = (DiskMask)diskMask.clone();
        notDone.andNot(cellDoneMasks[taskId]);       

        return notDone.isEmpty();
    }

    /** update and return True if anything changed */
    boolean refreshCellDone(int taskId) {

        DiskMask oldMask = (DiskMask)cellDoneMasks[taskId].clone();
        cellDoneMasks[taskId].clear();

        // iterate all nodes and union all the doneMasks
        ManagedService.ProxyObject[] proxies =
            ServiceManager.proxyFor(DataDoctor.class);

        for (int i=0; proxies != null && i < proxies.length; i++) {
            if (! (proxies[i] instanceof DataDocProxy)) {
                continue;
            }
            cellDoneMasks[taskId].or(((DataDocProxy)proxies[i]).
                                            doneMask(taskId));
        } 

        return !cellDoneMasks[taskId].equals(oldMask);
    }

 /** check all dd proxies and return id of 1st node w/ !failureTolerance
     return -1 if everyone is happy */
    int cellWidePossibleDataLossCheck() {
	
        // iterate all nodes, looking for even 1 isntance of !failureTolerance
        ManagedService.ProxyObject[] proxies =
            ServiceManager.proxyFor(DataDoctor.class);
	
        for (int i=0; proxies != null && i < proxies.length; i++) {
            if (! (proxies[i] instanceof DataDocProxy)) {
                continue;
            }
	    
	    if(!((DataDocProxy)proxies[i]).failureTolerance()) {
		return i;
	    }
	}
	return -1;
    }
    
    /*********************  cell slosh mask  *************************
     * Done mask for sloshing.  This is important because we cannot
     * complete the Cluster Expansion until sloshing is complete.
     */
    void clearCellSloshMask() { cellSloshMask.clear(); }
    DiskMask cellSloshMask() {return (DiskMask)cellSloshMask.clone();}

    /** check the cell slosh mask, are all nodes done sloshing? */
    boolean isSloshDone(ArrayList expansionNodeIds, 
                        DiskMask expandedMask) {

        // get list of all nodes not done sloshing
        DiskMask notDone = (DiskMask)expandedMask.clone();
        notDone.andNot(cellSloshMask);       
        DiskIdList notDoneList = notDone.onlineDiskIds();

        // if any of these nodes were supposed to slosh, return false
        for (int i=0; i < notDoneList.size(); i++) {
            DiskId diskId = (DiskId)notDoneList.get(i);
            int nodeId = diskId.nodeId();
            if (expansionNodeIds.contains(new Integer(nodeId))) {
                return false;
            }
        } 
        return true;
    }

    /** update and return True if anything changed */
    boolean refreshCellSloshDone(ArrayList nodeIds) {

        DiskMask oldMask = (DiskMask)cellSloshMask.clone();
        cellSloshMask.clear();

        // iterate all nodes and union all the doneMasks
        DataDocProxy[] proxies = getProxies(nodeIds);
        for (int i=0; i < proxies.length; i++) {
            if (proxies[i] != null) {
                cellSloshMask.or(proxies[i].sloshDoneMask());
            }
        }

        return !cellSloshMask.equals(oldMask);
    }

    /*****************  provide info to the alert tree ***************/

    /**
     * Alert API
     * At this point we only export:
     * - RemoveDupFrags
     * - RemoveTempFrags
     * - PopulateTempFrags
     * - PopulateExtCache
     * - RecoverLostFrags
     * - ScanFrags
     */
  
    private CycleStates[] cycleStates;
    
    /*****************  cell recovery done info  *********************
     * We remember the time of the last recovery, and the disk mask
     * used, in order to implement Unhealed Failure Count.
     */
    long timeFinished(int taskId) { return timeFinished[taskId]; }
    void timeFinished(int taskId, long t) { timeFinished[taskId] = t; }
    public long timeFinished(String taskLabel) {

        return timeFinished[TaskList.taskId(taskLabel)];
    }
    DiskMask maskUsed(int taskId) { return maskUsed[taskId]; }
    void maskUsed(int taskId, DiskMask m) { maskUsed[taskId] = m; }
    DiskMask maskUsed(String taskLabel) {

        DiskMask mask = new DiskMask();
        for (int i=0; i < maskUsed.length; i++) {
            if (TaskList.taskLabel(i).equals(taskLabel)) {
                mask = maskUsed[i];
                break;
            }
        } 
        return mask;
    }


    /** get proxies for the given nodeIds, used when sloshing */
    private DataDocProxy[] getProxies(ArrayList nodeIds) {

        DataDocProxy[] proxies = new DataDocProxy[nodeIds.size()];
        for (int i=0; i < nodeIds.size(); i++) {
            int nodeId = ((Integer)nodeIds.get(i)).intValue();
            proxies[i] = DataDocProxy.getDataDocProxy(nodeId);
        } 
        return proxies;
    }


    /* PRIVATE ATTRIBUTES */

    private DiskMask[] doneMasks;       // local disks done tasks
    private DiskMask sloshDoneMask;     // local disks done sloshing

    private DiskMask[] cellDoneMasks;   // union of all nodes doneMasks
    private DiskMask cellSloshMask;     // union of nodes sloshDoneMasks

    // when a task completes cell-wide, record info about it
    private long[] timeFinished;        // time the task completed
    private DiskMask[] maskUsed;        // disk mask used

    private int unhealedFailuresTotal = 0;   // total unrecovered failures
    private int unhealedFailuresUnique = -1;  // failures on unique nodes

    private boolean failureTolerance = true;  // dynamic quorum met?

    private class CycleStates implements java.io.Serializable {
        private int _disk;
        private CycleStateInfo[] _values;

        CycleStates (int disk) {
            _disk = disk;

            // define the properties
            int numTasks = TaskList.getInstance().numTasks();
            _values = new CycleStateInfo[numTasks];
            for (int i = 0; i < numTasks; i++) {
                _values[i] = new CycleStateInfo (_disk, i);
            }
        }
        public CycleStateInfo getCycleState(int taskId) {

            if(null == _values[taskId])
                throw new IllegalArgumentException("Attempt to fetech bad task: " + taskId);

            return _values[taskId];            
        }
        public void refresh() {
            for (int i = 0; i < _values.length; i++) {
                _values[i].refresh();
            }
        }

    }

    // taskid, diskid
    private class CycleStateInfo implements java.io.Serializable {
        public int completion;
        public int cyclesDone;
        public int numFaults;
        public long cycleStart;
        public long currentRunStart;
        public long currentCycleStart;
        private int _taskId;
        private int _disk;

        CycleStateInfo(int disk, int taskId) {

            _taskId=taskId;
            _disk=disk;
            completion= -1;
            cyclesDone= -1;
            numFaults= -1;
            cycleStart= -1;            
            currentRunStart = -1;
            currentCycleStart = -1;
        }
        void refresh() {
            TaskList taskList = TaskList.getInstance();
            TaskListElem listElem = (TaskListElem) taskList.get (_taskId);

            numFaults =  listElem.getErrorCount(_disk);
            completion = listElem.taskCompletion(_disk);
            cyclesDone = listElem.cyclesDone(_disk);
            cycleStart = listElem.getCycleStart(_disk);
            currentRunStart = listElem.getCurrentRunStart(_disk);
            currentCycleStart = listElem.getCurrentCycleStart(_disk);
        }
    }
}
