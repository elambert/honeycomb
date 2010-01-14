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



import com.apple.cocoa.foundation.*;
import com.apple.cocoa.application.*;

import com.sun.honeycomb.datadoctor.TaskStepperGlue;
import com.sun.honeycomb.datadoctor.GUICallback;

public class Controller 
    implements GUICallback {

    public NSLevelIndicator levelIndicator; /* IBOutlet */
    public NSLevelIndicator load; /* IBOutlet */
    public NSProgressIndicator runningState; /* IBOutlet */
    public NSTableView table; /* IBOutlet */

    private int cycleGoal;
    private TaskStepperGlue glue;
    private boolean fastMode;

    public Controller() {
	cycleGoal = 40;
	fastMode = false;
    }

    public void awakeFromNib() {
        runningState.setUsesThreadedAnimation(true);
        glue = new TaskStepperGlue(this);

        NSArray array = table.tableColumns();
        for (int i=0; i<array.count(); i++) {
            NSTableColumn column = (NSTableColumn)array.objectAtIndex(i);
            column.setIdentifier(new Integer(i));
            switch (i) {
            case 0:
                column.headerCell().setStringValue("Step");
                break;
            case 1:
                column.headerCell().setStringValue("Absolute time");
                break;
            case 2:
                column.headerCell().setStringValue("Current drift");
                break;
            }
            column.sizeToFit();
        }
        table.setDataSource(glue.getDataSource());
	load.setDoubleValue(0);
    }
    
    public void setStep(int value) {
        levelIndicator.setIntValue(value);
	load.setDoubleValue((double)glue.getLoad());
        table.reloadData();
    }
    
    public void startStepper(Object sender) { /* IBAction */
        runningState.startAnimation(this);
        glue.startStepper(cycleGoal);
    }

    public void stopStepper(Object sender) { /* IBAction */
	System.out.println("Stop requested");
        runningState.stopAnimation(this);
        glue.stopStepper();
    }

    public void hickup(Object sender) { /* IBAction */
	glue.hickup();
    }

    public int cycleGoal() {
	return(cycleGoal);
    }
    
    public void setCycleGoal(int value) {
	cycleGoal = fastMode ? value/10 : value;
	if (glue != null) {
	    glue.setCycleGoal((long)cycleGoal);
	}
	System.out.println("Changed cycle goal ["+
			   cycleGoal+"]");
    }

    public boolean fastMode() {
	return(fastMode);
    }

    public void setFastMode(boolean v) {
	fastMode = v;
	glue.fastMode(fastMode);
	setCycleGoal(fastMode ? cycleGoal : cycleGoal*10);
    }
}
