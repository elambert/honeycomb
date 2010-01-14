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

 
package com.sun.honeycomb.common;

public class TestRequestParameters 
{
    private static ThreadLocal logTag = new ThreadLocal();
    private static ThreadLocal last_logTag = new ThreadLocal();
    
    public static String PARAM_LOGTAG = "logtag";
    public static void setLogTag(String value)
    {
        logTag.set(value);
    }
    
    public static String getLogTag(){
    	if (logTag.get() == null)
            return null;
    	else
            return logTag.get().toString();
    }
    
    public static void setLastLogTag(String value)
    {
        last_logTag.set(value);
    }
    
    public static String getLastLogTag(){
    	if (last_logTag.get() == null)
            return null;
    	else
            return last_logTag.get().toString();
    }

    private static ThreadLocal layoutMapId = new ThreadLocal();
    public static String PARAM_LAYOUT_MAP_ID = "mapid";
    public static void setLayoutMapId(Integer value)
    {
        layoutMapId.set(value);
    }
    public static Integer getLayoutMapId() 
    {
    	if (layoutMapId.get() == null)
            return null;
    	else
            return (Integer) layoutMapId.get();
    }

    private static ThreadLocal calcHash = new ThreadLocal();
    public static boolean setCalcHash(boolean value)
    {
        boolean b = getCalcHash();
        calcHash.set(new Boolean(value));
        return b;
    }
    public static boolean getCalcHash()
    {
        Boolean b = (Boolean) calcHash.get();
        if (b == null)
            return true;
        return b.booleanValue();
    }

    private static ThreadLocal lastTime = new ThreadLocal();
    public static void setTime(long time) {
        lastTime.set(new Long(time));
    }
    public static long getTime() {
        Long l = (Long) lastTime.get();
        if (l == null)
            return -1;
        return l.longValue();
    }

    private static ThreadLocal lastHash = new ThreadLocal();
    public static void setHash(String hash) {
        lastHash.set(hash);
    }
    public static String getHash() {
        return (String) lastHash.get();
    }
}
