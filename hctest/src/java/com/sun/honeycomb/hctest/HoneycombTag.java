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



package com.sun.honeycomb.hctest;

import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.util.NameValue;
import java.util.Properties;

public class HoneycombTag extends HoneycombTestConstants {
    // for help text
    public NameValue[] getPropUsage() { return propHelp; }

    /**
     * Honeycomb specific tags go here (and not in Tag.java).
     */
    public static final String DATAOPNOFAULT = "data-op-no-fault";
    public static final String STOREDATA = "store";
    public static final String RETRIEVEDATA = "retrieve";
    public static final String DELETEDATA = "delete";
    public static final String STOREMETADATA = "store-metadata";
    public static final String RETRIEVEMETADATA = "retrieve-metadata";
    public static final String DELETEMETADATA = "delete-metadata";
    public static final String DELETE = "delete";
    public static final String QUERY = "query";

    public static final String JAVA_API = "java-op";

    public static final String INTERNAL_NFS = "nfs_internal";

    /* Tags for CLI tests */
    public static final String CLI = "cli";       
    
    /* Tags for multicell tests */
    public static final String MULTICELL = "multicell";       
    
    /* Tags for webdav tests */
    public static final String WEBDAV = "webdav";

    /* Tags for CM tests */
    public static final String CMM = "cmm";       
    public static final String SVC_MGMT = "svc-mgmt";
    public static final String CMM_ONLY = "cmm-only";
    public static final String CMM_ONLY_WITH_SNIFFER = "cmm-only-with-sniffer";
    public static final String NODE_MGR = "node-mgr"; 
    public static final String FULL_HC = "full-hc";
    public static final String CONFIG = "config";
    public static final String POSITIVE_CONFIG = "PositiveTest";
    

    /* Tags for C API tests */    
    public static final String C_API = "capi";
    public static final String JAVA2C = "java2c";
    public static final String CAPI_OA = "capi_oa";
    public static final String CAPI_NVOA = "capi_nvoa";
    public static final String CAPI_OAEZ = "capi_oaez";    
    public static final String CAPI_NVOAEZ = "capi_nvoaez";
    public static final String CAPI_MEM = "capi_mem";
    public static final String CAPI_PERF = "capi_perf";

    /* Tags for data doctor tests */
    public static final String DATA_DOCTOR = "datadoctor";
    public static final String DD_FRAGMENT_HEAL = "fragment-heal";
    public static final String SYS_CACHE = "syscache";
    public static final String EXT_CACHE = "extcache";
    public static final String HEALING = "healing";

    /* Platform tags */
    public static final String P_DEFAULT = "p_default";
    public static final String P_WINXP = "p_winxp";
    public static final String P_SOL64 = "p_solx64";
    public static final String P_SPARC = "p_sparc";
    public static final String P_LINUX = "p_linux";

    /* Performance tags */
    public static final String PERF_BASIC = "perf_basic";

    /* Emulator */
    public static final String EMULATOR = "emulator";
    
    /* Switch tags */
    public static final String SWITCH = "switch";

    /* Compliance tags */
    public static final String COMPLIANCE = "compliance";

    NameValue[] propHelp = {
        new NameValue(DELETE, "run the delete-related tests"),
        new NameValue(INTERNAL_NFS, "Tests for the internal NFS layer, do not use HC client API"),

        /* Tag help for CM tests */
        new NameValue(CMM_ONLY, "Run Honeycomb in CMM-only configuration"),
        new NameValue(NODE_MGR, "Run Honeycomb in NodeMgr configuration with test services"),
        new NameValue(FULL_HC, "Run Honeycomb in normal cluster configuration (all HC services). " +
                      "This tag is used only by CM tests, other tests run against normal cluster by default."),
        new NameValue(CMM, "Run tests that exercise CMM functionality"),
        new NameValue(SVC_MGMT, "Run tests that exercise Service Management functionality (NodeMgr)"),

        /* Tag help for C API tests */
        new NameValue(C_API, "Run all tests that use C API from the client"),
        new NameValue(JAVA2C, "Run normal Java regression tests via C API"),
        new NameValue(CAPI_OA, "Run tests that use OA flavor of C API; other flavors are NVOA, OAEZ, NVOAEZ"),
        new NameValue(CAPI_MEM, "Run memory profiling tests on C API"),
        new NameValue(CAPI_PERF, "Run performance tests on C API"),

        /* Help for platform tags */
        new NameValue(P_DEFAULT, "Run API tests from default client platform (ie platform doesn't matter)"),
        new NameValue(P_LINUX, "Run API tests from officially supported Linux platform"),
        new NameValue(P_WINXP, "Run API tests from officially supported Windows XP platform"),
        new NameValue(P_SOL64, "Run API tests from officially supported Solaris AMD x64 platform"),
        new NameValue(P_SPARC, "Run API tests from officially supported Solaris Sparc platform"),

        /* Help for performance tags */
        new NameValue(PERF_BASIC, "Basic performance regression test"),

        /* Help for emulator tags */
        new NameValue(EMULATOR, "Basic emulator-compatible tests"),
        
        /* Tags for CLI tests */
        new NameValue(CLI, "Run CLI tests"),

        /* Tags for multicell tests */
        new NameValue(MULTICELL, "Run multicell tests"),

        /* Tags for webdav tests */
        new NameValue(WEBDAV, "Run webdav tests")
    };
}
