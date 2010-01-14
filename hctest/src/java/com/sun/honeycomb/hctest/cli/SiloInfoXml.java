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



package com.sun.honeycomb.hctest.cli;

import java.io.StringReader;
import java.util.Properties;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.TransformerException;

import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.hctest.util.*;

/**
 *
 * @author jk142663
 */
public class SiloInfoXml {
    
    // silo_info.xml file
    protected static final String SILO_INFO_XML_FILE = 
            "/config/silo_info.xml";
    
    // xsl stylesheet silo_info.xsl - required to parse silo_info.xml file
    private static final String SILO_INFO_XSL_FILE = 
            "/opt/test/etc/silo_info.xsl";
    
    private static final String SILO_INFO_ADMIN_IP = 
            "admin-vip";
    private static final String SILO_INFO_DATA_IP = 
            "data-vip";
    private static final String SILO_INFO_SP_IP = 
            "sp-vip";
    private static final String SILO_INFO_GATEWAY_IP = 
            "gateway";
    private static final String SILO_INFO_SUBNET_IP = 
            "subnet";
    
    private String siloInfofileStdout = null;
    private int cellId = -1;
    private String siloAdminIp, siloDataIp, siloSpIp, siloGateway, siloSubnet;
    
    /** Creates a new instance of SiloInfoXml */
    public SiloInfoXml(ClusterNode node, int cellId) 
                        throws HoneycombTestException {
        siloInfofileStdout = node.runCmd("cat " + SILO_INFO_XML_FILE); 
         
        this.cellId = cellId;
        
        // get admin ip
        this.siloAdminIp = getParamValue(SILO_INFO_ADMIN_IP);
        
        // get data ip
        this.siloDataIp = getParamValue(SILO_INFO_DATA_IP);
        
        // get sp ip
        this.siloSpIp = getParamValue(SILO_INFO_SP_IP);
        
        // get gateway
        this.siloGateway = getParamValue(SILO_INFO_GATEWAY_IP);
        
        // get subnet 
        this.siloSubnet = getParamValue(SILO_INFO_SUBNET_IP);    
    }
    
    private String getParamValue(String paramName) {          
        Properties prop = new Properties();
        
        prop.setProperty("cellid", new Integer(getSiloCellId()).toString());        
        prop.setProperty("paramname", paramName);      
        
        try {
            StringReader in = new StringReader(siloInfofileStdout);
            StreamSource xmlStreamSource = new StreamSource(in);
        
            StreamSource xslStreamSource = new StreamSource(SILO_INFO_XSL_FILE);
                
            return XmlConvertor.transform(xmlStreamSource, xslStreamSource, prop);
            
        } catch (TransformerException tfe) {
            Log.WARN("Unable to get value of " + paramName + " in " +
                    SILO_INFO_XML_FILE);
            return "";
        }
    }
    
    protected int getSiloCellId() {
        return this.cellId;
    }
    
    protected String getSiloAdminIp() {
        return this.siloAdminIp;
    }
    
    protected String getSiloDataIp() {
        return this.siloDataIp;
    }
    
    protected String getSiloSpIp() {
        return this.siloSpIp;
    }
    
    protected String getSiloGateway() {
        return this.siloGateway;
    }
    
    protected String getSiloSubnet() {
        return this.siloSubnet;
    }
}
