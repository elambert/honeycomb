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



package com.sun.honeycomb.time;

public class NTPServer implements java.io.Serializable {
    /** Server IP address or hostname */
    private String ipAddr; 
    /** Is Server synced */ 
    private boolean isSync;
    /** Is Server synced to its local hardware clock */ 
    private boolean isRemoteSync;
    /** Is Server running NTP daemon */
    private boolean isRunning;
    /** Is server trusted */
    private boolean isTrusted;

    public NTPServer(String ipAddr) {
        this.ipAddr = ipAddr;
        this.isSync = true;
        this.isRemoteSync = true;
        this.isRunning = true;
        this.isTrusted = true;
    }
       
    public boolean getRemoteSyncStatus() { return isRemoteSync; }  
    public boolean getSyncStatus() { return isSync; }  
    public boolean getRunningStatus() { return isRunning; }  
    public boolean getTrustedStatus() { return isTrusted; }  
    public String getHost() { return ipAddr; }
      
    public void setRemoteSyncStatus(boolean s) { this.isRemoteSync = s; }
    public void setSyncStatus(boolean s) { this.isSync = s; }
    public void setRunningStatus(boolean s) { this.isRunning = s; }
    public void setTrustedStatus(boolean s) { this.isTrusted = s; }
}  
