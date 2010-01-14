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

public class DebugOperation
    implements OperationInterface {

    /* adding these options (plus port number) to a java command line makes the JVM
     * listen on given port for a debugger connection. You can then attach the debugger
     * to the running JVM at any time by doing:
     * jdb -attach <port>
     */
    private static String debugOptions = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=";
    
    /* for each service JVM, we use this port + index into JVM array from node_config.xml */
    private static int baseDebugPort = 8000;

    private String[] jvms = null; // list of JVMs from node_config.xml

    private Document config = null; // needed by private methods
    private PrintStream out = null; 

    DebugOperation() {
    }

    public boolean execute(PrintStream _out,
                           Document _config,
                           String[] args)
        throws NodeConfigException {

        out = _out;
        config = _config;
        
        boolean overwrite = false;

        if (args.length < 2) {
            throw new NodeConfigException("debug needs a target and a subcommand");
        }

        if (!args[0].equals("print") && !args[0].equals("on") && !args[0].equals("off")) {
            throw new NodeConfigException("Sub operation ["+args[0]+"] is not supported");
        }

        if (args[1].equals("ALL")) {
            overwrite = executeForEachJVM(args[0]);
        } else {
            overwrite = executeForJVM(args[0], args[1]);
        }

        return overwrite;
    }

    public void usage(PrintStream out) {
        out.println("debug\n"+
                    "\ton <jvm name>  : enable debugging support for given JVM\n"+
                    "\toff <jvm name> : disable debugging support for given JVM\n"+
                    "\tprint <jvm name> : show whether debugging support is on or off in given JVM\n"+
                    "\tuse ALL for <jvm name> to apply command to all service JVMs"); 
    }

    private boolean executeForEachJVM(String cmd)
        throws NodeConfigException {

        boolean overwrite = false;

        String[] jvms = getJVMs();
        for (int i=0; i < jvms.length; i++) {
            try {
                overwrite |= executeForJVM(cmd, jvms[i]);
            } catch (NodeConfigException e) {
                out.println("ERROR: "+e.getMessage());
            }
        }
        return overwrite;
    }

    private boolean executeForJVM(String cmd, String jvmName)
        throws NodeConfigException {

        if (jvmName.equals("NODE-SERVERS")) { // special case
            out.println("JVM NODE-SERVERS : not controlled by node config editor. "+
                                          "Use honeycomb start script and debug port "+debugPort(jvmName));
            return false;
        }

        Element jvm = Utils.getElement(config.getDocumentElement(), 
                                          JVM_TAG, JVM_NAME_ATTR, jvmName);
        if (jvm == null) {
            throw new NodeConfigException("JVM ["+jvmName+"] does not exist. Use add jvm first");
        }

        String params = jvm.getAttribute(JVM_PARAMS_ATTR);
        String port = findDebugPort(params);

        boolean overwrite = false;

        if (cmd.equals("print")) { // PRINT DEBUG STATE: ON (WHICH PORT) OR OFF
            if (port == null) {
                out.println("JVM "+jvmName+" : debugging is OFF");
            } else {
                out.println("JVM "+jvmName+" : debugging is ON on port "+port+". Use: jdb -attach "+port);
            }
        } 
        else if (cmd.equals("on")) { // TURN DEBUG ON
            if (port != null) {
                out.println("JVM "+jvmName+" : debugging is already ON on port "+port+". Use: jdb-attach "+port);
            } else {
                port = debugPort(jvmName);
                if (port == null) {
                    throw new NodeConfigException("JVM "+jvmName+" : no debug port configured");
                }
                String newParams = addDebugPort(params, port);
                jvm.setAttribute(JVM_PARAMS_ATTR, newParams);
                overwrite = true;
            }
        } 
        else if (cmd.equals("off")) { // TURN DEBUG OFF
            if (port == null) {
                out.println("JVM "+jvmName+" : debugging is already OFF");
            } else {
                String newParams = removeDebugPort(params);
                jvm.setAttribute(JVM_PARAMS_ATTR, newParams);
                overwrite = true;
            }
        } 
        else {
            throw new NodeConfigException("Sub operation ["+cmd+"] is not supported");
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

    private String debugPort(String jvm) {
        String[] jvms = getJVMs();
        for (int i=0; i < jvms.length; i++) {
            if (jvms[i].equals(jvm)) {
                return String.valueOf(baseDebugPort + i);
            }
        }
        return null; // no match
    }

    private String findDebugPort(String params) {
        String[] opts = params.split(" ");
        for (int i=0; i < opts.length; i++) {
            if (opts[i].startsWith(debugOptions)) {
                String[] dopts = opts[i].split("=");
                String port = dopts[dopts.length-1]; // last token
                return port;
            }
        }
        return null; // no debug options found
    }

    private String addDebugPort(String params, String port) {
        String newParams = params + " " + debugOptions + port + " ";
        return newParams;
    }

    private String removeDebugPort(String params) {
        StringBuffer newParams = new StringBuffer();
        String[] opts = params.split(" ");
        for (int i=0; i < opts.length; i++) {
            if (!opts[i].startsWith(debugOptions)) {
                newParams.append(opts[i] + " ");
            } // do not append debug options
        }
        return newParams.toString();
    }

}
