package com.sun.honeycomb.client;

import java.io.IOException;

import com.sun.honeycomb.client.Connection;
import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.common.ArchiveException;

public class TestNVOA extends NameValueObjectArchive {

    public TestNVOA(String address) throws ArchiveException, IOException {
        super(address);
    }

    public TestNVOA(String address, int port) throws ArchiveException, IOException {
        super(address, port);
    }
    
    /*
     * Just overwrite the newConnection method so we don't share the connection
     * pools from any previous instances of NameValueObjectArchive objects. This
     * is the only way to use the load gaming strategy correctly.
     */
    protected Connection newConnection(String address, int port)
            throws ArchiveException, IOException {
        return new Connection(address, port, false);
    }
}
