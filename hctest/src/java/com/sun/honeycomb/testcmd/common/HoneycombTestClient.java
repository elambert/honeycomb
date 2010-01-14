package com.sun.honeycomb.testcmd.common;

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



import java.io.*;
import java.lang.*;
import java.util.*;
import java.nio.channels.FileChannel;

import com.sun.honeycomb.common.*;
import com.sun.honeycomb.client.*;

import com.sun.honeycomb.client.TestNVOA;

/*
 * Honeycomb Client Library
 */

public class HoneycombTestClient {

    private static NameValueObjectArchive archive = null;
    private static final int maxResults = 10000000;
    public static final int USE_DEFAULT_MAX_RESULTS = -1;
    private boolean debuggingEnabled = false;
    private int initSleep = 5000;

    public HoneycombTestClient(String ip) throws HoneycombTestException {
	try{
	    archive = new TestNVOA(ip);
	}
	catch (ArchiveException ae){
	    throw new HoneycombTestException ("init failed: " + ae.getMessage(), ae);
	}
	catch (IOException ioe){
	    throw new HoneycombTestException ("init failed: " + ioe.getMessage(), ioe);
	}
/*
try skipping the sleep -BR
        // Adding a sleep seems to alleviate some hang problems when
        // using the Honeycomb client immediately after instantiating
        // it.
        try {
            Thread.sleep(initSleep);
        } catch (InterruptedException e) {}
*/
    }

    // Retrieve an object and write it to filename
    public void retrieve(String oid, String filename)
					throws HoneycombTestException {

        FileChannel filechannel = null;
        FileOutputStream fileoutputstream = null;

        try {
            fileoutputstream = new FileOutputStream(filename);
            filechannel = fileoutputstream.getChannel();
        } catch(IOException iox) {
            throw new HoneycombTestException("Failed to create temp file: " + 
					     iox.getMessage(), iox);
        }

        try {
            ObjectIdentifier objectidentifier = new ObjectIdentifier(oid);
            archive.retrieveObject(objectidentifier, filechannel);
        } catch(Throwable t) {
            throw new HoneycombTestException(t);
        } finally {
            try {
                filechannel.close();
                fileoutputstream.close();
            } catch (Exception ignore) {}
        }
    }

    // RangeRetrieve an object into a file
    public void rangeRetrieve(String oid, String filename, long offset,
        			long length) throws HoneycombTestException {

        FileChannel filechannel = null;
        FileOutputStream fileoutputstream = null;

        try {
            fileoutputstream = new FileOutputStream(filename);
            filechannel = fileoutputstream.getChannel();
        } catch(IOException iox) {
            throw new HoneycombTestException("Failed to create temp file: " + 
					     iox.getMessage(), iox);
        }

        try {
            ObjectIdentifier objectidentifier = new ObjectIdentifier(oid);
            archive.retrieveObject(objectidentifier, filechannel,
					offset, length);
        } catch(Throwable t) {
            throw new HoneycombTestException(t);
        } finally {
            try {
                filechannel.close();
                fileoutputstream.close();
            } catch (Exception ignore) {}
        }
    }

    // Store a file to Honeycomb
    public String store(String filename)  
					throws HoneycombTestException {

        String oid = null;
        FileChannel filechannel = null;
        FileInputStream fileinputstream = null;
        ObjectIdentifier objectidentifier = null;

        if (debuggingEnabled) {
            System.out.println("Calling store with " + filename);
        }

        try {
            fileinputstream = new FileInputStream(filename);
            filechannel = fileinputstream.getChannel();
        } catch(IOException iox) {
            throw new HoneycombTestException("Failed to open file: " + 
                iox.getMessage(), iox);
        }

        try {
            SystemRecord sr = archive.storeObject(filechannel);

            objectidentifier = sr.getObjectIdentifier();
        } catch(Throwable t) {
            throw new HoneycombTestException(t);
        } finally {
            try {
                filechannel.close();
                fileinputstream.close();
            } catch (Exception ignore) {}
        }
        if (objectidentifier != null) {
            oid = objectidentifier.toString();
        }

        return oid;
    }


    // Store a file and extended metadata to Honeycomb
    public String store(String filename, NameValueRecord nv) 
					throws HoneycombTestException {
        if (nv == null) {
            throw new HoneycombTestException("NameValueRecord is null");
        }

        String oid = null;
        FileChannel filechannel = null;
        FileInputStream fileinputstream = null;
        ObjectIdentifier objectidentifier = null;

        try {
            fileinputstream = new FileInputStream(filename);
            filechannel = fileinputstream.getChannel();
        } catch(IOException iox) {
            throw new HoneycombTestException("Failed to open file: " +
                                             iox.getMessage(), iox);
        }

        try {
            SystemRecord sr = archive.storeObject(filechannel, nv);
            objectidentifier = sr.getObjectIdentifier();
        } catch(Throwable t) {
            throw new HoneycombTestException(t);
        } finally {
            try {
                filechannel.close();
                fileinputstream.close();
            } catch (Exception ignore) {}
        }

        if (objectidentifier != null) {
            oid = objectidentifier.toString();
        }

        return oid;
    }

    public SystemRecord addSimpleMetadata(String oid, NameValueRecord nvr)
    throws HoneycombTestException {
        SystemRecord sr;
        try {
            sr = archive.storeMetadata(new ObjectIdentifier(oid), nvr);
        } catch (Throwable t) {
            throw new HoneycombTestException(t);
        }

        return (sr);
    }

    // creates different oid pointing to same data
    public Object[] addMetadata(String oid, NameValueRecord nvr)
                                        throws HoneycombTestException {
        if (nvr == null) {
            throw new HoneycombTestException("NameValueRecord is null");
        }
        ObjectIdentifier old_oid = new ObjectIdentifier(oid);
        return addMetadata(old_oid, nvr);
    }

    public Object[] addMetadata(ObjectIdentifier oid, NameValueRecord nvr)
					throws HoneycombTestException {
        try {
            Object oo[] = new Object[2];
            // add nvr data to old_nvr
            NameValueRecord old_nvr = archive.retrieveMetadata(oid);
            String[] keys = nvr.getKeys();
            for (int i=0; i<keys.length; i++)
		// -->
                old_nvr.put(keys[i], nvr.getString(keys[i]));
            oo[0] = archive.storeMetadata(oid, old_nvr); // SystemRecord
            oo[1] = old_nvr;
            // archive.delete(oid);
            return oo;
        } catch(Throwable t) {
            throw new HoneycombTestException(t);
        }
    }

    public NameValueRecord retrieveMetadata(String oid) 
                                        throws HoneycombTestException {
        NameValueRecord nvr = null;
        try {
            ObjectIdentifier objectidentifier = new ObjectIdentifier(oid);
            nvr = archive.retrieveMetadata(objectidentifier);
        } catch(Throwable t) {
            throw new HoneycombTestException(t);
        }
        return nvr;
    }

    // Query extended metadata
// ObjectIdentifierList
    public QueryResultSet query(String q, int n) throws HoneycombTestException {

        QueryResultSet qr = null;

        try {
            if (n == USE_DEFAULT_MAX_RESULTS) {
                qr = archive.query(q, maxResults);
            } else {
                qr = archive.query(q, n);
            }
        } catch (Throwable t) {
            throw new HoneycombTestException(t);
        }

        return qr;
    }

    public void delete(String oid) throws HoneycombTestException {
        try {
            archive.delete(new ObjectIdentifier(oid));
        } catch (Throwable t) {
            throw new HoneycombTestException(t);
        }
    }

    // Get the schema
    public NameValueSchema getSchema() throws HoneycombTestException {
        NameValueSchema schema = null;

        try {
            schema = archive.getSchema();
        } catch (Throwable t) {
            throw new HoneycombTestException(t);
        }

        return schema;
    }
    
    public NameValueRecord createRecord(){
	return archive.createRecord();
    }

    // Enable/Disable debugging for this test case
    public void setDebug(boolean b) {
        debuggingEnabled = b;
    }

    // Query for Enable/Disable debugging status for this test case
    public boolean getDebug() {
        return (debuggingEnabled);
    }
}
