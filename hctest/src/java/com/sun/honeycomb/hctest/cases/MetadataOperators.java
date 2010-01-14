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



package com.sun.honeycomb.hctest.cases; 

import java.util.HashMap;

import com.sun.honeycomb.test.*;
import com.sun.honeycomb.test.util.*;
import com.sun.honeycomb.hctest.*;
import com.sun.honeycomb.hctest.util.*;
import com.sun.honeycomb.client.NameValueSchema.ValueType;

import com.sun.honeycomb.client.*;

/**
 * Test various query operators.
 *
 */

public class MetadataOperators extends HoneycombLocalSuite {

    private CmdResult storeResult;
    private boolean emulator = false;

    public MetadataOperators() {
        super();
    }

    public void setUp() throws Throwable {
        super.setUp();
        setFilesize(HoneycombTestConstants.DEFAULT_FILESIZE_SMALL);
        String emulatorStr =
            TestRunner.getProperty(HoneycombTestConstants.PROPERTY_NO_CLUSTER);
        if (emulatorStr != null) {
            emulator = true;
        }
        doStoreTestNow();
    }

    public void tearDown() throws Throwable {
        super.tearDown();
    }

    private void addTags() {
        addTag(Tag.REGRESSION);
        addTag(Tag.POSITIVE);
        addTag(Tag.QUICK);
        addTag(Tag.SMOKE);
        addTag(HoneycombTag.QUERY);
        addTag(HoneycombTag.JAVA_API);
        addTag(HoneycombTag.EMULATOR);
    }

    private void doStoreTestNow() throws HoneycombTestException {
        storeResult = store(getFilesize());
        
        Log.INFO("Stored file of size " + getFilesize() +
                 " as oid " + storeResult.mdoid);
    }

    /**
     * Test Boolean operators for String 
     */
    public boolean testStringBooleanOperators()
        throws HoneycombTestException {

        addTags();
        if (excludeCase()) 
            return false;
        return testBooleanOperators(NameValueSchema.STRING_TYPE,
                                    HoneycombTestConstants.MD_VALID_FIELD1,
                                    HoneycombTestConstants.MD_VALID_FIELD2);

    }


    /**
     * Test Boolean operators for Long
     */
    public boolean testLongBooleanOperators()
        throws HoneycombTestException {
        addTags();
        if (excludeCase()) 
            return false;
        return testBooleanOperators(NameValueSchema.LONG_TYPE,
                                    HoneycombTestConstants.MD_LONG_FIELD1,
                                    HoneycombTestConstants.MD_LONG_FIELD2);

    }

    /**
     * Test Long operators
     */
    public boolean testLongOperators()
        throws HoneycombTestException {

        HashMap map;
        String oid;
        boolean res;

        addTags();
        if (excludeCase()) 
            return false;

        map = new HashMap();
        map.put(HoneycombTestConstants.MD_LONG_FIELD1, new Long(-87));
        oid = addMetadata(map);

        
        res = queryCheckOid("{fn ABS(" +
                            HoneycombTestConstants.MD_LONG_FIELD1 +
                            ")}=" + 87 + "", oid);
        if (res == false) {
            Log.INFO("ABS test failed");
            return false;
        }

	map = new HashMap();
        map.put(HoneycombTestConstants.MD_LONG_FIELD1, new Long(7));
        map.put(HoneycombTestConstants.MD_LONG_FIELD2, new Long(3));
        oid = addMetadata(map);


        res = queryCheckOid("{fn MOD(" +
                            HoneycombTestConstants.MD_LONG_FIELD1 +
                            "," + HoneycombTestConstants.MD_LONG_FIELD2 +
			    ")}=" + 1 + "", oid);

        if (res == false) {
            Log.INFO("MOD test failed");
            return false;
        } 
        return true;
    }

    /**
     * Test Double operators
     */
    public boolean testDoubleOperators()
        throws HoneycombTestException {

        HashMap map;
        String oid;
        boolean res;

        addTags();
        if (excludeCase())
            return false;

	// ABS function
        map = new HashMap();
        map.put(HoneycombTestConstants.MD_DOUBLE_FIELD1, new Double(-99.9));
        oid = addMetadata(map);


        res = queryCheckOid("{fn ABS(" +
                            HoneycombTestConstants.MD_DOUBLE_FIELD1 +
                            ")}=" + 99.9 + "", oid);
        if (res == false) {
            Log.INFO("ABS test failed for double");
            return false;
        }
	/**
	 *Tag.NORUN <6503337:space at the end of query causes it to fail>
	 */
	/**
	 *res = queryCheckOid("{fn ABS( " +
         *                   HoneycombTestConstants.MD_DOUBLE_FIELD1 +
         *                   ")}=" + 99.9 + " ", oid);
         *if (res == false) {
         *   Log.INFO("ABS test(trailing space) failed for double");
         *   return false;
         *}
	 */ 

        /**
         * The following test will pass against Derby
         */
        /**
	 *
	 * //SQRT function
	 *map = new HashMap();
         *map.put(HoneycombTestConstants.MD_DOUBLE_FIELD1, new Double(9.0));
         *oid = addMetadata(map);
         *
         *res = queryCheckOid("{fn SQRT({fn ABS(" +
         *                   HoneycombTestConstants.MD_DOUBLE_FIELD1 +
         *                   ")})}=" + 3.0 + "", oid);
         *if (res == false) {
         *   Log.INFO("SQRT test failed for double");
         *   return false;
         *}
	 *
	 */
        return true;
    }

    /**
     * Test Date operators
     */
    public boolean testDateOperators()
        throws HoneycombTestException {

        HashMap map;
        String oid;
        boolean res;

        addTags();
        if (excludeCase())
            return false;

        // CURR_DATE function
        map = new HashMap();
        map.put(HoneycombTestConstants.MD_DATE_FIELD1, "2000-01-01");
        oid = addMetadata(map);

        res = queryCheckOid(
                            HoneycombTestConstants.MD_DATE_FIELD1 +
                            "< CURRENT_DATE", oid);
        if (res == false) {
            Log.INFO("CURRENT_DATE test failed");
            return false;
        }
        /**
         *Tag.NORUN <6503337:space at the end of query causes it to fail>
         */
        /*
         *res = queryCheckOid(
         *                   HoneycombTestConstants.MD_DATE_FIELD1 +
         *                   "< CURRENT_DATE ", oid);
         *if (res == false) {
         *   Log.INFO("CURRENT_DATE(trailing space) test failed");
         *   return false;
         *}
	 */

	/**
	 * The following tests will pass against Derby
	 */
	/**
	 *
	 * //DAY, MONTH and YEAR
	 *res = queryCheckOid("{fn DAY(" +
         *                   HoneycombTestConstants.MD_DATE_FIELD1 +
         *                   ")}=" + 1 , oid);
         *if (res == false) {
         *   Log.INFO("DAY test failed");
         *   return false;
         *}
	 *res = queryCheckOid("{fn MONTH(" +
         *                   HoneycombTestConstants.MD_DATE_FIELD1 +
         *                   ")}=" + 1 , oid);
         *if (res == false) {
         *   Log.INFO("MONTH test failed");
         *   return false;
         *}
	 *res = queryCheckOid("{fn YEAR(" +
         *                   HoneycombTestConstants.MD_DATE_FIELD1 +
         *                   ")}=" + 2000 , oid);
         *if (res == false) {
         *   Log.INFO("YEAR test failed");
         *   return false;
         *}
	 *
	 */
        return true;
    }

    /**
     * Test Time operators
     */
    public boolean testTimeOperators()
        throws HoneycombTestException {

        HashMap map;
        String oid;
        boolean res;

        addTags();
        if (excludeCase())
            return false;

        // CURR_DATE function
        map = new HashMap();
        map.put(HoneycombTestConstants.MD_TIME_FIELD1, "01:01:01");
        oid = addMetadata(map);

        res = queryCheckOid(
                            HoneycombTestConstants.MD_TIME_FIELD1 +
                            "< '03:03:03'", oid);
        if (res == false) {
            Log.INFO("CURRENT_TIME test failed");
            return false;
        }
        /**
         *Tag.NORUN <6503337:space at the end of query causes it to fail>
         */
        /*
         *res = queryCheckOid(
         *                   HoneycombTestConstants.MD_TIME_FIELD1 +
         *                   "< '03:03:03'", oid);
         *if (res == false) {
         *   Log.INFO("CURRENT_TIME(trailing space) test failed");
         *   return false;
         *}
	 */
        //HOUR, MINUTE, SECOND
        res = queryCheckOid("{fn HOUR(" +
                            HoneycombTestConstants.MD_TIME_FIELD1 +
                            ")}=" + 1 , oid);
        if (res == false) {
            Log.INFO("HOUR test failed");
            return false;
        }
        res = queryCheckOid("{fn MINUTE(" +
                            HoneycombTestConstants.MD_TIME_FIELD1 +
                            ")}=" + 1 , oid);
        if (res == false) {
            Log.INFO("MINUTE test failed");
            return false;
	}
        res = queryCheckOid("{fn SECOND(" +
                            HoneycombTestConstants.MD_TIME_FIELD1 +
                            ")}=" + 1 , oid);
        if (res == false) {
            Log.INFO("SECOND test failed");
            return false;
        } 
        return true;
    }


    /**
     * Test TimeStamp operators
     */
    public boolean testTimeStampOperators()
        throws HoneycombTestException {

        HashMap map;
        String oid;
        boolean res;

        addTags();
        if (excludeCase())
            return false;

        // CURR_DATE function
        map = new HashMap();
        map.put(HoneycombTestConstants.MD_TIMESTAMP_FIELD1, "2001-01-01T01:01:01.999Z");
        oid = addMetadata(map);

        res = queryCheckOid(
                            HoneycombTestConstants.MD_TIMESTAMP_FIELD1 +
                            "< CURRENT_TIMESTAMP", oid);
        if (res == false) {
            Log.INFO("CURRENT_TIMESTAMP test failed");
            return false;
        } 
        /**
         *Tag.NORUN <6503337:space at the end of query causes it to fail>
         */
        /*
         *res = queryCheckOid(
         *                   HoneycombTestConstants.MD_TIMESTAMP_FIELD1 +
         *                   "< CURRENT_TIMESTAMP ", oid);
         *if (res == false) {
         *   Log.INFO("CURRENT_TIMESTAMP(trailing space) test failed");
         *   return false;
         *}
	 */

	// TIMESTAMPDIFF, TIMESTAMPADD 
        res = queryCheckOid("{fn TIMESTAMPDIFF(SQL_TSI_YEAR, CURRENT_TIMESTAMP, " +
                            HoneycombTestConstants.MD_TIMESTAMP_FIELD1 +
                            ")}<"+ 6, oid);
        if (res == false) {
            Log.INFO("TIMESTAMPDIFF test failed");
            return false;
        } 

        res = queryCheckOid("{fn TIMESTAMPADD(SQL_TSI_YEAR, 1, " +
                            HoneycombTestConstants.MD_TIMESTAMP_FIELD1 +
                            ")}< CURRENT_TIMESTAMP", oid);
        if (res == false) {
            Log.INFO("TIMESTAMPADD test failed");
            return false;
        } 
        return true;
    }


    /**
     * Test String operators
     */
    public boolean testStringOperators()
        throws HoneycombTestException {

        HashMap map;
        String oid;
        boolean res;

        addTags();
        if (excludeCase()) 
            return false;


        map = new HashMap();
        map.put(HoneycombTestConstants.MD_VALID_FIELD1, "yeahTestCase");
        oid = addMetadata(map);


        // Uppercase, lowercase
        res = queryCheckOid("{fn UCASE(" +
                            HoneycombTestConstants.MD_VALID_FIELD1 +
                            ")}='" + "YEAHTESTCASE" + "'", oid);
        if (res == false) {
            Log.INFO("UCASE test failed");
            return false;
        }
        res = queryCheckOid("{fn LCASE(" +
			    HoneycombTestConstants.MD_VALID_FIELD1 +
                            ")}='" + "yeahtestcase" + "'", oid);
        if (res == false) {
            Log.INFO("UCASE test failed");
            return false;
        }
	
        /**
         *Tag.NORUN <6503337:space at the end of query causes it to fail>
         */
        /*
         *res = queryCheckOid("{fn LCASE(" +
         *                   HoneycombTestConstants.MD_VALID_FIELD1 +
         *                   ")}='" + "yeahtestcase" + " '", oid);
         *if (res == false) {
         *   Log.INFO("LCASE(trailing space) test failed");
         *   return false;
         *}
	 */

	//Substring	
	res = queryCheckOid("{fn SUBSTRING(" +
			    HoneycombTestConstants.MD_VALID_FIELD1 +
			    "," + 1 + "," + 3 + ")}='" + "yea" + "'", oid);
        if (res == false) {
            Log.INFO("SUBSTRING test failed");
            return false;
        } 


 	//Length
        res = queryCheckOid("{fn LENGTH(" +
                            HoneycombTestConstants.MD_VALID_FIELD1 +
                            ")}=" + 12 , oid);  
	if (res == false) {
            Log.INFO("LENGTH test failed");
            return false;
        } 


	//Locate 
        map = new HashMap();
        map.put(HoneycombTestConstants.MD_VALID_FIELD1, "yeahTestCase");
        map.put(HoneycombTestConstants.MD_VALID_FIELD2, "Test");
        oid = addMetadata(map);
	res = queryCheckOid("{fn LOCATE(" +
                            HoneycombTestConstants.MD_VALID_FIELD2 +
                            "," + 
			    HoneycombTestConstants.MD_VALID_FIELD1 +
			    "," + 5 + ")}=" + 5 , oid);
	 if (res == false) {
            Log.INFO("Locate test failed");
            return false;
        } 


        //LTRIM, RTRIM
        map = new HashMap();
        map.put(HoneycombTestConstants.MD_VALID_FIELD1, " yeahTestCase");
	oid = addMetadata(map);
	res = queryCheckOid("{fn LTRIM(" +
                            HoneycombTestConstants.MD_VALID_FIELD1 +
                            ")}='" + "yeahTestCase" + "'", oid);
        if (res == false) {
            Log.INFO("LTRIM test failed");
            return false;
        }
	map = new HashMap();
        map.put(HoneycombTestConstants.MD_VALID_FIELD1, "yeahTestCase ");
        oid = addMetadata(map);
	res = queryCheckOid("{fn RTRIM(" +
                            HoneycombTestConstants.MD_VALID_FIELD1 +
                            ")}='" + "yeahTestCase" + "'", oid);
        if (res == false) {
            Log.INFO("RTRIM test failed");
            return false;
        }  

        // Concatenation
        map = new HashMap();
        map.put(HoneycombTestConstants.MD_VALID_FIELD1, "yeahTestCase");
        map.put(HoneycombTestConstants.MD_VALID_FIELD2, "Ouups");
        oid = addMetadata(map);
        res = queryCheckOid("(" + HoneycombTestConstants.MD_VALID_FIELD1 +
                            " || " + 
                            HoneycombTestConstants.MD_VALID_FIELD2 +
                            ")='" + "yeahTestCaseOuups" + "'", oid);
        if (res == false) {
            Log.INFO("concatenation test failed test failed");
            return false;
        }
        return (true);
    }


    /**
     * Test Char operators
     */
    public boolean testCharOperators()
        throws HoneycombTestException {

        HashMap map;
        String oid;
        boolean res;

        addTags();
        if (excludeCase())
            return false;


        map = new HashMap();
        map.put(HoneycombTestConstants.MD_CHAR_FIELD1, "yeahTestCase");
        oid = addMetadata(map);


        // Uppercase, lowercase
        res = queryCheckOid("{fn UCASE(" +
                            HoneycombTestConstants.MD_CHAR_FIELD1 +
                            ")}='" + "YEAHTESTCASE" + "'", oid);
        if (res == false) {
            Log.INFO("UCASE test failed");
            return false;
        } 

        res = queryCheckOid("{fn LCASE(" +
                            HoneycombTestConstants.MD_CHAR_FIELD1 +
                            ")}='" + "yeahtestcase" + "'", oid);
        if (res == false) {
            Log.INFO("UCASE test failed");
            return false;
        }

        /**
         *Tag.NORUN <6503337:space at the end of query causes it to fail>
         */
        /*
         *res = queryCheckOid("{fn LCASE(" +
         *                   HoneycombTestConstants.MD_CHAR_FIELD1 +
         *                   ")}='" + "yeahtestcase" + " '", oid);
         *if (res == false) {
         *   Log.INFO("LCASE(trailing) test failed");
         *   return false;
         *}
	 */

        //Substring
        res = queryCheckOid("{fn SUBSTRING(" +
                            HoneycombTestConstants.MD_CHAR_FIELD1 +
                            "," + 1 + "," + 3 + ")}='" + "yea" + "'", oid);
        if (res == false) {
            Log.INFO("SUBSTRING test failed");
            return false;
        }

        //Length
        res = queryCheckOid("{fn LENGTH(" +
                            HoneycombTestConstants.MD_CHAR_FIELD1 +
                            ")}=" + 12 , oid);
        if (res == false) {
            Log.INFO("LENGTH test failed");
            return false;
        } 

        //LTRIM, RTRIM
        map = new HashMap();
        map.put(HoneycombTestConstants.MD_CHAR_FIELD1, " yeahTestCase");
        oid = addMetadata(map);
        res = queryCheckOid("{fn LTRIM(" +
                            HoneycombTestConstants.MD_CHAR_FIELD1 +
                            ")}='" + "yeahTestCase" + "'", oid);
        if (res == false) {
            Log.INFO("LTRIM test failed");
            return false;
        } 

        map = new HashMap();
        map.put(HoneycombTestConstants.MD_CHAR_FIELD1, "yeahTestCase ");
        oid = addMetadata(map);
        res = queryCheckOid("{fn RTRIM(" +
                            HoneycombTestConstants.MD_CHAR_FIELD1 +
                            ")}='" + "yeahTestCase" + "'", oid);
        if (res == false) {
            Log.INFO("RTRIM test failed");
            return false;
        } 
        return (true);
    }


    public boolean testRegularExprOperators()
        throws HoneycombTestException {
        
        //
        // Test 'LIKE' syntax
        //
        HashMap map;
        String oid;
        boolean res;

        addTags();
        if (excludeCase()) 
            return false;

        // 1. Test wildcard character
        map = new HashMap();
        map.put(HoneycombTestConstants.MD_VALID_FIELD1, "testlike");
        oid = addMetadata(map);
        res = queryCheckOid(HoneycombTestConstants.MD_VALID_FIELD1 +
                            " like '" + "test%" + "'", oid);
        if (res == false) {
            Log.INFO("Test for like (1) test failed");
            return false;
        }

        //
        // escape syntax not suppported by HADB...
        //
        //if (emulator) {
        if (false) {
            // 2. Test single wildcard character using escape JDBC sequence
            map = new HashMap();
            map.put(HoneycombTestConstants.MD_VALID_FIELD1, "test_like");
            oid = addMetadata(map);
            res = queryCheckOid(HoneycombTestConstants.MD_VALID_FIELD1 +
                                " like '" + "tes_\\_like" + "' " +
                                "{escape '\\'}", oid);
            if (res == false) {
                Log.INFO("Test for like (2) test failed");
                return false;
            }

            // 3. Combination of the two latest cases
            map = new HashMap();
            map.put(HoneycombTestConstants.MD_VALID_FIELD1, "test_lik%");
            oid = addMetadata(map);
            res = queryCheckOid(HoneycombTestConstants.MD_VALID_FIELD1 +
                                " like '" + "%\\_lik\\%" + "' " +
                                "{escape '\\'}", oid);
            if (res == false) {
                Log.INFO("Test for like (3) test failed");
                return false;
            }
        }


        //
        // Test IN syntax
        //
        map = new HashMap();
        map.put(HoneycombTestConstants.MD_VALID_FIELD1, "test_in");
        oid = addMetadata(map);
        res = queryCheckOid(HoneycombTestConstants.MD_VALID_FIELD1 +
                            " in " + "('test_in', 'testin')", oid);
        if (res == false) {
            Log.INFO("Test for in (1) test failed");
            return false;
        }
        map.put(HoneycombTestConstants.MD_VALID_FIELD1, "testin");
        oid = addMetadata(map);
        res = queryCheckOid(HoneycombTestConstants.MD_VALID_FIELD1 +
                            " in " + "('test_in', 'testin')", oid);
        if (res == false) {
            Log.INFO("Test for in (2) test failed");
            return false;
        }

        //if (emulator) {
        if (false) {
            //
            // Mix of the two previous syntax
            //
            map = new HashMap();
            map.put(HoneycombTestConstants.MD_VALID_FIELD1, "_cardamon%epice");
            map.put(HoneycombTestConstants.MD_VALID_FIELD2, "origin");
            oid = addMetadata(map);
            res = queryCheckOid(HoneycombTestConstants.MD_VALID_FIELD1 +
                                " like " + "'\\_%\\%epic_'" + " {escape '\\'} " +
                                " AND " + HoneycombTestConstants.MD_VALID_FIELD2 +
                                " in " + "('origin', 'end')", oid);
            if (res == false) {
                Log.INFO("Test for in (1) test failed");
                return false;
            }
        }
        return true;
    }

    /**
     * Test Logical Operators
     */
    public boolean testLogicalOperators()
        throws HoneycombTestException {

        boolean res;

        addTags();
        if (excludeCase()) 
            return false;

        HashMap map = new HashMap();
        map.put(HoneycombTestConstants.MD_VALID_FIELD1, "value_field1");
        map.put(HoneycombTestConstants.MD_VALID_FIELD2, "value_field2");
        map.put(HoneycombTestConstants.MD_LONG_FIELD1, new Long(567657756));
        String oid = addMetadata(map);

        res = queryCheckOid(HoneycombTestConstants.MD_VALID_FIELD1 + "='" +
                            "value_field1'" + " AND (" +
                            HoneycombTestConstants.MD_VALID_FIELD2 + "='" +
                            "value_field2'" + " OR " + 
                            HoneycombTestConstants.MD_VALID_FIELD2 + "='" +
                            "value_bad_field2'" + ") AND " + 
                            HoneycombTestConstants.MD_LONG_FIELD1 + "=" +
                            "567657756", oid);
        if (res == false) {
            Log.INFO("Test (1) for logicalOperator failed test failed");
            return false;
        }

        res = queryCheckOid(HoneycombTestConstants.MD_VALID_FIELD1 + "='" +
                            "value_field1'" + " OR " +
                            HoneycombTestConstants.MD_VALID_FIELD1 + "='" +
                            "value_field2'" + " AND " + 
                            HoneycombTestConstants.MD_VALID_FIELD2 + "='" +
                            "value_bad_field2'" + " AND " + 
                            HoneycombTestConstants.MD_LONG_FIELD1 + "=" +
                            "567657756", oid);
        if (res == false) {
            Log.INFO("Test (2) for logicalOperator failed test failed");
            return false;
        }
        return true;
    }

    /**
     * Test Boolean operators for String
     */
    private boolean testBooleanOperators(ValueType type,
                                         String FIELD1, String FIELD2)
        throws HoneycombTestException {

        HashMap map = null;
        String s1 = "string1";
        String s2 = "string11";
        long l1 = 345;
        long l2 = 346;
        Object o1 = "" , o2 = "";

        int resNotEq, resLower, resLowerEq, resEqual, resGreater, 
            resGreaterEq, res;
        String qNotEq = "",
            qLower = "",
            qLowerEq = "",
            qEqual = "",
            qGreater = "",
            qGreaterEq = ""; 


        if (type.equals(NameValueSchema.STRING_TYPE)) {
            qNotEq = FIELD1 + "<>'" + s1 + "'";
            qLower = FIELD1 + "<'" + s1 + "'";
            qLowerEq = FIELD1 + "<='" + s1 + "'";
            qEqual = FIELD1 + "='" + s1 + "'";
            qGreater = FIELD1 + ">'" + s1 + "'";
            qGreaterEq = FIELD1 + ">='" + s1 + "'";
            o1 = s1;
            o2 = s2;
        } else if (type.equals(NameValueSchema.LONG_TYPE)) {
            qNotEq = FIELD1 + "<>" + l1;
            qLower = FIELD1 + "<" + l1;
            qLowerEq = FIELD1 + "<=" + l1;
            qEqual = FIELD1 + "=" + l1;
            qGreater = FIELD1 + ">" + l1 ;
            qGreaterEq = FIELD1 + ">=" + l1;
            o1 = new Long(l1);
            o2 = new Long(l2);
        }

        //
        // Test for operator >, >=, =, <=, < and <> between an attribute string
        // and a literal
        //
        map = new HashMap();
        map.put(FIELD1, o1);
        addMetadata(map);

        map = new HashMap();
        map.put(FIELD1, o2);
        addMetadata(map);

        resNotEq = returnQueryResults(qNotEq);
        resLower = returnQueryResults(qLower);
        resLowerEq = returnQueryResults(qLowerEq);
        resEqual = returnQueryResults(qEqual);
        resGreater = returnQueryResults(qGreater);
        resGreaterEq = returnQueryResults(qGreaterEq);

        map = new HashMap();
        map.put(FIELD1, o1);
        addMetadata(map);

        res = returnQueryResults(qNotEq);
        if (res != resNotEq) {
            Log.INFO("query <> failed for attribute "  + 
                     FIELD1 + " and literal " +
                     o1);
            return false;
        }
        res = returnQueryResults(qLower);
        if (res != resLower) {
            Log.INFO("query < failed for attribute "  + 
                     FIELD1 + " and literal " +
                     o1);
            return false;
        }
        res = returnQueryResults(qLowerEq);
        if (res != resLowerEq + 1) {
            Log.INFO("query <= failed for attribute "  + 
                     FIELD1 + " and literal " +
                     o1);
            return false;
        }
        res = returnQueryResults(qEqual);
        if (res != resEqual + 1) {
            Log.INFO("query = failed for attribute "  + 
                     FIELD1 + " and literal " +
                     o1);
            return false;
        }
        res = returnQueryResults(qGreaterEq);
        if (res != resGreaterEq + 1) {
            Log.INFO("query >= failed for attribute "  + 
                     FIELD1 + " and literal " +
                     o1);
            return false;
        }
        res = returnQueryResults(qGreater);
        if (res != resGreater) {
            Log.INFO("query > failed for attribute "  + 
                     FIELD1 + " and literal " +
                     o1);
            return false;
        }

        //
        // Test for operator >, >=, =, <=, < and <> between two attributes
        //
        qNotEq = FIELD1 + "<>" + FIELD2;
        qLower = FIELD1 + "<" + FIELD2;
        qLowerEq = FIELD1 + "<=" + FIELD2;
        qEqual = FIELD1 + "=" + FIELD2;
        qGreater = FIELD1 + ">" + FIELD2 ;
        qGreaterEq = FIELD1 + ">=" + FIELD2;

        map = new HashMap();
        map.put(FIELD1, o1);
        map.put(FIELD2, o2);
        addMetadata(map);        

        resNotEq = returnQueryResults(qNotEq);
        resLower = returnQueryResults(qLower);
        resLowerEq = returnQueryResults(qLowerEq);
        resEqual = returnQueryResults(qEqual);
        resGreater = returnQueryResults(qGreater);
        resGreaterEq = returnQueryResults(qGreaterEq);

        map = new HashMap();
        map.put(FIELD1, o1);
        map.put(FIELD2, o2);
        addMetadata(map);        
        res = returnQueryResults(qNotEq);
        if (res != resNotEq + 1) {
            Log.INFO("query <> failed for attribute "  + 
                     FIELD1 + " and attribute " +
                     FIELD2);
            return false;
        }
        res = returnQueryResults(qLower);
        if (res != resLower + 1) {
            Log.INFO("query < failed for attribute "  + 
                     FIELD1 + " and attribute " +
                     FIELD2);
            return false;
        }
        res = returnQueryResults(qLowerEq);
        if (res != resLowerEq + 1) {
            Log.INFO("query <= failed for attribute "  + 
                     FIELD1 + " and attribute " +
                     FIELD2);
            return false;
        }
        res = returnQueryResults(qGreater);
        if (res != resGreater) {
            Log.INFO("query > failed for attribute "  + 
                     FIELD1 + " and attribute " +
                     FIELD2);
            return false;
        }
        res = returnQueryResults(qGreaterEq);
        if (res != resGreaterEq) {
            Log.INFO("query >= failed for attribute "  + 
                     FIELD1 + " and attribute " +
                     FIELD2);
            return false;
        }
        return true;
    }

    private String addMetadata(HashMap map)
        throws HoneycombTestException {
        CmdResult cr = addMetadata(storeResult.mdoid, map);
        if (!cr.pass) {
            throw new HoneycombTestException("Failed to add metadata to OID " +
                                             storeResult.mdoid);
        }
        return cr.mdoid;
    }


    private int returnQueryResults(String q)
        throws HoneycombTestException {
        int results = 0;
        try {
            CmdResult cr = query(q);
            QueryResultSet qrs = (QueryResultSet) cr.rs;

            while (qrs.next()) {
                results++;
            }
        } catch (Exception e) {
            HoneycombTestException hexc = new HoneycombTestException("query " +
                                                                    q + 
                                                                    " failed");
            hexc.initCause(e);
            throw hexc;
        }
        return results;
    }

    private boolean queryCheckOid(String q, String oid)
       throws HoneycombTestException {

        try {
            CmdResult cr = query(q);
            QueryResultSet qrs = (QueryResultSet) cr.rs;

            while (qrs.next()) {
                ObjectIdentifier curOid = qrs.getObjectIdentifier();
                if (oid.equals(curOid.toString())) {
                    return (true);
                }
            }
        } catch (Exception e) {
            HoneycombTestException hexc = new HoneycombTestException("query " +
                                                                     q + 
                                                                    " failed");
            hexc.initCause(e);
            throw hexc;
        }
        return (false);
    }

}
