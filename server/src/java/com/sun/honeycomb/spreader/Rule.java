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



package com.sun.honeycomb.spreader;

/**
 * One forwarding rule in the switch. Just a collection of data with
 * accessors and utils.
 *
 * @author Shamim Mohamed
 * @version $Id: Rule.java 10855 2007-05-19 02:54:08Z bberndt $
 */
public class Rule implements Comparable {
    private int JUNK = 0xdeadbeef; // arbitrary

    private String vIP;

    private int srcAddr;
    private int srcAddrMask;
    private String literalSrc = null;

    private int ipPort;
    private int portMask;

    private int switchPort = JUNK;

    public static final int BY_PROTO = 0;
    public static final int BY_PORT = 1;

    // Depending on ruleType either proto or port is set
    private int ruleType;

    private String proto;
    private int destPort;

    Rule(String vIP, int switchPort) {
        this(vIP, -1, 0, 0, 0, 0, switchPort);
    }

    // ndmp
    Rule(String vIP, String proto, int switchPort) {
        this(vIP, proto, 0, 0, 0, 0, switchPort);
    }

    
    Rule(String vIP, int destPort, int switchPort) {
        this(vIP, destPort, 0, 0, 0, 0, switchPort);
    }

    // ntp rule + ndmp inbound traffic
    Rule(String vIP, int srcIpPort, String proto, int switchPort) {
        this(vIP, proto, 0, 0, srcIpPort, 0, switchPort); 
    }

    Rule(String vIP, String src, int srcIpPort, int switchPort) {
	this(vIP, 0, src, srcIpPort, 0, switchPort);
    }

    Rule(String vIP, int destPort, String host, int srcIpPort, int portMask,
         int switchPort) {
        this(vIP, destPort, 0, 0, srcIpPort, portMask, switchPort);
        // This is for literal src value (e.g. "10.1.254.12" or "192.168.0.0/16"
        literalSrc = host;
    }

    Rule(String vIP, String proto, int srcAddr, int srcAddrMask,
         int ipPort, int portMask,
         int switchPort) {
        this.ruleType = BY_PROTO;
        this.proto = proto;

        init(vIP, srcAddr, srcAddrMask, ipPort, portMask, switchPort);
    }

    Rule(String vIP, int destPort, int srcAddr, int srcAddrMask,
         int ipPort, int portMask,
         int switchPort) {
        this.ruleType = BY_PORT;
        this.destPort = destPort;

        init(vIP, srcAddr, srcAddrMask, ipPort, portMask, switchPort);
    }

    /**
     * Prints the string representation of the rule in a form
     * acceptable to the external program "zrule"
     *
     * @return string representing the rule: options for zrule
     */
    public String toString() {

        String rule = "";
        if (vIP != null)
            rule = "-dest " + vIP;

        if (literalSrc != null)
            rule += " -src " + literalSrc;
        else if (srcAddrMask > 0)
            rule += " -src " + srcAddr + "!" + srcAddrMask;

        // both srcport and portMask are specified
        if(portMask > 0)
            rule += " -srcport " + ipPort + "!" + portMask;
        else if(ipPort > 0) 
            rule += " -srcport " + ipPort;
             
        String type = "";
        switch (ruleType) {
        case BY_PROTO:
            if (proto.length() > 0)
                type = " -t " + proto;
            break;
        case BY_PORT:
            if (destPort > 0)
                type = " -destport " + destPort;
            break;
        default:
            throw new RuntimeException("ruleType " + ruleType + "invalid");
        }

        rule += type;

        switch(switchPort) {
        case ZNetlink2Message.NULLPORT:
            rule += " -X";
            break;

        case ZNetlink2Message.ACCEPT:
            rule += " -A";
            break;

        default:
            rule += " -s " + switchPort;
            break;
        }

        if (destPort > 0 || portMask > 0)
            // We don't do UDP
            rule += " -t tcp";

        return rule.trim();
    }

    public boolean equals(Object r) {
        return (r != null) && (this.compareTo(r) == 0);
    }

    public int compareTo(Object o) {
        return toString().compareTo(o.toString());
    }

    public String getVIP() {
        return vIP;
    }

    public int getSwitchPort() {
        return switchPort;
    }

    public int getSrcAddr() {
        return srcAddr;
    }

    public int getSrcAddrMask() {
        return srcAddrMask;
    }

    public int getSrcIpPort() {
        return ipPort;
    }

    public int getPortMask() {
        return portMask;
    }

    public String getProto() {
        if (ruleType == BY_PROTO)
            return proto;

        throw new RuntimeException("No protocol for this rule");
    }

    public int getDestPort() {
        if (ruleType == BY_PORT)
            return destPort;

        throw new RuntimeException("No detsination port for this rule");
    }    

    public int getRuleType() {
        return ruleType;
    }

    public String getLiteralSrc() {
        return literalSrc;
    }

    /**
     * Common initializer used by all constructors
     */
    private void init(String vIP, int srcAddr, int srcAddrMask,
                      int ipPort, int portMask, int switchPort) {
        if ((vIP != null && vIP.length() == 0) || switchPort == JUNK)
            throw new RuntimeException("Rule (dest \"" + vIP +
                                       "\" -> " + switchPort + ") invalid");
        this.vIP = vIP;
        this.srcAddr = srcAddr;
        this.srcAddrMask = srcAddrMask;
        this.ipPort = ipPort;
        this.portMask = portMask;
        this.switchPort = switchPort;

    }

}
