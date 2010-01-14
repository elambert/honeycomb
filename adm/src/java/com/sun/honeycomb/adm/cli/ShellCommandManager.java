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


package com.sun.honeycomb.adm.cli;

import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Logger;


import com.sun.honeycomb.adm.client.AdminClientImpl;
import com.sun.honeycomb.adm.cli.config.CliBundleAccess;
import java.util.MissingResourceException;
import com.sun.honeycomb.adm.cli.config.*;

public class ShellCommandManager extends TreeMap {

    /** The property prefix for the command name entries */
    private static final String COMMAND_NAME_PREFIX 
        = "cli.hcsh.cmd.name.";

    /** The property defines where to look for commands */

    private static final String COMMAND_PACKAGE     
        = "cli.hcsh.cmd.package.adm";

    private static final String COMMAND_ALIASED   = ".aliases";
    private static final String COMMAND_IS_HIDDEN = ".isHidden";

    protected ShellCommandManager (CliConfigProperties p) {
        super();

        String pkgName = p.getProperty (COMMAND_PACKAGE);
        

        for (Enumeration names=p.propertyNames(); names.hasMoreElements();) {
            String name = (String) names.nextElement();
        
            if (name.startsWith (COMMAND_NAME_PREFIX)) {
                if (name.endsWith (COMMAND_ALIASED) 
                       || name.endsWith (COMMAND_IS_HIDDEN)) {
                    // skip isHidden and aliases sub-properties
                    continue;
                }

                String className 
                    = name.substring (COMMAND_NAME_PREFIX.length());

                String cmdName = p.getProperty (
                    COMMAND_NAME_PREFIX + className);

                String[] aliases = null;
                String aliasString=null;
                try {
                    aliasString =getLocalString("cli.commandname."+cmdName+".aliases");
                }  catch (MissingResourceException e) {
                    //
                    // There are no aliases
                    //
                    aliasString=null;                   
                }

                if (null == aliasString) {
                    aliases = null;
                }
                else {
                    aliases = aliasString.split("\\s*,\\s*");
                }

                Boolean isHidden = Boolean.valueOf (
                    p.getProperty (COMMAND_NAME_PREFIX
                        + className + ".isHidden"));
                // Make sure we have the trailing .dot
                if (! (pkgName.endsWith ("."))) {
                    pkgName = pkgName + ".";
                }

                String internationalizedCommand=null;
                try {
                    internationalizedCommand=getLocalString("cli.commandname."+cmdName);
                }  catch (MissingResourceException e) {
                    //
                    // Command isn't aliased - disable and continue
                    //
                    // Internationalize
                    System.out.println("Missing bundle resource - cli.commandname."+cmdName + ". Command disabled.");
                    continue;
                    
                    
                }

                Object obj;
                try {

                    Class cls = Class.forName (pkgName + className);
                    Constructor[] constr = cls.getConstructors();
                    // TODO: allow for other constructors to be defined
                    obj = constr[0].newInstance(
                        new Object[] {internationalizedCommand,
                                      aliases,
                                      isHidden});
                }
                catch (Exception cnfe) {
                    //
                    // Internationalize
                    // 
                    System.out.println (
                        "unable to initialize command '" + internationalizedCommand 
                        + "' class " + (pkgName + internationalizedCommand) + ": " 
                        + cnfe.getMessage());


                    continue;
                }


                if (obj instanceof IShellCommand) {
                    
                    put (internationalizedCommand, obj);


                    if (aliases != null) {
                        for (int i = 0; i < aliases.length; ++i) {
                            put (aliases[i], obj);
                        }
                    }
                }
                else {
                    System.out.println (
                        "Unable to initialize command '" + className 
                        + "' is not an instanceof IShellCommand");                    
                    continue;
                }
            }
        }
    }

    protected String getLocalString (String key) {
        String localString=null;

        localString= CliBundleAccess.getInstance().getBundle().getString(key);
        return localString;
    }

    public IShellCommand get (String name) throws CommandNotFoundException {




        Object cmd = get ((Object) name);
        if (cmd == null) {
            throw new CommandNotFoundException (name + "; " +
                                                getLocalString ("cli.command_not_found"));
        }
        return (IShellCommand) cmd;
    }
}
