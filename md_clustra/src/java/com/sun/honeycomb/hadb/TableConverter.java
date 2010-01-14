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



package com.sun.honeycomb.hadb;

import java.util.ArrayList;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.DatabaseMetaData;
import java.sql.Statement;

import com.sun.honeycomb.common.NewObjectIdentifier;

import com.sun.honeycomb.emd.common.EMDException;

import com.sun.honeycomb.emd.config.RootNamespace;
import com.sun.honeycomb.emd.config.Table;
import com.sun.honeycomb.emd.config.EMDConfigException;

public class TableConverter {

    private static final Logger LOG = 
        Logger.getLogger(TableConverter.class.getName());

    protected DatabaseConverter parent;
    protected String configTableName;
    protected String newDBTableName;
    protected Table table;
    protected NewObjectIdentifier lastOid;


    public TableConverter(DatabaseConverter parent, 
                          String configTableName,
                          NewObjectIdentifier lastOid) {
        this.parent = parent;
        this.configTableName = configTableName;
        this.lastOid = lastOid;
        LOG.info("Creating TableConverter for table "+configTableName+
                 " at OID="+
                 (lastOid == null ? "<not specified>" :
                  lastOid.toHexString()));
    }


    public String getConfigTableName() {
        return configTableName;
    }

    public String getNewDBTableName() {
        return newDBTableName;
    }

    public Table getTable() {
        return table;
    }

    public NewObjectIdentifier getLastOid() {
        return lastOid;
    }

    public void setLastOid(NewObjectIdentifier lastOid) {
        this.lastOid = lastOid;
    }

    public void startConvert()
        throws EMDException {

        // This function is a no-op until we use threads during conversion
        table = RootNamespace.getInstance().getTable(configTableName);
        newDBTableName = 
            AttributeTable.getInstance().getTableName(table);
        if (LOG.isLoggable(Level.INFO)) {
            LOG.info("Starting TableConverter for table "+
                     configTableName+
                     " to new HADB table "+newDBTableName+
                     (lastOid == null ? "" :
                      " at OID="+lastOid.toHexString()));
        }
    }

}
