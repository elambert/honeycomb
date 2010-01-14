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

import javax.wbem.cim.CIMDataType;
import javax.wbem.cim.CIMException;
import javax.wbem.cim.CIMElement;
import javax.wbem.cim.CIMMethod;

public class Utils {
    
    public static final String EVENT_CLASS_NAME = "HCMGMTEvent";
    public static final String INTERACTIVE_CIM_KEYWORD = "Interactive";
    
    public static class XsdEncoding {
        public String type;
        public String extraDef;

        private XsdEncoding(String _type) {
            type = _type;
            extraDef = null;
        }

        private XsdEncoding(String _type,
                            String _extraDef) {
            type = _type;
            extraDef = _extraDef;
        }
    }

    public static XsdEncoding mofToXsd(CIMDataType type)
        throws CIMException {
        
        switch (type.getType()) {
        case CIMDataType.BOOLEAN:
            return(new XsdEncoding("xs:boolean"));

        case CIMDataType.SINT8:
            return(new XsdEncoding("xs:byte"));

        case CIMDataType.SINT16:
            return(new XsdEncoding("xs:short"));

         case CIMDataType.SINT8_ARRAY:
             return(new XsdEncoding("xs:byte", "maxOccurs=\"unbounded\""));

        case CIMDataType.SINT32:
            return(new XsdEncoding("xs:integer"));

        case CIMDataType.SINT32_ARRAY:
            return(new XsdEncoding("xs:integer", "maxOccurs=\"unbounded\""));

        case CIMDataType.SINT64:
            return(new XsdEncoding("xs:long"));

        case CIMDataType.STRING:
            return(new XsdEncoding("xs:string"));

        case CIMDataType.XML:
            return(new XsdEncoding("xs:xml"));

        case CIMDataType.STRING_ARRAY:
            return(new XsdEncoding("xs:string", "maxOccurs=\"unbounded\""));
        
        case CIMDataType.REFERENCE:
            return(new XsdEncoding(CIMElement.nameToJava(type.getRefClassName())));

        case CIMDataType.REFERENCE_ARRAY:
            return(new XsdEncoding(CIMElement.nameToJava(type.getRefClassName()),
                                   "maxOccurs=\"unbounded\""));
    
        default:
            throw new CIMException(CIMException.CIM_ERR_NOT_SUPPORTED,
                                   "The data type "+
                                   type.toMOF()+" is not supported");
        }
    }

    public static String mofToJava(CIMDataType type)
        throws CIMException {

        switch (type.getType()) {
        case CIMDataType.BOOLEAN:
            return("Boolean");

         case CIMDataType.SINT8:
             return("Byte");

         case CIMDataType.SINT16:
             return("Short");

         case CIMDataType.SINT8_ARRAY:
             return("List<Byte>");

        case CIMDataType.SINT32:
            return("BigInteger");

        case CIMDataType.SINT32_ARRAY:
            return("List<BigInteger>");

        case CIMDataType.SINT64:
            return("Long");

        case CIMDataType.STRING:
            return("String");

        case CIMDataType.XML:
            return("org.w3c.dom.Document");

        case CIMDataType.STRING_ARRAY:
            return("List<String>");

        case CIMDataType.REFERENCE:
            return(CIMElement.nameToJava(type.getRefClassName()));

        case CIMDataType.REFERENCE_ARRAY:
            return("List<" + CIMElement.nameToJava(type.getRefClassName()) +
                   ">");
        
        default:
            throw new CIMException(CIMException.CIM_ERR_NOT_SUPPORTED,
                                   "The data type "+
                                   type.toMOF()+" is not supported");
        }
    }

    public static boolean isAnArray(CIMDataType type) {
        switch (type.getType()) {
        case CIMDataType.SINT8_ARRAY:
            return(true);

        case CIMDataType.SINT32_ARRAY:
            return(true);

        case CIMDataType.STRING_ARRAY:
            return(true);

        case CIMDataType.REFERENCE_ARRAY:
            return(true);

        default:
            return(false);
        }
    }

    /*
     * Only the types that can be used as keys have to be defined in the
     * method below.
     */
    
    public static String castToString(CIMDataType type,
                                      String name)
        throws CIMException {
        
        switch (type.getType()) {
        case CIMDataType.SINT32:
            return(name+".toString()");

        case CIMDataType.STRING:
            return(name);
        
        default:
            throw new CIMException(CIMException.CIM_ERR_NOT_SUPPORTED,
                                   "The data type "+
                                   type.toMOF()+" is not supported");
        }
    }

    /*
     * Only the types that can be used as keys have to be defined in the
     * method below.
     */

    public static String castFromString(CIMDataType type,
                                        String value)
        throws CIMException {
        
        switch (type.getType()) {
        case CIMDataType.SINT32:
            return("new BigInteger("+value+")");

        case CIMDataType.STRING:
            return(value);
            
        default:
            throw new CIMException(CIMException.CIM_ERR_NOT_SUPPORTED,
                                   "The data type "+
                                   type.toMOF()+" is not supported");
        }
    }

    public static String capitalizeFirstLetter(String input) {
        StringBuffer sb = new StringBuffer(input);
        sb.replace(0, 1, input.substring(0, 1).toUpperCase());
        return(sb.toString());
    }

    public static String generateMethodTypeName(CIMMethod method,
                                                boolean args) {
        String suffix = args ? "Args" : "Result";
        return(capitalizeFirstLetter(method.getOriginClass().getJavaName())+
               capitalizeFirstLetter(method.getJavaName())
               +suffix);
    }

}
