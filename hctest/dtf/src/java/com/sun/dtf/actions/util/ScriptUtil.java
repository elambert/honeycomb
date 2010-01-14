package com.sun.dtf.actions.util;

import java.io.InputStream;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.ActionFactory;
import com.sun.dtf.actions.basic.Script;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.state.ActionState;
import com.sun.dtf.state.DTFState;

public class ScriptUtil {

    /**
     * 
     * 
     * @param xmlFile
     * @param state
     * @throws DTFException
     */
    public static void executeScript(InputStream xmlFile, 
                                     DTFState state)
                  throws DTFException {
        DTFState current = ActionState.getInstance().getState();
        
        try {
            ActionState.getInstance().setState(state);
            
            // Parsing errors would be reported here.
            Action root = ActionFactory.parseAction(xmlFile);
            assert (root instanceof Script);
            
            state.setAction(root);
            root.execute();
        } catch (DTFException e) { 
            /*
             * ScripUtil is the only place the exceptions are logged from and 
             * by controlling if it was logged we can avoid logging the same 
             * messages more than once.
             */
            if (!e.wasLogged())  {
                Action.getLogger().error("Error running script.",e);
                e.logged();
            }
            throw e;
        } finally {
            ActionState.getInstance().delState();
            ActionState.getInstance().setState(current);
            // clean up unlocked components
            Action.getComm().getCommClient("dtfc").unregister(state);
        }
    }
}
