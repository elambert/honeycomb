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
 *The Initial Developer of the Original Code is:
 * Brian Schlosser
 *
 *Portions created by: Brian Schlosser
 *are Copyright (c) 2003 Brian Schlosser
 *
 *All Rights Reserved.
 *
 *Contributor(s): Brian Schlosser
		  WBEM Solutions, Inc.
 */
package javax.wbem.cim;

import java.util.Enumeration;
import java.util.Vector;

/**
 * Some utility methods to help in cloneing objects
 * 
 * @author Brian Schlosser
 */
final class CloneUtil {

    /**
     * Static helper class, no use making instances.
     */
    private CloneUtil() {
    }

    protected static Vector cloneQualifiers(Vector qualifiers) {
        Vector clone = new Vector(qualifiers.size());
        for (Enumeration quals = qualifiers.elements();
            quals.hasMoreElements();) {
            CIMQualifier qualifier = (CIMQualifier) quals.nextElement();
            clone.add(qualifier.clone());
        }
        return clone;
    }
    
    protected static Vector cloneProperties(Vector properties) {
        return cloneProperties(properties, true, true, false);
    }

    protected static Vector cloneProperties(Vector properties, boolean reset) {
        return cloneProperties(properties, false, false, reset);
    }
    
    protected static Vector cloneProperties(Vector properties, 
        boolean includeQualifiers, boolean includeClassOrigin, boolean reset) {
        Vector clone = new Vector(properties.size());
        for (Enumeration eProps = properties.elements();
            eProps.hasMoreElements();) {
                
            CIMProperty property = (CIMProperty) eProps.nextElement();
            clone.add(property.clone(includeQualifiers, includeClassOrigin, reset));
        }
        return clone;
    }
    
    protected static Vector cloneParameter(Vector parameters, 
                                           boolean includeQualifiers) {
        Vector clone = new Vector(parameters.size());
        for (Enumeration eParams = parameters.elements();
            eParams.hasMoreElements();) {
            CIMParameter parameter = (CIMParameter) eParams.nextElement();
            clone.add(parameter.clone(includeQualifiers));
        }
        return clone;
    }
    
    protected static Vector cloneMethods(Vector methods, 
                                         boolean includeQualifiers,
                                         boolean includeClassOrigin) {
        Vector clone = new Vector(methods.size());
        for (Enumeration eMethods = methods.elements();
            eMethods.hasMoreElements();) {
            CIMMethod method = (CIMMethod) eMethods.nextElement();
            clone.add(method.clone(includeQualifiers, includeClassOrigin));
        }
        return clone;
    }
}
