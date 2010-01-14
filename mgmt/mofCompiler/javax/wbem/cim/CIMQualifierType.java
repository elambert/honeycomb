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

import java.util.Vector;
import java.util.Enumeration;

/** 
 * Represents a CIM qualifier type, which is a 
 * template for a CIM qualifier. This class is useful for checking
 * that a characteristic is a valid CIM qualifier characteristic.
 * 
 * @since WBEM 1.0
 */
public class CIMQualifierType extends CIMElement {

    final static long serialVersionUID = 200;

    /** 
     * CIM Data Type of this CIM Qualifier.
     * @serial
     */   
    private CIMDataType type; 
    
    /** 
     * List of CIM flavors for this qualifier.
     * @serial
     */   
    private Vector	flavors;
     
    /** 
     * Default CIM Value.
     * @serial
     */  
    private CIMValue	defaultValue;
    
    /** 
     *  List of meta-model constructs to which this qualifier can be applied.
     * @serial
     */
    private Vector	scope;

    /**
     * Gives the scopes to which this qualifier type can be applied.
     * @return Vector of CIM element scopes for which this qualifier type
     *         is applicable.
     */
    public Vector getScope() {
	return (Vector)scope.clone();
    }
    
    /** 
     * Returns the CIM data type of this qualifier type
     *
     * @return 	The data type of this qualifier type. This
     *		data type must agree with the data type of
     *		the CIMQualifier of the same name.
     */
    public CIMDataType getType() {
	return type;
    }
    
    /**   
     * Returns the default values for this qualifier type
     *
     * @return The CIM value for this qualifier type.
     */
    public CIMValue getDefaultValue() {
	return defaultValue;
    }

    /** 
     * Sets the CIM data type to the specified type
     *
     * @param type	the CIM data type of this qualifier type
     *
     */
    public void setType(CIMDataType type) {
	this.type = type;
    }
    
    /**     
     * Sets the default value for this qualifier type to the 
     * specified CIM value
     *
     * @param value 	list of default CIM values for this qualifier type
     *
     */
    public void setDefaultValue(CIMValue value) {
	defaultValue = value;
    }

  
    /** 
     * Constructor creates a CIM qualifier type.
     */
    public CIMQualifierType() {
	this("");
    }

   
    /**  
     * Constructor creates a new CIM qualifier type,
     * using the name of the specified CIM qualifier type.
     *
     * @param Name 	The name of an existing CIM qualifier type.
     */
    public CIMQualifierType(String Name) {
	super(Name);
	scope = new Vector();
	flavors = new Vector();
    }

    /**
     * Returns a String representation of the CIMQualifierType
     * This method is intended to be used only for debugging purposes,
     * and the format of the returned string may vary between
     * implementations.  The returned string may be empty but may not be
     * null.
     *
     * @return A string representation of this qualifier type.
     */
    public String toString() {
	return toMOF();
    }

    /**
     * Returns a MOF representation of the CIMQualifierType.
     *
     * @return  A string representation of this qualifier type in
     *          Managed Object Format (MOF).
     */
    public String toMOF() {
	MOFFormatter mf = new MOFFormatter();
 	return mf.toString(this);
    }

    /** 
     * Expands the qualifier type's scope to include the input meta element.
     * 
     * @param metaElement the meta element to be included in the scope
     */
    public void addScope(CIMScope metaElement) {

	if (!hasScope(metaElement)) {
	    scope.addElement(metaElement);
	}
    }

    /** 
     * Checks if the qualifier type's scope includes the input meta element.
     * 
     * @param metaElement the meta element which is checked for scope inclusion.
     * @return true if the meta element is included in the qualifier type's 
     * scope, otherwise false.
     */
    public boolean hasScope(CIMScope metaElement) {
	if (scope == null) {
	    return false;
	}
	Enumeration e = scope.elements();
	while (e.hasMoreElements()) {
	    CIMScope cs = (CIMScope)e.nextElement();
	    if (cs.equals(metaElement) || 
		cs.equals(CIMScope.getScope(CIMScope.ANY))) {
		return true;
	    }
	}
	return false;
    }

    /** 
     * Checks if the qualifier's flavor includes the input flavor.
     * 
     * @param 	flavor 	the flavor element which is checked for 
     *			flavor inclusion.
     * @return 	True if the flavor is included in the qualifier's
     * 		flavor,	otherwise false.
     */
    public boolean hasFlavor(CIMFlavor flavor) {

	if (flavor == null) {
	    return false;
	}
	Enumeration e = flavors.elements();
	while (e.hasMoreElements()) {
	    if (((CIMFlavor)e.nextElement()).equals(flavor)) {
		return true;
	    }
	}
	return false;
    }

    /** 
     * Adds the input flavor and verifies if there is any conflict.
     * 
     * @param newFlavor The flavor to be added
     */
    public void addFlavor(CIMFlavor newFlavor) {
	if (!hasFlavor(newFlavor)) {
	    flavors.addElement(newFlavor);
	}
    }

    /**
     * Get the flavors of this qualifier type.
     * @return Vector of flavors for this qualifier type.
     */
    public Vector getFlavor() {
    	return (Vector)flavors.clone();
    }

    /** 
     * Checks if the default values are defined
     * 
     * @return true if the default value is defined, false otherwise.
     */
    public boolean hasDefaultValue() {
	if (defaultValue == null) {
	    return false;
	} else {
	    return true;
	}
    }

    /** 
     * Checks if the value is an array or not.
     * 
     * @return 	true if the qualifier type is an array  
     *  	otherwise false.
     */
    public boolean isArrayValue() {
	//622338: Check datatype not value
	if (type == null) {
	    return false;
	} else {
	    return type.isArrayType();
	}
    }
}
