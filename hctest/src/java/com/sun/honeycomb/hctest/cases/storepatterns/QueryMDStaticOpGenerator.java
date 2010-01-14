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

import com.sun.honeycomb.hctest.util.HoneycombTestConstants;
import com.sun.honeycomb.test.TestRunner;
import com.sun.honeycomb.test.util.HoneycombTestException;
import com.sun.honeycomb.test.util.Log;

/*
 * Dummy Query Metadata OperationGenerator
 * creates dumb MD objects to associate with existing Objects.
 */
public class QueryMDStaticOpGenerator extends MDStaticOpGenerator {
	
	long timestart = System.currentTimeMillis();
	long iteration = 0;
	
	public final static int SIMPLE_QUERY = 0;
	public final static int COMPLEX_QUERY = 1;
	public final static int EMPTY_QUERY = 2;
    public final static int SPECIAL_QUERY = 3;
	
	private static int querytype = 0; // 0 is simple;
	
	public static int getQueryType(){
		return querytype;
	}
	
	public QueryMDStaticOpGenerator() throws Throwable {
		String query_type = TestRunner.getProperty(HoneycombTestConstants.PROPERTY_QUERY_TYPE);
		if (query_type != null)
			if (query_type.equalsIgnoreCase("complexquery")){
                            querytype = COMPLEX_QUERY;
			} else if (query_type.equalsIgnoreCase("emptyquery")){
                            querytype = EMPTY_QUERY;
			} else if (query_type.equalsIgnoreCase("simplequery")){
                            querytype = SIMPLE_QUERY;
                        } else if (query_type.equalsIgnoreCase("specialquery")){
                            querytype = SPECIAL_QUERY;
                        } else {
                            // Default to simple
                            querytype = SIMPLE_QUERY;
                            Log.WARN("Defaulting to simple query mode, unknown query type: " + query_type);
			}
	}
	
	public Operation getOperation(ArrayList fileSizes) throws HoneycombTestException {
		Operation op = new Operation(Operation.QUERYMETADATA);
		String query = null;
		
		if (querytype == COMPLEX_QUERY ) {
                    // Ofoto-like browsing query: give me leaves of 6-level-deep dir tree
                    query = 
                        attr("first")  + "=" + val(getDir()) + " AND " +
                        attr("second") + "=" + val(getDir()) + " AND " +
                        attr("third")  + "=" + val(getDir()) + " AND " +
                        attr("fourth") + "=" + val(getDir()) + " AND " +
                        attr("fifth")  + "=" + val(getDir()) + " AND " +
                        attr("sixth")  + "=" + val(getDir());
		} else if (querytype == EMPTY_QUERY){
                    query = attr("user") + "=" + val("Impossible Value");
		} else if (querytype == SPECIAL_QUERY) {
                    query = attr("system.object_size") + "< 10000";
                } else {
                    // Default simple query type
                    query = attr("user") + "=" + val(users[user_count++%users.length]);
		}
		
		op.setQuery(query);		
		return op;	
	}

    /* Atrribute in query must be in double quotes
     */
    private String attr(String attrName) {
        return "\"" + attrName + "\"";
    }

    /* Value in query must be in single quotes 
     */
    private String val(String value) {
        return "'" + value + "'";
    }
}
