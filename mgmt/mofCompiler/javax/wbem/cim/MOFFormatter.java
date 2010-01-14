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
 *Contributor(s): WBEM Solutions, Inc., 
 *                Brian Schlosser
 */

package javax.wbem.cim;

import java.io.Serializable;
import java.util.StringTokenizer;
import java.util.Enumeration;
import java.util.Vector;
/** 
 * MOFFormatter will format CIM Elements to the corresponding
 * MOF String representation  
 * @since	WBEM 1.0
 */
 
class MOFFormatter implements Serializable {

    final static long serialVersionUID = 200;

    private final static String INDENT      = "    ";
    private final static char   DELIMITER   = ',';

    private final static char   SINGLEQUOTE = '\'';
    private final static char   QUOTE       = '\"';
    private final static char   SPACE       = ' ';
    private final static char   COLON       = ':';
    private final static char   NEWLINE     = '\n';
    
    // CIMKeywords (Reserved words)
    private final static String ANY             = "any";
    private final static String AS              = "as";
    private final static String ASSOCIATION     = "association";
    private final static String CLASS           = "class";
    private final static String DISABLEOVERRIDE = "disableoverride";
    private final static String DT_BOOL         = "boolean";
    private final static String DT_CHAR16       = "char16";
    private final static String DT_DATETIME     = "datetime";
    private final static String DT_REAL32       = "real32";
    private final static String DT_REAL64       = "real64";
    private final static String DT_SINT16       = "sint16";
    private final static String DT_SINT32       = "sint32";
    private final static String DT_SINT64       = "sint64";
    private final static String DT_SINT8        = "sint8";
    private final static String DT_STR          = "string";
    private final static String DT_UINT16       = "uint16";
    private final static String DT_UINT32       = "uint32";
    private final static String DT_UINT64       = "uint64";
    private final static String DT_UINT8        = "uint8";
    private final static String ENABLEOVERRIDE  = "enableoverride";
    private final static String FLAVOR          = "Flavor";
    private final static String INDICATION      = "indication";
    private final static String INSTANCE        = "instance";
    private final static String METHOD          = "method";
    private final static String NULL            = "null";
    private final static String OF              = "of";
    private final static String PARAMETER       = "parameter";
    private final static String PROPERTY        = "property";
    private final static String QUALIFIER       = "Qualifier";
    private final static String REF             = "REF";
    private final static String REFERENCE       = "reference";
    private final static String RESTRICTED      = "restricted";
    private final static String SCHEMA          = "schema";
    private final static String SCOPE           = "Scope";
    private final static String TOSUBCLASS      = "tosubclass";
    private final static String TRANSLATABLE    = "translatable";

    public MOFFormatter() {
    }
    
    public String toString(Object o) {
        String mof;
        if (o instanceof CIMInstance) {
            mof = cimInstance((CIMInstance)o);
        } else if (o instanceof CIMClass) {
            mof = cimClass((CIMClass)o);
        } else if (o instanceof CIMValue) {
            mof = cimValue((CIMValue)o);
        } else if (o instanceof CIMQualifierType) {
            mof = cimQualifierType((CIMQualifierType)o);
        } else if (o instanceof CIMQualifier) {
            mof = cimQualifier((CIMQualifier)o);
        } else if (o instanceof CIMDateTime) {
            mof = cimDateTime((CIMDateTime)o);
	} else if (o instanceof CIMArgument) {
	    mof = cimArgument((CIMArgument)o); 
        } else if (o instanceof CIMProperty) {
            mof = cimProperty((CIMProperty)o); 
        } else if (o instanceof CIMScope) {
            mof = cimScope((CIMScope)o);
        } else if (o instanceof CIMFlavor) {
            mof = cimFlavor((CIMFlavor)o);
        } else if (o instanceof CIMDataType) {
            mof = cimDataType((CIMDataType)o);
        } else if (o instanceof CIMMethod) {
            mof = cimMethod((CIMMethod)o);
        } else if (o instanceof CIMParameter) {
            mof = cimParameter((CIMParameter)o);
        } else {
            mof = String.valueOf(o);
        }
        return mof;
    }

    public String arrayToMOFString(Object[] objArray, boolean b) {
        return arrayToMOFString(objArray, INDENT, b, false, true);
    }

    public String arrayToMOFString(Object[] objArray, String indent, 
				                   boolean bracketed, 
				                   boolean delimited, boolean lf) {
        StringBuffer str = new StringBuffer();
        if (objArray != null && objArray.length > 0) {
            if (bracketed) {
                str.append(indent);
                str.append('[');
            }
            for (int i = 0; i < objArray.length; i++) {
                if (i > 0) {
                    if(delimited) { 
                        str.append(DELIMITER);
                    }
                    if(lf) {
                        str.append(NEWLINE);
                        str.append(indent);
                    }
                    str.append(SPACE);
                }
            
                Object obj = objArray[i];
                if(obj instanceof String ||
                   obj instanceof CIMObjectPath) {
                    str.append(QUOTE);
                    str.append(StringUtil.escape(obj.toString()));
                    str.append(QUOTE);
                } else if(obj instanceof Character) {
                    str.append(SINGLEQUOTE);
                    str.append(StringUtil.escape(obj.toString()));
                    str.append(SINGLEQUOTE);
                } else {
                    str.append(toString(obj));
                }
            }
            if (bracketed) {
                if(lf) {
                    str.append(NEWLINE);
                    str.append(indent);
                }
                str.append("]");
                if(lf) {
                    str.append(NEWLINE);
                }
            }
        } 
        return str.toString();
    }

    public String vectorToMOFString(Vector v) {
        return vectorToMOFString(v, INDENT, false, false, true);
    }
 
    public String vectorToMOFString(Vector vector, boolean bracketed, 
                                    boolean delimited) {
        return vectorToMOFString(vector, INDENT, bracketed, delimited, true);
    }
 
    public String vectorToMOFString(Vector  v, 
                                    String  indent, 
                                    boolean bracketed,
                                    boolean delimited, 
                                    boolean lf) {
        return arrayToMOFString(v.toArray(), indent, bracketed, delimited, lf);
    }

    //?? This needs revisited.
    public String wrapText(String textString, int numColumns) {
        StringTokenizer stringTokenizer = new StringTokenizer(textString,
                                                              " \t\n", true);
        StringBuffer buff = new StringBuffer();
        String word;
        int lineLength = 0;

        while (stringTokenizer.hasMoreTokens()) {
            word = stringTokenizer.nextToken();
            int length = word.length();
            if(length == 1 && word.charAt(0) == NEWLINE) {
                lineLength = 0;
            } else if(lineLength + length > numColumns) {
                buff.append(NEWLINE);
                lineLength = 0;
            }
            // add word if it isn't a space at the beginning of a line
            else if(!((length == 1 && word.charAt(0) == ' ') && 
                    buff.charAt(buff.length() - 1) == NEWLINE)) {
                buff.append(word);
                lineLength += word.length();
            }
        }

        return buff.toString();
    }
    
    private Vector sortQ(Vector in) {
        Vector out = new Vector();
        Enumeration e = in.elements();
        while (e.hasMoreElements()) {
            CIMQualifier cqt = (CIMQualifier)e.nextElement();
            if (cqt.getName().equalsIgnoreCase("Association")) {
                out.insertElementAt(cqt,0);
            } else if (cqt.getName().equalsIgnoreCase("Indication")) {
                out.insertElementAt(cqt,0);               
            } else {
               out.add(cqt);
            }
        }
        return out;
    }
    public String cimClass(CIMClass cc) {
        StringBuffer buffer = new StringBuffer();
        
        Vector qualifiers = sortQ(cc.getQualifiers());
        
        buffer.append(vectorToMOFString(qualifiers, true, true));
        buffer.append(CLASS);
        buffer.append(SPACE);
        buffer.append(cc.getName());
        buffer.append(SPACE);
        if (cc.getSuperClass().length() > 0) {
            buffer.append(COLON);
            buffer.append(SPACE);
            buffer.append(cc.getSuperClass());
            buffer.append(SPACE);
        }
        buffer.append("{");
        buffer.append(NEWLINE);
        Vector properties = cc.getProperties();
        if(properties.size() > 0) {
            buffer.append(vectorToMOFString(properties, "", 
                                            false, false, true));
            buffer.append(NEWLINE);
        }
        Vector methods = cc.getMethods();
        if(methods.size() > 0) {
            buffer.append(vectorToMOFString(methods, "", 
                                            false, false, true));
            buffer.append(NEWLINE);
        }
        buffer.append("};");

        return buffer.toString();
    }
 
    public String cimValue(CIMValue cv) {
        StringBuffer buffer = new StringBuffer();
        
        Object o = cv.getValue();
        if (o != null) {                
            if (cv.getType() != null && cv.getType().isArrayType()) {
                buffer.append(vectorToMOFString((Vector)o, "", 
                                                false, true, false));
            } else {
                if (o instanceof String || o instanceof CIMObjectPath) {
                    buffer.append(QUOTE);
                    buffer.append(StringUtil.escape(o.toString()));
                    buffer.append(QUOTE);
                } else if (o instanceof Character) {
                    buffer.append(SINGLEQUOTE);
                    buffer.append(StringUtil.escape(o.toString()));
                    buffer.append(SINGLEQUOTE);
                } else {
                    buffer.append(o);
                }
            }
        }
        else
        {
            buffer.append("null");
        }

        return buffer.toString();
    }

    public String cimQualifierType(CIMQualifierType cqt) {
        StringBuffer buffer = new StringBuffer();

        buffer.append(QUALIFIER);
        buffer.append(SPACE);
        buffer.append(cqt.getName());
        buffer.append(" : ");
        buffer.append(cqt.getType());
        if (cqt.getType()!= null && cqt.getType().isArrayType()) {
            buffer.append("[]");
        } else {
            buffer.append(" = ");
            buffer.append(cqt.getDefaultValue());
        }
        
        buffer.append(DELIMITER);
        buffer.append(NEWLINE);
        
        if (!cqt.getScope().isEmpty()) {
            buffer.append(INDENT);
            buffer.append(SCOPE);
            buffer.append('('); 
            buffer.append(vectorToMOFString(cqt.getScope(), "", false, 
                              true, false));
            buffer.append(')');
        }

        if (!cqt.getFlavor().isEmpty()) {
            buffer.append(DELIMITER);
            buffer.append(NEWLINE);
            buffer.append(INDENT);
            buffer.append(FLAVOR);
            buffer.append('('); 
            buffer.append(vectorToMOFString(cqt.getFlavor(), "", false, 
                                            true, false));
            buffer.append(')');
        }

        buffer.append(';');
        
        return buffer.toString();
    }

    public String cimQualifier(CIMQualifier cq) {
        StringBuffer buffer = new StringBuffer(cq.getName());

        CIMValue value = cq.getValue();
        if (value != null) {
            if (value.getType() != null && value.getType().isArrayType()) {
                buffer.append('{');
                buffer.append(value.toString());
                buffer.append('}');
            } else {
                buffer.append('(');
                buffer.append(value.toString());
                buffer.append(')');
            }
        }
        
        return buffer.toString();
    }

    public String cimDateTime(CIMDateTime cdt) {
        return "\"" + cdt.getDateTimeString() + "\"";
    }

    public String cimDataType(CIMDataType cdt) {
        switch (cdt.getType()) {
            case CIMDataType.UINT8 :
                return DT_UINT8;
            case CIMDataType.SINT8 :
                return DT_SINT8;
            case CIMDataType.UINT16 :
                return DT_UINT16;
            case CIMDataType.SINT16 :
                return DT_SINT16;
            case CIMDataType.UINT32 :
                return DT_UINT32;
            case CIMDataType.SINT32 :
                return DT_SINT32;
            case CIMDataType.UINT64 :
                return DT_UINT64;
            case CIMDataType.SINT64 :
                return DT_SINT64;
            case CIMDataType.REAL32 :
                return DT_REAL32;
            case CIMDataType.REAL64 :
                return DT_REAL64;
            case CIMDataType.STRING :
                return DT_STR;
            case CIMDataType.CHAR16 :
                return DT_CHAR16;
            case CIMDataType.DATETIME :
                return DT_DATETIME;
            case CIMDataType.BOOLEAN :
                return DT_BOOL;
            case CIMDataType.UINT8_ARRAY :
                return DT_UINT8;
            case CIMDataType.SINT8_ARRAY :
                return DT_SINT8;
            case CIMDataType.UINT16_ARRAY :
                return DT_UINT16;
            case CIMDataType.SINT16_ARRAY :
                return DT_SINT16;
            case CIMDataType.UINT32_ARRAY :
                return DT_UINT32;
            case CIMDataType.SINT32_ARRAY :
                return DT_SINT32;
            case CIMDataType.UINT64_ARRAY :
                return DT_UINT64;
            case CIMDataType.SINT64_ARRAY :
                return DT_SINT64;
            case CIMDataType.REAL32_ARRAY :
                return DT_REAL32;
            case CIMDataType.REAL64_ARRAY :
                return DT_REAL64;
            case CIMDataType.STRING_ARRAY :
                return DT_STR;
            case CIMDataType.CHAR16_ARRAY :
                return DT_CHAR16;
            case CIMDataType.DATETIME_ARRAY :
                return DT_DATETIME;
            case CIMDataType.BOOLEAN_ARRAY :
                return DT_BOOL;
            case CIMDataType.NULL :
                return NULL;
            case CIMDataType.REFERENCE :
                return cdt.getRefClassName() + " " + REF;
            case CIMDataType.REFERENCE_ARRAY:
                return cdt.getRefClassName() + " " + REF;
            default :
                return "";
        }
    }

    public String cimFlavor(CIMFlavor cf) {
        switch (cf.getFlavor()) {
            case CIMFlavor.ENABLEOVERRIDE :
                return ENABLEOVERRIDE;
            case CIMFlavor.DISABLEOVERRIDE :
                return DISABLEOVERRIDE;
            case CIMFlavor.RESTRICTED :
                return RESTRICTED;
            case CIMFlavor.TOSUBCLASS :
                return TOSUBCLASS;
            case CIMFlavor.TRANSLATE :
                return TRANSLATABLE;
            default :
                return "UNKNOWN";
        }
    }

    //?? Should get rid of this
    private boolean cimclass = true;

    public String cimInstance(CIMInstance ci) {
        StringBuffer buffer = new StringBuffer();

        //Note: Qualifiers do not exist on instances - errata Spec 2.3
        buffer.append(INSTANCE);
        buffer.append(SPACE);
        buffer.append(OF);
        buffer.append(SPACE);
        buffer.append(ci.getClassName());
        buffer.append(SPACE);
        String alias = ci.getAlias();
        if(alias != null && alias.length() > 0) {
            buffer.append(AS);
            buffer.append(SPACE);
            buffer.append(alias);
            buffer.append(SPACE);
        }
        buffer.append('{');
        buffer.append(NEWLINE);

        Vector properties = ci.getProperties();
        if(properties.size() > 0) {
            cimclass = false;
            buffer.append(vectorToMOFString(properties, "", 
                                            false, false, true));
            cimclass = true;
            buffer.append(NEWLINE);
        }

        buffer.append("};");
        
        return buffer.toString();
    }

    public String cimMethod(CIMMethod cm) {
        String name = cm.getName();
        if(name == null || name.length() == 0) {
            return "";
        }
        
        StringBuffer buffer = new StringBuffer();
        
        buffer.append(vectorToMOFString(cm.getQualifiers(), true, true));
        buffer.append(INDENT);
        buffer.append(cm.getType());
        buffer.append(SPACE);
        buffer.append(name);
        buffer.append('(');
        buffer.append(vectorToMOFString(cm.getParameters(), "", false, true, false));
        buffer.append(");");
        
        return buffer.toString();
    }

    public String cimParameter(CIMParameter cp) {
        StringBuffer buffer = new StringBuffer();
        
        Vector qualifiers = cp.getQualifiers();
        if(qualifiers.size() > 0) {
            buffer.append(vectorToMOFString(cp.getQualifiers(), "", true, true, false));
            buffer.append(SPACE);
        }
        buffer.append(cp.getType());
        buffer.append(SPACE);
        buffer.append(cp.getName());
        if (cp.getType() != null && cp.getType().isArrayType()) {
            buffer.append("[]");
        }
        
        return buffer.toString();
    }
    
    public String cimArgument(CIMArgument ca) {
        StringBuffer buffer = new StringBuffer(ca.getName());
        
        buffer.append("\n{\n");
        
        if (ca.getQualifiers() != null) {
            buffer.append(vectorToMOFString(ca.getQualifiers(), true, true));
        }
        buffer.append('\n');
        CIMValue value = ca.getValue();
        if (value != null) {
            buffer.append(value);
        }
        buffer.append("\n}");

        return buffer.toString();
    }

    public String cimProperty(CIMProperty cp) {
        String name = cp.getName();
        if(name == null || name.length() == 0) {
            return "";
        }
            
        StringBuffer buffer = new StringBuffer();
        
        CIMDataType cdt = cp.getType();

        buffer.append(vectorToMOFString(cp.getQualifiers(), INDENT,
                                        true, true, true));
        buffer.append(INDENT);
        
        if(cimclass) {
            buffer.append(toString(cdt));
            buffer.append(SPACE);
        }
        
        buffer.append(name);
        if(cimclass && cdt != null) {
            if (cdt.isArrayType()) {
                buffer.append("[]");
            }
        } 
        
        if(!cimclass || (cimclass && cp.getValue() != null && !cdt.isReferenceType())) {
            buffer.append(" = ");
            CIMValue value = cp.getValue();
            if(value == null) {
                buffer.append("null");
            } else {
                if (value.getType() != null && value.getType().isArrayType()) {
                    buffer.append('{');
                }
                buffer.append(cimValue(value));
                if (value.getType() != null && value.getType().isArrayType()) {
                    buffer.append('}');
                }
            }
        }
        buffer.append(";");
        buffer.append(NEWLINE);
        return buffer.toString();
    }

    public String cimScope(CIMScope cs) {
        switch (cs.getScope()) {
            case CIMScope.SCHEMA :      return SCHEMA;
            case CIMScope.CLASS :       return CLASS;
            case CIMScope.ASSOCIATION : return ASSOCIATION;
            case CIMScope.INDICATION :  return INDICATION;
            case CIMScope.PROPERTY :    return PROPERTY;
            case CIMScope.REFERENCE :   return REFERENCE;
            case CIMScope.METHOD :      return METHOD;
            case CIMScope.PARAMETER :   return PARAMETER;
            case CIMScope.ANY :         return ANY;
            default :                   return "UNKNOWN";
        }
    }
}

