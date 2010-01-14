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



package com.sun.honeycomb.delete;

import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;
import java.io.File;
import com.sun.honeycomb.common.NewObjectIdentifier;
import com.sun.honeycomb.common.ArchiveException;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.LockMode;
 
public class OIDTagTable {
    
    private static OIDTagTable instance = null;
    
    public static void init() 
        throws DatabaseException {
        instance = new OIDTagTable();
    }
    public static synchronized void destroy() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    public static synchronized OIDTagTable getInstance() {
        if (instance == null) {
            throw new RuntimeException("OIDTagTable not initialized");
        }
        return(instance);
    }

    private Environment dbEnv;
    private Database mainDb;

    private OIDTagTable() 
        throws DatabaseException {
        File dbHome = new File(Constants.getRootDir()+"/db");
        if (!dbHome.exists()) {
            dbHome.mkdir();
        }

	    EnvironmentConfig envConfig = new EnvironmentConfig();
	    envConfig.setAllowCreate(true);
	    envConfig.setTxnNoSync(true);
	    envConfig.setCacheSize(0x800000); // 8MB
	    dbEnv = new Environment(dbHome, envConfig);

	    DatabaseConfig dbConfig = new DatabaseConfig();
	    dbConfig.setAllowCreate(true);
	    mainDb = dbEnv.openDatabase(null, "main", dbConfig);
    }

    private void close() {
        if (mainDb != null) {
            try {
                mainDb.close();
                mainDb = null;
            } catch (DatabaseException e) {
            }
        }
         
        if (dbEnv != null) {
            try {
                dbEnv.close();
                dbEnv = null;
            } catch (DatabaseException e) {
            }
        }
    }

    public void register(String tag,
                         NewObjectIdentifier oid) 
        throws ArchiveException {
        try {
            DatabaseEntry key = new DatabaseEntry(tag.getBytes());
            DatabaseEntry value = new DatabaseEntry(oid.toString().getBytes());
            mainDb.put(null, key, value);
        } catch (DatabaseException e) {
            ArchiveException newe = new ArchiveException("OIDTagTable.register failed ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
    }

    public NewObjectIdentifier resolve(String tag)
        throws ArchiveException {

        try {
            DatabaseEntry key = new DatabaseEntry(tag.getBytes());
            DatabaseEntry value = new DatabaseEntry();
            OperationStatus status = mainDb.get(null, key, value, LockMode.DIRTY_READ);
            if (status == OperationStatus.NOTFOUND) {
                throw new ArchiveException("OID not found ["+
                                           tag+"]");
            }
            return( new NewObjectIdentifier(new String(value.getData())) );
        } catch (DatabaseException e) {
            ArchiveException newe = new ArchiveException("OIDTagTable.resolve failed ["+
                                                         e.getMessage()+"]");
            newe.initCause(e);
            throw newe;
        }
    }
}
