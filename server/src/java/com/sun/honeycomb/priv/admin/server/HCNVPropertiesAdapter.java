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



package com.sun.honeycomb.priv.mgmt.server;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.w3c.dom.Document;
import com.sun.honeycomb.mgmt.server.EventSender;
import com.sun.honeycomb.mgmt.common.MgmtException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.cm.cluster_membership.ServerConfigException;


public class HCNVPropertiesAdapter 
    implements  HCNVPropertiesAdapterInterface {

    private static transient final Logger logger = 
      Logger.getLogger(HCNVPropertiesAdapter.class.getName());

    public void loadHCNVProperties()
        throws InstantiationException {

    }

    public BigInteger getDummy() throws MgmtException {
        return BigInteger.valueOf(0);
    }

    public BigInteger setProperties(HCNameValuePropArray props)
        throws MgmtException {

        Map map = new HashMap();
        List<HCNameValueProp> list = props.getProperties();
        Iterator it = list.iterator();
        while (it.hasNext()) {
            HCNameValueProp curProp = (HCNameValueProp) it.next();
            String name =  curProp.getName();
            String value = curProp.getValue();
            map.put(name, value);
        }
        try {
            ClusterProperties.getInstance().putAll(map);
        } catch (ServerConfigException sve) {
            logger.log(Level.SEVERE, "failed to set the properties", sve);
            throw new MgmtException("Failed to set the properties");
        }
        return BigInteger.valueOf(0);
    }

}
