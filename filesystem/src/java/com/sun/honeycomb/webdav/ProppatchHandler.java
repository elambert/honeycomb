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



package com.sun.honeycomb.webdav;

import com.sun.honeycomb.fscache.HCFile;
import com.sun.honeycomb.fscache.FSCache;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ProtocolConstants;
import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.CanonicalEncoding;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Stack;
import java.util.Vector;
import java.util.Iterator;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.text.SimpleDateFormat;
import java.text.ParsePosition;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.mortbay.http.HttpException;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.util.URI;
import org.mortbay.util.UrlEncoded;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Create/modify compliance attributes (which are WebDAV properties).
 */
public class ProppatchHandler extends SpecificHandler {

    // Known attributes (in the HCFS DTD)
    private static final String ELT_EXPIRATION = "expiration";
    private static final String ELT_LEGALHOLD = "legalholds";
    private static final String ELT_TAG = "case";

    private static final Logger logger =
        Logger.getLogger(ProppatchHandler.class.getName());

    public ProppatchHandler() {
    }

    public void handle(HCFile file, String[] extraPath,
                       HttpRequest request, HttpResponse response,
                       InputStream inp, OutputStream outp)
            throws IOException, HttpException {
        Map properties;

        // Authentication has already been handled by Jetty.

        // Parse the XML and figure out the property name(s) and value(s)
        try {
            properties = getProperties(inp);
        }
        catch (SAXException e) {
            throw new HttpException(HttpResponse.__400_Bad_Request,
                                    e.toString());
        }
        catch (ParserConfigurationException e) {
            logger.log(Level.SEVERE, "Parsing request XML: ", e);
            throw new HttpException(HttpResponse.__500_Internal_Server_Error);
        }
        catch (InvalidBodyException e) {
            throw new HttpException(HttpResponse.__400_Bad_Request,
                                    e.toString());
        }

        for (Iterator i = properties.keySet().iterator(); i.hasNext(); ) {
            String propname = (String) i.next();
            Object value = properties.get(propname);

            if (value == null)
                logger.info("Erasing \"" + propname + "\"");
            else
                logger.info("Setting \"" + propname + "\"=" + value);
                
            if (propname.equals(ELT_EXPIRATION)) {
                if (value == null)
                    throw new HttpException(HttpResponse.__403_Forbidden,
                                            "Cannot remove expiration date");
                try {
                    file.setExpiration(parseDate(value.toString()));
                }
                catch (ArchiveException e) {
                    throw new HttpException(HttpResponse.__409_Conflict,
                                            e.getMessage());
                }
            }
            else if (propname.equals(ELT_LEGALHOLD))
                try {
                    file.setLegalHolds((Set)value);
                }
                catch (ArchiveException e) {
                    // Assigning the legal hold set should always succeed
                    logger.log(Level.WARNING, "setLegalHolds failed", e);
                    throw new HttpException(HttpResponse.__500_Internal_Server_Error,
                                            e.getMessage());
                }
            else {
                String msg = "Couldn't handle element \"" + propname + "\"";
                logger.severe(msg);
                throw new HttpException(HttpResponse.__500_Internal_Server_Error,
                                        msg);
            }
                                        
        }
    }

    /*
   Example XML:

   <propertyupdate xmlns="DAV:" xmlns:HCFS="http://www.sun.com/honeycomb/">
       <set>
           <prop>
               <HCFS:legalholds>
                   <HCFS:tag>People v. Microsoft</HCFS:tag>
                   <HCFS:tag>Sun v. Microsoft<HCFS:tag/>
               </HCFS:legalholds>
           </prop>
           <prop>
               <HCFS:expiration>
                   2006-12-25T10:10:00Z
               </HCFS:expiration>
           </prop>
       </set>
       <remove>
           <prop>
               <HCFS:someotherprop/>
           </prop>
       </remove>
   </propertyupdate>

    */

    /**
     * Parse a &lt;propertyupdate> element and return a name-value
     * map. If the value is null, the name is to be removed. A value
     * can be a string or a list of strings.
     */
    private Map getProperties(InputStream inputStream)
            throws ParserConfigurationException,
                   IOException, SAXException, InvalidBodyException {
        Document document = null;
        Map retval = new HashMap();

        try {
            DocumentBuilder documentBuilder = Constants.getDocumentBuilder();
            document = documentBuilder.parse(inputStream);
        } catch (SAXParseException e) {
            // There is no XML content
            logger.info("The PROPPATCH request does not have an XML body");
            throw new InvalidBodyException(e.toString());
        }
        
        NodeList childList = null;

        if (document == null)
            throw new InvalidBodyException("null document");

        // Get the root element "propertyupdate" of the document
        Element rootElement = document.getDocumentElement();
        childList = rootElement.getChildNodes();

        for (int i = 0; i < childList.getLength(); i++) {
            Node currentNode = childList.item(i);

            if (currentNode.getNodeType() != Node.ELEMENT_NODE)
                // Ignore other types of nodes
                continue;

            String nodeName = currentNode.getNodeName();

            // If nodeName has an XML namespace in front, strip it
            int pos = nodeName.lastIndexOf(':');
            if (pos >= 0)
                nodeName = nodeName.substring(pos + 1);

            boolean deleting;
            if (nodeName.equals("set"))
                deleting = false;
            else if (nodeName.equals("remove"))
                deleting = true;
            else
                // We don't know what this element is; ignore
                continue;

            // Get all <prop> children and their values
            
            childList = currentNode.getChildNodes();
            for (int j = 0; j < childList.getLength(); j++) {
                Node propNode = childList.item(j);

                if (propNode.getNodeType() != Node.ELEMENT_NODE)
                    continue;

                String pnodeName = propNode.getNodeName();
                pos = pnodeName.lastIndexOf(':');
                if (pos >= 0)
                    pnodeName = pnodeName.substring(pos + 1);

                if (!pnodeName.equalsIgnoreCase("prop"))
                    continue;

                // Get all properties in this node
                NodeList propChildren = propNode.getChildNodes();
                for (int k = 0; k < propChildren.getLength(); k++) {
                    Node property = propChildren.item(k);

                    if (property.getNodeType() != Node.ELEMENT_NODE)
                        continue;

                    String propName = property.getNodeName();

                    // Remove XML namespace
                    if ((pos = nodeName.lastIndexOf(':')) >= 0)
                        propName = propName.substring(pos + 1);

                    if (deleting)
                        retval.put(propName, null);
                    else
                        retval.put(propName, getValue(propName, property));
                }

            } // All children of <prop>
        } // All <set> and <remove> elements

        return retval;
    }

    private Object getValue(String nodeName, Node node)
            throws InvalidBodyException {

        // Check the value we actually have with what the node
        // expects. We know that HCFS:expiration takes a string, and
        // HCFS:legalholds takes a list of strings.

        int pos;
        NodeList childList = node.getChildNodes();

        if (nodeName.equals(ELT_EXPIRATION))
            return getChildStringValue(node);

        if (nodeName.equals(ELT_LEGALHOLD)) {
            // There's a list of children, each of which has exactly
            // one child, a string
       
            Set tags = new HashSet();

            for (int i = 0; i < childList.getLength(); i++) {
                Node valueNode = childList.item(i);
                String valueName = valueNode.getNodeName();
                
                if ((pos = valueName.lastIndexOf(':')) >= 0)
                    valueName = valueName.substring(pos + 1);

                if (valueName.equalsIgnoreCase(ELT_TAG))
                    tags.add(getChildStringValue(valueNode));
            }

            return tags;
        }

        // It's something else, and we don't know what to do about it.
        return null;
    }

    /** The node has exactly one child, a string; return it */
    private String getChildStringValue(Node node) throws InvalidBodyException {
        // Example: <value>Hello, world!</value>

        NodeList childList = node.getChildNodes();
        if (childList.getLength() != 1)
            throw new InvalidBodyException("Node " + node +
                                           " is expected to have one child");

        Node child = childList.item(0);
        if (child.getNodeType() != Node.TEXT_NODE)
            throw new InvalidBodyException("Node " + node +
                                           " child is not string");

        return child.getNodeValue();
    }

    /** Given a date string, parse it */
    private Date parseDate(String s) {
        if (s == null || s.equalsIgnoreCase("unknown"))
            return null;
        return CanonicalEncoding.decodeISO8601Date(s);
    }

    private class InvalidBodyException extends Exception {
        InvalidBodyException() { super(); }
        InvalidBodyException(String m) { super(m); }
        InvalidBodyException(Exception e) { super(e); }
    }
}
