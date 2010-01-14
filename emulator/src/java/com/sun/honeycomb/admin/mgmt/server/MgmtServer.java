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



package com.sun.honeycomb.admin.mgmt.server;
import java.util.Date;

public class MgmtServer extends MgmtServerBase implements MgmtServerIntf {

    static private MgmtServer   mgmtServer;
    private long _sessionId;
    private Date _sessionTime;
    private static final long LOGIN_TIMEOUT=1000*60*5; // five minute timeout

    public MgmtServer() {
        super();
        mgmtServer = this;
    }

    static public MgmtServer getInstance() {
        return mgmtServer;
    }

    public String getName() {
        return "mgmtServer";
    }

    protected void executeInRunLoop() {
    }

    //
    // Login/logout support - logic lives is HCSiloAdapter.java
    // 
    public long getSessionId() {
        return _sessionId;
    }

    public void setSessionId(long sessionId) {
        _sessionId=sessionId;
    }

    public void updateSessionTime() {
        Date now=new Date();
        _sessionTime=now;
    }
    public void clearSessionTime() {
        _sessionTime=null;
    }
    public boolean isTimedOut() {
        Date now=new Date();
        if(_sessionTime==null)
            return true;
        if( (_sessionTime.getTime()+LOGIN_TIMEOUT) > now.getTime())
            return false;
        else 
            return true;
           
    }
}
