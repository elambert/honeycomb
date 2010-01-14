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
 * The CIMMethodException class is used for exceptions that occur for methods.
 * 
 * @author      Sun Microsystems, Inc.
 * @version 	1.1 03/01/01
 * @since	WBEM 1.0
 */
public class CIMMethodException extends CIMSemanticException {

    final static long serialVersionUID = 200;


    /**
     * The method of a subclass is trying to override the method of the 
     * superclass, but the method of the superclass already has been 
     * overridden by a method that belongs to another subclass. The 
     * method that you are trying to override does not exist in the 
     * class hierarchy because it has not been defined.    
     * This error message uses two parameters:
     * - {0} is replaced by the name of the overriding method.
     * - {1} is replaced by the name of the overridden method.
     */
    public final static String NO_OVERRIDDEN_METHOD = 
				"NO_OVERRIDDEN_METHOD";

    /**
     * A method is specified to override another method that has already 
     * been overridden by a third method. Once a method has been overridden, 
     * it cannot be overridden again.   
     * This error message takes three parameters:
     * - {0} is replaced by the name of the method that tries to override the
     *    specified method, represented by {1}
     * - {1} is replaced by the method that already has been overridden by the 
     *   method represented by {2}
     * - {2} is replaced by the method that has overridden parameter {1}
     */
    public final static String METHOD_OVERRIDDEN = "METHOD_OVERRIDDEN";


    /**
     * In most cases, the specified method was not defined for the class. 
     * If the method is defined for the specified class, another method 
     * name may have been mispelled or typed differently in the definition. 
     * This error message uses two parameters:
     * - {0} is replaced by the name of the specified method
     * - {1} is replaced by the name of the class
     */
    public final static String NO_SUCH_METHOD = "NO_SUCH_METHOD";

    /**
     * Creates a CIMMethodException with no detail message.
     */
    public CIMMethodException() {
	super();
    }

    /**
     * Creates a CIMMethodException with the specified message. 
     *
     * @param s		the detail message.	
     */
    public CIMMethodException(String s) {
	super(s);
    }

    /**
     * Creates a CIMMethodException with the specified message 
     * and one exception parameter.
     *
     * @param s		the detail message.	
     * @param param     exception parameter.
     */
    public CIMMethodException(String s, Object param) {
	super(s, param);
    }

    /**
     * Creates a CIMMethodException with the specified message 
     * and two exception parameters.
     *
     * @param s		the detail message.	
     * @param param1    first Exception parameter.
     * @param param2    second Exception parameter.
     */
    public CIMMethodException(String s, Object param1, Object param2) {
	super(s, param1, param2);
    }

    /**
     * Creates a CIMMethodException with the specified message 
     * and three exception parameters.
     *
     * @param s		the detail message.	
     * @param param1    first Exception parameter.
     * @param param2    second Exception parameter.
     * @param param3    third Exception parameter.
     *
     */
    public CIMMethodException(String s, Object param1, 
					Object param2, 
					Object param3) {
	super(s, param1, param2, param3);
    }

    /**
     * Creates a CIMMethodException with the specified message
     * and an array of exception parameters.
     *
     * @param s		the detail message.	
     * @param param     array of exception parameters.
     *
     */
    public CIMMethodException(String s, Object[] param) {
	super(s, param);
    }


}
