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
import java.util.Vector;

/**
 * 
 * 
 * This class encapsulates any CIM value which can be assigned to properties,
 * references and qualifiers. CIM values have a datatype (CIMDataType) and
 * the actual value(s).
 *
 * @author	Sun Microsystems, Inc.
 * @version 	1.4 01/28/02
 * @since	wbem 1.0
 */
public class CIMValue implements Serializable {

    final static long serialVersionUID = 200;

    /**
     * Default CIM data type of this CIMValue is null.
     * @serial
     */
    private CIMDataType dataType = null;
    
    /**
     * Initialize valueVector to a null vector.
     * @serial
     */
    private Vector valueVector = null;
    
    /**
     * Initialize a null object.
     * @serial
     */
    private Object value = null;
    
    /**
     * By default, a CIMValue is not an array.
     * @serial
     */
    private boolean isArrayVal = false;

    /**
     * Initialize CIMValue true to a CIMValue Boolean object containing true.
     * @serial
     */
    public final static CIMValue TRUE = new CIMValue(Boolean.valueOf("true"));

    /**
     * Initialize CIMValue false to a CIMValue Boolean object containing false.
     * @serial
     */
    public final static CIMValue FALSE = new CIMValue(Boolean.valueOf("false"));

    private void initialize(Object o) {
	if (o instanceof Vector) {
	    Vector v = (Vector)o;
	    if (v == null) { 
                throw new NullPointerException();
            }
	    isArrayVal = true;
	    int type = CIMDataType.findType(v);
	    if (type != CIMDataType.NULL) {
		initialize(v, new CIMDataType(type));
	    } else {
		// If v contains all NULLs this is a NULL array type with no
		// data type.
		valueVector = (Vector)(v.clone());
	    }
	} else {
	    int type = CIMDataType.findType(o);
	    // If this is null data, this CIMValue is a NULL value with no
	    // data type.
	    if (type != CIMDataType.NULL) {
		if (o instanceof CIMObjectPath) {
		    initialize(o,
		    new CIMDataType(((CIMObjectPath)o).getObjectName()));
		} else {
		    initialize(o, new CIMDataType(type));
		}
	    }
	}
    }

    private void initialize(Object o, CIMDataType dt) {
	int type;
	int dtType;

	if (dt.isArrayType()) {
	    throw new IllegalArgumentException();
        }

	if (o == null) {
	    dataType = dt;
	    return;
	}

	type = CIMDataType.findType(o);
	dtType = dt.getType();

	if (type != dtType) { throw new 
		IllegalArgumentException();
        }

	value = o;
	dataType = dt;
    }

    private void initialize(Vector v, CIMDataType dt) {
	int type;
	int dtType;

	if (!dt.isArrayType()) {
	    throw new IllegalArgumentException();
        }
	
	if (v == null) {
	    dataType = dt;
	    return;
	}

	dtType = dt.getType();
	valueVector = new Vector();
	for (int i = 0; i < v.size(); i++) {
	    Object o;
	    o = v.elementAt(i);
	    if (o != null) {
		type = CIMDataType.findType(o);
		type = CIMDataType.findArrayType(type);
		if (type != dtType) { throw new 
			IllegalArgumentException();
                }
	    }
	    // Creating a new copy which is immutable
	    valueVector.addElement(o);
	}
	dataType = dt;
    }

    /**
     * Creates a primitive CIMValue.
     *
     * @param o Object used to initialize this CIMValue. This object should
     * be a valid Java representation of a primitive CIM value, like String,
     * Integer, UnsignedInt16, etc.
     * @param dt CIMDataType used to initialize this CIMValue.
     * @exception IllegalArgumentException if the type of o does not match
     *            dt OR dt is an array.
     */
    public CIMValue(Object o, CIMDataType dt) {
	initialize(o, dt);
    }
    
    /**
     * Creates an array CIMValue. An array CIMValue contains primitive values
     * of the same type. 
     *
     * @param v	Vector of primitive data types used to initialize the 
     * array CIMValue. These primitive values must be valid Java 
     * representations of a primitive CIM value, like String, Integer, 
     * UnsignedInt16, etc.
     * @param dt CIMDataType used to initialize this CIMValue. 
     * @exception IllegalArgumentException if v does not have the same data type
     * as specified in dt.
     */
    public CIMValue(Vector v, CIMDataType dt) {
	isArrayVal = true;
	initialize(v, dt);
    }
    /**
     * Creates a CIMValue which may either be primitive or a CIM array type.
     * @param o	Java object used to initialize this CIMValue. It may either
     * be a primitive type like String, Integer, UnsignedInt16, etc or a
     * Vector of primitive types. The data type is automatically determined.
     * @exception IllegalArgumentException if o is not a valid primitive type
     * or array type.
     *
     */
    public CIMValue(Object o) {
	initialize(o);
    }

    /**
     * Returns true if this CIMValue contains
     * an array value.
     *
     * @return 	True if this CIMValue contains
     *	  	an array value. Otherwise, false.
     * @deprecated use <code>getType().isArrayType()</code> instead
     */
    public boolean isArrayValue() {
	return (isArrayVal);
    }
    
    /**
     * Returns true if this CIMValue contains
     * a null data type.
     *
     * @return 	True if this CIMValue contains
     *	  	a null data type. Otherwise, returns false.
     */
    public boolean isNull() {
        return (dataType == null);
    }

    /**
     * @deprecated use @link{#isNull()} instead
     * @return  True if this CIMValue contains
     *      a null data type. Otherwise, returns false.
     * @see #isNull()
     */
    public boolean isNullValue() {
        return isNull();
    }

    /**
     * Returns a MOF representation of the CIMValue.
     *
     * @return  A string representation of this value in
     *          Managed Object Format (MOF).
     */
    public String toMOF() {
        MOFFormatter mf = new MOFFormatter();
        return mf.toString(this);
    }
    
    /**
     * Returns a String representation of the CIMValue.
     * This method is intended to be used only for debugging purposes,
     * and the format of the returned string may vary between
     * implementations.  The returned string may be empty but may not be
     * null.
     *
     * @return A string representation of this value.
     */
    public String toString() {
	return toMOF();
    }

    /**
     * Returns true if the value vector of this
     * CIMValue contains the specified Object.
     *
     * @param obj the object to check for
     * @return 	True if the value vector contains the 
     *		the specified object. Otherwise, 
     *		false.
     * @deprecated use <code>getValue().contains()</code> instead
     */
    public boolean contains(Object obj) {
	if (isArrayValue()) {
	    return (valueVector.contains(obj));
        } else
	{
	    if ((value == null) && (obj == null)) {
		return true;
	    }
	    return (obj.equals(value));
	}
    }

    /**
     * Returns true if the value and data type
     * of this CIMValue are empty. If the
     * data type is null, it has a null value - it is 
     * not empty.  
     *
     * @return 	True if the CIMValue is empty.
     *		Otherwise, false.
     */
    public boolean isEmpty() {
	// if dataType is null then that means this is a null value, not
	// empty
	return ((value == null) && (valueVector == null) && (dataType != null));
    }

    /**
     * Returns the size of the data type of 
     * this CIMValue. 
     *
     * @return The size of the data type of this 
     *		CIMValue.
     *
     */
    public int size() {
	if (isArrayValue()) {
	    return (valueVector.size());
        } else {
	    return CIMDataType.SIZE_SINGLE;
        }
    }

    /**
     * Returns the value of this CIMValue. 
     *
     * @return Object containing the value of
     *         this CIMValue. In case the value is an array CIMValue, a
     *         Vector of primitive values is returned.
     */
    public Object getValue() {
	if (isEmpty()) {
	    return null;
        }
	if (!isArrayValue()) {
	    return value;
        }
	// We want this vector to be immutable
	return valueVector.clone();
    }
    
    /**
     * Returns the CIM data type of this CIMValue. 
     *
     * @return The CIMDataType of this CIMValue.
     */
    public CIMDataType getType() {
	return dataType;
    }

    /**
     * Returns true if the Object obj equals this
     * CIMValue. 
     *
     * @param 	obj	The Object to compare.
     * @return 	True if the specified object equals
     *		this CIMValue. Otherwise, false.
     */
    public boolean equals(Object obj) {
	if (obj == null) { 
	    return false;
	}
	if (!(obj instanceof CIMValue)) {
	    return false;
	}
	CIMValue cv = (CIMValue)obj;
	if (dataType == null) {
	    if (cv.dataType != null) {
		return false;
	    }
	} else {
	    if (!dataType.equals(cv.dataType)) {
		return false;
	    }
	}
	if (value != null) {
	    return (value.equals(cv.value));
	}
	if (valueVector != null) {
	    if (cv.valueVector == null) {
		return false;
  	    }	
	    if (valueVector.size() != cv.valueVector.size()) {
		return false;
	    }
	    for (int i = 0; i < valueVector.size(); i++) {
		Object o = valueVector.elementAt(i);
		if (!o.equals(cv.valueVector.elementAt(i))) {
		    return false;
		}
	    }
	    return true;
	} else {
	    if (cv.valueVector != null) {
		return false;
            }
	}
	return true;
    }
}
