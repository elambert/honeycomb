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



package com.sun.honeycomb.spreader;

import java.io.*;


// Test class to modify behaviour of SwitchRules to suit unit tests
public class TestSwitchRules extends SwitchRules {

    public static String imaskFile;
    public static String iruleFile;

    /************************************************************************
     * @inherit javadoc
     **/
    protected BufferedReader getIMaskReader() throws MalformedOutputException {
        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(imaskFile));
        } catch (IOException e) {
            System.out.println("zimask.out: " + e);
            throw new MalformedOutputException(e);
        }
        return br;
    }

    /************************************************************************
     * @inherit javadoc
     **/
    protected BufferedReader getIRuleReader() throws MalformedOutputException {
        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(iruleFile));
        } catch (IOException e) {
            System.out.println("zimask.out: " + e);
            throw new MalformedOutputException(e);
        }
        return br;
    }

    /************************************************************************
     * @inherit javadoc
     **/
    TestSwitchRules(int maxHVal, int maxPVal) throws MalformedOutputException {
        super(maxHVal, maxPVal);
    }
}
