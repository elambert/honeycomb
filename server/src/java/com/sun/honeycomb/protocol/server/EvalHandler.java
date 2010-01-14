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



package com.sun.honeycomb.protocol.server;

import java.io.FileWriter;
import java.io.PrintStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetAddress;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpInputStream;
import org.mortbay.http.HttpOutputStream;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpMessage;
import org.mortbay.http.HttpResponse;

import java.util.logging.Level;
//import java.util.logging.Logger;


public class EvalHandler extends ProtocolHandler {

    public EvalHandler(final ProtocolBase newService) {
        super(newService);
    }

    Object interpreter = null;
    final static String TEXTAREA_NAME = "expression";

private final static String EXAMPLE = 
    "// Example program\n" +
    //    "com.sun.honeycomb.coordinator.Coordinator.setDefaultBackingStore(new com.sun.honeycomb.coordinator.FileBackingStore(\"/tmp/bs\"));" +
    "d = com.sun.honeycomb.util.sysdep.DiskOps.getDiskOps();\n" + 
    "mounts = d.getCurrentMounts();\n" + 
    "System.out.println(mounts.size() + \" mount points:\");\n" + 
    "c = mounts.values();\n" + 
    "i = c.iterator();\n" + 
    "int count = 1;\n" + 
    "while (i.hasNext()){\n" + 
    "  System.out.println(count++ + \": \" + i.next());\n" + 
    "}\n";


    protected void handle(final String pathInContext,
                          final String pathParams,
                          final HttpRequest request,
                          final HttpResponse response,
                          final HttpFields trailer)
        throws IOException {

	FileWriter fw = new FileWriter("/tmp/foo");
	fw.write("hi");
	fw.flush();
	fw.close();

	String expression = request.getParameter(TEXTAREA_NAME);
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.fine("Eval service called on " + expression);
        }
	
	OutputStream os = response.getOutputStream();
	PrintStream out = new PrintStream(os);
	PrintStream save = System.out;
	System.setOut(out);
	InetAddress me = InetAddress.getLocalHost();
	out.println("<html> <body><h1>Eval Service for " + me.getHostName() + "</h1>");
	out.println("Enter an expression using ");
	out.println("<a href=\"http://www.beanshell.org/manual/syntax.html#Basic_Syntax\">BeanShell syntax</a>");
	out.println(", then click on \"Evaluate\".<br>");
	out.println("You have access to all public methods and slots.<br>");
	out.println("System.out is redirected to the browser.");
// 	out.println("<h3>Parameters:</h3>");
// 	java.util.Iterator iter = request.getParameterNames().iterator();
// 	while (iter.hasNext())
// 	    out.println(iter.next() + "<br>");

// 	out.println("<h3>FIELDS:</h3>");
// 	java.util.Enumeration fields = request.getFieldNames();
// 	while (fields.hasMoreElements())
// 	    out.println(fields.nextElement() + "<br>");

	out.println("<hr>");
	out.println("<form action='/eval' method='post'>");
	out.println("<textarea cols=100 rows=14 name='" + TEXTAREA_NAME + "'>");
	if (expression == null)
	    out.println(EXAMPLE);
	else
	    out.println(expression);
	out.println("</textarea>");
	out.println("<br>");
	out.println("<button type=submit value='submit'>Evaluate</button>");
	out.println("</form>");
	out.println("<br>");
	out.println("<hr>");
	out.println("<pre>");
	try{
	    if (expression == null)
		out.println("<i>Outputy from evaluation will appear here.</i>");
	    else{
 		if (interpreter == null)
 		    interpreter = new bsh.Interpreter();
 		out.println(((bsh.Interpreter)interpreter).eval(expression));
	    }
	out.println("</pre>");
	}
	catch (Throwable e){
	    out.println("</pre>");
	    out.println("Error: " + e);
	    out.println("<pre>");
	    e.printStackTrace();
	    out.println("</pre>");
	}
	finally{
	    System.setOut(save);
	}
	out.println("</body></html>");
	out.flush();
	os.close();
    }

}
