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
 *are Copyright (c) 2002 Sun Microsystems, Inc.
 *
 *All Rights Reserved.
 *
 *Contributor(s): Brian Schlosser
 *                WBEM Solutions, Inc.
 */

package javax.wbem.cim;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

/**
 * Base class for managed system objects. 
 *
 * @author  Brian Schlosser
 * @since   WBEM 1.0
 */
abstract public class CIMObject extends CIMQualifiedElement {

    /**   
     * list of properties for this CIM object
     * @serial 
     */
    protected Vector properties;

    /**   
     * list of all properties for this CIM object
     * @serial
     */
    protected Vector allproperties;
    
    /**
     * The name of this CIMObject. 
     * This will include the host/namespace information
     */
    private CIMObjectPath cop;

    /**
     * @param objectName the name for the object
     */
    protected CIMObject(String objectName) {
        super(objectName);
        cop = new CIMObjectPath(objectName, "");      
        properties    = new Vector();
        allproperties = new Vector();
	cop.setObjectName(objectName);
    }

    /**
     * This method returns the CIMObjectPath that represents this CIMObject.
     *
     * @return the CIMObjectPath that represents this CIMObject
     */
    public CIMObjectPath getObjectPath() {
        cop.setObjectName(getName());
        return cop;
    }
    
    /**
     * This method will set the CIMObjectPath that represents this CIMObject. 
     *
     * @return the CIMObjectPath that represents this CIMObject
     */
    public void setObjectPath(CIMObjectPath op) {
        cop = (CIMObjectPath)op.clone();
    }

    /**
     * Returns a list of key properties for this CIM object,
     *
     * @return Vector the list of CIM properties that are keys
     *          for this CIM object
     */
    public Vector getKeys() {
        Vector v = cop.getKeys();
        //Note: This is written for backward compat
        //When going to 2.0 just use one line
        //return cop.getKeys()
        if (!v.isEmpty()) {
            return v;
        }
        v = new Vector();
        for (Enumeration eProps = properties.elements();
            eProps.hasMoreElements();) {
            CIMProperty pe = (CIMProperty) eProps.nextElement();
            if ((pe.isKey()) && (pe.getOverridingProperty() == null)) {
                v.add(pe.clone(false, true));
            }
        }
        return v;
    }


    /**  
     * Gets the list of properties for this CIM object
     *
     * @return Vector   The list of properties for this CIM object
     */
    public Vector getProperties() {
        return properties;
    }

    /**  
     * Sets the properties for this CIM object to the specified property list
     *
     * @param properties The list of properties to set for this CIM object
     */
    public void setProperties(Vector properties) {
        this.properties    = new Vector();
        this.allproperties = new Vector();
        
        if(properties != null) {
            for (Enumeration eProps = properties.elements();
                eProps.hasMoreElements();) {
                addProperty((CIMProperty) eProps.nextElement());
            }
        }
    }

    /**  
     * Adds the property to this CIM object
     *
     * @param property property to add to this CIM object
     */
    protected void addProperty(CIMProperty property) {
        if (property.getOverridingProperty() == null) {
            properties.addElement(property);
        }
        allproperties.addElement(property);
    }

    /** 
     * Sets the value of the specified property. This is another form
     * of the updatePropertyValue method.
     *
     * @param name  The name of the property to set.
     * @param value The value to set to.
     *
     * @exception CIMException Throws this exception if the property 
     *                         doesn't exist on the instance
     */
    public void setProperty(String name, CIMValue value) throws CIMException{
        CIMProperty property = getProperty(name);
        if (property == null) {
            throw new CIMException(CIMException.CIM_ERR_NO_SUCH_PROPERTY, name);
        }

        property.setValue(value);
    }

    /** 
     * Updates the property values for this CIM object with 
     * the specified list of property values
     *
     * @param properties the list of CIMProperties and their values 
     *                   for this CIM object
     *
     * @exception CIMException if a property in the vector doesn't 
     *                         exist on the instance
     */
    public void updatePropertyValues(Vector properties) throws CIMException {
        for (Enumeration eProps = properties.elements(); eProps.hasMoreElements();) {
            CIMProperty property = (CIMProperty) eProps.nextElement();
            updatePropertyValue(property);
        }
    }
    
    /** 
     * Updates the value of the specified CIM property.
     * 
     * @param property CIM property to update. <code>property</code> contains 
     *                 the value to be used.
     *
     * @exception CIMException if the property doesn't exist on the object
     */
    public void updatePropertyValue(CIMProperty property) throws CIMException {
        CIMProperty ipe = getProperty(property.getName(), 
                property.getOriginClass());
        if (ipe == null) { 
            throw new CIMException(CIMException.CIM_ERR_NO_SUCH_PROPERTY, 
                                   property.getOriginClass() + "." + 
                                   property.getName());
        }
        ipe.setValue(property.getValue());
    }

    /**  
     * Get the specified CIM Property.
     *  
     * @param name  The string name of the property to get. 
     *              It can also be of the form 
     *              "originClass.propertyName"
     * @return CIMProperty <code>null</code> if the property does not 
     *                     exist, otherwise returns the CIM property.
     */
    public CIMProperty getProperty(String name) {
        if ((allproperties == null) || (name == null)) {
            return null;
        }
        
        int i = name.indexOf('.');
        if (i != -1) {
            return getProperty(name.substring(i + 1, name.length()),
                               name.substring(0, i));
        }
        
        int tempIndex = allproperties.indexOf(new CIMProperty(name));
        if (tempIndex == -1) {
            return null;
        }
        
        CIMProperty pe = (CIMProperty) allproperties.elementAt(tempIndex);

        String override = pe.getOverridingProperty();
        if (override != null) {
            pe = getProperty(override);
        }
        return pe;
    }

    /**  
     * Returns the specified CIMProperty.
     *
     * @param name        The string name of the property to get.
     * @param originClass (Optional) The string name of the class 
     *                     in which the property was defined.
     * @return CIMProperty Null if the property does not exist, 
     *                      otherwise returns the CIM property.
     */
    public CIMProperty getProperty(String name, String originClass) {
        CIMProperty pe;
        if ((allproperties == null) || (name == null)) {
            return null;
        }
        if ((originClass == null) || (originClass.length() == 0)) {
            return getProperty(name);
        }
        int size = allproperties.size();
        for (int i = 0; i < size; i++) {
            pe = (CIMProperty) allproperties.elementAt(i);
            if (pe.getName().equalsIgnoreCase(name)
                    && pe.getOriginClass().equalsIgnoreCase(originClass)) {
                if (pe.getOverridingProperty() != null) {
                    return (getProperty(pe.getOverridingProperty()));
                } else {
                    return pe;
                }
            }
        }
        return null;
    }

    protected Vector getFilteredProperties(String  propertyList[], 
                                           boolean includeQualifier,
                                           boolean includeClassOrigin) {
        Vector propList = null;
        
        if (propertyList == null) {
            if (allproperties != null && allproperties.size() > 0) {
                propList = new Vector();
                for (Enumeration eProps = allproperties.elements();
                    eProps.hasMoreElements();) {
                    CIMProperty pe = (CIMProperty) eProps.nextElement();
                    propList.add(pe.clone(includeQualifier, 
                                        includeClassOrigin));
                }
            }
        } else {
            Set hm = new HashSet();
            propList = new Vector();
            for (int i = 0; i < propertyList.length; i++) {
                CIMProperty cp = getProperty(propertyList[i]);
                // Ignore unfound properties
                if (cp != null) {
                    String propName = cp.getOriginClass() + "." + cp.getName();
                    // Ignore duplicates
                    if(!hm.contains(propName)) {
                        hm.add(propName);
                        propList.add(cp.clone(includeQualifier, 
                                            includeClassOrigin));
                    }
                }
            }
        }
        return propList;
    }    
}
