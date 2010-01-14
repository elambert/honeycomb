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

public class Field_t1 extends MDTestcase {
    private static Logger log
        = Logger.getLogger(Field_t1.class.getName());

    public Field_t1(String name) {
        super(name);
    }

    private String maxStrLength = Integer.toString(Field.MAX_STRING_LENGTH);
    private String maxStrLengthPlusOne =
                   Integer.toString(Field.MAX_STRING_LENGTH + 1);
    private String maxCharLength = Integer.toString(Field.MAX_CHAR_LENGTH);
    private String maxCharLengthPlusOne =
                   Integer.toString(Field.MAX_CHAR_LENGTH + 1);

    public void testXXX() {
    }

    // Field of string can not be longer than Field.MAX_STRING_LENGTH.
    public void testFieldOfStringFail() throws EMDConfigException {
        String cfg = getConfigXml(schemaXml(null,
                    fieldXml(1, "string", maxStrLengthPlusOne)));
        validateSchemaFail(cfg,
                "Field of string can not be longer than " +
                maxStrLength + ".");
    }

    public void testFieldOfString() throws EMDConfigException {
        String cfg = getConfigXml(
                schemaXml(null, fieldXml(1, "string", maxStrLength)));
        validateSchema(cfg);
    }

    // Field of char can not be longer than Field.MAX_CHAR_LENGTH.
    public void testFieldOfCharFail() throws EMDConfigException{
        String cfg = getConfigXml(
                schemaXml(null, fieldXml(1, "char", maxCharLengthPlusOne)));
        validateSchemaFail(cfg,
                "Field of char can not be longer than " +
                maxCharLength + ".");
    }

    public void testFieldOfChar() throws EMDConfigException {
        String cfg = getConfigXml(
                schemaXml(null, fieldXml(1, "char", maxCharLength)));
        validateSchema(cfg);
    }

    // Field of binary can not be longer than Field.MAX_CHAR_LENGTH.
    public void testFieldOfBinaryFail() throws EMDConfigException{
        String cfg = getConfigXml(
                schemaXml(null, fieldXml(1, "binary", maxCharLengthPlusOne)));
        validateSchemaFail(cfg,
                "Field of binary can not be longer than " +
                maxCharLength + ".");
    }

    public void testFieldOfBinary() throws EMDConfigException {
        String cfg = getConfigXml(
                schemaXml(null, fieldXml(1, "binary", maxCharLength)));
        validateSchema(cfg);
    }

    // Namespace  has to be extensible to change field from
    // non-querable to querable.
    // XXX the fix for CR6546543 broke this test.

    public void xtestFieldToQueryableFail()throws EMDConfigException {
        String temp = getConfigXml(schemaXml(
                    nsXml("myNS", "true", "false", null,
                        fieldXml("myFd", "false"))));
        RootNamespace rn = validateSchema(temp);
        String cfg = getConfigXml(schemaXml(
                    nsXml("myNS", "true", "false", null,
                          fieldXml("myFd", "true"))));
        validateSchemaFail(rn, cfg,
            "Namespace has to be extensible to change field from " +
            "non-querable to querable.");
    }

    // OK to change field from non-querable to querable if namespace
    // is extensible.
    public void testFieldToQueryable()throws EMDConfigException {
        String temp = getConfigXml(schemaXml(
                    nsXml("myNS", "true", "true", null,
                          fieldXml("myFd", "false"))));
        RootNamespace rn = validateSchema(temp);
        String cfg = getConfigXml(schemaXml(
                    nsXml("myNS", "true", "true", null,
                          fieldXml("myFd", "true"))));
        validateSchema(rn, cfg);
    }

    // Queryable field can not be made non-queryable.
    public void testFieldToNonQueryable()throws EMDConfigException {
        String temp = getConfigXml(schemaXml(
                    nsXml("myNS", "true", "true", null,
                          fieldXml("myFd", "true"))));
        RootNamespace rn = validateSchema(temp);
        String cfg = getConfigXml(schemaXml(
                    nsXml("myNS", "true", "true", null,
                          fieldXml("myFd", "false"))));
        validateSchemaFail(rn, cfg,
            "Queryable field can not be made non-queryable.");
    }

    // Same field with different type can not occurs twice in a Namespace.
    public void testSameFieldFail_t1() throws EMDConfigException {
        StringBuffer temp = new StringBuffer(fieldXml(1, "string", "64"));
        temp.append(fieldXml(1, "char", "64"));
        String cfg = getConfigXml(schemaXml(nsXml(
                       "myNS", "true", "true", null, temp)));
        validateSchemaFail(cfg,
            "Namesapce can not have same field occurs twice.");
    }

    // Same field with different length can not occurs twice in a Namespace.
    public void testSameFieldFail_t2() throws EMDConfigException {
        StringBuffer temp = new StringBuffer(fieldXml(1, "string", "64"));
        temp.append(fieldXml(1, "string", "32"));
        String cfg = getConfigXml(schemaXml(nsXml(
                       "myNS", "true", "true", null, temp)));
        validateSchemaFail(cfg,
            "Namesapce can not have same field occurs twice.");
    }

    // OK if two fields with same name, type and length.
    // Note: The second field will be ignored duing the merge.
    public void testSameField() throws EMDConfigException {
        StringBuffer temp = new StringBuffer(fieldXml(1, "string", "64"));
        temp.append(fieldXml(1, "string", "64"));
        String cfg = getConfigXml(schemaXml(nsXml(
                       "myNS", "true", "true", null, temp)));
        validateSchema(cfg);
    }

    // Field is not in namespace or schema context.
    public void testFieldContextFail_t1() throws EMDConfigException {
        // Field is define in a Table.
        String cfg = getConfigXml(null, null, tablesXml(fieldXml(1)));
        validateSchemaFail(cfg,
            "Field is not in namespace or schema context.");
    }

    // Field is not in namespace or schema context.
    public void testFieldContextFail_t2() throws EMDConfigException {
        // Field is define in a fsView.
        StringBuffer temp = new StringBuffer(fieldXml(1, "myName"));
        temp.append(fieldXml(1, MDNAME, "string", "64", "false"));
        String cfg = getConfigXml(schemaXml(null, temp),
              viewsXml(viewXml("myView", "${myName}.txt", fieldXml(1))), null);
        validateSchemaFail(cfg,
            "Field is not in namespace or schema context.");
    }

    // Field tag without an eclosing <namespace> or <schema>.
    public void testFieldTagFail_t1() throws EMDConfigException {
        // Field is define outside of namespace or schema context.
        String cfg = getConfigXml(fieldXml(1));
        validateSchemaFail(cfg,
            "Found a field tag without an eclosing <namespace> or <schema>.");
    }
}
