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



package com.sun.honeycomb.common;

import java.util.BitSet;

public class Getopt {
    private BitSet optchars;
    private BitSet optsWithArg;

    private String optstring;
    private String optstringNoArgs;
    private String[] argv;
    private int optind;

    public class Option {
	private char optname;
	private String optarg;
	private int optind;
	private boolean hasMore;
        private boolean noArgs;
        private boolean noSwitch;

	public int index() { return optind; }
	public char name() { return optname; }
	public String value() { return optarg; }
        public boolean noargs() {return noArgs; }
        public boolean noSwitch() {return noSwitch; }
        
	Option(int index, char name, String optarg, boolean noArgs, boolean noSwitch) {
	    this.optind = index;
	    this.optname = name;
	    this.optarg = optarg;
            this.noArgs = noArgs;
            this.noSwitch = noSwitch;
	}
    }

    public Getopt(String[] args, String optstr) {
	this.argv = args;
	this.optstring = optstr;

	setup();
    }

    public Getopt(String[] args, String optstr, String optstrNoArgs) {
        this.argv = args;
        this.optstring = optstr;
        this.optstringNoArgs = optstrNoArgs;
        
        setup();
    }

    public boolean hasMore() {
	return switchOption() || nonSwitchOption();
    }

    private boolean switchOption() {
	return optind < argv.length && isValidOption(argv[optind]);        
    }
    
    private boolean nonSwitchOption() {
        return optind < argv.length && !isOption(argv[optind]);
    }

    public String[] remaining() { 
        if (optind >= argv.length) 
            return null; 
    
        String[] retval = new String[argv.length - optind]; 
        for (int i = 0; i < retval.length; i++) 
            retval[i] = argv[optind + i]; 
 
        return retval; 
    }
    
    public void back() {
        if (optind > 0)
            optind--;
    }

    /** The meat */
    public Option next() {
	// If an option doesn't take an argument, it may be combined
	// with other non-argument-taking options. We handle this by
	// removing the handled option from the string i.e. "-xzf",
	// after we handle the "x" option, becomes "-zf"

	for (;;) {
	    if (!switchOption()) {
                if (nonSwitchOption()) {
                    int index = optind;
                    optind++;
                    String nonSwitch = "-";
                    return new Option(optind, nonSwitch.charAt(0), argv[index], false, true);
                }
                else {
                    return null;
                }
            }

	    // Examine argv[optind]. We already know it begins with a
	    // '-'. Is it in optchars? If not, it's bogus.
	    String arg = argv[optind];
	    char o = arg.charAt(1);

	    if (!optchars.get(o)) {
		System.err.println("Option \"-" + o + "\" unknown.");
		if (arg.length() > 2)
		    argv[optind] = "-" + arg.substring(2);
		else
		    optind++;
		continue;
	    }

	    // Is it in optsWithArg? If so, find an argument for it:
	    // the rest of the string if it's of size > 2, the next
	    // argument if it's not

	    String optarg = null;
	    int index = optind;

	    if (optsWithArg.get(o)) {
		optind++;
		if (arg.length() > 2)
		    optarg = arg.substring(2);
		else {
		    if (optind >= argv.length)
			optarg = "";
		    else
			optarg = argv[optind++];
		}
	    }
	    else {
		// Set up for option after this one
		if (arg.length() > 2)
		    argv[optind] = "-" + arg.substring(2);
		else
		    optind++;
	    }
	    return new Option(index, o, optarg, false, false);
	}
    }

    private void setup() {
	optind = 0;
	optchars = new BitSet(256);
	optsWithArg = new BitSet(256);

	char prev = 0;
	for (int i = 0; i < optstring.length(); i++) {
	    char c = optstring.charAt(i);
	    if (c == '-' || c == ' ' || c == '?')
		throw new RuntimeException("Option string \"" + optstring +
					   "\" malformed");
	    if (c == ':') {
		if (prev > 0)
		    optsWithArg.set(prev);
		prev = 0;
	    }
	    else {
		optchars.set(c);
		prev = c;
	    }
	}
    }
    
    private boolean isValidOption(String s) {
        return isOption(s) && optchars.get(s.charAt(1));
    }
    
    private boolean isOption(String s) {
	return s.length() > 1 && s.startsWith("-");
    }

    public static void main(String[] args) {
	Getopt opts = new Getopt(args, "a:b:c::xyz");
	while (opts.hasMore()) {
	    Getopt.Option option = opts.next();

	    System.out.print("Got option \"-" + option.name() + "\"");
	    if (option.value() != null)
		System.out.print(" with argument \"" + option.value() + "\"");
	    System.out.println("");
	}
    }
}
