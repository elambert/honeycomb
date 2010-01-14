package com.sun.dtf.actions.file;

import java.net.URI;

import com.sun.dtf.DTFNode;
import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.component.Component;
import com.sun.dtf.comm.rpc.ActionResult;
import com.sun.dtf.comm.rpc.Node;
import com.sun.dtf.exception.DTFException;

public abstract class Returnfile extends Action {
   
    protected String uri = null;

    public Returnfile() { }
    
    public URI getUri() throws DTFException { return parseURI(uri); }
    public void setUri(String uri) { this.uri = uri; }

    public static void genReturnFile(String uri, 
                                     int offset, 
                                     String remotefile,
                                     boolean append) {
        ActionResult ar = (ActionResult) getContext(Node.ACTION_RESULT_CONTEXT);
        Component cmp = new Component();
        cmp.setId(DTFNode.getId());
        Getfile newget = new Getfile();
        newget.setUri(uri);
        newget.setOffset(offset);
        newget.setAppend(append);
        newget.setRemotefile(remotefile);
        cmp.addAction(newget);
        ar.addAction(cmp);
    }
}
