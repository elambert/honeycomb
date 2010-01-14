//
//  Task.java
//  TaskStepperBench
//
//  Created by Sacha Arnoud on 3/27/06.
//  Copyright 2006 __MyCompanyName__. All rights reserved.
//

package com.sun.honeycomb.datadoctor;

import com.apple.cocoa.foundation.*;
import com.apple.cocoa.application.*;

import java.util.Random;
import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.layout.DiskMask;
import java.net.URL;

public class Task 
    implements Steppable, NSTableView.DataSource {
    
    private static final int NB_STEPS = 10;

    private GUICallback callback;
    private Random rand;
    private long startTime;
    private long multiplyFactor;

    private long[] times;
    private long[] drifts;

    private int nbSteps;
    private long stepGoal;
    private boolean hickup;
    private Thread t;
    
    public Task(GUICallback _callback) {
        callback = _callback;
        times = new long[NB_STEPS];
        drifts = new long[NB_STEPS];
	multiplyFactor = 1000;
    }

    public void setMultiplyFactor(long value) {
	multiplyFactor = value;
    }

    public void init(String taskName,
                     DiskId diskId) {
        rand = new Random();
        startTime = -1;
        nbSteps = 0;
	hickup = false;
	t = null;
    }

    public void hickup() {
	hickup = true;
    }
    
    public void setGoal(long cycleGoal) {
        stepGoal = cycleGoal*1000/NB_STEPS;
    }

    public String getName() {
        return("Task");
    }

    public int getNumSteps() {
        return(NB_STEPS);
    }

    public void step(int stepNum) {
        if (startTime == -1)
            startTime = System.currentTimeMillis();
        System.out.println("RUN "+(stepNum+1));
        times[stepNum] = System.currentTimeMillis()-startTime;
        drifts[stepNum] = nbSteps*stepGoal-times[stepNum];
        nbSteps++;
        callback.setStep(stepNum+1);
        int timeToWait = -1;
	if (hickup) {
	    timeToWait = 10;
	    hickup = false;
	    System.out.println("Hickup for step "+(stepNum+1));
	} else {
	    timeToWait = rand.nextInt(5);
	}
	synchronized (this) {
	    t = Thread.currentThread();
	}
        try {
	    System.out.println("Task go to sleep");
            Thread.sleep(timeToWait*multiplyFactor);
	    System.out.println("Task woke up");
        } catch (InterruptedException e) {
	    System.out.println("Task "+(stepNum+1)+" interrupted");
        }
	synchronized (this) {
	    t = null;
	}
    }

    public void newDiskMask(DiskMask mask) {
    }

    public synchronized void abortStep() {
	if (t != null)
	    t.interrupt();
    }

    public int numberOfRowsInTableView(NSTableView aTableView) {
        return(NB_STEPS);
    }

    public Object tableViewObjectValueForLocation(NSTableView aTableView,
                                                  NSTableColumn aTableColumn,
                                                  int rowIndex) {
        int column = ((Integer)aTableColumn.identifier()).intValue();
        switch (column) {
        case 0:
            return(new Integer(rowIndex+1));
        case 1:
            return(new Long(times[rowIndex]));
        case 2:
            return(new Long(drifts[rowIndex]));
        default:
            return(null);
        }
    }

    public void tableViewSetObjectValueForLocation(NSTableView aTableView,
                                                   Object anObject,
                                                   NSTableColumn aTableColumn,
                                                   int rowIndex) {
    }
    
    public void tableViewSortDescriptorsDidChange(NSTableView tableView,
                                                  NSArray oldDescriptors) {
    }

    public int tableViewValidateDrop(NSTableView tableView,
                                     NSDraggingInfo info,
                                     int row,
                                     int operation) {
        return(0);
    }

    public boolean tableViewWriteRowsToPasteboard(NSTableView tableView,
                                                  NSArray rows,
                                                  NSPasteboard pboard) {
        return(false);
    }

    public boolean tableViewWriteRowsToPasteboard(NSTableView tableView,
                                                  NSIndexSet rows,
                                                  NSPasteboard pboard) {
        return(false);
    }

    public NSArray tableViewNamesOfPromisedFilesDroppedAtDestination(NSTableView tv,
                                                                     URL dropDestination,
                                                                     NSIndexSet indexSet) {
        return(null);
    }

    public boolean tableViewAcceptDrop(NSTableView tableView,
                                       NSDraggingInfo info,
                                       int row,
                                       int operation) {
        return(false);
    }
}