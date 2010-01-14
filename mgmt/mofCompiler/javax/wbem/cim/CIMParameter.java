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


/** 
 * Creates and instantiates a CIM parameter, a value passed
 * to a CIM method from a calling method.
 * 
 * @author  Sun Microsystems, Inc. 
 * @since   WBEM 1.0
 */
public class CIMParameter 
    extends CIMQualifiedElement
    implements Cloneable, CIMTypedElement {

    final static long serialVersionUID = 200;

    /**
     * CIM data type of this CIM parameter.
     * @serial
     */
    protected CIMDataType type; 
    
    /**
     * Size of this CIM parameter.
     * @serial
     */
    protected int size; 
    
    /** 
     * Sets the data type of this parameter to the specified
     * CIM data type
     *
     * @param type The CIM data type assigned to the parameter
     *				
     */
    public void setType(CIMDataType type) {
        this.type = type;
    }
	    
    /**      
     * Returns the data type of this parameter
     *
     * @return CIMDataType	The CIM data type of this parameter
     */
    public CIMDataType getType() {
        return type;
    }

    /** 
     * Returns the size of this parameter
     *
     * @return the integer value of the size of this parameter
     */
    public int getSize() {
        return size;
    }
    
    /** 
     * Sets the size of this parameter to the specified size
     *
     * @param size the integer size assigned to this parameter 
     */
    public void setSize(int size) {
        this.size = size;
    }
    
    /** 
     * Creates and instantiates a CIM parameter
     */
    public CIMParameter() {
        this("");
    }

    /**      
     * Takes a string for the name of an existing CIM parameter and 
     * creates a new instance of a CIM parameter, using the name
     * and identifier of the existing CIM parameter.
     *
     * @param name 	name of an existing CIM parameter
     */
    public CIMParameter(String name) {
        super(name);
    }

    public CIMParameter(String name,
                        CIMDataType type) {
        super(name);
        setType(type);
    }

    /**
     * Returns a String representation of the CIMParameter.
     * This method is intended to be used only for debugging purposes,
     * and the format of the returned string may vary between
     * implementations.  The returned string may be empty but may not be
     * null.
     *
     * @return string representation of this parameter
     */
    public String toString() {
        return toMOF();
    }

    /**
     * Returns a MOF representation of the CIMParameter
     *
     * @return  a string representation of this parameter in
     *          Managed Object Format (MOF)
     */
    public String toMOF() {
        MOFFormatter mf = new MOFFormatter();
        return mf.toString(this);
    }
    
    /**
     * Returns an object that is a copy of this CIM parameter.
     * This method calls clone(boolean includeQualifier) with
     * the true value, which indicates that this CIM parameter
     * contains CIM qualifiers.
     *
     * @return  An object that is a copy of this CIM parameter.
     */
    public synchronized Object clone() {
        return clone(true);
    }
    
    /**
     * Returns an object that is a copy of this CIM parameter.
     * 
     * @param 	includeQualifier A boolean that is true if this
     * CIM parameter contains CIM qualifiers, otherwise false.
     * 
     * @return  An object that is a copy of this CIM parameter.
     * 
     */
    synchronized Object clone(boolean includeQualifier) {
        CIMParameter parameter = new CIMParameter();
        parameter.name = name;
        parameter.type = type;
        parameter.size = size;
        if (qualifiers != null && includeQualifier) {
            parameter.qualifiers = CloneUtil.cloneQualifiers(qualifiers);
        }
        return parameter;
    }
}
