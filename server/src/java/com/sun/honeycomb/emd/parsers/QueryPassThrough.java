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



/**
 * This class describes all text (such as SQL syntax) that is 
 * "passed through" unchanged from the Honeycomb query to the 
 * HADB query.
 *
 * NOTE: pass through text should only include character from
 * the Latin-1 character set.  We actually only use US-ASCII.
 */

package com.sun.honeycomb.emd.parsers;

import com.sun.honeycomb.common.*;

import java.util.*;
import java.io.*;
import com.sun.honeycomb.emd.cache.CacheManager;
import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.EMDConfigException;
import java.util.logging.Level;

public class QueryPassThrough
    extends QueryNode {
    
    private String attribute;

    public QueryPassThrough(String newPassThrough) {
        super(TYPE_PASS_THROUGH, null, null);
        attribute = newPassThrough;

    }

    public String toSQLString() 
        throws NoSuchElementException {
        
        StringBuffer result = new StringBuffer();

        result.append(attribute);
        
        return(result.toString());
    }

    public String toString() {

        return(attribute);
    }

    public String getAttribute() { return attribute;}
}
