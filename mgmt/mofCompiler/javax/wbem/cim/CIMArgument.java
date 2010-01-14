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
 * This class represents instances of parameters that are passed into an 
 * extrinsic method when it is invoked. The argument has a name, value and 
 * may have qualifiers.
 * 
 * @author	Sun Microsystems, Inc. 
 * @since	WBEM 1.0
 */
public class CIMArgument extends CIMParameter implements Cloneable {

    final static long serialVersionUID = 200;

    /**
     * The value of this argument.
     */
    protected CIMValue value;
    
    /**
     * Sets the value for this argument. 
     *
     * @param value The value for this argument.
     */  
    public void setValue(CIMValue value) {
        this.value = value;
        if ((value != null) && (value.getType() != null)) {
            type = value.getType();
        }
    }

    /**
     * Gets the value for this argument
     * 
     * @return The CIM value for this argument.
     */
    public CIMValue getValue() {
        return value;
    }

    /** 
     * Sets the data type of this parameter to the specified
     * CIM data type. If a value is set, the type comes from the 
     * value's type and this method is ignored.
     *
     * @param type The CIM data type assigned to the argument
     *              
     */
    public void setType(CIMDataType type) {
        if (value != null) {
            return;
        } else {
            super.setType(type);
        }
    }

    /** 
     * Instantiates a CIM argument
     */
    public CIMArgument() {
        this("");
    }

    /**      
     * Creates a CIMArgument of the appropriate name. When used with method
     * invocations, the argument names corresponds to the parameters defined
     * for the method being invoked.
     *
     * @param name the name of the CIM argument
     */
    public CIMArgument(String name) {
        super(name);
    }

    /**      
     * Creates a CIMArgument of the appropriate name and value. When used with 
     * method invocations, the argument names corresponds to the parameters 
     * defined for the method being invoked.
     *
     * @param name  name of the CIM argument
     * @param value The value for this argument. If the value and its type
     *               is non-null, this argument's type is updated with the
     *               value's type.
     */
    public CIMArgument(String name, CIMValue value) {
        super(name);
        setValue(value);
    }

    /**
     * Clones this CIM argument. The included qualifiers are
     * themselves cloned.
     * 
     * @param includeQualifier Specifies if the cloned object should
     *         contain cloned qualifiers or not.
     * 
     * @return A clone of this CIM argument with or without qualifiers
     */
    public synchronized Object clone(boolean includeQualifier) {
        CIMArgument argument = new CIMArgument();
        argument.name  = name;
        argument.size  = size;
        argument.type  = type;
        argument.value = value;
        if (qualifiers != null && includeQualifier) {
            argument.qualifiers = CloneUtil.cloneQualifiers(qualifiers);
        }
        return argument;
    }
}
