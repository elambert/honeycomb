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

     
package com.sun.honeycomb.hctest.cases.storepatterns;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.*;
import java.util.*;
import com.sun.honeycomb.client.*;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.task.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.hctest.rmi.*;
import com.sun.honeycomb.hctest.rmi.clntsrv.clnt.ClntSrvClnt;
import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;



public class RmiServerThread extends Thread implements ServerThreadInterface{
    OperationFactoryInterface _factory;
    int _serverNum;
    boolean _isDone;
    int _timeout;
    int _threadId;
    static ArrayList _dataVips = null;
    private Random _random = new Random(12345);
    public RmiServerThread(int serverNum,int threadId, OperationFactoryInterface factory) {
        _factory = factory;
        _serverNum=serverNum;
        _isDone=false;

        _threadId=threadId;
        //        Log.INFO("RmiServerThread started on server:"+serverNum);
        _timeout = Settings.getValue(Settings.TIMEOUT_MS,
                                     Settings.TIMEOUT_MS_DEFAULT);
        start();
    }
    public boolean isDone() {
        return _isDone;
    }
    public void run() {
        while(_factory.keepGoing(_serverNum,_threadId)) {
            try {
                while(_factory.keepGoing(_serverNum,_threadId)) {                
                    dispatch( _factory.next(_serverNum,_threadId));                
                }
            } catch (HoneycombTestException e) {
                Log.ERROR("Failed at thread toplevel with error: "+ Log.stackTrace(e));
            }
        }
        _isDone=true;
    }
    public void dispatch(Operation curOp) throws HoneycombTestException {        

        //
        // Each operation 
        //
    	if (curOp.getRequestedOperation() == Operation.NONE) {
    		try {
				Thread.sleep(NoneOpGenerator.SLEEP_TIME*1000);
			} catch (InterruptedException e) {
				// Who cares!
			}
    		Log.INFO("SLEEP REQ on server:" + _serverNum + " thread:" + _threadId);
    	} else if ( (curOp.getRequestedOperation() == Operation.DATASTORE) ) {
            Log.INFO("STORE REQ on server:" + _serverNum + " thread:" + _threadId + " size:" + curOp.getStoreSize());
            doDataStore(curOp);
        } else if ( ( curOp.getRequestedOperation() == Operation.DATAFETCH ) ) {
            Log.INFO("FETCH REQ on server:" + _serverNum + " thread:" + _threadId + " OID: " + curOp.getOID());
            doDataFetch(curOp);
        } else if ( ( curOp.getRequestedOperation() == Operation.METADATASTORE ) ) {
            Log.INFO("MDSTORE REQ on server:" + _serverNum + " thread:" + _threadId + " OID: " + curOp.getOID());
            doMetadataStore(curOp);
        } else if ( ( curOp.getRequestedOperation() == Operation.METADATAFETCH ) ) {
            Log.INFO("MDFETCH REQ on server:" + _serverNum + " thread:" + _threadId + " OID: " + curOp.getOID());
            doMetadataFetch(curOp);
        } else if ( (curOp.getRequestedOperation() == Operation.DELETE ) ) {
            Log.INFO("DELETE REQ on server:" + _serverNum + " thread:" + _threadId + " OID: " + curOp.getOID());
            doDelete(curOp);
        } else if ( (curOp.getRequestedOperation() == Operation.AUDITOBJECT ) ) {
            Log.INFO("AUDITOBJ REQ on server:" + _serverNum + " thread:" + _threadId + " OID: " + curOp.getOID());
            doAudit(curOp);
        } else if ( (curOp.getRequestedOperation() == Operation.DATASTOREWITHMD) ) {
            Log.INFO("STOREWITHMD REQ on server:" + _serverNum + " thread:" + _threadId + " size:" + curOp.getStoreSize());
            doDataStoreWithMD(curOp);
        } else if ( (curOp.getRequestedOperation() == Operation.QUERYMETADATA) ) {
            Log.INFO("QUERYMD REQ on server:" + _serverNum + " thread:" + _threadId );
            doQueryMD(curOp);
        } else {            
            Log.INFO("UNKNOWN REQ request request:" + curOp.getRequestedOperation() + " on server:" + _serverNum + " thread:" + _threadId + " OID: " + curOp.getOID());
        }
    }

    private void doBytePatternDataStore(Operation curOp) throws HoneycombTestException {
        HoneycombRemoteSuite rs =(HoneycombRemoteSuite) SuiteHolder.getSuite();
        //
        // Begin tricky byte stuff
        //

        String hexString = (String)Settings.getString(Settings.BYTEPATTERN,
                                                    Settings.BYTEPATTERNDEFAULT);

        //        String hexString = new String("de ad be ef");
        String pairs[] =  hexString.split(" ");
        byte[] b = new byte[pairs.length];
        for(int i=0;i<pairs.length;i++) {
            // the 16 specifies you want it to parse as base 16 (hex)
            b[i] = (byte)Integer.parseInt(pairs[i], 16);
        } 

        int numStores=(int)(curOp.getStoreSize()/pairs.length);
        System.out.println("RmiServerThread: bytePattern store:" + numStores + " * " + pairs.length);
        //
        // End tricky byte stuff
        //

        StoreTask st = rs.startStore(_serverNum,b,numStores,null,null);

        rs.waitForTask(st,_timeout);
        if(null!= st.result) {     
            curOp.setResult(st.result);
        }
        
        _factory.done(curOp,_serverNum,_threadId);
    }
    
    private void doDataStore(Operation curOp) throws HoneycombTestException {

        HoneycombRemoteSuite rs =(HoneycombRemoteSuite) SuiteHolder.getSuite();

        StoreTask st = rs.startStore(_serverNum,curOp.getStoreSize(),null,getDataVip());
                
        rs.waitForTask(st,_timeout);
        
        curOp.setResult(st.result);
        curOp.setThrown(st.thrown);
        
        _factory.done(curOp,_serverNum,_threadId);
    }
    
    private void doQueryMD(Operation curOp) throws HoneycombTestException {

        HoneycombRemoteSuite rs =(HoneycombRemoteSuite) SuiteHolder.getSuite();

        QueryTask qt = rs.startQuery(_serverNum,curOp.getQuery(),getDataVip());
                
        rs.waitForTask(qt,_timeout);
        
        curOp.setResult(qt.result);
        curOp.setThrown(qt.thrown);
        
        _factory.done(curOp,_serverNum,_threadId);
    }
    
    private void doDataStoreWithMD(Operation curOp) throws HoneycombTestException {

        HoneycombRemoteSuite rs =(HoneycombRemoteSuite) SuiteHolder.getSuite();

        StoreTask st = rs.startStore(_serverNum,curOp.getStoreSize(),curOp.getMetaData(),getDataVip());
                
        rs.waitForTask(st,_timeout);
        
        curOp.setResult(st.result);
        curOp.setThrown(st.thrown);
        
        _factory.done(curOp,_serverNum,_threadId);
    }
    
    private void doDelete(Operation curOp) throws HoneycombTestException {

        HoneycombRemoteSuite rs =(HoneycombRemoteSuite) SuiteHolder.getSuite();

        DeleteTask dt = rs.startDelete(_serverNum,curOp.getOID());
        dt.setNoisy(false);
        
        rs.waitForTask(dt,_timeout);
        
        curOp.setResult(dt.result);      
        curOp.setThrown(dt.thrown);
        
        _factory.done(curOp,_serverNum,_threadId);
    }
    
    private void doAudit(Operation curOp) throws HoneycombTestException {

        HoneycombRemoteSuite rs =(HoneycombRemoteSuite) SuiteHolder.getSuite();
        AuditTask at = rs.startAudit(_serverNum,curOp.getOID());
        at.setNoisy(false);
        rs.waitForTask(at,_timeout);
        curOp.setResult(at.result);      
        curOp.setThrown(at.thrown);
        
        _factory.done(curOp,_serverNum,_threadId);
    }
    
    private void doMetadataStore(Operation curOp) throws HoneycombTestException {
        HoneycombRemoteSuite rs =(HoneycombRemoteSuite) SuiteHolder.getSuite();
    	// TODO: future implementation must generate the actual MetaData on the RMI Server side...
        // not be passed through the RMI call because this will take it's time and will not allow the 
        // framework to work with optimal performance
        AssocMetaDataTask mdt = rs.startAssocMetaData(_serverNum,curOp.getOID(), curOp.getMetaData(), getDataVip());
        mdt.setNoisy(false);
        rs.waitForTask(mdt,_timeout);
        curOp.setResult(mdt.result);      
        curOp.setThrown(mdt.thrown);
        
        _factory.done(curOp,_serverNum,_threadId);
    }


    private void doDataFetch(Operation curOp) throws HoneycombTestException {
        HoneycombRemoteSuite rs =(HoneycombRemoteSuite) SuiteHolder.getSuite();
        RetrieveTask rt = rs.startRetrieve(_serverNum,curOp.getOID(),getDataVip());
        rt.setNoisy(false);
        
        rs.waitForTask(rt,_timeout);
        
        curOp.setResult(rt.result);        
        curOp.setThrown(rt.thrown);
        
        _factory.done(curOp,_serverNum,_threadId);
    }


    private void doMetadataFetch(Operation curOp) throws HoneycombTestException {
        HoneycombRemoteSuite rs =(HoneycombRemoteSuite) SuiteHolder.getSuite();

        RetrieveMDTask rt = rs.startRetrieveMD(_serverNum,curOp.getOID(),getDataVip());

        rs.waitForTask(rt,_timeout);
        curOp.setResult(rt.result);
        curOp.setThrown(rt.thrown);
        _factory.done(curOp,_serverNum,_threadId);
    }

    //
    // returns null if dataVIP is used, so the old mechanism works.
    // returns a datavip string from datavips if that's defined.
    //
    private String getDataVip() {
        String list = TestRunner.getProperty(Settings.DATAVIPS);
        if (list == null) 
            return null;
        else {
            //
            // Set up list of VIPS
            //
            if (_dataVips==null) {
                _dataVips = new ArrayList();

                StringTokenizer st = new StringTokenizer(list, ",");
                while (st.hasMoreTokens()) {
                    String client = st.nextToken().trim();
                        _dataVips.add(client);
                }    
            }
            //
            // check out a vip
            //
            int index = _random.nextInt(_dataVips.size());
            String vip = (String)_dataVips.get(index);
            System.out.println("Got random vip: " + vip);
            return vip;
        }
    }

}

