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



package com.sun.honeycomb.fs;

public class View {
    String name;
    String[] attributeNames;
    Representation representation;
    
    public View(String newName,
                String[] newAttributes) {
        name = newName;
        attributeNames = newAttributes;
        representation = null;
    }

    public View(String newName,
                String[] newAttributes,
                Representation newRepresentation) {
        name = newName;
        attributeNames = newAttributes;
        representation = newRepresentation;
    }

    public String getName() {
        return(name);
    }

    public String[] getAttributes() {
        return(attributeNames);
    }

    public Representation getRepresentation() {
        return(representation);
    }

    public String getAttributesString() {
        String result = attributeNames[0];

        for (int i=1; i<attributeNames.length; i++) {
            result += ", "+attributeNames[i];
        }

        return(result);
    }

    public String toString() {
        String result;

        result = "View name : "+name+"\n";
        result += "Attributes : "+getAttributesString()+"\n";
        result += "Representation: "+representation;
        
        return(result);
    }
}
