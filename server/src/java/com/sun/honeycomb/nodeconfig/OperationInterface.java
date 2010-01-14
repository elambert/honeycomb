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

public interface OperationInterface {

    static final String JVM_TAG = "jvm";
    static final String JVM_NAME_ATTR = "name";
    static final String JVM_RSS_ATTR = "rss";
    static final String JVM_PARAMS_ATTR = "params";

    static final String SVC_TAG = "service";
    static final String SVC_NAME_ATTR = "name";
    static final String SVC_CLASS_ATTR = "class";
    static final String SVC_SHUTDOWN_TIMEOUT = "shutdownTimeout";

    static final String GRP_TAG = "group";
    static final String GRP_LOCATION_ATTR = "location";
    static final String GRP_LEVEL_ATTR = "runlevel";
    static final String GRP_MASTER_LOCATION = "master-node";
    
    static final String NODE_TAG = "node";
    static final String NODE_CP_ATTR = "classpath";

    // Returns true if the document needs to be rewritten

    boolean execute(PrintStream out,
                    Document config,
                    String[] args)
        throws NodeConfigException;

    void usage(PrintStream out);

}
