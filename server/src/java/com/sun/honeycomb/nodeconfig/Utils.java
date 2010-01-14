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

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class Utils {

    public static String[] getElements(Element root,
                                       String tagName,
                                       String attrName) {
        NodeList list = root.getElementsByTagName(tagName);
        String[] result = new String[list.getLength()];
        for (int i=0; i<list.getLength(); i++) {
            Element el = (Element)list.item(i);
            result[i] = (attrName == null)
                ? el.getNodeName()
                : el.getAttribute(attrName);
        }
        
        return(result);
    }

    public static Element[] getElements(Element root,
                                        String tagName) {
        NodeList list = root.getElementsByTagName(tagName);
        Element[] result = new Element[list.getLength()];
        for (int i=0; i<list.getLength(); i++) {
            result[i] = (Element)list.item(i);
        }
        
        return(result);
    }
    
    public static Element getElement(Element root,
                                     String tagName,
                                     String attrName,
                                     String attrValue) {
        if (root.getTagName().equals(tagName)) {
            if (attrName == null) {
                return(root);
            }
            if (root.getAttribute(attrName).equals(attrValue)) {
                return(root);
            }
        }

        NodeList list = root.getElementsByTagName(tagName);
        
        for (int i=0; i<list.getLength(); i++) {
            Element el = (Element)list.item(i);
            if (attrName == null) {
                return(el);
            }
            if (el.getAttribute(attrName).equals(attrValue)) {
                return(el);
            }
        }
        
        return(null);
    }
}
