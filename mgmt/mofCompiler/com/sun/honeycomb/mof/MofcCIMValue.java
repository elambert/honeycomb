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

import java.io.Serializable;
import java.util.Vector;

/**
 * 
 * 
 * This class ...
 * 
 *
 * @author	Sun Microsystems, Inc.
 * @version 	1.4, 02/26/01
 */
public class MofcCIMValue implements Serializable {

    public final static int SIZE_SINGLE=-1;
    public final static int SIZE_UNLIMITED=-2;
    public boolean isArrayVal;
    public Vector  vVector;

    public MofcCIMValue() {
        isArrayVal = false;
        vVector = new Vector();
    }

    public boolean isArrayValue() {
        return isArrayVal; 
    }

    public void setIsArrayValue(boolean ArrayValue) {
        isArrayVal = ArrayValue; 
    }

    public String toString() {
        return(new String (
                           " isArrayVal: " + isArrayVal + "\n" +
                           vVector+"\n"));
    }

    public void addElement(Object obj) {
        vVector.addElement(obj);
    }

    public boolean contains(Object obj) {
        return(vVector.contains(obj));
    }

    public boolean isEmpty() {
        return(vVector.isEmpty());
    }

    public int size() {
        return(vVector.size());
    }

    public Object firstElement() {
        return(vVector.firstElement());
    }
}
