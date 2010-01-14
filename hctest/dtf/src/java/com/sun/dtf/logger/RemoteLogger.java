package com.sun.dtf.logger;

import com.sun.dtf.DTFNode;
import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.flowcontrol.Sequence;
import com.sun.dtf.actions.logging.Log;
import com.sun.dtf.comm.rpc.Node;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.state.ActionState;

public class RemoteLogger {
    
    private static RemoteLogger _instance = null;
    
    private RemoteLogger() { }
    
    public static synchronized RemoteLogger getInstance() { 
        if (_instance == null)
            _instance = new RemoteLogger();
        
        return _instance;
    }

    public void info(String message) throws DTFException { 
        String tag = new Throwable().getStackTrace()[1].getClassName(); 
        info(tag, message);
    }
        
    public void info(String tag, String message) throws DTFException { 
        Log log = new Log();
        log.setCDATA(message);
        log.setTag(tag);
        log.setLevel(Log.INFO);
        sendLog(log);
    }

    public void error(String message) throws DTFException { 
        String tag = new Throwable().getStackTrace()[1].getClassName(); 
        error(tag, message);
    }
    
    public void error(String tag, String message) throws DTFException { 
        Log log = new Log();
        log.setCDATA(message);
        log.setTag(tag);
        log.setLevel(Log.ERROR);
        sendLog(log);
    }
    
    public void warn(String message) throws DTFException { 
        String tag = new Throwable().getStackTrace()[1].getClassName(); 
        warn(tag, message);
    }
    
    public void warn(String tag, String message) throws DTFException { 
        Log log = new Log();
        log.setCDATA(message);
        log.setTag(tag);
        log.setLevel(Log.WARN);
        sendLog(log);
    }
    
    private void sendLog(Log log) throws DTFException { 
        ActionState as = ActionState.getInstance();
        String threadId = 
                   (String)as.getState().getGlobalContext(Node.REMOTE_THREAD_CONTEXT);
        
        if (DTFNode.getOwner() != null && threadId != null) { 
            Sequence seq = new Sequence();
            seq.setThreadID(threadId);
            seq.addAction(log);
            String ownerId = DTFNode.getOwner().getOwner();
            Action.getComm().sendAction(ownerId, seq);
        }
    }
}