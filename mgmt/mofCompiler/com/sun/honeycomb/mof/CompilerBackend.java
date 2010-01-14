/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



package com.sun.honeycomb.mof;

import java.util.Vector;
import javax.wbem.cim.*;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.ArrayList;

public class CompilerBackend {

    private CIMCompiler parser;

    public String       filename;
    public int          lineno;
    public String		curLevel;
    public boolean      erroneousUnit;
    public boolean      erroneousPart;
    public CIMQualifierType	curQualifierTypeEl;
    public CIMDataType	curType;
    public int			size;
    public MofcCIMValue	curValues;
    public boolean      erroneousQualifierList;
    public Vector       qualifiers;
    public CIMClass 	curClassEl;
    public String		curValueType;
    public CIMProperty 	curPropRefEl;
    public CIMMethod 	curMethodEl;
    public CIMParameter	curParameterEl;
    public CIMInstance 	curInstanceEl;
    public CIMProperty	curIPropertyEl;
    public CIMQualifier	curQualifierEl;
    public String 		curSchema;
    public String       curClassAlias;
    public Vector       properties;
    public Vector       methods;    
    public int          refsRequired;
    public String       curInstanceAlias;
    public Vector       parameters;
    public Hashtable	classAliases;
    public ArrayList    allClasses;
    
    public CompilerBackend(CIMCompiler _parser) {
        parser = _parser;

        filename = "";
        lineno = 0;
        curLevel = "ASSOCIATION";
        erroneousUnit=false;
        erroneousPart=false;
        curQualifierTypeEl = new CIMQualifierType();
        curType = null;
        size = 0;
        curValues = new MofcCIMValue();
        erroneousQualifierList=false;
        qualifiers = new Vector();
        curClassEl = new CIMClass();
        curValueType = "";
        curPropRefEl = new CIMProperty();
        curMethodEl = new CIMMethod();
        curParameterEl = new CIMParameter();
        curInstanceEl = new CIMInstance();
        curIPropertyEl = new CIMProperty();
        curQualifierEl = new CIMQualifier();
        curSchema = "";
        curClassAlias = null;
        properties = new Vector();
        methods = new Vector();
        refsRequired = 0;
        curInstanceAlias = null;
        parameters = new Vector();
        classAliases=new Hashtable();
        allClasses = new ArrayList();
    }

    public CIMClass[] getAllClasses() {
        CIMClass[] result = new CIMClass[allClasses.size()];
        allClasses.toArray(result);
        return(result);
    }

    public void switchNamespace(String newNamespace)
        throws CIMException {
        throw new CIMException(CIMException.CIM_ERR_NOT_SUPPORTED);
    }

    public void addQualifierType()
        throws CIMException {
        throw new CIMException(CIMException.CIM_ERR_NOT_SUPPORTED);
    }

    public void assignQualifierTypeScope(CIMScope applies) 
        throws CIMException {
        throw new CIMException(CIMException.CIM_ERR_NOT_SUPPORTED);
    }

    public void assignQualifierTypeFlavor(CIMFlavor newFlavor) 
        throws CIMException {
        throw new CIMException(CIMException.CIM_ERR_NOT_SUPPORTED);
    }

    public void addQualifier() {
        if (!erroneousQualifierList) {
            qualifiers.addElement(curQualifierEl);
        }
        curQualifierEl = new CIMQualifier();
    }

    public void checkQualifierList() throws CIMException {
        if (!erroneousQualifierList) {
            if (qualifiers.contains(new CIMQualifier("ASSOCIATION"))) {
                if (!((CIMQualifier)qualifiers.firstElement()).getName().equalsIgnoreCase("association")) {
                    erroneousQualifierList=true;
                    reportError("ERR_SEM", "ERR_ASSOC_QUALIFIER_MISUSE", 1, "");
                }
            }
        }
    }

    public void assignQualifierNameType(String qualifierName) {
        curQualifierEl.setName(qualifierName);
    }

    public void assignQualifierParameter(boolean arrayType) {
        if (!erroneousQualifierList) {
            if(!curValues.isEmpty()) {
                CIMValue cv;
                if(arrayType) {
                    cv = new CIMValue(curValues.vVector);
                } else {
                    cv = new CIMValue(curValues.firstElement());
                }
                curQualifierEl.setValue(cv);
            }
        }
        curValues = new MofcCIMValue();
    }

    public void assignQualifierFlavor(CIMFlavor newFlavor) {
        curQualifierEl.addFlavor(newFlavor);
    }

    void reportError(String errorType,
                     String error,
                     int exit,
                     String data) 
        throws CIMException {
        Integer lineNo = new Integer(parser.getCurrentLine());

        System.err.println(I18N.loadStringFormat("ERROR_LINE", filename, lineNo));
        System.err.println(I18N.loadString(errorType));
        System.err.println(I18N.loadStringFormat(error, data));
        System.exit(exit);
    }

    public void addClass() throws CIMException {
        curClassEl.setProperties(properties);
        curClassEl.setMethods(methods);

	    if (curClassAlias != null) {
            classAliases.put(curClassAlias, curClassEl.getName());
	    }

        allClasses.add(curClassEl);
        
        curClassEl = new CIMClass();
        curClassAlias = null;
        properties = new Vector();
        methods = new Vector();
    }
    
    public void assignClassName(String className) throws CIMException {
        if(className.indexOf("_") < 0) {
            if(curSchema.length() == 0) {
                reportError("ERR_SEM", "ERR_ILLEGAL_SCHEMA_NAME", 1, curSchema);
            } else {
                className = curSchema + "_" + className;
            }
        }
        curClassEl.setName(className);
    } 

    public void assignClassQualifiers() {
        int tempIndex;
        if (!erroneousUnit) {
            // assign Qualifier ASSOCIATION
            CIMQualifier qe; 
            tempIndex = qualifiers.indexOf(new CIMQualifier("association"));
            if(tempIndex >= 0) {
                qe = (CIMQualifier)qualifiers.elementAt(tempIndex);
            } else {
                qe = null;
            }
            if (qe != null) {
                CIMValue Tmp = qe.getValue();
                // We are assuming that the default for assoc. qualifier
                // is false according to the CIM spec. Hence having no
                // value indicates that it is false.
                if ((Tmp != null) && Tmp.equals(CIMValue.TRUE)) {
                    curClassEl.setIsAssociation(true);
                    curLevel = "ASSOCIATION";
                }
            }
            if (!erroneousPart) {
                curClassEl.setQualifiers(qualifiers);
            }
        }
        qualifiers = new Vector();
    }

    public void assignClassAlias(String aliasName) {
        //curClassEl.setAlias(aliasName);
        // check for duplicate alias names
        curClassAlias = aliasName;
    }

    public void assignSuperclassName(String superclassName) {
        curClassEl.setSuperClass(superclassName);
    }

    public void assignPropertyQualifiers() {
        if (!erroneousPart) {
    	    curPropRefEl.setQualifiers(qualifiers);
            CIMQualifier cq = curPropRefEl.getQualifier("key");
            if (cq!=null) {
                CIMValue cv = cq.getValue();
                if (cv != null) {
                    if (cv.getValue().equals(new Boolean(true))) {
                        curPropRefEl.setKey(true);
                    }
                } else {
                    curPropRefEl.setKey(true);
                }
            }
        }
        qualifiers = new Vector();
    }

    public void addProperty() {
        if (erroneousUnit) {
            erroneousPart=true;
        }
        if (!erroneousPart) {
            verifyQualifiers(curPropRefEl);
            properties.addElement(curPropRefEl);
        } else {
            erroneousUnit=true;
        }
        curPropRefEl = new CIMProperty();
    }

    public void assignFeatureName(String featureName) throws CIMException {
        if ((properties.contains(new CIMProperty(featureName))) ||
            (methods.contains(new CIMMethod(featureName)))) {
            erroneousPart=true;
            reportError("ERR_SEM", "ERR_FEATURE_REDEFINED", 1,
                        curClassEl.getName());
        } else {
            curPropRefEl.setName(featureName);
            curMethodEl.setName(featureName);
            curMethodEl.setOriginClass(curClassEl);
        }
    }

    public void assignReferenceQualifiers() throws CIMException {
        if (!erroneousPart) {
            try {
                curPropRefEl.setQualifiers(qualifiers);
            } catch(Exception e) {
                //XXX: Need to catch what type of exception and give user 
                // real info
                reportError("ERR_EXC", "ERR_EXC_SET_QUAL", 1, e.toString());
            }
        }
        qualifiers = new Vector();
    }

    public void addReference() {
        if (erroneousUnit) {
            erroneousPart=true;
        }
        if (!erroneousPart) {
            refsRequired--;
            properties.addElement(curPropRefEl);
        }
        curPropRefEl = new CIMProperty();
    } 

    public void assignRefClassName(CIMDataType refClassType) {
        curPropRefEl.setType(refClassType);
    }

    public CIMObjectPath getInstanceName(String aliasName)
        throws CIMException {
        throw new CIMException(CIMException.CIM_ERR_NOT_SUPPORTED);
    }

    public void assignMethodQualifiers() {
        curMethodEl.setQualifiers(qualifiers);
        qualifiers = new Vector();
    }

    public void assignMethodParameters() {
        if (!erroneousPart) {
            curMethodEl.setParameters(parameters);
        }
        parameters = new Vector();
    }

    public void addMethod() {
        if (erroneousUnit) { 
            erroneousPart=true;
        }
        if (!erroneousPart) {
            methods.addElement(curMethodEl);
        } else {
            erroneousUnit=true;
        }
        curMethodEl= new CIMMethod();
    } 

    public void addParameter() {
        if (erroneousUnit) {
            erroneousPart=true;
        }
        if (!erroneousPart) {
            parameters.addElement(curParameterEl);
        } else {
            erroneousUnit=true;
        }
        curParameterEl = new CIMParameter();
    }

    public void assignParameterQualifiers() {
        if (!erroneousPart) {
            curParameterEl.setQualifiers(qualifiers);
        }
        qualifiers = new Vector();
    }

    public void assignParameterName(String ParameterName) throws CIMException {
        if (parameters.contains(new CIMParameter(ParameterName))) {
            reportError("ERR_SEM", "ERR_PARAMETER_EXISTS", 2, ParameterName);
        } else {
            curParameterEl.setName(ParameterName);
        }
    }

    public void assignInstanceQualifiers() {
        if (!erroneousUnit) {
            curInstanceEl.setQualifiers(qualifiers);
        }
        qualifiers = new Vector();
    }

    public void addInstance()
        throws CIMException {
        throw new CIMException(CIMException.CIM_ERR_NOT_SUPPORTED);
    }

    public void assignInstanceClass(String instanceClass) {
        curInstanceEl.setClassName(instanceClass);
    }

    public void assignInstanceAlias(String aliasName) {
        curInstanceAlias = aliasName;
    } 

    public void assignInstancePropertyQualifiers() 
        throws CIMException {
        throw new CIMException(CIMException.CIM_ERR_NOT_SUPPORTED);
    }

    public void addInstanceProperty() 
        throws CIMException {
        throw new CIMException(CIMException.CIM_ERR_NOT_SUPPORTED);
    }

    public void verifyQualifiers(CIMProperty cp) {
        boolean hasValue = false, hasValuemap = false;
        int numValues=-1, numValuemaps=0;
        boolean hasBitValue = false, hasBitmap = false;
        int numBitValues=-1, numBitmaps=0;
        Vector v = cp.getQualifiers();
        Enumeration e = v.elements();
        while (e.hasMoreElements()) {
            CIMQualifier cq = (CIMQualifier)e.nextElement();
            if (cq.getName().equalsIgnoreCase("values")) {
                hasValue = true;
                CIMValue cv = cq.getValue();
                Vector vv = (Vector) cv.getValue();
                numValues = vv.capacity();
            }
            if (cq.getName().equalsIgnoreCase("valuemap")) {
                hasValuemap = true;
                CIMValue cv = cq.getValue();
                Vector vv = (Vector) cv.getValue();
                numValuemaps = vv.capacity();
            }
            if (cq.getName().equalsIgnoreCase("BitMap")) {
                hasBitValue = true;
                CIMValue cv = cq.getValue();
                Vector vv = (Vector) cv.getValue();
                numBitValues = vv.capacity();
            }
            if (cq.getName().equalsIgnoreCase("BitValues")) {
                hasBitmap = true;
                CIMValue cv = cq.getValue();
                Vector vv = (Vector) cv.getValue();
                numBitmaps = vv.capacity();
            }
        }
        if (hasValue && hasValuemap) {
            //check number of array
            if (numValues != numValuemaps) {
                try {
                    reportError("ERR_SEM", "ERR_ILLEGAL_VALUES", 1,
                                this.curClassEl.getName() + "." 
                                + cp.getName()  + " Values/ValueMap qualifiers don't have " +
                                "the same number of elements (Values has " + numValues + 
                                " ValueMap has " + numValuemaps + ")");
                } catch (Exception eig) {}
            }
        }
        if (hasBitValue && hasBitmap) {
            //check number of array
            if (numBitValues != numBitmaps) {
                try {
                    reportError("ERR_SEM", "ERR_ILLEGAL_VALUES", 1,
                                this.curClassEl.getName() + "." 
                                + cp.getName()  + " BitMap/BitValues qualifiers don't have "
                                + "the same number of elements (Values has " + numBitValues
                                + " ValueMap has " + numBitmaps + ")");
                } catch (Exception eig) {}
            }
        }

    }
}
