package com.sun.dtf.comm.rpc;

import org.apache.xmlrpc.common.TypeFactory;
import org.apache.xmlrpc.common.XmlRpcStreamConfig;
import org.apache.xmlrpc.serializer.TypeSerializer;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

public abstract class DTFSerializerBase implements TypeSerializer {
  
	protected static final Attributes ZERO_ATTRIBUTES = new AttributesImpl();
	public static final String ACTION_NAME_ATTRIBUTE = "name";

    private final XmlRpcStreamConfig _config;
    private final TypeFactory _typeFactory;

    /** Creates a new instance.
     * @param pTypeFactory The factory being used for creating serializers.
     * @param pConfig The configuration being used for creating serializers.
     */
    public DTFSerializerBase(TypeFactory pTypeFactory, XmlRpcStreamConfig pConfig) {
        _typeFactory = pTypeFactory;
        _config = pConfig;
    }
    
    public XmlRpcStreamConfig getXMLRpcStreamConfig() { 
        return _config;
    }
    
    public TypeFactory getTypeFactory() { 
        return _typeFactory;
    }
}
