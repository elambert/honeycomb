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



import java.io.IOException;
import java.nio.ByteBuffer;

import com.sleepycat.db.Db;
import com.sleepycat.db.DbBtreeCompare;
import com.sleepycat.db.Dbt;

import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.common.NewObjectIdentifier;

public class ATimeCompare implements DbBtreeCompare {

    /**
     * private  Helper class.
     */
    private class Info {
        public long atime;
        public NewObjectIdentifier oid;
    }

    public int compare(Db arg0, Dbt t1, Dbt t2) {
        try {
            Info info1 = extractInfo(t1);
            Info info2 = extractInfo(t2);
            /*
             * If the aTimes are equals then thats when we really on the OID 
             * for sorting.
             */
            if (info1.atime == info2.atime) {
                return info1.oid.compareTo(info2.oid);
            } else if (info1.atime < info2.atime)
                return -1;
            else
                return 1;
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse key correctly.", e);
        }
    }

    private Info extractInfo(Dbt t) throws IOException {
        Info result = new Info();
        byte[] bytes = t.getData();
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        result.oid = (NewObjectIdentifier)
                                    new ByteBufferCoder(buffer).decodeCodable();
        result.atime = buffer.getLong();
        return result;
    }
}
