/*
 *EXHIBIT A - Sun Industry Standards Source License
 *
 *"The contents of this file are subject to the Sun Industry
 *Standards Source License Version 1.2 (the "License");
 *You may not use this file except in compliance with the
 *License. You may obtain a copy of the 
 *License at http://wbemservices.sourceforge.net/license.html
 *
 *Software distributed under the License is distributed on
 *an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either
 *express or implied. See the License for the specific
 *language governing rights and limitations under the License.
 *
 *The Original Code is WBEM Services.
 *
 *The Initial Developer of the Original Code is:
 *Sun Microsystems, Inc.
 *
 *Portions created by: Sun Microsystems, Inc.
 *are Copyright (c) 2001 Sun Microsystems, Inc.
 *
 *All Rights Reserved.
 *
 *Contributor(s): WBEM Solutions, Inc., AppIQ, Inc.
 */

package javax.wbem.cim;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

/**
 *
 * Path to the specified CIM class or CIM instance or CIM qualifier. 
 * The CIM object path is a reference to CIM elements. It is only valid
 * in context of an active connection to a CIM object manager on a host.
 * In order to uniquely identify a given object on a host, it includes the 
 * namespace, object name,  and  keys (if the object is an instance). 
 * The namespace is taken to  be relative to the namespace that the 
 * CIMClient is currently connected to.
 * A key is a property or set of properties used to uniquely identify an 
 * instance of a class. Key properties are marked with the KEY qualifier. 
 * <p>
 * For example, the object path: 
 * <p>
 * <code>//myserver/root/cimv2:Solaris_ComputerSystem.Name=mycomputer,
 * CreationClassName=Solaris_ComputerSystem</code>
 * <p>
 * has two parts: 
 * <p>
 * <ul>
 * <li><code>//myserver/root/cimv2</code> - The default CIM namespace on 
 * host <code>myserver</code>. 
 * <li><code>Solaris_ComputerSystem.Name=mycomputer,
 * CreationClassName=Solaris_ComputerSystem</code> - A
 * specific Solaris Computer System object. This Solaris computer system is uniquely 
 * identified by two key property values in the format 
 * (key property = value): 
 * <ul>
 *    <li>Name=mycomputer 
 *    <li>CreationClassName=Solaris_ComputerSystem 
 * </ul>         
 * </ul>
 * @author	Sun Microsystems, Inc.
 * @since	WBEM 1.0
 */
public class CIMObjectPath implements Serializable {

    final static long serialVersionUID = 200;

    /**
     * Namespace portion of the CIM object path.
     * @serial
     *
     */
    private String nameSpace 	= "";
    
    /**
     * Class or qualifier portion of the CIM object path.
     * @serial
     *
     */
    private String objectName 	= "";
  
    /**
     * Instance portion of the CIM object path.
     * The instance portion concatenates, or joins
     * together, the key properties in the instance.
     * @serial
     */
    private Vector keys;
     
    /**
     * Host name portion of the CIM object path.
     * @serial
     */   
    private String host = "";

    /**
     * Constructs a default CIM Object Path with empty namespace, objectName
     * and keys.
     *
     */
    public CIMObjectPath() {
        keys = new Vector();
    }


    /**
     * Constructs a CIM Object Path referencing a CIM element. The name
     * can refer to a class name or a qualifier type name, depending on
     * the particular operation being done. In order to refer to instances
     * the keys must be set.
     *
     * @param objectPath the string form of an object path for a CIM element that will be parsed
     * and used to initialize the object
     *
     */
    public CIMObjectPath(String objectPath) {
        this();
        parseOp(objectPath);
    }

    /**
     *
     * Constructs a CIM Object Path referencing a CIM element along with its
     * namespace. The name can refer to a class name or a qualifier type name, 
     * depending on the particular operation being done. In order to refer to 
     * instances the keys must be set.
     *
     * @param elementName  the name of a CIM element.
     * @param nameSpace	   the namespace relative to the current namespace.
     */

    public CIMObjectPath(String elementName, String nameSpace) {
        this(elementName);
        this.nameSpace = CIMNameSpace.validateNameSpace(nameSpace);
    }

    /**
     * Constructs a CIM Object Path referencing the instance
     * identified by the key values contained in the vector. 
     *
     * @param objectName  the name of the class the instance belongs to.
     * @param keys        vector of CIMProperty. The keys of the instance
     *              	  instantiated with key values.
     */

    public CIMObjectPath(String objectName, Vector keys) {
	this(objectName);
	this.keys = (Vector)keys.clone();		
    }

    /**
     * Adds a Key to the object path.
     * @param name   name of the key property
     * @param value  the CIMValue of the key property
     * @exception IllegalArgumentException If the name or value is null
     */ 
    public void addKey(String name, CIMValue value) {
        CIMProperty cp = new CIMProperty(name, value);
        addKey(cp);
    }
    
    /**
     * Adds a Key to the object path.
     * @param cp   The CIMProperty to add
     * @exception IllegalArgumentException If the name or value is null
     */ 
    public void addKey(CIMProperty cp) {

        if ((cp.getName() == null) || (cp.getValue()== null) || cp.getValue().isNull()) {
            //XXX need client API properties file for I18N
            throw new IllegalArgumentException("name or value can not be null");
        }
        
        cp.setKey(true);
        keys.addElement(cp);
    }
    
     /**
      * Gets a key by name.
      *
      * @param  name        the name of the key
      * @return CIMProperty the CIMProperty with the given name, null if not found.
      */
     public CIMProperty getKey(String name) {
         for (Iterator iter = keys.iterator(); iter.hasNext();) {
             CIMProperty key = (CIMProperty) iter.next();

             if(key.getName().equalsIgnoreCase(name)) {
                 return key;
             }
         }
         return null;
     }

    /**
     * Gets the keys for this CIMObjectPath object. 
     * @return Vector of CIMProperty.
     */
    public Vector getKeys() {
	return (Vector)keys.clone();
    }

    /**
     * Sets the keys for this CIMObjectPath.
     *
     * @param v   vector of CIMProperty.
     */
    public void setKeys(Vector v) {
	keys = v;
        for (Enumeration keys = v.elements(); keys.hasMoreElements(); ) {
            CIMProperty key = (CIMProperty) keys.nextElement();
            key.setKey(true);
        }
    }

    /**
     * Gets the namespace for this CIMObjectPath.
     *
     * @return String  name of the namespace
     */
    public String getNameSpace() {
	return nameSpace;
    }
	
    /**
     * Gets the host for this CIMObjectPath.
     *
     * @return String  name of the host
     */
    public String getHost() {
		return host;
    }
	
    /**
     * Gets the object name for this CIMObjectPath. Depending on the type
     * of reference, this object name can be either a class name or a qualifier
     * type name.
     *
     * @return String  name of this object
     */
    public String getObjectName() {
		return objectName;
    }

    /**
     * Parses the specified object name string in to it's relevant parts
     *
     * @param p  String name of this object
     * @deprecated use <code>new CIMObjectPath(String)</code> instead
     */
    public void parse(String p) {
        parseOp(p);
    }

    /**
     * Sets the host for this CIMObjectPath object.
     *
     * @param host  name of the host
     */
    public void setHost(String host) {
	if (host == null) {
	    this.host = "";
	} else {
	    this.host = host;
	}
    }
	
    /**
     * Sets the namespace for this CIMObjectPath object. 
     *
     * @param ns  string name of the namespace
     */
    public void setNameSpace(String ns) {
        nameSpace = CIMNameSpace.validateNameSpace(ns);
    }

    /**
     * Sets the object name for this CIMObjectPath. Depending on the type
     * of reference, this object name can be either a class name or a qualifier
     * type name.
     *
     * @param objectName  name of this object
     */
    public void setObjectName(String objectName) {
        if (objectName != null && objectName.indexOf(':') != -1 ) {
            objectName = objectName.substring(0, objectName.indexOf(':'));
        }
        this.objectName = objectName;
    }

    /**
     * Returns a String representation of the CIMObjectPath.
     * This method is intended to be used only for debugging purposes,
     * and the format of the returned string may vary between
     * implementations.  The returned string may be empty but may not be
     * null.
     *
     * @return string representation of this object path
     */
    public String toString()
    {
        final StringBuffer buffer = new StringBuffer();
        
        final boolean haveHost = host != null && host.length() > 0;
        final boolean haveNameSpace = nameSpace != null && nameSpace.length() > 0;

        if(haveHost) {
            buffer.append("//");
            buffer.append(host);
        }

        if(haveNameSpace) {
            if (nameSpace.charAt(0) != '/') {
                buffer.append('/');
            }
            buffer.append(nameSpace);
        }
        
        if (objectName != null && objectName.length() != 0) {
            if(haveHost || haveNameSpace) {
                buffer.append(':');
            }
            buffer.append(objectName);

            Enumeration e = keys.elements();
            if(e.hasMoreElements()) {
                buffer.append('.');
                while (e.hasMoreElements()) {
                    CIMProperty pe = (CIMProperty)e.nextElement();
                    if (pe != null)
                    {
                        buffer.append(pe.getName());
                        buffer.append('=');
                        CIMValue cv = pe.getValue();
                        if(cv != null) {
                            Object value = cv.getValue();

                            // Strings and ObjectPaths need to be quoted and escaped
                            if (value instanceof String) {
                                buffer.append(StringUtil.quote(value.toString()));
                            }
                            else if (value instanceof CIMObjectPath) {
                              buffer.append(value.toString());
//?? see comment at the end of method                              
//                              buffer.append(quote(value.toString()));
                            }
                            else if (value instanceof CIMDateTime) {
                                buffer.append('\"');
                                buffer.append(value.toString());
                                buffer.append('\"');
                            }
                            else
                            {
                                buffer.append(String.valueOf(value));
                            }
                        }
                        else {
                            buffer.append("null");
                        }
                        
                        if(e.hasMoreElements()) {
                            buffer.append(',');
                        }
                    }
                }
            }
        }
	return buffer.toString();
    }

    /**
     * Computes the hash code for this object path.
     *
     * @return int	the integer representing the hash code 
     * 			for this object path.
     */
    public int hashCode() {
        // <PJA> 22-May-2002
        // Java requires that a.equals(b) => a.hashCode() == b.hashCode()
        // Since equals() is careful to compare object paths irrespective of key order,
        // hashCode must do the same

		// lowercase strings to remove case-sensitivity.
        int hashcode = getObjectName().toLowerCase().hashCode();
        if(getHost() != null) {
            hashcode ^= getHost().toLowerCase().hashCode();
        }
        if(getNameSpace() != null) {
            hashcode ^= getNameSpace().toLowerCase().hashCode();
        }

        for (int i = 0; i < keys.size(); i++)
        {
            CIMProperty property = (CIMProperty) keys.elementAt(i);
            hashcode ^= property.getName().toLowerCase().hashCode();

            if (property.getValue() != null)
            {
                CIMValue value = property.getValue();
                // Don't just hash property.getValue(), as CIMValue doesn't override hashCode
                if (value != null && value.getValue() != null)
                {
                    hashcode ^= value.getValue().hashCode();
                }
            }
        }

        return hashcode;
    }
    
    /**
     * Compares this object path with the specified object path for equality
     *
     * @param  o        the object to compare
     * @return boolean	true if the specified path references the same object
     *			Otherwise, false.
     */
    public boolean equals(Object o) {
        //  <PJA> 22-May-2002
        //  Almost all object paths are created with no originClass qualifiers for the properties. But
        //  object paths obtained from CIMInstance always have the originClass qualifiers. This leads
        //  to all kinds of misery trying to mix and match inside hash tables etc, unless we set
        //  ignoreClassOrigin to true.
        return equals(o, true);
    }

    /*
     * This method will parse a CIM Object path in the following format
     *
     * [namespace:]class_name[.name=value[,name=value]]
     * Examples:
     *  CIM_StorageVolume
     *  CIM_StoragePool.InstanceID=97654,a=c
     *  CIM_Directory.Name="C:\\"
     *  /root/cimv2:CIM_StoragePool.InstanceID=97654,a=c
     *  //host/root/cimv2:CIM_StoragePool.InstanceID=97654,a=c
     *  //host/root/cimv2:CIM_Directory.Name="C:\\"
     *  //host:5988/root/cimv2:CIM_Directory.Name="C:\\"
     *  //host/root/cimv2:CIM_DirectoryContainsFile.GroupComponent="CIM_Directory.Name=\"C:\\\\\"",PartComponent="CIM_LogicalFile.Name=\"foo.txt\""
     */
    private void parseOp(String op) {

        //Need to pull the host portion out of op
        String host      = "";
        String namespace = "";
        String classname = "";
        String values    = "";
        int hostIndexBegin = op.indexOf("//");      
        int hostIndexEnd = op.indexOf("/", hostIndexBegin + 2);
        if (hostIndexBegin != -1) {
            host = op.substring(hostIndexBegin + 2, hostIndexEnd);
            op = op.substring(hostIndexEnd);        
        }
        int cnIndex = op.indexOf('.');
        int nsIndex = -1;

        if(cnIndex != -1 && op.charAt(cnIndex - 1) == '/') {
            // We found the . in //./ try again.
            cnIndex = op.indexOf('.', cnIndex + 1);
        }

        if(cnIndex == -1) {
            nsIndex = op.lastIndexOf(':', op.length());
        } else {
            nsIndex = op.lastIndexOf(':', cnIndex);
        }
        
        
        //determine the namespace portion
        if (nsIndex != -1) {
            //there is a namespace portion
            namespace = op.substring(0, nsIndex);
            if (namespace.equals(":")) {
                namespace = "";
            }
            if(namespace.startsWith("//")) {
                int idx = namespace.indexOf('/', 2);
                if(idx == -1) {
                    host = namespace.substring(2);
                    namespace = "";
                } else {
                    host = namespace.substring(2, idx);
                    namespace = namespace.substring(idx + 1);
                }
            }
            nsIndex++;
        } else {
            nsIndex = 0;
        }
        
        //determine the classname portion
        if (cnIndex != -1) {
            //there is a class name (with values after it)
            classname = op.substring(nsIndex, cnIndex);
            values = op.substring(cnIndex + 1, op.length());
        } else {
            //there are no values, just the class name
            classname = op.substring(nsIndex, op.length());
        }

        //Create a vector of the keys
        Vector keys = new Vector();
        if (values.length() != 0) {
            String [] propertyList = parsePropertyList(values);
            for (int i = 0; i < propertyList.length; i++) {
                int eqIndex = propertyList[i].indexOf('=');
                if(eqIndex != -1) {
                    CIMProperty cp = new CIMProperty();
                    cp.setName(propertyList[i].substring(0, eqIndex));
                    String value = propertyList[i].substring(eqIndex + 1);
                    value = StringUtil.unquote(value);
                    cp.setValue(new CIMValue(value));
                    keys.addElement(cp);
                } else {
                    throw new IllegalArgumentException("Invalid property initialization " + propertyList[i] + " there is no '='.");
                }
            }
        }
        
        //Do this at the end just in case there is an exception.
        setHost(host);
        setNameSpace(namespace);
        setObjectName(classname);
        setKeys(keys);
    }
    
    private static String[] parsePropertyList(String properties) {
        List propertyList = new ArrayList();
        boolean inQuotes = false;
        int lastComma = 0;
        int length = properties.length();
        
        for (int i = 0; i < length; i++) {
            char c = properties.charAt(i);
            if(c == '\"') {
                inQuotes = !inQuotes;
            }
            else if(!inQuotes && c == ',') {
                propertyList.add(properties.substring(lastComma, i));
                lastComma = i + 1;
            }
        }
        
        if(inQuotes) {
            throw new IllegalArgumentException("String literal is not properly closed by a double quote:" + properties);
        }
        else if(lastComma >= length) {
            throw new IllegalArgumentException("Found a comma, but there was now subsequent property and value: " + properties);
        }
        else {
            propertyList.add(properties.substring(lastComma));
        }
        
        return (String[]) propertyList.toArray(new String[] {});
    }

    /**
     * Creates and returns a copy of this object.
     * 
     * @return a copy of this object
     */    
    public Object clone() {
        CIMObjectPath cop = new CIMObjectPath();
        cop.setObjectName(objectName);
        cop.setHost(host);
        cop.setNameSpace(nameSpace);
        cop.setKeys((Vector)keys.clone());
        return cop;
    }
    
    /**
     * Compares this object path with the specified object path for equality
     *
     * @param  o                 the object to compare
     * @param  ignoreClassOrigin ignores the Class origins if true.
     * @return boolean           true if the specified path references the same
     *                            object, otherwise false.
     */
    public boolean equals(Object o, boolean ignoreClassOrigin) {
        if (!(o instanceof CIMObjectPath)) {
            return false;
        }
        CIMObjectPath inp = (CIMObjectPath) o;
        if (inp.host == null) {
            if ((host != null) && (host.length() != 0)) {
                return false;
            }
        } else {
            if (!inp.host.equalsIgnoreCase(host)) {
                return false;
            }
        }
        if (inp.nameSpace == null) {
            if ((nameSpace != null) && (nameSpace.length() != 0)) {
                return false;
            }
        } else {
            if (!inp.nameSpace.equalsIgnoreCase(nameSpace)) {
                return false;
            }
        }
        if (inp.objectName == null) {
            if ((objectName != null) && (objectName.length() != 0)) {
                return false;
            }
        } else {
            if (!inp.objectName.equalsIgnoreCase(objectName)) {
                return false;
            }
        }
        if (inp.keys == null) {
            if (keys != null) {
                return false;
            }
        } else {
            if (keys == null) {
                return false;
            }
            if (inp.keys.size() != keys.size()) {
                return false;
            }
            Hashtable ht = new Hashtable();
            CIMProperty pe;
            int i;
            for (i = 0; i < keys.size(); i++) {
                pe = (CIMProperty) keys.elementAt(i);
                if (ignoreClassOrigin) {
                    ht.put(pe.getName().toLowerCase(), pe);
                } else {
                    ht.put(
                        pe.getOriginClass().toLowerCase()
                            + "."
                            + pe.getName().toLowerCase(),
                        pe);
                }
            }

            for (i = 0; i < keys.size(); i++) {
                CIMProperty ipe = (CIMProperty)inp.keys.elementAt(i);
                if (ignoreClassOrigin) {
                    pe = (CIMProperty)ht.get(ipe.getName().toLowerCase());
                } else {
                    pe =  (CIMProperty)ht.get(
                            ipe.getOriginClass().toLowerCase() + "."
                            + ipe.getName().toLowerCase());
                }
                if (pe == null) {
                    return false;
                }
                CIMValue val = pe.getValue();
                CIMValue ival = ipe.getValue();
                if (ival == null) {
                    if (val != null) {
                        return false;
                    }
                } else {
                    if (!ival.equals(val)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
