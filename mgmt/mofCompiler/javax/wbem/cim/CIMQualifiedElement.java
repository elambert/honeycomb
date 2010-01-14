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
 *are Copyright (c) 2002 Sun Microsystems, Inc.
 *
 *All Rights Reserved.
 *
 *Contributor(s): Brian Schlosser
 *                WBEM Solutions, Inc.
 */
package javax.wbem.cim;

import java.util.Enumeration;
import java.util.Vector;

abstract class CIMQualifiedElement extends CIMElement {

    /**
     * List of qualifiers for this method
     * 
     * @serial
     */
    protected Vector qualifiers;

    /**
     * @param elementName
     */
    public CIMQualifiedElement(String elementName) {
        super(elementName);
        qualifiers = new Vector();
    }

    /**
     * Sets the list of qualifiers for this element to the specified list of
     * qualifiers
     * 
     * @param qualifiers list of qualifiers to be assigned to the element
     */
    public void setQualifiers(Vector qualifiers) {
        this.qualifiers = qualifiers;
    }

    /**
     * Returns the list of qualifiers for this method
     * 
     * @return Vector list of qualifiers for this method
     */
    public Vector getQualifiers() {
        return qualifiers;
    }

    /**
     * Adds the specified CIM qualifier to this element.
     * 
     * @param cq The qualifier to add
     * @exception CIMException if the qualifier already exists.
     */
    public void addQualifier(CIMQualifier cq) throws CIMException {
        if (this.getQualifier(cq.getName()) != null) {
            throw new CIMException(
                "Element "
                    + this.getName()
                    + " already has qualifier "
                    + cq.getName());
        }

        qualifiers.addElement(cq);
    }

    /**
     * Removes the specified CIM qualifier from the element.
     * 
     * @param name The name of the qualifier to remove.
     * @return True if the qualifier exists, else false.
     */
    public boolean removeQualifier(String name) {
        return qualifiers.removeElement(new CIMQualifier(name));
    }

    /**
     * Sets the value of a qualifier for this element
     * 
     * @param qualifier The qualifier to set
     * @return the updated qualifier
     * @exception CIMException if the qualifier does not exist
     */
    public CIMQualifier setQualifier(CIMQualifier qualifier)
            throws CIMException {
        return setQualifier(qualifier.getName(), qualifier.getValue());
    }

    /**
     * Sets the value of a qualifier for this element
     * 
     * @param name The name of the qualifier to set
     * @param value The value to give the qualifier
     * @return the updated qualifier
     * @exception CIMException if the qualifier does not exist
     */
    public CIMQualifier setQualifier(String name, CIMValue value)
        throws CIMException {
        CIMQualifier qe = getQualifier(name);
        if (qe == null) {
            throw new CIMException(
                "Element "
                    + this.getName()
                    + " does not have qualifier "
                    + name);
        }
        qe.setValue(value);
        return qe;
    }

    /**
     * Gets a qualifier by name.
     * 
     * @param name The name of the qualifier to get.
     * @return Null if the qualifier does not exist, otherwise returns the
     *         reference to the qualifier.
     */
    public CIMQualifier getQualifier(String name) {
        if (name == null) {
            return null;
        }

        for (Enumeration enumQualifiers = qualifiers.elements();
            enumQualifiers.hasMoreElements();) {
            CIMQualifier qualifier = (CIMQualifier) enumQualifiers.nextElement();
            if (qualifier.getName().equalsIgnoreCase(name)) {
                return qualifier;
            }
        }
        return null;
    }

    /**
     * Checks whether the specified qualifier is one of the qualifiers in this
     * CIM element.
     * 
     * @param name the name of the qualifier
     * @return true if the qualifier exists in this CIM element, otherwise
     *         false.
     */
    public boolean hasQualifier(String name) {
        return getQualifier(name) != null;
    }
}
