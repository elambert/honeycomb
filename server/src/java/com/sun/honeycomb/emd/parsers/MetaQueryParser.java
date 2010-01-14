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



package com.sun.honeycomb.emd.parsers;

import java.io.InputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import com.sun.honeycomb.emd.cache.CacheInterface;
import com.sun.honeycomb.emd.common.EMDException;
import java.util.logging.Logger;

public class MetaQueryParser
    extends DefaultHandler {

    private static final String META_QUERY_PREFIX       = "<query";
    private static final String QUERY_TAG               = "query";
    private static final String ATTRIBUTE_CACHEID       = "cacheId";
    private static final String ATTRIBUTE_QUERY         = "query";

    /**********************************************************************
     *
     * Public static methods
     *
     **********************************************************************/

    public static class MetaQuery {
        private String cacheId;
        private String query;

        private MetaQuery(String nCacheId,
                          String nQuery) {
            cacheId = nCacheId;
            query = nQuery;
        }

        public String getCacheId() {
            return(cacheId);
        }

        public String getQuery() {
            return(query);
        }
    }

    private static MetaQuery[] parseQuery(InputSource source) 
        throws EMDException {
        MetaQueryParser instance = new MetaQueryParser();
        
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();
            parser.parse(source, instance);
        } catch (SAXException e) {
            EMDException newe = new EMDException("Failed to parse the meta query ["+
                                                 e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (ParserConfigurationException e) {
            EMDException newe = new EMDException("Failed to parse the meta query ["+
                                                 e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        } catch (IOException e) {
            EMDException newe = new EMDException("Failed to parse the meta query ["+
                                                 e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }

        return(instance.getResult());
    }

    public static MetaQuery[] parseQuery(InputStream input) 
        throws EMDException {
        return(parseQuery(new InputSource(input)));
    }
    
    public static MetaQuery[] parseQuery(String inputString)
        throws EMDException {

        if (!inputString.regionMatches(true, 0,
                                       META_QUERY_PREFIX, 0, META_QUERY_PREFIX.length())) {
            // The query is not a meta query
            MetaQuery[] result = new MetaQuery[1];
            result[0] = new MetaQuery(CacheInterface.EXTENDED_CACHE, inputString);
            return(result);
        }

        return(parseQuery(new InputSource(new StringReader(inputString))));
    }

    /**********************************************************************
     *
     * Private implementation
     *
     **********************************************************************/
    
    private ArrayList queries;

    private MetaQueryParser() {
        super();
        queries = new ArrayList();
    }

    public void startElement(String namespaceURI,
                             String localName,
                             String qName,
                             Attributes atts)
        throws SAXException {
        
        if (!qName.equalsIgnoreCase(QUERY_TAG)) {
            return;
        }

        String cacheId = atts.getValue(ATTRIBUTE_CACHEID);
        if (cacheId == null) {
            throw new SAXException("No "+ATTRIBUTE_CACHEID+" attribute found for tag "
                                   +QUERY_TAG);
        }

        String query = atts.getValue(ATTRIBUTE_QUERY);
        if (query == null) {
            throw new SAXException("No "+ATTRIBUTE_QUERY+" attribute found for tag "
                                   +QUERY_TAG);
        }

        queries.add(new MetaQuery(cacheId, query));
    }

    private MetaQuery[] getResult() {
        MetaQuery[] result = new MetaQuery[queries.size()];
        queries.toArray(result);
        return(result);
    }
}
