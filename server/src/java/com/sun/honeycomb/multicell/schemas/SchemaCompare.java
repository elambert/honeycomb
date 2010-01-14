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



package com.sun.honeycomb.multicell.schemas;

import java.util.Map;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;
import java.io.File;
import java.io.Writer;
import java.io.FileWriter;
import java.io.BufferedWriter;

import com.sun.honeycomb.emd.config.*;


public class SchemaCompare {

    private static final String TMP_SCHEMA_MASTER =  "/tmp/master.xml";
    private static final String TMP_SCHEMA_REMOTE =  "/tmp/remote.xml";

    private static transient final Logger logger = 
        Logger.getLogger(SchemaCompare.class.getName());

    private RootNamespace rootNamespaceMaster = null;
    private RootNamespace rootNamespaceLocal = null;

    public SchemaCompare(RootNamespace rootNamespace, boolean debug) 
        throws EMDConfigException {

        rootNamespaceMaster = rootNamespace;
        if (debug) {
            printSchema(rootNamespace, TMP_SCHEMA_MASTER);
        }
        rootNamespaceLocal = RootNamespace.getInstance();
        if (debug) {
            printSchema(rootNamespace, TMP_SCHEMA_REMOTE);
        }
    }

    public void checkSchema() 
        throws EMDConfigException {
        checkNamepsace();
        checkFSView();
        checkTables();
    }

    // debug only
    private void printSchema(RootNamespace ns, String filename) {
        Writer out = null;
        try {
            File tmpFile = new File(filename);
            if (tmpFile.exists()) {
                tmpFile.delete();
            }
            out = new BufferedWriter(new FileWriter(tmpFile));
            ns.export(out, true);
        } catch(IOException ioe) {
            logger.severe("failed to print the schema into " +
                filename);
        } finally {
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void checkNamepsace()
        throws EMDConfigException {

        // check first level 
        rootNamespaceMaster.compareDirect(rootNamespaceLocal);

        ArrayList childrenA = new ArrayList();
        rootNamespaceMaster.getChildren(childrenA, true);

        ArrayList childrenB = new ArrayList();
        rootNamespaceLocal.getChildren(childrenB, true);
        if (childrenA.size() != childrenB.size()) {
            throw new EMDConfigException("Number of namespace in the schema " +
              "varies between current hive and remote cell");
        }
        for (int i = 0; i < childrenA.size(); i++) {
            Namespace curNamespaceA = (Namespace) childrenA.get(i);
            Namespace curNamespaceB =
                rootNamespaceLocal.resolveNamespace(
                    curNamespaceA.getQualifiedName());
            if (curNamespaceB == null) {
                throw new  EMDConfigException("Namespace " + 
                  curNamespaceA.getQualifiedName() +
                  "does not exist in the schema " +
                  "of the remote cell");
            }
            curNamespaceA.compareDirect(curNamespaceB);
        }
    }

    private void checkFSView()
        throws EMDConfigException {

        if (rootNamespaceMaster.getNbViews() != 
            rootNamespaceLocal.getNbViews()) {
            throw new EMDConfigException("Number of views in the schema " +
              "differ between the hive and the remote cell");
        }
        FsView [] viewsA = rootNamespaceMaster.getViews();
        FsView [] viewsB = rootNamespaceLocal.getViews();
        for (int i = 0; i < viewsA.length; i++) {
            FsView curView = viewsA[i];
            boolean found = false;
            for (int j = 0; j < viewsB.length; j++) {
                if (curView.getName().equals(viewsB[j].getName())) {
                    curView.compareDirect(viewsB[j]);
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new EMDConfigException("View " + curView.getName() +
                  " does not exist in the schema " +
                  "of the remote cell");
            }
        }
    }

    private void checkTables()
        throws EMDConfigException {

        if (rootNamespaceMaster.getNbTables() !=
            rootNamespaceLocal.getNbTables()) {
            throw new EMDConfigException("Number of tables in the schema " +
              "differ between the hive and the remote cell");
        }
        Table [] tablesA = rootNamespaceMaster.getTables();
        Map tablesB = rootNamespaceLocal.getTablesMap();
        for (int i = 0; i < tablesA.length; i++) {

            Table curTable = 
                (Table) tablesB.get(tablesA[i].getName().toUpperCase());
            if (curTable == null) {
                throw new EMDConfigException("Table " + tablesA[i].getName() +
                  " is missing in the schema " +
                  "of the remote cell");
            }
            tablesA[i].compareDirect(curTable);
        }
    }
}
