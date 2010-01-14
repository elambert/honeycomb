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



package com.sun.honeycomb.mgmt.client;

import javax.xml.soap.SOAPException;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import com.sun.ws.management.client.exceptions.FaultException;
import javax.xml.datatype.DatatypeConfigurationException;
import org.w3c.dom.Document;
import com.sun.ws.management.client.Resource;
import com.sun.ws.management.client.ResourceFactory;
import com.sun.ws.management.Message;
import com.sun.ws.management.client.TransferableResource;
import javax.xml.bind.JAXBContext;
import com.sun.ws.management.client.ResourceState;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import com.sun.honeycomb.mgmt.common.Utils;
import com.sun.honeycomb.mgmt.common.MgmtException;
import com.sun.ws.management.transfer.Transfer;
import com.sun.ws.management.Management;
import com.sun.ws.management.addressing.Addressing;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPBody;
import java.util.UUID;
import javax.xml.datatype.DatatypeFactory;
import com.sun.ws.management.transport.HttpClient;
import java.util.HashSet;
import org.dmtf.schemas.wbem.wsman._1.wsman.SelectorSetType;
import java.util.List;
import java.util.Iterator;
import java.util.HashMap;
import org.dmtf.schemas.wbem.wsman._1.wsman.SelectorType;
import javax.xml.soap.Detail;
import javax.xml.soap.DetailEntry;

public abstract class ClientData {

    private JAXBContext jaxbContext;

    private static final long DEFAULT_TIMEOUT = 240000;
    private static boolean messageInitialized = false;

    private static final int STATE_INITIAL = 0;
    private static final int STATE_RUNNING = 1;
    private static final int STATE_COMPLETED = 2;

    private static HashMap     contextMap = null;
    private static JAXBContext evJaxbContext = null;

    private static synchronized JAXBContext getJaxbContext(
        String packageName) throws JAXBException {

        JAXBContext res = null;

        if (contextMap == null) {
            contextMap = new HashMap();
        }
        res = (JAXBContext) contextMap.get(packageName);
        if (res == null) {
            res = JAXBContext.newInstance(packageName);
            contextMap.put(packageName, res);
        }
        return res;
    }

    private static synchronized JAXBContext getEvContext() 
        throws JAXBException {
        if (evJaxbContext == null) {
            evJaxbContext = 
              JAXBContext.newInstance("com.sun.honeycomb.mgmt.client");
        }
        return evJaxbContext;
    }

    private TransferableResource resource;

    protected ClientData() {
        resource = null;
        jaxbContext = null;
    }

    private void setResource(TransferableResource resource) {
        this.resource = resource;
    }

    private void setJaxbContext(JAXBContext jaxbContext) {
        this.jaxbContext = jaxbContext;
    }

    private static void initializeMessage() 
        throws SOAPException {
        if (messageInitialized) {
            return;
        }
        synchronized (ClientData.class) {
            if (!messageInitialized) {
                Message.initialize();
                messageInitialized = true;
            }
        }
    }
   
    public static ClientData fetch(String destination,
                                   String resourceURI,
                                   java.util.Map<String,String> selectors,
                                   String packageName)
        throws SOAPException, JAXBException, IOException, FaultException,
               DatatypeConfigurationException {


        initializeMessage();

        Document content = Message.newDocument();
        Resource[] resources = ResourceFactory.find(destination, resourceURI,
                                                    DEFAULT_TIMEOUT, selectors);
        ResourceState resState = ((TransferableResource)resources[0]).get();

        JAXBElement el = (JAXBElement) getJaxbContext(packageName).createUnmarshaller().unmarshal(resState.getDocument());

        ClientData result = (ClientData)el.getValue();
        result.setResource((TransferableResource)resources[0]);
        result.setJaxbContext(getJaxbContext(packageName));
        return(result);
    }

    protected abstract JAXBElement getJAXB();

    public void push() 
        throws MgmtException {
        if (resource == null) {
            throw new MgmtException("You have to fetch the object from the server before being able to push new values for it");
        }

        try {
            Document doc = Message.newDocument();
            jaxbContext.createMarshaller().marshal(getJAXB(), doc);
            resource.put(doc);
        } catch (JAXBException e) {
            MgmtException newe = new MgmtException(e.getMessage());
            newe.initCause(e);
            throw newe;
        } catch (SOAPException e) {
            MgmtException newe = new MgmtException(e.getMessage());
            newe.initCause(e);
            throw newe;
        } catch (IOException e) {
            MgmtException newe = new MgmtException(e.getMessage());
            newe.initCause(e);
            throw newe;
        } catch (FaultException e) {
            MgmtException newe = new MgmtException(e.getMessage());
            newe.initCause(e);
            throw newe;
        } catch (DatatypeConfigurationException e) {
            MgmtException newe = new MgmtException(e.getMessage());
            newe.initCause(e);
            throw newe;
        }
    }


    private ClientData doInvoke(String methodName,
                                Document args,
                                MethodCallback callback)
        throws MgmtException {


        if (resource == null) {
            throw new MgmtException("You have to fetch the object from the server before being able to push new values for it");
        }

        try {
            // Global variables

            String messageId = "uuid:"+UUID.randomUUID().toString();

            SelectorSetType selectorSet = resource.getSelectorSet();
            HashSet<SelectorType> selectors = null;
            if (selectorSet != null) {
                selectors = new HashSet<SelectorType>();
                List<SelectorType> sels = selectorSet.getSelector();
                for (Iterator iter = sels.iterator(); iter.hasNext();) {
                    SelectorType element = (SelectorType) iter.next();
                    selectors.add(element);
                }
            }

            int state = STATE_INITIAL;
            HCMGMTEvent lastEv = null;
            ClientData result = null;

            while (state < STATE_COMPLETED) {
                Transfer xf = new Transfer();
                if (state == STATE_INITIAL) {
                    xf.setAction(Utils.BASE_CUSTOM_ACTION_URI+methodName);
                } else {
                    xf.setAction(Utils.BASE_CUSTOM_ONGOING_ACTION_URI);
                }
                xf.setReplyTo(resource.getReplyTo());
                xf.setMessageId(messageId);
            
                Management mgmt = new Management(xf);
                mgmt.setTo(resource.getDestination());
                mgmt.setResourceURI(resource.getResourceUri());
                mgmt.setTimeout(DatatypeFactory.newInstance().newDuration(DEFAULT_TIMEOUT));
                if (selectors != null) {
                    mgmt.setSelectors(selectors);
                }
            
                if (state == STATE_INITIAL) {
                    if (args != null) {
                        mgmt.getBody().addDocument(args);
                    }
                } else {
                    Document doc = Message.newDocument();
                    try {
                        getEvContext().createMarshaller().marshal(lastEv.getJAXB(), doc);
                    } catch (JAXBException e) {
                        MgmtException newe = new MgmtException("Failed to serialize the arguments ["+
                                                               e.getMessage()+"]");
                        newe.initCause(e);
                        throw newe;
                    }
                    mgmt.getBody().addDocument(doc);
                }
            
                // Execute the request
                Addressing response = HttpClient.sendRequest(mgmt);
                if (response.getBody().hasFault()) {
                    SOAPFault fault = response.getBody().getFault();
                    Detail detail = fault.getDetail();
                    String exceptionMessage = fault.getFaultString();
                    Iterator it = detail.getDetailEntries();
                    while (it.hasNext()) {
                        DetailEntry entry = (DetailEntry) it.next();
                        if (entry.getValue() != null) {
                            exceptionMessage = entry.getValue();
                            break;
                        }
                    }
                    throw new FaultException(exceptionMessage);
                }

                SOAPBody body = response.getBody();
                Document bodyDoc = body.extractContentAsDocument();

                if (response.getAction().startsWith(
                        Utils.BASE_CUSTOM_ONGOING_RESPONSE_URI)) {
                    JAXBElement el = 
                      (JAXBElement)getEvContext().createUnmarshaller().unmarshal(bodyDoc);
                    lastEv = (HCMGMTEvent)el.getValue();
                    if (callback != null) {
                        lastEv.setMessage(callback.callback(lastEv.getMessage()));
                    } else {
                        lastEv.setMessage(null);
                    }
                    state = STATE_RUNNING;
                } else {
                    JAXBElement el = (JAXBElement)jaxbContext.createUnmarshaller().unmarshal(bodyDoc);
                    result = (ClientData)el.getValue();
                    state = STATE_COMPLETED;
                }
            }
            return(result);
            
        } catch (JAXBException e) {
            MgmtException newe = new MgmtException(e.getMessage());
            newe.initCause(e);
            throw newe;
        } catch (SOAPException e) {
            MgmtException newe = new MgmtException(e.getMessage());
            newe.initCause(e);
            throw newe;
        } catch (IOException e) {
            MgmtException newe = new MgmtException(e.getMessage());
            newe.initCause(e);
            throw newe;
        } catch (FaultException e) {
            MgmtException newe = new MgmtException(e.getMessage());
            newe.initCause(e);
            throw newe;
        } catch (DatatypeConfigurationException e) {
            MgmtException newe = new MgmtException(e.getMessage());
            newe.initCause(e);
            throw newe;

        }
    }

    protected ClientData invoke(String methodName,
                                Document args,
                                MethodCallback callback)
        throws MgmtException {
        return(doInvoke(methodName, args, callback));
    }

    protected ClientData invoke(String methodName,
                                ClientData args,
                                MethodCallback callback)
        throws MgmtException {
        
        Document doc = null;
        if (args != null) {
            doc = Message.newDocument();
            try {
                jaxbContext.createMarshaller().marshal(args.getJAXB(), doc);
            } catch (JAXBException e) {
                MgmtException newe = new MgmtException("Failed to serialize the arguments ["+
                                                       e.getMessage()+"]");
                newe.initCause(e);
                throw newe;
            }
        }
        return(doInvoke(methodName, doc, callback));
    }

}
