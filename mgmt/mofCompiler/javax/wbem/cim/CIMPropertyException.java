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
 * Exception class representing property exceptions that occur in a CIM
 * property. 
 * 
 * @author      Sun Microsystems, Inc.
 * @version 	1.1 03/01/01
 * @since	WBEM 1.0
 */
public class CIMPropertyException extends CIMSemanticException {

    final static long serialVersionUID = 200;

    /**
     * The overridden property does not exist in the class hierarchy. 
     * This error message uses two parameters, the overriding property
     * name, and the overridden property name.
     */
    public final static String NO_OVERRIDDEN_PROPERTY = 
				"NO_OVERRIDDEN_PROPERTY";

    /**
     * The overridden property has already been overridden.
     * This error message use three parameters, the property 
     * which is doing the
     * overriding, the overridden property, the property
     * it has been overridden by.
     */
    public final static String PROPERTY_OVERRIDDEN = "PROPERTY_OVERRIDDEN";

    /**
     * A non-key is overriding a key. 
     * This is not allowed in CIM. This error message uses two 
     * parameters, the overriding and overridden property names.
     */
    public final static String KEY_OVERRIDE = "KEY_OVERRIDE";

    /**
     * A non-association class is defined with a 
     * reference as a property. In CIM, only associations can 
     * have references. This error message uses two parameters, 
     * the name of the reference and the name of the 
     * class which is trying to define  the reference.
     */
    public final static String CLASS_REFERENCE = "CLASS_REFERENCE";

    /**
     * A class is trying to define a new key.
     * In CIM once keys have been defined, we cannot introduce 
     * new keys in the class hierarchy. This error message uses
     * two parameters, the name of the new key property and the name 
     * of the class which is trying to define the new key.
     */
    public final static String NEW_KEY = "NEW_KEY";

    /**
     * Creates a CIMPropertyException with no detail message.
     */
    public CIMPropertyException() {
	super();
    }

    /**
     * Creates a CIMPropertyException with the specified message. 
     *
     * @param s		the detail message.
     */
    public CIMPropertyException(String s) {
	super(s);
    }

    /**
     * Creates a CIMPropertyException with the specified message 
     * and one exception parameter.
     *
     * @param s		the detail message.
     * @param param     exception parameter.
     */
    public CIMPropertyException(String s, Object param) {
	super(s, param);
    }

    /**
     * Creates a CIMPropertyException with the specified message 
     * and two exception parameters.
     *
     * @param s		the detail message.
     * @param param1    first Exception parameter.
     * @param param2    second Exception parameter.
     */
    public CIMPropertyException(String s, Object param1, Object param2) {
	super(s, param1, param2);
    }

    /**
     * Creates a CIMPropertyException with the specified message 
     * and three exception parameters.
     *
     * @param s		the detail message.
     * @param param1    first Exception parameter.
     * @param param2    second Exception parameter.
     * @param param3    third Exception parameter.
     *
     */
    public CIMPropertyException(String s, 
				Object param1, 	
				Object param2, 
				Object param3) {
	super(s, param1, param2, param3);
    }

    /**
     * Creates a CIMPropertyException with the specified message
     * and an array of exception parameters.
     *
     * @param s		the detail message.
     * @param param     Array of exception parameters
     *
     */
    public CIMPropertyException(String s, Object[] param) {
	super(s, param);
    }

}
