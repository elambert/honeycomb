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

import org.xml.sax.helpers.DefaultHandler;
import java.util.Stack;
import org.xml.sax.SAXException;
import org.xml.sax.Attributes;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Iterator;
import java.io.Writer;
import java.io.IOException;

import com.sun.honeycomb.emd.parsers.FilenameParser;
import com.sun.honeycomb.emd.parsers.ParseException;

import org.apache.commons.codec.net.QuotedPrintableCodec;
import org.apache.commons.codec.EncoderException;

public class XMLConfigHandler
    extends DefaultHandler {

    private static final Logger LOG = 
        Logger.getLogger(XMLConfigHandler.class.getName());

    private final static QuotedPrintableCodec qpCodec = 
        new QuotedPrintableCodec();

    public static boolean CLI_CONTEXT = false;
    public static final String TAG_METADATACONFIG = "metadataConfig";
    public static final String TAG_SCHEMA = "schema";
    public static final String TAG_FSVIEWS = "fsViews";
    public static final String TAG_TABLES = "tables";
    public static final String TAG_COLLATIONS = "collations";

    private boolean factoryDefault;
    private  int newNS;

    private RootNamespace rootNamespace;
    private Stack namespaceStack;
    private Stack tags;
    private FsView currentFsView;
    private Table currentTable;
    private String enclTag;

    public XMLConfigHandler(RootNamespace rootNamespace,
                            boolean factoryDefault) {
        this.rootNamespace = rootNamespace;
        tags = new Stack();
        namespaceStack = new Stack();
        namespaceStack.push(rootNamespace);
        this.factoryDefault = factoryDefault;
    }

    public void createNewNamespace(Attributes atts)
        throws SAXException {
        Namespace parent = (Namespace)namespaceStack.peek();
        String name = null;
        boolean writable = true;
        boolean extensible = true;

        for (int i = 0; i < atts.getLength(); i++) {
            String aName = atts.getQName(i);
            String aValue = atts.getValue(i);

            if (aName.equals(Namespace.ATT_NAME)) {
                name = aValue;
            } else if (aName.equals(Namespace.ATT_WRITABLE)) {
                writable = Boolean.valueOf(aValue).booleanValue();
            } else if (aName.equals(Namespace.ATT_EXTENSIBLE)) {
                extensible = Boolean.valueOf(aValue).booleanValue();
            }
        }

        validateNamespaceName(name, Namespace.TAG_NAMESPACE, 
                new String[] {Namespace.TAG_NAMESPACE, TAG_SCHEMA});
        Namespace childNamespace = parent.resolveNamespace(name);
        if (childNamespace != null) {

            if (CLI_CONTEXT) {
                LOG.info("The namespace [" + 
                    encode(childNamespace.getName()) + "] already exists.");
            }
            updateNamespaceAttributes(childNamespace, extensible);
            namespaceStack.push(childNamespace);
        } else {
            // If parent namespace is a committed namespace,
            // parent must be extebsible in order to have new child.
            validateExtensibility(parent, 
                "Failed to create namespace [" + name + "].");
            if (CLI_CONTEXT) {
                LOG.info("The namespace [" + encode(name) + 
                        "] will be created");
            }
            namespaceStack.push(new Namespace(parent,
                        name, writable, extensible, factoryDefault));
            // increment the namespace depth.
            ++newNS;
        }
    }

    public void createNewField(Attributes atts)
        throws SAXException {

        Namespace namespace = (Namespace)namespaceStack.peek();

        String name = null;
        String type = null;
        boolean queryable = true;
        boolean indexed = false;
        int length = -1;

        for (int i = 0; i < atts.getLength(); i++) {
            String aName = atts.getQName(i);
            String aValue = atts.getValue(i);

            if (aName.equals(Field.ATT_NAME)) {
                name = aValue;
            } else if (aName.equals(Field.ATT_TYPE)) {
                type = aValue;
            } else if (aName.equals(Field.ATT_QUERYABLE) ||
		        aName.equals(Field.ATT_INDEXABLE)) {
                queryable = Boolean.valueOf(aValue).booleanValue();
            } else if (aName.equals(Field.ATT_INDEXED)) {
                indexed = Boolean.valueOf(aValue).booleanValue();
            } else if (aName.equals(Field.ATT_LENGTH)) {
                try {
                    length = Integer.parseInt(aValue);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Illegal numeric format for " +
                            Field.ATT_LENGTH + "=" + aValue, e);
                }
            }
        }

        validateName(name, Field.TAG_FIELD, 
                new String[] {Namespace.TAG_NAMESPACE, TAG_SCHEMA});
        Field field = new Field(namespace, name, type, length, 
                                queryable, indexed);
        Field existing = namespace.resolveField(name);
        if (existing != null) {
            
            try {
                field.checkCompatibility(existing);
            } catch (EMDConfigException e) {
                    throw new IllegalArgumentException(e.getMessage());
            }
            if (CLI_CONTEXT) {
                LOG.info("Field [" + encode(name) +
                        "] already exists in namespace [" +
                        encode(namespace.getQualifiedName()) + "]");
            }
            updateFieldAttributes(namespace, existing, queryable, indexed); 
        } else {
        
            // To add new field, namespace must be extensible.
            validateExtensibility(namespace,
                    "Failed to Create Field [" + name + "].");
            namespace.addField(field);
            if (CLI_CONTEXT) {
                LOG.info("A field [" + encode(field.getQualifiedName()) +
                    "] of type [" + encode(type) + 
                    "] will be created in namespace [" + 
                    encode(namespace.getQualifiedName()) + "].");
            }
        }
    }

    public void createNewFsView(Attributes atts) 
        throws SAXException {
        String name = null;
        String filename = null;
        String[] archiveTypes = null;
        Namespace namespace = null;
        String unsetValue = null;
        boolean fsAttrs = false;
        boolean readOnly = false;
        boolean collapsingNulls = false;

        for (int i = 0; i < atts.getLength(); i++) {
            String aName = atts.getQName(i);
            String aValue = atts.getValue(i);
            
            if (aName.equals(FsView.ATT_NAME)) {
                name = aValue;
            } else if (aName.equals(FsView.ATT_FILENAME)) {
                filename = aValue;

            /* CR 6563045: disable "deep archive" for 1.1
            } else if (aName.equals(FsView.ATT_ZOPEN)) {
            archiveTypes = aValue == null ? null : aValue.split(","); */

            } else if (aName.equals(FsView.ATT_UNSET)) {
                unsetValue = aValue;
            } else if (aName.equals(FsView.ATT_FSATTRS)) {
                fsAttrs = getBoolValue(aValue);
            } else if (aName.equals(FsView.ATT_READONLY)) {
                readOnly = getBoolValue(aValue);
            } else if (aName.equals(FsView.ATT_FILESLEAFLEVEL)) {
                // collapsingNulls is the opposite of filesonlyatleaflevel 
                // so invert the boolean value 
                collapsingNulls = !getBoolValue(aValue);
            } else if (aName.equals(FsView.ATT_NAMESPACE)) {
                namespace = rootNamespace.resolveNamespace(aValue);
                if (namespace == null) {
                    throw new IllegalArgumentException(
                            "Unknown namespace [" + aValue + "]");
                }
            } else {
                throw new IllegalArgumentException(
                    "Unknown keyword [" + aName + "]");
            }
        }

        validateName(name, FsView.TAG_FSVIEW, new String[] {TAG_FSVIEWS});
        validateFsViewFileName(name, filename);

        currentFsView = new FsView(name, namespace, factoryDefault, 
                                  archiveTypes, unsetValue, readOnly, 
                                  collapsingNulls, fsAttrs);

        currentFsView.setFilename(
                getFileNameObject(filename, currentFsView, rootNamespace));
    }

    public void createNewfsAttribute(Attributes atts) 
        throws SAXException {

        String unsetString = null;
        String name = null;

        for (int i = 0; i < atts.getLength(); i++) {
            String aName = atts.getQName(i);
            String aValue = atts.getValue(i);

            if (aName.equals(FsAttribute.ATT_NAME)) {
                name = aValue;
            } else if (aName.equals(FsAttribute.ATT_UNSET)) {
                unsetString = aValue;
            }
        }

        validateName(name, FsAttribute.TAG_ATTRIBUTE, 
                new String[] {FsView.TAG_FSVIEW});
        currentFsView.addAttribute(name, unsetString, rootNamespace);
    }

    public void createNewTable(Attributes atts) 
        throws SAXException {
        String name = null;
        boolean autoGen = false;

        for (int i = 0; i < atts.getLength(); i++) {
            String aName = atts.getQName(i);
            String aValue = atts.getValue(i);
            
            if (aName.equals(Table.ATT_NAME)) {
                name = aValue;
            } else if (aName.equals(Table.ATT_AUTOGEN)) {
                autoGen = getBoolValue(aValue);
            }
        }

        validateName(name, Table.TAG_TABLE, new String[] {TAG_TABLES});
        currentTable = new Table(name, factoryDefault, autoGen);
    }

    public void createNewColumn(Attributes atts)
        throws SAXException {

        String name = null;
        for (int i = 0; i < atts.getLength(); i++) {
            String aName = atts.getQName(i);
            String aValue = atts.getValue(i);

            if (aName.equals(Column.ATT_NAME)) {
                name = aValue;
            }
        }

        validateName(name, Column.TAG_COLUMN, new String[] {Table.TAG_TABLE});
	    currentTable.addColumn(name, rootNamespace);
    }


    public void startElement(String namespaceURI,
                             String localName,
                             String qName,
                             Attributes atts) 
        throws SAXException {

        pushTag(qName);
        if (qName.equals(TAG_METADATACONFIG)) {
            validateRootTag();
        } else if (qName.equals(TAG_SCHEMA) ||
                   qName.equals(TAG_FSVIEWS) ||
                   qName.equals(TAG_COLLATIONS) ||
                   qName.equals(TAG_TABLES)) {
            validateEnclosingTag(qName, TAG_METADATACONFIG);
        } else if (qName.equals(Namespace.TAG_NAMESPACE)) {
            createNewNamespace(atts);
        } else if (qName.equals(Field.TAG_FIELD)) {
            createNewField(atts);
        } else if (qName.equals(FsView.TAG_FSVIEW)) {
            createNewFsView(atts);
        } else if (qName.equals(FsAttribute.TAG_ATTRIBUTE)) {
            createNewfsAttribute(atts);
        } else if (qName.equals(Table.TAG_TABLE)) {
            createNewTable(atts);
        } else if (qName.equals(Column.TAG_COLUMN)) {
            createNewColumn(atts);
        } else {
            throw new IllegalArgumentException(
                "Found an unrecognized tag <" + qName + ">.");
        }
    }
    
    public void endElement(String namespaceURI,
                           String localName,
                           String qName)
        throws SAXException {
        tags.pop();
        if (qName.equals(Namespace.TAG_NAMESPACE)) {
            removeNamespaceFromStack();
        } else if (qName.equals(FsView.TAG_FSVIEW)) {
            processCurrentView();
        } else if (qName.equals(Table.TAG_TABLE)) {
            processCurrentTable();
	    }
    }

    /**
     * Process the matadata table definition.  If a new table definition
     * is found, a new table is added to the current namespace.  If the
     * new table definition is compatible with the existing table 
     * definition, ignore the new table.
     * @throws IllegalArgumentException on error.
     */
    public void processCurrentTable() {

        String  name = currentTable.getName();
        Table pTable = rootNamespace.getTable(name.toUpperCase());
        if (null == pTable) {
            // the currentTable is brand new.
            try {
                rootNamespace.addTable(currentTable);
            } catch (EMDConfigException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
            if (CLI_CONTEXT) {
                LOG.info("The table [" + encode(name) + "] will be created");
                Iterator it = currentTable.getColumns().iterator();
                while (it.hasNext()) {
                   Column c = (Column)it.next();
                   LOG.info("A column for [" + encode(c.getName()) +
			           "] will be created in table [" + 
                       encode(currentTable.getName()) + "]");
                }
            }
        } else {
                // check the compability:
                // 1) table names are the same.
                // 2) current table is the same as or a subset of the 
                //    existing table.
                try {
                    currentTable.checkCompatibility(pTable);
                } catch (EMDConfigException e) {
                    throw new IllegalArgumentException(e.getMessage());
                }
                if (CLI_CONTEXT) {
                    LOG.info(
                            "The table [" + encode(name) + "] already exists.");
                }
        }

        currentTable = null;
    }

    /**
     * Process the matadata FsView definition.  If a new FsView definition
     * is found, a new FsView is added to the current namespace.  If the
     * new FsView definition is compatible with the existing FsView 
     * definition, ignore the new FsView. 
     * @throws IllegalArgumentException on error.
     */
    public void processCurrentView() {
        String name = currentFsView.getName();
        FsView pFsView = rootNamespace.getFsView(name);
        if (null == pFsView) {
            // the currentFsView is brand new.
            try {
                rootNamespace.addFsView(currentFsView);
            } catch (EMDConfigException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
            if (CLI_CONTEXT) {
                LOG.info("The FsView [" + encode(name) + "] will be created");
            }
        } else {
            try {
                // check the compability:
                currentFsView.compareDirect(pFsView);
            } catch (EMDConfigException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
            if (CLI_CONTEXT) {
                LOG.info(
                    "The FsView [" + encode(name) + "] already exists.");
            }
        }

        currentFsView = null;
    }

    public void removeNamespaceFromStack() {
        if (newNS > 0) {
            // decrement the namespace depth
            --newNS;
        }
        Namespace ns = (Namespace)namespaceStack.pop();
        // Remove the non-extensible namespace with no field and
        // no sub-namespace.
        if (!ns.isExtensible() && ns.hasNoDependent()) {
            ns.getParent().unregisterChild(ns.getName());   
            if (CLI_CONTEXT) {
                LOG.info("The namespace [" + encode(ns.getName()) +
                      "] is removed.  It does not contain any fields " +
                      "or sub-namespaces.");
            }
        }
    }
    
    private Filename getFileNameObject(String fileName,
                                       FsView view,
                                       RootNamespace rootNS) {
        try {
            return FilenameParser.parse(fileName, view, rootNS);
        } catch (EMDConfigException e) {
            throw new IllegalArgumentException(
                  "Parsing filename [" + fileName +
                  "] in view [" + view.getName() + "]: " + e.getMessage());
        } catch (ParseException e) {
            throw new IllegalArgumentException(
                  "Parse failed for filename [" + fileName +
                  "] in view [" + view.getName() + "]: " + e.getMessage());
        }
    }

    private boolean getBoolValue(String boolStr) {
        return null == boolStr ? false :
            "true".equalsIgnoreCase(boolStr) ||
            "yes".equalsIgnoreCase(boolStr);
    }

    private void validateName(String name, String tagName, String[] eTags) {
        if (name == null || "".equals(name.trim())) {
            throw new IllegalArgumentException(
                    "A " + tagName + " tag misses a name");
        }

        if (name.length() > Namespace.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Attribute name can not be longer than " + 
                    Namespace.MAX_NAME_LENGTH + " Characters.");
        }

        validateEnclosingTag(name, tagName, eTags);
    }

    // Validate the enclosing tag.
    public void validateRootTag() {
        if (null != enclTag) {
            throw new IllegalArgumentException(
                "Tag <" + TAG_METADATACONFIG + 
                "> can not be enclosed in tag <" + enclTag + ">.");
        }
    }

    public void validateEnclosingTag(String tag, String eTag) {
        if (!enclTag.equals(eTag)) {
            throw new IllegalArgumentException(
                "Found tag <" + tag + "> without an enclosing tag <" +
                eTag + ">.");
        }
    }

    private void validateEnclosingTag(String name, 
            String tagName, 
            String[] eTags) {
        if (0 == eTags.length) {
            return;
        }
        int last = eTags.length;
        for (int i = 0; i < last; i++) {
            if (enclTag == eTags[i]) {
                return;
            }
        }
        // throws IllegalArgumentException.
        StringBuffer tags = new StringBuffer();
        tags.append("<").append(eTags[0]).append(">");
        for (int i = 1; i < last - 1; i++) {
            tags.append(", <").append(eTags[i]).append(">");
        }
        if (last - 1 > 0) {
            tags.append(" or <").append(eTags[last - 1]).append(">");
        }

        throw new IllegalArgumentException(
            "Found <" + tagName + " name=" + name + 
            "> without an enclosing " + tags.toString());
    }

    private void validateNamespaceName(String name, 
            String tagName,
            String[] eTags) {
        validateName(name, tagName, eTags);

        if (name.indexOf(".") != -1) {
            throw new IllegalArgumentException(
                "Cannot use dotted name in namespace definition");
        }
    }
    
    private void validateFsViewFileName(String name, String filename) {
        if (filename == null || "".equals(filename)) {
            throw new IllegalArgumentException(
                    "Found a fsView [" + name + 
                    "] with no filename attribute");
        }
    }

    /**
     * Validate the existing namespace is extensible.
     * @param ns the namespace to be checked.
     * @param msg error message
     */
    private void validateExtensibility(Namespace ns, 
            String msg) {
        // if this is an existing and non-entensible namespace
        if (0 == newNS && !ns.isExtensible()) {
            throw new IllegalArgumentException(
                msg + "  The namespace [" + 
                ns.getQualifiedName() + "] is not extensible");
        }
    }

    /**
     * Update a namespace's attributes.
     * @param ns  the namespace whose attributes to be updated.
     * @param extensible a boolean value indicates if this namespace is 
     * extensible.
     */
    private void updateNamespaceAttributes(Namespace ns, boolean extensible)
        throws IllegalArgumentException {
        ns.setExtensible(extensible);
        if (CLI_CONTEXT && ns.isExtensible() && false == extensible) {
            LOG.info("Namespace [" + encode(ns.getQualifiedName()) + 
                "] has been changed from extensible to non-extensible.");
        }
    }

    /**
     * Update a field's attributes.
     * @param ns the namespace that contains this field.
     * @param fd the field whose attributes to be updated.
     * @param queryable a boolean value indicates if this field is queryable.
     * @param indexed a boolean value indicates if this field is indexed.
     */
    private void updateFieldAttributes(Namespace ns, 
                                  Field fd, 
                                  boolean queryable, 
                                  boolean indexed)
        throws IllegalArgumentException {

        String name = fd.getQualifiedName();

        fd.setQueryable(queryable);
        if (CLI_CONTEXT && !fd.isQueryable() && queryable) {
            LOG.info("Field [" + encode(name) + "] will become queryable");
        }
        if (!fd.isIndexed() && indexed) {
            if (CLI_CONTEXT) {
                LOG.info("Field [" + encode(name) + "] will become indexed");
            }
            fd.setIndexed();
        }
    }
    
    private static String encode (String s){
        try {
            return qpCodec.encode(s);
       }catch(EncoderException ee){
            return s;
       }
    } 

    public void pushTag(String tag) {
        enclTag = tags.isEmpty() ? null : (String)tags.peek();
        tags.push(tag);
    }

    public String popTag() {
        return (String)tags.pop();
    }
}
