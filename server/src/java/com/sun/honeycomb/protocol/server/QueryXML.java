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


package com.sun.honeycomb.protocol.server;

import com.sun.honeycomb.common.NameValueXML;
import com.sun.honeycomb.common.XMLException;
import com.sun.honeycomb.common.Encoding;
import com.sun.honeycomb.common.LogEscapeFormatter;
import com.sun.honeycomb.common.CanonicalEncoding;

import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.parsers.SAXParser;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.EncoderException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.InputStream;
import java.io.IOException;

public class QueryXML extends Encoding{
    String query = null;
    String key = null;
    ArrayList selects = new ArrayList();
    ArrayList parameters = new ArrayList();
    final static SAXParserFactory factory = SAXParserFactory.newInstance();
    protected static final Logger LOGGER =
        Logger.getLogger(QueryXML.class.getName());
    
    public Object[] getParameters() {
        return parameters.toArray();
    }

    class Handler extends DefaultHandler{

        int majorVersion = -1;
        int minorVersion = 0;

        public void startElement(String uri,
                                 String localName,
                                 String qName,
                                 Attributes attributes)
            throws SAXException{
            try{
                if (NameValueXML.PARAMETER_TAG.equals(qName)) {
                    Object value = decode(attributes.getValue(NameValueXML.PARAMETER_VALUE));
                    String iStr = attributes.getValue(NameValueXML.PARAMETER_INDEX);
                    int i;
                    try {
                        i = Integer.parseInt(iStr);
                    } catch (NumberFormatException e) {
                        throw new SAXException(e);
                    }
                    if (i > parameters.size()) {
                        for (int j=parameters.size();j<i;j++) {
                            parameters.add(null);
                        }
                    }
                    parameters.set(i-1, value);
                    if (LOGGER.isLoggable(Level.FINER))
                        LOGGER.finer(LogEscapeFormatter.native2ascii("Found parameter '" +
                                     CanonicalEncoding.encode(value) + "' " + i));
                }

                else  if (NameValueXML.PREPARED_STATEMENT_TAG.equals(qName)) {
                    query = decodeString(attributes.getValue(NameValueXML.SQL_NAME));
                    if (LOGGER.isLoggable(Level.FINER))
                        LOGGER.finer(LogEscapeFormatter.native2ascii("Found sql '" + query+"'"));
                }

                else  if (NameValueXML.SELECT_TAG.equals(qName)) {
                    String select = decodeName(attributes.getValue(NameValueXML.SELECT_VALUE));
                    selects.add(select);
                    if (LOGGER.isLoggable(Level.FINER))
                        LOGGER.finer(LogEscapeFormatter.native2ascii("Found select '" + select+"'"));
                }
                else  if (NameValueXML.KEY_TAG.equals(qName)) {
                    key = decodeName(attributes.getValue(NameValueXML.PARAMETER_VALUE));
                    if (LOGGER.isLoggable(Level.FINER))
                        LOGGER.finer(LogEscapeFormatter.native2ascii("Found key '" + key+"'"));
                }
            }
            catch (DecoderException de){
                throw new RuntimeException(de);
            }
        }
    }

    QueryXML (final InputStream is) {
        super(BASE_64);
        final Handler handler = new Handler();
        try{
            SAXParser parser = factory.newSAXParser();
            parser.parse(is, handler);
        }
        catch (SAXException sa){
            throw new RuntimeException (sa);
        }
        catch (ParserConfigurationException pce){
            throw new RuntimeException (pce);
        }
        catch (IOException ioe){
            throw new RuntimeException (ioe);
        }
    }

}
