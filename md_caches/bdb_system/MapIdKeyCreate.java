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



import com.sleepycat.db.DbSecondaryKeyCreate;
import com.sleepycat.db.Db;
import com.sleepycat.db.Dbt;
import com.sleepycat.db.DbException;
import java.util.logging.Logger;
import java.io.IOException;

import com.sun.honeycomb.emd.common.EMDException;
import java.util.logging.Level;
import com.sleepycat.bdb.bind.tuple.TupleOutput;
import com.sun.honeycomb.common.NewObjectIdentifier;

public class MapIdKeyCreate
    implements DbSecondaryKeyCreate {

    private static final Logger LOG = Logger.getLogger(MapIdKeyCreate.class.getName());

    public MapIdKeyCreate() {
    }

    public int secondaryKeyCreate(Db secondary,
                                  Dbt key,
                                  Dbt data,
                                  Dbt result)
        throws DbException {
        NewObjectIdentifier oid = (NewObjectIdentifier)BDBCache.decodeDbt(key);
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Create an index entry for "+oid);
        }
        
        try {
            TupleOutput output = new TupleOutput();
            output.writeInt(oid.getLayoutMapId());
            byte[] bytes = output.toByteArray();
            result.setData(bytes);
            result.setSize(bytes.length);
        } catch (IOException e) {
            LOG.log(Level.SEVERE,
                    "Failed to encode the entry to the index ["
                    +e.getMessage()+"]",
                    e);
            return(Db.DB_DONOTINDEX);
        }

        return(0);
    }
}
