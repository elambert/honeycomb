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



import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.Options;
import org.xml.sax.ErrorHandler;
import java.util.Collection;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JExpr;
import javax.xml.bind.JAXBElement;
import java.util.Iterator;
import java.util.List;
import java.util.Collections;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.model.CCustomizations;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.util.ArrayList;
import com.sun.codemodel.JClass;

public class HCPlugin
    extends Plugin {

    private static final String NS = "http://www.sun.com/honeycomb";

    public String getOptionName()
    {
        return "Xhc";
    }

    public String getUsage()
    {
        return "  -Xhc                :  enables the honeycomb specific plugin";
    }

    public List<String> getCustomizationURIs() {
        return Collections.singletonList(NS);
    }

    public boolean isCustomizationTagName(String nsUri, String localName) {
        if (!nsUri.equals(NS)) {
            return(false);
        }

        if (localName.equals("method")) {
            return(true);
        }
        if (localName.equals("arg")) {
            return(true);
        }

        return(false);
    }

    public boolean run(Outline outline, 
                       Options opt, 
                       ErrorHandler errorHandler)
    {
        for (ClassOutline classOutline : outline.getClasses()) {
            JDefinedClass klass = classOutline.implClass;
            String commonpkg = klass.getPackage().parent().name()+".common";
            String clientpkg = klass.getPackage().parent().name()+".client";

            // Add the factory field
            JClass factoryClass = outline.getCodeModel().directClass(clientpkg+".ObjectFactory");
            klass.field(JMod.PRIVATE | JMod.STATIC, factoryClass, "factory", JExpr._new(factoryClass));

            // Add the getJAXB method
            JMethod method = klass.method(JMod.PROTECTED, JAXBElement.class, "getJAXB");
            JBlock code = method.body();
            code._return(JExpr.direct("factory.createJAXB"+klass.name()+"(this)"));

            // Add the methods
            CCustomizations ccs = classOutline.target.getCustomizations();
            for (int i=0; i<ccs.size(); i++) {
                CPluginCustomization c = ccs.get(i);
                Element xml = c.element;
                boolean acknowledged = false;;
                if (xml.getLocalName().equals("method")) {
                    StringBuffer sb = new StringBuffer();
                    generateMethod(klass.name(), commonpkg, xml, sb);
                    klass.direct(sb.toString());
                    acknowledged = true;
                }
                if (acknowledged) {
                    c.markAsAcknowledged();
                }
            }
        }
        
        return(true);
    }

    private void generateMethod(String klass,
                                String commonpkg,
                                Element xml,
                                StringBuffer sb) {
        String name = xml.getAttribute("name");
        boolean interactive = xml.getAttribute("interactive").equals("true");
        ArrayList params = new ArrayList();

        String prefix = "    ";
        sb.append("\n"+prefix+"public "+xml.getAttribute("type")+" "+name+"(");
        if (interactive) {
            sb.append("com.sun.honeycomb.mgmt.client.MethodCallback callback,\n"+
                      prefix+"    ");
        }
        Node node = xml.getFirstChild();
        boolean first = true;
        String xmlArg = null;

        do {
            if ((node != null) && (node.getNodeType() == Node.ELEMENT_NODE) && (node.getLocalName().equals("arg"))) {
                Element el = (Element)node;
                if (first) {
                    first = false;
                } else {
                    sb.append(",\n"+prefix+"    ");
                }
                
                String argname = el.getAttribute("name");
                String type = el.getAttribute("type");
                sb.append(type+" "+argname);
                params.add(argname);
                if (type.equals("org.w3c.dom.Document")) {
                    xmlArg = argname;
                    break;
                }
            }
            if (node != null)
                node = node.getNextSibling();
        } while (node != null);
        sb.append(") \n"+
                  prefix+"    throws com.sun.honeycomb.mgmt.common.MgmtException {\n");

        // Build the arguments
        String lprefix = prefix+"    ";
        String argType = generateMethodTypeName(klass, name, true);
        if (params.size() > 0) {
            if (xmlArg == null) {
                sb.append(lprefix+argType+" args = factory.create"+capitalizeFirstLetter(argType)+"();\n");
                for (int i=0; i<params.size(); i++) {
                    String p = (String)params.get(i);
                    sb.append(lprefix+"args.set"+capitalizeFirstLetter(p)+"("+p+");\n");
                }
            }
        } else {
            sb.append(lprefix+"com.sun.honeycomb.mgmt.client.ClientData args = null;\n");
        }

        argType = generateMethodTypeName(klass, name, false);
        String callbackArg = interactive ? "callback" : "null";
        if (xmlArg != null) {
            sb.append(lprefix+argType+" res = ("+argType+")invoke(\""+name+"\", " + xmlArg + ", "+callbackArg+");\n");            
        } else {
            // Invoke the method and return the result
            sb.append(lprefix+argType+" res = ("+argType+")invoke(\""+name+"\", args, "+callbackArg+");\n");
        }
        sb.append(lprefix+"return(res.getValue());\n");
        sb.append(prefix+"}\n\n");
    }

    public static String capitalizeFirstLetter(String input) {
        StringBuffer sb = new StringBuffer(input);
        sb.replace(0, 1, input.substring(0, 1).toUpperCase());
        return(sb.toString());
    }

    public static String generateMethodTypeName(String klass,
                                                String method,
                                                boolean args) {
        String suffix = args ? "Args" : "Result";
        return(capitalizeFirstLetter(klass)+
               capitalizeFirstLetter(method)+
               suffix);
    }
}
