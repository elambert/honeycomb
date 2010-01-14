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

import java.io.PrintStream;
import java.io.IOException;
import java.util.ArrayList;
import java.io.Writer;


public class FsView {

    public static final String TAG_FSVIEW = "fsView";
    public static final String ATT_NAME = "name";
    public static final String ATT_FILENAME = "filename";
    public static final String ATT_ZOPEN = "zopen";
    public static final String ATT_UNSET = "unset";
    public static final String ATT_NAMESPACE = "namespace";
    public static final String ATT_FSATTRS = "fsattrs";
    public static final String ATT_READONLY = "readonly";
    public static final String ATT_FILESLEAFLEVEL = "filesonlyatleaflevel";

    private String name;
    private Filename filename;
    private Namespace namespace;
    private ArrayList attributes;
    private String[] autoArchiveTypes;
    private String unsetValue;
    private boolean factoryDefault;
    private boolean readOnly;
    private boolean collapseNulls;
    private boolean extendedAttrs;

    public FsView(String name,
                  Namespace namespace,
                  boolean factoryDefault) {
        this(name, namespace, factoryDefault, null, null, false, false, false);
    }

    public FsView(String name,
                  Namespace namespace,
                  boolean factoryDefault,
                  String[] autoArchiveTypes,
                  String unsetValue,
                  boolean readOnly,
                  boolean collapseNulls,
                  boolean extendedAttrs) {
        this.name = name;
        this.autoArchiveTypes = autoArchiveTypes;
        this.namespace = namespace;
        attributes = new ArrayList();
        this.unsetValue = unsetValue;
        this.factoryDefault = factoryDefault;
        this.readOnly = readOnly;
        this.collapseNulls = collapseNulls;
        this.extendedAttrs = extendedAttrs;
    }

    public void setFilename(Filename filename) {
        this.filename = filename;
        if (!readOnly && !filename.isWritable())
            readOnly = true;
    }

    public void setArchiveTypes(String[] archiveNames) {
        autoArchiveTypes = archiveNames;
    }

    public void setUnsetValue(String v) {
        unsetValue = v;
    }

    public void setUsesExtendedAttrs(boolean v) {
        extendedAttrs = v;
    }

    public void addAttribute(String name, 
            String unsetString, 
            RootNamespace namespace) {
        Field fd = null;
        try {
            fd = resolveField(name, namespace);
        } catch (EMDConfigException e) {
            throw new IllegalArgumentException(
                  "Failed to create the fsView [" +
                  name + "] - " + e.getMessage());
        }
        addAttribute(new FsAttribute(fd, unsetString, this));
    }

    public void addAttribute(FsAttribute attribute) {
        if (!attribute.getField().isQueryable()) {
            throw new IllegalArgumentException(
                  "Failed to create the fsView [" + name + 
                  "]. Attribute [" + attribute.getField().getName() +
                  "] is not queryable");
        }
        attributes.add(attribute);
        if (!readOnly && !attribute.getField().isWritable())
            readOnly = true;
    }

    public String getName() {
        return(name);
    }

    public Filename getFilename() {
        return(filename);
    }

    public Namespace getNamespace() {
        return(namespace);
    }

    public String[] getArchiveTypes() {
        return(autoArchiveTypes);
    }

    public String getUnsetValue() {
        return(unsetValue);
    }

    public boolean usesExtendedAttrs() {
        return(extendedAttrs);
    }

    public ArrayList getAttributes() {
        return(attributes);
    }

    public ArrayList getAllVariables() {
        ArrayList retval = new ArrayList();

        // Combine attributes with variables required for the filename

        for (int i = 0; i < attributes.size(); i++) {
            FsAttribute att = (FsAttribute) attributes.get(i);
            retval.add(att.getField().getQualifiedName());
        }

        retval.addAll(filename.getNeededAttributes());

        return retval;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean v) {
        readOnly = v;
    }

    public boolean isCollapsingNulls() {
        return collapseNulls;
    }

    public void setCollapsingNulls(boolean v) {
        collapseNulls = v;
    }

    public String toString() {
        return "<fsView " + name + ">";
    }

    public Field resolveField(String input)
        throws EMDConfigException {
        return resolveField(input, null);
    }

    public Field resolveField(String input, RootNamespace rootNamespace)
        throws EMDConfigException {
        Field result = null;

        if (namespace != null) {
            result = namespace.resolveField(input);
        }

        if (result == null) {
            if (rootNamespace == null) {
                result = RootNamespace.getInstance().resolveField(input);
            } else {
                result = rootNamespace.resolveField(input);
            }
        }

        if (result == null) {
            String error = null;
            if (namespace != null) {
                error = "Neither \"" +
                    namespace.getQualifiedName() + "."+input + "\" nor \"" +
                    input + "\" are valid attributes";
            } else {
                error = "No such attribute: \"" + input + "\"";
            }
            throw new EMDConfigException(error);
        }

        return result;
    }

    public String dequalifyField(Field field) {
        String qualifiedName = field.getQualifiedName();
        
        if (namespace == null) {
            return(qualifiedName);
        }
        if (!qualifiedName.startsWith(namespace.getQualifiedName() + ".")) {
            return qualifiedName;
        }
        
        return field.getQualifiedName().
            substring(namespace.getQualifiedName().length() + 1);
    }

    public void export(Writer out,
                       String prefix,
                       boolean includeFactoryDefault)
        throws IOException {
        if (factoryDefault && (!includeFactoryDefault)) {
            return;
        }

        out.write(prefix + "<fsView name=\"" + name + "\" filename=\"" +
                     filename.toEncodedString() + "\"");
        if (namespace != null) {
            out.write(" namespace=\"" + namespace.getQualifiedName() + "\"");
        }

        if (autoArchiveTypes != null) {
            out.write(" " + ATT_ZOPEN + "=\"");
            String s = "";
            for (int i = 0; i < autoArchiveTypes.length; i++)
                s += "," + autoArchiveTypes[i];
            out.write(s.substring(1) + "\"");
        }

        if (unsetValue != null)
            out.write(" " + ATT_UNSET + "=\"" + unsetValue + "\"");

        out.write(" " + ATT_FSATTRS + "=\"" +
                (extendedAttrs ? "true" : "false") + "\"");

        if (isReadOnly())
            out.write(" " + ATT_READONLY + "=\"true\"");

        // use the new keyword filesonlyatleaflevel instead
        out.write(" " +  ATT_FILESLEAFLEVEL + "=\"" +
            (isCollapsingNulls() ? "false" : "true") + "\""); 

        out.write(">\n");

        for (int i = 0; i < attributes.size(); i++) {
            FsAttribute att = (FsAttribute)attributes.get(i);
            att.export(out, prefix + "  ");
        }

        out.write(prefix + "</fsView>\n");
    }

    public boolean sameAttributes(FsView other) {
        if (attributes.size() != other.attributes.size()) {
            return false;
        }
        
        for (int i = 0; i < attributes.size(); i++) {
            String n1 = 
                ((FsAttribute)attributes.get(i)).getField().getQualifiedName();
            String n2 = ((FsAttribute)other.attributes.get(i)).
                                getField().getQualifiedName();
            if (!n1.equals(n2)) {
                return false;
            }
        }
        
        return true;
    }

    public void compareDirect(FsView view) 
        throws EMDConfigException {

        if (!filename.toString().equals(view.getFilename().toString())) {
            throw new EMDConfigException("The filename for the view " + 
              name + " differ from the hive to the remote new cell");
        }
        if (readOnly != view.isReadOnly()) {
            throw new EMDConfigException("The 'readonly' attribute for the " +
              "view " + name + " differ from the hive to the remote new cell");
        }
        if ((((namespace != null) && (view.getNamespace() == null))) ||
            ((namespace == null) && (view.getNamespace() != null)) ||
            ((namespace != null) && (view.getNamespace() != null) &&
             (!namespace.getName().equals(view.getNamespace().getName())))) {
            throw new EMDConfigException("The namespace for view " +
              name + " differ from the hive to the remote new cell"); 
        }
        if (((unsetValue != null) && (view.getUnsetValue() == null)) ||
            ((unsetValue == null) && (view.getUnsetValue() != null)) ||
            ((unsetValue != null) && (view.getUnsetValue() != null) &&
             (!unsetValue.equals(view.getUnsetValue())))) {
            throw new EMDConfigException("The 'unset' attribute for the view " +
              name + " differ from the hive to the remote new cell"); 
        }
        if (((autoArchiveTypes != null) && (view.getArchiveTypes() == null)) ||
            ((autoArchiveTypes == null) && (view.getArchiveTypes() != null))) {
            throw new EMDConfigException("The 'zopen' attribute for the view " +
              name + " differ from the hive to the remote new cell"); 
        }
        if ((autoArchiveTypes != null) && (view.getArchiveTypes() != null)) {
            if (autoArchiveTypes.length != view.getArchiveTypes().length) {
            throw new EMDConfigException("The 'zopen' attribute for the view " +
              name + " differ from the hive to the remote new cell"); 
            }
            for (int i = 0; i < autoArchiveTypes.length; i++) {
                if (!autoArchiveTypes[i].equals(view.getArchiveTypes()[i])) {
                    throw new EMDConfigException("The 'zopen' attribute for  " +
                      " the view " + name + 
                      " differ from the hive to the remote new cell"); 
                }
            }
        }
        if (extendedAttrs != view.usesExtendedAttrs()) {
            throw new EMDConfigException("The 'fsAttr' attribute for the view" +
              name + " differ from the hive to the remote new cell"); 
        }
        if (attributes.size() != view.getAttributes().size()) {
            throw new EMDConfigException("The nunmber of attributes for the " +
              "view " + name + " differ from the hive to the remote new cell");
        }
        for (int i = 0; i < attributes.size(); i++) {

            FsAttribute curAttr = (FsAttribute) attributes.get(i);
            String curStr = curAttr.getUnsetString();
            String curFieldName = curAttr.getField().getName();
            boolean found = false;
            for (int j = 0; j < attributes.size(); j++) {
                FsAttribute attr = (FsAttribute) view.getAttributes().get(j);
                String attStr = attr.getUnsetString();
                String attFieldName = attr.getField().getName();
                if (curFieldName.equals(attFieldName)) {
                    if (curStr != null && attStr == null ||
                        curStr == null && attStr != null ||
                        curStr != null && attStr != null && 
                        !curStr.equals(attStr)) {
                        throw new EMDConfigException("The 'unset' attribute " +
                          " of the field " + curAttr.getField().getName() + 
                          " for the view " + name + 
                          " differ from the hive to the remote new cell"); 
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new EMDConfigException("The attribute " + 
                  curAttr.getField().getName() + " is missing for the view " +
                  name);  
            }
        }
    }
}
