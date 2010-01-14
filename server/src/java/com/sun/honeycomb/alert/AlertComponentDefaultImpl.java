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



package com.sun.honeycomb.alert;

import java.util.HashMap;
import java.util.ArrayList;

//
// Default implementation for the 'root' and 'node' AlertComponent.
//
public abstract class AlertComponentDefaultImpl implements AlertComponent
{
    protected HashMap   childrenMap;
    protected ArrayList childrenVector;
    protected int       nbChildren;

    protected String    tag;
    protected int       type;

    AlertComponentDefaultImpl(AlertProperty prop) {
        nbChildren = 0;
        tag = prop.getName();
        type = prop.getType();

        childrenMap = new HashMap();
        childrenVector = new ArrayList();
    }

    public int getNbChildren() {
        return nbChildren;
    }

    // Default implementation for the interface.
    public abstract AlertProperty getPropertyChild(int index)
        throws AlertException;
    public abstract AlertComponent getPropertyValueComponent(String property)
        throws AlertException;

    public boolean getPropertyValueBoolean(String property)
       throws AlertException {
        throw new AlertException("Not implemented");
    }

    public int getPropertyValueInt(String property) 
        throws AlertException {
        throw new AlertException("Not implemented");
    }

    public long getPropertyValueLong(String property)
        throws AlertException {
        throw new AlertException("Not implemented");
    }

    public float getPropertyValueFloat(String property)
        throws AlertException {
        throw new AlertException("Not implemented");
    }

    public double getPropertyValueDouble(String property)
        throws AlertException {
        throw new AlertException("Not implemented");
    }

    public String getPropertyValueString(String property)
        throws AlertException {
        throw new AlertException("Not implemented");
    }
}
