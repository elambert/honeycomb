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

import com.sun.honeycomb.client.NameValueRecord;
import com.sun.honeycomb.client.ObjectIdentifier;
import com.sun.honeycomb.client.SystemRecord;

public class RecordState {

	public int type = 0;
	
	public static final int STORE_TYPE = 1;
	public static final int RETRIEVE_TYPE = 2;
	public static final int DELETE_TYPE = 3;
	public static final int LINK_TYPE = 4;

	public int status;
	public long startTime;
	public Long endTime;
	public long api_time;
	public String info;
	public String sha1;
	public NameValueRecord metadata;
	public Long numBytes;
	public String logtag;
	public SystemRecord systemRecord;
	public ObjectIdentifier oid;
	public Long offset;
	public Long length;
	public boolean noSuchObjectException;
	public ObjectIdentifier linkOID;

	public static RecordState newStoreOp(int status, long startTime, Long endTime, long api_time,
			String info, String sha1, NameValueRecord metadata,
			Long numBytes, String logtag, SystemRecord systemRecord) {
		RecordState result = new RecordState();
		result.status = status;
		result.startTime = startTime;
		result.endTime = endTime;
		result.api_time = api_time;
		result.info = info;
		result.sha1 = sha1;
		result.metadata = metadata;
		result.numBytes = numBytes;
		result.logtag = logtag;
		result.systemRecord = systemRecord;
		result.type = STORE_TYPE;
		return result;
	}

	public static RecordState newRetrieveOp(int status, long startTime, Long endTime, long api_time,
			String info, ObjectIdentifier oid, String sha1, Long numBytes,
			Long offset, Long length, String log_tag) {
		RecordState result = new RecordState();
		result.status = status;
		result.startTime = startTime;
		result.endTime = endTime;
		result.api_time = api_time;
		result.info = info;
		result.oid = oid;
		result.sha1 = sha1;
		result.numBytes = numBytes;
		result.offset = offset;
		result.length = length;
		result.logtag = log_tag;
		result.type = RETRIEVE_TYPE;
		return result;
	}
	
	public static RecordState newRetrieveMDOp(int status, long startTime, Long endTime, long api_time,
		    String info, ObjectIdentifier oid, Long numBytes, Long offset, 
		    Long length, String log_tag) {
		RecordState result = new RecordState();
		result.status = status;
		result.startTime = startTime;
		result.endTime = endTime;
		result.api_time = api_time;
		result.info = info;
		result.oid = oid;
		result.numBytes = numBytes;
		result.offset = offset;
		result.length = length;
		result.logtag = log_tag;
		result.type = RETRIEVE_TYPE;
		return result;
	}
	
	public static RecordState newDeleteOp(int status, long startTime, Long endTime, long api_time,
			String info, ObjectIdentifier oid, String log_tag,
			boolean noSuchObjectException) {
		RecordState result = new RecordState();
		result.status = status;
		result.startTime = startTime;
		result.endTime = endTime;
		result.api_time = api_time;
		result.info = info;
		result.oid = oid;
		result.logtag = log_tag;
		result.noSuchObjectException = noSuchObjectException;
		result.type = DELETE_TYPE;
		return result;
	}

	public static RecordState newLinkOp(int status, long startTime, Long endTime, long api_time,
			String info, ObjectIdentifier linkOID,
			NameValueRecord metadata, String log_tag,
			SystemRecord systemRecord) {
		RecordState result = new RecordState();
		result.status = status;
		result.startTime = startTime;
		result.endTime = endTime;
		result.api_time = api_time;
		result.info = info;
		result.linkOID = linkOID;
		result.metadata = metadata;
		result.logtag = log_tag;
		result.systemRecord = systemRecord;
		result.type = LINK_TYPE;
		return result;
	}
}

