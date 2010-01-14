package com.sun.dtf.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.channels.Channels;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;


import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.exception.UnsupportedFeatureException;
import com.wutka.dtd.DTD;
import com.wutka.dtd.DTDAttribute;
import com.wutka.dtd.DTDDecl;
import com.wutka.dtd.DTDElement;
import com.wutka.dtd.DTDEntity;
import com.wutka.dtd.DTDParser;

public class DTDHandler {
    
    public static long ATTRIB_REQUIRED = 0;
    public static long ATTRIB_OPTIONAL = 1;

    private DTD _dtd = null;

    public DTDHandler(InputStream dtd) throws DTFException {
        try {
            Reader reader = Channels.newReader(Channels.newChannel(dtd), "UTF-8"); 
            DTDParser parser = new DTDParser(reader);
            _dtd = parser.parse(false);
        } catch (IOException e) {
            throw new ParseException("error loading dtd file.", e);
        }
    }

    public Hashtable getAttributes(String elementName) {
        DTDElement element = 
                    ((DTDElement) _dtd.elements.get(elementName.toLowerCase()));

        if (element == null)
            return new Hashtable();
        else
            return element.attributes;
    }
    
    public boolean isAttributeRequired(String attributeName, String elementName) { 
        DTDElement element = (DTDElement)_dtd.elements.get(elementName);
        DTDAttribute attribute = element.getAttribute(attributeName);
        return (attribute.getDecl() == DTDDecl.REQUIRED);
    }

    public boolean isAttributeOptional(String attributeName, String elementName) { 
        DTDElement element = (DTDElement)_dtd.elements.get(elementName);
        DTDAttribute attribute = element.getAttribute(attributeName);
        return (attribute.getDecl() == DTDDecl.IMPLIED);
    }
    
    public Hashtable getElements() {
        return _dtd.elements;
    }

    public Hashtable getEntities() {
        return _dtd.entities;
    }

    public void writeTo(OutputStream os) throws UnsupportedFeatureException {
        PrintStream ps = new PrintStream(os);
       
        DTD dtd = _dtd;
        Enumeration e =  null;

        HashMap processed_entities = new HashMap();
        Hashtable process = new Hashtable();

        // Entities first
        e = dtd.entities.elements();

        do { 
            while (e.hasMoreElements()) {
                boolean processlater = false;
                DTDEntity entity = (DTDEntity) e.nextElement();
    
                String[] fields = entity.value.split("\\|");
              
                for(int i = 0; i < fields.length; i++) { 
                    if (fields[i].matches("%.*;")) {
                        String entityRef = fields[i].substring(1,fields[i].length()-1);
                        // check all of the referenced entities and if it has not 
                        // been processed yet do not process this one and instead
                        // put this one back in the list of entities to process
                        if (!processed_entities.containsKey(entityRef)) {
                           // this element is not ready to be processed. 
                           processlater = true; 
                        }
                    }
                }
               
                if (!processlater) { 
                    processed_entities.put(entity.name,entity);
                    process.remove(entity.name);
                    
                    // <!ENTITY % entity_name "entity_value">
                    ps.println("<!ENTITY % " + entity.name + " \"" + 
                               (entity.value == null ? "" : entity.value ) +"\"> ");
                    ps.println();
        
                    if (entity.externalID != null) 
                        throw new UnsupportedFeatureException(
                                            "External entities are not supported yet.");
        
                    if (entity.ndata != null) 
                        throw new UnsupportedFeatureException(
                                               "External ndata are not supported yet.");
                } else { 
                    process.put(entity.name, entity);
                }
            }
            
            e = process.elements();
        } while (process.size() != 0);
       
       
        while (e.hasMoreElements()) {
            throw new UnsupportedFeatureException(
                                            "Notations are not supported yet.");
        }
    }
}