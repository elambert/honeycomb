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


package com.sun.honeycomb.client;

import com.sun.honeycomb.common.ArchiveException;
import com.sun.honeycomb.common.NoSuchObjectException;
import com.sun.honeycomb.common.ThreadPropertyContainer;
import com.sun.honeycomb.common.TestRequestParameters;

import com.sun.honeycomb.hctest.Audit;
import com.sun.honeycomb.hctest.util.DigestableReadChannel;
import com.sun.honeycomb.hctest.util.DigestableWriteChannel;
import com.sun.honeycomb.hctest.util.HCUtil;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.util.HCLocale;

import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;
import com.sun.honeycomb.test.util.RandomUtil;
import com.sun.honeycomb.test.TestRunner;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Map;


/**
 ** Provides a pass-through interface to the NameValueObjectArchive
 ** methods.
 */

public class TestNVOA extends NameValueObjectArchive 
{
    private boolean auditEnabled;
    private String auditHost;
    private String cluster;

    public TestNVOA(String address)
        throws ArchiveException, 
               IOException
    {
        super(address);
        init();
    }

    public TestNVOA(String address, 
                int port)
        throws ArchiveException, 
               IOException
    {
        super(address, port);
        init();
    }

    protected Connection newConnection(String address, int port)
        throws ArchiveException, IOException
    {
        return new TestConnection(address, port);
    }

    // Visibility is public for the RMI Server syncing of common properties.
    public void init()
    {
        Log.DEBUG("TestNVOA::init()");
        
        this.auditHost = System.getProperty(HCLocale.PROPERTY_DBHOST);
        this.cluster = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_CLUSTER);
                
        if ((this.auditHost == null) || (this.cluster == null)) {
            this.auditEnabled = false;
            Log.INFO("TestNVOA:: Logging not enabled (set property cluster inorder to fix this) You SHOULD fix this!!!");
        } else {
            this.auditEnabled = true;
        }
    }

    // Some tests pass illegal input and want the ability to
    // avoid tripping up the audit framework while still 
    // leveraging the test client code, so we provide the 
    // ability to disable audit
    public void disableAudit() {
        auditEnabled = false;
    }

    public SystemRecord storeObject(ReadableByteChannel data)
        throws ArchiveException, 
               IOException
    {
        SystemRecord systemRecord = null;
        Throwable caught =  null;
        DigestableReadChannel drc = new DigestableReadChannel(data);   
        long t0 = 0;
        long t1 = 0;
        long api_time = 0;
        int status = 2;
        String sha1 = null;
        Long numBytes = null;
        String info = null;

        setLogTag();
        //String logtag = ThreadPropertyContainer.getLogTag();        
        String logtag = TestRequestParameters.getLogTag(); 
        Log.INFO("TestNVOA::storeObject() [" + logtag + "] starting.");
        
        setApiTime(-1);
        setHash(null);
        try {
            t0 = System.currentTimeMillis();
            systemRecord = super.storeObject(drc);
            t1 = System.currentTimeMillis();
            status = 0;
            sha1 = HCUtil.convertHashBytesToString(drc.digest());
            setHash(sha1);
            numBytes = new Long(drc.getNumBytes());
            Log.INFO("TestNVOA::storeObject() sha1(" + sha1 + ") numBytes(" + numBytes + ") tag [" + logtag + "] oid " + systemRecord.getObjectIdentifier());
        } catch (Throwable t) {
            t1 = System.currentTimeMillis();
            caught = t;
            status = 1;
            info = Log.stackTrace(t);
            
        }
        api_time = t1 - t0;
        // api_time -= drc.getDigestTime();  // not valid
        setApiTime(api_time);
        
        unSetLogTag();
        
        if (status == 1)
        	Log.ERROR("TestNVOA::storedObject() [" + logtag + "] request failed.");
        else 
        	Log.INFO("TestNVOA::storedObject() [" + logtag + "] request succeeded.");
        
        if (this.auditEnabled) {
            try {
                Audit audit = Audit.getInstance(this.auditHost, this.cluster);
                audit.recordStoreOp(status,
                                    t0,
                                    new Long(t1),
                                    api_time,
                                    info,
                                    sha1,
                                    null, // metadata
                                    numBytes,
                                    logtag,
                                    systemRecord);
            }
            catch (Throwable t) {
                Log.ERROR("audit failed");
                Log.ERROR(Log.stackTrace(t));
            }
        }

        if (caught != null) {
            if (caught instanceof ArchiveException) {
                throw (ArchiveException) caught;
            } else if (caught instanceof IOException) {
                throw (IOException) caught;
            } else {
                Log.ERROR("TestNVOA caught unexpected exception: " + caught);
                throw new RuntimeException(caught);
            }
        }

        return systemRecord;
    }

    public static String getHostname(){
    	try {
            InetAddress addr = InetAddress.getLocalHost();
        
            // Get IP Address
            byte[] ipAddr = addr.getAddress();
        
            // Get hostname
            String hostname = addr.getHostName();
            return hostname;
        } catch (UnknownHostException e) {
        	return "unresolved hostname";
        }
    }

    public void unSetLogTag() {
    	TestRequestParameters.setLogTag(null);
    }
    public void setApiTime(long time) {
        TestRequestParameters.setTime(time);
    }
    public void setHash(String s) {
        TestRequestParameters.setHash(s);
    }
    
    public void setLogTag() {
    	String name = Thread.currentThread().getName(); 
    	
    	if (name.matches("RMI TCP Connection(.*)")){
    		name = name.substring(name.indexOf("(")+1,name.indexOf(")"));
    	} else if (name.matches("Thread-.*")) {
    		name = name.substring(name.indexOf("-"),name.length());
    	}

        TestRequestParameters.setLogTag(getHostname() + ":" + name + ":" + System.currentTimeMillis());
        TestRequestParameters.setLastLogTag(TestRequestParameters.getLogTag());
    }
        
    public SystemRecord storeObject(ReadableByteChannel data,
                                    NameValueRecord metadata)
        throws ArchiveException, 
               IOException
    {
        SystemRecord systemRecord = null;
        Throwable caught =  null;
        DigestableReadChannel drc = new DigestableReadChannel(data);   
        long t0 = 0;
        long t1 = 0;
        long api_time = 0;
        int status = 2;
        String sha1 = null;
        Long numBytes = null;
        String info = null;

        setLogTag();
        //String logtag = ThreadPropertyContainer.getLogTag(); 
        String logtag = TestRequestParameters.getLogTag(); 
        Log.INFO("TestNVOA::storeObject() [" + logtag + "] starting.");

        setApiTime(-1);
        setHash(null);
        try {
            t0 = System.currentTimeMillis();
            systemRecord = super.storeObject(drc, metadata);
            t1 = System.currentTimeMillis();            
            status = 0;
            sha1 = HCUtil.convertHashBytesToString(drc.digest());
            setHash(sha1);
            numBytes = new Long(drc.getNumBytes());
            Log.INFO("TestNVOA::storeObject() sha1(" + sha1 + ") numBytes(" + numBytes + ") tag [" + logtag + "] oid " + systemRecord.getObjectIdentifier());
        } catch (Throwable t) {
            t1 = System.currentTimeMillis();
            caught = t;
            status = 1;
            info = Log.stackTrace(t);
        }
        api_time = t1 - t0;
        // api_time -= drc.getDigestTime(); // not valid
        setApiTime(api_time);

        unSetLogTag();
        
        if (status == 1)
        	Log.ERROR("TestNVOA::storedObject() [" + logtag + "] failed.");
        else 
        	Log.INFO("TestNVOA::storedObject() [" + logtag + "] request succeeded.");

        if (this.auditEnabled) {
            try {
                Audit audit = Audit.getInstance(this.auditHost, this.cluster);
                audit.recordStoreOp(status,
                                    t0,
                                    new Long(t1),
                                    api_time,
                                    info,
                                    sha1,
                                    metadata,
                                    numBytes,
                                    logtag,
                                    systemRecord);
            }
            catch (Throwable t) {
                Log.ERROR("audit failed");
                Log.ERROR(Log.stackTrace(t));
            }
        }

        if (caught != null) {
            if (caught instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) caught;
            } else if (caught instanceof ArchiveException) {
                throw (ArchiveException) caught;
            } else if (caught instanceof IOException) {
                throw (IOException) caught;
            } else {
                Log.ERROR("TestNVOA caught unexpected exception: " + caught);
                throw new RuntimeException(caught);
            }
        }

        return systemRecord;
    }

    public SystemRecord storeMetadata(ObjectIdentifier link,
                                      NameValueRecord metadata)
        throws ArchiveException, 
               IOException
    {
        SystemRecord systemRecord = null;
        Throwable caught =  null;
        long t0 = 0;
        long t1 = 0;
        int status = 2;
        Long numBytes = null;
        String info = null;

        setLogTag();
        //String logtag = ThreadPropertyContainer.getLogTag(); 
        String logtag = TestRequestParameters.getLogTag(); 
        Log.INFO("TestNVOA::storeMetadata() [" + logtag + "] request starting for OID: " + link.toString());

        setApiTime(-1);
        setHash(null);
        try {
            t0 = System.currentTimeMillis();
            systemRecord = super.storeMetadata(link, metadata);
            t1 = System.currentTimeMillis();
            status = 0;
        } catch (Throwable t) {
            t1 = System.currentTimeMillis();
            caught = t;
            status = 1;
            info = Log.stackTrace(t);
        }
        setApiTime(t1 - t0);
        
        unSetLogTag();
        
        if (status == 1)
        	Log.ERROR("TestNVOA::storeMetadata() [" + logtag + "] failed for OID: " + link.toString());
        else 
        	Log.INFO("TestNVOA::storeMetadata() [" + logtag + "] request succeeded.");
        
        if (this.auditEnabled) {
            try {
                Audit audit = Audit.getInstance(this.auditHost, this.cluster);
                audit.recordLinkOp(status,
                                   t0,
                                   new Long(t1),
                                   t1-t0,
                                   info,
                                   link,
                                   metadata,
                                   logtag,
                                   systemRecord);
            }
            catch (Throwable t) {
                Log.ERROR("audit failed");
                Log.ERROR(Log.stackTrace(t));
            }
        }

        if (caught != null) {
            if (caught instanceof IllegalArgumentException) {
	        throw (IllegalArgumentException) caught;
            } else if (caught instanceof ArchiveException) {
                throw (ArchiveException) caught;
            } else if (caught instanceof IOException) {
                throw (IOException) caught;
            } else {
                Log.ERROR("TestNVOA caught unexpected exception: " + caught);
                throw new RuntimeException(caught);
            }
        }

        return systemRecord;
    }

    public long retrieveObject(ObjectIdentifier oid,
                               WritableByteChannel data)
        throws ArchiveException, 
               IOException
    {
        Long numBytes = null;
        Throwable caught = null;
        DigestableWriteChannel dwc = new DigestableWriteChannel(data);
        String sha1 = null;
        long t0 = 0;
        long t1 = 0;
        long api_time = 0;
        int status = 2;
        String info = null;

        setLogTag();
        //String logtag = ThreadPropertyContainer.getLogTag(); 
        String logtag = TestRequestParameters.getLogTag(); 
        Log.INFO("TestNVOA::retrieveObject() [" + logtag + "] starting for OID: " + oid.toString());
        
        setApiTime(-1);
        setHash(null);
        try {
            t0 = System.currentTimeMillis();
            numBytes = new Long(super.retrieveObject(oid, dwc));
            t1 = System.currentTimeMillis();
            status = 0;
            sha1 = HCUtil.convertHashBytesToString(dwc.digest());
            setHash(sha1);
        } catch (Throwable t) {
            t1 = System.currentTimeMillis();
            caught = t;
            status = 1;
            info = Log.stackTrace(t);
        }
        api_time = t1 - t0;
        // api_time -= dwc.getDigestTime();  // not valid
        setApiTime(api_time);

        unSetLogTag();
        
        if (status == 1)
        	Log.ERROR("TestNVOA::retrieveObject() [" + logtag + "] failed for OID: " + oid.toString());
        else 
        	Log.INFO("TestNVOA::retrieveObject() [" + logtag + "] request succeeded.");
        
        if (this.auditEnabled) {
            try {
                Audit audit = Audit.getInstance(this.auditHost, this.cluster);
                audit.recordRetrieveOp(status,
                                       t0,
                                       new Long(t1),
                                       api_time,
                                       info,
                                       oid,
                                       sha1,
                                       numBytes,
                                       null,
                                       null,
                                       logtag);
            }
            catch (Throwable t) {
                Log.ERROR("audit failed");
                Log.ERROR(Log.stackTrace(t));
            }
        }

        if (caught != null) {
            if (caught instanceof ArchiveException) {
                throw (ArchiveException) caught;
            } else if (caught instanceof IOException) {
                throw (IOException) caught;
            } else {
                Log.ERROR("TestNVOA caught unexpected exception: " + caught);
                throw new RuntimeException(caught);
            }
        }

        return numBytes.longValue();
    }

    public void delete(ObjectIdentifier oid)
        throws ArchiveException,
               IOException
    {
        Throwable caught = null;
        long t0 = 0;
        long t1 = 0;
        int status = 2;
        String info = null;
        boolean noSuchObjectException = false;

        setLogTag();
        //String logtag = ThreadPropertyContainer.getLogTag(); 
        String logtag = TestRequestParameters.getLogTag(); 
        Log.INFO("TestNVOA::delete() [" + logtag + "] request starting for OID: " + oid.toString());
        
        setApiTime(-1);
        try {
            t0 = System.currentTimeMillis();
            super.delete(oid);
            t1 = System.currentTimeMillis();
            status = 0;
        } catch( NoSuchObjectException e ){
        	t1 = System.currentTimeMillis();
            caught = e;
            status = 1;
            info = Log.stackTrace(e);
            noSuchObjectException = true;
        } catch (Throwable t) {
            t1 = System.currentTimeMillis();
            caught = t;
            status = 1;
            info = Log.stackTrace(t);
        }
        setApiTime(t1-t0);
        
        unSetLogTag();
        
        if (status == 1)
        	Log.ERROR("TestNVOA::delete() [" + logtag + "] request failed for OID: " + oid.toString());
        else 
        	Log.INFO("TestNVOA::delete() [" + logtag + "] request succeeded for OID: " + oid.toString());
        
        if (this.auditEnabled) {
            try {
                Audit audit = Audit.getInstance(this.auditHost, this.cluster);
                audit.recordDeleteOp(status,
                                     t0,
                                     new Long(t1),
                                     t1-t0,
                                     info,
                                     oid,
                                     logtag,
                                     noSuchObjectException);
            }
            catch (Throwable t) {
                Log.ERROR("audit failed");
                Log.ERROR(Log.stackTrace(t));
            }
        }

        if (caught != null) {
            if (caught instanceof ArchiveException) {
                throw (ArchiveException) caught;
            } else if (caught instanceof IOException) {
                throw (IOException) caught;
            } else {
                Log.ERROR("TestNVOA caught unexpected exception: " + caught);
                throw new RuntimeException(caught);
            }
        }
    }

    public QueryResultSet query(String query, int n)
        throws ArchiveException,
               IOException
    {
    	  Long numBytes = null;
          Throwable caught = null;
          long t0 = 0;
          long t1 = 0;
          long api_time = 0;
          int status = 2;
          String info = null;
          QueryResultSet qrs = null;
          
          setLogTag();
          //String logtag = ThreadPropertyContainer.getLogTag(); 
          String logtag = TestRequestParameters.getLogTag(); 
          Log.INFO("TestNVOA::queryMetadata() [" + logtag + "] query: " + query);
          
          setApiTime(-1);
          setHash(null);
          try {
              setApiTime(-1);
              t0 = System.currentTimeMillis();
              qrs = super.query(query, n);
              t1 = System.currentTimeMillis();
              setApiTime(t1-t0);
          } catch (Throwable t) {
              t1 = System.currentTimeMillis();
              caught = t;
              status = 1;
              info = Log.stackTrace(t);
          }
          api_time = t1 - t0;
          // api_time -= dwc.getDigestTime();  // not valid
          setApiTime(api_time);

          unSetLogTag();
          
          if (status == 1)
          	Log.ERROR("TestNVOA::queryMetadata() [" + logtag + "] failed for query: " + query);
          else 
          	Log.INFO("TestNVOA::queryMetadata() [" + logtag + "] request succeeded.");
          
          if (this.auditEnabled) {
              try {
                  Audit audit = Audit.getInstance(this.auditHost, this.cluster);
                  audit.recordQueryOp(status,
                                         t0,
                                         new Long(t1),
                                         api_time,
                                         query,
                                         logtag);
              }
              catch (Throwable t) {
                  Log.ERROR("audit failed");
                  Log.ERROR(Log.stackTrace(t));
              }
          }

          if (caught != null) {
              if (caught instanceof ArchiveException) {
                  throw (ArchiveException) caught;
              } else if (caught instanceof IOException) {
                  throw (IOException) caught;
              } else {
                Log.ERROR("TestNVOA caught unexpected exception: " + caught);
                throw new RuntimeException(caught);
              }
          }

          return qrs;
    }

    public NameValueRecord retrieveMetadata(ObjectIdentifier oid)
        throws ArchiveException,
               IOException
    {
        Long numBytes = null;
        Throwable caught = null;
        long t0 = 0;
        long t1 = 0;
        long api_time = 0;
        int status = 2;
        String info = null;
        NameValueRecord nvr = null;
        
        setLogTag();
        //String logtag = ThreadPropertyContainer.getLogTag(); 
        String logtag = TestRequestParameters.getLogTag(); 
        Log.INFO("TestNVOA::retrieveMetadata() [" + logtag + "] starting for OID: " + oid.toString());
        
        setApiTime(-1);
        setHash(null);
        try {
            t0 = System.currentTimeMillis();
            nvr = super.retrieveMetadata(oid);
            t1 = System.currentTimeMillis();
            status = 0;
        } catch (Throwable t) {
            t1 = System.currentTimeMillis();
            caught = t;
            status = 1;
            info = Log.stackTrace(t);
        }
        api_time = t1 - t0;
        // api_time -= dwc.getDigestTime();  // not valid
        setApiTime(api_time);

        unSetLogTag();
        
        if (status == 1)
        	Log.ERROR("TestNVOA::retrieveMetadata() [" + logtag + "] failed for OID: " + oid.toString());
        else 
        	Log.INFO("TestNVOA::retrieveMetadata() [" + logtag + "] request succeeded.");
        
        if (this.auditEnabled) {
            try {
                Audit audit = Audit.getInstance(this.auditHost, this.cluster);
                audit.recordRetrieveMDOp(status,
                                       t0,
                                       new Long(t1),
                                       api_time,
                                       info,
                                       oid,
                                       numBytes,
                                       null,
                                       null,
                                       logtag);
            }
            catch (Throwable t) {
                Log.ERROR("audit failed");
                Log.ERROR(Log.stackTrace(t));
            }
        }

        if (caught != null) {
            if (caught instanceof ArchiveException) {
                throw (ArchiveException) caught;
            } else if (caught instanceof IOException) {
                throw (IOException) caught;
            } else {
                Log.ERROR("TestNVOA caught unexpected exception: " + caught);
                throw new RuntimeException(caught);
            }
        }

        return nvr;        
    }
}
