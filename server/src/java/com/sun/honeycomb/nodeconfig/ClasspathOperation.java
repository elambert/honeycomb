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

public class ClasspathOperation
    implements OperationInterface {

    ClasspathOperation() {
    }

    public boolean execute(PrintStream out,
                           Document config,
                           String[] args)
        throws NodeConfigException {

        if (args.length == 0) {
            throw new NodeConfigException("cp needs a subcommand");
        }

        boolean result = false;

        if (args[0].equals("print")) {
            Element node = Utils.getElement(config.getDocumentElement(),
                                            NODE_TAG, null, null);
            out.println("Current classpath is :\n"+
                        node.getAttribute(NODE_CP_ATTR));

        } else if (args[0].equals("add")) {

            if (args.length != 2) {
                throw new NodeConfigException("cp add needs an extra argument");
            }
            String newPath = args[1];
            Element node = Utils.getElement(config.getDocumentElement(),
                                            NODE_TAG, null, null);
            String cp = node.getAttribute(NODE_CP_ATTR);
            String[] paths = cp.split(":");
            boolean found = false;

            for (int i=0; (!found) && (i<paths.length); i++) {
                if (paths[i].equals(newPath)) {
                    out.println("The classpath already contains ["+newPath+"]");
                    found = true;
                }
            }
            
            if (!found) {
                node.setAttribute(NODE_CP_ATTR, cp+":"+newPath);
                result = true;
            }

        } else if (args[0].equals("del")) {
            
            if (args.length != 2) {
                throw new NodeConfigException("cp del needs an extra argument");
            }
            String path = args[1];
            Element node = Utils.getElement(config.getDocumentElement(),
                                            NODE_TAG, null, null);
            String cp = node.getAttribute(NODE_CP_ATTR);
            String[] paths = cp.split(":");
            int index = -1;

            for (int i=0; (index == -1) && (i<paths.length); i++) {
                if (paths[i].equals(path)) {
                    index = i;
                }
            }
            
            if (index == -1) {
                out.println("The path element ["+path+"] has not been found in the classpath");
            } else {
                StringBuffer sb = new StringBuffer();
                boolean first = true;

                for (int i=0; i<paths.length; i++) {
                    if (i != index) {
                        if (first) {
                            first = false;
                        } else {
                            sb.append(":");
                        }
                        sb.append(paths[i]);
                    }
                }
                        
                node.setAttribute(NODE_CP_ATTR, sb.toString());
                result = true;
            }
            
        } else { 
            throw new NodeConfigException("Operation ["+args[0]+"] is unknown");
        }

        return(result);
    }

    public void usage(PrintStream out) {
        out.println("cp\n"+
                    "\tprint : print the current classpath\n"+
                    "\tadd <path> : add a path in the classpath list\n"+
                    "\tdel <path> : remove a path from the classpath");
    }
}

