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
 *Contributor(s): WBEM Solutions, Inc.
 *                Brian Schlosser
 */

package javax.wbem.cim;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;


/**
 * The CIMInstance class represents an instance of a CIM class. Clients
 * use CIMInstance in conjunction with the CIMClient instance methods like
 * createInstance, setInstance to manipulate instances within a namespace.
 *
 * @since WBEM 1.0
 * @see   javax.wbem.client.CIMClient
 */
public class CIMInstance extends CIMObject implements Serializable {

    final static long serialVersionUID = 200;

    /**
     * the name of the class this CIM instance is instantiated from
     * @serial
     */
    private String className;

    /**
     * the alias name for this CIM instance
     * @serial
     */
    private String alias;

    /**
     * Basic CIMInstance constructor. Initializes empty properties, 
     * and class name.
     *
     */
    public CIMInstance() {
        super("");
        className = "";
        alias     = "";
    }

    /**
     * This method returns the CIMObjectPath that represents this CIMObject.
     *
     * @return the CIMObjectPath that represents this CIMObject
     */
    public CIMObjectPath getObjectPath() {
        CIMObjectPath cop = super.getObjectPath();
        Vector keys = getKeys();
        if (keys != null && keys.size() > 0) {
            cop.setKeys(keys);
        }
        return cop;
    }

    /**
     * Returns a String representation of the CIMInstance.
     * This method is intended to be used only for debugging purposes,
     * and the format of the returned string may vary between
     * implementations.  The returned string may be empty but may not be
     * null.
     *
     * @return string representation of this instance
     */
    public String toString() {
        return toMOF();
    }

    /**
     * Returns a MOF representation of the CIMInstance.
     *
     * @return a string representation of this instance in
     *          Managed Object Format (MOF)
     */
    public String toMOF() {
        MOFFormatter mf = new MOFFormatter();
        return mf.toString(this);
    }

    /**
     * Gets the name of the class that instantiates this CIM instance
     *
     * @return name of class that instantiates this CIM instance
     */
    public String getClassName() {
        return className;
    }

    /**
     * Sets the name of the class that instantiates this CIM instance
     *
     * @param className the string for the name of the class that
     *                   instantiates this CIM instance
     */
    public void setClassName(String className) {
        this.className = new String(className);
    }

    /**
     * Gets the specified property
     *
     * @param propertyName	The text string for the name of the property
     * @return CIMProperty 	Values for the specified property. Returns
     *                       null if the property table is empty.
     */
    public CIMProperty getProperty(String propertyName) {
        if ((allproperties == null) || (propertyName == null)) {
            return null;
        }

        StringTokenizer st = new StringTokenizer(propertyName, ".");
        String property = st.nextToken();
        try {
            String subProperty = st.nextToken();

            CIMProperty cp = super.getProperty(property);
            if (cp != null) {
                // This means that the first token is a valid property name,
                // which must be an instance type. Should we throw an
                // error if it is not a valid instance type?
                while (true) {
                    try {
                        CIMInstance ci = (CIMInstance)cp.getValue().getValue();
                        cp = ci.getProperty(subProperty);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // Either the value is null or is not a CIMInstance
                        return null;
                    }
                    if (st.hasMoreTokens()) {
                        // get next token
                        subProperty = st.nextToken();
                    } else {
                        // run out of tokens
                        return cp;
                    }
                }
            } else {
                // We assume that the first token is an origin class name
                return getProperty(subProperty, property);
            }
        } catch (Exception e) {
            // only one token
        }

        // Only one token
        return super.getProperty(property);
    }

    /**
     * Gets the alias name of this CIM instance
     *
     * @return the alias of the instance, null if no alias exists.
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Sets the alias for this instance
     *
     * @param alias the alias name of this CIM instance
     */
    public void setAlias(String alias) {
        this.alias = alias;
    }

    /**
     * Gets the classname of this CIM instance. 
     *
     * @return the name of this CIM instance
     */
    public String getName() {
        return className;
    }

    /**
     * @deprecated use getKeys() instead
     * @return the list of keys for the instance
     * 
     * @see CIMObject#getKeys()
     */
    public Vector getKeyValuePairs() {
        return getKeys();
    }

    /**
     * Returns only the local elements for this instance and filters out the
     * rest. This works like a clone method. Note that this depends on the
     * classOrigin property of its elements, so it will not work if you
     * invoke filterProperties and set includeClassOrigin to false.
     *
     * @return CIMInstance populated with the local elements.
     * @see #filterProperties(String[], boolean, boolean)
     */
    public CIMInstance localElements() {
        CIMInstance ci = new CIMInstance();

        ci.className  = className;
        ci.setObjectPath(getObjectPath());

        if (allproperties != null) {
            Vector ov = new Vector();
            for (Enumeration props = allproperties.elements();
                props.hasMoreElements();) {
                CIMProperty pe = (CIMProperty) props.nextElement();
                if (!pe.isPropagated()) {
                    ov.add(pe);
                }
            }
            ci.setProperties(ov);
        }
        return ci;
    }

    /**
     * Returns only the elements for this instance that have classOrigin
     * set to one of the names in the className list. Thus, only the elements
     * local to the classes specified in the className list are retained.
     * The rest are filtered out. This works like a clone method. Note that this
     * depends on the  classOrigin property of its elements, so it will not work
     * if you invoke filterProperties and set includeClassOrigin to false.
     *
     * @param classNames A list of strings containing the classes with which
     *                  the classOrigin must be matched.
     *
     * @return CIMInstance populated with the specified elements.
     * @see #filterProperties(String[], boolean, boolean)
     */
    public CIMInstance localElements(List classNames) {

        if (classNames == null) {
            return this;
        }

        Set classSet = new HashSet();
        for (Iterator iter = classNames.iterator(); iter.hasNext();) {
            String s = (String)iter.next();
            classSet.add(s.toLowerCase());
        }

        CIMInstance ci = new CIMInstance();

        ci.className  = className;
        ci.setObjectPath(getObjectPath());
        
        if (allproperties != null) {
            Vector ov = new Vector();
            for (Enumeration eProps = allproperties.elements();
                eProps.hasMoreElements();) {
                CIMProperty pe = (CIMProperty) eProps.nextElement();
                if (classSet.contains(pe.getOriginClass().toLowerCase())) {
                    ov.add(pe);
                }
            }
            ci.setProperties(ov);
        }

        return ci;
    }

    /**
     * This method returns a new CIMInstance with properties filtered according
     * to the input parameters. Inclusion of class origin and qualifiers can
     * also be controlled.
     *
     * @param propertyList If the PropertyList input parameter is not NULL, the
     * members of the array define one or more Property names.  The returned
     * Instance does not include elements for any Properties missing from this
     * list. If the PropertyList input parameter is an empty array this
     * signifies that no Properties are included in each returned class. If the
     * PropertyList input parameter is NULL this specifies that all Properties
     * are included in each returned class.
     * If the PropertyList contains duplicate elements or invalid property
     * names, they are ignored.
     *
     * @param  includeQualifier   qualifiers are only included if true.
     * @param  includeClassOrigin classOrigins are only included if true.
     *
     * @return CIMInstance matching the input filter.
     * @see #localElements()
     */
    public CIMInstance filterProperties(String  propertyList[],
        boolean includeQualifier, boolean includeClassOrigin) {
             
        CIMInstance ci  = new CIMInstance();
        ci.className = className;
        ci.alias         = alias;
        ci.setObjectPath(getObjectPath());
        
        Vector propList = getFilteredProperties(propertyList,
                                                includeQualifier,
                                                includeClassOrigin);
        ci.setProperties(propList);

        return ci;
    }

    /**
     * Creates and returns a copy of this object.
     * 
     * @return a copy of this object
     */
    public Object clone() {

        CIMInstance ci = new CIMInstance();

        ci.className     = className;
        ci.alias         = alias;
        ci.setObjectPath(getObjectPath());
        
        if(allproperties != null) {
            ci.setProperties(CloneUtil.cloneProperties(allproperties));
        }

        return ci;
    }
        
    /**
     * Indicates whether some other instance is "equal to" this one. Two
     * CIMInstances are considered equal if the names are the same. This method
     * does NOT compare each property value.
     * 
     * @param o the object to compare
     * @return true if the specified path references the same instance,
     *         otherwise false.
     */
    public boolean equals(Object o) {
        if (!(o instanceof CIMInstance)) {
            return false;
        }
        return getObjectPath().equals(((CIMInstance) o).getObjectPath());
    }

    /**
     * Computes the hash code for this instance. The hash code will be the
     * object path of the instance not including the host or namespace
     * information.
     * 
     * @return the integer representing the hash code for this object path.
     */
    public int hashCode() {
        return getObjectPath().hashCode();
    }
}
