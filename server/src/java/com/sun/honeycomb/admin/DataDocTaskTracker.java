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



package com.sun.honeycomb.admin;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Date;
import java.util.Arrays;

/**
 * Inner class to abstract out DataDoctor Recovery Task tracking. Calculations
 * have a reporting lag of up to 10 seconds: DataDoctorProxy publish time, and
 * the sleep() time of the AdminServer in which we run.
 */
public class DataDocTaskTracker {
    private int    _numNodes;         // how many nodes in the system?
    private int    _disksPerNode;     // how many disks per node?
    private String _name;             // task name
    private long   _taskStarted;
    private long   _taskCompleted;    // timestamp at completion
    private int[]  _cycles;           // holds the current cycle number
    private int[]  _oldCycles;        // holds the last completed cycle nb
    private int[]  _disks;            // holds the % completion per disk
    private long[] _times;            // holds the timestamp the disk completed
    private long[] _startTimes;       // holds the time this disks cycles started
    private int[]  _errors;           // holds the num errors per disk per task
    private long[] _currentRunStarts;  //holds timestamp run error free since 
    private long[] _currentCycleStarts;//holds timestamp of 10000 steps ago
    private long   _lastShortestDisk; 
    private long   _lastLongestDisk;
    private long   _lastAverageDisk;
    private long   _lastCompleted;      // timestamp of the last completed cycle (possibly
                                        // with errors
    private long   _lastValidCompleted; // holds the earliest time at which we know
                                        // this cycle completed clusterwide with no
                                        // errors. this is extremely important for
                                        // backup, to know when the last time popsyscache
                                        // knew about every object in the cluster
    private boolean _invalidCycle = false; // set if this cycle should count as 
                                           // a valid cycle. currently only used when
                                           // syscache is wiped.
    
    private int[]   _initialCycles;
    
    private static final Logger logger =
        Logger.getLogger(DataDocTaskTracker.class.getName());

    public DataDocTaskTracker (int task, String taskName, 
                               int numNodes, int disksPerNode) {
        _name = taskName;
        _numNodes = numNodes; // needs to be maximum size, 16 for us now.
        _disksPerNode = disksPerNode;
        _disks = new int[_numNodes*_disksPerNode];
        _cycles = new int[_numNodes*_disksPerNode];
        _initialCycles = new int[_numNodes*_disksPerNode];
        _times = new long[_numNodes*_disksPerNode];
        _errors = new int[_numNodes*_disksPerNode];
        _startTimes = new long[_numNodes*_disksPerNode];
        _currentRunStarts = new long[_numNodes*_disksPerNode];
        _currentCycleStarts = new long[_numNodes*_disksPerNode];
        Arrays.fill (_disks, -1);
        Arrays.fill (_cycles, Integer.MIN_VALUE);
        Arrays.fill (_initialCycles, Integer.MIN_VALUE);
        Arrays.fill (_times, -1);
        Arrays.fill (_errors, -1);
        Arrays.fill (_startTimes, -1);
        Arrays.fill (_currentRunStarts, 0);
        Arrays.fill (_currentCycleStarts, 0);
        _taskCompleted = 0;
        _lastShortestDisk = 0;
        _lastAverageDisk = 0;
        _lastLongestDisk = 0;
        _lastCompleted = 0;
        _lastValidCompleted = -1;
        
        // for every disk,each reset, we need to remember the cycle that disk
        // was on when we marked it complete
        _oldCycles = new int[_numNodes*_disksPerNode];
        Arrays.fill (_oldCycles, Integer.MIN_VALUE);
        
        _taskStarted = System.currentTimeMillis();
        _lastShortestDisk = Long.MAX_VALUE;
        _lastLongestDisk = Long.MIN_VALUE;
        
        _invalidCycle = false;
    }

    private long min (long[] values) {
        long minValue = Long.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > 0 && values[i] < minValue) {
                minValue = values[i];
            }
        }
        return minValue;
    }

    private long max (long[] values) {
        long maxValue = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > maxValue) {
                maxValue = values[i];
            }
        }
        return maxValue;
    }
    
    public void resetPreviousCompleted () {
        _lastValidCompleted = 0;
        _invalidCycle = true;
    }
    
    public void reset () {
        _lastCompleted = _taskCompleted;
        _taskCompleted = 0;
        _taskStarted = System.currentTimeMillis();

        _lastShortestDisk = Long.MAX_VALUE;
        _lastLongestDisk = Long.MIN_VALUE;

        //logger.info(_name + " === invalidCycle="+_invalidCycle);
        //logger.info(_name + " === errorCount="+getErrorCount());
        if (!_invalidCycle && getErrorCount() == 0) {
            // only if the cycle completed with no errors do we want to updated the
            // value of lastValidCompleted. THis value is used to determine the validity
            // of the data scanned/inserted by the given task.
            long last = min(_startTimes);
            //logger.info(_name + " === last="+last);
            if (last == Long.MAX_VALUE) {
                // if we got an invalid value back from min(startTimes) don't update
                // anything, because something's wrong
                logger.warning(_name + " cycle completed, but earliest start time was invalid");
            } else {
                // the earliest start time of the 1st disk task is our timestamp for
                // for the end of the previous cycle.
                logger.info (_name + " lastValidCycle time is " + new Date (last));
                _lastValidCompleted = last;
            }
        }
        
        _invalidCycle = false;
        
        // calculate short/long/avg and zero out the disks
        long totalTime = 0;
        int numDisks = _disks.length;
        for (int i = 0; i < _disks.length; i++) {
            if (_disks[i] == -1) {
                // logger.info ("skipping disk["+i+"] because it's dead");
                numDisks--;
                continue;
            }

            if (_times[i] < _lastShortestDisk)
                _lastShortestDisk = _times[i];
            if (_times[i] > _lastLongestDisk)
                _lastLongestDisk = _times[i];
            
           totalTime += _times[i];    
           _disks[i] = -1; 
           _times[i] = -1; 
           _errors[i] = -1;
           _startTimes[i] = -1;
           _initialCycles[i] = Integer.MIN_VALUE;
           _oldCycles[i] = _cycles[i];
        }

        if (numDisks == 0) {    
            _lastAverageDisk = 0;
            // logger.info ("reset found no disks");
            return;
        }
        _lastAverageDisk = totalTime / numDisks;
    }

    public boolean isComplete() {
        if (logger.isLoggable(Level.FINEST))
            logger.finest("disks.length=" + _disks.length);
        int numDisks = _disks.length;

        for (int i = 0; i < _disks.length; i++) {
            if (logger.isLoggable(Level.FINE))
                logger.fine (_name + ": " + _disks[i] + ":" + _cycles[i] + ":" +
                        _oldCycles[i] + ": " + _startTimes[i]);

            if (_disks[i] < 0) {
                if (logger.isLoggable(Level.FINE))
                    logger.fine (_name + ": offline disk, skipping");
                numDisks--;
                continue; // skip offline disks
            }

            if (_cycles[i] == _initialCycles[i]) {
                if (logger.isLoggable(Level.FINE))
                    logger.fine (_name + ": initial cycle, not complete");
                return false;
                
            }
            
            if (_cycles[i] == _oldCycles[i]) {
                if (logger.isLoggable(Level.FINE))
                    logger.fine (_name + ": cycles match, not complete");
                return false;
            }
        }

        // if all the disks are offline, we're not complete
        if (numDisks <= 0) {
            if (logger.isLoggable(Level.FINE))
                logger.fine(_name + ": not complete because numDisks=" + numDisks);
            return false;
        }

        _taskCompleted = System.currentTimeMillis();
        
        logger.info (_name + " completed at " + new Date(_taskCompleted)
            + " started at " + new Date (min (_startTimes)));
        return true;
    }

    public void updateCompletion (int nodeid, 
                                  int disk, 
                                  long startTime, 
                                  int percentDone,
                                  int cyclesDone, 
                                  int errorCount,
                                  long currentRunStart,
                                  long currentCycleStart) {
        int index = ((nodeid - 101) * _disksPerNode) + disk;

        if (logger.isLoggable(Level.FINER)) {
            logger.finer("Noting Update for " +_name + ":" + nodeid + ":" + disk+ " to " +
                         _disks[index] + "% complete at " + (_times[index]/(1000*60)) 
                         + " minutes, curCycle=" + cyclesDone +
                         " startTime=" + startTime +
                         " currentRunStart=" + currentRunStart +
                         " currentCycleStart=" + currentCycleStart);
        }


        _currentRunStarts[index] = currentRunStart;
        _currentCycleStarts[index] = currentCycleStart;

        // Note the original cycle that we started on. N.B.: Since cycles now
        // wrap, we can't always rely on this value (see isComplete())
        if (_cycles[index] == Integer.MIN_VALUE) {
            _initialCycles[index] = cyclesDone;
        }
        
        // once a disk hits 100%, don't change it's value until the
        // entire tasks completes (all disks hit 100%)
        if (_disks[index] == 100) {
            if (logger.isLoggable(Level.FINER)) {
                logger.finer ("Skipping update on" +_name + ":" + nodeid + ":" + disk+ " to " +
                              _disks[index] + "% complete is 100");
            }
            return;
        }

        _disks[index] = percentDone;

        if (_disks[index] == -1)
            return; // just mark the disk as offline, don't update anything
                    // else

        _cycles[index] = cyclesDone;
        _times[index] = System.currentTimeMillis() - _taskStarted;
        _errors[index] = errorCount;
        _startTimes[index] = startTime;

        if (logger.isLoggable(Level.FINER)) {
            logger.finer ("Updating " +_name + ":" + nodeid + ":" + disk+ " to " +
                          _disks[index] + "% complete at " + (_times[index]/(1000*60)) 
                          + " minutes, curCycle=" + cyclesDone + " startTime=" + startTime);
        }
    }

    public long getSlowestDiskTime() {
        if (_lastLongestDisk == Long.MIN_VALUE)
            return 0;
        return _lastLongestDisk;
    }

    public long getFastestDiskTime() {
        if (_lastShortestDisk == Long.MAX_VALUE)
            return 0;
        return _lastShortestDisk;
    }

    public long getAverageDiskTime() {
        return _lastAverageDisk;
    }

    public long getCompletionTime() {
        return _lastCompleted;
    }

    public long getStartTime() {
        long last = min(_startTimes);
        if (last == Long.MAX_VALUE) {
            return 0;
        } else {
            return last;
        }
    }
    
    public long getPreviousCompletionTime() {
        return _lastValidCompleted;
    }
    
    // calculate the percent completion of this task. this is just a simple]
    // average of all disks current completion percent
    public int getCompletionPercent() {
        int total = 0;
        int numDisks = _disks.length;

        for (int i = 0; i < _disks.length; i++) {
//           logger.info (_name + ", disk=" + _disks[i]);
           if (_disks[i] == -1) {
                numDisks--;
                continue;
           }
           if (_cycles[i] > _oldCycles[i]) {
            total += 100; // we've complted a cycle, so leave this at 100
           }
           else {
            total+= _disks[i];    
           }
        }
        if (numDisks == 0) {
            return 0;
        }
        return total/numDisks;
    }

    public int getErrorCount () {
        int count = 0;
        for (int i = 0; i < _errors.length; i++) {
            count += _errors[i];
        }
        return count;
    }
    
    public long getSuperRunStart() {
        return max(_currentRunStarts);
    }

    public long getSuperCycleStart() {
        long last = min(_currentCycleStarts);
        if (last == Long.MAX_VALUE) {
            return 0;
        } else {
            return last;
        }
    }

    public String getName() {
        return _name;
    }
}
