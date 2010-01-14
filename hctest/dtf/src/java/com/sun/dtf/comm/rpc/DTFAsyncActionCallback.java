package com.sun.dtf.comm.rpc;

import org.apache.xmlrpc.XmlRpcRequest;
import org.apache.xmlrpc.client.AsyncCallback;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.util.DTFActionCallback;
import com.sun.dtf.exception.DTFException;


public class DTFAsyncActionCallback implements AsyncCallback {
   
    private DTFActionCallback _callback = null;
    
    public DTFAsyncActionCallback(DTFActionCallback callback) { 
        _callback = callback;
    }
    
    public void handleError(XmlRpcRequest arg0, Throwable arg1) {
        _callback.failed(arg1);
    }

    public void handleResult(XmlRpcRequest arg0, Object result) {
        if (result instanceof ActionResult) { 
            _callback.succeeded((ActionResult)result);
        } else { 
            DTFException exception = 
                 new DTFException("Return type is not of the instance [" + 
                                  Action.getClassName(result.getClass()) + "]");
            _callback.failed(exception);
        }
    }
}
