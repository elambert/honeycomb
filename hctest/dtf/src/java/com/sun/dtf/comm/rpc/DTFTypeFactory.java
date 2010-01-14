package com.sun.dtf.comm.rpc;

import org.apache.ws.commons.util.NamespaceContextImpl;
import org.apache.xmlrpc.common.TypeFactoryImpl;
import org.apache.xmlrpc.common.XmlRpcController;
import org.apache.xmlrpc.common.XmlRpcStreamConfig;
import org.apache.xmlrpc.parser.TypeParser;
import org.apache.xmlrpc.serializer.TypeSerializer;
import org.xml.sax.SAXException;

import com.sun.dtf.actions.Action;
import com.sun.dtf.xml.ActionParser;
import com.sun.dtf.xml.ActionSerializer;


public class DTFTypeFactory extends TypeFactoryImpl {
    public DTFTypeFactory(XmlRpcController pController) {
        super(pController);
    }

    public TypeParser getParser(XmlRpcStreamConfig config,
                                NamespaceContextImpl context, 
                                String pURI, 
                                String pLocalName) {
        
        if (pLocalName.equals(ActionSerializer.ACTION_TAG)) { 
            /*
             * no parsing of functions or references when we're doing remote
             * actions it makes no sense for the time being and only results
             * in unnecessary complications.
             */
            return new ActionParser(false,false);
        }
        
        return super.getParser(config, context, pURI, pLocalName);
    }

    public TypeSerializer getSerializer(XmlRpcStreamConfig config, 
                                        Object object) 
                          throws SAXException {
      
        if (object instanceof Action) {
            return new ActionSerializer(getController().getTypeFactory(),config);
        } else {
            return super.getSerializer(config, object);
        }
    }
}