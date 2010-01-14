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

import java.util.Vector;

/**
 * Creates and instantiates a CIM method.
 *
 * @author  Sun Microsystems, Inc.
 * @since   WBEM 1.0
 */
public class CIMMethod extends CIMQualifiedElement 
    implements Cloneable, CIMTypedElement {

    final static long serialVersionUID = 200;

    /**
     * Top-level parent class in the class hierarchy.
     * @serial
     */
    private CIMClass originClass = null;

    /**
     * CIM data type.
     * @serial
     */
    private CIMDataType type;

    /**
     * Size of the method's return value.
     * @serial
     */
    private int size;		

    /**
     * List of paremeters for this method
     * @serial
     */
    private Vector parameters;

    /**
     * Overriding method for this method.
     * @serial
     */
    private String overridingMethod;

    /**
     * Creates and instantiates a CIM method.
     */
    public CIMMethod() {
        this("");
    }

    /**
     * Creates and instantiates a CIM method with the
     * specified name.
     *
     * @param name 	Name of an existing CIM method
     */
    public CIMMethod(String name) {
        super(name);
        parameters = new Vector();
    }

    /**
     * Returns the class in which this method was defined.
     *
     * @return String Name of class where this property was defined.
     */
    public CIMClass getOriginClass() {
        return originClass;
    }

    /**
     * Sets the class in which this method was defined.
     *
     * @param originClass The name of the class in which this property is
     * defined.
     */
    public void setOriginClass(CIMClass originClass) {
        if (originClass == null) {
            this.originClass = null;
        } else {
            this.originClass = originClass;
        }
    }

    /**
     * Sets the list of CIMParameters for this method to the specified
     * list of parameters
     *
     * @param parameters list of parameters to be assigned to this method
     */
    public void setParameters(Vector parameters) {
        this.parameters = parameters;
    }

    /**
     * Returns the list of CIMParameters for this method
     *
     * @return Vector list of this method's CIMParameters
     *
     */
    public Vector getParameters() {
        return parameters;
    }

    /**
     * Sets the data type of this method's return value to the specified
     * CIM data type.
     *
     * @param type the CIM data type assigned to the method's
     *			    return value.
     */
    public void setType(CIMDataType type) {
        this.type = type;
    }

    /**
     * Returns the data type of this method's return value
     *
     * @return the CIM data type of this method's return value
     */
    public CIMDataType getType() {
        return type;
    }

    /**
     * Returns the size of this method's return value
     *
     * @return the integer value of the size of the
     *			method's return value
     */
    public int getSize() {
        return size;
    }

    /**
     * Sets the size of this method's return type to the specified size
     *
     * @param size the integer size assigned to this method's
     *			    return value
     */
    public void setSize(int size) {
        this.size = size;
    }

    /**
     * Sets the name of the overriding method for this method.
     *
     * @param name The string name of the overriding method for this method.
     */
    public void setOverridingMethod(String name) {
        this.overridingMethod = name;
    }

    /**
     * Gets the name of the overriding method for this method.
     *
     * @return The name of the overriding method for this method.
     */
    public String getOverridingMethod() {
        return overridingMethod;
    }

    /**
     * Returns a String representation of the CIMMethod.
     * This method is intended to be used only for debugging purposes,
     * and the format of the returned string may vary between
     * implementations.  The returned string may be empty but may not be
     * null.
     *
     * @return the string representation of this method
     */
    public String toString() {
        return toMOF();
    }
    /**
     * Returns a MOF representation of the CIMMethod
     *
     * @return a string representation of this method in
     *          Managed Object Format (MOF)
     */
    public String toMOF() {
        MOFFormatter mf = new MOFFormatter();
        return mf.toString(this);
    }

    /**
     * Returns an object that is a copy of this CIM method.
     * This method calls clone(boolean includeQualifier,
     * boolean includeClassOrigin) with the true values,
     * which indicates that this CIM method contains
     * qualfiiers and class origin.
     *
     * @return An object that is a copy of this CIM method.
     *
     */
    public synchronized Object clone() {
        return clone(true, true);
    }

    /**
     * Returns an object that is a copy of this CIM method.
     *
     * @param includeQualifier A boolean that is true if this
     * 		CIM method contains CIM qualifiers, otherwise false.
     * @param includeClassOrigin A boolean that is true if this
     *		method contains the class origin
     *
     * @return An object that is a copy of this CIM method.
     *
     */
    public synchronized Object clone(boolean includeQualifier,
                                     boolean includeClassOrigin) {
        CIMMethod me = new CIMMethod();
        me.name = name;
        me.type = type;
        if (includeClassOrigin) {
            me.originClass = originClass;
        }
        me.size = size;
        me.overridingMethod = overridingMethod;
        if ((qualifiers != null) && includeQualifier) {
            me.qualifiers = CloneUtil.cloneQualifiers(qualifiers);
        }
        if (parameters != null) {
            me.parameters = CloneUtil.cloneParameter(parameters, includeQualifier);
        }

        return me;
    }
}
