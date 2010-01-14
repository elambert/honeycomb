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

import org.mortbay.http.HttpOutputStream;
import java.io.Writer;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.http.HttpFields;
import java.io.IOException;
import java.io.OutputStreamWriter;
import com.sun.honeycomb.cm.NodeMgr;

public class AdminHandler
    extends ProtocolHandler {
    
    public AdminHandler(ProtocolService service) {
        super(service);
    }

    protected void handle(final String pathInContext,
                          final String pathParams,
                          final HttpRequest request,
                          final HttpResponse response,
                          final HttpFields trailer)
        throws IOException {

        HttpOutputStream out = (HttpOutputStream)response.getOutputStream();
        Writer output = new OutputStreamWriter(out);

        output.write("<html><head><title>Honeycomb emulator interface</title></head><body>");

        if ((pathInContext.length() > 0) && (pathInContext.substring(1).startsWith("stop"))) {
            stop(output);
            return;
        }

        output.write("<h2>List of running services :</h2><br><br>");
        output.write("<table border=\"2\"><tr><th>Service Name</th><th>Nb of running threads</th></tr>");
        
        NodeMgr.ServiceStatus[] svcs = NodeMgr.getInstance().status();
        for (int i=0; i<svcs.length; i++) {
            output.write("<tr><td>"+svcs[i].name+"</td><td><center>"+
                         svcs[i].nbThreads+"</center></td></tr>");
        }
        output.write("</table><br><br>");
        output.write("<a href=\"/admin/stop\">Click here to shutdown the emulator</a>");
        
        footer(output);
    }

    private void footer(Writer output)
        throws IOException {
        output.write("</body></html>");
        output.close();
    }

    private void stop(Writer output) 
        throws IOException {
        output.write("<h3><font color=\"red\">Shutting down the emulator</color></h3>");
        footer(output);
        NodeMgr.getInstance().shutdown();
    }

    /** Admin I/F doesn't care about HTTP/1.1 */
    boolean validateHttpVersion(String clientVersion){
        return true;
    }
}
