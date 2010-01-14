package com.sun.dtf.comm.rpc;

import com.sun.dtf.DTFNode;
import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.flowcontrol.Sequence;
import com.sun.dtf.comm.Comm;
import com.sun.dtf.config.Config;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.recorder.RemoteRecorder;
import com.sun.dtf.state.ActionState;
import com.sun.dtf.state.DTFState;
import com.sun.dtf.storage.StorageFactory;

public class Node implements NodeInterface {
    
    public static final String ACTION_RESULT_CONTEXT="dtf.action.result.ctx"; 
    public static final String REMOTE_THREAD_CONTEXT="dtf.remote.thread.ctx"; 

    public Boolean heartbeat() {
        Comm.heartbeat();
        return Boolean.TRUE;
    }
    
    public ActionResult execute(String id, Action action) throws DTFException {
        ActionResult result = new ActionResult();
        ActionState as = ActionState.getInstance();
      
        String threadId = null;
        if (action instanceof Sequence) 
            threadId = ((Sequence)action).getThreadID();
      
        if (DTFNode.getType().equals("dtfx") && threadId != null) { 
            /*
             * XXX: I don't like this solution because it is in no way 
             *      elegant or easy to understand.
             *      
             * Remote events being executed on the dtfx under a specified 
             * threadid
             * 
             * We need to dup the state and also make sure that the Global 
             * Context isn't shared by other threads calling back.
             * 
             */
            DTFState state = as.getState(threadId).duplicate();
            as.setState(state);
            state.resetGlobalContext();
            state.registerContext(ACTION_RESULT_CONTEXT, result);
            try {
                action.execute();
            } finally { 
                state.unRegisterContext(ACTION_RESULT_CONTEXT);
                as.delState();
            }
        } else { 
            RemoteRecorder rrecorder = new RemoteRecorder(result, true, threadId);
   
            DTFState main = as.getState();
            DTFState state = new DTFState(new Config(),new StorageFactory());
            state.setComm(main.getComm());
            as.setState(state);
            state.registerContext(ACTION_RESULT_CONTEXT, result);
            try { 
                if (threadId != null)
                    state.registerGlobalContext(REMOTE_THREAD_CONTEXT, threadId);
                Action.pushRecorder(rrecorder,null);
                action.execute();
            } finally { 
                Action.popRecorder();
                state.unRegisterGlobalContext(REMOTE_THREAD_CONTEXT);
                state.unRegisterContext(ACTION_RESULT_CONTEXT);
                as.delState();
            }
        }
        
        return result;
    }
}
