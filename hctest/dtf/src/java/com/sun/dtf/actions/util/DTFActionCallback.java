package com.sun.dtf.actions.util;

import com.sun.dtf.comm.rpc.ActionResult;

public interface DTFActionCallback {
    public void succeeded(ActionResult action);
    public void failed(Throwable t);
}
