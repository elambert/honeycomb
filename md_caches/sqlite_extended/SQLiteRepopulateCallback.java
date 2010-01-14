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
import java.util.logging.Logger;
import SQLite.Database;
import java.util.logging.Level;

public class SQLiteRepopulateCallback
    implements Callback {
    
    private Database outputDB;
    private String commandPrefix;
    
    private static final Logger LOG = Logger.getLogger(SQLiteRepopulateCallback.class.getName());

    public SQLiteRepopulateCallback(Database nOutputDB) {
        outputDB = nOutputDB;
    }

    public void columns(String[] coldata) {
        StringBuffer command = new StringBuffer();
        command.append("insert into ");
        command.append(SQLiteExtended.SQLITE_MAIN_TABLE);
        command.append("(");
        for (int i=0; i<coldata.length; i++) {
            if (i>0) {
                command.append(", ");
            }
            command.append(coldata[i]);
        }
        command.append(") values (");
        commandPrefix = command.toString();
    }

    public void types(String[] types) {
        // Unused
    }

    public boolean newrow(String[] rowdata) {
        StringBuffer command = new StringBuffer(commandPrefix);
        for (int i=0; i<rowdata.length; i++) {
            if (i>0) {
                command.append(", ");
            }
            if (rowdata[i] != null) {
                command.append("'"+SQLiteExtended.normalizeString(rowdata[i])+"'");
            } else {
                command.append("''");
            }
        }
        command.append(")");

        try {
            outputDB.exec(command.toString(), null);
        } catch (SQLite.Exception e) {
            LOG.log(Level.SEVERE,
                    "Failed to populate the new database ["+
                    e.getMessage()+"]",
                    e);
            // Give up
            return(true);
        }

        return(false);
    }
}
