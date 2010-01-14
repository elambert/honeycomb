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

import java.io.IOException;
import javax.wbem.cim.CIMException;
import javax.wbem.cim.CIMClass;
import java.io.FileWriter;
import java.util.ArrayList;
import javax.wbem.cim.CIMDataType;
import java.util.Vector;
import javax.wbem.cim.CIMProperty;
import java.util.Iterator;
import java.util.HashMap;

public class ClientClassGenerator {

    private String packageName;
    private String packagePath;

    public ClientClassGenerator() {
        packageName = System.getProperty("rootPackage")+".client";
        packagePath = System.getProperty("outputDir")+"/"+
            packageName.replace('.', '/');
    }

    public void generate(CompilerBackend mofSpec)
        throws CIMException, IOException {

        FileWriter output = null;

        try {
            output = new FileWriter(packagePath+"/Fetcher.java");

            output.write(CIMCompiler.COMMON_JAVA_HEADER);
            output.write("package "+packageName+";\n\n");
            output.write("import javax.xml.soap.SOAPException;\n"+
                         "import javax.xml.bind.JAXBException;\n"+
                         "import java.io.IOException;\n"+
                         "import com.sun.ws.management.client.exceptions.FaultException;\n"+
                         "import javax.xml.datatype.DatatypeConfigurationException;\n"+
                         "import java.util.Map;\n"+
                         "import java.util.HashMap;\n"+
                         "import com.sun.honeycomb.mgmt.common.MgmtException;\n"+
                         CIMCompiler.STD_TYPE_IMPORTS);
            if (!packageName.equals("com.sun.honeycomb.mgmt.client")) {
                output.write("import com.sun.honeycomb.mgmt.client.ClientData;\n");
            }
            output.write("\n");

            output.write("public class Fetcher {\n\n");

            CIMClass[] classes = mofSpec.getAllClasses();
            for (int i=0; i<classes.length; i++) {
                generateClass(classes[i], output);
            }

            output.write("}\n");
        } finally {
            if (output != null)
                try { output.close(); } catch (IOException e) {}
        }
        
    }

    private void generateClass(CIMClass klass,
                               FileWriter output)
        throws CIMException, IOException {

        output.write("    public static "+klass.getJavaName()+" fetch"+klass.getJavaName()+"(String destination");

        CIMProperty[] keyProps = KeyCache.getInstance().resolve(klass.getName());
        for (int i=0; i<keyProps.length; i++) {
            CIMProperty prop = keyProps[i];
            String propName = prop.getJavaName();
            output.write(",\n"+
                         "                               "+Utils.mofToJava(prop.getType())+
                         " "+propName);
        }
        output.write(")\n"+
                     "        throws MgmtException {\n"+
                     "        try {\n");

        if (keyProps.length == 0) {
            output.write("            Map<String,String> selectors = null;\n");
        } else {
            output.write("            Map<String,String> selectors = new HashMap<String,String>();\n");

            for (int i=0; i<keyProps.length; i++) {
                String key = keyProps[i].getJavaName();
                CIMDataType type = keyProps[i].getType();
                output.write("            selectors.put(\""+key+"\", "+Utils.castToString(type, key)+");\n");
            }
        }

        output.write("            return(("+klass.getJavaName()+")ClientData.fetch(destination, \""+
                     klass.getJavaName()+"\", selectors, \"" + packageName +"\"));\n"+
                     "        } catch (SOAPException e) {\n"+
                     "            MgmtException newe = new MgmtException(\"fetch"+klass.getJavaName()+" failed: \"+e.getMessage());\n"+
                     "            newe.initCause(e);\n"+
                     "            throw newe;\n"+
                     "        } catch (JAXBException e) {\n"+
                     "            MgmtException newe = new MgmtException(\"fetch"+klass.getJavaName()+" failed: \"+e.getMessage());\n"+
                     "            newe.initCause(e);\n"+
                     "            throw newe;\n"+
                     "        } catch (IOException e) {\n"+
                     "            MgmtException newe = new MgmtException(\"fetch"+klass.getJavaName()+" failed: \"+e.getMessage());\n"+
                     "            newe.initCause(e);\n"+
                     "            throw newe;\n"+
                     "        } catch (FaultException e) {\n"+
                     "            MgmtException newe = new MgmtException(\"fetch"+klass.getJavaName()+" failed: \"+e.getMessage());\n"+
                     "            newe.initCause(e);\n"+
                     "            throw newe;\n"+
                     "        } catch (DatatypeConfigurationException e) {\n"+
                     "            MgmtException newe = new MgmtException(\"fetch"+klass.getJavaName()+" failed: \"+e.getMessage());\n"+
                     "            newe.initCause(e);\n"+
                     "            throw newe;\n"+
                     "        }\n"+
                     "    }\n\n");
    }
    
}
