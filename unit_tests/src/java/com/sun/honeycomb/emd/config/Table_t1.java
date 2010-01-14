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



package com.sun.honeycomb.emd.config;

import com.sun.honeycomb.emd.config.RootNamespace;
import java.util.logging.Logger;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

public class Table_t1 extends MDTestcase {
    private static Logger log
        = Logger.getLogger(Table_t1.class.getName());

    public Table_t1(String name) {
        super(name);
    }

    // Two columns can not reference to the same Field.
    public void testTwoColumnFieldFail() throws EMDConfigException {
        StringBuffer temp = new StringBuffer(tableXml("myTbale1"));
        temp.append(tableXml("myTbale2"));

        String cfg = getConfigXml(
                schemaXml(null, fieldXml(1)), null, tablesXml(temp));
        validateSchemaFail(cfg,
            "Field can not be referenced by more than one column.");
    }
    
    // Column can not reference a non-existing Field.
    public void testColumnFieldExistFail() throws EMDConfigException {
        String cfg = getConfigXml(null, null, tablesXml(1));
        validateSchemaFail(cfg,
            "Field referenced by a Column does not exist.");
    }

    // Column can not reference a non-queryable Field.
    public void testQueryableColumnFieldFail() throws EMDConfigException {
        String cfg = getConfigXml(schemaXml(null, fieldXml(
                                        1, MDNAME, "string", "64", "false")),
                           null, tablesXml(tableXml("myTable", 1, MDNAME)));
        validateSchemaFail(cfg,
            "Field referenced by a Column is not queryable.");
    }

    // OK to process the same table definition multiple times.
    public void testSameTable() throws EMDConfigException {
        String cfg = getConfigXml(schemaXml(null, fieldXml(1)), 
                null, tablesXml(1));
        RootNamespace rn = validateSchema(cfg);
        validateSchema(rn, cfg);
    }

    // Fial to pass the table compatIbility check.
    public void testTableCompatibilityFail_t1() throws EMDConfigException {
        String temp = getConfigXml(schemaXml(null, fieldXml(1)), 
                null, tablesXml(1));
        RootNamespace rn = validateSchema(temp);
        String cfg = getConfigXml(schemaXml(null, fieldXml(2)), 
                null, tablesXml(2));
        validateSchemaFail(rn, cfg,
                "Table can not be extended.");
    }

    // The number of columns in a table can not excceed 254.
    public void testTableColumnCountFail() throws EMDConfigException {
        String cfg = getConfigXml(
                schemaXml(null, fieldXml(255)), null, tablesXml(255));
        validateSchemaFail(cfg,
            "The number of columns in a table can not excceed 255.");
    }

    // OK if a table has 254 columns.
    public void testTableColumnCount() throws EMDConfigException {
        String cfg = getConfigXml(
                schemaXml(null, fieldXml(254, "string", "10")), 
                null, tablesXml(254));
        validateSchema(cfg);
    }

    // The row length of a table can not excceed 8080.
    public void testTableRowLengthFail() throws EMDConfigException {
        String cfg = getConfigXml(
                schemaXml(null, fieldXml(254, "string", "32")), 
                null, tablesXml(254));
        validateSchemaFail(cfg,
            "The row length of a table can not excceed 8080.");
    }

    // Column is not in the Table context.
    public void testColumnContextFail_t1() throws EMDConfigException {
        // column is defined in the namespace context.
        String cfg = getConfigXml(schemaXml(null, columnXml(1)));
        validateSchemaFail(cfg,
            "Column is not in the Table context.");
    }

    // Column is not in the Table context.
    public void testColumnContextFail_t2() throws EMDConfigException {
        // column is defined in the FsView context.
        StringBuffer temp = new StringBuffer(fieldXml(1, "myName"));
        temp.append(fieldXml(1, MDNAME, "string", "64", "false"));
        String cfg = getConfigXml(schemaXml(null, temp),
              viewsXml(viewXml("myView", "${myName}.txt", columnXml(1))), null);

        validateSchemaFail(cfg,
            "Column is not in the Table context.");
    }

    // Column is not in the Table context.
    public void testColumnContextFail_t3() throws EMDConfigException {
        // column is defined outside the table context.
        String cfg = getConfigXml(columnXml(1));
        validateSchemaFail(cfg,
            "Column is not in the Table context.");
    }

    // Table tag without an enclosing <tables>
    public void testTableTagFail_t1() throws EMDConfigException {
        String cfg = getConfigXml(
                schemaXml(null, fieldXml(1)), null, tableXml(1));
        validateSchemaFail(cfg,
            "Found a table tag without an enclosing <tables>.");
    }

    // cr 6523713
    // Unable to differentiate between autogenerated and user defined tables
    public void testCR6523713() throws EMDConfigException {
        String temp = getConfigXml(schemaXml(null, fieldXml(1, "myFd")));
        RootNamespace rn = validateSchema(temp);
        File f = null;
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream("temp.out"), "UTF-8"));
            rn.export(out, false);
            out.flush();
            out.close();
        } catch (IOException e) {
            throw new EMDConfigException(e.getMessage());
        } finally {
            if (out != null) {
                try { out.close(); }  catch (IOException e) {}
                out = null;
            }
        }
        f = new File("temp.out");
        rn = new RootNamespace();
        try {
            rn.loadConfigFile(f, false);
        } finally {
            if (null != f) {
                f.delete();
            }
        }
        Table t = rn.getTable("myFd");
        if (null == t) {
            fail("testCR6523713 failed: cannot find autogenerated table.");
        }
        if (!t.isAutoGenerated()) {
            fail("testCR6523713 failed: autogenerated flag is cleared.");
        }
    }
}
