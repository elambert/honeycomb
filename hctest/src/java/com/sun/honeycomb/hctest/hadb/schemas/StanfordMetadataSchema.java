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



package com.sun.honeycomb.hctest.hadb.schemas;

import java.util.ArrayList;
import java.util.Collection;

import com.sun.honeycomb.hctest.hadb.Utils;

public class StanfordMetadataSchema implements MetadataSchema {
	
	public StanfordMetadataSchema () {
		m_fields.add("oid");
		m_types.add(Utils.STRING_TYPE);
	}

	public String getName() {
		return m_name;
	}

	public Collection getFields() {
		return m_fields;
	}

	public Collection getTypes() {
		return m_types;
	}

	public String getFieldType(String field) {
		return Utils.STRING_TYPE;
	}
	
	public String [] getLegalValues (String field) {
		String [] values = m_emptyArray;
		return values;
	}
	
	private static final String m_name = "Stanford MetadataSchema";
	private static final ArrayList m_fields = new ArrayList(); 
	private static final ArrayList m_types = new ArrayList();
	private static final String [] m_emptyArray = {};
}
