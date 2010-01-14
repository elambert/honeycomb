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



package com.sun.honeycomb.hctest;

import java.util.ArrayList;

import sun.util.logging.resources.logging;

import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.SystemRecord;
import com.sun.honeycomb.test.util.Log;

/**
 ** Provides mechanisms for recording operations performed against a cluster
 ** and for querying the expected state of a cluster.
 **
 **/
public class ThreadedAudit extends Audit implements Runnable {

	private ArrayList records = new ArrayList();
	
	public ThreadedAudit(String host, String cluster) throws Throwable {
		super(host, cluster);
		new Thread(this).start();
		Log.INFO("Creating threaded audit...");
	}
	
	private void wakeUp(){
		synchronized(this){
			notify();
		}
	}

	
	/**
	 ** Record a store operation.
	 **/
	public void recordStoreOp(int status, long startTime, Long endTime,long api_time,
			String info, String sha1, NameValueRecord metadata, Long numBytes,
			String logtag, SystemRecord systemRecord) throws Throwable {
		
		records.add(RecordState.newStoreOp(status,startTime,endTime,api_time,info,sha1,metadata,numBytes,logtag,systemRecord));
		wakeUp();
	}
	
	/**
	 ** Record a retrieve operation.
	 **/
	public void recordRetrieveOp(int status, long startTime, Long endTime,long api_time,
			String info, ObjectIdentifier oid, String sha1, Long numBytes,
			Long offset, Long length, String log_tag) throws Throwable {
		records.add(RecordState.newRetrieveOp(status,startTime,endTime,api_time,info,oid,sha1,numBytes,offset,length,log_tag));
		wakeUp();
	}
	
	/**
	 ** Record a retrieve metadata operation.
	 **/
	public void recordRetrieveMDOp(int status, long startTime, Long endTime, long api_time,
			                       String info, ObjectIdentifier oid, Long numBytes, Long offset, 
			                       Long length, String log_tag) throws Throwable {
		records.add(RecordState.newRetrieveMDOp(status,startTime,endTime,api_time,info,oid,numBytes,offset,length,log_tag));
		wakeUp();
	}

	/**
	 ** Record a delete operation.
	 **/
	public void recordDeleteOp(int status, long startTime, Long endTime,long api_time,
			String info, ObjectIdentifier oid, String log_tag,
			boolean noSuchObjectException) throws Throwable {
		records.add(RecordState.newDeleteOp(status,startTime,endTime,api_time,info,oid,log_tag,noSuchObjectException));
		wakeUp();
	}

	/**
	 ** Record a succesful add metadata operation.
	 **/
	public void recordLinkOp(int status, long startTime, Long endTime,long api_time,
			String info, ObjectIdentifier linkOID, NameValueRecord metadata,
			String log_tag, SystemRecord systemRecord) throws Throwable {
		records.add(RecordState.newLinkOp(status,startTime,endTime,api_time,info,linkOID,metadata,log_tag,systemRecord));
		wakeUp();
	}

	private boolean timeToStop = false;
	public void timeToStop() {
		timeToStop = true;
	}
	
	private boolean allDone = false;
	public boolean isAllDone(){
		return allDone;
	}

	public void run() {
		do {
			try {
				synchronized(this){
					wait(1000);
				}
			} catch (InterruptedException e) {
				// Ignore exceptions
			}
			while(records.size() != 0) {
				RecordState record = (RecordState)records.remove(0);
				
				try {
					switch (record.type){
						case RecordState.STORE_TYPE:
							super.recordStoreOp(record.status,record.startTime,record.endTime,record.api_time,record.info,record.sha1,record.metadata,record.numBytes,record.logtag,record.systemRecord);
							break;
	
						case RecordState.RETRIEVE_TYPE:
							super.recordRetrieveOp(record.status,record.startTime,record.endTime,record.api_time,record.info,record.oid,record.sha1,record.numBytes,record.offset,record.length,record.logtag);
							break;
	
						case RecordState.DELETE_TYPE:
							super.recordDeleteOp(record.status,record.startTime,record.endTime,record.api_time,record.info,record.oid,record.logtag,record.noSuchObjectException);
							break;
	
						case RecordState.LINK_TYPE:
							super.recordLinkOp(record.status,record.startTime,record.endTime,record.api_time,record.info,record.linkOID,record.metadata,record.logtag,record.systemRecord);
							break;
					}
				} catch (Throwable t){
					Log.ERROR("Error recording operation in audit database: " + Log.stackTrace(t));
				}
			}	
		} while (!timeToStop);
		
		allDone = true;
	}
}
