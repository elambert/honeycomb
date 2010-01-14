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



package com.sun.honeycomb.oa;

import java.io.File;
import java.io.FileFilter;
import java.util.logging.Logger;

import com.sun.honeycomb.disks.DiskId;
import com.sun.honeycomb.disks.Disk;
import com.sun.honeycomb.diskmonitor.DiskProxy;
import com.sun.honeycomb.common.NewObjectIdentifier;

/**
 * For info about this class, see the real one under trunk/server.
 */
public interface OAServerIntf {

    public int[] listFragNumbers(NewObjectIdentifier oid, DiskId diskId);

    public class OAServer implements OAServerIntf {
        private static final Logger log = Logger.getLogger(OAServer.class.getName());

        public int[] listFragNumbers(NewObjectIdentifier oid, DiskId diskId) {
            Disk disk = DiskProxy.getDisk(diskId);
            if (disk == null) {
                throw new IllegalArgumentException("invalid disk " + diskId);
            }
            FragNumberFilter fragsFilter = new FragNumberFilter(oid);
            File[] ls = new File(Common.makeDir(oid, disk)).listFiles(fragsFilter);
            if (ls == null) {
                return null;
            }
            int[] res = new int[ls.length];
            for (int i = 0; i < ls.length; i++) {
                 res[i] = Common.extractFragNumFromFilename(ls[i].getName());
                 assert(res[i] > 0);
            }
            return res;
        }

        private static class FragNumberFilter implements FileFilter {
            String oidStr;
        
            public FragNumberFilter(NewObjectIdentifier oid) {
                oidStr = oid.toString();
            }
            public boolean accept(File f) {
                return (f.isFile() && 
                        !f.isHidden() &&
                        f.getName().startsWith(oidStr) &&
                        Common.extractFragNumFromFilename(f.getName()) >= 0);
            }
        }
    }

    public class Proxy {
        public static OAServerIntf getAPI(int nodeId) {
            return new OAServer();
        }
    }
}
