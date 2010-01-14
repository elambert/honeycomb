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



package com.sun.honeycomb.emd.parsers;


import java.util.ArrayList;
import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;


import com.sun.honeycomb.emd.config.FsView;
import com.sun.honeycomb.emd.config.Filename;
import com.sun.honeycomb.emd.config.EMDConfigException;
import com.sun.honeycomb.emd.config.RootNamespace;

/**
 * Parser for a virtual view filename. A valid string is a sequence of
 * tokens, of which there are two types: variable references
 * (e.g. ${foo}), and literal strings (anything else).
 */
public class FilenameParser {

    private RootNamespace rootNamespace;
    private FsView fsView;
    private SimpleBuffer fnameBuf;

    public static Filename parse(String filename,
                                 FsView fsView,
                                 RootNamespace rootNamespace) 
        throws ParseException, EMDConfigException {

        FilenameParser parser =
            new FilenameParser(rootNamespace, fsView, filename);
        return parser.doParse();
    }

    private FilenameParser(RootNamespace rootNamespace,
                           FsView fsView,
                           String input) {
        this.rootNamespace = rootNamespace;
        this.fsView = fsView;
        this.fnameBuf = new SimpleBuffer(input);
    }

    /**
     * This is a simple recursive-descent parser. Well, it <em>would</em>
     * be one, except that the grammar is not recursive (in fact, it's
     * regular -- it accepts a sequence of two types of tokens).
     */
    private Filename doParse() throws EMDConfigException, ParseException {
        ArrayList elements = new ArrayList();
        Filename.Element element;

        while ((element = nextElement()) != null)
            elements.add(element);

        Filename.Element[] els = new Filename.Element[elements.size()];
        elements.toArray(els);

        return new Filename(fsView, els, rootNamespace);
    }

    /** Swallow chars for the next token and return an Element */
    private Filename.Element nextElement() throws ParseException {
        if (fnameBuf.atEnd())
            return null;

        if (fnameBuf.lookaheadChar() == '$') {
            // Variable reference.

            if (!fnameBuf.matchAndSkip("${"))
                throw new ParseException("At pos " + fnameBuf.pos() +
                                         ", $ not followed by {");

            // Grab everything till "}"
            int startPos = fnameBuf.pos();
            String s = fnameBuf.getToChar('}');
            if (s == null)
                throw new ParseException("At pos " + startPos +
                                         ", unclosed ${");
            // Skip the "}"
            fnameBuf.matchAndSkip("}");

            return new Filename.Element(Filename.REPRESENTATION_VARIABLE, s);
        }
        else {
            // Literal: grab everything till "$" (or EOS)
            String s = fnameBuf.getToCharOrEnd('$');
            return new Filename.Element(Filename.REPRESENTATION_STRING, s);
        }
    }

    ////////////////////////////////////////////////////////////////
    // A simple string buffer: a string and a position inside it

    private static class SimpleBuffer {

        private String input;
        private int pos;

        SimpleBuffer(String s) {
            input = s;
            reset();
        }

        void reset() { pos = 0; }
        int pos() { return pos; }

        boolean atEnd() {
            return input == null || pos < 0 || pos >= input.length();
        }

        /** Peek into the buffer without advancing the position */
        char lookaheadChar() {
            return input.substring(pos, pos+1).charAt(0);
        }

        /** 
         * If the current point in the buffer is the string val, skip
         * past it and return true. (Else do nothing, return false.)
         */
        boolean matchAndSkip(String val) {
            if (input.substring(pos).startsWith(val)) {
                pos += val.length();
                return true;
            }

            return false;
        }

        /** Return the next n characters and advance the position */
        String getChars(int n) {
            if (pos + n > input.length())
                return null;
            int startPos = pos;
            pos += n;
            return input.substring(startPos, pos);
        }

        /** Get the substring till character c; null if c not present */
        String getToChar(char c) {
            return getToCharOrEnd(c, false);
        }

        /** Get the substring till character c; till EOL if c not present */
        String getToCharOrEnd(char c) {
            return getToCharOrEnd(c, true);
        }

        private String getToCharOrEnd(char c, boolean endOK) {
            int startPos = pos;
            int endPos = input.indexOf(c, pos);
            if (endPos < 0) {
                if (!endOK)
                    return null;
                pos = -1;
                return input.substring(startPos);
            }
            pos = endPos;
            return input.substring(startPos, endPos);
        }
    } // class SimpleBuffer

    ////////////////////////////////////////////////////////////////
    // For testing

    private void testParse() throws ParseException {
        Filename.Element element;

        while ((element = nextElement()) != null)
            System.out.println("\"" + element.toString() + "\"");
    }

    public static void main(String args[]) {
        BufferedReader console =
                new BufferedReader(new InputStreamReader(System.in));
        String line;
        System.err.print("> ");

        try {
            while ((line = console.readLine()) != null) {
                FilenameParser parser = new FilenameParser(null, null, line);
                try {
                    parser.testParse();
                }
                catch (ParseException e) {
                    System.err.println("Exception on \"" + line + "\": " + e);
                }

                System.err.print("> ");
            }
        }
        catch (IOException e) {
            System.err.println("Exception on stdin: " + e);
        }
    }

}
        
