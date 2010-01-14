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

 

package com.sun.honeycomb.hctest.cases.storepatterns;
import com.sun.honeycomb.hctest.util.*;


import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;


/**
  Settings and defaults live here. See
  StorePattern help for details.

  //
  //  FIXME: verify timeout value for below
  // 
  timeout - internal timeout value - if a thread doesn't return from a store, this is aborted 

*/

public class Settings {
    public static final String DATAVIPS = "datavips";

    public static final String MIN_SIZE_PARAMETER = "minsize";
    public static final long MIN_SIZE_PARAMETER_DEFAULT = 10;

    public static final String MAX_SIZE_PARAMETER = "maxsize";
    public static final long MAX_SIZE_PARAMETER_DEFAULT = 107374182400L; //100 gigabyte

    public static final String TOTAL_DATA_PARAMETER = "totaldata";
    public static final long TOTAL_DATA_PARAMETER_DEFAULT = -1; //infinite

    public static final String NUM_PENDING_PROCESSES = "processes";
    public static final int NUM_PENDING_PROCESSES_DEFAULT = 1;

    public static final String RUN_MILLISECONDS = "time";
    public static final int RUN_MILLISECONDS_DEFAULT = 30000;

    public static final String RETRYCOUNT = "retrycount";
    public static final int RETRYCOUNT_DEFAULT = 50;

    public static final String TIMEOUT_MS = "timeout";
    public static final int TIMEOUT_MS_DEFAULT = 0;

    public static final String NUMFILES = "numfiles";
    public static final int NUMFILES_DEFAULT = -1;


    public static final String BYTEPATTERN = "bytepattern";
    public static final String BYTEPATTERNDEFAULT="de ad be ef";
    
    public static final String FACTORY_NAME = "factory";
    public static final String FACTORY_NAME_DEFAULT="ContinuousMixFactory";
    
    public static final String MIX_NAMES = "mixes";
    public static final String MIX_NAMES_DEFAULT = "mix1";
        
    public static final String OPERATIONS = "operations";
    public static final String OPERATIONS_DEFAULT = "50%StoreOpGenerator,50%FetchOpGenerator";
    
    public static final String FRESHINTERVAL = "freshinterval";
    public static final long FRESHINTERVAL_DEFAULT = 300; // 5 minutes in seconds!
    
    public static final String MAXFETCHROWCOUNT = "maxfetchcount";
    public static final long MAXFETCHROWCOUNT_DEFAULT = 25; 
    
    public static final String SELECTIONTYPE = "selectiontype";
    // Possible values are: roundrobin and random
    public static final String SELECTIONTYPE_DEFAULT = "roundrobin";

	public static final String RUN_ID = "runid";

	public static final String OID = "oid";

    static long getSize(String param, long paramDefault) {
        String s = TestRunner.getProperty(param);
        if(s==null) {
            return paramDefault;
        } else {
            return HCUtil.parseSize(s);

        }
    }

    static long getTime(String param, long paramDefault) {
        String s = TestRunner.getProperty(param);
        if(s==null) {
            return paramDefault;
        } else {
            return HCUtil.parseTime(s);

        }
    }

    static int getValue(String param, int paramDefault) {
        String s = TestRunner.getProperty(param);
        if(s==null) {
            return paramDefault;
        } else {           
            return Integer.parseInt(s);

        }

    }


    static String getString(String param, String paramDefault) {
        String s = TestRunner.getProperty(param);
        if(s==null) {
            return paramDefault;
        } else {           
            return s;

        }

    }
    




}
