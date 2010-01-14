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

import java.lang.reflect.Field;
import java.util.Vector;
import java.io.Serializable;

/**
 * Encapsulates the CIM data types (as defined in the CIM Specification). 
 * This class includes methods that perform operations on CIM data types,
 * such as, returning the CIM data type of a CIM object, returning an array of 
 * CIM data types for an array of CIM objects, and creating an object or 
 * array of objects of the specified CIM data type.
 *
 * All CIM properties must have a valid CIM data type.
 *
 * @author Sun Microsystems, Inc.
 * @since  WBEM 1.0
 */
public class CIMDataType implements Serializable {

    final static long serialVersionUID = 200;


    private int type;
    private String refClassName;
    private int size;

    /** 
     * This size is for non-array types
     */
    public final static int SIZE_SINGLE		 = -1;

    /** 
     * This size is for unlimited size array types. This is the default value
     * for array types.
     */
    public final static int SIZE_UNLIMITED	= -2;

    /**
     * Invalid type
     */
    public final static int	INVALID	    	= -1;  

    /**
     * Unsigned 8-bit integer
     * @see UnsignedInt8
     */
    public final static int	UINT8	    	= 0;  

    /**
     * Signed 8-bit integer
     * Byte
     */
    public final static int	SINT8	    	= 1;

    /**
     * Unsigned 16-bit integer
     * @see UnsignedInt16
     */
    public final static int	UINT16	    	= 2;

    /**
     * Signed 16-bit integer
     * Short
     */
    public final static int	SINT16	    	= 3;

    /**
     * Unsigned 32-bit integer
     * @see UnsignedInt32
     */
    public final static int	UINT32	    	= 4;

    /**
     * Signed 32-bit integer
     * Integer
     */
    public final static int	SINT32	    	= 5;

    /**
     * Unsigned 64-bit integer
     * @see UnsignedInt64
     */
    public final static int	UINT64	    	= 6;


    /**
     * Signed 64-bit integer
     * Long
     */
    public final static int	SINT64	    	= 7;

    /**
     * UCS-2 string
     * String
     */
    public final static int	STRING	    	= 8;

    /**
     * Boolean
     */
    public final static int	BOOLEAN	    	= 9;

    /**
     * IEEE 4-byte floating-point
     * Float
     */
    public final static int	REAL32	    	= 10;


    /**
     * IEEE 8-byte floating-point
     * Double
     */
    public final static int	REAL64	    	= 11;

    /**
     * A string containing the date-time
     * @see CIMDateTime
     */
    public final static int	DATETIME    	= 12;

    /**
     * 16-bit UCS-2 character
     * Character
     */
    public final static int	CHAR16		= 13;

    /**
     * Unsigned 8-bit integer array
     * @see UnsignedInt8
     */
    public final static int	UINT8_ARRAY	= 14;  

    /**
     * Signed 8-bit integer array
     * Byte
     */
    public final static int	SINT8_ARRAY	= 15;

    /**
     * Unsigned 16-bit integer array
     * @see UnsignedInt16
     */
    public final static int	UINT16_ARRAY	= 16;

    /**
     * Signed 16-bit integer array
     * Short
     */
    public final static int	SINT16_ARRAY 	= 17;

    /**
     * Unsigned 32-bit integer array
     * @see UnsignedInt32
     */
    public final static int	UINT32_ARRAY    = 18;

    /**
     * Signed 32-bit integer array
     * Integer
     */
    public final static int	SINT32_ARRAY    = 19;

    /**
     * Unsigned 64-bit integer array
     * @see UnsignedInt64
     */
    public final static int	UINT64_ARRAY    = 20;


    /**
     * Signed 64-bit integer array
     * Long
     */
    public final static int	SINT64_ARRAY    = 21;


    /**
     * UCS-2 string array
     * String
     */
    public final static int	STRING_ARRAY    = 22;

    /**
     * Boolean array
     */
    public final static int	BOOLEAN_ARRAY   = 23; 

    /**
     * IEEE 4-byte floating-point
     * Float
     */
    public final static int	REAL32_ARRAY    = 24;

    /**
     * IEEE 8-byte floating-point
     * Double
     */
    public final static int	REAL64_ARRAY    = 25;

    /**
     * A string containing the date-time
     * @see CIMDateTime
     */
    public final static int	DATETIME_ARRAY  = 26;

    /**
     * 16-bit UCS-2 character
     * Character
     */
    public final static int	CHAR16_ARRAY    = 27;

    /**
     * Reference type
     * @see CIMObjectPath
     */
    public final static int	REFERENCE    = 28;

    /**
     * CIMInstance type
     * @see CIMInstance
     */
    public final static int	OBJECT    = 29;

    /**
     * Null type
     */
    public final static int	NULL    = 30;

    /**
     * CIMClass type
     * @see CIMClass
     */
    public final static int	CLASS    = 31;

    /**
     * Reference array type
     * @see CIMObjectPath
     */
    public final static int     REFERENCE_ARRAY    = 42;

    
    /**
     * New type XML
     */
    public final static int     XML    = 43; 

    /**
     * Returns the CIM data type for the specified object
     * 
     * @param o the object for which the data type is to be checked. It can
     *                either be a Java representation of a primitive type, like
     *                Integer, String, UnsignedInt16 or a Vector of these.
     * @return int the integer representing the CIM data type of the specified
     *         object. INVALID if the <code>o</code> is invalid and NULL if
     *         <code>o</code> is null.
     */ 
    public static final int findType(Object o) {
	if (o == null) {
	    return CIMDataType.NULL;
	}
	if (o instanceof Vector) {
	    return findType((Vector)o);
	}
	if (o instanceof UnsignedInt8) {
	    return CIMDataType.UINT8;
	}
	if (o instanceof Byte) {
	    return CIMDataType.SINT8;
	}
	if (o instanceof UnsignedInt16) {
	    return CIMDataType.UINT16;
	}
	if (o instanceof Short) {
	    return CIMDataType.SINT16;
	}
	if (o instanceof UnsignedInt32) {
	    return CIMDataType.UINT32;
	}
	if (o instanceof Integer) {
	    return CIMDataType.SINT32;
	}
	if (o instanceof UnsignedInt64) {
	    return CIMDataType.UINT64;
	}
	if (o instanceof Long) {
	    return CIMDataType.SINT64;
	}
	if (o instanceof String) {
	    return CIMDataType.STRING;
	}
	if (o instanceof Boolean) {
	    return CIMDataType.BOOLEAN;
	}
	if (o instanceof Float) {
	    return CIMDataType.REAL32;
	}
	if (o instanceof Double) {
	    return CIMDataType.REAL64;
	}
	if (o instanceof Character) {
	    return CIMDataType.CHAR16;
	}
	if (o instanceof CIMDateTime) {
	    return CIMDataType.DATETIME;
	}
	if (o instanceof CIMObjectPath) {
	    return CIMDataType.REFERENCE;
	}
	if (o instanceof CIMInstance) {
	    return CIMDataType.OBJECT;
	}
	if (o instanceof CIMClass) {
	    return CIMDataType.CLASS;
	}
	return CIMDataType.INVALID;
    }

    /**
     * Returns a CIM data type object with the specified type.
     * 
     * @param type the CIM data type
     * @return a CIMDataType object representing the type
     * @throws CIMException if the type is not a valid CIM type
     */
    public static CIMDataType getDataType(String type) throws CIMException {
  	if (type == null) {
  	    throw new IllegalArgumentException();
  	}
	if (type.equals("boolean")) {
	    return new CIMDataType(BOOLEAN);
	} else if (type.equals("char16")) {
	    return new CIMDataType(CHAR16);
	} else if (type.equals("datetime")) {
	    return new CIMDataType(DATETIME);
	} else if (type.equals("real32")) {
	    return new CIMDataType(REAL32);
	} else if (type.equals("real64")) {
	    return new CIMDataType(REAL64);
	} else if (type.equals("sint16")) {
	    return new CIMDataType(SINT16);
	} else if (type.equals("sint32")) {
	    return new CIMDataType(SINT32);
	} else if (type.equals("sint64")) {
	    return new CIMDataType(SINT64);
	} else if (type.equals("sint8")) {
	    return new CIMDataType(SINT8);
	} else if (type.equals("string")) {
	    return new CIMDataType(STRING);
	} else if (type.equals("uint16")) {
	    return new CIMDataType(UINT16);
	} else if (type.equals("uint32")) {
	    return new CIMDataType(UINT32);
	} else if (type.equals("uint64")) {
	    return new CIMDataType(UINT64);
	} else if (type.equals("uint8")) {
	    return new CIMDataType(UINT8);
	} else if (type.equals("xml")) {
	    return new CIMDataType(XML);
	} else {
	    return new CIMDataType(STRING);
	}
    }

    /**
     * Returns a CIM data type object with the specified predefined type.
     * 
     * @param type the CIM data type
     * @param isArray true if the type should represent an array
     * @return a CIMDataType object representing the type
     * @throws CIMException if the type is not a valid CIM type
     */
    public static CIMDataType getDataType(String type, boolean isArray)
	throws CIMException {
  	if (type == null) {
  	    throw new IllegalArgumentException();
  	}
	if (isArray) {
	    if (type.equals("boolean")) {
		return new CIMDataType(BOOLEAN_ARRAY);
	    } else if (type.equals("char16")) {
		return new CIMDataType(CHAR16_ARRAY);
	    } else if (type.equals("datetime")) {
		return new CIMDataType(DATETIME_ARRAY);
	    } else if (type.equals("real32")) {
		return new CIMDataType(REAL32_ARRAY);
	    } else if (type.equals("real64")) {
		return new CIMDataType(REAL64_ARRAY);
	    } else if (type.equals("sint16")) {
		return new CIMDataType(SINT16_ARRAY);
	    } else if (type.equals("sint32")) {
		return new CIMDataType(SINT32_ARRAY);
	    } else if (type.equals("sint64")) {
		return new CIMDataType(SINT64_ARRAY);
	    } else if (type.equals("sint8")) {
		return new CIMDataType(SINT8_ARRAY);
	    } else if (type.equals("string")) {
		return new CIMDataType(STRING_ARRAY);
	    } else if (type.equals("uint16")) {
		return new CIMDataType(UINT16_ARRAY);
	    } else if (type.equals("uint32")) {
		return new CIMDataType(UINT32_ARRAY);
	    } else if (type.equals("uint64")) {
		return new CIMDataType(UINT64_ARRAY);
	    } else if (type.equals("uint8")) {
		return new CIMDataType(UINT8_ARRAY);
            } else if (type.equals("REF")) {
                return new CIMDataType(REFERENCE_ARRAY);
	    } else {
		return new CIMDataType(STRING_ARRAY);
	    }
	} else {
	    return getDataType(type);
	}
    }

    /**
     * Returns the CIM data type for the specified list of objects.
     * 
     * @param v the list of objects for which the data type is to be checked
     * @return the integer for the CIM array data type of the specified
     *         list of objects. Returns INVALID if the list is empty. Returns
     *         NULL if an object is null.
     */ 
    public static final int findType(Vector v) {

	if (v == null) {
	    return CIMDataType.NULL;
	}

	Object o = null;
	int k = v.size();
	for (int i = 0; i < k; i++) {
	    o = v.elementAt(i);
	    if (o != null) {
		break;
            }
	}
	if (o == null) {
	    return CIMDataType.NULL;
	}

	int type = findType(o);
	return findArrayType(type);
    }
    
    /**
     * Takes a CIM data type and returns the CIM array type. Returns INVALID if
     * it is not an array type.
     * 
     * @param simpleType the integer for the CIM data type of this object
     * @return the integer for CIM array type. 
     */
    public static final int findArrayType(int simpleType) {
	if ((simpleType < UINT8) || (simpleType > CHAR16) && (simpleType != REFERENCE)) {
	    return INVALID;
        }
	return simpleType + 14;
    }


    /**
     * Takes a CIM array type and returns the CIM data type. Returns INVALID if
     * the type passed in is not an array type.
     * 
     * @param arrayType the integer for the CIM array type of this object
     * @return the integer for the CIM data type of this object. Otherwise,
     *         INVALID if there is no CIM data type.
     */
    public static final int findSimpleType(int arrayType) {
    	if ((arrayType < CIMDataType.UINT8_ARRAY) || 
            (arrayType > CIMDataType.CHAR16_ARRAY) &&
            (arrayType != CIMDataType.REFERENCE_ARRAY) ) {
	    return INVALID;
        } else {
	    return arrayType - 14;
        }
    }

    protected static CIMDataType predefined[] = new CIMDataType[NULL];

    /**
     * Constructor creates a new CIM data type object with the specified type
     * (does not take null types). For array types, the size is
     * initialized to SIZE_UNLIMITED by default, and for single valued types,
     * the size is initialized to SIZE_SINGLE.
     * 
     * @param type the CIM data type
     */
    public CIMDataType(int type) {
    	if ((type < CIMDataType.INVALID || type > CIMDataType.CHAR16_ARRAY) &&
	type != CIMDataType.OBJECT && type != CIMDataType.CLASS && 
        type != CIMDataType.REFERENCE_ARRAY && type != CIMDataType.XML) {
	    throw new IllegalArgumentException();
	}

	if ((type > CHAR16) && (type < REFERENCE)) {
	    size = SIZE_UNLIMITED;
        } else {
	    size = SIZE_SINGLE;
        }
	this.type = type;
	refClassName = null;
    }

    /**
     * Constructor creates a new CIM array data type with the specified size.
     * 
     * @param type the CIM array data type
     * @param size the CIM array size
     */
    public CIMDataType(int type, int size) {
    	if (type < CIMDataType.UINT8_ARRAY || type > CIMDataType.CHAR16_ARRAY && 
            type != CIMDataType.REFERENCE_ARRAY) {
	    throw new IllegalArgumentException(type + "");
	}

	if ((size != SIZE_UNLIMITED) && (size <= 0)) { 
	    throw new IllegalArgumentException(size + "");
        }

	this.type = type;
	this.size = size;
	refClassName = null;
    }

    /**
     * Returns a CIM data type object with the specified predefined type.
     * 
     * @param type the CIM data type
     * @return a CIMDataType object representing the type
     */
    public static CIMDataType getPredefinedType(int type) {
    	if ((type < CIMDataType.UINT8 || type > CIMDataType.CHAR16_ARRAY) &&
	type != CIMDataType.OBJECT && type != CIMDataType.CLASS && 
        type != CIMDataType.REFERENCE_ARRAY) {
	    throw new IllegalArgumentException();
	}
	if (predefined[type] == null) {
	    predefined[type] = new CIMDataType(type);
	}
	return predefined[type];

    }

    /**
     * Creates a new CIM REFERENCE data type object with the specified class
     * reference.
     * 
     * @param refClassName the CIM class reference name.
     */
    public CIMDataType(String refClassName) {
	if (refClassName == null) {
	    throw new NullPointerException();
	}
	type = REFERENCE;
	this.refClassName = refClassName;
	size = SIZE_SINGLE;
    }
    
    public CIMDataType(String refClassName, int size) {
        this(refClassName);
        type =REFERENCE_ARRAY;
        this.size = size;
    }

    /**
     * Checks if the type is an array type.
     * 
     * @return true if the type is an array type, false otherwise.
     */
    public boolean isArrayType() {
    	if (type < CIMDataType.UINT8_ARRAY || type > CIMDataType.CHAR16_ARRAY && 
            type != CIMDataType.REFERENCE_ARRAY) {
	    return false;
        } else {
	    return true;
        }
    }

    /**
     * Checks if the type is a reference type.
     * 
     * @return true if the type is a reference type or reference array, false otherwise.
     */
    public boolean isReferenceType() {
	return (type == CIMDataType.REFERENCE) || (type == CIMDataType.REFERENCE_ARRAY) ;
    }

    /**
     * Returns the data type
     * 
     * @return the type
     */
    public int getType() {
	return type;
    }

    /**
     * Lets you get the type from an XML CIMType.
     * 
     * @param cimtype the string name of the type
     * @return the int type
     */
    int getType(String cimtype) {
	Field[] fields = getClass().getFields();
	try {
	    for (int i = 0; i < fields.length; i++) {
		if (cimtype.equalsIgnoreCase(fields[i].getName())) {
		    return fields[i].getInt(fields[i]);
		}
	    }
	} catch (IllegalAccessException e) {

	}
	return -1;
    }

    /**
     * Returns the size
     * 
     * @return the size
     */
    public int getSize() {
	return size;
    }

    /**
     * Returns the classname for the reference type.
     * 
     * @return the reference classname
     */
    public String getRefClassName() {
	return refClassName;
    }

    /**
     * Checks that the specified object is a CIM data type
     * 
     * @param obj the object to check
     * @return true if the specified object is a CIM data type.
     *         Otherwise, false.
     */
    public boolean equals(Object obj) {
	if (obj == null) {
	   return false;
	}
	if (!(obj instanceof CIMDataType)) {
	    return false;
	}
	CIMDataType ct = (CIMDataType)obj;
	if ((refClassName == null) && (ct.refClassName != null)) {
	    return false;
	}
	if ((refClassName != null) && 
	    !refClassName.equals(ct.refClassName)) {
	    return false;
	}
	return ((type == ct.type) && (size == ct.size));
    }
  
    /**
     * Returns a String representation of the CIMDataType. This method is
     * intended to be used only for debugging purposes, and the format of the
     * returned string may vary between implementations. The returned string
     * may be empty but may not be null.
     * 
     * @return string representation of this data type
     */
    public String toString() {
	return toMOF();
    }

    /**
     * Returns a MOF representation of the CIMDataType
     * 
     * @return a string representation of this data type in Managed Object
     *         Format (MOF)
     */
    public String toMOF() {
	MOFFormatter mf = new MOFFormatter();
	return mf.toString(this);
    }
}
