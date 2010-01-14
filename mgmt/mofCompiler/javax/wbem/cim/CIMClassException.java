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
 */

package javax.wbem.cim;

/** 
 * The CIMClassException class represents an error condition that occurs 
 * in a CIM class. Errors occur when the CIM Object Manager encounters
 * an error that does not adhere to 
 * the guidelines of the CIM Specification. The CIM Object Manager
 * generates CIMClassException when one of these error conditions occur.
 * 
 * @author      Sun Microsystems, Inc.
 * @version 	1.1 03/01/01
 * @since	WBEM 1.0
 */

public class CIMClassException extends CIMSemanticException {

    final static long serialVersionUID = 200;


    /**  
     * A concrete class is defined without a key. All concrete, non-abstract 
     * classes must have at least one key property, flagged with a key
     * qualifier.
     * This error message uses one parameter which is replaced by
     * the name of the class.
     */
    public final static String KEY_REQUIRED = "KEY_REQUIRED";

    /**
     * An association class is defined with less than two references. All
     * association classes require at least two references.
     * This error message uses one parameter which is replaced by
     * the name of the association class.
     */
    public final static String REF_REQUIRED = "REF_REQUIRED";

    /**
     * The superclass of the specified class does not exist. 
     * This error message uses two parameters:
     *     - the name of the specified subclass.
     *     - the name of the class for which the specified 
     *	     subclass does not exist.
     */
    public final static String CIM_ERR_INVALID_SUPERCLASS = 
    "CIM_ERR_INVALID_SUPERCLASS";

    /**
     * Instances were programmed for the specified class, but the specified 
     * class is abstract. Abstract classes cannot have instances.
     * This class uses one parameter which is replaced by the name of 
     * the abstract class.
     */
    public final static String ABSTRACT_INSTANCE = "ABSTRACT_INSTANCE";

    /**
     * Create a CIMClassException with no detail message.
     */
    public CIMClassException() {
	super();
    }

    /**
     * Create a CIMClassException with the specified detail
     * message 
     *
     * @param s the detail message.
     */
    public CIMClassException(String s) {
	super(s);
    }

    /**
     * Creates a CIMClassException with the specified detail
     * message and one exception parameter.
     *
     * @param s		the detail message.
     * @param param     exception parameter.
     */
    public CIMClassException(String s, Object param) {
	super(s, param);
    }

    /**
     * Creates a CIMClassException with the specified detail
     * message and two excep parameters.
     *
     * @param s		the detail message.
     * @param param1    first Exception parameter.
     * @param param2    second Exception parameter.
     */
    public CIMClassException(String s, Object param1, Object param2) {
	super(s, param1, param2);
    }

    /**
     * Creates a CIMClassException with the specified detail
     * message and three exception parameters.
     *
     * @param s		the detail message.
     * @param param1    first Exception parameter.
     * @param param2    second Exception parameter.
     * @param param3    third Exception parameter.
     *
     */
    public CIMClassException(String s, 
			     Object param1, 
			     Object param2, 
			     Object param3) {
	super(s, param1, param2, param3);
    }

    /**
     * Creates a CIMClassException with the specified detail
     * message and an array of exception parameters.
     *
     * @param s		the detail message.
     * @param param     array of exception parameters
     *
     */
    public CIMClassException(String s, Object[] param) {
	super(s, param);
    }

}
