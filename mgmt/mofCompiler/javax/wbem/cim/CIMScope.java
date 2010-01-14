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
 * A class to encapsulate the different CIM Scopes.
 *
 * @author 	Sun Microsystems, Inc.
 * @version 	1.1 03/01/01
 * @since	WBEM 1.0
 */
public class CIMScope implements Serializable {

    final static long serialVersionUID = 200;

    /**
     * Integer value representing a CIM schema
     */
    public final static int	SCHEMA	    = 0;

    /**
     * Integer value representing a CIM class
     */
    public final static int	CLASS	    = 1;

    /**
     * Integer value representing a CIM association
     */
    public final static int	ASSOCIATION = 2;

    /**
     * Integer value representing a CIM indication
     */
    public final static int	INDICATION  = 3;

    /**
     * Integer value representing a CIM property
     */
    public final static int	PROPERTY    = 4;

    /**
     * Integer value representing a CIM reference
     */
    public final static int	REFERENCE   = 5;


    /**
     * Integer value representing a CIM method
     */
    public final static int	METHOD	    = 6;


    /**
     * Integer value representing a CIM parameter
     */
    public final static int	PARAMETER   = 7;
    
    /**
     * Integer value representing any CIM element
     */
    public final static int	ANY	    = 8;
    
    /**
     * Initializes an array of predefined CIM scope objects
     *
     */
    protected static CIMScope predefined[] = new CIMScope[9];

    /**
     * integer of a CIM scope. Must be one of the predefined 
     * scope values (0 through 8).
     */
    private int scope; 
    			
    
    // Constructors

    /**
     * Creates a new CIM scope object with the specified scope.
     *
     * @param scope 	the CIM scope. Valid CIM scope values range from
     *			0 to 8, inclusive. A value outside this range
     *			returns an error message.
     */
    public CIMScope(int scope) {
    	if (scope < CIMScope.SCHEMA || scope > CIMScope.ANY) {
	    throw new IllegalArgumentException();
	}
	this.scope = scope;
    }

    /**
     * Returns a CIM scope object with the specified CIM scope
     *
     * @param scope 	the CIM scope. Valid CIM scope values range from
     *			0 to 8, inclusive. A value outside this range
     *			returns an error message.
     *
     * @return The predefined CIM scope.
     */
    public static CIMScope getScope(int scope) {
    	if (scope < CIMScope.SCHEMA || scope > CIMScope.ANY) {
	    throw new IllegalArgumentException();
	}
	if (predefined[scope] == null) {
	    predefined[scope] = new CIMScope(scope);
	}
	return predefined[scope];

    }

    /**
     * Returns an integer representing the CIM scope 
     *
     * @return The integer representing the CIM scope.
     */
    public int getScope() {
	return scope;
    }

    /**
     *
     * Returns true if the specified object is a valid CIM scope. 
     * Otherwise, false.
     *
     * @param  o	The object to compare
     *
     * @return 	True if the specified object is a valid
     *		CIM scope. Otherwise, false.
     *
     */
    public boolean equals(Object o) {
	if (!(o instanceof CIMScope)) {
	    return false;
        }
	return (scope == ((CIMScope)o).scope);
    }

    /**
     * Returns a String representation of the CIMScope.
     * This method is intended to be used only for debugging purposes,
     * and the format of the returned string may vary between
     * implementations.  The returned string may be empty but may not be
     * null.
     *
     * @return A string representation of this scope.
     */
    public String toString() {
	return toMOF();
    }

    /**
     * Returns a MOF representation of the CIMScope.
     *
     * @return  A string representation of this scope in
     *          Managed Object Format (MOF).
     */
    public String toMOF() {
        MOFFormatter mf = new MOFFormatter();
        return mf.toString(this);
    }
}
