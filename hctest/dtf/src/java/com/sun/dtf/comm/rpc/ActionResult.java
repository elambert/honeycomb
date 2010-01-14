package com.sun.dtf.comm.rpc;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.DTFException;

public class ActionResult extends Action {
    public void execute() throws DTFException {
        /*
         * There is not state where this is executing since it's a remote 
         * execution, so there's no need to change state firstly, secondly it 
         * would only result in unnecessary code having to be written.
         */
        executeChildrenWithoutStateChange();
    }
}
