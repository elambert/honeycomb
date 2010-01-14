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



/* Utility class to query for all objects on a cluster, and to output
 *  a list of OIDs to standard out
 * 
 * Usage: java QueryAll <dataVIP>
 * Example: java QueryAll dev327-data > oids.out
 *
 */

package com.sun.honeycomb.hctest.util;

import com.sun.honeycomb.client.NameValueObjectArchive;
import com.sun.honeycomb.client.QueryResultSet;
import com.sun.honeycomb.client.ObjectIdentifier;

public class QueryAll
{

    public static void main (String[] args) {
	if (args.length != 1) {
	    System.out.println
		("Usage: QueryAll <hostname>");
	    System.exit(-1);
	}

	String host = args[0];

	String query = "system.object_id is not null";
	int resultsGroupSize = 2000;

	try {
	    NameValueObjectArchive nameValueObjectArchive = 
		new NameValueObjectArchive(host);
	    
	    QueryResultSet qrs = 
		nameValueObjectArchive.query(query, resultsGroupSize);
	    
	    while (qrs.next()) {
		System.out.println(qrs.getObjectIdentifier().toString());
	    }
	}
	catch (Exception e) {
	    System.out.println(e.getMessage());
	}
    }
}
