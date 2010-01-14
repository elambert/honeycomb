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


package com.sun.honeycomb.hctest.util;

import com.sun.honeycomb.hctest.Audit;
import com.sun.honeycomb.hctest.CmdResult;

import com.sun.honeycomb.hctest.rmi.auditsrv.clnt.*;

import com.sun.honeycomb.test.Metric;
import com.sun.honeycomb.test.TestCase;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.*;

import com.sun.honeycomb.common.*;
import com.sun.honeycomb.client.*;

import org.apache.commons.httpclient.HttpRecoverableException;

import java.io.*;
import java.lang.*;
import java.util.*;
import java.util.logging.Logger;
import java.security.NoSuchAlgorithmException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import java.sql.Time;
import java.sql.Timestamp;

/**
 *  Honeycomb API Client Library wrapper. 
 *  Methods typically return CmdResult. API Exceptions either thrown
 *  wrapped in HoneycombTestException or returned in CmdResult.
 *  Logging to audit server: MEAS lines parsed by util/MeasureLog.java
 */

public class HoneycombTestClient {

    private static final int MAX_RETRIES = 5;

    private static final Logger LOG =
        Logger.getLogger(HoneycombTestClient.class.getName());

    private int initSleep = 5000;

    private String dataVIP = null;
    private int dataPort = HoneycombTestConstants.DEFAULT_DATA_PORT;

    private TestNVOA archive = null;
    private ObjectArchive advancedArchive = null;
    private int connectTimeoutMsecs =
        HoneycombTestConstants.DEFAULT_CONNECT_TIMEOUT;
    private int socketTimeoutMsecs =
        HoneycombTestConstants.DEFAULT_SOCKET_TIMEOUT;

    private boolean throw_api = true;  // true == legacy behavior

    String auditIP = TestRunner.getProperty(
                                      HoneycombTestConstants.PROPERTY_AUDIT_IP);

    AuditSrvClnt ac = null;

    // Compliance retention time constants
    public static final Date DATE_UNSET = ProtocolConstants.DATE_UNSET;
    public static final Date DATE_UNSPECIFIED = ProtocolConstants.DATE_UNSPECIFIED;
    
    // Necessary for the syncing of properties between Master RMI Server and Secondary RMI Servers.
    public void initTestNVOA(){
    	if (archive != null){
    		archive.init();
    	}    	
    }
    
    public HoneycombTestClient(String dataVIP) throws HoneycombTestException {
        init(dataVIP, null);
    }
    public HoneycombTestClient(String dataVIP, int port)
                                               throws HoneycombTestException {
        this.dataPort = port;
        init(dataVIP, auditIP);
    }
    public HoneycombTestClient(String dataVIP, String auditIP)
                                               throws HoneycombTestException {
        init(dataVIP, auditIP);
    }
    private void init(String dataVIP, String auditIP) 
                                                throws HoneycombTestException {
        this.dataVIP = dataVIP;

        try {
            LOG.fine("creating object archive for " + 
                     dataVIP + ":" + dataPort);
            archive = new TestNVOA(dataVIP, dataPort);
            advancedArchive = new ObjectArchive(dataVIP, dataPort);
            LOG.fine("done creating object archive for " +
                     dataVIP + ":" + dataPort);
            
            // Use client-side timeouts to detect hangs
            String ct = HoneycombTestConstants.PROPERTY_CONNECT_TIMEOUT;
            String s = TestRunner.getProperty(ct);
            if (s != null) {
                try {
                    connectTimeoutMsecs = Integer.parseInt(s);
                } catch (Throwable t) {
                    throw new HoneycombTestException("Invalid value for " +
                                                     ct + ": " + s);
                }
            }
            String st = HoneycombTestConstants.PROPERTY_SOCKET_TIMEOUT; 
            s = TestRunner.getProperty(st);
            if (s != null) {
                try {
                    socketTimeoutMsecs = Integer.parseInt(s);
                } catch (Throwable t) {
                    throw new HoneycombTestException("Invalid value for " +
                                                     st + ": " + s);
                }
            }
            setConnectionTimeout(connectTimeoutMsecs);
            setSocketTimeout(socketTimeoutMsecs);
            
            if (auditIP != null) {
                if (this.auditIP != null  &&  !auditIP.equals(this.auditIP))
                    LOG.info("overriding TestRunner " + 
                             HoneycombTestConstants.PROPERTY_AUDIT_IP +
                             " property " + this.auditIP + " with " + auditIP);
                this.auditIP = auditIP;
            }
            if (auditIP != null)
                ac = new AuditSrvClnt(auditIP);

            // disable audit if commandline property requested that
            s = TestRunner.getProperty(
                HoneycombTestConstants.PROPERTY_AUDIT_DISABLE);
            if (s != null) {
                disableAudit();
            }
        } catch (Exception e) {
            throw new HoneycombTestException ("init failed [" + dataVIP +
                                             "]: " + e.toString(), e);
        }
    }

    /**
     * Disable Audit.
     */
    public void disableAudit() {
        if (archive != null) {
            LOG.info("Disabling Audit");
            archive.disableAudit();
        } else {
            LOG.warning("Could not disable audit since archive object " +
                "does not exist yet");
        }
    }

    /**
     * Set the connection timeout.
     */
    public void setConnectionTimeout(int msecs) {
        boolean logmsg = false;
        connectTimeoutMsecs = msecs;
        if (archive != null) {
            if (archive.getConnectionTimeout() != connectTimeoutMsecs) {
                archive.setConnectionTimeout(connectTimeoutMsecs);
                logmsg = true;
            }
        }
        if (advancedArchive != null) {
            if (advancedArchive.getConnectionTimeout() != connectTimeoutMsecs) {
                advancedArchive.setConnectionTimeout(connectTimeoutMsecs);
                logmsg = true;
            }
        }
        if (logmsg) {
            LOG.info("Set connection timeout to " + connectTimeoutMsecs +
                " msecs");
        }
    }

    /**
     * Set the socket timeout.
     */
    public void setSocketTimeout(int msecs) {
        boolean logmsg = false;
        socketTimeoutMsecs = msecs;
        if (archive != null) {
            if (archive.getSocketTimeout() != socketTimeoutMsecs) {
                archive.setSocketTimeout(socketTimeoutMsecs);
                logmsg = true;
            }
        }
        if (advancedArchive != null) {
            if (advancedArchive.getSocketTimeout() != socketTimeoutMsecs) {
                advancedArchive.setSocketTimeout(socketTimeoutMsecs);
                logmsg = true;
            }
        }
        if (logmsg) {
            LOG.info("Set socket timeout to " + socketTimeoutMsecs +
                " msecs");
        }
    }

    /**
     *  Set whether exceptions are thrown or added to CmdResult.
     */
    public void setThrowAPIExceptions(boolean val) {
        throw_api = val;
    }

    /////////////////////////////////////////////////////////////////
    //  audit-related routines
    //

    private AuditSrvClnt getAuditClient() {
        if (auditIP == null) {
            return null;
        }
        return ac;
    }

    // return true if problem
    private boolean getAC() {
        if (auditIP == null)
            return true;
        // not thread-safe
        if (ac == null) {
            try {
                ac = new AuditSrvClnt(auditIP);
            } catch (Exception e) {
                LOG.severe("logAudit " + e);
                // XXX %%% what now?
                // throw new RemoteException("getting audit client", e);
                return true;
            }
        }
        return false;
    }

    /**
     *  Log string to oid file on audit host. Parsed by util/AuditParser.java
     *  and task/ClientAuditThread.java.
     */
    private void logAudit(String oid, String msg) {
        if (getAC())
            return;
        try {
            //long t1 = System.currentTimeMillis();
            ac.logAudit(oid, msg);
            //LOG.info("audit log time: " + (System.currentTimeMillis()-t1) + 
            //                                 " " + msg);
        } catch (Exception e) {
            LOG.severe("logAudit " + e);
        }
    }

    private void auditStore(CmdResult cr, NameValueRecord nvr) {
        if (getAC())
            return;
        try {
            ac.auditStoreObject(cr.mdoid, cr.filesize, cr.datasha1, nvr, cr.logTag);
        } catch (Exception e) {
            LOG.severe("logAudit " + e);
        }
        
        //StringBuffer sb = new StringBuffer();
        //sb.append(HoneycombTestConstants.AUDIT_STORE).append("\t");
        //sb.append(Long.toString(cr.filesize)).append("\t");
        //sb.append(cr.datasha1).append("\t").append(cr.logTag);
        //if (nvr != null)
        //addNVR(sb, nvr);
        //logAudit(cr.mdoid, sb.toString());
    }

    private void auditAddMetadata(String linkOID, NameValueRecord nvr, String newoid) {
        if (getAC())
            return;
        try {
            ac.auditAddMetadata(linkOID, nvr, newoid);
        } catch (Exception e) {
            LOG.severe("logAudit " + e);
        }
    }

    /*
    private void addNVR(StringBuffer sb, NameValueRecord nvr) {
        String[] keys = nvr.getKeys();
        for (int i=0; i<keys.length; i++) {
            String name = keys[i];
            String value = nvr.getAsString(name);
            sb.append(name).append("\t").append(value);
        }
    }
    */

    /**
     *  Log a msg, returning true if successful, false if not.
     */
    private boolean logMsg(String msg) {
        if (getAC())
            return false;
        try {
            ac.logMsg(msg);
            return true;
        } catch (Exception e) {
            LOG.severe("logMsg " + e);
        }

        return false;
    }

    ///////////////////////////////////////////////////////////////////////

    private void assignOids(CmdResult cr) {

        if (cr.sr == null)
            return;

        ObjectIdentifier objectidentifier = cr.sr.getObjectIdentifier();
        if (objectidentifier != null) {
            cr.mdoid = objectidentifier.toString();
        }
        objectidentifier = QAClient.getLinkIdentifier(cr.sr);
        if (objectidentifier != null) {
            cr.dataoid = objectidentifier.toString();
        }
    }

    private void checkHash(CmdResult cr) throws HoneycombTestException {
        if (cr.pass == false)
            return;
        if (cr.sr == null) {
            cr.pass = false;
            cr.addException(new HoneycombTestException(
                   "HoneycombTestClient.checkHash: pass=true but sr=null"));
            return;
        }
        String hash = ByteArrays.toHexString(cr.sr.getDataDigest());
        if (hash == null) {
            cr.pass = false;
            cr.addException(new HoneycombTestException(
                   "HoneycombTestClient.checkHash: pass=true but cr.sr.getDataDigest()=null"));
            return;
        }
        if (TestRequestParameters.getCalcHash()  &&  !hash.equals(cr.datasha1)) {
            cr.pass = false;
            HoneycombTestException e =
                    new HoneycombTestException("store hash different: " +
                                   "hc: " + hash + " local: " + cr.datasha1);
            if (throw_api)
                throw e;
            cr.addException(e);
        }
    }

    ///////////////////////////////////////////////////////////////////////
    //  NVR/HashMap conversion

    private NameValueRecord hashToNVR(HashMap hm) 
        throws HoneycombTestException {
        if (hm == null)
            return null;

        try {
            NameValueRecord nvr = archive.createRecord();

            Set s = hm.entrySet();
            Iterator it = s.iterator();
            while (it.hasNext()) {
                Map.Entry e = (Map.Entry) it.next();
                String name = (String) e.getKey();
                Object value = e.getValue();
                if (value instanceof String) {
                    nvr.put(name, (String)value);
                } else if (value instanceof Long) {
                    Long l = (Long) value;
                    nvr.put(name, l.longValue());
                } else if (value instanceof Double) {
                    Double d = (Double) value;
                    nvr.put(name, d.doubleValue());
                } else if (value instanceof java.sql.Date) {
                    nvr.put(name,(java.sql.Date)value);
                } else if (value instanceof Time) {
                    nvr.put(name,(Time)value);
                } else if (value instanceof Timestamp) {
                    nvr.put(name,(Timestamp)value);
                } else if (value instanceof byte[]) {
                    nvr.put(name,(byte[])value);
                } else if (value == null) {
                    nvr.put(name, (String)null);
                } else {
                    throw new HoneycombTestException(
                         "map->nvr: unsupported type (value=" + value + "): " +
                                             value.getClass().getName());
                }
            }
            return nvr;
        } catch (Exception e) {
            throw new HoneycombTestException("hash -> nvr: schema/type problem: " + e.getMessage(), e);
        }
    }

    //
    public static HashMap nvr2Hash(NameValueRecord nvr) throws HoneycombTestException {
        String[] keys = nvr.getKeys();
        int len = keys.length;
        HashMap map = new HashMap(len);
        for (int i = 0; i < len; i++){
            String key = keys[i];
            NameValueSchema.ValueType type = nvr.getAttributeType(key);
            
            if (type == NameValueSchema.STRING_TYPE){
                map.put(key, nvr.getString(key));
            } else if (type == NameValueSchema.LONG_TYPE){
                map.put(key, new Long(nvr.getLong(key)));
            } else if (type == NameValueSchema.DOUBLE_TYPE){
                map.put(key, new Double(nvr.getDouble(key)));
            } else if (type == NameValueSchema.DATE_TYPE){
                map.put(key, nvr.getDate(key));
            } else if (type == NameValueSchema.TIME_TYPE){
                map.put(key, nvr.getTime(key));
            } else if (type == NameValueSchema.TIMESTAMP_TYPE){
                map.put(key, nvr.getTimestamp(key));
            } else if (type == NameValueSchema.CHAR_TYPE){
                map.put(key, nvr.getString(key));
            } else if (type == NameValueSchema.BINARY_TYPE){
                map.put(key, nvr.getBinary(key));
            } else
                throw new HoneycombTestException("nvr2Hash: unexpected type: " +
                                                 type + " for key " + key);
        }
        return map;
    }

    ///////////////////////////////////////////////////////////////////////

    private static String getTag() throws HoneycombTestException {
        return RandomUtil.getRandomString(HoneycombTestConstants.TAG_SIZE);
    }

    ///////////////////////////////////////////////////////////////////////
    //  store

    /*
     *  Implementation of the store method for the 'old' API.
     *  Calls the actual honeycomb api, with or without
     *  the metadata object, depending on whether nvr is null.
     *  Accumulates time spent in the API in multiple calls.
     */
    private void doStore(CmdResult cr, FileChannel filechannel, 
                                       NameValueRecord nvr)
                                         throws ArchiveException, IOException {
        SystemRecord sr;
        if (null != nvr) {
            sr = archive.storeObject(filechannel, nvr);
        } else {
            sr = archive.storeObject(filechannel);
        }
        //cr.time = cr.time + System.currentTimeMillis() - t1;
        long t = TestRequestParameters.getTime();
        if (t != -1)
            cr.time += t;
        cr.pass = true;
        cr.sr = sr;
        assignOids(cr);
    }
    
    private void doStore(CmdResult cr, HCTestReadableByteChannel bytechannel,
			NameValueRecord nvr) throws ArchiveException, IOException {
		SystemRecord sr;
		if (null != nvr) {
			sr = archive.storeObject(bytechannel, nvr);
		} else {
			sr = archive.storeObject(bytechannel);
		}
        	long t = TestRequestParameters.getTime();
        	if (t != -1)
            		cr.time += t;
		cr.pass = true;
		cr.sr = sr;
		assignOids(cr);
	}

    /*
     *  Store a file.
     */
    private CmdResult storeFile(String filename, NameValueRecord nvr) 
                                             throws HoneycombTestException {

        FileChannel filechannel = null;
        FileInputStream fileinputstream = null;
/*
        //
        //  get sha1
        //
        String hash;
        if (TestRequestParameters.getCalcHash())
            hash = HCUtil.computeHash(filename);
        else
            hash = "no hash calcd";
*/
        try {
            fileinputstream = new FileInputStream(filename);
            filechannel = fileinputstream.getChannel();
        } catch(IOException iox) {
            throw new HoneycombTestException("Failed to open file: " + 
                iox.getMessage(), iox);
        }

        CmdResult cr = new CmdResult();
        cr.dataVIP = dataVIP;
        cr.filename = filename;
        cr.pass = false;
        cr.time = 0;
        File f = new File(filename);
        cr.filesize = f.length();

        String cmdTag = getTag();
        if (logMsg(cmdTag + " STORE " + cr.filesize +
                                (nvr == null ? " (no md)" : " (with md)")))
            cr.logTag = cmdTag;

        StringBuffer sb = new StringBuffer();

        //long t1 = System.currentTimeMillis();
        try {
            //
            //  retry at least twice on ArchiveException
            //  (can result from high load) 
            //  load tests actually expect this- we should hit it.
            //  XXX we should check and only retry if it's the 
            //  'no available node' ArchiveException
            //
            try {
                doStore(cr, filechannel, nvr);
                cr.pass = true;
            } catch (ArchiveException t) {
                sb.append(t.getMessage()).append("\n");
                cr.addException(t);
                while (cr.retries < 2 && cr.pass != true) {
                    // only retry on host not available
                    if (t.getMessage().indexOf("no active host available") == -1)
                        break;
                    //LOG.info("Retrying store... high load can cause this. Reported error:"+ t.toString());
                    //
                    // FIXME - should we be sleeping here?
                    //
                    cr.retries++;
                    try {
                        doStore(cr, filechannel, nvr);
                    } catch (ArchiveException t2) {
                        t = t2;
                        sb.append(t.getMessage()).append("\n");
                        cr.addException(t);
                    }
                }
            }
        } catch (IOException e) {
            // from doStore()
            cr.pass = false;
            cr.addException(e);
            throw new HoneycombTestException(
                                          "Retry failed - doStore returned: " + 
                                          e.toString() + 
                                          " And: " + 
                                          sb.toString());
        } finally {
            long t = TestRequestParameters.getTime();
       	    if (t != -1)
      	        cr.time += t;
            try {
                filechannel.close();
                fileinputstream.close();
            } catch (Exception ignore) {}
        }

        if (cr.pass) {
            cr.datasha1 = TestRequestParameters.getHash();
            checkHash(cr);
            auditStore(cr, nvr);
        } else if (throw_api) {
            throw new HoneycombTestException("store failed even after " +
                "retrying due to " + sb.toString());
        }
        if (cr.pass) {
            if (nvr == null)
                logMsg(cmdTag + " STORE done " + cr.mdoid + 
                    "\nMEAS store " + cmdTag + " size " + cr.filesize + 
                                               " time " + cr.time);
            else
                logMsg(cmdTag + " STORE done " + cr.mdoid + 
                    "\nMEAS store_b " + cmdTag + " size " + cr.filesize + 
                                               " time " + cr.time);
        } else
            logMsg(cmdTag + " STORE failed");

        return cr;
    }
    
    private CmdResult storeFile(HCTestReadableByteChannel byteChannel, NameValueRecord nvr)
			throws HoneycombTestException {

		CmdResult cr = new CmdResult();
		cr.dataVIP = dataVIP;
		cr.filename = byteChannel.getFilename();
		cr.pass = false;
		cr.time = 0;
		cr.filesize = byteChannel.getSize();

		String cmdTag = getTag();
		if (logMsg(cmdTag + " STORE " + cr.filesize
				+ (nvr == null ? " (no md)" : " (with md)")))
			cr.logTag = cmdTag;

		StringBuffer sb = new StringBuffer();

		long t1 = System.currentTimeMillis();
		try {
			//
			// retry at least twice on ArchiveException
			// (can result from high load)
			// load tests actually expect this- we should hit it.
			// XXX we should check and only retry if it's the
			// 'no available node' ArchiveException
			//
			try {
				doStore(cr, byteChannel, nvr);
				// After the HCTestReadableByteChannel has been read once it has the 
				// sha1 hash already calculated.
                                cr.datasha1 = TestRequestParameters.getHash();
				cr.pass = true;
			} catch (ArchiveException t) {
				sb.append(t.getMessage()).append("\n");
				cr.addException(t);
				while (cr.retries < 2 && cr.pass != true) {
					// only retry on host not available
					if (t.getMessage().indexOf("no active host available") == -1)
						break;
					// LOG.info("Retrying store... high load can cause this.
					// Reported error:"+ t.toString());
					//
					// FIXME - should we be sleeping here?
					//
					cr.retries++;
					try {
						doStore(cr, byteChannel, nvr);
						// After the HCTestReadableByteChannel has been read once it has the 
						// sha1 hash already calculated.
                                                cr.datasha1 = TestRequestParameters.getHash();
						cr.pass = true;
					} catch (ArchiveException t2) {
						t = t2;
						sb.append(t.getMessage()).append("\n");
						cr.addException(t);
					}
				}
			}
		} catch (IOException e) {
			// from doStore()
			cr.pass = false;
			cr.addException(e);
			throw new HoneycombTestException(
					"Retry failed - doStore returned: " + e.toString()
							+ " And: " + sb.toString());
		} finally {
			cr.total_time = System.currentTimeMillis() - t1;
		}

		if (cr.pass) {
			checkHash(cr);
			auditStore(cr, nvr);
		} else if (throw_api) {
			throw new HoneycombTestException("store failed even after "
					+ "retrying due to " + sb.toString());
		}
		if (cr.pass) {
			if (nvr == null)
				logMsg(cmdTag + " STORE done " + cr.mdoid + "\nMEAS store "
						+ cmdTag + " size " + cr.filesize + " time " + cr.time);
			else
				logMsg(cmdTag + " STORE done " + cr.mdoid + "\nMEAS store_b "
						+ cmdTag + " size " + cr.filesize + " time " + cr.time);
		} else
			logMsg(cmdTag + " STORE failed");

		return cr;
	}


    /*
     * Implementation of the store method
     * for bytearrays
     */
    private CmdResult doStore(byte[] bytes, int repeats, NameValueRecord nvr) 
                                             throws HoneycombTestException {

        HCTestReadableByteChannel hctrbc= null;
        try {
            hctrbc = new HCTestReadableByteChannel(bytes, repeats);
        } catch (NoSuchAlgorithmException e) {
            throw new HoneycombTestException(e);
        }

        return storeObject(hctrbc, bytes.length * repeats, null);
    }



    /**
     *  Store a file and extended metadata to Honeycomb.
     */
    public CmdResult store(String filename, HashMap map) 
                                                throws HoneycombTestException {
        NameValueRecord nvr = hashToNVR(map);
        CmdResult cr = storeFile(filename, nvr);
        cr.mdMap = map;
        return cr;
    }
    
    public CmdResult store(HCTestReadableByteChannel byteChannel, HashMap map)
			throws HoneycombTestException {
		NameValueRecord nvr = hashToNVR(map);
	CmdResult cr = storeFile(byteChannel, nvr);
        cr.mdMap = map;
        return cr;
    }

    /**
     *  Store a byte pattern and extended metadata to Honeycomb.
     */
    public CmdResult store(byte[] bytes, int repeats, HashMap map) 
                                                throws HoneycombTestException {
        NameValueRecord nvr = hashToNVR(map);
        CmdResult cr = doStore(bytes, repeats, nvr);
        cr.mdMap = map;
        checkHash(cr);
        return cr;
    }

    /**
     * Used for API testing, this method takes in many parameters to force
     * the use of different aspects of the storeObject API.
     * Most tests should not use this function because it is overly 
     * flexible (and a bit confusing!).
     */
    public CmdResult storeObject(
        boolean useAdvAPI, // Which interface to use (OA or NVOA?)
        boolean passMD, // Pass MD or not?
        boolean streamMD, // If passing MD, stream it or not?
        ReadableByteChannel dataChannel, // Data channel
        HashMap mdMap, // MD record (if not streaming it)
        ReadableByteChannel metadataChannel, // MD stream
        String cacheID, // Which MD cache (for adv API only)
        long size, // for audit log
        String hash) // for audit log
        throws HoneycombTestException {

        ObjectArchive archiveToUse;

        if (useAdvAPI) {
            archiveToUse = advancedArchive;
        } else {
            archiveToUse = archive;
        }

        NameValueRecord metadataRecord = hashToNVR(mdMap);

        CmdResult cr = new CmdResult();
        cr.dataVIP = dataVIP;
        cr.mdMap = mdMap;
        cr.filesize = size;

        String cmdTag = getTag();
        if (logMsg(cmdTag + " STORE " + cr.filesize +
                                (passMD ? " (no md)" : " (with md)")))
            cr.logTag = cmdTag;

        boolean cmd;

        try {

            long t1 = System.currentTimeMillis();

            if (passMD) {
                // only can pass MD in the NVOA
                cr.sr = ((NameValueObjectArchive)archiveToUse).storeObject(
                    dataChannel, metadataRecord);
            } else {
                // !passMD
                cr.sr = archiveToUse.storeObject(dataChannel);
            }
            if (archiveToUse instanceof TestNVOA) {
                long t = TestRequestParameters.getTime();
       	        if (t != -1)
      	            cr.time = t;
            } else {
                cr.time = System.currentTimeMillis() - t1;
            }
            assignOids(cr);
            cr.pass = true;

            if (hash != null) {
                cr.datasha1 = hash;
            } else if (archiveToUse instanceof TestNVOA) {
                cr.datasha1 = TestRequestParameters.getHash();
            } else if (dataChannel instanceof HCTestReadableByteChannel) {
                cr.datasha1 = ((HCTestReadableByteChannel)dataChannel).computeHash();
            } else {
                cr.datasha1 = "[UNEXPECTED CASE in HoneycombTestClient]";
            }

            auditStore(cr, metadataRecord);

        } catch (Throwable t) {
            if (throw_api)
                throw new HoneycombTestException(t);
            cr.addException(t);
        }

        if (cr.pass)
            checkHash(cr);

        if (cr.pass) {
            String s;
            if (dataChannel instanceof HCTestReadableByteChannel)
                s = "store_ch";
            else
                s = "store";
            if (passMD)
                s += "_b ";
            else
                s += " ";
            logMsg(cmdTag + " STORE done " + cr.mdoid + 
                    "\nMEAS " + s + cmdTag + " size " + cr.filesize + 
                                               " time " + cr.time);
        } else
            logMsg(cmdTag + " STORE failed");

        return (cr);
    }

    /**
     * Used for the simple store streaming test
     */
    public CmdResult storeObject(ReadableByteChannel dataChannel, long size,
                                                                  String hash)
                                                throws HoneycombTestException {
        return storeObject(false, false, false, dataChannel, null, null, null,
                           size, hash);
    }

    ///////////////////////////////////////////////////////////////////////
    //  retrieve

    /**
     * Used for the simple retrieve streaming test
     */
    public CmdResult retrieveObject(String oid, WritableByteChannel dataChannel)
                                               throws HoneycombTestException {
        CmdResult cr = new CmdResult();
        cr.dataVIP = dataVIP;
        String cmdTag = getTag();
        if (logMsg(cmdTag + " RETRIEVE " + oid))
            cr.logTag = cmdTag;

        try {

            long t1 = System.currentTimeMillis();
            Log.DEBUG("calling advancedArchive.retrieveObject() with stream");
            advancedArchive.retrieveObject(new ObjectIdentifier(oid),
                                           dataChannel);
            Log.DEBUG("done with advancedArchive.retrieveObject() with stream");
            //long t = TestRequestParameters.getTime();
       	    //if (t != -1)
      	        //cr.time = t;
            cr.time = System.currentTimeMillis() - t1;
            cr.pass = true;

            String cmd;
            if (dataChannel instanceof HCTestWritableByteChannel) {
                cmd = "retrieve_ch ";
                HCTestWritableByteChannel ch = 
                                       (HCTestWritableByteChannel) dataChannel;
                cr.datasha1 = ch.computeHash();
                cr.filesize = ch.getBytesMoved();
            } else {
                cmd = "retrieve ";
                cr.datasha1 = "unknown";
            }
            logMsg(cmdTag + " RETRIEVE done " + oid + "  pass=" + cr.pass + 
                   "\nMEAS " + cmd + cmdTag + " size " + cr.filesize + 
                                              " time " + cr.time);
        } catch (Throwable t) {
            logMsg(cmdTag + " RETRIEVE failed " + oid);

            if (throw_api)
                throw new HoneycombTestException("retrieveObject failed: " +
                                                t.getMessage());
            cr.addException(t);
        }
        return (cr);
    }

    /**
     * Does retrieve and validates that hash matches passed in hash.
     */
    public boolean retrieveObject(String oid, WritableByteChannel dataChannel,
                               String dataHash) throws HoneycombTestException {
        CmdResult cr = retrieveObject(oid, dataChannel);
        if (cr.pass) {
            if (TestRequestParameters.getCalcHash() == false) {
                Log.INFO("hash not considered");
                return (true);
            } else if (dataHash.equals(cr.datasha1)) {
                Log.INFO("Retrieved hash for oid " + oid + " was " + dataHash +
                    " as expected");
                return (true);
            } else {
                throw new HoneycombTestException("retrieveObject failed: " +
                    "expected hash " + dataHash + " but got hash " +
                    cr.datasha1 + " for oid " + oid);
            }
        }

        return (false);
   }

    /**
     *  Add metadata to existing oid.
     */
    public CmdResult addMetadata(String oid, HashMap map)
                                                throws HoneycombTestException {

        NameValueRecord nvr = hashToNVR(map);

        CmdResult cr = new CmdResult();
        cr.mdMap = map;
        cr.dataVIP = dataVIP;

        String cmdTag = getTag();
        if (logMsg(cmdTag + " STORE_MD " + oid))
            cr.logTag = cmdTag;

        try {
            ObjectIdentifier objectidentifier = new ObjectIdentifier(oid);

            cr.sr = archive.storeMetadata(objectidentifier, nvr);
            long t = TestRequestParameters.getTime();
       	    if (t != -1)
      	        cr.time = t;

            objectidentifier = cr.sr.getObjectIdentifier();
            if (objectidentifier != null) {
                cr.mdoid = objectidentifier.toString();
            }
            cr.pass = true;

            auditAddMetadata(oid, nvr, cr.mdoid);

            /*

            //
            //  note in the old oid that an md oid points to it
            //
            logAudit(oid, HoneycombTestConstants.AUDIT_ADD_MD +
                                  "\t" + cr.mdoid);

            //
            //  log/audit md under the new md oid
            //
            StringBuffer sb = new StringBuffer();
            sb.append(HoneycombTestConstants.AUDIT_ADD_MD).append("\t");
            sb.append(oid);
            addNVR(sb, nvr);
            logAudit(cr.mdoid, sb.toString());

            // XXX %%% add audit to complex api version

            */

        } catch (Throwable t) {
            if (throw_api)
                throw new HoneycombTestException(t);
            cr.addException(t);
        }

        if (cr.pass)
            logMsg(cmdTag + " STORE_MD done " + oid + 
                   "\nMEAS addmd " + cmdTag + " time " + cr.time);
        else
            logMsg(cmdTag + " STORE_MD failed " + oid);

        return cr;
    }

    /**
     * Used for API testing, this method takes in many parameters to force
     * the use of different aspects of the storeMetadata API.
     * Most tests should not use this function because it is overly 
     * flexible (and a bit confusing!).
     */
    public CmdResult storeMetadata(
        boolean useAdvAPI, // Which interface to use (OA or NVOA?)
        ObjectIdentifier oid, // OID to add MD to
        boolean streamMD, // stream MD?
        HashMap mdMap, // MD record (if not streaming it)
        ReadableByteChannel metadataChannel, // MD stream
        String cacheID) // Which MD cache (for adv API only)
        throws HoneycombTestException {

        NameValueRecord metadataRecord = hashToNVR(mdMap);

        CmdResult cr = new CmdResult();
        cr.mdMap = mdMap;
        cr.dataVIP = dataVIP;

        String cmdTag = getTag();
        if (logMsg(cmdTag + " STORE_MD " + oid))
            cr.logTag = cmdTag;

        ObjectArchive archiveToUse;

        if (useAdvAPI) {
            archiveToUse = advancedArchive;
        } else {
            archiveToUse = archive;
        }

        try {
            long t1 = System.currentTimeMillis();

            cr.sr = ((NameValueObjectArchive)archiveToUse).storeMetadata(
                oid, metadataRecord);

            cr.time = System.currentTimeMillis() - t1;
            if (archiveToUse instanceof TestNVOA) {
                long t = TestRequestParameters.getTime();
                if (t == -1)
                    cr.time = t;
            }
            cr.pass = true;
        } catch (Throwable t) {
            if (throw_api)
                throw new HoneycombTestException(t);
            cr.addException(t);
        }

        if (cr.pass)
            logMsg(cmdTag + " STORE_MD done " + oid +
                   "\nMEAS addmd " + cmdTag + " time " + cr.time);
        else
            logMsg(cmdTag + " STORE_MD failed " + oid);

        return cr;
    }

    /**
     * Validate a SystemRecord.  Returns a string of errors or ""
     * if no errors were found.
     */
    public static String validateSystemRecord(SystemRecord sr, long size) {
        String errors = "";

        // XXX some addtional parameters need to be passed in order to
        // determine what is expected.

        // Did we get back an oid?
        ObjectIdentifier oid;
        oid = sr.getObjectIdentifier();
        LOG.fine("getObjectIdentifier() returned " + oid);
        if (oid == null) {
            errors += "getObjectIdentifier() returned null; ";
        }

        // Did we get back a link?
        oid = QAClient.getLinkIdentifier(sr);
        LOG.fine("getLinkIdentifier() returned " + oid);
        if (oid == null) {
            errors += "getLinkIdentifier() returned null; ";
        }

        // Did we get back the algorithm?
        String alg = sr.getDigestAlgorithm();
        LOG.fine("getDigestAlgorithm() returned " + alg);
        if (alg == null) {
            errors += "getDigestAlgorithm() returned null; ";
        }

        // Did we get back the size?
        long returnedSize = sr.getSize();
        LOG.fine("getSize() returned " + returnedSize);
        if (size != returnedSize) {
            errors += "getSize() did not return expected filesize (" +
                returnedSize + " != " + size + "); ";
        }
     
        // Did we get back the data digest?
        byte[] bytes = sr.getDataDigest();
        String bytesString = HCUtil.convertHashBytesToString(bytes);
        LOG.fine("getDataDigest() returned " + bytesString);
        if (bytes == null) {
            errors += "getDataDigest() returned null; ";
        }

        return (errors);
    }

    /**
     *  Retrieve an object and write it to filename.
     */
    public CmdResult retrieve(String oid, String filename)
                                        throws HoneycombTestException {
        //Log.DEBUG("retrieve oid [" + oid + "]");

        CmdResult cr = new CmdResult();
        cr.dataVIP = dataVIP;
        cr.mdoid = oid;

        while (cr.retries < MAX_RETRIES) {
            FileChannel filechannel = null;
            FileOutputStream fileoutputstream = null;

            try {
                fileoutputstream = new FileOutputStream(filename);
                filechannel = fileoutputstream.getChannel();
            } catch(IOException iox) {
                throw new HoneycombTestException(
                                             "Failed to create temp file: " + 
                                             iox.getMessage(), iox);
            }

            String cmdTag = getTag();
            if (logMsg(cmdTag + " RETRIEVE " + oid))
                cr.logTag = cmdTag;

            boolean fatal = true;
            try {
                ObjectIdentifier objectidentifier = new ObjectIdentifier(oid);

                archive.retrieveObject(objectidentifier, filechannel);
                long t = TestRequestParameters.getTime();
                if (t != -1)
                    cr.time = t;

                cr.filename = filename;
                cr.datasha1 = TestRequestParameters.getHash();
                File f = new File(filename);
                cr.filesize = f.length();
                cr.pass = true;

            } catch(Throwable t) {
                if (t instanceof ArchiveException) {
                    Throwable t2 = t.getCause();
                    if (t2 instanceof HttpRecoverableException)
                        fatal = false;
                }
                if (fatal  &&  throw_api)
                    throw new HoneycombTestException(t);
                cr.addException(t);
            } finally {
                try {
                    filechannel.close();
                    fileoutputstream.close();
                } catch (Exception ignore) {}
            }

            if (cr.pass) {
                logMsg(cmdTag + " RETRIEVE done " + oid +
                   "\nMEAS retrieve " + cmdTag + " size " + cr.filesize + 
                                                 " time " + cr.time);
                if (TestRequestParameters.getCalcHash()) {
                    if (cr.datasha1 == null  ||  cr.datasha1.length() == 0) {
                        logMsg(cmdTag + " RETRIEVE NO HASH " + oid);
                        throw new HoneycombTestException("null/0-len hash, size " + 
                                                 cr.filesize);
                    }
                } else {
                    cr.datasha1 = "hash not calcd";
                }
                break;
            } else {
                logMsg(cmdTag + " RETRIEVE failed " + oid);
                try {
                    File f = new File(filename);
                    if (f.exists())
                        f.delete();
                } catch (Exception e) {
                }
                if (fatal)
                    break;
                cr.retries++;
            }
        }
        return cr;
    }

    /**
     *  Retrieve an object and write it to nowhere... 
     */
    public CmdResult retrieve(String oid)
                                        throws HoneycombTestException {
        //Log.DEBUG("retrieve oid [" + oid + "]");

        CmdResult cr = new CmdResult();
        cr.dataVIP = dataVIP;
        cr.mdoid = oid;

        while (cr.retries < MAX_RETRIES) {
            DigestableWriteChannel bytechannel = null;

          	bytechannel = new DigestableWriteChannel(false);
          
            String cmdTag = getTag();
            if (logMsg(cmdTag + " RETRIEVE " + oid))
                cr.logTag = cmdTag;

            boolean fatal = true;
            try {
                ObjectIdentifier objectidentifier = new ObjectIdentifier(oid);

                archive.retrieveObject(objectidentifier, bytechannel);
                long t = TestRequestParameters.getTime();
                if (t != -1)
                    cr.time = t;
                cr.datasha1 = TestRequestParameters.getHash();

                cr.filename = "/dev/null";
                cr.filesize = bytechannel.getNumBytes();
                cr.pass = true;

            } catch(Throwable t) {
                if (t instanceof ArchiveException) {
                    Throwable t2 = t.getCause();
                    if (t2 instanceof HttpRecoverableException)
                        fatal = false;
                }
                if (fatal  &&  throw_api)
                    throw new HoneycombTestException(t);
                cr.addException(t);
            } finally {
                try {
                    bytechannel.close();
                } catch (Exception ignore) {}
            }

            if (cr.pass) {
                logMsg(cmdTag + " RETRIEVE done " + oid +
                   "\nMEAS retrieve " + cmdTag + " size " + cr.filesize + 
                                                 " time " + cr.time);
                if (TestRequestParameters.getCalcHash()) {
                    if (cr.datasha1 == null  ||  cr.datasha1.length() == 0) {
                        logMsg(cmdTag + " RETRIEVE NO HASH " + oid);
                        throw new HoneycombTestException("null/0-len hash, size " + 
                                                 cr.filesize);
                    }
                } else {
                    cr.datasha1 = "hash not calcd";
                }
                break;
            } else {
                logMsg(cmdTag + " RETRIEVE failed " + oid);
                
                if (fatal)
                    break;
                cr.retries++;
            }
        }
        return cr;
    }

    /**
     *  RangeRetrieve an object into a file.
     */
    public CmdResult rangeRetrieve(String oid, String filename, 
                                   long offset, long length) 
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

        CmdResult cr = new CmdResult();
        cr.dataVIP = dataVIP;
        cr.filename = filename;
        cr.filesize = length;

        String cmdTag = getTag();
        if (logMsg(cmdTag + " RANGE_RETRIEVE " + oid + " offset=" + offset
                                                     + " length=" + length))
            cr.logTag = cmdTag;

        try {
            ObjectIdentifier objectidentifier = new ObjectIdentifier(oid);

            archive.retrieveObject(objectidentifier, filechannel,
                                        offset, length);
            long t = TestRequestParameters.getTime();
            if (t != -1)
                cr.time = t;

            cr.pass = true;
        } catch(Throwable t) {
            if (throw_api)
                throw new HoneycombTestException(t);
            cr.addException(t);
        } finally {
            try {
                filechannel.close();
                fileoutputstream.close();
            } catch (Exception ignore) {}
        }

        if (cr.pass)
            logMsg(cmdTag + " RANGE_RETRIEVE done " + oid + 
                   "\nMEAS rretrieve " + cmdTag + " size " + length +
                                                  " time " + cr.time);
        else
            logMsg(cmdTag + " RANGE_RETRIEVE failed " + oid);
        return cr;
    }

    /**
     *  Get metadata for oid.
     */
    public CmdResult getMetadata(String oid) throws HoneycombTestException {

        CmdResult cr = new CmdResult();
        cr.dataVIP = dataVIP;
        cr.mdoid = oid;

        String cmdTag = getTag();
        if (logMsg(cmdTag + " GET_MD " + oid))
            cr.logTag = cmdTag;

        try {
            ObjectIdentifier objectidentifier = new ObjectIdentifier(oid);

            NameValueRecord nvr = archive.retrieveMetadata(objectidentifier);
            if (nvr == null)
                throw new HoneycombTestException(
                      "null nvr from archive.retrieveMetadata(" + oid + ")");
            cr.sr = nvr.getSystemRecord();
            cr.mdMap = nvr2Hash(nvr);  
            long t = TestRequestParameters.getTime();
            if (t != -1)
                cr.time = t;

            cr.pass = true;

        } catch(Throwable t) {
            if (throw_api)
                throw new HoneycombTestException(t);
            cr.addException(t);
        }

        if (cr.pass)
            logMsg(cmdTag + " GET_MD done " + oid + "  pass=" + cr.pass +
                   "\nMEAS getmd " + cmdTag + " time " + cr.time);
        else
            logMsg(cmdTag + " GET_MD done " + oid + "  pass=" + cr.pass);

        return cr;
    }

    /**
     *  Query for oid's on extended metadata.
     */
    public CmdResult query(String q, int n)
        throws HoneycombTestException {
        PreparedStatement stmt;
        try {
            stmt = new PreparedStatement(q);
        } catch (IllegalArgumentException e) {
            throw new HoneycombTestException(e);
        }
        return query(stmt, n);
    }

    public CmdResult query(PreparedStatement q, int n)
        throws HoneycombTestException {

        CmdResult cr = new CmdResult();
        cr.dataVIP = dataVIP;
        cr.query = q; // for result count hack
        cr.maxResults = n; // for result count hack

        String cmdTag = getTag();
        if (logMsg(cmdTag + " QUERY " + q))
            cr.logTag = cmdTag;

        if (n == HoneycombTestConstants.USE_DEFAULT_MAX_RESULTS) {
            n = HoneycombTestConstants.DEFAULT_MAX_RESULTS;
        }

        try {
            LOG.fine("calling query(" + q + ", " + n + ")");
            cr.rs = archive.query(q, n);
            long t = TestRequestParameters.getTime();
            if (t != -1)
                cr.time = t;

            LOG.fine("done with query(" + q + ", " + n + ")");

            cr.pass = true;
        } catch (Throwable t) {
            if (throw_api)
                throw new HoneycombTestException(t);
            cr.addException(t);
        }

        if (cr.pass)
            logMsg(cmdTag + " QUERY done" +
                   "\nMEAS query " + cmdTag + " time " + cr.time + " n " + n);
        else
            logMsg(cmdTag + " QUERY done  pass=" + cr.pass);

        return cr;
    }


    /**
     *  QueryPlus  for oid's on extended metadata.
     */
    public CmdResult query(String q, String[] selectKeys, int n)
        throws HoneycombTestException {
        PreparedStatement stmt;
        try {
            stmt = new PreparedStatement(q);
        } catch (IllegalArgumentException e) {
            throw new HoneycombTestException(e);
        }
        return query(stmt, selectKeys,n);
    }

    public CmdResult query(PreparedStatement q, String[] selectKeys, int n)
        throws HoneycombTestException {

        CmdResult cr = new CmdResult();
        cr.dataVIP = dataVIP;
        cr.query = q; // for result count hack
        cr.maxResults = n; // for result count hack

        String cmdTag = getTag();
        if (logMsg(cmdTag + " QUERY " + q))
            cr.logTag = cmdTag;

        if (n == HoneycombTestConstants.USE_DEFAULT_MAX_RESULTS) {
            n = HoneycombTestConstants.DEFAULT_MAX_RESULTS;
        }

        try {
            LOG.fine("calling query(" + q + ", " + n + ")");
            cr.rs = archive.query(q, selectKeys, n);
            long t = TestRequestParameters.getTime();
            if (t != -1)
                cr.time = t;

            LOG.fine("done with query(" + q + ", " + n + ")");

            cr.pass = true;
        } catch (Throwable t) {
            if (throw_api)
                throw new HoneycombTestException(t);
            cr.addException(t);
        }

        if (cr.pass)
            logMsg(cmdTag + " QUERYPLUS done" +
                   "\nMEAS query " + cmdTag + " time " + cr.time + " n " + n);
        else
            logMsg(cmdTag + " QUERY done  pass=" + cr.pass);

        return cr;
    }



    /**
     * Some of our test code likes to get result counts.  This is a hack to do
     * this by running the query and counting the results.  Note that this is
     * slow and might require a lot of round trips, but we'll pass in a high
     * number for max results to try to minimize this.  This method assumes
     * you called the query() function above which remembers the original
     * query in the CmdResult.  Note that if active stores are occuring,
     * the number returned by this function will be out of sync with the
     * actual results set.
     */
    public int queryResultCount(CmdResult cr) throws HoneycombTestException {
        int numresults = 0;
        if (cr.query == null) {
            throw new HoneycombTestException("query can't be null in cr");
        }

        // re-run the query
        LOG.fine("in queryResultCount()");
        CmdResult newcr = query(cr.query, cr.maxResults);
        QueryResultSet qrs = (QueryResultSet)newcr.rs;
        try {
            while (qrs.next()) {
                numresults++;
            }
        } catch (Throwable t) {
            throw new HoneycombTestException("qrs.next() failed " + t);
        }

        LOG.fine("queryResultCount() found " + numresults);
        return (numresults);
    }

    /**
     *  Delete oid from system.
     */
    public CmdResult delete(String oid) throws HoneycombTestException {

        CmdResult cr = new CmdResult();
        cr.dataVIP = dataVIP;
        cr.mdoid = oid;

        String cmdTag = getTag();
        if (logMsg(cmdTag + " DELETE " + oid))
            cr.logTag = cmdTag;

        try {
            archive.delete(new ObjectIdentifier(oid));
            cr.pass = true;
            long t = TestRequestParameters.getTime();
            if (t != -1)
                cr.time = t;

            logAudit(oid, HoneycombTestConstants.AUDIT_DELETE);

        } catch (Throwable t) {
            if (throw_api)
                throw new HoneycombTestException(t);
            cr.addException(t);
        }

        if (cr.pass)
            logMsg(cmdTag + " DELETE " + oid +
                   "\nMEAS delete " + cmdTag + " time " + cr.time);
        else
            logMsg(cmdTag + " DELETE failed " + oid + " time " + cr.time);

        return cr;
    }
    
    public CmdResult audit(String cluster, String oid) throws HoneycombTestException {
        
        CmdResult cr = new CmdResult();
        
        cr.dataVIP = dataVIP;
        cr.mdoid = oid;
        String cmdTag = getTag();
        if (logMsg(cmdTag + " AUDIT " + oid))
            cr.logTag = cmdTag;

        long t1 = System.currentTimeMillis();
        try {
            AuditResult auditresult = Audit.getInstance(cluster).auditObjectAndReturnMessages(archive,oid);
            cr.auditResult = auditresult;
            cr.time = System.currentTimeMillis() - t1;
            cr.pass = true;

            //logAudit(oid, HoneycombTestConstants.AUDIT);

        } catch (Throwable t) {
            cr.time = System.currentTimeMillis() - t1;
            if (throw_api)
                throw new HoneycombTestException(t);
            cr.addException(t);
        }

        if (cr.pass)
            logMsg(cmdTag + " AUDIT " + oid +
                   "\nMEAS AUDIT " + cmdTag + " time " + cr.time);
        else
            logMsg(cmdTag + " AUDIT failed " + oid + " time " + cr.time);

        return cr;
    }


    /**
     * Used for API testing, this method allows you to specify whether the
     * delete should occur with the advanced or basic api.
     */
    public CmdResult delete(
        boolean useAdvAPI, // Which interface to use (OA or NVOA?)
        ObjectIdentifier oid) // OID to delete (could be MD or data)
        throws HoneycombTestException {

        CmdResult cr = new CmdResult();
        cr.dataVIP = dataVIP;

        ObjectArchive archiveToUse;
        if (useAdvAPI) {
            archiveToUse = advancedArchive;
        } else {
            archiveToUse = archive;
        }

        String cmdTag = getTag();
        if (logMsg(cmdTag + " DELETE " + oid))
            cr.logTag = cmdTag;

        try {
            long t1 = System.currentTimeMillis();
            archiveToUse.delete(oid);
            cr.time = System.currentTimeMillis() - t1;
            cr.pass = true;
        } catch (Throwable t) {
            if (throw_api)
                throw new HoneycombTestException(t);
            cr.addException(t);
        }
        if (cr.pass)
            logMsg(cmdTag + " DELETE " + oid +
                   "\nMEAS delete " + cmdTag + " time " + cr.time);
        else
            logMsg(cmdTag + " DELETE failed " + oid + " time " + cr.time);
        return cr;
     }
 
    /**
     *  Get the metadata schema.
     */
    public CmdResult getSchema() throws HoneycombTestException {
        //NameValueSchema schema = null;

        CmdResult cr = new CmdResult();
        cr.dataVIP = dataVIP;
        String cmdTag = getTag();
        if (logMsg(cmdTag + " GETSCHEMA"))
            cr.logTag = cmdTag;

        try {
            long t1 = System.currentTimeMillis();
            cr.nvs = archive.getSchema();
            cr.time = System.currentTimeMillis() - t1;
            cr.pass = true;
        } catch (Throwable t) {
            if (throw_api)
                throw new HoneycombTestException(t);
        }

        if (cr.pass)
            logMsg(cmdTag + " GETSCHEMA done" +
                    "\nMEAS getschema " + cmdTag + " time " + cr.time);
        else
            logMsg(cmdTag + " GETSCHEMA done pass=" + cr.pass);

        return cr;
    }
    

    /**
     * Convenience pass-through method.  Simply calls 'storeObject' on the
     * given archive, afterwhich performs accounting of the action required
     * for subsequent audit.  This includes,
     * <ul>
     * <li> logs a record of the store on the audit server, if being used.
     * <li> posts a "storeTimeMillis" metric to the test result.
     * <li> TODO: logs a perf metric to the audit server.
     * </ul>
     * and logs an audit record if auditing is enabled.  Simply calls
     * 'storeObject' on the given archive.
     * @param archive The archive
     * @param data The ReadableByteChannel.  An HCTestReadableByteChannel
     *             is used so that the sha can be retrieved for the audit.
     */
    public static SystemRecord storeObject(ObjectArchive archive,
                                           HCTestReadableByteChannel data,
                                           TestCase test)
        throws ArchiveException,
               IOException,
               HoneycombTestException
    {
        String logTag = getTag();
        AuditSrvClnt.singleton.logMsg(logTag + " STOREOBJECT");
        long time = 0;
        long t1 = System.currentTimeMillis();
        SystemRecord sr = archive.storeObject(data);
        time = System.currentTimeMillis() - t1;
        if (archive instanceof TestNVOA) {
            long t = TestRequestParameters.getTime();
            if (t == -1)
                time = System.currentTimeMillis() - t1;
        }
        AuditSrvClnt.singleton.logMsg(logTag + " STOREOBJECT done " + 
                sr.getObjectIdentifier().toString() +
                "\nMEAS storeobject " + logTag + 
                " size " + data.getSize() + " time " + time);

        test.postMetric(new Metric("storeTimeMillis", Metric.TYPE_DOUBLE, time));
        AuditSrvClnt.auditStore(data, sr, logTag);
        return sr;
    }

    /** Add entries into a NameValueRecord.
      *
      * This method gets the schema associated with a particulaur archive 
      * and iterates through attributes made available by that schema.
      * For each schema attribut found, an entry is added to the NameValueRecord 
      * attribute's. The name of the atttribute added to the Record is equal 
      * to the name of the schema attribute. The value of the attribute depends 
      * on what type of attribute is being added. See list below
      *
      * Type -> Value
      * String -> "I am a string"
      * long -> Long.MAX_VALUE
      * double -> double.MAX_VALUE
      * byte -> byte.MAX_VALUE
      *
      * The actual number of attribute entries added to the NVR is controlled by the numEntries parameter. 
      * -If numEntries less than or  0, then an entry for each schema attribute will be added.
      * -If numEntries greater than 0 than that many enteries will be added to the NVR. 
      * Num entries can not exceed the number of attributes made available by the schema. Currently this 
      * method does not check for such a condition, so don't do it!
      */
    public static void fillNVR(NameValueRecord nvr, NameValueObjectArchive noa, int numEntries)  throws ArchiveException, IOException {
        NameValueSchema nvs = noa.getSchema();
        NameValueSchema.Attribute [] attrs = nvs.getAttributes();
        Object value = null;
        String charValue = "I am a char string";
        String stringValue = "I am a string";
        long longValue = Long.MAX_VALUE;
        double doubleValue = Double.MAX_VALUE;
        byte[] bytesValue = {Byte.MAX_VALUE};
        java.sql.Date dateValue = new java.sql.Date(0L);
        Time timeValue = new Time(0L);
        Timestamp timestampValue = new Timestamp(0L);
        
	if (numEntries <= 0) 
	    numEntries = attrs.length;
        
        for (int i = 0; i < numEntries; i++) {
            NameValueSchema.Attribute currentAttr = attrs[i];
            String attrName = currentAttr.getName();
            NameValueSchema.ValueType vt = currentAttr.getType();
            if (vt.equals(nvs.BINARY_TYPE)) {
                nvr.put(attrName,bytesValue);
            }else if (vt.equals(nvs.DOUBLE_TYPE)) {
                nvr.put(attrName, doubleValue);
            }else if (vt.equals(nvs.LONG_TYPE)) {
                nvr.put(attrName, longValue);
            }else if (vt.equals(nvs.STRING_TYPE)) {
                nvr.put(attrName,stringValue);
            }else if (vt.equals(nvs.CHAR_TYPE)) {
                nvr.put(attrName,charValue);
            }else if (vt.equals(nvs.DATE_TYPE)) {
                nvr.put(attrName,dateValue);
            }else if (vt.equals(nvs.TIME_TYPE)) {
                nvr.put(attrName,timeValue);
            }else if (vt.equals(nvs.TIMESTAMP_TYPE)) {
                nvr.put(attrName,timestampValue);
            } else if (vt.equals(nvs.OBJECTID_TYPE)) {
                // skip objectid field
            } else throw new IllegalArgumentException("unrecognized type");
        }
    }

    public static boolean equalityTest(Object equalObj1, Object equalObj2, Object equalObj3, Object unequalObj ) {
	boolean result = true;
	Object diffType = new java.util.ArrayList();
	if (equalObj1 instanceof java.util.ArrayList) {
	    diffType = new java.util.HashMap();
	}
	String testclass = equalObj1.getClass().getName();

	// null
	if (!Util.equalityNullTest(equalObj1)) {
	    Log.ERROR("equals() method for " + testclass + " reported equality against a null object");
	    result = false;
	}	

	// different type
	if (!Util.equalityDifferentInstanceTest(equalObj1, diffType)) {
	    Log.ERROR("equals() method for " + testclass + " reported equality against a class of different type");
	    result = false;
	}	


	// reflexive
	if (!Util.equalityReflexiveTest(equalObj1)) {
	    Log.ERROR("equals() method for " + testclass + " failed reflexive test"); 
	    result = false;
	}	

        // symmetrical
	if (!Util.equalitySymmetryTest(equalObj1,equalObj2)) {
	    Log.ERROR("equals() method for " + testclass + " failed symmetry test"); 
	    result = false;
	}	

	// transitive 
	if (!Util.equalityTransitiveTest(equalObj1,equalObj2,equalObj3)) {
	    Log.ERROR("equals() method for " + testclass + " failed transitive test"); 
	    result = false;
	}	

	// inequal
	if (! Util.equalityInequalityTest(equalObj1,unequalObj)) { 
	    Log.ERROR("equals() method for " + testclass + " report two unequal instances as equal");
	    result = false;
	}	

	return result;
    }

    public static boolean basicCompareToTest(Comparable equalObj1, Comparable equalObj2, Comparable unequalObj ) {

	boolean result = true;
	String testclass = equalObj1.getClass().getName();
	Comparable diffType = new String("");
	if (equalObj1 instanceof String )
	    diffType = new Integer(0);


	// compare to Null
	if ((equalObj1.compareTo(null) == 0)) {
	    result = false;
	    Log.ERROR("compareTO() method for " + testclass + " reported equality when  compared against a null");
	}

	// compare to different type
	if ((equalObj1.compareTo(diffType) == 0)) {
	    result = false;
	    Log.ERROR("compareTO() method for " + testclass + " reported equality when  compared against a object of different type");
	}
	// compare to same instances
	if ((equalObj1.compareTo(equalObj1) != 0)) {
	    result = false;
	    Log.ERROR("compareTO() method for " + testclass + " reported inequality when  compared against the same instance");
	}

	// compare to unequal instances
	if ((equalObj1.compareTo(unequalObj) == 0)) {
	    result = false;
	    Log.ERROR("compareTO() method for " + testclass + " reported equality when  compared against an unequal instance");
	    Log.ERROR("The result of equals() applied to the objects is: " + equalObj1.equals(unequalObj));
	}
	if ((unequalObj.compareTo(equalObj1) == 0)) {
	    result = false;
	    Log.ERROR("compareTO() method for " + testclass + " reported equality when  compared against an unequal instance");
	    Log.ERROR("The result of equals() applied to the objects is: " + equalObj1.equals(unequalObj));
	}

	// compare to equal instances
	if ((equalObj1.compareTo(equalObj2) != 0)) {
	    result = false;
	    Log.ERROR("compareTO() method for " + testclass + " reported inequality when  compared against an equal instance");
	}
	if ((equalObj2.compareTo(equalObj1) != 0)) {
	    result = false;
	    Log.ERROR("compareTO() method for " + testclass + " reported inequality when  compared against an equal instance");
	}

	return result;

    }

    public static boolean basicHashCodeTest (Object equalObj1, Object equalObj2) {
	boolean result = true;
	String testclass = equalObj1.getClass().getName();

	//make sure that on hashCode is equal for multiple invocation of HashCode on same instance
	if (equalObj1.hashCode() != equalObj1.hashCode()) {
	    result = false;
	    Log.ERROR("hashCode() method for " + testclass + " resulted in different value when invoked mutliple times on the same instance");
	}
	//make sure that on hashCode is equal for different yet equal instances
	if (equalObj1.hashCode() != equalObj2.hashCode()) {
	    result = false;
	    if (equalObj1.equals(equalObj2)) {
		Log.ERROR("hashCode() method for " + testclass + " resulted in different value for instances deemed equivalent by the equals() method.");
		Log.ERROR("hashCode() value for first object is: " + equalObj1.hashCode());
		Log.ERROR("hashCode() value for second object is: " + equalObj2.hashCode());
	    }
	    else {
		Log.ERROR("Objects used to test hashCode() equivalance where not deemed equal per their equals() method.");
	    }
	}

	return result;
    }

	public TestNVOA getArchive() {
		return archive;
	}

    /********************************************************
     *
     * Bug 6554027 - hide retention features
     *
     *******************************************************/
    /*
    // Set the compliance retention time
    public void setRetentionTime(ObjectIdentifier oid, Date retentionTime)
        throws HoneycombTestException {

        try {
            archive.setRetentionTime(oid, retentionTime);
        } catch (ArchiveException ae) {
            throw new HoneycombTestException(ae);
        } catch (IOException ioe) {
            throw new HoneycombTestException(ioe);
        }
    }

    // Set the compliance retention time
    public Date setRetentionTimeRelative(ObjectIdentifier oid,
                                         long retentionLength)
        throws HoneycombTestException {
        Date retentionTime;

        try {
            retentionTime = archive.setRetentionTimeRelative(oid,
                                                             retentionLength);
        } catch (ArchiveException ae) {
            throw new HoneycombTestException(ae);
        } catch (IOException ioe) {
            throw new HoneycombTestException(ioe);
        }

        return retentionTime;
    }

    // Get the compliance retention time
    public Date getRetentionTime(ObjectIdentifier oid)
        throws HoneycombTestException {
        Date retentionTime;

        try {
            retentionTime = archive.getRetentionTime(oid);
        } catch (ArchiveException ae) {
            throw new HoneycombTestException(ae);
        } catch (IOException ioe) {
            throw new HoneycombTestException(ioe);
        }

        return retentionTime;
    }

    // Add a compliance legal hold tag
    public void addLegalHold(ObjectIdentifier oid, String legalHold)
        throws HoneycombTestException {

        try {
            archive.addLegalHold(oid, legalHold);
        } catch (ArchiveException ae) {
            throw new HoneycombTestException(ae);
        } catch (IOException ioe) {
            throw new HoneycombTestException(ioe);
        }
    }

    // Remove a compliance legal hold tag
    public void removeLegalHold(ObjectIdentifier oid, String legalHold)
        throws HoneycombTestException {

        try {
            archive.removeLegalHold(oid, legalHold);
        } catch (ArchiveException ae) {
            throw new HoneycombTestException(ae);
        } catch (IOException ioe) {
            throw new HoneycombTestException(ioe);
        }
    }

    // Query compliance legal holds
    public QueryResultSet queryLegalHold(String legalHold, int resultCount)
        throws HoneycombTestException {

        try {
            return archive.queryLegalHold(legalHold, resultCount);
        } catch (ArchiveException ae) {
            throw new HoneycombTestException(ae);
        } catch (IOException ioe) {
            throw new HoneycombTestException(ioe);
        }
    }
    */

    // Set the cell compliance time
    public Date getDate() throws HoneycombTestException {
        Date date;

        try {
            date = archive.getDate();
        } catch (ArchiveException ae) {
            throw new HoneycombTestException(ae);
        } catch (IOException ioe) {
            throw new HoneycombTestException(ioe);
        }

        return date;
    }
}
