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
 *Contributor(s): Brian Schlosser
 *                WBEM Solutions, Inc.
 */
package javax.wbem.cim;

import java.util.Enumeration;
import java.util.Vector;

/**
 * Creates and instantiates a CIM property, a name/value pair used
 * to characterize instances of a class. Use this API to
 * create a new attribute to describe managed objects. For example,
 * after a printer upgrade to handle duplex (two-sided) printing, you
 * could use this interface to create a CIM property called duplex.
 *
 * The CIMProperty class inherits the property name from its parent
 * class (CIMProperty extends CIMQualifiedElement). A CIM Property is defined
 * by its name and origin class. 
 *
 */
public class CIMProperty
    extends CIMQualifiedElement
    implements Cloneable, CIMTypedElement {

    final static long serialVersionUID = 200;

    /**
     * Name of the class in which this property was originally defined.
     */
    private String originClass = "";

    /**
     * The name of the overriding CIM property.
     */
    private String  overridingProperty;

    /**
     * Flag to mark this property as a key.
     */
    private boolean key;

    /**
     * Flag to mark this a propagated property. 
     */
    private boolean propagated = true;

    /**    
     * CIM data type of this property. 
     * A Property type can be one of the defined CIM data types 
     * or a special data type, which is a reference to an instance 
     * of a class. A CIM property can have an infinite number of 
     * instance references. 
     */   
    private CIMDataType type;

    /**    
     * Default value for this property. New property instances are automatically
     * assigned this default value, unless the instance declaration explicitly
     * assigns a value to the property.
     */
    private CIMValue value;


    /**
     * Creates a new unnamed CIM property
     */
    public CIMProperty() {
        this("");
    }

    /**
     * Creates a new property with the given name and no value.
     *
     * @param name the name of an existing CIM property
     */
    public CIMProperty(String name) {
        super(name);
    }

    /**
     * Creates a new property with the given name and value.
     *
     * @param name  name of an existing CIM property
     * @param value CIM value of an existing CIM property
     */
    public CIMProperty(String name, CIMValue value) {
        super(name);
        this.value = value;
        setPropagated(false);
    }

    /**
     * Returns the class or instance in which this property 
     * was defined. 
     *
     * @return Name of class where this property was defined.
     */
    public String getOriginClass() {
        return originClass;
    }

    /**
     * Sets the class or instance in which this property 
     * was defined.
     *
     * @param originClass The name of the class in which this property is
     * 			defined.
     */
    public void setOriginClass(String originClass) {
        if (originClass == null) {
            this.originClass = "";
        } else {
            this.originClass = originClass;
        }
    }

    /**
     * Sets the value for this property.
     *
     * @param value the CIM value for this property
     */
    public void setValue(CIMValue value) {
        if( value != null && isKey() && value.getType().isArrayType()) {
            throw new IllegalArgumentException(
                "Key properties can't have array values: " +
                value.toString());
        }

        this.value = value;
        this.setPropagated(false);
    }

    /**
     * Gets the value for this property
     * 
     * @return The CIM value for this property.
     */
    public CIMValue getValue() {
        return value;
    }
    
    /**
     * Sets the data type of this property to the specified CIM data type. A
     * Property data type can be one of the defined CIM data types or a special
     * data type, which is a reference to an instance of a class. A CIM
     * property can have an infinite number of instance references.
     * 
     * @param type the CIM data type of this property
     */
    public void setType(CIMDataType type) {
        this.type = type;
    }
    
    /**
     * Gets the CIM data type of this property
     * 
     * @return The CIM data type of this property.
     */
    public CIMDataType getType() {
	if (type != null) {
	    return type;
	}

	if (value != null) {
	    return value.getType();
	}

	return null;
    }

    /**
     * Specifies this property as an overriding property
     *
     * @param name the name of the property
     */
    public void setOverridingProperty(String name) {
        this.overridingProperty = name;
    }

    /**
     * Gets the overriding property
     *
     * @return The name of the overriding property.
     */
    public String getOverridingProperty() {
        return overridingProperty;
    }

    /**
     * Identifies whether or not this CIM Property data type is a reference to
     * an instance (link to another CIM object). For example, you might want to
     * show properties that are references to instances by using different
     * icons on a GUI.
     * 
     * @return True if this property is a CIM reference. Otherwise, false.
     */
    public boolean isReference() {
	CIMDataType type = getType();
        if (type == null) {
            return false;
        }
        return (type.getType() == CIMDataType.REFERENCE);
    }
	
    /**
     * Adds the specified CIM qualifier to this property.
     * 
     * @param cq The qualifier to add
     * @exception CIMException if the qualifier already exists.
     */
    public void addQualifier(CIMQualifier cq)
	       throws CIMException {
        if (getQualifier(cq.getName()) != null) {
            throw new CIMException(CIMException.CIM_ERR_ALREADY_EXISTS, cq);
        }
        if(cq.getName().equalsIgnoreCase("key") &&
           cq.hasValue() &&
           cq.getValue().getType().isArrayType()) {
            throw new IllegalArgumentException("Array properties can't be keys: " +
                                               cq.toString());
        }
        if (cq.getName().equalsIgnoreCase("key") && 
                (!cq.hasValue() || cq.getValue().equals(CIMValue.TRUE))) {
            setKey(true);
        }

        super.addQualifier(cq);
    }

    /* (non-Javadoc)
     * @see javax.wbem.cim.CIMQualifiedElement#setQualifiers(java.util.Vector)
     */
    public void setQualifiers(Vector qualifiers) {
        super.setQualifiers(qualifiers);
        
        for (Enumeration quals = qualifiers.elements(); quals.hasMoreElements(); ) {
            CIMQualifier qualifier = (CIMQualifier) quals.nextElement();
            
            if (qualifier.getName().equalsIgnoreCase("key")) {
                if (!qualifier.hasValue() || qualifier.getValue().equals(CIMValue.TRUE)) {
                    setKey(true);
                } else {
                    setKey(false);
                }
                break;
            }
        }
    }
    
    /**
     * Makes a copy of this property, including property name, identifier,
     * type, origin class, size, value, and qualifiers.
     *
     * @return Newly created property.
     */
    public synchronized Object clone() {
        return clone(true, true);
    }

    /**
     * Makes a copy of this property, including property name, identifier,
     * type, origin class, size, value, and qualifiers.
     *
     * @param  includeQualifier   qualifiers are only included if true.
     * @param  includeClassOrigin classOrigin is only included if true.
     * @return the newly created property
     */
    public synchronized Object clone(boolean includeQualifier,
                                     boolean includeClassOrigin) {
        return clone(includeQualifier, includeClassOrigin, false);                                 
    } 
                                     
                                     
    protected synchronized Object clone(boolean includeQualifier,
        boolean includeClassOrigin, boolean reset) {

        CIMProperty pe = new CIMProperty();
        pe.name  = name;
        pe.type  = type;
        pe.value = value;
        pe.overridingProperty = overridingProperty;

        pe.setKey(key);
        if (reset) {
            pe.setPropagated(true);
        } else {
            pe.setPropagated(propagated);
        }

        if (includeClassOrigin) {
            pe.originClass = originClass;
        }
        if (qualifiers != null && includeQualifier) {
            pe.qualifiers = CloneUtil.cloneQualifiers(qualifiers);
        }
        return pe;
    }

    /**
     * Convenience method for determining if this property is a 
     * Key. 
     *
     * @return true if this property is a key.
     */
    public boolean isKey() {
        return key;
    }

    /**
     * Convenience method for making this property a key
     *
     * @param key 
     */
    public void setKey(boolean key) {
        this.key = key;
    }

    /**
     * Determines if this property is Propagated.
     *  
     * When this property is part of a class, this
     * value designates that the classorigin value is the same
     * as the class name. 
     * For an instance, this  
     * designates if the value of this property was inherited from
     * the class or if it was set as part of this instance.  
     *
     * @return true if this property is propagated.
     */
    public boolean isPropagated() {
        return propagated;
    }

    /**
     * Set this property as propagated.
     *
     * @param propagated 
     */
    public void setPropagated(boolean propagated) {
        this.propagated = propagated;
    }
}
