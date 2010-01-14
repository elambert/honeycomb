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




package com.sun.honeycomb.multicell.lib;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

// Not clean to call CellInfo from here
import com.sun.honeycomb.multicell.CellInfo;
import com.sun.honeycomb.common.ProtocolConstants;

public class XMLWriter {

    private static final String BEGIN_OPEN_TAG = "<";
    private static final String BEGIN_CLOSE_TAG = "</";
    private static final String END_TAG = ">";
    private static final String END_EMPTY_TAG = "/>";
    private static final String INDENT_SPACE = "    ";

    private static final String XML_HEADER = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";


    public static void generateXML(List cells,
                                   long versionMajor,
                                   OutputStream out)
        throws IOException {
        
        PrintStream printer = new PrintStream(out);
        printer.println(XML_HEADER);

        int indent = 0;
        HashMap map = new HashMap();
        map.put(ProtocolConstants.ATT_VERSION_MAJOR,
	  String.valueOf(versionMajor));
        openTag(ProtocolConstants.TAG_MC_DESC, map, indent++, false, printer);
        for (int i = 0; i < cells.size(); i++) {
            Cell curCell = (Cell) cells.get(i);
            curCell.generateXMLServer(printer, indent);
        }
        closeTag(ProtocolConstants.TAG_MC_DESC, --indent, printer);
        printer.flush();
        printer.close();
    }
    
    
    public static void generateXMLClient(List cells,
                                         long versionMajor,
                                         long versionMinor,
                                         OutputStream out)
        throws IOException {
        
        PrintStream printer = new PrintStream(out);
        printer.println(XML_HEADER);

        int indent = 0;
        HashMap map = new HashMap();
        map.put(ProtocolConstants.ATT_VERSION_MAJOR,
                String.valueOf(versionMajor));
        map.put(ProtocolConstants.ATT_VERSION_MINOR,
                String.valueOf(versionMinor));
        openTag(ProtocolConstants.TAG_MC_DESC, map, indent++, false, printer);
        for (int i = 0; i < cells.size(); i++) {
            CellInfo curCell = (CellInfo) cells.get(i);
            curCell.generateXMLClient(printer, indent);
        }
        closeTag(ProtocolConstants.TAG_MC_DESC, --indent, printer);
        printer.flush();
    }

    


    public static void openTag(String tagName,
                                Map attributes,
                                int indentLevel,
                                boolean close,
                               PrintStream printer) {
        try {
            indent(indentLevel, printer);
            
            printer.print(BEGIN_OPEN_TAG);
            printer.print(tagName);
            printer.print(" ");
            
            Iterator it = attributes.keySet().iterator();
            while (it.hasNext()) {
                String name = (String) it.next();
                String value = (String) attributes.get(name);
                printer.print(" ");
                printer.print(name);
                printer.print("=\"");
                printer.print(value);
                printer.print("\"");
            }
            printer.println((close) ? END_EMPTY_TAG : END_TAG);
        } catch (IOException ioe) {
            throw new MultiCellLibError("can't create XML config file", ioe);
        }
    }
        
    public static void closeTag(String name, int indentLevel,
                                PrintStream printer) {
        try {
            indent(indentLevel, printer);
            
            printer.print(BEGIN_CLOSE_TAG);
            printer.print(name);
            printer.println(END_TAG);
        } catch (IOException ioe) {
            throw new MultiCellLibError("can't create XML config file", ioe);
        } 
    }

    private static void indent(int indentLevel, PrintStream printer)
        throws IOException {
        for (int i = 0; i < indentLevel; i++) {
            printer.print(INDENT_SPACE);
        }
    }
}
