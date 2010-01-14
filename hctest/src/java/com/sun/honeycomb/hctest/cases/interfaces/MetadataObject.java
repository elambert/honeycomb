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



package com.sun.honeycomb.hctest.cases.interfaces;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.client.*;

/**
 * MetadataObject stores info about a metadata object that is part of the
 * scenarios tests.
 */
public class MetadataObject {
    public HashMap hm;
    public String metadataOID;
    // XXX metadata hashes are going away
    public String computedMetadataHash;
    public String returnedMetadataHash;
    public SystemRecord sr;
    public boolean deleted;
    public boolean queriable;
    
    public MetadataObject() {
        hm = new HashMap();
        metadataOID = null;
        computedMetadataHash = null;
        returnedMetadataHash = null;
        sr = null;
        deleted = false;
        queriable = false;
    }

    public MetadataObject(NameValueRecord nvr) throws HoneycombTestException {
        this();

        if (nvr == null) {
            throw new HoneycombTestException("nvr is unexpectedly null");
        }

        // XXX hashes?
        sr = nvr.getSystemRecord();
        metadataOID = (sr.getObjectIdentifier()).toString();

        String keys[] = nvr.getKeys();
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            hm.put(key, nvr.getAsString(key));
        }
    }

    public String toString() {
        return ("metadataOID " + metadataOID + "; deleted=" + deleted +
            "; queriable=" + queriable +
            // For now, MD hash isn't meaningful
            // "; computedMetadataHash " + computedMetadataHash +
            // "; returnedMetadataHash " + returnedMetadataHash +
            "; map " + hm);
    }

    public String statusString() {
        String s = "";

        if (deleted) {
            s += "deleted";
        } else {
            s += "active";
        }

        s+= ",";

        if (queriable) {
            s += "queriable";
        } else {
            s += "non-queriable";
        }

        return (s);
    }

    public String IDString() {
        return (metadataOID);
    }

    public String summaryString() {
        return (metadataOID + "(" + statusString() + ")");
    }

    public void put(Object o, Object p) {
        hm.put(o, p);
    }

    public String hashString() {
        return ("MetadataHash returned" + HoneycombInterface.DELIM +
            returnedMetadataHash + HoneycombInterface.DELIM +
            "MetadataHash computed" + HoneycombInterface.DELIM +
            computedMetadataHash);
    }

    public String queryString() {
        return (HoneycombInterface.MD_INCARNATION_FIELD + "='" + 
              hm.get(HoneycombInterface.MD_INCARNATION_FIELD).toString() + "'");
    }

    // Copied/modified from NameValueRecord.java v.2752
    public String[] getKeys() {
        int size = (hm != null) ? hm.size() : 0;
        String[] result = null;

        if (size > 0) {
            result = new String[size];
            Iterator keys = hm.keySet().iterator();
            for (int i = 0; keys.hasNext(); i++) {
                result[i] = (String)keys.next();
            }
        }

        return result;
    }

}
