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



package com.sun.honeycomb.admin;

import com.sun.honeycomb.cm.ManagedService;
import com.sun.honeycomb.cm.ManagedServiceException;

/**
 * Public definition of the Profiler Interface.
 * FIXME: this interface should not be defined here but with the current
 * framework, it is not possible to add a CLI command that is implemented 
 * by a different package.
 * In this specific case, the profiler is implemented in the performance
 * package but the CLI command (through the admin server) is implemented
 * here.
 */
public interface ProfilerIntf 
    extends ManagedService.RemoteInvocation, ManagedService {

        static final String PROFILER_INSTALL_PATH= "/opt/honeycomb/etc/profiler/";
        static final String PROFILER_DTRACE_PATH = PROFILER_INSTALL_PATH + "dtrace/";
        static final String PROFILER_PARSER_PATH = PROFILER_INSTALL_PATH + "parsers/";

        static final String MODULE_PREFIX = "hcprofile_";
        static final String PARSER_PREFIX = "hcparser_";
        static final String RESULT_TARFILE = "perfresults";
        
        static final String VERSION_FILE = "/version";
        static final String PROFILE_RESULT_DIR = "/profile/";
        static final String PROFILE_RESULT_TARDIR = PROFILE_RESULT_DIR + RESULT_TARFILE;

        
        void startProfiling(String module, int howlong) throws ManagedServiceException;
        
        String stopProfiling() throws ManagedServiceException;

        /*
         * the profiler service exports the following proxy object.
         */
        public class Proxy extends ManagedService.ProxyObject {

            private String curProfileModule;
            
            public Proxy(String module) {
                curProfileModule = module;
            }
            public String getPrfModule() {
                return curProfileModule;
            }
            public String toString() {
                return super.toString();
            }
        }
}
