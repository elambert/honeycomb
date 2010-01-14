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

/** 
 * 
 * Exception class representing semantic exceptions that occur in a CIM
 * element. These exceptions are generally thrown when the CIMOM tries to
 * add/modify/delete CIM elements and encounters situations that are illegal
 * according to the CIM spec.
 * 
 * @author      Sun Microsystems, Inc.
 * @version 	1.1 03/01/01
 * @since	WBEM 1.0
 */

public class CIMSemanticException extends CIMException {

    final static long serialVersionUID = 200;


    /** 
     * No such qualifier exception. This is 
     * specified when the required qualifier is not found. The 
     * message needs two parameters, the element to which
     * the qualifier is being applied, and the qualifier name.
     */
    public final static String NO_SUCH_QUALIFIER2 = "NO_SUCH_QUALIFIER2";

    /** 
     * No such qualifier exception, but where we do not care
     * which particular element is causing it. This messages has
     * one parameter, the qualifier name.
     */
    public final static String NO_SUCH_QUALIFIER1 = "NO_SUCH_QUALIFIER1";

    /** 
     * Scope error exception. 
     * This message is specified when the required 
     * qualifier scope does not allow it to be applied 
     * to the particular CIM element. This message includes 
     * three parameters, the element name, the qualifier name, 
     * and the type of CIM element on which it is being applied.
     */
    public final static String SCOPE_ERROR = "SCOPE_ERROR";

    /** 
     * The qualifier does not have a value. For e.g. when an OVERRIDE qualifier
     * is used, but no value is specified.
     */
    public final static String NO_QUALIFIER_VALUE = "NO_QUALIFIER_VALUE";

    /** 
     * Invalid qualifier name. 
     * A qualifier name cannot be zero length. 
     */
    public final static String INVALID_QUALIFIER_NAME = 
				"INVALID_QUALIFIER_NAME";

    /**
     * An element tries to override a qualifier that has a 
     * DisableOverride flavor.
     */
    public final static String QUALIFIER_UNOVERRIDABLE =
				"QUALIFIER_UNOVERRIDABLE";

    /** 
     * Type cast exception. This message is 
     * specified when the there is a mismatch in the value of a property or
     * method parameter and its defined type.
     * This error message uses five parameters, the element name, 
     * the class to which it belongs,
     * the type defined for the element, the type of the value being assigned
     * and the actual value.
     */
    public final static String TYPE_ERROR = "TYPE_ERROR";

    /**
     * Creates a CIMSemanticException with no detail message.
     */
    public CIMSemanticException() {
	super();
    }

    /**
     * Creates a CIMSemanticException with the specified detail
     * message.
     *
     * @param ID the detail message.
     */
    public CIMSemanticException(String ID) {
	super(ID);
    }

    /**
     * Creates a CIMSemanticException with the specified detail
     * message and one exception parameter.
     *
     * @param s		the detail message.
     * @param param     exception parameter.
     */
    public CIMSemanticException(String s, Object param) {
	super(s, param);
    }

    /**
     * Creates a CIMSemanticException with the specified detail
     * message and two exception parameters.
     *
     * @param s		the detail message.
     * @param param1    first Exception parameter.
     * @param param2    second Exception parameter.
     */
    public CIMSemanticException(String s, Object param1, Object param2) {
	super(s, param1, param2);
    }

    /**
     * Creates a CIMSemanticException with the specified detail
     * message and three exception parameters.
     *
     * @param s		the detail message
     * @param param1    first Exception parameter.
     * @param param2    second Exception parameter.
     * @param param3    third Exception parameter.
     *
     */
    public CIMSemanticException(String s, 
				Object param1, 
				Object param2, 
				Object param3) {
	super(s, param1, param2, param3);
    }

    /**
     * Creates a CIMSemanticException with the specified detail
     * message and an array of exception parameters.
     *
     * @param s		the detail message.
     * @param param     array of exception parameters
     *
     */
    public CIMSemanticException(String s, Object[] param) {
	super(s, param);
    }

}
