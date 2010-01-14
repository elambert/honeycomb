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


package com.sun.honeycomb.adm.cli.editline;


import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;

/**
 * A simple wrapper around libedit (Editline). It uses native methods calls,
 * if they are available (ie, libedit.a or .so is present). Otherwise it
 * defaults to using a BufferedReader.
 */
public class Editline {

    /** the class we should use to complete command names */
    private EditlineCompleter _completer = null;

    /** boolean that controls whether we should use native calls */
    private boolean           _useEditline = true; 

    /** reader used when Editline cannot be found */
    private BufferedReader    _reader  = null;

    /** a timer thread used to interrupt us when we exceed our allotment */
    private PromptTimeout     _timeout = null;

    protected Editline () {
        super();
        //
        //  50 minutes
        //
        _timeout = new PromptTimeout (50*60*1000, Thread.currentThread());
        _timeout.start();
    }
    
    public static Editline create (String appName) {
        Editline e = new Editline();

        try {
            /** attempt to load libjava-editline.so */
            System.loadLibrary ("java-editline");
        }
        catch (UnsatisfiedLinkError ule) {
            System.out.println ("Error loading libjava-editline.so: " 
                + ule.getMessage());
            e._useEditline = false;
        }

        if (e._useEditline) {
            e.initEditlineImpl (appName);
        }

        return e;
    }
   
    /**
     *
     * Displays a prompt on stadout and read a string from stdin. NULL is 
     * returned if the user presses enter, rather than an empty String.
     * EOFException is thrown if the user hits CTRL-D,
     */
    public String readline (String prompt, boolean useEditline) 
        throws EOFException, IOException, UnsupportedEncodingException {
        String line = "";
  
        try {
            _timeout.arm();
//            System.out.println ("_useEditline: " + _useEditline);
//            System.out.println ("useEditline: " + useEditline);
            if (_useEditline && useEditline) {
//                System.out.println ("used editline:");
                line = readlineImpl (prompt);

                if (line != null) {
                    addToHistory (line);
                }
            }
            else {
                // This code is never used unless the native readline 
                // isn't available
                System.out.print (prompt);
    
                if (_reader == null) {
                    _reader = new BufferedReader (
                        new InputStreamReader (System.in));
                }

                line = _reader.readLine();

                if (line == null) {
                    throw new EOFException ("end-of-file");
                }

                if (line.length() == 0) {
                    line = null;
                }
            }
            _timeout.reset();
        } catch (InterruptedException e) {
            System.out.println ("Connection timed out: " + e.getMessage());
        }

        return line;
    }

    public String readline (String prompt) 
        throws EOFException, IOException, UnsupportedEncodingException {
        return readline (prompt, false);
    }

    public void addToHistory(String line) {
        if (_useEditline) {
            addToHistoryImpl(line);
        }
    }

    public void setCompleter (EditlineCompleter elc) {
        _completer = elc;
        if (_useEditline) {
            setCompleterImpl (_completer);
        }
    }

    public EditlineCompleter getCompleter () {
        return _completer;
    }

    public void cleanup() {
        if (_useEditline) {
            cleanupEditlineImpl();
        }
    }

    public boolean hasTerminal () {
        if (_useEditline) {
            return hasTerminalImpl();
        }

        return true;
    }

    public String getLineBuffer() {
        if (_useEditline) {
            return getLineBufferImpl();
        }

        return null;
    }
/*
    public int getHistoryLength () {
        if (_useEditline) {
            return getHistoryLengthImpl();
        }

        return 0;
    }

    public void clearHistory () {
        if (_useEditline) {
            clearHistoryImpl ();
        }
    }

    public String getHistoryEntry (int i) {
        if (i < 0 || i > getHistoryLength()) {
            throw new ArrayIndexOutOfBoundsException (i);
        }

        return getHistoryEntryImpl (i);
    }

    public void getHistory (Collection c) {
        if (_useEditline) {
            getHistoryImpl (c);
        }
    }
    public void readHistoryFile (String filename)
        throws EOFException, UnsupportedEncodingException {
        if (_useEditline) {
            readHistoryFileImpl (filename);
        }
    }

    public void writeHistoryFile (String filename)
        throws EOFException, UnsupportedEncodingException {
        if (_useEditline) {
            writeHistoryFileImpl (filename);
        }
    }

    public String getWordBreakCharacters() {
        if (_useEditline) {
          return getWordBreakCharactersImpl();
        }

        return null;
    }

    public void setWordBreakCharacters (String wordBreakCharacters)
        throws UnsupportedEncodingException {
        if (_useEditline) {
            setWordBreakCharactersImpl(wordBreakCharacters);
        }
    }
*/
    /* ********** NATIVE CODE IMPLEMENTATIONS ********** */

    private native void initEditlineImpl (String appName);
    
    private native void cleanupEditlineImpl();

    private native String readlineImpl (String prompt)
        throws EOFException, UnsupportedEncodingException, InterruptedException;

    private native void addToHistoryImpl(String line);
    
    private native boolean hasTerminalImpl();

    private native void setCompleterImpl (EditlineCompleter _completer);

    private native String getLineBufferImpl();

    public native static void disableEcho ();
    public native static void enableEcho ();
/*
    private native String[] getHistoryImpl (Collection c);
    private native int getHistoryLengthImpl();
    private native void clearHistoryImpl ();
    private native String getHistoryEntryImpl (int i);
    private native void readHistoryFileImpl (String filename)
        throws EOFException, UnsupportedEncodingException;
    private native void writeHistoryFileImpl (String filename)
        throws EOFException, UnsupportedEncodingException;
    private native String getWordBreakCharactersImpl();
    private native void setWordBreakCharactersImpl (String wordBreakCharacters)
        throws UnsupportedEncodingException;
*/
}
