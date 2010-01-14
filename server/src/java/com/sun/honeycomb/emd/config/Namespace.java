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



package com.sun.honeycomb.emd.config;

import java.util.HashMap;
import java.io.IOException;
import java.util.Iterator;
import java.util.ArrayList;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Namespace {

    public static final String TAG_NAMESPACE = "namespace";
    public static final String ATT_NAME = "name";
    public static final String ATT_WRITABLE = "writable";
    public static final String ATT_EXTENSIBLE = "extensible";
    public static final int MAX_NAME_LENGTH = 63;

    private Namespace parent;

    private String name;
    private String qualifiedName;
    private boolean writable = true;
    private boolean extensible = true;
    private boolean factoryDefault = false;

    private HashMap fields = new HashMap();
    private HashMap children = new HashMap();

    /** default constructor */
    protected Namespace() {
    }
 
    /**
     * Constructor.
     * @param parent the parent Nanespace object.
     * @param name the name of this namespace.
     * @param writable a boolean value indicates if this namespace is writable.
     * @param extensible a boolean value indicates if this namespace is
     * extensible.
     * @param factoryDefault a boolean value indicates if this is a 
     * factory-default namespace.
     */
    public Namespace(Namespace parent,
                     String name,
                     boolean writable,
                     boolean extensible,
                     boolean factoryDefault) {
        this.parent = parent;
        this.name = name;
        this.qualifiedName = (parent.qualifiedName == null)
            ? name : parent.qualifiedName + "." + name;
        this.writable = parent.writable && writable;
//        this.extensible = parent.extensible && extensible; 
        this.extensible =  extensible; 
        this.factoryDefault = parent.factoryDefault || factoryDefault;
        parent.registerChild(this);
    }

    /**
     * Get the current value of name attribute.
     * @return String the current value of name attribute.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the current value of extensible attribute.
     * @return boolean true if this namespace is extensible, otherwise false.
     */
    public boolean isExtensible() {
        return extensible;
    }

    /**
     * Set the current value of extensible attribute.
     * @param bool a boolean value to indicate whether the namespace is 
     * extensible or not.
     */
    public void setExtensible(boolean bool) {
        // OK to change namespace from extensible to non-extensible.
        // However, not vice versa.
        if (!extensible && bool) {
            throw new IllegalArgumentException(
                "Namespace [" + getQualifiedName() +
                "] can not be changed from non-extensible to extensible.");
        }
        extensible = bool;
    }

    /**
     * Get the current value of writable attribute.
     * @return boolean true this namespace is writeable, otherwise false. 
     */
    public boolean isWritable() {
        return writable;
    }

    /**
     * Get the current value of factoryDefault attribute.
     * @return boolean true this is a factory-default namespace,
     * otherwise false.  
     */
    public boolean isFactoryDefault() {
        return factoryDefault;
    }

    /**
     * Get the QualifiedName of this namespace.
     * @return String the QualifiedName of this namespace.
     */
    public String getQualifiedName() {
        return qualifiedName;
    }

    public void getQualifiedName(StringBuffer buffer,
                                 String separator) {
        if (parent == null) {
            // Root element, no qualifiedName
            return;
        }

        if (parent.parent != null) {
            // Need a separator
            buffer.append(separator);
        }

        buffer.append(name);
    }

    public void getFields(ArrayList list,
                          boolean recursive) {
        list.addAll(fields.values());
        if (recursive) {
            Iterator ite = children.values().iterator();
            while (ite.hasNext()) {
                ((Namespace)ite.next()).getFields(list, recursive);
            }
        }
    }

    /**
     * Check to see if this namespace has any fields defined.
     * @return boolean true if this namespace has no field define, 
     * otherwise return false;
     */
    public boolean hasNoField() {
        return fields.isEmpty();
    }

    /**
     * Check to see if this namespace has any sub-namespaces defined.
     * @return boolean true if this namespace has no sub-namespace defined, 
     * otherwise return false;
     */
    public boolean hasNoChild() {
        return children.isEmpty();
    }

    /**
     * Check to see if this namespace any fields or sub-namespaces defined. 
     * @return boolean true if this namespace has neither field nor 
     * sub-namespace defined, otherwise return false;
     */
    public boolean hasNoDependent() {
        return hasNoField() && hasNoChild();
    }

    /**
     * Get the parent namespace.
     * @return Namespace the parent namespace.
     */
    public Namespace getParent() { 
        return parent;
    }
    
    public void getChildren(ArrayList list,
                            boolean recursive) {
        list.addAll(children.values());
        if (recursive) {
            Iterator ite = children.values().iterator();
            while (ite.hasNext()) {
                ((Namespace)ite.next()).getChildren(list, recursive);
            }
        }        
    }

    public void registerChild(Namespace child) {
        children.put(child.getName(), child);
    }
    
    // unregister using the NON-qualified name
    public void unregisterChild(String nsName) {
        children.remove(nsName);
    }

    protected Namespace getChild(String name) {
        return((Namespace)children.get(name));
    }
    
    public void addField(Field field) {
        fields.put(field.getName(), field);
    }

    public Field getField(String name) {
        return((Field)fields.get(name));
    }
    
    public Map getFields() {
        return fields;
    }
    
    public void removeField(String fieldName) {
        fields.remove(fieldName);
    }
    
    public void removeFields(String[] fieldNames) {
        for (int idx = 0; idx < fieldNames.length ; idx++) {
            removeField(fieldNames[idx]);
        }
    }

    private void export(Writer out,
                        String prefix,
                        boolean includeFactoryDefault) 
        throws IOException {
        if (factoryDefault && (!includeFactoryDefault)) {
            return;
        }
        out.write(prefix+"<"+TAG_NAMESPACE+
                  " "+ATT_NAME+"=\""+
                  name+"\" "+ATT_WRITABLE+"=\""+
                  writable+"\" "+ATT_EXTENSIBLE+"=\""+
                  extensible+"\">\n");

        exportDependants(out, prefix+"  ", includeFactoryDefault);

        out.write(prefix+"</namespace>\n");
    }

    protected void exportDependants(Writer out,
                                    String prefix,
                                    boolean includeFactoryDefault)
        throws IOException {
        Iterator ite = children.values().iterator();
        while (ite.hasNext()) {
            ((Namespace)ite.next()).export(out, prefix, includeFactoryDefault);
        }

        ite = fields.values().iterator();
        while (ite.hasNext()) {
            ((Field)ite.next()).export(out, prefix);
        }
    }

    public Namespace resolveNamespace(String qualifiedName) {
        String[] names = qualifiedName.split("\\.");
        return(resolveNamespace(names, 0));
    }

    public Field resolveField(String qualifiedName) {
        String[] names = qualifiedName.split("\\.");
        return(resolveField(names, 0));
    }

    protected Namespace resolveNamespace(String[] names,
                                         int position) {
        Namespace next = (Namespace)children.get(names[position]);
        if (next == null) {
            return(null);
        }

        if (position == names.length-1) {
            return(next);
        }
        return(next.resolveNamespace(names, position+1));
    }

    protected Field resolveField(String[] names,
                                 int position) {
        if (position == names.length-1) {
            //Look for the field
            return((Field)fields.get(names[position]));
        }
        Namespace next = (Namespace)children.get(names[position]);
        if (next == null) {
            return(null);
        }
        return(next.resolveField(names, position+1));
    }

    public void compareDirect(Namespace ns)
        throws EMDConfigException {

        if (writable != ns.isWritable()) {
            throw new EMDConfigException(
                "'writable' parameter differ on namespace " + qualifiedName);
        }
        if (extensible != ns.isExtensible()) {
            throw new EMDConfigException(
                "'extensible' parameter differ on namespace " + qualifiedName);
        }

        Map nsFields = ns.getFields();
        if (fields.size() != nsFields.size()) {
            throw new EMDConfigException(
                "number of fields differ on namespace " + qualifiedName);            
        }
        ArrayList list = new ArrayList();
        getFields(list, false);
        for (int i = 0; i < list.size(); i++) {
            Field curField = (Field) list.get(i);
            Field matchingField = (Field) nsFields.get(curField.getName());
            if (matchingField == null) {
                throw new EMDConfigException("field " + curField.getName() +
                                            " does not exist in remote " +
                                            " namepsace " + qualifiedName);
            }
            curField.compareDirect(matchingField);
        }
    }
}
