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



package com.sun.honeycomb.hctest.cases.api.client;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.MetadataRecord;
import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.NameValueSchema;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.client.TestNVOA;
import com.sun.honeycomb.common.ArchiveException;

import com.sun.honeycomb.hctest.rmi.auditsrv.clnt.AuditSrvClnt;
import com.sun.honeycomb.hctest.util.HCTestReadableByteChannel;
import com.sun.honeycomb.hctest.util.HoneycombTestClient;
import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.hctest.*;

import com.sun.honeycomb.test.Metric;
import com.sun.honeycomb.test.Suite;
import com.sun.honeycomb.test.Tag;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.matrix.Domain;
import com.sun.honeycomb.test.matrix.Matrix;
import com.sun.honeycomb.test.matrix.SimpleDomain;
import com.sun.honeycomb.test.util.Log;
import java.io.File;
import java.io.FileOutputStream;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * This class is a catch-all testsuite used to patch the coverage holes for
 * NameValueObjectArchive class left by the regression tests.
 */
public class NameValueObjectArchiveTest extends HoneycombSuite {
    private String dataVIP;
    
    public void setUp() throws Throwable {
        dataVIP = testBed.dataVIPaddr;
        if (dataVIP == null) {
            TestCase c = new TestCase(this, "NameValueObjectArchive tests", 
                                      "setup");
            c.testFailed(HoneycombTestConstants.PROPERTY_CLUSTER + " or " +
                         HoneycombTestConstants.PROPERTY_DATA_VIP +
                         " needs to be set");
        }
    }
    
    public void runTests() {
        try {
            testConstructor();
            testStoreObject();
            testWriteRecord();
        } catch (Throwable t) {
            Log.ERROR("unexpected exception thrown within NameValueObjectArchiveTest::runTests()");
            Log.ERROR(Log.stackTrace(t));
        }
    }
    
    
    
    /**
     * NameValueObjectArchive(null) - XFAIL
     * NameValueObjectArchive("validhost") - XPASS
     */
    public void testConstructor() throws Throwable {

        if (dataVIP == null)
            return;

        // Setup matrix of test cases.
        Matrix m = new Matrix();
        m.add("address", new SimpleDomain(new Object [] {null, dataVIP}));
        
        // Iterate through test cases
        m.reset();
        while (m.hasNext()) {
            HashMap perm = m.next();
            
            String address = (String) perm.get("address");
            
            TestCase c = new TestCase(this, "NameValueObjectArchive(address)", "address=" + address);
	    c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
            if (!c.excludeCase()) {
                boolean result = false;
                String notes = "";
                Throwable t = null;
                try {
                    NameValueObjectArchive archive = new TestNVOA(address);
                } catch (Throwable caught) {
                    t = caught;
                }
                
                // analyze.  post result
                if (address == null) {
                    if (t == null) {
                        result = false;
                        String msg = "A suitable exception should be thrown if the address specified is null.\n";
                        notes += msg;
                        Log.ERROR(notes);
                    } else if (t instanceof IllegalArgumentException ||
                            t instanceof NullPointerException) {
                        result = true;
                        String msg = "The correct exception was thrown.\n";
                        notes += msg;
                        Log.INFO(notes);
                    } else {
                        result = false;
                        String msg = "An unexpected Throwable was caught.\n";
                        notes += msg;
                        notes += Log.stackTrace(t);
                        Log.ERROR(notes);
                    }
                } else { // address isn't null
                    if (t != null) {
                        result = false;
                        String msg = "An unexpected Throwable was caught.\n";
                        notes += msg;
                        notes += Log.stackTrace(t);
                        Log.ERROR(notes);
                    } else {
                        result = true;
                    }
                }
                c.postResult(result, notes);
            }
        }
    }
    
    /**
     * This method tests NVOA.writeRecord(MetaDataRecord, OutputStream);
     */
    public void testWriteRecord() throws Throwable {
        if (dataVIP == null)
            return;

        NameValueObjectArchive nvoa = null;
        
        //** negative tests cases **
        
        // writeRecord (null, null): XFAIL (throws NPE)
        TestCase c = new TestCase(this,"NameValueObjectArchive.writeRecord(MetadataRecord,OutputStream)",
                "metadataRecord = null && OutputStream == null");
        c.addTag(new String[] {Tag.REGRESSION,Tag.NEGATIVE, Tag.QUICK, Tag.SMOKE,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
        if (!c.excludeCase()) {
	    nvoa = new TestNVOA(this.dataVIP);
            Throwable t = null;
	    boolean result = true;
	    String notes = null;
	    String msg = null;
            try {
                nvoa.writeRecord(null, null);
            } catch (Throwable thrown) {
                t = thrown;
            }
            if (t == null) {
                result = false;
                notes = "A suitable exception should be thrown if the metadataRecord or Outputsteam is null.\n";
                Log.ERROR(notes);
            } else if ( !(t instanceof NullPointerException) && !(t instanceof ArchiveException)) {
                result = false;
                notes = "An unexpected throwable was caught.\n";
                notes += Log.stackTrace(t);
                Log.ERROR(notes);
            }
            c.postResult(result, notes);
        }
        
        // writeRecord (MDR, null): XFAIL (throws NPE)
        c = new TestCase(this,"NameValueObjectArchive.writeRecord(MetadataRecord,OutputStream)",
                "metadataRecord = emptyMDR && OutputStream == null");
        c.addTag(new String[] {Tag.REGRESSION,Tag.NEGATIVE, Tag.QUICK, Tag.SMOKE,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
        if (!c.excludeCase()) {
	    boolean result = true;
	    String notes = null;
	    String msg = null;
	    nvoa= new TestNVOA(this.dataVIP);
	    NameValueRecord emptyMDR = nvoa.createRecord();
	    File tmpFile = File.createTempFile("nvoaTestWriteRecord","tmp");
	    OutputStream validOS = new FileOutputStream(tmpFile);
            Throwable t = null;
            try {
                nvoa.writeRecord(emptyMDR, null);
            } catch (Throwable thrown) {
                t = thrown;
            }
            if (t == null) {
                result = false;
                notes += "A suitable exception should be thrown if the Outputsteam is null.\n";
                Log.ERROR(notes);
            } else if ( !(t instanceof NullPointerException) && !(t instanceof ArchiveException)) {
                result = false;
                notes += "An unexpected throwable was caught.\n";
                notes += Log.stackTrace(t);
                Log.ERROR(notes);
            }
            c.postResult(result, notes);
        }
        
        // writeRecord (null, validOS): XFAIL
        c = new TestCase(this,"NameValueObjectArchive.writeRecord(MetadataRecord,OutputStream)",
                "metadataRecord = null && OutputStream == valid File outputstream");
        c.addTag(new String[] {Tag.REGRESSION,Tag.NEGATIVE, Tag.QUICK, Tag.SMOKE,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
        if (!c.excludeCase()) {
	    nvoa = new TestNVOA(this.dataVIP);
	    boolean result = true;
	    String notes = null;
	    String msg = null;
            File myFile = File.createTempFile("validOS","tmp");
            FileOutputStream validOS = new FileOutputStream(myFile);
            Throwable t = null;
            try {
                nvoa.writeRecord(null, validOS);
            } catch (Throwable thrown) {
                t = thrown;
            }
            if (t == null) {
                result = false;
                notes += "A suitable exception should be thrown if the MetaDataRecord is null.\n";
                Log.ERROR(notes);
            } else if ( !(t instanceof NullPointerException) && !(t instanceof ArchiveException)) {
                result = false;
                notes += "An unexpected throwable was caught.\n";
                notes += Log.stackTrace(t);
                Log.ERROR(notes);
            }
            c.postResult(result, notes);
        }

       //test writing an emptyRecord: XPASS 
        c = new TestCase(this, "NameValueObjectArchive.writeRecord(MetadataRecord,OutputStream)", "emptyMDR");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    nvoa = new TestNVOA(dataVIP);
	    boolean result= writeMDRRecordToFile(nvoa, nvoa.createRecord());
	    c.postResult(result, result ? "" : "failed to write an empty NameValueRecord");
	}

       //test writing a simple Record: XPASS
        c = new TestCase(this, "NameValueObjectArchive.writeRecord(MetadataRecord,OutputStream)", "simpleMDR");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    nvoa = new TestNVOA(dataVIP);
	    NameValueRecord simpleRecord = nvoa.createRecord();
	    HoneycombTestClient.fillNVR(simpleRecord, nvoa,1);
	    boolean result= writeMDRRecordToFile(nvoa, simpleRecord);
	    c.postResult(result, result ? "" : "failed to write a simple NameValueRecord");
	}

       //test writing a less simple Record: XPASS
        c = new TestCase(this, "NameValueObjectArchive.writeRecord(MetadataRecord,OutputStream)", "multEntryMDR");
	c.addTag(new String [] {Tag.POSITIVE, Tag.REGRESSION, Tag.SMOKE, Tag.QUICK,
                            HoneycombTag.EMULATOR, HoneycombTag.JAVA_API});
	if (!c.excludeCase()) {
	    nvoa = new TestNVOA(dataVIP);
	    NameValueRecord multiEntryRecord = nvoa.createRecord();
	    HoneycombTestClient.fillNVR(multiEntryRecord,nvoa, 0);
	    boolean result= writeMDRRecordToFile(nvoa, multiEntryRecord);
	    c.postResult(result, result ? "" : "failed to write a multi-Entry NameValueRecord");
	}
    }

    public void testStoreObject() throws Throwable {
        if (dataVIP == null)
            return;

        // Setup matrix of test cases.
        // (4096 is the buffer size used in the Apache client.  TODO: reference the
        // constant directly in the code.)
        Matrix m = new Matrix();
        m.add("size", new SimpleDomain(new Object [] {
            null, new Long(0), new Long(1), new Long(2), new Long(3),
                    new Long(1023), new Long(1024), new Long(1025),
                    new Long(4095), new Long(4096), new Long(4097),
                    new Long(10*1024), new Long(64*1024), new Long(100*1024),
                    new Long(1024*1024), new Long(10*1024*1024), new Long(100*1024*1024)/*,
            new Long(1024*1024*1024), new Long(10*1024*1024*1024)*/ // this ended up being a negative size.
        }));
        
        // Iterate through test cases.
        //NameValueObjectArchive archive = new TestNVOA(this.dataVIP);
        m.reset();
        while (m.hasNext()) {
            HashMap perm = m.next();
            Long size = (Long) perm.get("size");
            
            TestCase c = new TestCase(this, "NameValueObjectArchive::storeObject(size)", "size=" + size);
            c.addTag(Tag.REGRESSION);
            if (size == null) {
                c.addTag(Tag.SMOKE);
                c.addTag(Tag.QUICK);
                c.addTag(Tag.NEGATIVE);
                c.addTag(HoneycombTag.EMULATOR);
                c.addTag(HoneycombTag.JAVA_API);
            } else {
                c.addTag(Tag.POSITIVE);
                if (size.longValue() < 1024*1024*1024) {
                    c.addTag(Tag.SMOKE);
                    c.addTag(Tag.QUICK);
                    c.addTag(HoneycombTag.EMULATOR);
                    c.addTag(HoneycombTag.JAVA_API);
                } else {
                    c.addTag(Tag.HOURLONG);
                }
            }
            if (!c.excludeCase()) {
		NameValueObjectArchive archive = new TestNVOA(this.dataVIP);
                Log.INFO("storing file: " + size + " Bytes");
                boolean result = false;
                String notes = "";
                Throwable caught = null;
                try {
                    HCTestReadableByteChannel data = null;
                    if (size != null) {
                        data = new HCTestReadableByteChannel(size.longValue());
                    }
                    ArrayList metrics = new ArrayList();
                    metrics.add(new Metric("sizeBytes", Metric.TYPE_DOUBLE, size.longValue()));
                    SystemRecord sr =
                            //HoneycombTestClient.storeObject(archive, data, metrics);
                            HoneycombTestClient.storeObject(archive, data, c);
                    //Iterator i = metrics.iterator();
                    //while (i.hasNext()) {
                    //c.postMetric((Metric) i.next());
                    //}
                } catch (Throwable t) {
                    caught = t;
                }
                
                // Evaluate result
                if (size == null) {
                    if (caught != null) {
                        if (caught instanceof IllegalArgumentException ||
                                caught instanceof NullPointerException) {
                            result = true;
                            String msg = "The correct exception was thrown\n";
                            notes += msg;
                            Log.INFO(notes);
                        } else {
                            result = false;
                            String msg = "An unexpected Throwable was caught.\n";
                            notes += msg;
                            notes += Log.stackTrace(caught);
                            Log.ERROR(notes);
                        }
                    } else {
                        result = false;
                        String msg = "An IllegalArgumentException should be thrown for 'null' argument.\n";
                        notes += msg;
                        Log.ERROR(notes);
                    }
                } else {
                    if (caught != null) {
                        result = false;
                        String msg = "An unexpected Throwable was caught.\n";
                        notes += msg;
                        notes += Log.stackTrace(caught);
                        Log.ERROR(notes);
                    } else {
                        result = true;
                    }
                }
                c.postResult(result, notes);
            }
        }
    }

// worker methods

    private boolean writeMDRRecordToFile(NameValueObjectArchive nvoa, MetadataRecord mdr) {
		File myFile = null;
		OutputStream myOS = null;

		try {
		    myFile = File.createTempFile("nvoaWriteRecord","tmp");
		    myOS = new FileOutputStream(myFile);
		}
		catch (IOException ioe) {
		    Log.stackTrace(ioe);
		    Log.ERROR("Unable to setup needed file stream! doh!!");
		    return false;
		}

		boolean result = false;
		try {
                    nvoa.writeRecord(mdr, myOS);
		    result = true;
		} 
		catch (Throwable t){
		    result = false;
		    Log.stackTrace(t);
		    Log.ERROR("An unexpected throwable was caught!");
		}
		finally {
		    try {
			myOS.close();
		    }
		    catch (IOException ioe) {
			Log.stackTrace(ioe);
			Log.WARN("Unable to cleanup after myself. Could not close output stream for file: " + myFile);
		    }
		    if (myFile.exists() && !myFile.delete()) {
			Log.WARN("Unable to cleanup after myself. Could not delete file: " + myFile);
		    }
		    return result;
		}

    }
    
    /*
        currentTest = createTestCase(m.getName());
        boolean retval = false;
        String notes = null;
     
        try {
            Object o = m.invoke(testObject, null);
            retval = ((Boolean) o).booleanValue();
        } catch (Throwable t) {
            if (t instanceof InvocationTargetException) {
                //  "InvocationTargetException is a checked exception
                //  that wraps an exception thrown by an invoked method
                //  or constructor."
                t = t.getCause();
                }
            Log.ERROR("ERROR: Test threw an exception, assuming failure");
            notes = Result.logTrace(t);
        }
        if (!currentTest.skipped()) // if skipped, ignore retval
            currentTest.postResult(retval, notes); // post pass/fail result to QB
     */

    /*
      public boolean testNameValueObjectArchiveA_2() throws Throwable
      {
      // Should this generate an exception?
      boolean result = false;
      try {
      NameValueObjectArchive archive = new TestNVOA("");
      } catch (Throwable t) {
      result = true;
      }
      return result;
    }
     
    public boolean testNameValueObjectArchiveA_3() throws Throwable
    {
        // Should this generate an exception?
        boolean result = false;
        try {
            NameValueObjectArchive archive = new TestNVOA(null);
        } catch (Throwable t) {
            result = true;
        }
        return result;
    }
     
    public boolean testNameValueObjectArchiveA_4() throws Throwable
    {
        // Should this generate an exception?
        boolean result = false;
        try {
            NameValueObjectArchive archive = new TestNVOA("102.442.234.34.3505");
        } catch (Throwable t) {
            result = true;
        }
        return result;
    }
     
    public boolean testNameValueObjectArchiveB_1() throws Throwable
    {
        NameValueObjectArchive archive = new TestNVOA(dataVIP, 80);
        return true;
    }
     
    public boolean testNameValueObjectArchive_GetID_1() throws Throwable
    {
        NameValueObjectArchive archive = new TestNVOA(dataVIP);
        String id = archive.getID();
        Log.INFO("id = " + id);
    }
     
     */
    
    /*
     
    public boolean testNameValueObjectArchive_StoreObjectA_1() throws Throwable
    {
        HCTestReadableByteChannel data = new HCTestReadableByteChannel(0);
        NameValueObjectArchive archive = new TestNVOA(dataVIP);
        SystemRecord sr = archive.storeObject(data);
    }
     
    public boolean testNameValueObjectArchive_StoreObjectA_2() throws Throwable
    {
        boolean result = false;
        NameValueObjectArchive archive = new TestNVOA(dataVIP);
        try {
            SystemRecord r = archive.storeObject(null);
        }
        catch (NullPointerException npe) {
            result = true;
	    }
        return result;
    }
     
    public boolean testNameValueObjectArchive_StoreObjectB_1() throws Throwable
    {
        NameValueObjectArchive a = new TestNVOA(dataVIP);
        NameValueRecord nvr = new NameValueRecord();
        SystemRecord r = archive.storeObject(new HCTestReadableByteChannel(0), nvr);
        return true;
    }
     
    public boolean testNameValueObjectArchive_StoreObjectB_1() throws Throwable
    {
        boolean result = false;
        NameValueObjectArchive a = new TestNVOA(dataVIP);
        try {
            SystemRecord r = archive.storeObject(new HCTestReadableByteChannel(0), null);
        }
        catch (NullPointerException npe) {
            result = true;
        }
        return result;
    }
     
    public boolean testNameValueObjectArchive_StoreObjectB_1() throws Throwable
    {
        boolean result = false;
        NameValueObjectArchive a = new TestNVOA(dataVIP);
        try {
            SystemRecord r = archive.storeObject(null, null);
        }
        catch (NullPointerException npe) {
            result = true;
        }
        return result;
    }
     */
    
    /*
      Log.INFO("storing file: 0");
      archive.storeObject(new HCTestReadableByteChannel(0));
      Log.INFO("storing file: 1");
      archive.storeObject(new HCTestReadableByteChannel(1));
      Log.INFO("storing file: 2");
      archive.storeObject(new HCTestReadableByteChannel(2));
      Log.INFO("storing file: 4047");
      archive.storeObject(new HCTestReadableByteChannel(4047));
      Log.INFO("storing file: 4048");
      archive.storeObject(new HCTestReadableByteChannel(4048));
      Log.INFO("storing file: 4049");
      archive.storeObject(new HCTestReadableByteChannel(4049));
      Log.INFO("storing file: 4050");
      archive.storeObject(new HCTestReadableByteChannel(4050));
     */
}
