//
//  TaskStepperGlue.java
//  TaskStepperBench
//
//  Created by Sacha Arnoud on 3/27/06.
//  Copyright 2006 __MyCompanyName__. All rights reserved.
//

package com.sun.honeycomb.datadoctor;

import com.apple.cocoa.foundation.*;
import com.apple.cocoa.application.*;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

public class TaskStepperGlue {
    
    private TaskStepper stepper;
    private Task task;
    private Method loadMethod;

    public TaskStepperGlue(GUICallback callback) {
        task = new Task(callback);
        stepper = new TaskStepper(new TaskInfo("Bench", TaskList.INFINITE_CYCLES,
                                               true, Task.class.getName(), "bla"),
                                  task);
	Class stepperClass = TaskStepper.class;

	try {
	    loadMethod = stepperClass.getMethod("getLoad", null);
	} catch (NoSuchMethodException e) {
	    System.out.println("The stepper does not provide load");
	    loadMethod = null;
	}
    }
    
    public void startStepper(long cycleGoal) {
        task.setGoal(cycleGoal);
        task.init("Bench", null);
        stepper.startStepper(cycleGoal, null);
    }
    
    public void stopStepper() {
        stepper.stopStepper();
    }

    public void setCycleGoal(long value) {
	stepper.cycleGoal(value);
	task.setGoal(value);
    }
    
    public void fastMode(boolean fast) {
	task.setMultiplyFactor(fast ? 100 : 1000);
    }

    public void hickup() {
	task.hickup();
    }

    public NSTableView.DataSource getDataSource() {
        return(task);
    }

    public float getLoad() {
	if (loadMethod == null) {
	    return(0);
	}

	float result = 0;
	try {
	    result = ((Float)loadMethod.invoke(stepper, null)).floatValue();
	} catch (IllegalAccessException e) {
	    result = 0;
	} catch (InvocationTargetException e) {
	    result = 0;
	}

	return(result);
    }
}
