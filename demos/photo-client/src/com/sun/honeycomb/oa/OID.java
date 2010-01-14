package com.sun.honeycomb.oa;

import com.sun.honeycomb.adapter.UID;

import com.sun.honeycomb.client.ObjectIdentifier;

public class OID implements UID{

    ObjectIdentifier oid;
    OID (ObjectIdentifier oid){
        this.oid = oid;
    }

    public String toString(){
        return oid.toString();
    }
}

