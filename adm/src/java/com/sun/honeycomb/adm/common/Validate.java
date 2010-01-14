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


package com.sun.honeycomb.adm.common;

import java.net.Inet4Address;
import java.util.HashMap;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Common validation routines that can be used by the CLI and GUI
 */
public class Validate
{
    HashMap netmaskMap = null;
    
    private static final String NUM_255 = "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
    private static final String NUM_000 = "(?:0|00|000?)";
    public static final Pattern IP_ADDRESS_PATTERN =
	Pattern.compile( "^(?:" + NUM_255 + "\\.){3}" + NUM_255 + "$");
    public static final Pattern IP_ADDRESS_PATTERN_EMULATED =
        Pattern.compile( "^(?:" + NUM_255 + "\\.){3}" + NUM_255 +
                         "(:([0-9][0-9][0-9][0-9])?)$");

    private static final Pattern IP_LOOSE_FORM_PATTERN = 
            Pattern.compile( "^([0-9]+\\.){2,}+[0-9]*+$");
    
    private static final Pattern IP_FORM_PATTERN = 
            Pattern.compile( "^([0-9]+\\.){3}+[0-9]+$");
    
    // Base regexp pattern for domains taken from
    // O'Reilly's Mastering Regular Expressions, 3rd Edition
    //
    // Domain names are restricted to the ASCII letters "a" through "z" 
    // (case-insensitive), the digits "0" through "9", and the hyphen, 
    // with some other restrictions.  
    private static final String SUB_DOMAIN = "(?i:[a-z0-9]|[a-z0-9][-a-z0-9]{0,61}[a-z0-9])";
    private static final String TOP_LEVEL_DOMAINS = 
	   // "(\\.(com|edu|biz|in(?:t|fo)|mil|net|org|[a-z][a-z]))?";
	    "(?x-i:com|edu|gov|int|mil|net|org|biz|info|name|museum|coop|aero|[a-z][a-z])";
    
    private static final String DOMAIN = "(?:" + SUB_DOMAIN + "\\.)+" + TOP_LEVEL_DOMAINS;
    
    
    private static final Pattern DOMAIN_NAME_PATTERN = 
	Pattern.compile(DOMAIN);
    
    // Hostname pattern is like the domain name pattern but less restrictive
    // To allow for names like "hc-dev" and "hc-dev.sfbay"
    private static final String HOSTNAME =
	SUB_DOMAIN + "|" 
	+ DOMAIN + "|"
	+ "(?:" + SUB_DOMAIN + "\\.)+" + SUB_DOMAIN;
    private static final Pattern HOSTNAME_PATTERN = 
	Pattern.compile(HOSTNAME);
    
    public static final Pattern RFC_822_EMAIL_VALIDATION_PATTERN =
	Pattern.compile("^([0-9a-zA-Z]+[-._+&])*[0-9a-zA-Z]+@([-0-9a-zA-Z]+[.])+[a-zA-Z]{2,6}$");
    
    /**
     * Top level assembly part number pattern.  This pattern enforces 547-XXXX-XX
     * where X is any value 0-9
     */
    public static final Pattern TOPLEVEL_ASSEMBLY_PART_NUM_PATTERN =
            Pattern.compile("^594-[0-9][0-9][0-9][0-9]-[0-9][0-9]$");
    
    /**
     * Serial # pattern.  This pattern enforces the pattern of YYWWMMUUUU
     * where 
     * <ul>
     * <li>YY = Year of manufacture</li>
     * <li>WW = Week of manufacture, 01-52</li>
     * <li>MM = Manufactring plant code where M may be any value A-Z.
     * <li>UUUU = Unique manufacturing # where U may be any value 0-9</li>
     * </ul>
     */
    public static final Pattern TOPLEVEL_ASSEMBLY_SERIAL_NUM_PATTERN =
	Pattern.compile("^[0-9][0-9]([0][1-9]|[1-4][0-9]|5[0-2])[A-Z][A-Z][0-9][0-9][0-9][0-9]$");
    
    /**
     * Marketing number pattern.  This pattern only enforces the pattern of
     * XX-XXXX-XXXXXXX where X can be A-Z or 0-9
     */
    public static final Pattern MARKETING_NUMBER_PATTERN =
	Pattern.compile("^[A-Z0-9][A-Z0-9]-[A-Z0-9][A-Z0-9][A-Z0-9][A-Z0-9]-[A-Z0-9][A-Z0-9][A-Z0-9][A-Z0-9][A-Z0-9][A-Z0-9][A-Z0-9]$");
    
    
    
    private static int NETMASKS[] = new int [] {
	 0x80000000,	// 128.0.0.0 for CIDR mask /1
	 0xc0000000,	// 192.0.0.0 for CIDR mask /2
	 0xe0000000,	// 224.0.0.0 for CIDR mask /3
	 0xf0000000,    // 240.0.0.0 for CIDR mask /4
	 0xf8000000,	// 248.0.0.0 for CIDR mask /5
	 0xfc000000,	// 252.0.0.0 for CIDR mask /6
	 0xfe000000,	// 254.0.0.0 for CIDR mask /7
	 0xff000000,	// 255.0.0.0 for CIDR mask /8
	 0xff800000,	// 255.128.0.0 for CIDR mask /9
	 0xffc00000,	// 255.192.0.0 for CIDR mask /10
	 0xffe00000,	// 255.224.0.0 for CIDR mask /11
	 0xfff00000,	// 255.240.0.0 for CIDR mask /12
	 0xfff80000,	// 255.248.0.0 for CIDR mask /13
	 0xfffc0000,	// 255.248.0.0 for CIDR mask /14
	 0xfffe0000,	// 255.254.0.0 for CIDR masks /15
	 0xffff0000,	// 255.255.0.0 for CIDR mask /16
	 0xffff8000,	// 255.255.128.0 for CIDR mask /17
	 0xffffc000,	// 255.255.192.0 for CIDR mask /18
	 0xffffe000,	// 255.255.224.0 for CIDR mask /19
	 0xfffff000,	// 255.255.240.0 for CIDR mask /20
	 0xfffff800,	// 255.255.248.0 for CIDR mask /21
	 0xfffffc00,	// 255.255.252.0 for CIDR mask /22
	 0xfffffe00,	// 255.255.254.0 for CIDR mask /23
	 0xffffff00,	// 255.255.255.0 for CIDR mask /24
	 0xffffff80,	// 255.255.255.128 for CIDR mask /25
	 0xffffffc0,	// 255.255.255.192 for CIDR mask /26
	 0xffffffe0,	// 255.255.255.224 for CIDR mask /27
	 0xfffffff0,	// 255.255.255.240 for CIDR mask /28
	 0xfffffff8,	// 255.255.255.248 for CIDR mask /29
	 0xfffffffc,	// 255.255.255.252 for CIDR mask /30
	 0xfffffffe,	// 255.255.255.254 for CIDR mask /31
	 0xffffffff,	// 255.255.255.255 for CIDR mask /32
    };
     
    
    private Validate() {}
    
    /**
     * Determine whether the specified domain name is valid. 
     *
     * @param domainName the domain name to validate
     * @return boolean true if domain name is valid false otherwise
     */
    public static boolean isValidDomainName(String domainName) {
	
	// Domain Names can not exceed 255 characters
	if (domainName.length() > 255)
	    return false;
	Matcher m = DOMAIN_NAME_PATTERN.matcher(domainName);
	return m.matches();
    }
    
    /**
     * Perform some basic email address validation to ensure a
     * that the email address conforms to the RFC 822 specification 
     * ie user@company.xxx
     */
    public static boolean isValidEmailAddress(String emailAddress) {
	Matcher m = RFC_822_EMAIL_VALIDATION_PATTERN.matcher(emailAddress);
	return m.matches();
    }
    
    /**
     * Determine whether the specified hostname is valid.  This is a lose
     * check and does not check to ensure the hostname is pingable
     * or can be looked up.
     *
     * @param hostname the hostname to validate
     */
    public static boolean isValidHostname(String hostname) {
        
        Matcher m = IP_FORM_PATTERN.matcher(hostname);
	if (m.matches()) {
            return isValidIpAddress(hostname);
        }
	if (hostname.length() > 255)
	    return false;
	m = HOSTNAME_PATTERN.matcher(hostname);
	return m.matches();
    }
    
    /**
     * Determine whether the specified hostname is valid by trying to lookup
     * the hostname with the system. 
     *
     * @param hostname the hostname to validate
     * @return boolean true if lookup was successful, false otherwise
     */
    public static boolean isValidHostnameStick(String hostname) {
	return (Utils.getIpAddress(hostname) != null);
    }
    
    /**
     * Determine whether the specified ip address is valid.  This check
     * is limited to checking whether each of the numbers in the ip addresses
     * falls between 0-255.
     *
     * @param ipAddress the string that needs to be validated 
     * to check whether it's an ip address.
     * @return boolean true if specified value is a valid IP address.
     * false otherwise
     */
    public static boolean isValidIpAddress(String ipAddress)
    {
	Matcher m = IP_ADDRESS_PATTERN.matcher(ipAddress);
	return m.matches();
    }
    
    /**
     * Determine whether the specified netmask is valid.
     *
     * @param ipAddress the string that needs to be validated 
     * to check whether it's an ip address.
     * @return boolean true if specified value is a valid IP address.
     * false otherwise
     * @return int, 0 indicates it's valid.  Any other value represents
     * and invalid netmask.
     * <P>
     * Invalid Netmask Codes:<BR>
     * <UL>
     * <LI>1 - Missing mask</LI>
     * <LI>2 - Mask is invalid (IP Portion of netmask is invalid)</LI>
     * <LI>3 - Invalid mask bit specified.  Not a number</LI>
     * <LI>4 - Mask bit out of range.  Must be between 1 and 32.</LI>
     * <LI>5 - Invalid IP/mask combination.</LI>
     * </UL>
     */
    public static int validNetmask(String netmask)
    {
	int index = netmask.indexOf('/');
	if (index == -1 || index == netmask.length())
	    return 1;
	String mask = netmask.substring(index + 1);
	String ip = netmask.substring(0, index);
	if (ip == null || isValidIpAddress(ip) == false) {
	    return 2;
	}
	int iMask = 0;
	try {
	    iMask = Integer.valueOf(mask).intValue();
	}
	catch (NumberFormatException nfe) {
	    return 3;
	}
	if (iMask < 1 || iMask > 32) {
	    return 4;
	}
	
	String hexIp = getIpAsHexStr(ip);
	
	//
	// We have to read the hex value as a Long since  
	// since using Integer.valueOf("0xffffff00", 16)
	// will result in a NumberFormatException
	long ipValue = Long.valueOf(hexIp, 16).longValue();
	
	int netMaskValue = NETMASKS[iMask-1];
	long n = (netMaskValue & ipValue);
	return n == ipValue ? 0 : 5;
    }
    
    /**
     * Convert the IP address to a hex value
     * @param ip the ip address to convert
     * @return String the hex representation of the IP address
     */
    private static String getIpAsHexStr(String ip) {
	String[] ipAddr = ip.split("\\.");
	int[] octets = new int[ipAddr.length];
	StringBuffer hexStr = new StringBuffer();
	for (int i=0; i < ipAddr.length; i++) {
	    int value = Integer.valueOf(ipAddr[i]).intValue();
	    hexStr.append(toHex(value));
	}
	return hexStr.toString();
    }
    
    private static String toHex(int value) {
	StringBuffer buf = new StringBuffer(Integer.toHexString(value));
	if (buf.length() != 2)
	    buf.insert(0, "0");
	return buf.toString();
    }

    /**
     * Validate that this is a top level serial # for the Sun StorageTek 5800 System
     * A valid serial number must be 10 characters.  The format of a serial number
     * is YYWWMMUUUU
     * where 
     * <ul>
     * <li>YY = Year of manufacture</li>
     * <li>WW = Week of manufacture</li>
     * <li>MM = Manufactring plant code where M may be any value A-Z.  
     * Currently the code used is MM but this may change to FF when
     * manufacturing moves to Flextronics.</li>
     * <li>UUUU = Unique manufacturing # where U may be any value 0-9</li>
     * </ul>
     * 
     * @return boolean true if serial number is valid, false otherwise
     */
    public static boolean isValidTopLevelAssemblySerialNumber(String serialNumber) {
        Matcher matcher = TOPLEVEL_ASSEMBLY_SERIAL_NUM_PATTERN.matcher(serialNumber);
        return matcher.matches();
    }
    
    /**
     * Validate the Top Level Assembly Part Number
     * <P>
     * In Sun an assembly part number is always a decimal  number 594-4516-02 . 
     * The 594 is the class for X-options and storage arrays. 
     * Technically the only The only part of number that could change 
     * (on current systems) is the "-02 ".  This would be bumped up (-03) if 
     * there was a functional change to the hardware in the system.
     * <P>
     * Be a little less restrictive here to allow for
     * later changes.   Only enforce 594 value and that 3-4-2 digit form is 
     * maintained.
     * @return boolean true if valid, false otherwise
     */
    public static boolean isValidTopLevelAssemblyPartNumber(String partNumber) {
        Matcher matcher = TOPLEVEL_ASSEMBLY_PART_NUM_PATTERN.matcher(partNumber);
        return matcher.matches();
    }
    
    /**
     * Validate the marketing number holds to the pattern of XX-XXXX-XXXXXXX
     * where X can be any value 0-9 or A-Z
     * @return boolean true if valid, false otherwise
     */
    public static boolean isValidMarketingNumber(String marketingNumber) {
        Matcher matcher = MARKETING_NUMBER_PATTERN.matcher(marketingNumber);
        return matcher.matches();
    }
    
    
}
