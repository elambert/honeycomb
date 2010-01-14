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



package com.sun.honeycomb.hctest.hadb.generators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;



import com.sun.honeycomb.hctest.hadb.MetadataAttribute;
import com.sun.honeycomb.hctest.hadb.schemas.MetadataSchema;
import com.sun.honeycomb.hctest.hadb.schemas.OfotoSchema;

public class OfotoGenerator implements MetadataGenerator {

	public OfotoGenerator () {
		m_attrNames = m_schema.getFields();
		Iterator iter = m_attrNames.iterator();
		TreeSet ts = new TreeSet();
		while (iter.hasNext()) {
			String fieldName = (String) iter.next();
			ts.add(new OfotoNode(fieldName,m_schema.getLegalValues(fieldName)));
		}
		Iterator tsIter = ts.iterator();
		rootNode = (OfotoNode) tsIter.next();
		OfotoNode prevNode = rootNode;
		while (tsIter.hasNext()) {
			OfotoNode curNode = (OfotoNode)tsIter.next();
			prevNode.child = curNode;
			prevNode = curNode;
		}
	}
	
	public void setAttributeSize(long size) {
		//Ignored by this generator
	}

	public long getAttributeSize() {
		return OFOTO_ATTR_SIZE;
	}

	public Collection generateMetaData() {
		ArrayList list = new ArrayList();
		rootNode.getNextPath(list);
		return list;
	}

	public MetadataSchema getSchema() {
		return m_schema;
	}
	
	public void printMetadata(Collection c) {
		Iterator iter = c.iterator();
		StringBuffer sb = new StringBuffer();
		while (iter.hasNext()) {
			MetadataAttribute md = (MetadataAttribute)iter.next();
			sb.append(md.getAttrValue() + "/");
		}
		
		System.out.println(sb.toString());
	}
	
	
	public static void main (String [] args) {
		OfotoGenerator og = new OfotoGenerator();
		System.out.println("Root node  is " + og.rootNode.nodeName);
		OfotoNode curNode = og.rootNode.child;
		while (curNode != null) {
			System.out.println("next node  is " + curNode.nodeName);		
			curNode = curNode.child;
		}
		
		for (int i = 0; i < 15; i++) {
			og.printMetadata(og.generateMetaData());
		}
	}
	
	
	private OfotoSchema m_schema = new OfotoSchema(); 
	private static final long OFOTO_ATTR_SIZE = 7;
	private Collection m_attrNames = null;
	private OfotoNode rootNode = null;
	
	class OfotoNode implements Comparable{
		
		OfotoNode(String name, String [] values ) {
			this.nodeName = name;
			this.values = values;
		}
		
		public boolean equals(Object that) {
			if (that == null) {
				return false;
			}
			
			if (! (that instanceof OfotoNode)) {
				return false;
			}
			
			OfotoNode onode = (OfotoNode)that;
			return onode.nodeName.equals(this.nodeName);
		}
		
		public int compareTo(Object that) {
			if (this.equals(that)) {
				return 0;
			}
			OfotoNode onode = (OfotoNode)that;
			return this.nodeName.compareTo(onode.nodeName);
		}
		
		public void addChild( OfotoNode child) {
			this.child = child;
		}
		
		public boolean getNextPath (ArrayList list) {
			list.add(new MetadataAttribute(nodeName, nodeName + values[nextValuePtr]));
			if (child == null) {
				if (++nextValuePtr == values.length) {
					nextValuePtr = 0;
					return true;
				} else {
					return false;
				}
			} else {
				if (child.getNextPath(list)) {
					if (++nextValuePtr == values.length) {
						nextValuePtr = 0;
						return true;
					}
				} 
			}
			return false;
		}
		
		private String nodeName = null;
		private int nextValuePtr = 0;
		private String [] values;
		private OfotoNode child = null;
	}
	
}