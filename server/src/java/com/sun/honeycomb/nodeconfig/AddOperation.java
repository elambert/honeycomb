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

public class AddOperation
    implements OperationInterface {

    AddOperation() {
    }

    public boolean execute(PrintStream out,
                           Document config,
                           String[] args)
        throws NodeConfigException {

        if (args.length == 0) {
            throw new NodeConfigException("add needs a suboperation");
        }

        if (args[0].equals("jvm")) {
            if (args.length != 3) {
                throw new NodeConfigException("add jvm takes 2 extra arguments");
            }
            String jvmName = args[1];
            int memSize = Integer.parseInt(args[2]);

            if (Utils.getElement(config.getDocumentElement(),
                                 JVM_TAG, JVM_NAME_ATTR, jvmName) != null) {
                throw new NodeConfigException("JVM ["+jvmName+"] already exists");
            }

            out.println("Adding JVM ["+jvmName+"] with "+memSize+"M of memory");

            Element node = Utils.getElement(config.getDocumentElement(),
                                            NODE_TAG, null, null);
            Element jvm = config.createElement(JVM_TAG);
            jvm.setAttribute(JVM_NAME_ATTR, jvmName);
            jvm.setAttribute(JVM_RSS_ATTR, memSize+"MB");
            jvm.setAttribute(JVM_PARAMS_ATTR, "-server -Dsun.io.useCanonCaches=false -Xms"+(memSize>>1)+"m -Xmx"+memSize+"m");
            node.appendChild(jvm);

        } else if (args[0].equals("svc")) {

            if (args.length < 2) {
                throw new NodeConfigException("add svc does not have enough parameters ["+
                                              args.length+"]");
            }
		
            boolean masterSvc = false;
            String shutdownTimeout = null;

            int baseIdx = 1;
            boolean doneWithExtraArgs = false;

            while (!doneWithExtraArgs) {
                if (args[baseIdx].equals("master")) {
                    masterSvc = true;
                } else if (args[baseIdx].startsWith("shutdownTimeout")) {
                    String[] tokens = args[baseIdx].split("=");
                    shutdownTimeout = tokens[1];
                } else {
                    doneWithExtraArgs = true;
                }
                if (!doneWithExtraArgs) {
                    baseIdx++;
                }
            }
            
            if (args.length < 4+baseIdx) {
                throw new NodeConfigException("add svc does not have enough parameters ["+
                                              args.length+"]");
            }

            String jvmName = args[baseIdx];
            String svcName = args[baseIdx+1];
            String svcClass = args[baseIdx+2];
            String runlevel = args[baseIdx+3];

            Element jvm = Utils.getElement(config.getDocumentElement(),
                                           JVM_TAG, JVM_NAME_ATTR, jvmName);
            if (jvm == null) {
                throw new NodeConfigException("JVM ["+jvmName+"] does not exist. Use add jvm first");
            }
            
            Element group = Utils.getElement(jvm, GRP_TAG, GRP_LEVEL_ATTR, runlevel);
            if (group == null) {
                // Create the group
                out.println("Creating a group for runlevel "+runlevel+" in JVM ["+jvmName+"]");
                group = config.createElement(GRP_TAG);
                if (masterSvc) {
                    group.setAttribute(GRP_LOCATION_ATTR, GRP_MASTER_LOCATION);
                }
                group.setAttribute(GRP_LEVEL_ATTR, runlevel);
                jvm.appendChild(group);
            } else {
                if (Utils.getElement(group, SVC_TAG, SVC_NAME_ATTR, svcName) != null) {
                    throw new NodeConfigException("Service "+svcName+" already exists in JVM ["+jvmName+"]");
                }
            }

            Element svc = config.createElement(SVC_TAG);
            svc.setAttribute(SVC_NAME_ATTR, svcName);
            svc.setAttribute(SVC_CLASS_ATTR, svcClass);
            if (shutdownTimeout != null) {
                svc.setAttribute(SVC_SHUTDOWN_TIMEOUT, shutdownTimeout);
            }
            group.appendChild(svc);

        } else {
            throw new NodeConfigException("Sub operation ["+args[0]+"] is not supported");
        }

        return(true);
    }
    
    public void usage(PrintStream out) {
        out.println("add\n"+
                    "\tjvm <jvm name> <mem size> : create a new JVM\n"+
                    "\tsvc [master] [shutdownTimeout=???] <jvm name> <svc name> <svc class> <runlevel> : create a new service in a preexisting JVM");
    }
}
