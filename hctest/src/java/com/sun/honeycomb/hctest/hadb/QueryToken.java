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



package com.sun.honeycomb.hctest.hadb;


public class QueryToken {

	public QueryToken (String attr, String table, String type) {
		this.m_attributeName = attr;
		this.m_table = table; 
		this.m_type = type;
	}
	
	
	public boolean equals(Object obj) {
		
		if (! (obj instanceof QueryToken ))
			return false;
		
		QueryToken qt = (QueryToken)obj;
		if (!(this.getFullyQualifiedName().equals(qt.getFullyQualifiedName())))
			return false;
		else 
			return true;
	}
	
	
	public int hashCode() {
		return this.getFullyQualifiedName().hashCode();
	}
	
	
	public String getFullyQualifiedName () {
		return m_table + "." + m_attributeName;
	}
	
	
	public String getTableName() {
		return m_table;
	}
	
	
	public String getAttributeName() {
		return m_attributeName;
	}
	
	
	public String getType () {
		return m_type;
	}
	
	
	public String getValue () {
		return m_value;
	}
	
	public String m_attributeName;
	public String m_table;
	public String m_type;
	public String m_value;
	public boolean isSelectToken = false;
	
}
