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
 *Contributor(s): Brian Schlosser,
 *                WBEM Solutions, Inc.
 */

package javax.wbem.cim;

import java.util.Enumeration;
import java.util.Vector;

/** 
 * Creates and instantiates a Java object that represents a CIM class. 
 * A CIM class is a template for creating a CIM instance. A CIM class 
 * represents a collection of CIM instances, all of which support a 
 * common type (for example, a set of properties, methods, and associations). 
 * 
 * @author  Sun Microsystems, Inc. 
 * @since   WBEM 1.0
 */
public class CIMClass extends CIMObject {

    final static long serialVersionUID = 200;

    /**    
     * Name of parent class.
     * @serial 
     */
    private String superClass;
    
    /** 
     * Indicates whether or not this CIM class belongs to an 
     * association.
     * 
     * @serial 
     */
    private boolean isAssociationClass;
    
    /** 
     * Indicates whether or not this CIM class has a
     * key property. 
     *
     * @serial
     */
    private boolean isKeyedClass;
    
    /**  
     * List of methods for this CIM class.
     *
     * @serial 
     */
    private Vector methods;
    
    /**  
     * List of methods for this CIM class.
     *
     * @serial 
     */
    private Vector allmethods;
    

    /**    
     * Creates and instantiates a Java object representing a CIM class.
     * To declare the most basic CIM class, you need only specify
     * the class name. If you use this constructor to create the CIM
     * Class, use the <code>setName</code> method to assign a name to 
     * the CIM class.
     * <p>
     * <b>Example:</b>
     * <p>
     * <pre>
     * {
     * // Connect to the root/cimv2 namespace on the local host and 
     * // create a new class called myclass.
     * <code>CIMClient cc = new CIMClient();</code>
     * // Construct a new CIMClass object.
     * <code>CIMClass cimclass = new CIMClass();</code>
     * // Set the name of the CIM class to myclass.
     * <code>cimclass.setName("myclass");</code>
     * ...
     * }
     * </pre>
     */
    public CIMClass() {
        this("");
    }

    /**      
     * Creates and instantiates a Java object representing a CIM Class 
     * with the specified name.
     * <p>
     * @param name 	Name of the CIM class.
     * <p>
     * <b>Example:</b>
     * <p>
     * <pre>
     * {
     * // Construct a new CIMClass object with the name myclass.
     * <code>CIMClass cimclass = new CIMClass(myclass);</code>
     * ...
     * }
     * </pre>
     */
    public CIMClass(String name) {
        super(name);
        superClass = null;
        methods    = new Vector();
        allmethods = new Vector();
    }

    /**
     * Returns a String representation of the CIMClass.
     * This method is intended to be used only for debugging purposes,
     * and the format of the returned string may vary between
     * implementations.  The returned string may be empty but may not be
     * null.
     *
     * @return string representation of this class
     */
    public String toString() {
        return toMOF(); 
    }

    /**
     * Returns a MOF representation of the CIM class. 
     *
     * @return Managed Object Format (MOF) representation of 
     *          this class.
     */
    public String toMOF() {
        MOFFormatter mf = new MOFFormatter();
        return mf.toString(this);
    }

    /**  
     * Gets the parent of this CIM class.
     *
     * @return The parent class.
     */
    public String getSuperClass() {
        return superClass;
    }

    /** 
     * Sets the parent of this CIM class to the name of the 
     * super class contained in the specified string.
     *
     * @param superClass The parent class.
     */
    public void setSuperClass(String superClass) {
        this.superClass = superClass;
    }

    /**  
     * Convenience method to identify if this class is keyed.
     * Only keyed classes can have instances. 
     * Returns true if this CIM class has a key property.
     * Otherwise, returns false.
     *
     * @return True if this CIM class has a key property.
     *          otherwise returns false.
     */
    public boolean isKeyed() {
        return isKeyedClass;
    }

    /**   
     * Takes a boolean that indicates whether or not this CIM
     * class has a key property. 
     * 
     * @param isKeyedValue True indicates this CIM class has a key
     *                      property. False indicates this CIM class 
     *                      has no key properties.	
     */
    public void setIsKeyed(boolean isKeyedValue) {
        isKeyedClass = isKeyedValue;
    }

    /**  
     * Returns the specified CIM method in this CIM class.
     *  
     * @param name The string name of the method to get. The name 
     *              can also be in the form "originClass.methodName"
     * @return null if the method does not exist, 
     *          otherwise returns the CIM method.
     */
    public CIMMethod getMethod(String name) {
        if ((allmethods == null) || (name == null)) {
            return null;
        }
        int i = name.indexOf('.');
        if (i != -1) {
            return getMethod(name.substring(i + 1, name.length()),
                    name.substring(0, i)); 
        }

        int tempIndex = allmethods.indexOf(new CIMMethod(name));
        if (tempIndex == -1) {
            return null; 
        }

        CIMMethod me = (CIMMethod)allmethods.elementAt(tempIndex);
        String override = me.getOverridingMethod();
        if (override != null) {
            me = getMethod(override);
        }
        return me;
    }

    /**  
     * Returns the CIM method specified by its name and optionally, its
     * origin class. The origin class is the class in which the 
     * method is defined.
     *
     * @param name        The string name of the method to get.
     * @param originClass (Optional) The class in which the method 
     *                     was defined.
     * @return CIMMethod  Null if the method does not exist, 
     *                     otherwise returns the CIM method.
     */
    public CIMMethod getMethod(String name, String originClass) {
        if ((allmethods == null) || (name == null)) {
            return null;
        }
        if ((originClass == null) || (originClass.length() == 0)) {
            return getMethod(name);
        }
        for (Enumeration methods = allmethods.elements(); 
            methods.hasMoreElements(); ) {
                
            CIMMethod method = (CIMMethod) methods.nextElement();
            if(method.getName().equalsIgnoreCase(name) &&
               method.getOriginClass().getName().equalsIgnoreCase(originClass)) {
                if (method.getOverridingMethod() != null) {
                    return getMethod(method.getOverridingMethod());
                } else {
                    return method;
                }
            }
        }
        return null;
    }
    
    /** 
     * Identifies whether or not this CIM class is an association.
     * An association is a relationship between two (or more) classes or 
     * instances of two classes. The properties of an association class 
     * include pointers, or references, to the two (or more) instances. 
     * All CIM classes can be included in one or more associations. 
     * <p>
     * This method returns true if this CIM class is an association.
     * Otherwise, false. 
     *
     *
     * @return True if this CIM class belongs to
     *          an association; otherwise, false.
     */
    public boolean isAssociation() {
        return isAssociationClass;
    }

    /**   
     * Sets the <code>isAssociation</code> field to true to indicate that this
     * CIM class is an association. 
     * <p>
     * An association is a relationship between two classes or between 
     * instances of two classes. The properties of an association class 
     * include pointers, or references, to the two classes or instances. 
     * <p>
     * Class association is one of the most powerful CIM features. It provides 
     * a way to organize a collection of management objects into meaningful 
     * relationships. For example, a <code>Solaris_ComputerSystem</code> object 
     * might contain a <code>Solaris_Disk</code>, Processor A, and Processor B.
     * The <code>Solaris_ComputerSystem</code> has an association with each of 
     * the objects it contains. Because this particular association is a 
     * containment association, it is represented by a class called 
     * <code>CIM_contains</code>. The <code>CIM_contains</code> class contains 
     * references to each of the objects that belong to the association. In  
     * this case, <code>CIM_contains</code> has a reference to 
     * <code>Solaris_Disk</code> and a reference to 
     * <code>Solaris_ComputerSystem</code>. 
     * <p>
     * All CIM classes can be included in one or more associations. 
     *
     * @param association True indicates this CIM
     *                     class belongs to an association.
     *                     False indicates that this CIM
     *                     class does not belong to an
     *                     association.
     */
    public void setIsAssociation(boolean association) {
        isAssociationClass = association;
    }

    /** 
     * Gets the CIM properties in this CIM class, including 
     * overriden properties.
     *
     * @return Vector CIMProperty objects in this CIM class.
     */
    public Vector getAllProperties() {
        return allproperties;
    }
	
    /** 
     * Gets the CIM methods in this CIM class, including 
     * overridden ones.
     *
     * @return Vector CIMMethod objects in this CIM class.
     */
    public Vector getAllMethods() {
        return allmethods;
    }
    /** 
     * Gets the CIM methods in this CIM class.
     *
     * @return Vector CIMMethod objects in this CIM class.
     */
    public Vector getMethods() {
        return methods;
    }
	
    /** 
     * Adds the specified property to the CIM properties
     * in this CIM class.
     * 
     * @param property The property to add to this CIM class.
     */
    public void addProperty(CIMProperty property) {
        super.addProperty(property);
    }

    /**
     * Returns the number of properties in this class.
     *
     * @return the number of properties.
     * @deprecated use getProperties().size() instead
     */
    public int numberOfProperties() {
        return properties.size();
    }

    /**
     * Returns the number of qualifiers declared in this CIM Class.
     *
     * @return the number of qualifiers in this CIM Class.
     * @deprecated use getQualifiers().size() instead
     */
    public int numberOfQualifiers() {
        return qualifiers.size();
    }

    /** 
     * Adds the specified CIM method to this CIM class.
     * 
     * @param method The CIMMethod object to add to this CIM class.
     */ 
    public void addMethod(CIMMethod method) {
        if (method.getOverridingMethod() == null) {
            methods.add(method);
        }
        allmethods.add(method);
    }

    /**
     * Replaces the existing methods in this class with the specified methods.
     *
     * @param methods Vector of CIMMethod elements.
     */
    public void setMethods(Vector methods) {
        this.methods = new Vector();
        this.allmethods = new Vector();

        if (methods != null) {
            int size = methods.size();
            for (int i = 0; i < size; i++) {
                addMethod((CIMMethod)methods.elementAt(i));
            }
	}
    }

    /**
     * Returns a new CIM instance initialized with the default
     * CIM properties, qualifiers, and name of this CIM class.
     *
     * @return CIMInstance	CIM instance of this CIM class.
     */
    public CIMInstance newInstance() {
        CIMInstance ci = new CIMInstance();
        ci.setClassName(name);
        ci.setObjectPath(getObjectPath());

        if (allproperties != null) {
            ci.setProperties(CloneUtil.cloneProperties(allproperties, true));
        }

        return ci;
    }

    /**
     * Returns only the local elements for this class and filters out the rest.
     * This works like a clone method. Note that this depends on the 
     * classOrigin property of its elements, so it will not work if you 
     * invoke filterProperties and set includeClassOrigin to false.
     *
     * @return CIMClass populated with the local elements.
     * @see #filterProperties(String[], boolean, boolean)
     */
    public CIMClass localElements() {
        CIMClass cc = new CIMClass();

        cc.name       = name;
        cc.superClass = superClass;
        cc.isAssociationClass = isAssociationClass;
        cc.isKeyedClass       = isKeyedClass;
        cc.setObjectPath(getObjectPath());

        if (properties != null) {
            Vector ov = new Vector();
            for (Enumeration eProps = properties.elements(); 
                eProps.hasMoreElements();) {
                CIMProperty pe = (CIMProperty) eProps.nextElement();
                if (pe.getOriginClass().equalsIgnoreCase(name)) {
                    ov.add(pe);
                }
            }
            cc.setProperties(ov);
        }

        if (methods != null) {
            Vector ov = new Vector();
            for (Enumeration eMethods = methods.elements(); 
                eMethods.hasMoreElements();) {
                CIMMethod me = (CIMMethod) eMethods.nextElement();
                if (me.getOriginClass().getName().equalsIgnoreCase(name)) {
                    ov.add(me);
                }
            }
            cc.setMethods(ov);
        }

        cc.setQualifiers(qualifiers);

        return cc;
    }

    /**
     * This method returns a new CIMClass with properties filtered according to
     * the input parameters. Inclusion of class origin and qualifiers can also
     * be controlled.
     *
     * @param propertyList If the PropertyList input parameter is not NULL, the 
     * members of the array define one or more Property names.  The returned 
     * CIMClass does not include elements for any Properties missing from this 
     * list. If the PropertyList input parameter is an empty array this 
     * signifies that no Properties are included in each returned class. If the 
     * PropertyList input parameter is NULL this specifies that all Properties 
     * are included in each returned class.  
     * If the PropertyList contains duplicate elements or invalid property 
     * names, they are ignored.
     *
     * @param  includeQualifier   qualifiers are only included if true.
     * @param  includeClassOrigin classOrigins are only included if true.
     *
     * @return CIMClass matching the input filter.
     * @see CIMClass#localElements()
     */
    public CIMClass filterProperties(String  propertyList[], 
                                     boolean includeQualifier,
                                     boolean includeClassOrigin) {

        Vector propList = getFilteredProperties(propertyList, 
                                                includeQualifier, 
                                                includeClassOrigin);

        Vector methodList = null;
        if (allmethods != null && propertyList == null) {
            methodList = CloneUtil.cloneMethods(allmethods, includeQualifier, 
                    includeClassOrigin);
        } 

        CIMClass cc = new CIMClass();
        
        cc.name               = name;
        cc.superClass         = superClass;
        cc.isAssociationClass = isAssociationClass;
        cc.isKeyedClass       = isKeyedClass;

        cc.setProperties(propList);
        cc.setMethods(methodList);
        if (includeQualifier) {
            cc.setQualifiers(qualifiers);
        }

        return cc;
    }

}
