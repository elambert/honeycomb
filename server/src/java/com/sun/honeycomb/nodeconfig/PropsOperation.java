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



package com.sun.honeycomb.nodeconfig;

import org.w3c.dom.Document;
import java.io.PrintStream;
import org.w3c.dom.Element;

public class PropsOperation
    implements OperationInterface {


    private String[] jvms = null; // list of JVMs from node_config.xml

    private Document config = null; // needed by private methods
    private PrintStream out = null; 

    PropsOperation() {
    }

    public boolean execute(PrintStream _out,
                           Document _config,
                           String[] args)
        throws NodeConfigException {

        out = _out;
        config = _config;
        
        boolean overwrite = false;

        if (args.length < 3) {
            throw new NodeConfigException("props needs a subcommand,jvmname and property");
        }
        
        if (args[1].equals("ALL")) {
            overwrite = executeForEachJVM(args[0], args[2]);
        } else {
            overwrite = executeForJVM(args[0], args[1], args[2]);
        }

        return overwrite;
    }

    public void usage(PrintStream out) {
        out.println("props\n"+
                    "\tset <jvm name> <property>  : set property on a given JVM." +
                    "\tunset <jvm name> <property> : unset property on a given JVM." +
                    "\tuse ALL for <jvm name> to apply command to all service JVMs"); 
    }

    private boolean executeForEachJVM(String cmd, String property)
        throws NodeConfigException {

        boolean overwrite = false;

        String[] jvms = getJVMs();
        for (int i=0; i < jvms.length; i++) {
            try {
                overwrite |= executeForJVM(cmd, jvms[i], property);
            } catch (NodeConfigException e) {
                out.println("ERROR: "+e.getMessage());
            }
        }
        return overwrite;
    }

    private boolean executeForJVM(String cmd, String jvmName, String property)
        throws NodeConfigException {

        Element jvm = Utils.getElement(config.getDocumentElement(), 
                                          JVM_TAG, JVM_NAME_ATTR, jvmName);
        if (jvm == null) {
            throw new NodeConfigException("JVM ["+jvmName+"] does not exist. Use add jvm first");
        }

        String params = jvm.getAttribute(JVM_PARAMS_ATTR);

        boolean overwrite = false;
	
        if (cmd.equalsIgnoreCase("set")){
        	overwrite = true;
            jvm.setAttribute(JVM_PARAMS_ATTR,params.trim() + " " + property + "  ");
        } else if (cmd.equalsIgnoreCase("unset")){
        	overwrite = true;
        	jvm.setAttribute(JVM_PARAMS_ATTR,(params.trim().replace(property,"") + "  "));
        } else {
        	throw new NodeConfigException("Unknown props subcommand: " + cmd + " only set and unset supported.");
        }
                
        return overwrite;
    }

    /* Get a list of JVM names from config file. Preserves order.
     */
    private String[] getJVMs() {
        if (jvms == null) {
            jvms = Utils.getElements(config.getDocumentElement(), 
                                     JVM_TAG, JVM_NAME_ATTR);
        }
        return jvms;
    }
}
