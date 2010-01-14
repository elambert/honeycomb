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

import java.util.Map;
import java.util.HashMap;
import javax.wbem.cim.CIMClass;
import javax.wbem.cim.CIMException;
import java.util.ArrayList;
import java.util.Vector;
import javax.wbem.cim.CIMProperty;

public class KeyCache {

    private static KeyCache instance = null;

    public static synchronized KeyCache getInstance() {
        if (instance == null) {
            instance = new KeyCache();
        }
        return(instance);
    }

    private Map cache;

    public KeyCache() {
        cache = new HashMap();
    }

    public void load(CompilerBackend data) 
        throws CIMException {
        CIMClass[] classes = data.getAllClasses();
        for (int i=0; i<classes.length; i++) {
            loadClass(classes[i]);
        }
    }

    private void loadClass(CIMClass klass) 
        throws CIMException {
        CIMProperty[] keys = null;
        if (klass.getSuperClass() != null) {
            keys = resolve(klass.getSuperClass());
        }
        ArrayList newKeys = new ArrayList();
        Vector props = klass.getAllProperties();
        for (int i=0; i<props.size(); i++) {
            CIMProperty prop = (CIMProperty)props.get(i);
            if (prop.hasQualifier("Key")) {
                newKeys.add(prop);
            }
        }
        int length = newKeys.size();
        if (keys != null)
            length += keys.length;
        
        CIMProperty[] result = new CIMProperty[length];
        int index = 0;
        if (keys != null) {
            for (int i=0; i<keys.length; i++) {
                result[i] = keys[i];
            }
            index = keys.length;
        }
        for (int i=0; i<newKeys.size(); i++) {
            result[index] = (CIMProperty)newKeys.get(i);
            index++;
        }

        cache.put(klass.getName(), result);
    }

    public CIMProperty[] resolve(String name) 
        throws CIMException {
        CIMProperty[] result = (CIMProperty[])cache.get(name);
        if (result == null)
            throw new CIMException("The class "+name+" has not been loaded yet");
        return(result);
    }

}
