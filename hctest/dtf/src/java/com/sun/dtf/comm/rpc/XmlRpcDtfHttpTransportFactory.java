package com.sun.dtf.comm.rpc;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcSunHttpTransport;
import org.apache.xmlrpc.client.XmlRpcTransport;
import org.apache.xmlrpc.client.XmlRpcTransportFactoryImpl;

public class XmlRpcDtfHttpTransportFactory extends XmlRpcTransportFactoryImpl {

    public XmlRpcDtfHttpTransportFactory(XmlRpcClient pClient) {
        super(pClient);
    }

    public XmlRpcTransport getTransport() { 
        return new XmlRpcSunHttpTransport(getClient());
    }
}