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
 *                Brian Schlosser
 */

package javax.wbem.cim;

import java.io.Serializable;

/**
 * Base class for managed system elements.
 * 
 * @since WBEM 1.0
 */
public class CIMElement implements Serializable {

    final static long serialVersionUID = 200;
    protected String name;

    /**
     * Instantiates a new CIM element with no name.
     */
    public CIMElement() {
        name = "";
    }

    /**
     * Takes an existing CIM element and uses its name to instantiate a new CIM
     * element instance.
     * 
     * @param element an existing CIM element.
     * @deprecated
     */
    public CIMElement(CIMElement element) {
        name = element.name;
    }

    /**
     * Creates a new CIM element with the given name.
     * 
     * @param elementName The string for the name of the element.
     */
    public CIMElement(String elementName) {
        name = elementName;
    }

    /**
     * Returns a string representing the name of a CIM element instance.
     * 
     * @return the name of this CIM element.
     */
    public String getName() {
        return name;
    }

    public static String nameToJava(String input) {
        return(input.replaceAll("_", ""));
    }

    public String getJavaName() {
        return(nameToJava(name));
    }

    /**
     * Sets the name of this CIM element instance
     * 
     * @param name the string name to give this CIM element
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns a String representation of the CIMElement This method is
     * intended to be used only for debugging purposes, and the format of the
     * returned string may vary between implementations. The returned string
     * may be empty but may not be null.
     * 
     * @return string representation of this element
     */
    public String toString() {
        return name;
    }

    /**
     * Takes a CIM element and returns true if it is equal to this CIM element.
     * Otherwise, it returns false. Useful for comparing two CIM elements, for
     * example, to determine whether a CIM element exists in a vector.
     * 
     * @param obj The object to be determined a CIM element.
     * @return True indicates the CIM element equals this CIM element. False
     *         indicates the CIM element does not equal this CIM element.
     */
    public boolean equals(Object obj) {
        if (obj instanceof CIMElement) {
            return name.equalsIgnoreCase(((CIMElement) obj).name);
        } else {
            return false;
        }
    }

    /**
     * Takes a CIM element and returns a CIM element instance with the name
     * assigned. Use this method to to overwrite the existing object without
     * creating a new one.
     * 
     * @param element The CIM element used to assign properties (name and
     *                identifier) this CIM element.
     * @return this CIM element with its new values.
     * @deprecated use setName(String) instead.
     */
    public CIMElement assign(CIMElement element) {
        this.name = element.name;
        return this;
    }

    /**
     * Compares the element identifier of this CIM element with the element of
     * the input identifier. Useful for sorting CIM elements by identifier.
     * Returns true if this CIM element identifier is less than the input
     * element identifier. Otherwise, returns false.
     * 
     * @param element The CIM element whose identifier is to be checked.
     * @return True if this CIM element identifier is less than zero.
     *         Otherwise, returns false.
     */
    public boolean lessThan(CIMElement element) {
        if (name.compareToIgnoreCase(element.name) < 0) {
            return true;
        } else {
            return false;
        }
    }
}
