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



package com.sun.ws.management.server.handler;

import com.sun.ws.management.server.Handler;
import com.sun.ws.management.server.HandlerContext;
import com.sun.ws.management.Management;
import java.util.HashMap;
import java.util.Iterator;
import org.dmtf.schemas.wbem.wsman._1.wsman.SelectorType;
import java.util.Map;
import javax.xml.bind.JAXBException;
import javax.xml.soap.SOAPException;
import javax.xml.bind.JAXBContext;
import com.sun.honeycomb.mgmt.common.Utils;

public abstract class TopHandler 
    implements Handler {

    private String implPackage = null;

    protected JAXBContext jaxbContext = null;
    protected JAXBContext evJaxbContext = null;

    protected TopHandler(String implPackage) 
        throws JAXBException {
        this.implPackage = implPackage;
        jaxbContext = JAXBContext.newInstance(implPackage);
        evJaxbContext = JAXBContext.newInstance("com.sun.honeycomb.mgmt.server");
    }
    
    protected Object getProvider(String classname) 
        throws ClassNotFoundException,
               InstantiationException,
               IllegalAccessException {

        Class klass = Class.forName(implPackage+"."+classname);
        return(klass.newInstance());
    }

    protected Map<String,String> decodeSelector(Management request) 
        throws JAXBException, SOAPException {
        if (request.getSelectors() == null) {
            return(null);
        }

        HashMap<String,String> result = new HashMap<String,String>();
        Iterator selectors = request.getSelectors().iterator();
        while (selectors.hasNext()) {
            SelectorType sel = (SelectorType)selectors.next();
            result.put(sel.getName(), (String)sel.getContent().get(0));
        }
        
        return(result);
    }

    protected abstract void load(Management request)
        throws JAXBException, InstantiationException, SOAPException;

    public abstract void handle(String action,
                       String resource,
                       HandlerContext context,
                       Management request,
                       Management response)
        throws Exception;
}
