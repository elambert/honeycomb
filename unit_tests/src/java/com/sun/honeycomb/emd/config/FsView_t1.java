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

public class FsView_t1 extends MDTestcase {
    private static Logger log
        = Logger.getLogger(FsView_t1.class.getName());

    public FsView_t1(String name) {
        super(name);
    }
    
    // Filename can not reference to a non-existing Field.
    public void testFilenameFieldExistFail() throws EMDConfigException {
        String cfg = getConfigXml(null, viewsXml("${myName}.txt"), null);
        validateSchemaFail(cfg,
            "Field referenced by an attribute does not exist.");
    }
    
    // Attribute can not reference to a non-existing Field.
    public void testAttributeFieldsExistFail() throws EMDConfigException {
        String cfg = getConfigXml(schemaXml(null, fieldXml(1, "myName")),
                                  viewsXml("${myName}.txt"), null);
        validateSchemaFail(cfg,
            "Field referenced by an attribute does not exist.");
    }
    
    // Both filename and attribute exist.
    public void testViewFieldsExist() throws EMDConfigException {
        StringBuffer temp = new StringBuffer(fieldXml(1, "myName"));
        temp.append(fieldXml(1));
        String cfg = getConfigXml(schemaXml(null, temp),
                                  viewsXml("${myName}.txt"), null);
        validateSchema(cfg);
    }

    // Attribute can not reference to a non-queryable Field.
    public void testQueryableAttributeFieldFail() throws EMDConfigException {
        StringBuffer temp = new StringBuffer(fieldXml(1, "myName"));
        temp.append(fieldXml(1, MDNAME, "string", "64", "false"));
        String cfg = getConfigXml(schemaXml(null, temp),
                                  viewsXml("${myName}.txt"), null);
        validateSchemaFail(cfg,
            "Field referenced by an attribute is not queryable.");
    }

    // Attribute is not in the FsView context.
    public void testAttributeContextFail_t1() throws EMDConfigException {
        // Attribute is defined in the namespace context.
        String cfg = getConfigXml(schemaXml(null, attributeXml(1)));
        validateSchemaFail(cfg, 
            "Attribute is not in the FsView context.");
    }

    // Attribute is not in the FsView context.
    public void testAttributeContextFail_t2() throws EMDConfigException {
        // Attribute is defined in the Table context.
        String cfg = getConfigXml(null, null, 
                tablesXml(tableXml("myTable", attributeXml(1))));
        validateSchemaFail(cfg,
            "Attribute is not in the FsView context.");
    }
    
    // Attribute is not in the FsView context.
    public void testAttributeContextFail_t3() throws EMDConfigException {
        // Attribute is defined outside the FsView context.
        String cfg = getConfigXml(attributeXml(1));
        validateSchemaFail(cfg,
            "Attribute is not in the FsView context.");

    }

    // Testing filesonlyatleaflevel keyword 
    public void testFsViewFilesAtLeafLevel_t1() throws EMDConfigException {
        StringBuffer temp = new StringBuffer(fieldXml(1, "myName"));
        temp.append(fieldXml(1));
        String cfg = getConfigXml(schemaXml(null, temp),
                                  viewsXml("${myName}.txt", "true"), null);
        validateSchema(cfg);
    }

    // Testing filesonlyatleaflevel keyword 
    public void testFsViewFilesAtLeafLevel_t2() throws EMDConfigException {
        StringBuffer temp = new StringBuffer(fieldXml(1, "myName"));
        temp.append(fieldXml(1));
        String cfg = getConfigXml(schemaXml(null, temp),
                                  viewsXml("${myName}.txt", "false"), null);
        validateSchema(cfg);
    }

    // FsView tag without an eclosing <fsViews>
    public void testFsViewTagFail_t1() throws EMDConfigException {
        StringBuffer temp = new StringBuffer(fieldXml(1, "myName"));
        temp.append(fieldXml(1));
        String cfg = getConfigXml(schemaXml(null, temp),
                                  viewXml(1, "${myName}.txt"), null);
        validateSchemaFail(cfg,
            "Found a fsView tag without an eclosing <fsViews>.");
    }

    // cr 6447306
    // The "filename" attribute in FsView ignores "namespace" attribute
    public void testCR6447306() throws EMDConfigException {
        StringBuffer temp = new StringBuffer(fieldXml(1, "myName"));
        temp.append(fieldXml(1));
        String cfg = getConfigXml(
                schemaXml(nsXml("myNS", "true", "true", null, temp)),
                viewXml("myNS", "${myName}.txt"), null);
        validateSchema(cfg);
    }
}
