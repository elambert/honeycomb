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



import SQLite.Callback;
import com.sun.honeycomb.emd.remote.MDOutputStream;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.honeycomb.emd.common.EMDException;
import com.sun.honeycomb.emd.common.QueryMap;
import com.sun.honeycomb.emd.common.HexObjectIdentifier;
import com.sun.honeycomb.emd.common.MDHit;
import com.sun.honeycomb.emd.config.Field;

public class SQLiteCallback
    implements Callback {
    
    public static final byte CALLBACK_SIMPLE_QUERY       = 1;
    public static final byte CALLBACK_QUERY_PLUS         = 2;
    public static final byte CALLBACK_SELECT_UNIQUE      = 3;

    private static final Logger LOG = Logger.getLogger(SQLiteCallback.class.getName());
    
    private byte type;
    private int resultType;
    private MDOutputStream output;
    private int maxResults;
    private String tag;
    private int nbrResults;
    private String[] cols;

    public SQLiteCallback(byte nType,
                          MDOutputStream nOutput,
                          int nMaxResults,
                          String nTag) {
        this(nType, 0, nOutput, nMaxResults, null, nTag);
    }

    public SQLiteCallback(byte nType,
                          int nResultType,
                          MDOutputStream nOutput,
                          int nMaxResults,
                          String[] nCols,
                          String nTag) {
        type = nType;
        resultType = nResultType;
        output = nOutput;
        maxResults = nMaxResults;
        cols = nCols;
        tag = nTag;
        nbrResults = 0;
    }

    public void columns(String[] coldata) {
        if (cols == null) {
            cols = coldata;
        }
    }

    public void types(String[] types) {
        // Unused
    }
    
    public boolean newrow(String[] rowdata) {
        
        try {
            
            switch (type) {
            case CALLBACK_SIMPLE_QUERY: {
                String oid = rowdata[0];
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Got a result for oid "+oid+" ["+
                             tag+"]");
                }
                output.sendObject(new MDHit(oid, null));
            } break;

            case CALLBACK_QUERY_PLUS: {
                String oid = rowdata[0];
                String[] copy = new String[rowdata.length];
                System.arraycopy(rowdata, 0, copy, 0, rowdata.length);
                QueryMap map = new QueryMap(cols, copy);
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Got a result for oid "+oid+" ["+
                             tag+"]");
                }
                output.sendObject(new MDHit(oid, map));
            } break;

            case CALLBACK_SELECT_UNIQUE:
                if ( (rowdata[0] != null) && (rowdata[0].length() > 0) ) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("Got a result with value "+rowdata[0]+" ["+
                                 tag+"]");
                    }
                    Object result = null;
                    switch (resultType) {
                        //                     case Field.TYPE_BYTE:
                        //                         result = new Byte(rowdata[0]);
                        //                         break;
                        
                    case Field.TYPE_LONG:
                        try {
                            result = new Long(rowdata[0]);
                        } catch (NumberFormatException ignored) {
                        }
                        break;

                    case Field.TYPE_DOUBLE:
                        try {
                            result = new Double(rowdata[0]);
                        } catch (NumberFormatException ignored) {
                        }
                        break;
                        
                    default:
                        result = rowdata[0];
                    }
                     
                    if (result != null) {
                        output.sendObject(result);
                    }
                }
                break;
            }

        } catch (EMDException e) {
            LOG.log(Level.SEVERE,
                    "Failed to fetch results from the SQLite database ["+
                    e.getMessage()+"]",
                    e);
        }

        ++nbrResults;
        return( (maxResults != -1) && (nbrResults >= maxResults) );
    }
}
