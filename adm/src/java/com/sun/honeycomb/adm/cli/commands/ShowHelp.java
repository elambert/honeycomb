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



package com.sun.honeycomb.adm.cli.commands; 

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.util.Iterator;
import com.sun.honeycomb.adm.cli.config.CliBundleAccess;
import java.util.MissingResourceException;
import com.sun.honeycomb.adm.cli.ShellCommandManager;
import com.sun.honeycomb.adm.cli.IShellCommand;
import com.sun.honeycomb.adm.cli.CommandNotFoundException;

public class ShowHelp {
    public ShowHelp( ShellCommandManager commands) {
        try {
            BufferedWriter out 
                = new BufferedWriter (new OutputStreamWriter(System.out));

            int counter = 1;
            out.write(getLocalString("cli.help.assist"));
            out.newLine();
            for (Iterator iter = commands.keySet().iterator(); 
                    iter.hasNext();) { 

                String name = (String) iter.next();

                IShellCommand cmd;
                try {
                    cmd = (IShellCommand) commands.get (name);
                }
                catch (CommandNotFoundException cnfe) {
                    // skip a bit, brother
                    continue;
                }

                if (! cmd.getName().equals (name) || cmd.isHidden()) {
                    // skip this (it's an alias or a hidden command)
                    continue;
                }

                if (counter % 5 == 0) {
                    counter = 1;
                    out.newLine();
                    out.flush();
                }

                int nameLength = name.length();

                if (nameLength > 19) {
                    out.write (name.substring (0, 19));
                }
                else if (nameLength < 20) {
                    out.write (name);
                    int paddingLength = 20 - nameLength;
                    for (int i = 0; i < paddingLength; ++i) {
                        out.write (" ");
                    }
                }

                ++counter;
            }

            out.newLine();
            out.flush();
        }
        catch (IOException ioe) {
            assert false;
        }
    }
    protected String getLocalString(String l) {
        try {
            return CliBundleAccess.getInstance().getBundle().getString(l);
        } catch (MissingResourceException e) {

            String missingString=getLocalString("common.missingString");
            return missingString;
        }
        
    }
}
