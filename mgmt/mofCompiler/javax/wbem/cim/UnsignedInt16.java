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

import java.io.Serializable;

/**
 * Creates and instantiates an unsigned 16-bit integer object
 * 
 * @author Sun Microsystems, Inc.
 * @since WBEM 1.0
 */
public class UnsignedInt16 extends UnsignedInt implements Serializable {

    final static long serialVersionUID = 200;

    /**
     * the maximum value this int can have
     */
    public final static int MAX_VALUE = 0xffff;

    /**
     * the minimum value this int can have
     */
    public final static int MIN_VALUE = 0;

    /**
     * Constructor creates an unsigned 16-bit integer object for the specified
     * int value. Only the bottom 16 bits are considered.
     * 
     * @param a the integer to be represented as an unsigned 16-bit integer
     *                object
     */
    public UnsignedInt16(int a) {
        if ((a < MIN_VALUE) || (a > MAX_VALUE)) {
            throw new NumberFormatException();
        }
        value = new Integer(a);
    }

    /**
     * Constructor creates an unsigned 16-bit integer object for the specified
     * string. Only the bottom 16 bits are considered.
     * 
     * @param a the string to be represented as an unsigned 16-bit integer
     * @throws NumberFormatException if the number is out of range
     */
    public UnsignedInt16(String a) throws NumberFormatException {
        Integer temp = new Integer(a);
        int intValue = temp.intValue();
        if ((intValue < MIN_VALUE) || (intValue > MAX_VALUE)) {
            throw new NumberFormatException();
        }
        value = temp;
    }

    /**
     * Compares this unsigned 16-bit integer object with the specified object
     * for equality
     * 
     * @param o the object to compare
     * @return true if the specified object is an unsigned 16-bit integer .
     *         Otherwise, false.
     */
    public boolean equals(Object o) {
        if (!(o instanceof UnsignedInt16)) {
            return false;
        }
        return (((UnsignedInt16) o).value.equals(this.value));
    }
}
