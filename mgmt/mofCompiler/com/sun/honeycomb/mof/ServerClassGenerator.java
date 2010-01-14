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

import javax.wbem.cim.CIMException;
import java.io.IOException;
import java.io.FileWriter;
import javax.wbem.cim.CIMProperty;
import java.util.Vector;
import javax.wbem.cim.CIMClass;
import com.sun.honeycomb.mof.CIMCompiler;
import javax.wbem.cim.CIMDataType;
import javax.wbem.cim.CIMElement;
import java.util.Iterator;
import javax.wbem.cim.CIMMethod;
import javax.wbem.cim.CIMParameter;

public class ServerClassGenerator {

    private String handlerPkg;
    private String handlerDir;
    private String packageName;
    private String packagePath;

    public ServerClassGenerator() {
        handlerPkg = System.getProperty("handlerPackage");
        handlerDir = System.getProperty("outputDir")+"/"+handlerPkg.replace('.', '/');
        packageName = System.getProperty("rootPackage")+".server";
        packagePath = System.getProperty("outputDir")+"/"+
            packageName.replace('.', '/');
    }

    public void generate(CompilerBackend mofSpec)
        throws CIMException, IOException {
        
        CIMClass[] classes = mofSpec.getAllClasses();
        for (int i=0; i<classes.length; i++) {
            generateHandler(classes[i]);
            generateAdapter(classes[i]);
            generateProxy(classes[i]);
        }
    }

    private void generateHandler(CIMClass klass) 
        throws CIMException, IOException {
        
        FileWriter output = null;
        try {
            String classname = klass.getJavaName().toLowerCase()+"_Handler";
            output = new FileWriter(handlerDir+"/"+classname+".java");
            generateHandler(klass, classname, output);
        } finally {
            if (output != null)
                try { output.close(); } catch (IOException e) {}
        }
    }

    private String buildAdapterName(CIMClass klass,
                                    boolean interfaceVersion) {
        return(buildAdapterName(klass.getJavaName(), interfaceVersion));
    }

    private String buildAdapterName(String javaName,
                                    boolean interfaceVersion) {
        String interfaceString = interfaceVersion ? "Interface" : "";
        
        return(javaName+"Adapter"+interfaceString);
    }


    private void generateAdapter(CIMClass klass) 
        throws CIMException, IOException {
        
        FileWriter output = null;
        try {
            String classname = buildAdapterName(klass, true);
            output = new FileWriter(packagePath+"/"+classname+".java");
            generateAdapter(klass, classname, output);
        } finally {
            if (output != null)
                try { output.close(); } catch (IOException e) {}
        }
    }

    private void generateHandler(CIMClass klass,
                                 String classname,
                                 FileWriter output)
        throws CIMException,IOException {
        String commonPkg = System.getProperty("rootPackage")+".common";
        output.write(CIMCompiler.COMMON_JAVA_HEADER);
        output.write("package "+handlerPkg+";\n\n");
        output.write("import com.sun.ws.management.server.HandlerContext;\n"+
                     "import com.sun.ws.management.Management;\n"+
                     "import com.sun.ws.management.transfer.Transfer;\n"+
                     "import com.sun.ws.management.addressing.ActionNotSupportedFault;\n"+
                     "import "+packageName+"."+klass.getJavaName()+";\n"+
                     "import "+packageName+"."+buildAdapterName(klass, true)+";\n"+
                     "import "+packageName+".ObjectFactory;\n"+
                     "import java.util.Map;\n"+
                     "import javax.xml.bind.JAXBException;\n"+
                     "import org.w3c.dom.Document;\n"+
                     "import javax.xml.soap.SOAPException;\n"+
                     "import javax.xml.bind.JAXBElement;\n"+
                     "import "+packageName+"."+klass.getJavaName()+"Proxy;\n"+
                     "import com.sun.honeycomb.mgmt.common.Utils;\n"+
                     CIMCompiler.STD_TYPE_IMPORTS+
                     "import com.sun.honeycomb.mgmt.server.HCMGMTEvent;\n"+
                     "import com.sun.honeycomb.mgmt.server.EventSender;\n"+
                     "import com.sun.honeycomb.mgmt.server.Task;\n"+
                     "import com.sun.honeycomb.mgmt.common.MgmtException;\n"+
                     "\n");
        output.write("public class "+classname+"\n"+
                     "    extends TopHandler {\n\n");

        // Define the attributes
        output.write("    private "+buildAdapterName(klass, true)+" adapter;\n"+
                     "    private "+klass.getJavaName()+"Proxy proxy;\n"+
                     "    private ObjectFactory factory;\n"+
                     "\n");
        output.write("    public "+classname+"()\n"+
                     "        throws JAXBException,\n"+
                     "               ClassNotFoundException,\n"+
                     "               InstantiationException,\n"+
                     "               IllegalAccessException {\n"+
                     "        super(\"" + packageName + "\");\n"+
                     "        adapter = ("+buildAdapterName(klass, true)+")getProvider(\""+buildAdapterName(klass, false)+"\");\n"+
                     "        proxy = new "+klass.getJavaName()+"Proxy();\n"+
                     "        factory = new ObjectFactory();\n"+
                     "    }\n\n");

        // The load method
        output.write("    protected void load(Management request)\n"+
                     "        throws JAXBException, InstantiationException, SOAPException {\n"+
                     "        Map<String,String> sel = decodeSelector(request);\n"+
                     "        adapter.load"+klass.getJavaName()+"(");

        CIMProperty[] keyProps = KeyCache.getInstance().resolve(klass.getName());
        boolean first = true;
        for (int i=0; i<keyProps.length; i++) {
            CIMProperty prop = keyProps[i];
            if (first) {
                first = false;
            } else {
                output.write(",\n                ");
            }
            output.write(Utils.castFromString(prop.getType(),
                                              "sel.get(\""+prop.getJavaName()+"\")"));
        }
        output.write(");\n"+
                     "    }\n\n");

        // The sendObject method

        output.write("    private void sendObject(Management response,\n"+
                     "                            "+klass.getJavaName()+" reply)\n"+
                     "        throws JAXBException, SOAPException {\n");
        // Encode the reply object in XML
        output.write("            Document bodySoc = Management.newDocument();\n"+
                     "            jaxbContext.createMarshaller().marshal(factory.createJAXB"+klass.getJavaName()+"(reply), bodySoc);\n"+
                     "            response.getBody().addDocument(bodySoc);\n");
        output.write("    }\n\n");

        // The handle method

        output.write("    public void handle(String action,\n"+
                     "                       String resource,\n"+
                     "                       HandlerContext context,\n"+
                     "                       Management request,\n"+
                     "                       final Management response)\n"+
                     "        throws Exception {\n");
        output.write("        load(request);\n\n"+
                     "        if (action.equals(Transfer.GET_ACTION_URI)) {\n");

        /*
         * GET_ACTION_URI
         */

        output.write("            response.setAction(Transfer.GET_RESPONSE_URI);\n");
        // Build the reply object
        output.write("            // Build the reply object\n"+
                     "            "+klass.getJavaName()+" reply = factory.create"+klass.getJavaName()+"();\n"+
                     "            proxy.populate(adapter, reply);\n"+
                     "            sendObject(response, reply);\n");
        
        /*
         * PUT_ACTION_URI
         */

        output.write("        } else if (action.equals(Transfer.PUT_ACTION_URI)) {\n"+
                     "            response.setAction(Transfer.PUT_RESPONSE_URI);\n"+
                     "            Document xml = request.getBody().extractContentAsDocument();\n"+
                     "            JAXBElement jaxb = (JAXBElement)jaxbContext.createUnmarshaller().unmarshal(xml);\n"+
                     "            "+klass.getJavaName()+" update = ("+klass.getJavaName()+")jaxb.getValue();\n"+
                     "            proxy.update(adapter, update);\n"+
                     "            sendObject(response, update);\n");

        /*
         * ONGOING ACTIONS
         */

        output.write("        } else if (action.equals(Utils.BASE_CUSTOM_ONGOING_ACTION_URI)) {\n"+
                     "            // Get the event\n"+
                     "            Document xml = request.getBody().extractContentAsDocument();\n"+
                     "            JAXBElement jaxb = (JAXBElement)evJaxbContext.createUnmarshaller().unmarshal(xml);\n"+
                     "            HCMGMTEvent event = (HCMGMTEvent)jaxb.getValue();\n"+
                     "            // Send the event to the context\n"+
                     "            InteractiveContext ctx = ContextRepository.getInstance().matchContext(event.getCookie());\n"+
                     "            ctx.incomingReply(response, event);\n"+
                     "            ctx.waitForReadyResponse();\n");
        
        /*
         * CUSTOM ACTIONS
         */

        Iterator methods = klass.getAllMethods().iterator();
        while (methods.hasNext()) {
            CIMMethod m = (CIMMethod)methods.next();
            String argtype = packageName+"."+Utils.generateMethodTypeName(m, true);
            boolean isXmlMethod = false;
            for (int i = 0; i < m.getParameters().size(); i++) {
                CIMParameter curParam = (CIMParameter) m.getParameters().get(i);
                if (curParam.getType().getType() == CIMDataType.XML) {
                    isXmlMethod = true;
                    break;
                }
            }

            String restype = Utils.generateMethodTypeName(m, false);
            boolean hasArguments = (m.getParameters().size() > 0);
            boolean isInteractive = m.hasQualifier(Utils.INTERACTIVE_CIM_KEYWORD);

            output.write("        } else if (action.equals(Utils.BASE_CUSTOM_ACTION_URI+\"" + m.getJavaName() + "\")) {\n" );
            output.write("            final Document xml = request.getBody().extractContentAsDocument();\n");
            if (isInteractive) {
                output.write("            final InteractiveContext ctx = ContextRepository.getInstance().getFreeContext();\n"+
                             "            ctx.init(response, new Task() {\n"+
                             "                public void run() throws MgmtException {\n"+
                             "                    // Method to be executed\n"+
                             "                    try {\n");
                
            } 

            if (hasArguments) {
                if (isXmlMethod) {
                    output.write("            Document args = xml;\n");
                } else {
                    output.write("            JAXBElement jaxb = (JAXBElement)jaxbContext.createUnmarshaller().unmarshal(xml);\n"+
                                 "            "+argtype+" args = ("+argtype+")jaxb.getValue();\n");
                }
            }
            output.write("            "+packageName+"."+restype+" res = factory.create"+Utils.capitalizeFirstLetter(restype)+"();\n");
            output.write("            proxy."+m.getJavaName()+"(adapter, res");
            if (hasArguments) {
                output.write(", args");
            }
            if (isInteractive) {
                output.write(", ctx");
            }
            output.write(");\n");

            output.write("            Document xmlresp = Management.newDocument();\n"+
                         "            jaxbContext.createMarshaller().marshal(factory.createJAXB"+
                         Utils.capitalizeFirstLetter(Utils.generateMethodTypeName(m, false))+"(res), xmlresp);\n");
            if (isInteractive) {
                output.write("            ctx.sendDocument(Utils.BASE_CUSTOM_RESPONSE_URI+\""+m.getJavaName()+"\", xmlresp);\n");
            } else {
                output.write("            response.setAction(Utils.BASE_CUSTOM_RESPONSE_URI+\""+m.getJavaName()+"\");\n");
                output.write("            response.getBody().addDocument(xmlresp);\n");
            }
            
            if (isInteractive) {
                output.write("                    } catch (JAXBException e) {\n"+
                             "                        // Do something\n"+
                             "                    }\n");
                output.write("                }\n"+
                             "            });\n"+
                             "            ctx.waitForReadyResponse();\n");
            }
        }

        /*
         * FOOTER
         */        

        output.write("        } else {\n"+
                     "            throw new UnsupportedOperationException(\"The action \"+action+\" is not supported\");\n"+
                     "        }\n");
        output.write("    }\n\n");
        output.write("}\n");
    }


    private void generateAdapter(CIMClass klass,
                                 String classname,
                                 FileWriter output)
        throws CIMException, IOException {
        output.write(CIMCompiler.COMMON_JAVA_HEADER);
        output.write("package "+packageName+";\n\n");
        output.write("import org.w3c.dom.Document;\n");
        output.write("import com.sun.honeycomb.mgmt.server.EventSender;\n");
        output.write("import com.sun.honeycomb.mgmt.common.MgmtException;\n");
        output.write(CIMCompiler.STD_TYPE_IMPORTS+
                     "\n");
        output.write("public interface "+classname);
        if (klass.getSuperClass() != null) {
            output.write("\n    extends "+buildAdapterName(CIMElement.nameToJava(klass.getSuperClass()), true));
        }
        output.write(" {\n\n");

        // Define the load method
        output.write("    public void load"+klass.getJavaName()+"(");
        CIMProperty[] keyProps = KeyCache.getInstance().resolve(klass.getName());
        boolean first = true;
        for (int i=0; i<keyProps.length; i++) {
            CIMProperty prop = keyProps[i];
            if (first) {
                first = false;
            } else {
                output.write(",\n        ");
            }
            output.write(Utils.mofToJava(prop.getType())+" "+prop.getJavaName());
        }
        output.write(")\n"+
                     "        throws InstantiationException;\n\n");

        // Define the accessors
        output.write("    /*\n"+
                     "    * This is the list of accessors to the object\n"+
                     "    */\n");
        Vector props = klass.getAllProperties();
        for (int i=0; i<props.size(); i++) {
            CIMProperty prop = (CIMProperty)props.get(i);
            String name = prop.getJavaName();
            String type = Utils.mofToJava(prop.getType());
            String mName = Utils.capitalizeFirstLetter(name);

            if (Utils.isAnArray(prop.getType())) {
                // Array accessors
                output.write("    public void populate"+mName+"("+type+" array) throws MgmtException;\n");
            } else {
                // Simple accessors
                output.write("    public "+type+" get"+mName+"() throws MgmtException;\n");
            }
            if (!prop.hasQualifier("Read")) {
                output.write("    public void set"+mName+"("+type+" value) throws MgmtException;\n");
            }
        }

        // Define the custom actions
        Iterator methods = klass.getAllMethods().iterator();
        if (methods.hasNext()) {
            output.write("\n    /*\n"+
                         "     * This is the list of custom actions\n"+
                         "     */\n");
            while (methods.hasNext()) {
                CIMMethod method = (CIMMethod)methods.next();
                output.write("    public "+Utils.mofToJava(method.getType())+" "+
                             method.getJavaName()+"(");
                first = true;
                if (method.hasQualifier(Utils.INTERACTIVE_CIM_KEYWORD)) {
                    output.write("EventSender eventSender");
                    first = false;
                }
                Iterator params = method.getParameters().iterator();
                while (params.hasNext()) {
                    CIMParameter p = (CIMParameter)params.next();
                    if (first) {
                        first = false;
                    } else {
                        output.write(",\n        ");
                    }
                    output.write(Utils.mofToJava(p.getType())+" "+p.getJavaName());
                }
                output.write(") throws MgmtException;\n");
            }
        }

        output.write("\n}\n");
    }

    private void generateProxy(CIMClass klass) 
        throws CIMException, IOException {
        FileWriter output = null;
        try {
            output = new FileWriter(packagePath+"/"+klass.getJavaName()+"Proxy.java");
            generateProxy(klass, output);
        } finally {
            if (output != null)
                try { output.close(); } catch (IOException e) {}
        }
    }

    private void generateProxy(CIMClass klass,
                               FileWriter output)
        throws CIMException, IOException {
        output.write(CIMCompiler.COMMON_JAVA_HEADER);
        output.write("package "+packageName+";\n\n");
        output.write("import org.w3c.dom.Document;\n");
        output.write("import com.sun.honeycomb.mgmt.server.EventSender;\n");
        output.write("import com.sun.honeycomb.mgmt.common.MgmtException;\n");

        output.write(CIMCompiler.STD_TYPE_IMPORTS+
                     "\n");


        output.write("public class "+klass.getJavaName()+"Proxy");
        if (klass.getSuperClass() != null) {
            output.write("\n    extends "+CIMElement.nameToJava(klass.getSuperClass())+
                         "Proxy");
        }
        output.write(" {\n\n");

        // Constructor
        output.write("    public "+klass.getJavaName()+"Proxy() {\n");
        if (klass.getSuperClass() != null) {
            output.write("        super();\n");
        }
        output.write("    }\n\n");

        // Populate method
        output.write("    public void populate("+buildAdapterName(klass, true)+" adapter,\n"+
                     "                         "+klass.getJavaName()+" obj) throws MgmtException {\n");
        if (klass.getSuperClass() != null) {
            output.write("        super.populate(adapter, obj);\n");
        }
        Vector props = klass.getAllProperties();
        for (int i=0; i<props.size(); i++) {
            CIMProperty prop = (CIMProperty)props.get(i);
            String mName = Utils.capitalizeFirstLetter(prop.getJavaName());
            CIMDataType type = prop.getType();
            if (Utils.isAnArray(type)) {
                String typeS = Utils.mofToJava(type);
                output.write("        "+typeS+" "+prop.getJavaName()+" = obj.get"+mName+"();\n"+
                             "        "+prop.getJavaName()+".clear();\n"+
                             "        adapter.populate"+mName+"("+prop.getJavaName()+");\n");
            } else {
                output.write("        obj.set"+mName+
                             "(adapter.get"+mName+"());\n");
            }
        }
        output.write("    }\n\n");

        // update method
        output.write("    public void update("+buildAdapterName(klass, true)+" adapter,\n"+
                     "                         "+klass.getJavaName()+" obj) throws MgmtException {\n");
        if (klass.getSuperClass() != null) {
            output.write("        super.update(adapter, obj);\n");
        }
        for (int i=0; i<props.size(); i++) {
            CIMProperty prop = (CIMProperty)props.get(i);
            String mName = Utils.capitalizeFirstLetter(prop.getJavaName());
            CIMDataType type = prop.getType();
            if (Utils.isAnArray(type)) {
                // Not yet supported
            } else {
                if (!prop.hasQualifier("Read")) {
                    output.write("        if (!adapter.get"+mName+"().equals(obj.get"+mName+"())) {\n"+
                                 "            adapter.set"+mName+"(obj.get"+mName+"());\n"+
                                 "        }\n");
                }
            }
        }
        output.write("    }\n\n");

        // Extra actions
        Iterator methods = klass.getAllMethods().iterator();
        while (methods.hasNext()) {
            CIMMethod method = (CIMMethod)methods.next();
            boolean hasArguments = (method.getParameters().size() > 0);
            boolean isInteractive = method.hasQualifier(Utils.INTERACTIVE_CIM_KEYWORD);

            String xmlArg = null;
            Iterator params = method.getParameters().iterator();
            while (params.hasNext()) {
                CIMParameter p = (CIMParameter)params.next();
                if (p.getType().getType() == CIMDataType.XML) {
                    xmlArg = p.getJavaName();
                    break;
                }
            }
            output.write("    public void "+method.getJavaName()+"("+
                         buildAdapterName(klass, true)+" adapter,\n"+
                         "        "+Utils.generateMethodTypeName(method, false)+" res");
            if (hasArguments) {
                if (xmlArg != null) {
                    output.write(",\n        Document "+xmlArg);
                } else {
                    output.write(",\n        "+Utils.generateMethodTypeName(method, true)+" args");
                }
            }
            if (isInteractive) {
                output.write(",\n        EventSender eventSender");
            }
            output.write(") throws MgmtException {\n");
            output.write("        res.setValue(adapter."+method.getJavaName()+"(");
            boolean first = true;
            if (isInteractive) {
                output.write("eventSender");
                first = false;
            }
            if (xmlArg != null) {
                if (!first) output.write(", ");
                output.write(xmlArg);
            } else { 
                params = method.getParameters().iterator();
                while (params.hasNext()) {
                    CIMParameter p = (CIMParameter)params.next();
                    if (first) {
                        first = false;
                    } else {
                        output.write(", ");
                    }
                    output.write("args.get"+Utils.capitalizeFirstLetter(p.getJavaName())+"()");
                }
            }
            output.write("));\n");
            output.write("    }\n\n");
        }
        
        // Footer
        output.write("}\n");
    }
}
