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


package com.sun.honeycomb.adm.cli.parser;

import java.util.Locale;

public abstract class Option {
    public static final int UNPARSED = -1;
    public static final int SHORT    =  0;
    public static final int LONG     =  1;
    
    private String  _shortForm  = null;
    private String  _longForm   = null;
    private boolean _wantsValue = false;

    private boolean _parsed;
    private int     _type;

    protected Option (char shortForm, 
                      String longForm, 
                      boolean wantsValue ) {

        if (longForm == null)
            throw new IllegalArgumentException (
                "null arg forms not allowed");

        _shortForm  = new String(new char[]{shortForm});
        _longForm   = longForm;
        _wantsValue = wantsValue;
        _parsed     = false;
    }

    public String shortForm() { 
        return _shortForm; 
    }

    public String longForm() { 
        return _longForm; 
    }

    public boolean wantsValue() { 
        return _wantsValue; 
    }

    /**
     * Invoked by OptionParser to mark this option as parsed
     */
    public void isParsed (int type) {
        assert (type == LONG || type == SHORT);
        _parsed = true;
        _type   = type;
    }

    /**
     * Returns the type of option that was passed to command. If the Option has
     * been created but not yet parsed, UNPARSED is returned.
     */
    public int getOptionType () {
        return _type;
    }

    public final Object getValue (String arg, Locale locale)
        throws IllegalOptionValueException {

        if (_wantsValue) {
            if (arg == null) {
                throw new IllegalOptionValueException(this, "");
            }

            return parseValue (arg, locale);
        }
        else {
            return Boolean.TRUE;
        }
    }

    protected Object parseValue (String arg, Locale locale)
        throws IllegalOptionValueException {
            return null;
    }

    public String toString() {
        switch (_type) {
            case UNPARSED:
                return "-" + shortForm() + "/--" + longForm();
            case SHORT:
                return "-" + shortForm();
            case LONG:
                return "--" + longForm();
            default:
                assert (false);
                return null;
        }
    }
}

