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



package com.sun.honeycomb.client;

import com.sun.honeycomb.test.util.Log;

import com.sun.honeycomb.common.TestRequestParameters;
import com.sun.honeycomb.common.ArchiveException;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

class TestConnection extends Connection
{
    TestConnection(String cld, int port) 
        throws ArchiveException, IOException
    {
        super(cld, port);
    }

    TestConnection(String cld, int port, boolean reuseConnection) 
        throws ArchiveException, IOException
    {
        super(cld, port, reuseConnection);
    }

    protected Map getExtraQueryParameters()
    {
        Map extraParameters = new HashMap();

        String logTag = TestRequestParameters.getLogTag();
        if (logTag != null) {
            extraParameters.put(TestRequestParameters.PARAM_LOGTAG, logTag);
        }

        Integer layoutMapId = TestRequestParameters.getLayoutMapId();
        if (layoutMapId != null) {
            extraParameters.put(TestRequestParameters.PARAM_LAYOUT_MAP_ID, layoutMapId);
        }

        return extraParameters;
    }
}
