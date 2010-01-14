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

import java.io.ByteArrayOutputStream;
import java.util.Enumeration;
import java.util.Properties;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 *
 * @author jk142663
 */

public class XmlConvertor {
    
    /**
     * Use Xalan to apply stylesheet to the XML data 
     * no arguments to XSL 
     */
    public static String transform(
        StreamSource xmlSource, 
        StreamSource xslSource) throws TransformerException {
        
        return (transform(xmlSource, xslSource, null /* no args to xsl */));       
    }
    
    /**
     * Use Xalan to apply stylesheet to the XML data 
     * pass input arguments to stylesheet via Properties 
     */
    public static String transform(
        StreamSource xmlSource, 
        StreamSource xslSource,
        Properties xslParams) throws TransformerException {
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StreamResult streamResult = new StreamResult(out);
        TransformerFactory tfactory = TransformerFactory.newInstance();
        Transformer transformer = tfactory.newTransformer(xslSource);
        
        // passing parameters to XSLT, the param has to be declared in the XSLT 
        // arguments to the XSLT are via properties
        if (xslParams != null) {
            for (Enumeration e = xslParams.propertyNames();
                e.hasMoreElements(); ) {
                
                String key = (String)e.nextElement();
                transformer.setParameter(key, xslParams.getProperty(key));
            }
        }
        transformer.transform(xmlSource, streamResult);
             
        return new String(out.toByteArray()).trim();
    }
}

