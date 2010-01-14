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



import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sleepycat.db.Db;
import com.sleepycat.db.DbException;
import com.sleepycat.db.DbSecondaryKeyCreate;
import com.sleepycat.db.Dbt;
import com.sun.honeycomb.coding.ByteBufferCoder;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.SystemMetadata;

public class ATimeKeyCreate implements DbSecondaryKeyCreate {

    private static final Logger LOG = Logger.getLogger(ATimeKeyCreate.class.getName());

    public ATimeKeyCreate() {
        
    }

    public int secondaryKeyCreate(Db secondary, Dbt key, Dbt data, Dbt result)
            throws DbException {
       
        NewObjectIdentifier oid = (NewObjectIdentifier) BDBCache.decodeDbt(key);
        SystemMetadata systemMetadata = (SystemMetadata) BDBCache.decodeDbt(data);
        long aTime = 0;
        
        aTime = systemMetadata.getCTime();

        if (systemMetadata.getDTime() > aTime) {
            aTime = systemMetadata.getDTime();
        }

	/********************************************************
	 *
	 * Bug 6554027 - hide retention features
	 *
	 *******************************************************/
	/*
        if (systemMetadata.getExtensionModifiedTime() > aTime) {
            aTime = systemMetadata.getExtensionModifiedTime();
        }
	*/

        createDbt(result, oid, aTime);
       
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Create an ATime index entry for " + oid);
        }

        return (0);
    }
    
    public static void createDbt(Dbt result, NewObjectIdentifier oid, long atime) { 
        ByteBuffer buffer = ByteBuffer.allocate(4 + BDBCache.SIZE_OBJECTID);
        new ByteBufferCoder(buffer).encodeCodable(oid);
        buffer.putLong(atime);
        result.setData(buffer.array());
        result.setSize(buffer.position());
    }
}
