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



package com.sun.honeycomb.nodeconfig;

import java.util.HashMap;
import java.io.FileInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.TransformerFactory;
import java.io.PrintStream;
import org.w3c.dom.Document;
import java.io.File;
import java.util.Iterator;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.OutputKeys;

public class NodeConfigEditor {

    private static final String NODE_CONFIG_PROPERTY = "node.config";
    
    private HashMap ops;

    private NodeConfigEditor() {
        ops = new HashMap();
        ops.put("list", new ListOperation());
        ops.put("add", new AddOperation());
        ops.put("del", new DeleteOperation());
        ops.put("cp", new ClasspathOperation());
        ops.put("debug", new DebugOperation());
        ops.put("props", new PropsOperation());
    }

    private void execute(PrintStream out,
                         String[] in_args) 
        throws NodeConfigException {

        if (in_args.length < 1) {
            throw new NodeConfigException("Need at least 1 argument [operation type]");
        }

        String[] args = new String[in_args.length-1];
        System.arraycopy(in_args, 1,
                         args, 0,
                         args.length);

        OperationInterface op = (OperationInterface)ops.get(in_args[0]);
        if (op == null) {
            throw new NodeConfigException("Operation type ["+in_args[0]+"] does not exist");
        }

        String nodeConfig = System.getProperty(NODE_CONFIG_PROPERTY);
        if (nodeConfig == null) {
            nodeConfig = "/opt/honeycomb/share/node_config.xml";
        }
        
        FileInputStream nodeConfigFile = null;
        Document doc = null;

        try {
            nodeConfigFile = new FileInputStream(nodeConfig);
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            doc = builder.parse(nodeConfigFile);
        } catch (IOException e) {
            NodeConfigException newe = new NodeConfigException("Failed to parse ["+nodeConfig+"] : "+e.getMessage());
            newe.initCause(e);
            throw newe;
        } catch (ParserConfigurationException e) {
            NodeConfigException newe = new NodeConfigException("Failed to parse ["+nodeConfig+"] : "+e.getMessage());
            newe.initCause(e);
            throw newe;
        } catch (SAXException e) {
            NodeConfigException newe = new NodeConfigException("Failed to parse ["+nodeConfig+"] : "+e.getMessage());
            newe.initCause(e);
            throw newe;
        } finally {
            if (nodeConfigFile != null) {
                try {
                    nodeConfigFile.close();
                } catch (IOException ignored) {
                }
            }
        }

        boolean needToOverwrite = op.execute(out, doc, args);

        if (needToOverwrite) {
            out.println("\n***** Writing a new version of ["+
                        nodeConfig+"] *****\n");
            
            new File(nodeConfig).renameTo(new File(nodeConfig+".bak"));
            StreamResult result = new StreamResult(new File(nodeConfig));
            DOMSource source = new DOMSource(doc.getDocumentElement());

            try {
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.transform(source, result);
            } catch (TransformerConfigurationException e) {
                NodeConfigException newe = new NodeConfigException("Failed to generate ["+nodeConfig+"] : "+e.getMessage());
                newe.initCause(e);
                throw newe;
            } catch (TransformerException e) {
                NodeConfigException newe = new NodeConfigException("Failed to generate ["+nodeConfig+"] : "+e.getMessage());
                newe.initCause(e);
                throw newe;
            }
        }
    }

    private void usage(PrintStream out) {
        out.println("Usage :\n");

        out.println("help\n"+
                    "\tPrint help\n");

        Iterator iter = ops.values().iterator();
        while (iter.hasNext()) {
            OperationInterface op = (OperationInterface)iter.next();
            op.usage(out);
            out.println("");
        }
    }

    public static void main(String[] arg) {
        NodeConfigEditor editor = new NodeConfigEditor();
        PrintStream out = System.out;

        if ((arg.length == 1) && (arg[0].equals("help"))) {
            editor.usage(out);
            return;
        }

        try {
            editor.execute(out, arg);
        } catch (NodeConfigException e) {
//             e.printStackTrace();
            out.println(e.getMessage());
            editor.usage(out);
        }
    }
}
