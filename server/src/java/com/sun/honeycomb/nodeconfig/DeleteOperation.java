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

public class DeleteOperation
    implements OperationInterface {

    DeleteOperation() {
    }

    public boolean execute(PrintStream out,
                           Document config,
                           String[] args)
        throws NodeConfigException {
        
        if (args.length == 0) {
            throw new NodeConfigException("del needs a subcommand");
        }

        if (args[0].equals("svc")) {
            if (args.length != 3) {
                throw new NodeConfigException("del svc needs 2 extra parameters");
            }
            Element jvm = Utils.getElement(config.getDocumentElement(),
                                           JVM_TAG, JVM_NAME_ATTR, args[1]);
            if (jvm == null) {
                throw new NodeConfigException("JVM ["+args[1]+"] couldn't be found");
            }

            Element svc = Utils.getElement(jvm, SVC_TAG, SVC_NAME_ATTR, args[2]);
            if (svc == null) {
                throw new NodeConfigException("There is no "+args[2]+" service in the JVM ["+
                                              args[1]+"]");
            }
            svc.getParentNode().removeChild(svc);

            // Check if the JVM still contains services

            Element[] children = Utils.getElements(jvm, SVC_TAG);
            if ((children == null) || (children.length == 0)) {
                out.println("The JVM ["+args[1]+"] does not have any service left. Deleting the JVM");
                
                jvm.getParentNode().removeChild(jvm);
            }

        } else {
            throw new NodeConfigException("Unknown command ["+args[0]+"]");
        }

        return(true);
    }

    public void usage(PrintStream out) {
        out.println("del\n"+
                    "\tsvc <jvm name> <svc name> : deletes a service from the config file");
    }
}
