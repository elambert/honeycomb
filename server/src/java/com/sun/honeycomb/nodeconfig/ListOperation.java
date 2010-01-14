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
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

public class ListOperation
    implements OperationInterface {
    
    ListOperation() {
    }

    // Returns true if the document needs to be rewritten

    public boolean execute(PrintStream out,
                           Document config,
                           String[] args)
        throws NodeConfigException {

        if (args.length == 0) {
            throw new NodeConfigException("list takes a subargument");
        }

        String subcmd = args[0];
        
        if (subcmd.equals("jvms")) {

            out.println("List of available jvms :");
            String[] jvms = Utils.getElements(config.getDocumentElement(),
                                              JVM_TAG,
                                              JVM_NAME_ATTR);
            for (int i=0; i<jvms.length; i++) {
                out.println("\t"+jvms[i]);
            }

        } else if (subcmd.equals("svcs")) {

            String jvmName = (args.length < 2) ? null : args[1];
            if (jvmName == null) {
                throw new NodeConfigException("No JVM specified");
            }
            
            NodeList list = config.getElementsByTagName(JVM_TAG);
            Element jvm = Utils.getElement(config.getDocumentElement(),
                                           JVM_TAG, JVM_NAME_ATTR, jvmName);
            if (jvm == null) {
                throw new NodeConfigException("JVM ["+jvmName+"] is not in the config file");
            }
            
            out.println("List of services in JVM ["+jvmName+"]");

            Element[] elems = Utils.getElements(jvm, SVC_TAG);            
            for (int i=0; i<elems.length; i++) {
                Element parent = (Element)elems[i].getParentNode();
                out.println("\t"+elems[i].getAttribute(SVC_NAME_ATTR)+" [runlevel "+
                            parent.getAttribute(GRP_LEVEL_ATTR)+"]");
            }
        } else {
            throw new NodeConfigException("Operation ["+subcmd+"] is not implemented");
        }

        return(false);
    }

    public void usage(PrintStream out) {
        out.println("list\n"+
                    "\tjvms : list all the configured JVMS\n"+
                    "\tsvcs <jvm> : list all the services in a given JVM");
    }
}
