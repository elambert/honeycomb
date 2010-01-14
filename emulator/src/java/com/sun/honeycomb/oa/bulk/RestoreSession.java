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



package com.sun.honeycomb.oa.bulk;

import com.sun.honeycomb.config.ClusterProperties;
import com.sun.honeycomb.emd.common.SysCacheException;
import com.sun.honeycomb.oa.bulk.stream.StreamReader;
import java.nio.channels.ReadableByteChannel;

/*
 * The emulator overrides the server version to remove the event logic.
 */
public class RestoreSession extends BaseRestoreSession {

    public static final String PROP_RESTORE_SESSION_IN_PROGRESS = 
                                "honeycomb.oa.bulk.restore.session.in.progress";

    public RestoreSession(ReadableByteChannel channel, 
                          String streamFormat, 
                          Callback callback,
                          int options) throws SessionException {
        super(channel, streamFormat, callback, options);
    }
    


    // These have no meaning for the emulator because it has no sys cache.
    public static void restoreInCourse() throws SysCacheException { 
    }

    boolean confirmStateBeforeRestore() throws SysCacheException {
        return true;
    }

    boolean confirmStateAfterRestore() throws SysCacheException {
        return true;
    }

    void setCorruptedState() {
    }

}
