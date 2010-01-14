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
 *Contributor(s): _______________________________________
*/

package javax.wbem.cim;

import java.io.Serializable;

/**
 * A class to encapsulate the different CIM qualifier flavors,
 * which specify overriding and inheritance rules. These rules
 * specify how qualifiers are transmitted from classes to derived
 * classes, or from classes to instances.
 *
 * @author 	Sun Microsystems, Inc.
 * @version 	1.3 08/22/01
 * @since	WBEM 1.0
 */
public class CIMFlavor implements Serializable {

    final static long serialVersionUID = 200;


    /**
     * this qualifier is overridable
     */
    public final static int	ENABLEOVERRIDE	    = 0;  

    /**
     * this qualifier cannot be overriden
     */
    public final static int	DISABLEOVERRIDE	    = 1;

    /**
     * this qualifier applies only to the class in which it is declared
     */
    public final static int	RESTRICTED	    = 2;

    /**
     * this qualifier is inherited by any subclass
     */
    public final static int	TOSUBCLASS	    = 3;

    /**
     * this qualifier can be specified in multiple locales (language
     * and country combination).
     */
    public final static int	TRANSLATE	    = 4;

    protected static CIMFlavor predefined[] = new CIMFlavor[5];

    /**
     * Initializes an array of flavors
     */
    private int flavor;

    /**
     * Creates a new CIMFlavor object with the specified flavor
     *
     * @param flavor 	the flavor. Must be one of the predefined
     *			flavor values (0 through 4). A value outside
     *			this range returns an error message.
     * @exception	IllegalArgumentException
     */
    public CIMFlavor(int flavor) {
    	if (flavor < CIMFlavor.ENABLEOVERRIDE || flavor > CIMFlavor.TRANSLATE) {
	    throw new IllegalArgumentException();
	}
	this.flavor = flavor;
    }

    /**
     * Returns a CIMFlavor object with the specified predefined flavor.
     *
     * @param flavor 	the flavor. Must be one of the predefined
     *			flavor values (0 through 4). A value outside
     *			this range returns an error message.
     * @return a CIMFlavor object for the predefined flavor.
     * @exception IllegalArgumentException
     */
    public static CIMFlavor getFlavor(int flavor) {
    	if (flavor < CIMFlavor.ENABLEOVERRIDE || flavor > CIMFlavor.TRANSLATE) {
	    throw new IllegalArgumentException();
	}
	if (predefined[flavor] == null) {
	    predefined[flavor] = new CIMFlavor(flavor);
	}
	return predefined[flavor];

    }

    /**
     * Returns the qualifier flavor 
     *
     * @return the integer representing the CIM qualifier flavor
     */
    public int getFlavor() {
	return flavor;
    }

    /**
     *
     * Compares this CIMFlavor to the specified object.
     *
     * @param  obj the object to compare with.
     *
     * @return true if the objects are the same; false otherwise.
     */
    public boolean equals(Object obj) {
	if (!(obj instanceof CIMFlavor)) {
	    return false;
	}
	return (flavor == ((CIMFlavor)obj).flavor);
    }

    /**
     * Returns a String representation of the CIMFlavor. 
     * This method is intended to be used only for debugging purposes,
     * and the format of the returned string may vary between
     * implementations.  The returned string may be empty but may not be
     * null.
     *
     * @return string representation of this flavor
     */
    public String toString() {
	return toMOF();
    }

    /**
     * Returns a MOF representation of the CIMFlavor.
     *
     * @return  a string representation of this flavor in 
     * 		Managed Object Format (MOF) 
     */
    public String toMOF() {
        MOFFormatter mf = new MOFFormatter();
        return mf.toString(this);
    }
}
