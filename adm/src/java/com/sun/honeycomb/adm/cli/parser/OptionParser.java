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

import java.util.Hashtable;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Locale;
import java.text.NumberFormat;

/**
 * <p>
 * OptionParser is an Java implementation similiar to the C-language getopt
 * for the parsing of command-line program arguments.
 * </p>
 */
public class OptionParser {
    public static final int OPTION_BOOLEAN = 1;
    public static final int OPTION_INTEGER = 2;
    public static final int OPTION_DOUBLE  = 3;
    public static final int OPTION_STRING  = 4;
    public static final int OPTION_BYTE    = 5;
    public static final int OPTION_SHORT   = 6;

    private Hashtable _options = new Hashtable();
    private ArrayList _remainingArgs;
    protected Hashtable _values;

    /**
     * Convenience method to save some typing
     */
    protected final Option addOption (int type, char shrt, String lng) {
        switch (type) {
            case OPTION_BOOLEAN:
                return addBooleanOption (shrt, lng);
            case OPTION_DOUBLE:
                return addDoubleOption (shrt, lng);
            case OPTION_INTEGER:
                return addIntegerOption (shrt, lng);
            case OPTION_STRING:
                return addStringOption (shrt, lng);
            case OPTION_BYTE:
                return addByteOption (shrt, lng);
            case OPTION_SHORT:
                return addShortOption (shrt, lng);
        }

        assert (false) : "Invalid option type passed to addOption";
        return null;
    }

    protected final Option addStringOption (char shortForm, String longForm) {
        Option opt = new StringOption (shortForm, longForm);
        addOption (opt);
        return opt;
    }

    protected final Option addIntegerOption (char shortForm, String longForm) {
        Option opt = new IntegerOption (shortForm, longForm);
        addOption (opt);
        return opt;
    }

    protected final Option addByteOption (char shortForm, String longForm) {
        Option opt = new ByteOption (shortForm, longForm);
        addOption (opt);
        return opt;
    }

    protected final Option addDoubleOption (char shortForm, String longForm ) {
        Option opt = new DoubleOption (shortForm, longForm);
        addOption (opt);
        return opt;
    }

    protected final Option addBooleanOption (char shortForm, String longForm ) {
        Option opt = new BooleanOption (shortForm, longForm);
        addOption (opt);
        return opt;
    }

    protected final Option addShortOption (char shortForm, String longForm ) {
        Option opt = new ShortOption (shortForm, longForm);
        addOption (opt);
        return opt;
    }

    protected final Option addOption (Option opt) {
        _options.put ("-" + opt.shortForm(), opt);
        _options.put ("--" + opt.longForm(), opt);
        return opt;
    }

    public final Object getOptionValue (Option o) {
        if (null== _values) 
            return null;
        else 
            return _values.get (o.longForm());
    }

    public final String[] getRemainingArgs() {
        String[] ret = null;
        if (_remainingArgs != null) {
            ret = new String [_remainingArgs.size()];
            _remainingArgs.toArray (ret);
        }
        return ret;
    }
    //
    // For clearing out state
    //
    public final void clearParse() {
        _remainingArgs = new ArrayList();
        _values        = new Hashtable();        
    }
    public final void parse (String[] argv)
        throws IllegalOptionValueException, UnknownOptionException {
        parse (argv, Locale.getDefault());
    }

    public final void parse (String[] argv, Locale locale)
        throws IllegalOptionValueException, UnknownOptionException {
        int position = 0;
        _remainingArgs = new ArrayList();
        _values        = new Hashtable();
	if (argv == null)
	    return;
        while (position < argv.length) {
            boolean isLong = false;
            String curArg = argv[position];
            if (curArg.startsWith("-")) {
                if (curArg.equals("--")) {
                    position++;
                    break;
                }

                String valueArg = null;
                if (curArg.startsWith("--")) {
                    isLong = true;
                    int equalsPos = curArg.indexOf ("=");
                    if (equalsPos != -1) {
                        valueArg = curArg.substring (equalsPos + 1);
                        curArg = curArg.substring (0, equalsPos);
                    }
                }

                Option opt = (Option) _options.get (curArg);

                if (opt == null) {
                    throw new UnknownOptionException (curArg);
                }

                opt.isParsed (isLong ? Option.LONG : Option.SHORT);

                Object value = null;
                if (opt.wantsValue()) {
                    if (valueArg == null) {
                        position++;
                        valueArg = null;
                        if (position < argv.length) {
                            valueArg = argv[position];
                        }
                    }
                    value = opt.getValue (valueArg, locale);
                }
                else {
                    value = opt.getValue (null, locale);
                }
                _values.put (opt.longForm(), value);
                position++;
            }
            else {
                // shift non-argument bits to the end to support command-line
                // parameters like: cmdname SOMESTRING -x --do-something
                _remainingArgs.add (curArg);
                position++;
            }
        }
    }

    public boolean getOptionValueBoolean (Option opt) {
        Object obj = getOptionValue (opt);

        if (obj instanceof Boolean) {
            Boolean bool = (Boolean) obj;
            if (bool == Boolean.TRUE) {
                return true;
            }
        }

        return false;
    }

    public double getOptionValueDouble (Option opt) {
        Object obj = getOptionValue (opt);

        if (obj instanceof Double) {
            Double dbl = (Double) obj;
            return dbl.doubleValue();
        }

        return 0.0;
    }

    public Short getOptionValueShort (Option opt) {
        Object obj = getOptionValue (opt);

        if (obj instanceof Short) {
            Short shrt = (Short) obj;
            return shrt.shortValue();
        }

        return 0;
    }


    public int getOptionValueInteger (Option opt) {
        Object obj = getOptionValue (opt);

        if (obj instanceof Integer) {
            Integer intgr = (Integer) obj;
            return intgr.intValue();
        }

        return 0;
    }

    public byte getOptionValueByte (Option opt) {
        Object obj = getOptionValue (opt);

        if (obj instanceof Byte) {
            Byte byt = (Byte) obj;
            return byt.byteValue();
        }

        return -1;
    }


    public String getOptionValueString (Option opt) {
        Object obj = getOptionValue (opt);

        if (obj instanceof String) {
            return (String) obj;
        }

        return null;
    }
}
