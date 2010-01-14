package com.sun.honeycomb.testcmd.common;

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



import java.io.Serializable;
import java.util.List;
import java.util.ListIterator;

public class ExitStatus implements Serializable {

    public static final int SUCCESS = 0;
    private int returnCode;
    private String cmdExecuted;
    private List outStrings;
    private List errStrings;

    public ExitStatus() {
        returnCode = 0;
        cmdExecuted = null;
        outStrings = null;
        errStrings = null;
    }

    public ExitStatus(String s, int i, List list, List list1) {
        returnCode = i;
        cmdExecuted = s;
        outStrings = list;
        errStrings = list1;
    }

    public int getReturnCode()
    {
        return returnCode;
    }

    public String getCmdExecuted()
    {
        return cmdExecuted;
    }

    public List getOutStrings()
    {
        return outStrings;
    }

    public List getErrStrings()
    {
        return errStrings;
    }

    public void setReturnCode(int i)
    {
        returnCode = i;
    }

    public void setCmdExecuted(String s)
    {
        cmdExecuted = s;
    }

    public void setOutStrings(List list)
    {
        outStrings = list;
    }

    public void setErrStrings(List list)
    {
        errStrings = list;
    }

    public boolean outStringsMatch(String regexp)
    {
        return (stringMatches(regexp, outStrings));
    }

    public boolean errStringsMatch(String regexp)
    {
        return (stringMatches(regexp, errStrings));
    }

    private boolean stringMatches(String regexp, List stringList)
    {
        if (stringList == null) {
            return (false);
        }

        ListIterator i = stringList.listIterator();
        while (i.hasNext()) {
            String s = (String) i.next();
            if (s.matches(regexp)) {
                return (true);
            }
        }

        return (false);
    }

    public String getOutputString()
    {
        StringBuffer buf = new StringBuffer();
        ListIterator i;
        
        i = outStrings.listIterator();
        buf.append("outStrings={");
        while (i.hasNext()) {
            buf.append((String) i.next() + ";");
        }
        buf.append("}");

        i = errStrings.listIterator();
        buf.append("; errStrings={");
        while (i.hasNext()) {
            buf.append((String) i.next() + ";");
        }
        buf.append("}");

        return (buf.toString());
    }

    public String toString()
    {
        StringBuffer buf = new StringBuffer();
        buf.append("returnCode=" + returnCode + ", ");
        buf.append("cmdExecuted=" + cmdExecuted + ", ");
        buf.append(getOutputString());
        return (buf.toString());
    }
}
