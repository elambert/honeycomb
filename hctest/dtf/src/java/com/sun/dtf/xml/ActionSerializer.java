package com.sun.dtf.xml;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import org.apache.xmlrpc.common.TypeFactory;
import org.apache.xmlrpc.common.XmlRpcStreamConfig;
import org.apache.xmlrpc.serializer.TypeSerializer;
import org.apache.xmlrpc.serializer.TypeSerializerImpl;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.reference.RefWrapper;
import com.sun.dtf.comm.rpc.DTFSerializerBase;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.logger.DTFLogger;

public class ActionSerializer extends DTFSerializerBase implements TypeSerializer {
  
    private static DTFLogger _logger = DTFLogger.getLogger(ActionSerializer.class);

    public static final String ACTION_TAG = "dtf_action";
    
    public ActionSerializer(TypeFactory pTypeFactory, XmlRpcStreamConfig pConfig) {
        super(pTypeFactory,pConfig);
    }
    
    protected void writeObject(ContentHandler pHandler, Object pObject) throws SAXException {
        TypeSerializer ts = getTypeFactory().
                                getSerializer(getXMLRpcStreamConfig(), pObject);
       
        if (ts == null) 
            throw new SAXException("Unsupported Java type: " + pObject.getClass().getName());
        
        ts.write(pHandler, pObject);
    }
    
    protected void writeData(ContentHandler pHandler, Object pObject) throws SAXException {
        Object[] data = (Object[]) pObject;
        for (int i = 0;  i < data.length;  i++) {
            writeObject(pHandler, data[i]);
        }
    }

    public void write(final ContentHandler pHandler, 
                      Object object) throws SAXException {
        write(pHandler,object,true);
    }
   
    /**
     * 
     * @param pHandler
     * @param object
     * @param valueNeeded used internally so that we don't wrap Action classes
     *                    with unnecessary value tags :)
     * @throws SAXException
     */
    private void write(final ContentHandler pHandler, 
                       Object object,
                       boolean valueNeeded) throws SAXException {
        
        if (object == null)
            return;
        
        if (!(object instanceof Action))
            throw new SAXException("Object not of Action type, got [" + 
                                   object + "]");
        
        Action action = (Action) object;
        AttributesImpl attributes = new AttributesImpl();
 
        if (action instanceof RefWrapper) { 
            RefWrapper wrapper = (RefWrapper) action;
            try {
                action = wrapper.lookupReference();
            } catch (ParseException e) {
                throw new SAXException("Issue looking up reference.",e);
            }
        }
        
        /*
         * Get all the getters/setters and serialize them as attributes or in 
         * the case of subActions that have setter and getters serialize those
         * at the top as independent actions :)
         */
        Class actionClass = action.getClass();
        Method[] methods = actionClass.getMethods();
        StringBuffer buffer = null;
       
        for (int index = 0; index < methods.length; index++) {
            Class[] args = new Class[0];
           
            Method getter = methods[index];
            try {
                // ignore static methods 
                String getName = getter.getName().toLowerCase();
                int modifier = getter.getModifiers();
                if (!Modifier.isStatic(modifier)) { 
                    if ( getName.equals("getclass")) {
                        Object result = getter.invoke(action, (Object[])args);
                        attributes.addAttribute("", 
                                                "class", 
                                                "class", 
                                                "", 
                                                result.toString().substring(6));
                    } else  if (getName.equals("getcdata")) { 
                        Object result = getter.invoke(action, (Object[])args);
                        buffer = new StringBuffer();
                        buffer.append(result.toString());
                    } else  if ( getName.startsWith("get") && 
                               !getter.getReturnType().equals(Void.TYPE)) {
                        Object result = getter.invoke(action, (Object[])args);
                       
                        if (result != null) {
                            String name = getName.substring("get".length());
                            attributes.addAttribute("", 
                                                    name,
                                                    name,
                                                    "", 
                                                    result.toString());
                        }
                    }
                }
            } catch (SecurityException e) {
                _logger.warn("Error invoking getter:  " + getter.getName() + " .",e);
            } catch (IllegalArgumentException e) {
                _logger.warn("Error invoking getter:  " + getter.getName() + " .",e);
            } catch (IllegalAccessException e) {
                _logger.warn("Error invoking getter:  " + getter.getName() + " .",e);
            } catch (InvocationTargetException e) {
                _logger.warn("Error invoking getter:  " + getter.getName() + " .",e);
            }
        }

        if (valueNeeded)
            pHandler.startElement("", 
                                  TypeSerializerImpl.VALUE_TAG,
                                  TypeSerializerImpl.VALUE_TAG,
                                  ZERO_ATTRIBUTES);
        pHandler.startElement("", ACTION_TAG, ACTION_TAG, attributes);
     
        if (buffer != null) { 
            String buff = buffer.toString();
            pHandler.characters(buff.toCharArray(), 0, buff.length());
        }
     
        ArrayList children =  action.children();
        for (int i = 0; i < children.size(); i++)
            write(pHandler, children.get(i), false);
        
        pHandler.endElement("", ACTION_TAG, ACTION_TAG);
        
        if (valueNeeded)
            pHandler.endElement("", 
                                TypeSerializerImpl.VALUE_TAG,
                                TypeSerializerImpl.VALUE_TAG);
    }
    
}
