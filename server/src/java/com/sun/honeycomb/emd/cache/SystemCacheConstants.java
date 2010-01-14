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



package com.sun.honeycomb.emd.cache;

public interface SystemCacheConstants {

    /*
     * The getObjects query take 1 argument, the layoutMapId to query
     * against.
     */
    String SYSTEM_QUERY_GETOBJECTS      = "getObjects";
    String SYSTEM_QUERY_GETMDSTHATPOINT = "getMDsThatPoint";
  
    /*
     * takes 1 argument which is the OID to check for existence in the system cache, 
     * if an attributes object is passed on the query then the system record will be 
     * returned.
     */
    String SYSTEM_QUERY_CHECKOID        = "checkOID";
    
    /*
     * Used only by the system cache state machine: This query returns 
     * some of the objects that are currently not restored. Only some 
     * because of an optimization so that we don't waste time since we
     * did find one object still not restored.
     */
    String SYSTEM_QUERY_ISNOTRESTORED   = "isNotRestored";
    
    /* 
     * Used only by the sysstem cache state machine and it basically
     * flips all the objects in the system cache into the not restored
     * state.
     */
    String SYSTEM_QUERY_SETNOTRESTORED  = "setAllNotRestored";

    /* 
     * takes two long timestamp arguments that define the interval of time for which to 
     * return all objects with ctime or dtime in that interval
     */
    String SYSTEM_QUERY_GETCHANGES      = "getChanges";

    /*
     * Takes a legal hold string are queries for all oids which have that legal hold
     */
    String SYSTEM_QUERY_HOLD            = "queryHold";
}
