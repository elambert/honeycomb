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

public class RootNamespace_t1 extends MDTestcase {
    private static Logger log
        = Logger.getLogger(RootNamespace_t1.class.getName());

    public RootNamespace_t1(String name) {
        super(name);
    }

    ///////////////////////////////////////
    // Validate attribute name.
    ///////////////////////////////////////
    
    // Name can not be longer than 63 characters.
    public void testNameFail_t1() throws EMDConfigException {
        String nameTooLong = "testloooooooooooooooooooooooooo" + 
                          "oooooooooooooooooooooooooooooogname";
        String cfg = getConfigXml(schemaXml(nsXml(nameTooLong)));
        validateSchemaFail(cfg, "Name can not be longer than 63 characters");
    }

    // Name can not be an empty string.
    public void testNameFail_t2() throws EMDConfigException {
        String emptyName = "    ";
        String cfg = getConfigXml(schemaXml(nsXml(emptyName)));
        validateSchemaFail(cfg, "Name can not be an empty string");
    }

    // Name conatins Unicode supplemental chars
    /* Currently it is a failure.
    public void testNameFail_t3() throws EMDConfigException {
        String name = "bad\ud800name";
        String cfg = getConfigXml(schemaXml(nsXml(name)));
        validateSchemaFail(cfg,
                "Name can not conatin Unicode supplemental chars.");
    }
    */
    
    // Successful case for name. 
    public void testName_t1() throws EMDConfigException {
        String name = "good_name";
        String cfg = getConfigXml(schemaXml(nsXml(name)));
        validateSchema(cfg);
    }

    // Successful case for name with Unicode chars. 
    public void testName_t2() throws EMDConfigException {
        String name = "good\u0393name";
        String cfg = getConfigXml(schemaXml(nsXml(name)));
        validateSchema(cfg);
    }

    // Namespace name can not contain '.'.
    public void testNamespaceNameFail_t1() throws EMDConfigException {
        String badNSName = "my.test";
        String cfg = getConfigXml(schemaXml(nsXml(badNSName)));
        validateSchemaFail(cfg,
            "Namespace name can not contain '.'");
    }

    // Committed namespace must be extensible to add sub-namespace. 
    public void testNonextensibleNSFail_t1() throws EMDConfigException {
        
        String setup = getConfigXml(schemaXml(nsXml(
                        "myNS", "true", "false", null, fieldXml(1))));
        RootNamespace rn = validateSchema(setup);

        String cfg = getConfigXml(schemaXml(nsXml(
                       "myNS", "true", "false", nsXml("myNS2"), null)));
        validateSchemaFail(rn, cfg,
            "Committed namespace must be extensible to add sub-namespace.");
    }

    // Committed namespace must be extensible to add field. 
    public void testNonextensibleNSFail_t2() throws EMDConfigException {
        
        String setup = getConfigXml(schemaXml(nsXml(
                        "myNS", "true", "false", null, fieldXml(1))));
        RootNamespace rn = validateSchema(setup);

        String cfg = getConfigXml(schemaXml(nsXml(
                    "myNS", "true", "false", null, fieldXml(1, "myField2"))));
        validateSchemaFail(rn, cfg,
            "Committed namespace must be extensible to add field.");
    }

    // OK if add fields or sub-namespace to extensible namespace.
    public void testExtensibleNS() throws EMDConfigException {

        String setup = getConfigXml(schemaXml(nsXml("myNS")));
        RootNamespace rn = validateSchema(setup);

        String cfg = getConfigXml(schemaXml(nsXml(
                       "myNS", "true", "true", nsXml("myNS2"), 
                       fieldXml(1, "myField1"))));
        validateSchema(rn, cfg);
    }
   
    // Namespace can not have empty non-extensible sub-namespace.
    public void testNonExtEmptyNSFail() throws EMDConfigException {
        String cfg = getConfigXml(schemaXml(nsXml(
                        "myNS", "true", "false", null, null)));
        RootNamespace rn = validateSchema(cfg);
        if (null != rn.getChild("myNS")) {
            fail("Namespace can not have empty non-extensible sub-namespace.");
        }
    }

    // OK to change namespace from extensible to non-extensible.
    public void testNSToNonExtensible()throws EMDConfigException {
        RootNamespace rn = validateSchema(
                getConfigXml(schemaXml(nsXml("myNS"))));
        String cfg = getConfigXml(schemaXml(
                    nsXml("myNS", "true", "false", null, null)));
        validateSchema(rn, cfg);
    }

    // Namespace can not be changed from non-extensible to extensible.
    public void testNSToExtensibleFail()throws EMDConfigException {
        String cfg = getConfigXml(schemaXml(
                    nsXml("myNS", "true", "false", null, fieldXml(1))));
        RootNamespace rn = validateSchema(cfg);
        validateSchemaFail(rn, getConfigXml(schemaXml(nsXml("myNS"))),
            "Namespace can not be changed from non-extensible to extensible.");
    }

    // Namespace is not in namespace or schema context.
    public void testNamespaceContextFail_t1() throws EMDConfigException {
        // Namespace is define in a Table.
        String cfg = getConfigXml(null, null, tablesXml(nsXml("myNS")));
        validateSchemaFail(cfg,
            "Namespace is not in namespace or schema context.");
    } 

    // Namespace is not in namespace or schema context.
    public void testNamespaceContextFail_t2() throws EMDConfigException {
        // Namespace is define in a fsView.
        StringBuffer temp = new StringBuffer(fieldXml(1, "myName"));
        temp.append(fieldXml(1, MDNAME, "string", "64", "false"));
        String cfg = getConfigXml(schemaXml(null, temp),
            viewsXml(viewXml("myView", "${myName}.txt", nsXml("myNS"))), null);
        validateSchemaFail(cfg,
            "NamespaceFiled is not in namespace or schema context.");
    } 

    // Namespace tag without an eclosing <namespace> or <schema>.
    public void testNamespaceTagFail_t1() throws EMDConfigException {
        // Namespace is define outside of namespace or schema context.
        String cfg = getConfigXml(nsXml("myNS"));
        validateSchemaFail(cfg,
          "Found a Namespace tag without an eclosing <namespace> or <schema>.");
    } 
}
