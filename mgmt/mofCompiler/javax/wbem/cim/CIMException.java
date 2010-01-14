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
 * Superclass of classes used to represent exceptional CIM conditions.
 * These exceptions are recoverable by user programs and therefore must
 * be declared in the throws clause of a method and caught by using 
 * try/catch statements.
 *
 * Any method that throws any instance of CIMException that is not a 
 * RuntimeException must declare the CIMException(s) in its throws 
 * clause as part of the method's declaration. This is a Java language
 * requirement.  Any method that calls this method must either catch the
 * exception(s) by using try/catch statements or declare the exception(s)
 * in its own throws clause.
 * 
 * @author      Sun Microsystems, Inc.
 * @version     1.6 02/28/02
 * @since       WBEM 1.0
 */
public class CIMException extends Exception {

    final static long serialVersionUID = 200;


    private String ID = null;
    private Object[] params = null;
    private String description;

    /*
     * Order matters!
     */
    private String[] xmlnames = {"CIM_ERR_FAILED",
				 "CIM_ERR_ACCESS_DENIED",
				 "CIM_ERR_INVALID_NAMESPACE",
				 "CIM_ERR_INVALID_PARAMETER",
				 "CIM_ERR_INVALID_CLASS",
				 "CIM_ERR_NOT_FOUND",
				 "CIM_ERR_NOT_SUPPORTED",
				 "CIM_ERR_CLASS_HAS_CHILDREN",
				 "CIM_ERR_CLASS_HAS_INSTANCES",
				 "CIM_ERR_INVALID_SUPERCLASS",
				 "CIM_ERR_ALREADY_EXISTS",
				 "CIM_ERR_NO_SUCH_PROPERTY",
				 "CIM_ERR_TYPE_MISMATCH",
				 "CIM_ERR_QUERY_LANGUAGE_NOT_SUPPORTED",
				 "CIM_ERR_INVALID_QUERY",
				 "CIM_ERR_METHOD_NOT_AVAILABLE",
				 "CIM_ERR_METHOD_NOT_FOUND"};
    /**
     * Default cim exception.
     */
    public final static String DEFAULT = "DEFAULT";

    /**
     * Default cim exception with one parameter. This is no longer used.
     */
    public final static String PDEFAULT = "PDEFAULT";

    /**
     * General CIMException. Requires one parameter.
     */
    public final static String CIM_ERR_FAILED = "CIM_ERR_FAILED";

    /**
     * Access Denied CIMException.
     * This can be thrown by the WBEM Server or a Provider if the principal
     * is not authenticated or autorized.
     */
    public final static String CIM_ERR_ACCESS_DENIED = "CIM_ERR_ACCESS_DENIED";
    
    /**
     * The action is not supported. This can be thrown by a provider or the
     * WBEM Server itself when it does not support a particular method.
     */
    public final static String CIM_ERR_NOT_SUPPORTED = "CIM_ERR_NOT_SUPPORTED";

    /**
     * This error is thrown when a client tries to connect to a CIM object
     * manager which does not support the version of the client. This will
     * typically happen when new clients try to connect to old CIM object
     * managers.
     */
    public final static String VER_ERROR = "VER_ERROR";

    /**
     * Invalid parameter is passed to a method.
     * This error message uses one parameter, the parameter 
     * which caused the exception.
     */
    public final static String CIM_ERR_INVALID_PARAMETER = 
				"CIM_ERR_INVALID_PARAMETER";

    /**
     * Invalid namespace specified.
     * This error message uses one parameter, the invalid namespace name.
     */
    public final static String CIM_ERR_INVALID_NAMESPACE = 
				"CIM_ERR_INVALID_NAMESPACE";

    /**
     * Invalid class specified.
     * For e.g. when one tries to add an instance for a class that does
     * not exist.
     * This error message uses one parameter, the invalid class name.
     */
    public final static String CIM_ERR_INVALID_CLASS = 
				"CIM_ERR_INVALID_CLASS";

    /**
     * Element cannot be found.
     * This error message uses one parameter, the element that 
     * cannot be found.
     */
    public final static String CIM_ERR_NOT_FOUND = 
				"CIM_ERR_NOT_FOUND";

    /**
     * Element already exists.
     * This error message uses one parameter, the element that 
     * already exists.
     */
    public final static String CIM_ERR_ALREADY_EXISTS =
				"CIM_ERR_ALREADY_EXISTS";

    /**
     * Class has subclasses.
     * This error message uses one parameter, the class name.
     * The exception is thrown by the WBEM Server to disallow
     * invalidation of the subclasses by the super class deletion. Clients must
     * explicitly delete the subclasses first. The check for subclasses is
     * made before the check for class instances.
     */
    public final static String CIM_ERR_CLASS_HAS_CHILDREN =
				"CIM_ERR_CLASS_HAS_CHILDREN";

    /**
     * Class has instances.
     * This error message uses one parameter, the class name.
     * The exception is thrown by the WBEM Server to disallow
     * invalidation of the static instances by the class deletion. Clients must
     * explicitly delete the static instances first. The check for subclasses is
     * made before the check for class instances i.e. CIM_ERR_CLASS_HAS_CHILDREN
     * is thrown before CIM_ERR_CLASS_HAS_INSTANCES
     */
    /**
     * Class has instances.
     * Thsi error message uses one parameter, the class name.
     * For example, this exception would be thrown if you try
     * to delete a class that has instances.
     */
    public final static String CIM_ERR_CLASS_HAS_INSTANCES =
				"CIM_ERR_CLASS_HAS_INSTANCES";

    /**
     * Invalid query.
     * This error message uses has two parameters, the 
     * invalid snippet of the query, and additional info with the actual error
     * in the query.
     */
    public final static String CIM_ERR_INVALID_QUERY =
				"CIM_ERR_INVALID_QUERY";

    /**
     * The property does not exist in the class/instance being manipulated.
     * This error message uses has one parameter, the  name of the property that
     * does not exist.
     */
    public final static String CIM_ERR_NO_SUCH_PROPERTY =
				"CIM_ERR_NO_SUCH_PROPERTY";

    /**
     * Low memory.
     */
    public final static String CIM_ERR_LOW_ON_MEMORY =
				"CIM_ERR_LOW_ON_MEMORY";

    /**
     * The requested query language is not recognized.
     * This error message has one parameter, the invalid query language string.
     */
    public final static String CIM_ERR_QUERY_LANGUAGE_NOT_SUPPORTED =
				"CIM_ERR_QUERY_LANGUAGE_NOT_SUPPORTED";

    /**
     * The method is not available
     */
    public final static String CIM_ERR_METHOD_NOT_AVAILABLE =
				 "CIM_ERR_METHOD_NOT_AVAILABLE";
        
    /**
     * The method is not found
     */
    public final static String CIM_ERR_METHOD_NOT_FOUND =
				 "CIM_ERR_METHOD_NOT_FOUND";

    
    /** 
     * The request action timed out.
     */
    public final static String TIMED_OUT = "TIMED_OUT";


    /**
     * Contructs a CIMException with no detail message.
     */
    public CIMException() {
	this("DEFAULT");
    }

    /**
     * Creates a CIMException with the specified message.
     *
     * @param s		the symbolic name of the CIMException. E.g. 
     * CIM_ERR_FAILED, CIM_ERR_NOT_FOUND, etc.
     */
    public CIMException(String s) {
	super(s);
	if ((s == null) || (s.length() == 0)) {
	    ID = "DEFAULT";
	} else {
	    ID = s;
	}
	description = "";
    }
    
    public CIMException(String s, String description) {
        super(s);
        if ((s == null) || (s.length() == 0)) {
            ID = "DEFAULT";
        } else {
            ID = s;
        }
        this.description = description;
    }
    /**
     * Creates a CIMException with the specified message 
     * and one exception parameter.
     *
     * @param s		the symbolic name of the CIMException. E.g. 
     * CIM_ERR_FAILED, CIM_ERR_NOT_FOUND, etc.
     * @param param     exception parameter.
     */
    public CIMException(String s, Object param) {
	this(s);
	params = new Object[1];
	params[0] = param;
    }

    /**
     * Creates a CIMException with the specified message 
     * and two exception parameters.
     *
     * @param s		the symbolic name of the CIMException. E.g. 
     * CIM_ERR_FAILED, CIM_ERR_NOT_FOUND, etc.
     * @param param1    first Exception parameter.
     * @param param2    second Exception parameter.
     */
    public CIMException(String s, Object param1, Object param2) {
	this(s);
	params = new Object[2];
	params[0] = param1;
	params[1] = param2;
    }

    /**
     * Creates a CIMException with the specified message
     * and three exception parameters.
     *
     * @param s		the symbolic name of the CIMException. E.g. 
     * CIM_ERR_FAILED, CIM_ERR_NOT_FOUND, etc.
     * @param param1    first Exception parameter.
     * @param param2    second Exception parameter.
     * @param param3    third Exception parameter.
     *
     */
    public CIMException(String s, Object param1, 
				  Object param2, 
			  	  Object param3) {
	this(s);
	params = new Object[3];
	params[0] = param1;
	params[1] = param2;
	params[2] = param3;
    }

    /**
     * Creates a CIMException with the specified message
     * and an array of exception parameters.
     *
     * @param s		the symbolic name of the CIMException. E.g. 
     * CIM_ERR_FAILED, CIM_ERR_NOT_FOUND, etc.
     * @param param     array of exception parameters.
     *
     */
    public CIMException(String s, Object[] param) {
	this(s);
	params = (Object [])param.clone();
    }

    /**
     * Returns the detail message for this exception.
     * Application programs can use this to take appropriate
     * action for specific exceptions.
     * @return String the detail message.
     */
    public String getID() {
	return ID;
    }

    /**
     * Returns the parameters for this exception.
     * Application programs can use to take appropriate
     * action for specific exceptions.
     *
     * @return Object[] the exception parameters.
     */
    public Object[] getParams() {
	return params;
    }

    /**
     * Sets the substitution args for the exception.
     * Primarily used by the LogRecord class to
     * reconstruct an exception from a string.
     *
     * @param parm the substitution paramaters
     */
    public void setParams(Object parm[]) {
	if (parm != null) {
	    params = (Object[])parm.clone();
	}
    }

    /**
     * Prints out the ID and a comma separated list of parameters on a
     * new line.
     * 
     * @return a String representation of the exception
     */
    public String toString() {
	StringBuffer sb = new StringBuffer();
	sb.append(""+ID);
	if (params == null) {
	    return sb.toString();
	}
	sb.append(":\n");
	for (int i = 0; i < params.length; i++) {
	    sb.append(""+params[i]);
	    if (i != params.length - 1) { sb.append(",");
            }
	}
	return sb.toString();
    }

    /**
     * Retrieve the description for this exception.
     * @return The description that has been set for this exception. NULL if
     * no description has been set.
     */
    public String getDescription() {
	return description;
    }

    /**
     * Set the description for this exception.
     * @param description the description to be set.
     */
    public void setDescription(String description) {
	this.description = description;
    }

    /**
     * Errors in CIM Operations over HTTP have to be reported as
     * integers.
     * 
     * @param s the string for the code
     * @return the integer representation of the XML code
     */
    public int getXmlCode(String s) {
	for (int i = 0; i < xmlnames.length; i++) {
	    if (xmlnames[i].equals(s)) {
		return i + 1;
	    }
	}
	return -1;
    }

    /**
     * Gets the name when you know the code.
     * 
     * @param code the int code
     * @return the String representation of the XML code
     */
    public String getXmlCode(int code) {
	code = (1 <= code && code <= xmlnames.length ? code : 1);
	return xmlnames[code - 1];
    }

    /**
     * Returns true if the exception is a valid XML exception
     * 
     * @param s the string for the code
     * @return true if the code is an acceptable XML code
     */
    public boolean isXmlCode(String s) {
	return getXmlCode(s) > 0;
    }
}
