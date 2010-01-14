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



package com.sun.honeycomb.admingui;

/**
 * names used in the XML docs. must match those defined in the protocol schema
 */
public interface XmlRpcParamDefs {

    public static final String APIVER = "1.1";

    /* method calls. must match method names defined in MainHandler.java */
    public static final String GETAPIVER = "getAPIVersion";
    
    public static final String GETVERS = "getVersions";
    public static final String GETNOUFAILRS = "getNumOfUnhealedFailures";
    public static final String GETMAXNOAUTHCLIENTS = "getMaxNumAuthClients";
    public static final String HASQUORUM = "hasQuorum";
    public static final String LOGIN = "login";
    public static final String LOGGEDIN = "loggedIn";
    public static final String LOGOUT   = "logout";
    public static final String GETHADBSTATUS = "getHADBStatus";
    public static final String GETSWOK       = "getSwitchesOk";

    public static final String GETCELLPROPS  = "getCellProps";
    public static final String SETCELLPROPS  = "setCellProps";
    public static final String GETADMIP  = "getAdminIP";
    public static final String SETADMIP  = "setAdminIP";
    public static final String GETDATAIP = "getDataIP";
    public static final String SETDATAIP = "setDataIP";
    public static final String GETSPIP   = "getServiceNodeIP";
    public static final String SETSPIP   = "setServiceNodeIP";
    public static final String GETGTWYIP = "getGatewayIP";
    public static final String SETGTWYIP = "setGatewayIP";
    public static final String GETNETIP  = "getNetwork";
    public static final String SETNETIP  = "setNetwork";
    public static final String GETSUBNET = "getSubnet";
    public static final String SETSUBNET = "setSubnet";    
    public static final String GETSMTPIP = "getSMTPIP";
    public static final String SETSMTPIP = "setSMTPIP";
    public static final String GETNTPIPS = "getNTPIPs"; 
    public static final String SETNTPIPS = "setNTPIPs";  
    public static final String GETLOGIP  = "getLogIP";
    public static final String SETLOGIP  = "setLogIP";
    public static final String GETSMTPPORT = "getSMTPPort";
    public static final String SETSMTPPORT = "setSMTPPort";
    public static final String GETDATE = "getDate";
    public static final String GETLIC  = "getLicense";
    public static final String SETLIC  = "setLicense";

    public static final String ISDNSON = "isDnsEnabled";
    public static final String SETDNSON  = "enableDns";
    public static final String SETDNSOFF = "disableDns";  
    public static final String GETDOMAIN_NAME = "getDomainName";
    public static final String SETDOMAIN_NAME = "setDomainName";    
    public static final String GETDNS_SEARCH  = "getDnsSearch";
    public static final String SETDNS_SEARCH  = "setDnsSearch";
    public static final String GETPRIDNSIP = "getPrimaryDnsIP";
    public static final String SETPRIDNSIP = "setPrimaryDnsIP";
    public static final String GETSECDNSIP = "getSecondaryDnsIP";  
    public static final String SETSECDNSIP = "setSecondaryDnsIP";

    public static final String GETPSWD     = "getPasswd";
    public static final String SETPSWD     = "setPasswd";
    public static final String VERIFYPSWD  = "verifyPasswd";
    public static final String GETCELLS    = "getCells";
    public static final String GETNODE     = "getNode";
    public static final String GETNODES    = "getNodes";
    public static final String GETDISKS    = "getDisks";
    public static final String GETDISKSONNODE = "getDisksOnNode";
    public static final String GETSVCS     = "getServices";
    public static final String GETCLIENTS  = "getClients";
    public static final String SETCLIENTS  = "setClients";
    public static final String GETEMAILTO   = "getEmailTo";
    public static final String GETEMAILCC   = "getEmailCc";
    public static final String SETEMAILTO   = "setEmailTo";
    public static final String SETEMAILCC   = "setEmailCc";
    public static final String GETSENSORS  = "getSensors";
    public static final String GETPERFSTATS = "getPerfStats";
    public static final String GETSWITCH  = "getSwitch";
    public static final String GETSWITCHES  = "getSwitches";
    public static final String GETSP = "getSp";
    public static final String HASCACHEDCELLS = "hasCachedCells";
    public static final String GETCACHEDCELLS = "getCachedCells";
    public static final String GETNOCELLS = "getNumCells";

    public static final String POWERNODEON  = "powerNodeOn";
    public static final String POWERNODEOFF = "powerNodeOff";
    public static final String REBOOTNODE   = "rebootNode";
    public static final String REBOOTCELL   = "rebootCell";
    public static final String POWERCELLOFF = "powerCellOff";
    public static final String ENABLEDISK   = "enableDisk";
    public static final String DISABLEDISK  = "disableDisk";
    public static final String WIPEDISKS  = "wipeDisks";
    public static final String ADDCELL    = "addCell";
    public static final String DELCELL    = "delCell";

    public static final String GETMDCFG = "getMetadataCfg";
    public static final String SETMDCFG = "setMetadataCfg";

    public static final String GETCNS = "getCnsInfo";
    public static final String SETCNS = "setCnsInfo";
    public static final String DOCNSREG = "doCnsRegister";
    public static final String ISCNSREG = "isCnsRegistered";

            
    /* method results */

    public static final String ADMIP  = "admIP";
    public static final String DATAIP = "dataIP";
    public static final String SPIP   = "spIP";
    public static final String GTWYIP  = "gtwyIP";
    public static final String SUBNET = "subnet";

    public static final String ID        = "id";
    public static final String FREE      = "free";
    public static final String ALIVE     = "alive";
    public static final String DISKS     = "disks";
    public static final String DISKID    = "diskid";
    public static final String STATUS    = "status";
    public static final String CAPUSED   = "capUsed";
    public static final String CAPTOTAL  = "capTotal";
    public static final String ADDR      = "addr";
    public static final String SVCNAME   = "svcName";
    public static final String FRU       = "fru";
    public static final String VER       = "ver";
    public static final String NUM_NODES = "numNodes";
    public static final String NAME      = "name";
    public static final String TYPE      = "type";
    // sensor info
    public static final String DDR_V   = "ddrVoltage";
    public static final String CPU_V   = "cpuVoltage";
    public static final String MB3V_V  = "mb3vVoltage";
    public static final String MB5V_V  = "mb5vVoltage";
    public static final String MB12V_V = "mb12vVoltage";
    public static final String BAT_V   = "batVoltage";
    public static final String CPU_T   = "cpuTemp";
    public static final String SYS_T   = "sysTemp";
    public static final String CPU_F   = "cpuFanSpeed";
    public static final String SYSF1_F = "sysFan1Speed";
    public static final String SYSF2_F = "sysFan2Speed";
    public static final String SYSF3_F = "sysFan3Speed";
    public static final String SYSF4_F = "sysFan4Speed";
    public static final String SYSF5_F = "sysFan5Speed";
    // firmware info
    public static final String SPBIOS = "spBios";
    public static final String SPSMDS = "spSmdc";
    public static final String SW1OVRLAY = "sw1Overlay";
    public static final String SW2OVRLAY = "sw2Overlay";
    public static final String BIOS = "bios";
    public static final String SMDC = "smdc";

    // CNS info
    public static final String CNSACCT = "cnsAcct";
    public static final String PROXYUSR  = "proxyUsr";
    public static final String PROXYSRV  = "proxySrv";
    public static final String PROXYPORT = "proxyPort";
    public static final String USEPROXYAUTH = "useProxyAuth";
    
    // perfstats
    public static final String PFS_INTERV = "interval";
    public static final String PFS_STORE     = "storeOnly";
    public static final String PFS_STOREMD   = "storeMd";
    public static final String PFS_STOREBOTH = "storeBoth";
    public static final String PFS_RETRV     = "retrieve";
    public static final String PFS_RETRVMD   = "retrieveMd";
    public static final String PFS_QUERY     = "query";
    public static final String PFS_DEL       = "del";
    public static final String PFS_WDAVPUT   = "webdavPut";
    public static final String PFS_WDAVGET   = "webdavGet";
    public static final String PFS_LOAD1MIN  = "load1Min";
    public static final String PFS_LOAD5MIN  = "load5Min";
    public static final String PFS_LOAD15MIN = "load15Min";
    
    public static final String FAULT_NO_PERM     = "PermissionException";   
    public static final String FAULT_INVALID_SID = "Invalid session id: ";
            
}
