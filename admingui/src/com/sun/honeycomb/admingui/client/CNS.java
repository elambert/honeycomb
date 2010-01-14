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



package com.sun.honeycomb.admingui.client;

/**
 * encapsulates CNS (online) registration information
 */
public class CNS {
    
    // registration info
    private String account, contactName, contactEmail;
    private boolean okToContact;
    private boolean authenticateProxy;
    
    // proxy info
    private String proxy, proxyUser;
    private int proxyPort;

    public CNS(String account) {
        this.account = account;
        this.okToContact = false;
        this.authenticateProxy = false;
        contactName = contactEmail = proxy = proxyUser = "";
        proxyPort = -1;
    }

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    
    public boolean getOkToContact() { return okToContact; }
    public void setOkToContact(boolean ok) { this.okToContact = ok; }
    
    public boolean getUseProxyAuth() { return authenticateProxy; }
    public void setUseProxyAuth(boolean auth) { this.authenticateProxy = auth; }
    
    public String getContactName() { return contactName; }
    public void setContactName(String name) { this.contactName = name; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String email) { this.contactEmail = email; }

    // proxy-related methods
    
    public String getProxy() { return proxy; }
    public void setProxy(String proxy) { this.proxy = proxy; }
    
    public int getProxyPort() { return proxyPort; }
    public void setProxyPort(int proxyPort) { this.proxyPort = proxyPort; }

    public String getProxyUser() { return proxyUser; }
    public void setProxyUser(String proxyUser) { this.proxyUser = proxyUser; }
    
    public String toString() {
        return "CNS[" + account + "," + contactName + "," +
                contactEmail + "," + proxy + ":" + proxyPort + "," +
                proxyUser + "]";
    }
    
    
}
