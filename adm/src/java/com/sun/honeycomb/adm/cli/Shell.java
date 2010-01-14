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

import sun.misc.Signal;
import sun.misc.SignalHandler;

import com.sun.honeycomb.adm.client.*;
import com.sun.honeycomb.adm.cli.config.*;


import com.sun.honeycomb.adm.cli.config.CliBundleAccess;

import com.sun.honeycomb.adm.cli.editline.Editline;
import com.sun.honeycomb.adm.cli.editline.EditlineCompleter;
import com.sun.honeycomb.adm.cli.PermissionException;
import com.sun.honeycomb.adm.cli.ConnectException;

import com.sun.honeycomb.adm.cli.commands.ShowHelp;
import java.io.IOException;
import java.io.EOFException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.Properties;
import java.util.Iterator;
import java.util.Vector;
import java.util.ArrayList;
import com.sun.honeycomb.adm.cli.config.CliConfigProperties;
import com.sun.honeycomb.mgmt.common.MgmtException;
import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple *nix shell written in Java. Reads input via 
 * {@link com.sun.honeycomb.adm.cli.editline.Editline}, a small wrapper
 * around libeditline.so, parses it and runs commands {@link ShellCommand}.
 */
public class Shell implements EditlineCompleter {

    /** Used to store all of the shells currently running. We use a vector
     *  because maybe one day we'll allow backgrounding and we'll need a 
     *  stack. */
    protected static Vector SHELL_STACK = new Vector();

    protected Editline            _editline;
    protected CliConfigProperties          _config;
    protected ShellCommandManager _commands;
    protected Properties          _env;

    protected boolean            _keepRunning;
    protected Iterator           _completionMatches;

    protected Thread             _shellThread;

    protected boolean            _isInteractive;
    protected String[]           _batchCmds;
    protected int                _batchIndex;
    protected AdminClient          _api;
    protected AdminClientInternal  _apiInternal;

    public static Logger	logger = Logger.getLogger("com.sun.honeycomb.adm.cli");
    private static ShutdownThread _shutdownThread;

    /**
     *
     */
    protected Shell (boolean isInteractive) {
        _isInteractive = isInteractive;

        try {
            _config = CliConfigProperties.getInstance ();
        }
        catch (Exception e) {
            
            System.out.println ("Failed to read cell config: " + e.getMessage());
            System.out.println ("Management Console unavailable.");
            System.exit (1);
        }

        if (null == _config) {
            System.out.println ("Management Console unavailable.");
            System.exit (2);
        }


        _keepRunning = true;
        _shellThread = Thread.currentThread();

        // Create and register a new SignalHandler
        SignalHandler handler = new SignalHandler() {
            public void handle (Signal sig) {
                try {
                    _shellThread.interrupt();
                }
                catch (SecurityException se) {
                    System.out.println ("SignalHandler unable to interrupt thread: "
                        + se.getMessage());
                    System.exit(254);
                }
            }
        };
        Signal.handle (new Signal ("INT"), handler); 

        _editline = Editline.create ("hcsh");
        _editline.setCompleter (this); // TODO: Make this work!


        // This will force an instantiation of the default local and resource
        // bundle, which, if not found, should cause the JVM to abort.
        try {
            _api  = new AdminClientImpl();
            _apiInternal = new AdminClientInternalImpl(_api);

        } catch (PermissionException pe) {
            logger.log(Level.SEVERE, "CLI: Internal error: Unable to disable autologout");
            System.exit(2);
        } catch (Exception e) {
            System.out.println("Unable to connect to master cell, check the " +
              "system is online and reachable");
            System.exit(2);
        }

        // This must be done before a new ShellCommandManager() is created
        SHELL_STACK.addElement (this);

        // Initialize the environment and commands
        _env = new Properties();
        _commands = new ShellCommandManager (_config);
    }

    /**
     * Returns true if we should keep processing input
     */
    protected boolean keepRunning() {
        return _keepRunning;
    }

    /**
     * Returns true if this is an interactive shell, false if it's scripted.
     */
    protected boolean isInteractive() {
        return _isInteractive;
    }

    /**
     *
     */
    public String prompt (String text) 
        throws EOFException, IOException {
        return prompt (text, true);
    }
    
public String promptNoEcho (String text) 
    throws EOFException, IOException {
    if (null != _editline) {
        _editline.disableEcho();
    }
    return prompt (text, true);
//_editline.enableEcho();
}

    public String prompt (String text, boolean editable) 
        throws EOFException, IOException {
        String data = null;
	assert _editline != null;
	data = _editline.readline (text, editable);
        return data;
    }
    
    public void cleanup () {
        _editline.cleanup();
        try {
            getApi().logout();
        } catch (MgmtException e) {
            System.err.println("Error on hive logout.");
        } catch (ConnectException e) {
            System.err.println("Error on hive logout.");
        }
    }

    /**
     * Call this to end the exit the shell
     */
    protected void terminate () {
        _keepRunning = false;
    }

    /**
     * Used to get the ShellCommandManager for this Shell
     */
    protected ShellCommandManager getShellCommandManager () {
        return _commands;
    }


    public void disableAutoLogout() {       
        if(!Runtime.getRuntime().removeShutdownHook (_shutdownThread)) {
            logger.log(Level.SEVERE, "CLI: Internal error: Unable to disable autologout");
        }

    }

    /**
     * This is the main method of the shell. It loops, reading input and
     * executing commands. Commands are read from some the arguments passed to 
     * Shell by login.
     */
    protected void run () {
        while (keepRunning()) {
            try {
                long start = System.currentTimeMillis();
                Command[] cmds = parseCommand();
                long stop = System.currentTimeMillis();

                if (cmds != null) {
                    for (int i = 0; i < cmds.length; i++) {
                        try {

			    int exitcode = exec (cmds[i]);

			    // log command to external log
                            String str = 
                                getLocalString("info.admin.cli.execcommand");

			    // create array with command, status, and arguments
			    Object [] args = { cmds[i].getName(), 
					       (exitcode == 0)? new String("success") : new String("error"),
					       Arrays.toString(cmds[i].getArgs()) };


                        }
                        catch (CommandNotFoundException cnfe) {
                            System.out.println (cnfe.getMessage());                            
                            _env.setProperty ("?", "1");
                            new ShowHelp(_commands);
                        } 
                        catch (MgmtException ce) {
			    outputException(ce);
                            _env.setProperty ("?", "2");
			    logger.log(Level.FINE, "CLI: ", ce);

                        }  catch (PermissionException e) {
                            System.out.println("\n" + getLocalString("common.readonlyerr"));


                        }  catch (ConnectException e) {
                            System.out.println("Unable to connect to master cell in hive: " +
                                               e.getMessage()+
                                               ". Hive may not be online.");
                        } catch (Throwable t) {
			    outputException(t);
                        }
                    }
                }
            }
            catch (EOFException e) {
                System.out.println ("exit");
                terminate();
            } 
            catch (AdminUnavailableException e) {

                System.out.println (e.getMessage());
                terminate();
            }
            catch (Throwable t) {

                System.out.println (
                    getLocalString ("common.default_error") + ": " 
                            + t.getMessage());
                logger.log(Level.SEVERE, "CLI: Internal error", t);
            }
        }
    }

    /**
     * Gets the next command for non-interactive execution
     */
    protected String nextBatchCommand () {
        String next = null;
        if (_batchIndex < _batchCmds.length) {
            next = _batchCmds[_batchIndex];
            _batchIndex++;
        }
        return next;
    }

    /**
     * Parses either STDIN or the arguments passed to the shell to create
     * a Command object, which represents all data needed to run a command.
     */
    protected Command[] parseCommand() throws IOException, EOFException {
        Command[] cmds = null;
        String line = null;
        
        if (isInteractive()) {
            line = _editline.readline ("ST5800 $ ", true);
            
        } else {
            line = nextBatchCommand();
            if (line == null) {
                terminate();
            }
        }
    
        //
        // Tiny hooky here on line.
        //
        if (line != null && line.length() > 0) {
            // normalize all of spaces, because split ("\\s+") doesn't do 
            // the right thing...
            line = line.trim();
            line = line.replaceAll ("\\s+", " ");
            String[] commands = line.split ("\\s*;\\s*");
            cmds = new Command[commands.length];

            for (int i = 0; i < commands.length; i++) {
                String[] cmdArgs = commands[i].split ("\\s");
                String[] argv = null;
            
                if (cmdArgs.length > 1)  {
                    argv = new String [cmdArgs.length - 1];
                    System.arraycopy (cmdArgs, 1, argv, 0, cmdArgs.length - 1);
                }

                cmds[i] = new Command (cmdArgs[0], argv);
            }
        }

        return cmds;
    }

    /**
     * Executes a Command
     */
    protected int exec (Command cmd) throws CommandNotFoundException,
                                            MgmtException,
                                            ConnectException,
                                            PermissionException {
    

        IShellCommand command = (IShellCommand) _commands.get (cmd.getName());
        return command.main (_env, cmd.getArgs());
    }
    
    /**
     * Display the copyright notice
     */
    protected void displayCopyright() {
	System.out.println(getLocalString("cli.copyright.miniNotice"));
    }

    /**
     * Just a silly helper function to display the message of the day
     */
    protected void displayMOTD () {
        System.out.println (getLocalString ("common.appname"));
    }

    /** 
     * One day this will perform command-line completion
     */
    public String complete (String text, int state) {
        if (state == 0) {
            // first call to complete(): initialize our choices-iterator
            _completionMatches = _commands.tailMap(text).keySet().iterator();
        }

        if (_completionMatches.hasNext()) {
            String nextKey = (String) _completionMatches.next();

            // make sure the cmmand is not hidden:
            try {
                IShellCommand cmd = (IShellCommand) _commands.get (nextKey);
                while (cmd.isHidden()) {
                    nextKey = (String) _completionMatches.next();
                    cmd = (IShellCommand) _commands.get (nextKey);
                }
            } catch (CommandNotFoundException e) {
                assert false : "command not found during tab lookup";
            }

            if (nextKey.startsWith(text)) {
                return nextKey;
            }
        }

        return null; // no more matches to try
    }

    /**
     * Gets the currently executing Shell
     */
    public static Shell getCurrentShell ()
    {   
        //System.out.println ("Shell.getCurrentShell()");
        return (Shell)(SHELL_STACK.elementAt(SHELL_STACK.size()-1));
    }

    protected AdminClient getApi ()
    {
        //System.out.println ("Shell.getApi()");
        return _api;
    }


    /**
     * Handle to get a AdminClientInternal obj, since we really need only one.
     */
    protected AdminClientInternal getInternalApi()
    {
        //System.out.println ("Shell.getApi()");
        return _apiInternal;
    }


    protected String getLocalString (String key) {
        String localString=null;

        localString= CliBundleAccess.getInstance().getBundle().getString(key);
        return localString;
    }


    /**
     * This is the entry point into the Shell.
     */
    public static void main (String[] argv) {
        boolean isInteractive = true;
        if (argv.length > 0) {
            isInteractive = false;
        }
        Shell app = new Shell (isInteractive);



        //System.out.println ("Adding shutdown hoook");
        _shutdownThread = new ShutdownThread(app);
        Runtime.getRuntime().addShutdownHook (_shutdownThread);

        if (isInteractive) {
            app.displayMOTD ();
	    app.displayCopyright();
        }
        else {
            app._batchIndex = 0;

            // argv[0] = "-c"
            if (argv.length == 2) {
                // there's only one command and no args, so don't do all the
                // messiness below
                app._batchCmds = new String[] { argv[1] };
            }
            else {
                StringBuffer tmp = new StringBuffer();
                for (int i = 1; i < argv.length; i++) {
                    tmp.append (argv[i]);
                    if (i+1 < argv.length) {
                        tmp.append (" ");
                    }
                }
                String[] commands = tmp.toString().split ("\\s*;\\s*");
                app._batchCmds = commands;
            }
        }
        app.run();
    }
    
    void enableEcho(boolean enable)
    {
        if (null != _editline) {
            if (enable) {
                _editline.enableEcho();
            } else {
                _editline.disableEcho();
            }
        }
        
    }
    
    /**
     * Prints out the error message that should be outputed to the user
     * based on the exception.  In the case of internal errors and
     * communication problems a generic message is outputed instead.
     * In these cases the exception stack trace is outputed to the log
     * with the prefix "CLI:"
     *
     * @param e the execption to output.
     */
    public static void outputException(Throwable t) {
	if (t instanceof MgmtException) {
	    String msg = t.getMessage();
	    
	    // TODO: This is a temporary workaround until I (KT) can figure out
	    // how to override the mof classes to use a different exception
	    // class that extents from MgmtException for communication errors
	    // that occur when using the JAXP/SOAP infrastructure.  The current
	    // automated messages generated are not user friendly.   Therefore
	    // generate a more friendly user message and log the actual
	    // exception to the log file.
	    if (msg.startsWith("fetch")) {
		System.out.println(
		    "Unable to perform requested operation due to a communication problem with the\n" +
		    "server.  If this problem persists contact the administrator of this system.");
		Throwable cause = t.getCause() != null ? t.getCause() : t;
		Shell.logger.log(Level.SEVERE, "CLI: Fetch error", cause);
	    } else {
		System.out.println(t.getMessage());
	    }
	} else {
	    System.out.println(
		"Internal error, if the problem persists please report "
		+ "the problem to your\nSun Customer service representative.");
	    Shell.logger.log(Level.SEVERE, "CLI: Internal error", t);
	}
        
    }

    /**
     * Inner helper class. One day this might be merged into ShellCommand, but
     * for now it's seperate and distinct.
     */
    class Command {
        private String   _name;
        private String[] _argv;

        Command (String name, String[] argv) {
            _name = name;
            _argv = argv;
        }

        public String[] getArgs() {
            return _argv;
        }

        public String getName() {
            return _name;
        }
    }

    public static class ShutdownThread extends Thread {
        Shell _shell = null;

        ShutdownThread(Shell shell) {
            super();
            _shell = shell;
        }

        public void run() {
            //System.out.println ("Shutdown hook runnning");
            _shell.cleanup();
        }
    }
    
   
}
