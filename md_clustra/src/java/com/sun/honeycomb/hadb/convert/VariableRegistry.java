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



package com.sun.honeycomb.hadb.convert;

import java.util.Set;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.parsers.QueryExpression;
import com.sun.honeycomb.emd.parsers.QueryAttribute;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.emd.config.Field;
import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.hadb.AttributeTable;

public class VariableRegistry 
    implements ConvertConstants {
    
    private AttributeTable attrTable;
    private Variable referenceVariable;
    private Variables variables;
    private static final Logger LOG = Logger.getLogger(VariableRegistry.class.getName());
    
    public VariableRegistry() {
        referenceVariable = null;
        variables = new Variables();
    }
    
    void setAttrTable(AttributeTable nAttrTable) {
        attrTable = nAttrTable;
    }

    public void putVariable(QueryExpression expr)
        throws EMDException {
        putVariable(expr.getAttribute(), expr.getAttributeType());
    }
    public void putVariable(QueryAttribute expr)
    throws EMDException {
        putVariable(expr.getAttribute(), expr.getAttributeType());
    }
    
    public void putVariable(String name, int type)
        throws EMDException {
        if (variables.contains(name)) {
            return;
        }
        if (name.equals(OID_ATTRIBUTE)) {
            return;
        }
        
        Variable var = new Variable(name, type);
        variables.add(var);
        
        if (referenceVariable == null) {
            referenceVariable = var;
        }
    }

    public void putVariable(Field field)
        throws EMDException {
	String varName = field.getQualifiedName();
        if (variables.contains(varName)) {
            return;
        }
        if (varName.equals(OID_ATTRIBUTE)) {
            return;
        }
        
        Variable var = new Variable(field);
        variables.add(var);
        
        if (referenceVariable == null) {
            referenceVariable = var;
        }
    }
    
    public void appendOID(StringBuffer sb) throws EMDException {
        sb.append(FIELD_OBJECTID);
    }
    
    public void appendSelect(StringBuffer sb,
            List attributes)
            throws EMDException {
        if (referenceVariable == null) {
            putVariable(INSERT_COMMIT_PROPERTY, INSERT_COMMIT_TYPE);
        }
        
        appendOID(sb);
        
        if ((attributes != null) && (attributes.size() > 0)) {
            for (int i=0; i<attributes.size(); i++) {
                String attrName = (String)attributes.get(i);
                if (!attrName.equals(OID_ATTRIBUTE)) {
                    sb.append(", ");
                    appendRepresentation(sb, attrName);
                }
            }
        }
    }
    
    private int walk(boolean includeReference,
            Walker walker)
            throws EMDException {
        Iterator entries = variables.iterator();
        int nbElems = 0;
        
        while (entries.hasNext()) {
            Variable entry = (Variable)entries.next();
            if ((!includeReference) && (entry.equals(referenceVariable))) {
                continue;
            }
            if (nbElems>0) {
                walker.insertLink();
            }
            walker.position = nbElems;
            walker.insertElement(entry);
            nbElems++;
        }
        
        return(nbElems);
    }

    public void appendFrom(StringBuffer sb, boolean outerJoin)
    throws EMDException {
        Set tableNames = new LinkedHashSet();
        Iterator entries = variables.iterator();

        //Get the list of table names from the current query variables
        while (entries.hasNext()) {
            Variable entry = (Variable)entries.next();

            // OID is in every table, don't add "system" on its account
            if (entry.name.equals(OID_ATTRIBUTE))
                continue;

            tableNames.add(entry.table);
        }

        if (tableNames.size() < 1) {
            throw new EMDException("There must be at least one table name in the generated query");
        }


        //Now generate a FROM clause of the form:
        // ((tableOne INNER JOIN tableTwo USING (objectid)) INNER JOIN tableThree USING (objectid))
        for (int i=0; i<tableNames.size()-1; i++) {
            sb.append("(");
        }
        Iterator tableEntries = tableNames.iterator();
        sb.append((String)tableEntries.next());
        while (tableEntries.hasNext()) {
            String tableName = (String)tableEntries.next();
            if (outerJoin)
                sb.append(" left outer join ");
            else 
                sb.append(" inner join ");
            sb.append(tableName);              
            sb.append(" using (objectid))");
        }
 
    }
    
    public void appendCookie(StringBuffer sb,
                             NewObjectIdentifier lastOid,
                             List literals,
                             boolean useLegacyOid) {
        if (lastOid == null) {
            return;
        }
        
        sb.append(FIELD_OBJECTID);
        sb.append(">?");
        if (useLegacyOid) {
            // 1.0 Tables require the OID in String format
            literals.add(lastOid.toLegacyHexString());
            LOG.info("Using lastOid in legacy string format: "+lastOid.toLegacyHexString());
        } else {
            literals.add(lastOid.getDataBytes());
        }
    }
    
    public void appendOrderBy(StringBuffer sb) {
        sb.append(" ORDER BY ");
        sb.append(FIELD_OBJECTID);
    }
    
    public void appendRepresentation(StringBuffer sb,
                                     String name)
        throws EMDException {
        Variable var = (Variable)variables.get(name);
        if (var == null) {
            if (name.equals(OID_ATTRIBUTE)) {
                appendOID(sb);
                return;
            }
            throw new EMDException("Variable ["+
                    name+"] is not in the variable registry");
        }
        
        sb.append(var.representation);
    }
    
    class Variable {
        public String representation;
        public String name;
        public int type;
        public String table;

        public Variable(String nName,
                        int nType)
            throws EMDException {
            this(RootNamespace.getInstance().resolveField(nName));
        }

        public Variable(Field field)
            throws EMDException {

            //QueryConvert.populateRegistry should have already been called.

            type = field.getType();
            name = field.getQualifiedName();
            table = attrTable.getTableNameForField(field);
            representation = table+"."+field.getTableColumn().getColumnName();

        }
        
        public boolean equals(Object other) {
            if (other instanceof String) {
                return(name.equals((String)other));
            }
            if (!(other instanceof Variable)) {
                return(false);
            }
            return(name.equals(((Variable)other).name));
        }
    }
    
    private abstract class Walker {
        protected StringBuffer sb;
        public int position;
        
        public Walker(StringBuffer nSb) {
            sb = nSb;
        }
        
        public abstract void insertLink();
        
        public abstract void insertElement(Variable var)
        throws EMDException;
    }
}
