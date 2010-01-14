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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

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
 * All the HTTP/XML stuff is here. Methods for connection to HC
 * metadata are in {@link FileProperties}.
 */
public class PropfindHandler extends SpecificHandler {

    private static final Logger LOG =
        Logger.getLogger(PropfindHandler.class.getName());

    private static final int FIND_BY_PROPERTY = 0;
    private static final int FIND_ALL_PROP = 1;
    private static final int FIND_PROPERTY_NAMES = 2;
    
    private static final String statusOK =
        "HTTP/1.1 " + HttpResponse.__200_OK + " OK";
    private static final String statusNotFound =
        "HTTP/1.1 " + HttpResponse.__404_Not_Found + " Not found";

    public PropfindHandler() {
    }

    public void handle(HCFile file, String[] extraPath,
                       HttpRequest request, HttpResponse response,
                       InputStream inp, OutputStream outp)
            throws IOException, HttpException {
        try {
            if (extraPath != null)
                throw new HttpException(HttpResponse.__404_Not_Found,
                                        "No such file: " + file.fileName() +
                                        FSCache.combine(extraPath));
                realHandler(file, request, response, inp, outp);
        }
        catch (ParserConfigurationException e) {
            LOG.log(Level.SEVERE, "Couldn't parse PROPFIND \"" + e + "\"", e);
            throw new HttpException(HttpResponse.__500_Internal_Server_Error);
        }
        catch (SAXException e) {
            LOG.log(Level.SEVERE, "Couldn't parse PROPFIND \"" + e + "\"", e);
            throw new HttpException(HttpResponse.__400_Bad_Request);
        }
    }

    private void realHandler(HCFile file,
                             HttpRequest request, HttpResponse response,
                             InputStream in, OutputStream out)
        throws
            IOException, HttpException,
            ParserConfigurationException, SAXException  {

        // Propfind depth
        int depth = getDepth(request, file);

        if (LOG.isLoggable(Level.INFO))
            LOG.info("  propfind " + file.fileName() + " depth = " + depth);

        Vector properties = null; // properties which are to be displayed
        int type;                 // type of request

        try {
            properties = getPropertyNames(in);
            if (properties == null)
                type = FIND_PROPERTY_NAMES;
            else
                type = FIND_BY_PROPERTY;
        }
        catch (NoPropertyNamesException e) {
            // Default PROPFIND type if no XML body. The specification
            // (http://www.webdav.org/specs/rfc2518.html) says: An
            // empty PROPFIND request body MUST be treated as a
            // request for the names and values of all properties
            LOG.fine("No property names: " + e);
            type = FIND_ALL_PROP;
        }

        sendResponse(request, response, out, file, depth, type, properties);
    }

    private void sendResponse(HttpRequest request, HttpResponse response,
                              OutputStream outputStream,
                              HCFile file,
                              int depth, int type, Vector properties)
            throws IOException, HttpException {

        response.setStatus(HttpResponse.__207_Multi_Status);
        response.setContentType("text/xml; charset=UTF-8");

        // Create multistatus object
        XMLWriter xmlOut = new XMLWriter(outputStream);
        xmlOut.writeXMLHeader();

        xmlEltStart(xmlOut, "multistatus" +
                            Constants.generateNamespaceDeclarations());

        sendProperties(request, file, xmlOut, type, properties, depth);
        
        xmlEltEnd(xmlOut, "multistatus");
            
        xmlOut.sendData(true);
    }

    /**
     * Get the property names in the XML in the input. A null return
     * value means all property values are required. If there's no
     * XML, or it asks for a list of properties, an exception is
     * thrown.
     */
    private Vector getPropertyNames(InputStream inputStream)
            throws NoPropertyNamesException, ParserConfigurationException,
                   IOException, SAXException {
        Document document = null;

        try {
            DocumentBuilder documentBuilder = Constants.getDocumentBuilder();
            document = documentBuilder.parse(inputStream);
        } catch (SAXParseException e) {
            // There is no XML content
            LOG.fine("The PROPFIND request does not have an XML body");
            throw new NoPropertyNamesException(e);
        }
        
        Node propNode = null;
        NodeList childList = null;

        if (document == null)
            throw new NoPropertyNamesException("null document");

        // Get the root element of the document
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

            if (nodeName.equals("propname"))
                return null;

            if (nodeName.equals("prop")) {
                propNode = currentNode;
                break;
            }

            if (nodeName.equals("allprop"))
                throw new NoPropertyNamesException("<allprop/>");
        }

        // If there's no element named "prop", we have no prop names
        if (propNode == null)
            throw new NoPropertyNamesException("no <prop>");

        LOG.finer("<prop>");

        Vector properties = new Vector();

        childList = propNode.getChildNodes();
        for (int i = 0; i < childList.getLength(); i++) {
            Node currentNode = childList.item(i);

            if (currentNode.getNodeType() != Node.ELEMENT_NODE)
                continue;

            String nodeName = currentNode.getNodeName();
            String propertyName = nodeName;

            int pos;
            if ((pos = nodeName.indexOf(':')) != -1)
                propertyName = nodeName.substring(pos + 1);

            properties.addElement(propertyName);

            if (LOG.isLoggable(Level.FINER))
                LOG.finer("    prop \"" + propertyName + "\"");
        }

        LOG.finer("</prop>");

        return properties;
    }

    /** Find and parse the "Depth:" header from the request */
    private int getDepth(HttpRequest request, HCFile file) {
        String depthStr = request.getField(ProtocolConstants.DEPTH_HEADER);

        int depth = 0;

        if (depthStr == null)
            depth = FileProperties.INFINITY;
        else if (depthStr.equalsIgnoreCase(ProtocolConstants.INFINITY))
            depth = FileProperties.INFINITY;
        else if (depthStr.equals("0"))
            depth = 0;
        else if (depthStr.equals("1"))
            depth = 1;

        return depth;
    }

    /**
     * For depth specified, traverse the sub-tree at file and emit the
     * value for every property name specified.
     */
    private boolean sendProperties(HttpRequest request, HCFile file,
                                   XMLWriter xmlOut,
                                   int type, Vector props, int depth)
            throws IOException, HttpException {

        if (depth == 0)
            return sendProperties(request, file, file, xmlOut, type, props);

        if (LOG.isLoggable(Level.FINE))
            LOG.fine("getting descendants for " + file.fileName() +
                     " (depth=" + depth + ")");

        HCFile[] kids = FileProperties.getDescendants(file, depth);

        if (kids == null || kids.length == 0) {
            if (LOG.isLoggable(Level.FINER))
                LOG.finer("No children for \"" + file.fileName() + "\"");
            return sendProperties(request, file, file, xmlOut, type, props);
        }

        String msg = null;
        if (LOG.isLoggable(Level.FINER))
            msg = "File " + file.fileName() + " has " + kids.length +
                " children at depth " + depth + ":";

        for (int i = 0; i < kids.length; i++) {
            sendProperties(request, kids[i], file, xmlOut, type, props);
            xmlOut.sendData(false);
            if (LOG.isLoggable(Level.FINER))
                msg += " \"" + kids[i].fileName() + "\"";
        }

        if (LOG.isLoggable(Level.FINER))
            LOG.finer(msg);

        return true;
    }

    /** Print file's specified properties to the XML output stream */
    private boolean sendProperties(HttpRequest request,
                                   HCFile file, HCFile requestRoot,
                                   XMLWriter xmlOut,
                                   int type, Vector properties) {

        xmlEltStart(xmlOut, "response");

        // Generate the href element

        String href = getHref(request, file, requestRoot);
        if (LOG.isLoggable(Level.FINE))
            LOG.fine("href (" + file.fileName() + ") = \"" + href + "\"");

        xmlEltStart(xmlOut, "href");
        xmlOut.writeText(href);
        xmlEltEnd(xmlOut, "href");

        // Emit requested properties
        
        switch (type) {

        case FIND_ALL_PROP :
            writeAllProperties(xmlOut, file);
            break;

        case FIND_PROPERTY_NAMES :
            writePropertyNames(xmlOut, file);
            break;

        case FIND_BY_PROPERTY :
            writeProperties(xmlOut, file, properties);
            break;
        }

        xmlEltEnd(xmlOut, "response");
        return true;
    }

    /**
     * Construct the href attribute from the request and returned
     * document
     */
    private String getHref(HttpRequest request,
                           HCFile file, HCFile requestRoot) {

        StringBuffer sb = new StringBuffer();
        sb.append(request.getRootURL()).append(ProtocolConstants.WEBDAV_PATH);
        sb.append(URLencoder.encode(file.fileName()));

        return sb.toString();
    }

    private void writeAllProperties(XMLWriter xmlOut, HCFile file) {
        if (LOG.isLoggable(Level.FINE))
            LOG.fine("    all properties for \"" + file.fileName() + "\"");

        xmlEltStart(xmlOut, "propstat");
        xmlEltStart(xmlOut, "prop");

        FileProperties fileProps = new FileProperties(file);
        Map allProperties = fileProps.getAllProperties();
        Set propNames = allProperties.keySet();
        for (Iterator i = propNames.iterator(); i.hasNext(); ) {
            String name = (String) i.next();
            String value = (String) allProperties.get(name);

            xmlProp(xmlOut, FileProperties.XMLname(name), value);
        }

        xmlEltEnd(xmlOut, "prop");

        xmlEltStart(xmlOut, "status");
        xmlOut.writeText(statusOK);
        xmlEltEnd(xmlOut, "status");

        xmlEltEnd(xmlOut, "propstat");
    }


    private void writePropertyNames(XMLWriter xmlOut, HCFile file) {
        if (LOG.isLoggable(Level.FINE))
            LOG.fine("    property names for \"" + file.fileName() + "\"");

        xmlEltStart(xmlOut, "propstat");
        xmlEltStart(xmlOut, "prop");

        FileProperties fileProps = new FileProperties(file);
        String[] pNames = fileProps.getPropertyNames();

        for (int i = 0; i < pNames.length; i++)
            xmlElt(xmlOut, pNames[i]);

        xmlEltEnd(xmlOut, "prop");

        xmlEltStart(xmlOut, "status");
        xmlOut.writeText(statusOK);
        xmlEltEnd(xmlOut, "status");

        xmlEltEnd(xmlOut, "propstat");
    }

    private void writeProperties(XMLWriter xmlOut, HCFile file,
                                 Vector propertyNames) {
        if (LOG.isLoggable(Level.FINE)) {
            String msg = "For \"" + file.fileName() + "\" need properties {";
            String delim = "";
            Enumeration properties = propertyNames.elements();
            while (properties.hasMoreElements()) {
                msg += delim + (String) properties.nextElement();
                delim = ", ";
            }
            LOG.fine(msg + "}");
        }

        FileProperties fileProps = new FileProperties(file);

        Vector missProps = new Vector();

        // Parse the list of properties

        xmlEltStart(xmlOut, "propstat");
        xmlEltStart(xmlOut, "prop");

        Enumeration properties = propertyNames.elements();
        while (properties.hasMoreElements()) {
            String property = (String) properties.nextElement();

            if (LOG.isLoggable(Level.FINEST))
                LOG.finest("        \"" + property + "\"");

            String propValue = fileProps.getProperty(property);
            String pname = FileProperties.XMLname(property);
            if (propValue == null)
                missProps.addElement(pname);
            else
                xmlProp(xmlOut, pname, propValue);
        }

        xmlEltEnd(xmlOut, "prop");

        xmlEltStart(xmlOut, "status");
        xmlOut.writeText(statusOK);
        xmlEltEnd(xmlOut, "status");

        xmlEltEnd(xmlOut, "propstat");

        // Missing properties

        if (LOG.isLoggable(Level.FINER))
            LOG.finer("    missing props for \"" + file.fileName() + "\"");

        Enumeration missList = missProps.elements();
        if (missList.hasMoreElements()) {

            xmlEltStart(xmlOut, "propstat");

            xmlEltStart(xmlOut, "prop");
            while (missList.hasMoreElements()) {
                String m = (String) missList.nextElement();
                if (LOG.isLoggable(Level.FINER))
                    LOG.finer("        \"" + m + "\"");
                xmlElt(xmlOut, m);
            }
            xmlEltEnd(xmlOut, "prop");

            xmlEltStart(xmlOut, "status");
            xmlOut.writeText(statusNotFound);
            xmlEltEnd(xmlOut, "status");

            xmlEltEnd(xmlOut, "propstat");
        }
    }

    private void xmlElt(XMLWriter xml, String name) {
        xml.writeElement(null, name, XMLWriter.NO_CONTENT);
    }
    private void xmlEltStart(XMLWriter xml, String name) {
        xml.writeElement(null, name, XMLWriter.OPENING);
    }
    private void xmlEltEnd(XMLWriter xml, String name) {
        xml.writeElement(null, name, XMLWriter.CLOSING);
    }

    private void xmlProp(XMLWriter xml, String name, long value) {
        xmlProp(xml, name, Long.toString(value));
    }
    private void xmlProp(XMLWriter xml, String name, String value) {
        xml.writeProperty(null, name, value);
    }
 
    private class NoPropertyNamesException extends Exception {
        NoPropertyNamesException() { super(); }
        NoPropertyNamesException(String m) { super(m); }
        NoPropertyNamesException(Exception e) { super(e); }
    }
}
