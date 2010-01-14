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
*/

package javax.wbem.cim;

import java.util.Enumeration;
import java.util.Vector;

/**
 * Represents a CIM qualifier. A qualifier provides additional
 * information about classes, associations, indications, methods, method ,
 * parameters, triggers, instances, properties, or references.
 *
 * Each CIMQualifier must have a CIMQualifierType. A qualifier
 * and its qualifier type must have the same name. 
 * 
 * The default value of this CIM qualifier can override the default 
 * value of its CIM qualifier type.
 *
 * @since  WBEM 1.0
 */
public class CIMQualifier extends CIMElement implements Cloneable {

    final static long serialVersionUID = 200;

    /**
     * @serial	
     */
    private CIMValue value;

    /**
     * @serial	
     */
    private Vector flavors = new Vector();

    /**
     * Returns the CIM value of this qualifier
     *
     * @return The CIM value for this qualifier.
     */
    public CIMValue getValue() {
	return value;
    }

    /**
     * Sets the CIM value of this qualifier to the specified value
     *
     * @param value the CIM value to assign this qualifier.
     */
    public void setValue(CIMValue value) {
	this.value = value;
    }

    /**
     * Sets the CIM qualifier type for this qualifier to the specified type.
     * A CIM qualifier and its CIM qualifier type must have the same name. This
     * method initializes this qualifier's flavors (inheritance rules), type,
     * scope, and default values with the values of its CIM qualifier type.
     *
     * @param qualifierType the CIM qualifier type of this qualifier
     */
    public void setDefaults(CIMQualifierType qualifierType) {
	if (!hasValue() && qualifierType.hasDefaultValue()) {
	    CIMValue cv = qualifierType.getDefaultValue();
	    if ((cv.getType() == null) && (qualifierType.getType() != null)) {
		this.value = new CIMValue(cv.getValue(), qualifierType.getType());
            } else {
		this.value = cv;
            }
	}
	if ((flavors == null) || (flavors.size() == 0)) {
	    flavors = (Vector)qualifierType.getFlavor().clone();
	}
    }

    /**
     * Checks if the qualifier's flavor includes the input flavor.
     *
     * @param flavor the flavor element which is checked for flavor
     *                inclusion.
     * @return True if the flavor is included in the qualifier's flavor,
     *          otherwise false.
     */
    public boolean hasFlavor(CIMFlavor flavor) {

	if (flavors == null) {
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
     * Adds the specified flavor and verifies that there is not any conflict.
     *
     * @param newFlavor The flavor to be added
     */
    public void addFlavor(CIMFlavor newFlavor) {
        if (!hasFlavor(newFlavor)) {
            flavors.addElement(newFlavor);
        }
    }

    /**
     * Returns the CIM flavors for this CIM qualifier.
     *
     * @return A vector of CIM flavors in this CIM qualifier.
     */
    public Vector getFlavor() {
        return flavors;
    }

    /**
     * Constructor instantiates a CIM qualifier with empty name
     * value fields.
     */
    public CIMQualifier() {
	this("");
    }

    /**
     * Constructor instantiates a CIM qualifier with the specified name.
     *
     * @param qualifierName the name to assign this qualifier
     */
    public CIMQualifier(String qualifierName) {
	super(qualifierName);
	flavors = new Vector();
    }

    /**
     * Constructor instantiates a CIM qualifier with the specified name and
     * type.
     *
     * @param qualifierName The name for this qualifier
     * @param qualifierType The qualifier type for this qualifier
     */
    public CIMQualifier(String qualifierName, CIMQualifierType qualifierType) {
	this(qualifierName);
	setDefaults(qualifierType);
    }

    /**
     * Returns a String representation of the CIMQualifier.
     * This method is intended to be used only for debugging purposes,
     * and the format of the returned string may vary between
     * implementations.  The returned string may be empty but may not be
     * null.
     *
     * @return A string representation of this qualifier.
     */
    public String toString() {
	return toMOF();
    }
    /**
     * Returns a MOF representation of the CIMQualifier.
     *
     * @return A string representation of this qualifier in
     *          Managed Object Format (MOF)
     */
    public String toMOF() {
        MOFFormatter mf = new MOFFormatter();
        return mf.toString(this);
    }

    /**
     * Checks if the qualifier has a value set.
     *
     * @return True if a value is present, otherwise false.
     */
    public boolean hasValue() {
	return (value != null);
    }

    /**
     * Returns true if this qualifier is equal to the input. Values are
     * not compared, only the names
     *
     * @param o	The object to compare.
     * @return 	True if the input qualifier is equal, otherwise flase.
     */
    public boolean equals(Object o) {
	// Not checking values here, since this equals is used by Vector
	// to check if it contains a given qualifier. A given qualifier
	// is present if the name matches, it does not matter if the
	// values are different.
	if (!(o instanceof CIMQualifier)) {
	    return false;
	}
	return super.equals(o);
    }

    /**
     * Returns a copy of this CIM qualifier.
     *
     * @return An object that is a copy of the
     * specified CIM qualifier, including its name,
     * identifier, value, and a vector of CIM
     * flavors.
     */
    public synchronized Object clone() {
	CIMQualifier qe = new CIMQualifier();
	qe.name    = name;
	qe.value   = value;
	qe.flavors = (Vector)flavors.clone();
	return qe;
    }
}
